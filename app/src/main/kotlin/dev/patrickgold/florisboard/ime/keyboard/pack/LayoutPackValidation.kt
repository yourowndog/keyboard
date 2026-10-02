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

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole

/**
 * Stage 06 layout-pack validation model.
 *
 * Validation is a pure function of a decoded [LayoutPackDocument]: no `Context`, no preferences, no
 * assets. That is what lets the required test matrix — duplicate ids, invalid roles and units,
 * unsatisfiable rows, unsupported visibility — run as ordinary unit tests.
 *
 * Every finding is a [LayoutPackProblem] carrying a machine-readable [LayoutPackProblemKind] and the
 * JSON-ish path it was found at. The kind is what callers branch on; the path is what a user needs in
 * order to fix their pack. Nothing here produces a bare string, because a bare string cannot be
 * tested for anything except its spelling.
 */

/** What is wrong with a pack, as a closed vocabulary. */
enum class LayoutPackProblemKind {
    /** The document declares a schema this build cannot read. */
    UNSUPPORTED_SCHEMA_VERSION,

    /** A field carries a value outside its closed vocabulary — an unknown role, kind or condition. */
    UNKNOWN_VOCABULARY,

    /**
     * A field this schema version does not define.
     *
     * Treated as behaviour-bearing. A pack is user-authored data: an unrecognised field is either a
     * typo, in which case the author wants to know, or a capability from a newer schema, in which
     * case honouring the rest of the pack and dropping that one field is exactly the silent
     * truncation the stage forbids.
     */
    UNKNOWN_FIELD,

    /** A pack, row or key id that is blank. */
    BLANK_ID,

    /**
     * A pack id that cannot safely be used as an identifier.
     *
     * The pack id is both a filename and a preference value, so `../../theme.json` as an id is a
     * path-traversal write rather than a cosmetic problem. Constrained at the schema boundary, where
     * the pack is still untrusted input.
     */
    UNSAFE_ID,

    /** Two rows, or two keys in one row, share an id. */
    DUPLICATE_ID,

    /** A width is NaN or infinite. */
    NON_FINITE_UNITS,

    /** A width is zero, negative, or beyond [LayoutPackSchema.MAX_UNITS]. */
    UNITS_OUT_OF_RANGE,

    /** The row's elements do not add up to the width the row declares. */
    UNSATISFIABLE_ROW,

    /** An enabled row with no keys at all. */
    EMPTY_ROW,

    /** A spacer that also claims to do something, or a key that does nothing. */
    MALFORMED_ELEMENT,

    /** A row declares a role this schema does not let packs claim. */
    DISALLOWED_ROLE,

    /** An optional policy override is outside its accepted range. */
    POLICY_OUT_OF_RANGE,
}

/**
 * One validation finding.
 *
 * @param path where the problem is, e.g. `rows[2].keys[5].widthUnits`.
 * @param kind what class of problem it is.
 * @param detail a human-readable explanation. Shown to the user; never parsed.
 */
data class LayoutPackProblem(
    val path: String,
    val kind: LayoutPackProblemKind,
    val detail: String,
) {
    /** The form the Layout Builder shows. */
    fun describe(): String = "$path: $detail"
}

/**
 * A non-fatal observation made while reading a pack.
 *
 * Diagnostics are how the compatibility path stays honest. Migrating a schema version 1 pack has to
 * make decisions the pack never stated — what a row means, what an inert `showIfSetting` becomes —
 * and each decision is recorded here rather than being applied invisibly.
 */
data class LayoutPackDiagnostic(
    val path: String,
    val kind: LayoutPackDiagnosticKind,
    val detail: String,
)

/** Why a diagnostic was raised. */
enum class LayoutPackDiagnosticKind {
    /** A schema version 1 row id was resolved through the explicit legacy mapping. */
    LEGACY_ROLE_MAPPED,

    /** A schema version 1 row said nothing recognisable and took the documented fallback role. */
    LEGACY_ROLE_AMBIGUOUS,

    /** A schema version 1 field was dropped because version 2 has no equivalent for its value. */
    LEGACY_FIELD_DROPPED,

    /** A schema version 1 value was rewritten to the version 2 equivalent. */
    LEGACY_VALUE_REWRITTEN,
}

/** Validates decoded pack documents. */
object LayoutPackValidation {

    /**
     * Width comparison tolerance.
     *
     * Widths are `Float` because that is what `TextKey.authoredWidthUnits` is, and a pack authored
     * with `1.1` units three times should satisfy a `3.3`-unit row. The tolerance is scaled by the
     * expected width so it stays meaningful for wide rows.
     */
    private const val UNIT_TOLERANCE: Float = 1e-3f

