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

import dev.patrickgold.florisboard.app.layoutbuilder.LayoutKey
import dev.patrickgold.florisboard.app.layoutbuilder.LayoutPack
import dev.patrickgold.florisboard.app.layoutbuilder.LayoutRow
import dev.patrickgold.florisboard.ime.keyboard.LayoutPackRowSemantics
import dev.patrickgold.florisboard.ime.keyboard.PackRoleSource

/**
 * Migrates a schema version 1 layout pack to the current schema.
 *
 * Version 1 is the unversioned shape in [LayoutPack]. It records rows, keys, widths, spacers and an
 * `enabled` flag, and it records *nothing* about what a row means. The migration therefore has to
 * make decisions the pack never stated, and the contract for those decisions is:
 *
 * - every inferred role is attributed through [PackRoleSource], so an inference can never be read
 *   back as pack-declared metadata;
 * - every inference and every rewritten or dropped value produces a [LayoutPackDiagnostic];
 * - the migration is a pure function, so running it twice on the same input gives the same output,
 *   and running it on an already-migrated document is a no-op.
 *
 * What it deliberately does *not* do is default every row to alpha, or synthesise two bottom
 * modifier rows to make a pack look like the bundled Coding composition. A row the pack did not
 * describe stays undescribed and says so.
 */
object LayoutPackCompatibility {


    /**
     * The outcome of migrating one pack.
     *
     * @param roleSources how each row's role was decided, by row id. Carried beside the document
     *   because it describes this migration, not the pack — see [ActiveLayoutPack.roleSources].
     */
    data class Result(
        val document: LayoutPackDocument,
        val diagnostics: List<LayoutPackDiagnostic>,
        val roleSources: Map<String, PackRoleSource> = emptyMap(),
    )

    /**
     * Migrates [legacy] to the current schema.
     *
     * Never fails. A version 1 pack that was already broken stays broken in a way
     * [LayoutPackValidation] can describe, rather than throwing here where there is no user-facing
     * place to put the message.
     */
    fun migrate(legacy: LayoutPack): Result {
        val diagnostics = mutableListOf<LayoutPackDiagnostic>()
        val roleSources = mutableMapOf<String, PackRoleSource>()
        val rows = legacy.rows.mapIndexed { index, row ->
            migrateRow("rows[$index]", row, diagnostics, roleSources)
        }
        val document = LayoutPackDocument(
            schemaVersion = LayoutPackSchema.CURRENT_VERSION,
            id = legacy.id,
            label = legacy.label,
            units = legacy.units.toFloat(),
            rows = rows,
        )
        return Result(document, diagnostics, roleSources)
    }

    private fun migrateRow(
        path: String,
        row: LayoutRow,
        diagnostics: MutableList<LayoutPackDiagnostic>,
        roleSources: MutableMap<String, PackRoleSource>,
    ): LayoutPackRow {
        val (role, roleSource) = LayoutPackRowSemantics.resolve(row.id)
        roleSources[row.id] = roleSource
        when (roleSource) {
            PackRoleSource.COMPATIBILITY_MAPPING -> diagnostics += LayoutPackDiagnostic(
                path = "$path.role",
                kind = LayoutPackDiagnosticKind.LEGACY_ROLE_MAPPED,
                detail = "row id '${row.id}' is a known historical id and was mapped to " +
                    "'${role.serialName}'",
            )
            PackRoleSource.COMPATIBILITY_FALLBACK -> diagnostics += LayoutPackDiagnostic(
                path = "$path.role",
                kind = LayoutPackDiagnosticKind.LEGACY_ROLE_AMBIGUOUS,
                detail = "row id '${row.id}' names no known role; took the documented fallback " +
                    "'${role.serialName}'. Declare a role to make this definite.",
            )
            // A row id that *is* a role name said what it meant, so there is nothing to report.
            PackRoleSource.DECLARED_ROW_ID, PackRoleSource.DECLARED_ROLE -> Unit
        }

        val visibility = migrateVisibility(path, row.showIfSetting, diagnostics)
        val keys = row.keys.map { migrateKey(it) }
        val authoredUnits = row.units.toFloat()
        val resolvedUnits = keys.sumOf { it.widthUnits.toDouble() }.toFloat()
        val rowUnits = if (authoredUnits.isFinite() && authoredUnits > 0f && authoredUnits == resolvedUnits) {
            authoredUnits
        } else {
            diagnostics += LayoutPackDiagnostic(
                path = "$path.units",
                kind = LayoutPackDiagnosticKind.LEGACY_VALUE_REWRITTEN,
                detail = "row declared $authoredUnits units but its elements occupy $resolvedUnits; " +
                    "the migrated row uses the element total, preserving the geometry version 1 rendered",
            )
            resolvedUnits
        }

        return LayoutPackRow(
            id = row.id,
            role = role,
            // Version 1 rendered the authored key widths directly and did not enforce this total.
            // Stating the actual element total makes the migrated row satisfiable without moving a
            // key, including the shipped bottom row whose 13 units were labelled as a 12-unit row.
            units = rowUnits,
            enabled = row.enabled,
            visibleWhen = visibility,
            policy = null,
            keys = keys,
        )
    }

    private fun migrateVisibility(
        path: String,
        showIfSetting: String?,
        diagnostics: MutableList<LayoutPackDiagnostic>,
    ): LayoutPackVisibility {
        val raw = showIfSetting?.trim()
        if (raw.isNullOrEmpty()) {
            return LayoutPackVisibility.ALWAYS
        }
        // Dropping it preserves behaviour exactly: version 1 never evaluated this field, so every
        // row carrying it was always shown. Promoting even a recognisable spelling to a real
        // condition would make an existing row disappear when the preference is false.
        diagnostics += LayoutPackDiagnostic(
            path = "$path.visibleWhen",
            kind = LayoutPackDiagnosticKind.LEGACY_FIELD_DROPPED,
            detail = "showIfSetting '$raw' was inert in schema 1; the row stays always-visible " +
                "to preserve its previous behaviour",
        )
        return LayoutPackVisibility.ALWAYS
    }

    private fun migrateKey(key: LayoutKey): LayoutPackElement {
        // Version 1 had two ways to say "spacer": the flag, and — accidentally — any key whose code
        // did not resolve. Both become an explicit spacer, so the migrated pack renders exactly as
        // the original did instead of newly failing validation on a hole the user already had.
        val isSpacer = key.spacer || !LayoutPackKeyResolver.isResolvable(key.code)
        return LayoutPackElement(
            kind = if (isSpacer) LayoutPackElementKind.SPACER else LayoutPackElementKind.KEY,
            id = key.id,
            code = if (isSpacer) "" else key.code,
            label = key.label,
            // `LayoutManager` has always coerced a non-positive authored width to one unit. The
            // coercion moves here so the migrated pack states the width it will actually be laid
            // out with.
            widthUnits = if (key.units > 0) key.units.toFloat() else 1f,
            style = key.style,
        )
    }
}

/** The condition's serialized name. */
internal fun LayoutPackVisibility.serialName(): String = when (this) {
    LayoutPackVisibility.ALWAYS -> "always"
    LayoutPackVisibility.MOD_ROWS_VISIBLE -> "mod_rows_visible"
    LayoutPackVisibility.NUMBER_ROW_ENABLED -> "number_row_enabled"
    LayoutPackVisibility.DEV_ROW_ENABLED -> "dev_row_enabled"
}
