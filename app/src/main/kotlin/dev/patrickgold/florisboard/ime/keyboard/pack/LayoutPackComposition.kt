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

import dev.patrickgold.florisboard.ime.keyboard.PackRoleSource
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryItem
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryItemKind
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryRow

/**
 * Turns a decoded pack into the rows composition and geometry consume.
 *
 * This is the only place that answers "which rows does this pack contribute, and what are they?".
 * `LayoutManager` calls it and then does nothing but build `TextKey`s from the result, which is what
 * makes the required restart test meaningful: the test exercises the same resolution the runtime
 * does, rather than a reimplementation of it.
 */

/**
 * The pack currently in effect, with the provenance of its roles.
 *
 * [roleSources] is carried beside the document rather than inside it on purpose. "This role was
 * inferred by the version 1 compatibility path" is a fact about *this read*, not about the pack: once
 * a migrated pack is written back at the current schema, its roles really are declared, and a
 * persisted `roleSource` would then be a lie that outlived its reason.
 */
data class ActiveLayoutPack(
    val document: LayoutPackDocument,
    val roleSources: Map<String, PackRoleSource> = emptyMap(),
) {
    /**
     * How the role of the row with id [rowId] was decided.
     *
     * Defaults to [PackRoleSource.DECLARED_ROLE] because a document that reached this point without
     * a recorded inference came from a schema version 2 pack, where the role is a declared field.
     */
    fun roleSourceOf(rowId: String): PackRoleSource =
        roleSources[rowId] ?: PackRoleSource.DECLARED_ROLE

    companion object {
        /** No pack selected. Composition falls through to the bundled extension layouts. */
        val None = ActiveLayoutPack(LayoutPackDocument.None)
    }
}

/**
 * The preference state the closed set of [LayoutPackVisibility] conditions is evaluated against.
 *
 * Passed in rather than read here, so resolution stays a pure function and the required tests can
 * sweep every combination without a preference store.
 */
data class LayoutPackConditions(
    val modRowsVisible: Boolean = true,
    val numberRowEnabled: Boolean = false,
    val devRowEnabled: Boolean = false,
) {
    /** Whether [condition] currently holds. Exhaustive: a new condition must be decided, not defaulted. */
    fun satisfies(condition: LayoutPackVisibility): Boolean = when (condition) {
        LayoutPackVisibility.ALWAYS -> true
        LayoutPackVisibility.MOD_ROWS_VISIBLE -> modRowsVisible
        LayoutPackVisibility.NUMBER_ROW_ENABLED -> numberRowEnabled
        LayoutPackVisibility.DEV_ROW_ENABLED -> devRowEnabled
    }
}

/**
 * One pack row that survived enablement and visibility, ready to be laid out.
 *
 * @param sourceIndex the row's index in the pack, which provenance records so a diagnostic can point
 *   back at the file even after conditional rows have been dropped.
 */
data class ResolvedPackRow(
    val sourceIndex: Int,
    val rowId: String,
    val role: SemanticRowRole,
    val roleSource: PackRoleSource,
    val widthUnits: Float,
    val elements: List<LayoutPackElement>,
)

object LayoutPackComposition {

    /**
     * The rows [pack] contributes under [conditions], in arrangement order.
     *
     * A row is dropped when it is disabled, when its condition does not hold, or when it declares no
     * keys. Dropping is the only thing conditions may do: nothing here can reorder rows, change what
     * a row is, or invent one.
     */
    fun resolve(
        pack: ActiveLayoutPack,
        conditions: LayoutPackConditions = LayoutPackConditions(),
    ): List<ResolvedPackRow> {
        val document = pack.document
        return document.rows.mapIndexedNotNull { index, row ->
            when {
                !row.enabled -> null
                !conditions.satisfies(row.visibleWhen) -> null
                row.keys.isEmpty() -> null
                else -> ResolvedPackRow(
                    sourceIndex = index,
                    rowId = row.id,
                    role = row.role,
                    roleSource = pack.roleSourceOf(row.id),
                    widthUnits = row.widthUnits(document.units),
                    elements = row.keys,
                )
            }
        }
    }

    /**
     * The resolved rows as solver input.
     *
     * The solver is the single geometry authority, so expressing a pack as [GeometryRow]s is how a
     * test can ask "does this pack still lay out identically?" without a device. Item ids are
     * row-scoped and positional *within* the row, which is what the solver needs them to be —
     * [ResolvedPackRow.rowId] carries the durable identity.
     */
    fun geometryRows(rows: List<ResolvedPackRow>): List<GeometryRow> = rows.map { row ->
        GeometryRow(
            stableId = row.rowId,
            role = row.role,
            items = row.elements.mapIndexed { index, element ->
                GeometryItem(
                    stableId = "${row.rowId}#$index",
                    widthUnits = element.widthUnits.toDouble(),
                    kind = if (element.isSpacer) GeometryItemKind.SPACER else GeometryItemKind.KEY,
                )
            },
        )
    }
}