    /** Longest acceptable pack id. Long enough for a reverse-domain name, short enough for a filename. */
    const val MAX_PACK_ID_LENGTH: Int = 64

    private val SAFE_PACK_ID = Regex("""[A-Za-z0-9._\-]{1,$MAX_PACK_ID_LENGTH}""")

    /**
     * Whether [id] may be used as a pack identity.
     *
     * Also the filename rule, which is why `.` is allowed but `/`, `\` and `..`-only ids are not: a
     * pack arriving from a document picker must not be able to choose where it is written.
     */
    fun isSafePackId(id: String): Boolean =
        SAFE_PACK_ID.matches(id) && id.trim('.').isNotEmpty()

    /**
     * Every problem with [document], in document order.
     *
     * An empty list means the pack is safe to activate. This never throws: a pack is untrusted
     * input, and a validator that can crash on bad input is not a validator.
     */
    fun validate(document: LayoutPackDocument): List<LayoutPackProblem> {
        val problems = mutableListOf<LayoutPackProblem>()

        if (document.schemaVersion > LayoutPackSchema.CURRENT_VERSION) {
            problems += LayoutPackProblem(
                path = "schemaVersion",
                kind = LayoutPackProblemKind.UNSUPPORTED_SCHEMA_VERSION,
                detail = "pack declares schema ${document.schemaVersion}, " +
                    "this build reads up to ${LayoutPackSchema.CURRENT_VERSION}",
            )
        }
        if (document.id.isBlank()) {
            problems += LayoutPackProblem(
                path = "id",
                kind = LayoutPackProblemKind.BLANK_ID,
                detail = "a pack needs a stable, non-blank id",
            )
        } else if (!isSafePackId(document.id)) {
            problems += LayoutPackProblem(
                path = "id",
                kind = LayoutPackProblemKind.UNSAFE_ID,
                detail = "a pack id must be 1..$MAX_PACK_ID_LENGTH characters of letters, digits, " +
                    "'.', '_' or '-'; it is used as a filename and as a preference value",
            )
        }
        problems += validateUnits("units", document.units)

        val seenRowIds = mutableSetOf<String>()
        for ((rowIndex, row) in document.rows.withIndex()) {
            val rowPath = "rows[$rowIndex]"
            problems += validateRow(rowPath, row, document.units, seenRowIds)
        }
        return problems
    }

