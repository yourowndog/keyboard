/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.keyboard.pack

import dev.patrickgold.florisboard.app.layoutbuilder.LayoutPack
import dev.patrickgold.florisboard.ime.keyboard.PackRoleSource
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Reading and writing layout packs, by schema version.
 *
 * This is the one place that decides what a pack file *is*. Three outcomes are possible and all
 * three are reportable:
 *
 * - the bytes are not a pack at all — [LayoutPackReadOutcome.document] is null;
 * - the bytes are a pack this build understands — a document comes back, possibly with problems and
 *   with diagnostics from the compatibility path;
 * - the bytes declare a schema newer than [LayoutPackSchema.CURRENT_VERSION] — refused with
 *   [LayoutPackProblemKind.UNSUPPORTED_SCHEMA_VERSION] rather than decoded as far as it happens to
 *   fit.
 *
 * That last case is the reason this codec exists rather than a bare `Json { ignoreUnknownKeys }`.
 * Ignoring unknown keys is what version 1 did, and it means a pack written by a newer build loads
 * silently with its new capabilities stripped — a conditional row becomes unconditional, a policy
 * override vanishes — and then gets written back in that reduced form. Truncation is not
 * compatibility.
 */

/**
 * Everything learned from reading one pack file.
 *
 * Carrying a document *and* problems together is deliberate: a pack can be understandable and
 * unusable at the same time, and the user needs to be told which rows are wrong rather than that
 * "the file failed".
 *
 * @param document what was decoded, or null if nothing could be.
 * @param sourceVersion the schema version the bytes declared, or [LayoutPackSchema.LEGACY_VERSION]
 *   for an unversioned version 1 pack.
 * @param problems why the pack may not be activated. Empty means it may.
 * @param diagnostics decisions the compatibility path made on the pack's behalf.
 * @param roleSources how each row's role was decided, by row id. Empty for a pack already written at
 *   the current schema, where every role is a declared field.
 */
data class LayoutPackReadOutcome(
    val document: LayoutPackDocument?,
    val sourceVersion: Int,
    val problems: List<LayoutPackProblem> = emptyList(),
    val diagnostics: List<LayoutPackDiagnostic> = emptyList(),
    val roleSources: Map<String, PackRoleSource> = emptyMap(),
) {
    /** Whether this pack is safe to activate. */
    val isUsable: Boolean
        get() = document != null && problems.isEmpty()

    /** The decoded pack as an activatable value, or null when it may not be activated. */
    fun asActivePack(): ActiveLayoutPack? =
        document?.takeIf { problems.isEmpty() }?.let { ActiveLayoutPack(it, roleSources) }

    /** Whether reading it required the version 1 compatibility path. */
    val wasMigrated: Boolean
        get() = document != null && sourceVersion < LayoutPackSchema.CURRENT_VERSION

    /** A single-line summary for logs and for the Layout Builder. */
    fun describe(): String = when {
        document == null -> "unreadable: " + problems.joinToString { it.describe() }
        problems.isEmpty() -> "ok (schema $sourceVersion)"
        else -> "invalid (schema $sourceVersion): " + problems.joinToString { it.describe() }
    }
}

object LayoutPackCodec {

    /**
     * The writer and the strict version 2 reader.
     *
     * `encodeDefaults` is load-bearing: [LayoutPackDocument.schemaVersion] has a default, and
     * omitting it would write a version 2 pack that reads back as unversioned version 1.
     * `explicitNulls = false` keeps absent optional fields absent rather than writing `null` for
     * them, so a round trip is byte-stable and an omitted `policy` stays omitted.
     */
    private val strictJson = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    /**
     * The version 1 reader.
     *
     * Tolerant on purpose, and pinned as such by `PersistenceMigrationFixtureTest`: version 1 packs
     * on disk were written by builds that ignored unknown keys, so refusing one now would orphan a
     * user's saved layout. Tolerance stops at version 1 — it is not inherited by version 2.
     */
    private val legacyJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /** Serializes [document] at the current schema version. */
    fun encode(document: LayoutPackDocument): String =
        strictJson.encodeToString(
            LayoutPackDocument.serializer(),
            document.copy(schemaVersion = LayoutPackSchema.CURRENT_VERSION),
        )

