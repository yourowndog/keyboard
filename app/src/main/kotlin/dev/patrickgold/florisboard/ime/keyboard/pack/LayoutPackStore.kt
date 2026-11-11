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

import java.io.File

/**
 * Stage 06 layout-pack persistence.
 *
 * Deliberately a plain `java.io.File` store with no `Context`: the required tests — missing pack,
 * corrupt pack, unsupported-newer pack, repeated migration, preservation of the legacy user file —
 * are all about what happens on disk, and they run as ordinary unit tests against a temporary
 * directory. `LayoutPackRepository` is the thin Android adapter that supplies the directories and
 * the bundled asset.
 *
 * Layout on disk:
 *
 * ```
 * files/layouts/user/lcars_hacker_en_us.user.json   the schema version 1 file, never written again
 * files/layouts/user/packs/<packId>.json            schema version 2 packs
 * files/layouts/user/packs/<packId>.json.bak        the previous contents of the above
 * ```
 *
 * @param packsDir where current-schema packs live.
 * @param legacyUserFile the single fixed filename the Layout Builder has always written. Read, never
 *   written, never deleted.
 * @param readBundledDefault the bundled pack's raw JSON, or null when it cannot be read.
 */
class LayoutPackStore(
    private val packsDir: File,
    private val legacyUserFile: File,
    private val readBundledDefault: () -> String?,
) {

    /** Why a selected pack could not be restored. */
    enum class FallbackReason {
        /** No file exists for the selected id — it was deleted, or never written. */
        MISSING,

        /** The file exists but is not a readable pack. */
        UNREADABLE,

        /** The file is a readable pack that validation refuses. */
        INVALID,

        /** The file declares a schema newer than this build understands. */
        UNSUPPORTED_NEWER,
    }

    /** The outcome of asking for the pack that should be active. */
    sealed interface Restoration {
        /** Nothing is selected. The bundled extension layouts compose the keyboard, as always. */
        data object NoSelection : Restoration

        /** The selected pack was restored. */
        data class Restored(
            val pack: ActiveLayoutPack,
            val outcome: LayoutPackReadOutcome,
        ) : Restoration

        /**
         * The selection could not be honoured, so nothing is activated.
         *
         * The fallback is deliberately [ActiveLayoutPack.None] rather than the bundled pack. A
         * corrupt file must cost the user their *customisation*, not their keyboard: falling back to
         * the bundled LCARS arrangement would silently hand them a layout they never chose, which is
         * a worse failure than returning to the composition they had before they ever saved a pack.
         */
        data class FellBack(
            val reason: FallbackReason,
            val problems: List<LayoutPackProblem>,
        ) : Restoration
    }

    /** The outcome of writing a pack. */
    sealed interface SaveResult {
        data class Written(val file: File) : SaveResult

        /** Nothing was written and the previous contents are untouched. */
        data class Rejected(val problems: List<LayoutPackProblem>) : SaveResult

        /** The write itself failed. The previous contents are untouched. */
        data class Failed(val message: String) : SaveResult
    }

    /** The file a pack with [packId] is stored in. */
    fun fileFor(packId: String): File = File(packsDir, "$packId.json")

    /** Whether a current-schema file exists for [packId]. */
    fun exists(packId: String): Boolean = fileFor(packId).isFile

    /**
     * Reads the pack stored under [packId].
     *
     * Returns null only when no file exists; an existing but unreadable file comes back as an
     * outcome with problems, because "corrupt" and "absent" need different user-facing answers.
     */
    fun read(packId: String): LayoutPackReadOutcome? {
        val file = fileFor(packId)
        if (!file.isFile) return null
        val raw = runCatching { file.readText() }.getOrElse { error ->
            return LayoutPackReadOutcome(
                document = null,
                sourceVersion = LayoutPackSchema.CURRENT_VERSION,
                problems = listOf(
                    LayoutPackProblem(
                        path = file.name,
                        kind = LayoutPackProblemKind.UNKNOWN_FIELD,
                        detail = "could not be read: ${error.message ?: error::class.simpleName}",
                    )
                ),
            )
        }
        return LayoutPackCodec.decode(raw)
    }

    /**
     * The pack that should be active for [selectedPackId].
     *
     * A blank or absent id means nothing is selected, which is the state every install is in until
     * the user applies a pack. Every other failure mode produces a [Restoration.FellBack] naming the
     * reason, so startup can log something specific instead of "layout pack unavailable".
     */
    fun restore(selectedPackId: String?): Restoration {
        if (selectedPackId.isNullOrBlank()) {
            return Restoration.NoSelection
        }
        if (!LayoutPackValidation.isSafePackId(selectedPackId)) {
            return Restoration.FellBack(
                reason = FallbackReason.MISSING,
                problems = listOf(
                    LayoutPackProblem(
                        path = "selectedPackId",
                        kind = LayoutPackProblemKind.UNSAFE_ID,
                        detail = "'$selectedPackId' is not a usable pack id",
                    )
                ),
            )
        }
        val outcome = read(selectedPackId)
            ?: return Restoration.FellBack(
                reason = FallbackReason.MISSING,
                problems = listOf(
                    LayoutPackProblem(
                        path = fileFor(selectedPackId).name,
                        kind = LayoutPackProblemKind.UNKNOWN_FIELD,
                        detail = "no layout pack is stored under the id '$selectedPackId'",
                    )
                ),
            )
        val pack = outcome.asActivePack()
        if (pack != null) {
            return Restoration.Restored(pack, outcome)
        }
        val reason = when {
            outcome.problems.any { it.kind == LayoutPackProblemKind.UNSUPPORTED_SCHEMA_VERSION } ->
                FallbackReason.UNSUPPORTED_NEWER
            outcome.document == null -> FallbackReason.UNREADABLE
            else -> FallbackReason.INVALID
        }
        return Restoration.FellBack(reason, outcome.problems)
    }

    /**
     * Writes [document] at the current schema version.
     *
     * The write is staged: the serialized text goes to a temporary file, is decoded back and compared
     * against [document], and only then replaces the live file — with the live file's previous
     * contents kept as `<packId>.json.bak`. An interrupted or corrupt write therefore cannot destroy
     * a pack the user already had, and a round trip that loses information fails before it is
     * visible rather than after.
     */
    fun save(document: LayoutPackDocument): SaveResult {
        val problems = LayoutPackValidation.validate(document)
        if (problems.isNotEmpty()) {
            return SaveResult.Rejected(problems)
        }
        val normalized = document.copy(schemaVersion = LayoutPackSchema.CURRENT_VERSION)
        val serialized = LayoutPackCodec.encode(normalized)

        val verification = LayoutPackCodec.decode(serialized)
        if (verification.document != normalized) {
            return SaveResult.Failed(
                "the encoded pack did not read back identically, so it was not written " +
                    "(${verification.describe()})",
            )
        }

        val target = fileFor(normalized.id)
        val temp = File(packsDir, "${normalized.id}.json.tmp")
        return runCatching {
            packsDir.mkdirs()
            temp.writeText(serialized)
            val backup = File(packsDir, "${target.name}.bak")
            if (target.isFile) {
                backup.delete()
                target.copyTo(backup, overwrite = true)
                check(target.delete()) { "could not replace ${target.name}" }
            }
            runCatching {
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                }
            }.getOrElse { error ->
                target.delete()
                if (backup.isFile) {
                    backup.copyTo(target, overwrite = true)
                }
                throw error
            }
            if (temp.isFile) {
                temp.delete()
            }
            SaveResult.Written(target) as SaveResult
        }.getOrElse { error ->
            temp.delete()
            SaveResult.Failed(error.message ?: error::class.simpleName ?: "unknown write failure")
        }
    }

    /** What an import of the legacy fixed user file did. */
    sealed interface LegacyImport {
        /** There is no legacy file to import. */
        data object NothingToImport : LegacyImport

        /** A current-schema pack already exists for the legacy pack's id, so nothing was written. */
        data class AlreadyImported(val packId: String) : LegacyImport

        /** The legacy file was migrated and written at the current schema. */
        data class Imported(
            val packId: String,
            val diagnostics: List<LayoutPackDiagnostic>,
        ) : LegacyImport

        /** The legacy file could not be migrated. It is left exactly as it was. */
        data class Failed(val problems: List<LayoutPackProblem>) : LegacyImport
    }

    /**
     * Brings the legacy fixed user file forward to the current schema, once.
     *
     * Idempotent by construction rather than by a flag: the second call finds the migrated pack
     * already present and returns [LegacyImport.AlreadyImported] without reading or writing
     * anything. There is no version counter to get out of step with the files.
     *
     * The legacy file is never written and never deleted. It is the only copy of the user's layout
     * that predates this stage, and the cost of keeping it is a few kilobytes.
     */
    fun importLegacyUserFile(): LegacyImport {
        if (!legacyUserFile.isFile) {
            return LegacyImport.NothingToImport
        }
        val raw = runCatching { legacyUserFile.readText() }.getOrElse { error ->
            return LegacyImport.Failed(
                listOf(
                    LayoutPackProblem(
                        path = legacyUserFile.name,
                        kind = LayoutPackProblemKind.UNKNOWN_FIELD,
                        detail = "could not be read: ${error.message ?: error::class.simpleName}",
                    )
                )
            )
        }
        val outcome = LayoutPackCodec.decode(raw)
        val document = outcome.document
        if (document == null || outcome.problems.isNotEmpty()) {
            return LegacyImport.Failed(outcome.problems)
        }
        if (exists(document.id)) {
            return LegacyImport.AlreadyImported(document.id)
        }
        return when (val saved = save(document)) {
            is SaveResult.Written -> LegacyImport.Imported(document.id, outcome.diagnostics)
            is SaveResult.Rejected -> LegacyImport.Failed(saved.problems)
            is SaveResult.Failed -> LegacyImport.Failed(
                listOf(
                    LayoutPackProblem(
                        path = legacyUserFile.name,
                        kind = LayoutPackProblemKind.UNKNOWN_FIELD,
                        detail = "migrated pack could not be written: ${saved.message}",
                    )
                )
            )
        }
    }

    /**
     * The bundled pack, migrated to the current schema.
     *
     * Only the Layout Builder uses this, as a starting point for editing. Startup restoration
     * deliberately does not: see [Restoration.FellBack].
     */
    fun readBundled(): LayoutPackReadOutcome? =
        readBundledDefault()?.let { LayoutPackCodec.decode(it) }
}

/**
 * The pack this restoration outcome asks composition to use.
 *
 * Both failure outcomes resolve to [ActiveLayoutPack.None]; they differ in what the caller logs, not
 * in what the keyboard draws.
 */
fun LayoutPackStore.Restoration.activePack(): ActiveLayoutPack = when (this) {
    is LayoutPackStore.Restoration.Restored -> pack
    LayoutPackStore.Restoration.NoSelection,
    is LayoutPackStore.Restoration.FellBack -> ActiveLayoutPack.None
}