    private fun validateRow(
        rowPath: String,
        row: LayoutPackRow,
        packUnits: Float,
        seenRowIds: MutableSet<String>,
    ): List<LayoutPackProblem> {
        val problems = mutableListOf<LayoutPackProblem>()

        if (row.id.isBlank()) {
            problems += LayoutPackProblem(
                path = "$rowPath.id",
                kind = LayoutPackProblemKind.BLANK_ID,
                detail = "a row needs a stable, non-blank id",
            )
        } else if (!seenRowIds.add(row.id)) {
            problems += LayoutPackProblem(
                path = "$rowPath.id",
                kind = LayoutPackProblemKind.DUPLICATE_ID,
                detail = "row id '${row.id}' is already used by an earlier row",
            )
        }

        if (row.role !in LayoutPackSchema.ALLOWED_ROLES) {
            problems += LayoutPackProblem(
                path = "$rowPath.role",
                kind = LayoutPackProblemKind.DISALLOWED_ROLE,
                detail = "a pack may not declare the '${row.role.serialName}' role",
            )
        }

        val rowUnits = row.widthUnits(packUnits)
        problems += validateUnits("$rowPath.units", rowUnits)

        row.policy?.let { problems += validatePolicy("$rowPath.policy", it) }

        // A disabled row is dropped before composition, so its contents never reach geometry. It
        // still has to be well-formed: the user can re-enable it, and a pack that only validates in
        // one visibility state is a pack that breaks when a switch is flipped.
        if (row.keys.isEmpty()) {
            problems += LayoutPackProblem(
                path = "$rowPath.keys",
                kind = LayoutPackProblemKind.EMPTY_ROW,
                detail = "a row must declare at least one key or spacer",
            )
            return problems
        }

        val seenKeyIds = mutableSetOf<String>()
        for ((keyIndex, element) in row.keys.withIndex()) {
            val keyPath = "$rowPath.keys[$keyIndex]"
            problems += validateUnits("$keyPath.widthUnits", element.widthUnits)

            val id = element.id
            if (id != null) {
                if (id.isBlank()) {
                    problems += LayoutPackProblem(
                        path = "$keyPath.id",
                        kind = LayoutPackProblemKind.BLANK_ID,
                        detail = "a declared key id must not be blank",
                    )
                } else if (!seenKeyIds.add(id)) {
                    problems += LayoutPackProblem(
                        path = "$keyPath.id",
                        kind = LayoutPackProblemKind.DUPLICATE_ID,
                        detail = "key id '$id' is already used in this row",
                    )
                }
            }

            when (element.kind) {
                LayoutPackElementKind.SPACER -> if (element.code.isNotBlank()) {
                    problems += LayoutPackProblem(
                        path = "$keyPath.code",
                        kind = LayoutPackProblemKind.MALFORMED_ELEMENT,
                        detail = "a spacer occupies width and does nothing, " +
                            "so it must not declare the code '${element.code}'",
                    )
                }
                LayoutPackElementKind.KEY -> if (element.code.isBlank()) {
                    problems += LayoutPackProblem(
                        path = "$keyPath.code",
                        kind = LayoutPackProblemKind.MALFORMED_ELEMENT,
                        detail = "a key must declare a code; use kind 'spacer' for structural filler",
                    )
                } else if (!LayoutPackKeyResolver.isResolvable(element.code)) {
                    // Schema version 1 turned an unresolvable code into a silent spacer, so an
                    // authoring typo became an invisible hole with only a log line behind it. Here
                    // the pack is refused and the author is told which key is wrong.
                    problems += LayoutPackProblem(
                        path = "$keyPath.code",
                        kind = LayoutPackProblemKind.MALFORMED_ELEMENT,
                        detail = "'${element.code}' is not a character, an internal key name, " +
                            "a known alias, or a key code",
                    )
                }
            }
        }

        // Satisfiability: the elements have to add up to the width the row claims. A row that says
        // 12 and supplies 11 does not fail loudly today — it quietly renders narrow — which is why
        // the Layout Builder has always refused to apply one.
        val declared = row.declaredElementUnits
        if (declared.isFinite() && rowUnits.isFinite() &&
            !nearlyEqual(declared, rowUnits)
        ) {
            problems += LayoutPackProblem(
                path = "$rowPath.keys",
                kind = LayoutPackProblemKind.UNSATISFIABLE_ROW,
                detail = "keys declare $declared units but the row is $rowUnits wide",
            )
        }
        return problems
    }

    private fun validatePolicy(path: String, policy: LayoutPackRowPolicy): List<LayoutPackProblem> {
        val problems = mutableListOf<LayoutPackProblem>()
        policy.heightFactorPercent?.let { value ->
            if (value !in LayoutPackRowPolicy.HEIGHT_FACTOR_RANGE) {
                problems += LayoutPackProblem(
                    path = "$path.heightFactorPercent",
                    kind = LayoutPackProblemKind.POLICY_OUT_OF_RANGE,
                    detail = "$value% is outside ${LayoutPackRowPolicy.HEIGHT_FACTOR_RANGE}",
                )
            }
        }
        policy.insetHorizontalPercent?.let { value ->
            if (value !in LayoutPackRowPolicy.INSET_RANGE) {
                problems += LayoutPackProblem(
                    path = "$path.insetHorizontalPercent",
                    kind = LayoutPackProblemKind.POLICY_OUT_OF_RANGE,
                    detail = "$value% is outside ${LayoutPackRowPolicy.INSET_RANGE}",
                )
            }
        }
        return problems
    }

    private fun validateUnits(path: String, units: Float): List<LayoutPackProblem> = when {
        !units.isFinite() -> listOf(
            LayoutPackProblem(
                path = path,
                kind = LayoutPackProblemKind.NON_FINITE_UNITS,
                detail = "width must be a finite number, got $units",
            )
        )
        units <= 0f || units > LayoutPackSchema.MAX_UNITS -> listOf(
            LayoutPackProblem(
                path = path,
                kind = LayoutPackProblemKind.UNITS_OUT_OF_RANGE,
                detail = "width must be greater than 0 and at most ${LayoutPackSchema.MAX_UNITS}, got $units",
            )
        )
        else -> emptyList()
    }

    private fun nearlyEqual(a: Float, b: Float): Boolean {
        val scale = maxOf(1f, kotlin.math.abs(a), kotlin.math.abs(b))
        return kotlin.math.abs(a - b) <= UNIT_TOLERANCE * scale
    }
}

/**
 * The role's serialized name.
 *
 * Read from the role's own stable-id prefix rather than derived a second way, so a pack's wire
 * vocabulary and a row's stable id cannot disagree about what a role is called.
 */
internal val SemanticRowRole.serialName: String
    get() = idPrefix