    /**
     * Reads [raw] as a layout pack of whatever schema version it declares.
     *
     * Never throws. A pack file is untrusted input that arrives from a document picker or from a
     * previous build, and the caller's only sane response to any failure is a safe fallback, which
     * needs a value rather than an exception.
     */
    fun decode(raw: String): LayoutPackReadOutcome {
        val root = runCatching { legacyJson.parseToJsonElement(raw) }.getOrElse { error ->
            return malformed("not valid JSON: ${error.messageOrType()}")
        }
        if (root !is JsonObject) {
            return malformed("a layout pack must be a JSON object, found ${root.describeType()}")
        }

        val versionElement = root["schemaVersion"]
        val declaredVersion = when {
            versionElement == null || versionElement is JsonNull -> null
            versionElement is JsonPrimitive -> versionElement.intOrNull
                ?: return malformed("schemaVersion must be an integer, found ${versionElement.content}")
            else -> return malformed("schemaVersion must be an integer, found ${versionElement.describeType()}")
        }

        return when {
            declaredVersion == null -> decodeLegacy(raw, LayoutPackSchema.LEGACY_VERSION)
            declaredVersion <= 0 -> malformed("schemaVersion must be positive, found $declaredVersion")
            declaredVersion == LayoutPackSchema.LEGACY_VERSION ->
                decodeLegacy(raw, LayoutPackSchema.LEGACY_VERSION)
            declaredVersion > LayoutPackSchema.CURRENT_VERSION -> LayoutPackReadOutcome(
                document = null,
                sourceVersion = declaredVersion,
                problems = listOf(
                    LayoutPackProblem(
                        path = "schemaVersion",
                        kind = LayoutPackProblemKind.UNSUPPORTED_SCHEMA_VERSION,
                        detail = "this pack was written for schema $declaredVersion; this build " +
                            "reads up to ${LayoutPackSchema.CURRENT_VERSION}. Reading it would " +
                            "silently drop whatever the newer schema added.",
                    )
                ),
            )
            else -> decodeCurrent(root, raw, declaredVersion)
        }
    }

    /** Reads [raw], then migrates it to the current schema. */
    private fun decodeLegacy(raw: String, sourceVersion: Int): LayoutPackReadOutcome {
        val legacy = runCatching { legacyJson.decodeFromString(LayoutPack.serializer(), raw) }
            .getOrElse { error ->
                return malformed(
                    "not a readable schema $sourceVersion pack: ${error.messageOrType()}",
                    sourceVersion,
                )
            }
        val migration = LayoutPackCompatibility.migrate(legacy)
        return LayoutPackReadOutcome(
            document = migration.document,
            sourceVersion = sourceVersion,
            problems = LayoutPackValidation.validate(migration.document),
            diagnostics = migration.diagnostics,
            roleSources = migration.roleSources,
        )
    }

    /** Reads a pack already written at the current schema version. */
    private fun decodeCurrent(
        root: JsonObject,
        raw: String,
        sourceVersion: Int,
    ): LayoutPackReadOutcome {
        // The structural pass runs before deserialization for two reasons. It reports *every*
        // unknown field and bad enum value with a path, where a strict deserializer aborts on the
        // first one; and having checked the closed vocabularies here, the deserializer below can no
        // longer fail on them, so a thrown exception genuinely means a malformed value.
        val structural = StructuralPass().apply { document(root) }.problems
        if (structural.isNotEmpty()) {
            return LayoutPackReadOutcome(
                document = null,
                sourceVersion = sourceVersion,
                problems = structural,
            )
        }
        val document = runCatching { strictJson.decodeFromString(LayoutPackDocument.serializer(), raw) }
            .getOrElse { error ->
                return malformed("not a readable schema $sourceVersion pack: ${error.messageOrType()}", sourceVersion)
            }
        return LayoutPackReadOutcome(
            document = document,
            sourceVersion = sourceVersion,
            problems = LayoutPackValidation.validate(document),
        )
    }

    private fun malformed(
        detail: String,
        sourceVersion: Int = LayoutPackSchema.CURRENT_VERSION,
    ): LayoutPackReadOutcome = LayoutPackReadOutcome(
        document = null,
        sourceVersion = sourceVersion,
        problems = listOf(
            LayoutPackProblem(
                path = "<document>",
                kind = LayoutPackProblemKind.UNKNOWN_FIELD,
                detail = detail,
            )
        ),
    )

    /**
     * Walks a version 2 document reporting unknown fields and out-of-vocabulary enum values.
     *
     * Unknown fields are reported rather than ignored. See the class comment: a pack is persisted
     * user data, and quietly discarding part of it is how a conditional row becomes unconditional.
     */
    private class StructuralPass {
        val problems = mutableListOf<LayoutPackProblem>()

        fun document(root: JsonObject) {
            unknownFields("", root, LayoutPackSchema.DOCUMENT_FIELDS)
            val rows = root["rows"] ?: return
            if (rows !is JsonArray) {
                malformedField("rows", "must be an array, found ${rows.describeType()}")
                return
            }
            rows.forEachIndexed { index, element ->
                val path = "rows[$index]"
                if (element !is JsonObject) {
                    malformedField(path, "a row must be an object, found ${element.describeType()}")
                } else {
                    row(path, element)
                }
            }
        }

        private fun row(path: String, row: JsonObject) {
            unknownFields(path, row, LayoutPackSchema.ROW_FIELDS)
            vocabulary("$path.role", row["role"], ROLE_NAMES, required = true)
            vocabulary("$path.visibleWhen", row["visibleWhen"], VISIBILITY_NAMES, required = false)

            row["policy"]?.let { policy ->
                if (policy is JsonObject) {
                    unknownFields("$path.policy", policy, LayoutPackSchema.POLICY_FIELDS)
                } else if (policy !is JsonNull) {
                    malformedField("$path.policy", "must be an object, found ${policy.describeType()}")
                }
            }

            val keys = row["keys"] ?: return
            if (keys !is JsonArray) {
                malformedField("$path.keys", "must be an array, found ${keys.describeType()}")
                return
            }
            keys.forEachIndexed { index, element ->
                val keyPath = "$path.keys[$index]"
                if (element !is JsonObject) {
                    malformedField(keyPath, "a key must be an object, found ${element.describeType()}")
                } else {
                    unknownFields(keyPath, element, LayoutPackSchema.ELEMENT_FIELDS)
                    vocabulary("$keyPath.kind", element["kind"], KIND_NAMES, required = false)
                    vocabulary("$keyPath.style", element["style"], STYLE_NAMES, required = false)
                }
            }
        }

        private fun unknownFields(path: String, obj: JsonObject, allowed: Set<String>) {
            for (name in obj.keys) {
                if (name in allowed) continue
                problems += LayoutPackProblem(
                    path = if (path.isEmpty()) name else "$path.$name",
                    kind = LayoutPackProblemKind.UNKNOWN_FIELD,
                    detail = "schema ${LayoutPackSchema.CURRENT_VERSION} defines no '$name' here; " +
                        "ignoring it could change what this pack does",
                )
            }
        }

        private fun vocabulary(
            path: String,
            element: JsonElement?,
            allowed: Set<String>,
            required: Boolean,
        ) {
            if (element == null || element is JsonNull) {
                if (required) {
                    problems += LayoutPackProblem(
                        path = path,
                        kind = LayoutPackProblemKind.UNKNOWN_VOCABULARY,
                        detail = "required; one of ${allowed.sorted()}",
                    )
                }
                return
            }
            val value = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (value == null || value !in allowed) {
                problems += LayoutPackProblem(
                    path = path,
                    kind = LayoutPackProblemKind.UNKNOWN_VOCABULARY,
                    detail = "'${(element as? JsonPrimitive)?.content ?: element.describeType()}' " +
                        "is not one of ${allowed.sorted()}",
                )
            }
        }

        private fun malformedField(path: String, detail: String) {
            problems += LayoutPackProblem(
                path = path,
                kind = LayoutPackProblemKind.UNKNOWN_FIELD,
                detail = detail,
            )
        }

        private companion object {
            val ROLE_NAMES: Set<String> = SemanticRowRole.entries.map { it.serialName }.toSet()
            val VISIBILITY_NAMES: Set<String> =
                LayoutPackVisibility.entries.map { it.serialName() }.toSet()
            val KIND_NAMES: Set<String> = setOf("key", "spacer")
            val STYLE_NAMES: Set<String> =
                setOf("default", "aux", "special_left", "special_right", "space")
        }
    }
}

private fun Throwable.messageOrType(): String = message ?: this::class.simpleName ?: "unknown error"

private fun JsonElement.describeType(): String = when (this) {
    is JsonNull -> "null"
    is JsonObject -> "an object"
    is JsonArray -> "an array"
    is JsonPrimitive -> if (isString) "a string" else "a number or boolean"
}
