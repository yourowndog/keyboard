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

package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The single structural geometry solver.
 *
 * Pure and deterministic: [solve] reads only its argument and always returns the same result for
 * the same input. It has no dependency on Compose, `Context`, preferences, or the keyboard object,
 * which is why it can be exercised exhaustively from unit tests.
 *
 * This is the production authority for placement. Both geometry consumers reach it through
 * [TextKeyboardGeometryBridge] — `FlorisImeSizing` for the frame height, `TextKeyboardLayout` for
 * the rows and keys inside that frame — so there is no second arithmetic to disagree with. The
 * legacy authorities it replaced (`Keyboard.layout()` and `KeyboardGeometryArithmetic`) are gone.
 *
 * See [SolvedGeometry] for the coordinate convention and rounding ownership, and
 * `omniboard-artifacts/implementation/keyboard-geometry/02-shared-solver.md` for the stage
 * contract.
 */
object KeyboardGeometrySolver {

    /**
     * Sub-pixel slack allowed before a row is judged too wide for the content area.
     *
     * Half a pixel is exactly the error edge-rounding can introduce, so this tolerates rounding
     * and nothing else. A row that genuinely does not fit is reported, not clipped.
     */
    private const val OVERFLOW_TOLERANCE = 0.5

    /** Solves [input], or explains why it cannot be solved. */
    fun solve(input: GeometrySolverInput): GeometrySolution {
        val plan = planVerticalElements(input)
        val reasons = validate(input, plan)
        if (reasons.isNotEmpty()) {
            return GeometrySolution.Unsatisfiable(reasons)
        }

        val heights = solveRowHeights(input, plan.totalGapHeight)
        if (heights is RowHeights.Unsatisfiable) {
            return GeometrySolution.Unsatisfiable(heights.reasons)
        }
        val rowHeights = (heights as RowHeights.Solved).heights
        val contentHeight = rowHeights.sum() + plan.totalGapHeight
        val frameHeight = contentHeight + input.insets.vertical

        val maxFrameHeight = (input.framePolicy as? FramePolicy.Intrinsic)?.maxFrameHeight
        if (maxFrameHeight != null && frameHeight > maxFrameHeight + OVERFLOW_TOLERANCE) {
            return GeometrySolution.Unsatisfiable(
                listOf("frame needs ${frameHeight}px but is capped at ${maxFrameHeight}px"),
            )
        }

        val contentLeft = input.insets.left
        val contentWidth = input.availableWidth - input.insets.horizontal
        val unitWidths = solveUnitWidths(input, contentWidth)

        // Vertical placement. The cursor is the exact running total; only edges are rounded, so
        // the final row's bottom edge rounds the exact content height rather than a sum of
        // independently rounded heights.
        var cursorY = input.insets.top
        val solvedRows = mutableListOf<SolvedRow>()
        val solvedGaps = mutableListOf<SolvedGap>()
        val overflowing = mutableListOf<String>()

        for (element in plan.elements) {
            val top = cursorY.roundToPx()
            when (element) {
                is VerticalElement.Gap -> {
                    cursorY += element.size
                    solvedGaps += SolvedGap(
                        kind = element.kind,
                        role = element.role,
                        rowAbove = element.rowAbove,
                        rowBelow = element.rowBelow,
                        bounds = GeometryRect(
                            left = contentLeft.roundToPx(),
                            top = top,
                            right = (contentLeft + contentWidth).roundToPx(),
                            bottom = cursorY.roundToPx(),
                        ),
                    )
                }
                is VerticalElement.Row -> {
                    val row = element.row
                    cursorY += rowHeights[element.index]
                    val bottom = cursorY.roundToPx()
                    val placement = placeItems(
                        input = input,
                        row = row,
                        unitWidth = unitWidths[element.index],
                        contentLeft = contentLeft,
                        contentWidth = contentWidth,
                        top = top,
                        bottom = bottom,
                    )
                    if (placement.overflowBy > OVERFLOW_TOLERANCE) {
                        overflowing += "row '${row.stableId}' needs " +
                            "${contentWidth + placement.overflowBy}px but the content area is ${contentWidth}px"
                    }
                    solvedRows += SolvedRow(
                        stableId = row.stableId,
                        role = row.role,
                        bounds = GeometryRect(
                            left = contentLeft.roundToPx(),
                            top = top,
                            right = (contentLeft + contentWidth).roundToPx(),
                            bottom = bottom,
                        ),
                        items = placement.items,
                        declaredSpacing = input.spacingFor(row.role),
                    )
                }
            }
        }

        if (overflowing.isNotEmpty()) {
            return GeometrySolution.Unsatisfiable(overflowing)
        }

        return GeometrySolution.Solved(
            SolvedGeometry(
                frame = GeometryRect(
                    left = 0,
                    top = 0,
                    right = input.availableWidth.roundToPx(),
                    bottom = frameHeight.roundToPx(),
                ),
                content = GeometryRect(
                    left = contentLeft.roundToPx(),
                    top = input.insets.top.roundToPx(),
                    right = (contentLeft + contentWidth).roundToPx(),
                    bottom = (input.insets.top + contentHeight).roundToPx(),
                ),
                rows = solvedRows,
                gaps = solvedGaps,
                insets = input.insets,
                orientation = input.orientation,
            ),
        )
    }

    // -- Vertical plan ---------------------------------------------------------------------

    private sealed interface VerticalElement {
        /** @param index the row's position in [GeometrySolverInput.rows], not its visual position. */
        data class Row(val index: Int, val row: GeometryRow) : VerticalElement

        data class Gap(
            val kind: GeometryGapKind,
            val role: SemanticRowRole,
            val rowAbove: String?,
            val rowBelow: String?,
            val size: Double,
        ) : VerticalElement
    }

    private class VerticalPlan(
        val elements: List<VerticalElement>,
        val totalGapHeight: Double,
    )

    /**
     * Interleaves rows with the gaps their role blocks declare.
     *
     * A block is a maximal run of adjacent rows sharing a role. Blocks are found from the roles
     * themselves; neither the number of rows nor a row's index takes part.
     */
    private fun planVerticalElements(input: GeometrySolverInput): VerticalPlan {
        val elements = mutableListOf<VerticalElement>()
        var totalGap = 0.0
        var index = 0
        while (index < input.rows.size) {
            val role = input.rows[index].role
            var end = index
            while (end + 1 < input.rows.size && input.rows[end + 1].role == role) end++
            val gaps = input.gapPolicy.gapsFor(role)

            if (gaps.above > 0.0) {
                elements += VerticalElement.Gap(
                    kind = GeometryGapKind.ABOVE_BLOCK,
                    role = role,
                    rowAbove = input.rows.getOrNull(index - 1)?.stableId,
                    rowBelow = input.rows[index].stableId,
                    size = gaps.above,
                )
                totalGap += gaps.above
            }
            for (i in index..end) {
                if (i > index && gaps.within > 0.0) {
                    elements += VerticalElement.Gap(
                        kind = GeometryGapKind.WITHIN_BLOCK,
                        role = role,
                        rowAbove = input.rows[i - 1].stableId,
                        rowBelow = input.rows[i].stableId,
                        size = gaps.within,
                    )
                    totalGap += gaps.within
                }
                elements += VerticalElement.Row(i, input.rows[i])
            }
            if (gaps.below > 0.0) {
                elements += VerticalElement.Gap(
                    kind = GeometryGapKind.BELOW_BLOCK,
                    role = role,
                    rowAbove = input.rows[end].stableId,
                    rowBelow = input.rows.getOrNull(end + 1)?.stableId,
                    size = gaps.below,
                )
                totalGap += gaps.below
            }
            index = end + 1
        }
        return VerticalPlan(elements, totalGap)
    }

    // -- Heights ---------------------------------------------------------------------------

    private sealed interface RowHeights {
        data class Solved(val heights: List<Double>) : RowHeights
        data class Unsatisfiable(val reasons: List<String>) : RowHeights
    }

    private fun solveRowHeights(input: GeometrySolverInput, totalGapHeight: Double): RowHeights {
        val units = input.rows.map {
            input.rowHeightPolicy.unitsFor(it.role) * input.overrides.rowHeightScale(it.role)
        }
        return when (val policy = input.framePolicy) {
            is FramePolicy.Intrinsic -> RowHeights.Solved(units.map { it * policy.rowBaseHeight })
            is FramePolicy.FitToHeight -> {
                val available = policy.frameHeight - input.insets.vertical - totalGapHeight
                if (available < 0.0) {
                    return RowHeights.Unsatisfiable(
                        listOf(
                            "frame height ${policy.frameHeight}px cannot hold ${totalGapHeight}px of gaps " +
                                "and ${input.insets.vertical}px of insets",
                        ),
                    )
                }
                if (input.rows.isEmpty()) return RowHeights.Solved(emptyList())
                val totalUnits = units.sum()
                if (totalUnits <= 0.0) {
                    return RowHeights.Unsatisfiable(
                        listOf("${input.rows.size} rows declare no height units between them"),
                    )
                }
                RowHeights.Solved(units.map { available * it / totalUnits })
            }
        }
    }

    // -- Widths ----------------------------------------------------------------------------

    /**
     * Resolves each row's unit width.
     *
     * Rows consuming the shared reference are measured against the widest row that *defines* it,
     * so a short row keeps the same grid instead of stretching to fill the width. Every other row
     * fits its own units to the full content width.
     */
    private fun solveUnitWidths(
        input: GeometrySolverInput,
        contentWidth: Double,
    ): List<Double> {
        val reference = input.widthPolicy.sharedReference
        val sharedUnitWidth = reference?.let { ref ->
            val maxUnits = input.rows
                .filter { it.role in ref.measuredRoles }
                .maxOfOrNull { it.totalWidthUnits }
                ?: 0.0
            if (maxUnits > 0.0) (contentWidth - ref.insetH) / maxUnits else null
        }
        return input.rows.map { row ->
            val useShared = sharedUnitWidth != null && reference != null && row.role in reference.consumerRoles
            when {
                useShared -> sharedUnitWidth!!
                row.totalWidthUnits > 0.0 -> contentWidth / row.totalWidthUnits
                else -> 0.0
            }
        }
    }

    private class RowPlacement(val items: List<SolvedItem>, val overflowBy: Double)

    private fun placeItems(
        input: GeometrySolverInput,
        row: GeometryRow,
        unitWidth: Double,
        contentLeft: Double,
        contentWidth: Double,
        top: Int,
        bottom: Int,
    ): RowPlacement {
        val scale = input.overrides.itemWidthScale(row.role)
        val fixedWidths = row.items.map { it.widthUnits * unitWidth * scale }
        val fixedTotal = fixedWidths.sum()

        // Flexible growth. Whatever width the fixed demand leaves over is shared among the items
        // that declare a grow weight, in proportion to it. Nothing here consults an item's kind,
        // code, or position: an item grows because it says it grows. A row without growers, or one
        // whose unit width already fits it to the content area, is placed exactly as before.
        val totalGrowWeight = row.items.sumOf { it.growWeight }
        val remainder = contentWidth - fixedTotal
        val widths = if (totalGrowWeight > 0.0 && remainder > 0.0) {
            row.items.mapIndexed { index, item ->
                fixedWidths[index] + remainder * (item.growWeight / totalGrowWeight)
            }
        } else {
            fixedWidths
        }
        val totalRowWidth = widths.sum()

        var cursorX = contentLeft + when (input.widthPolicy.alignment) {
            RowAlignment.START -> 0.0
            RowAlignment.CENTER -> (contentWidth - totalRowWidth) / 2.0
            RowAlignment.END -> contentWidth - totalRowWidth
        }

        val items = row.items.mapIndexed { index, item ->
            val left = cursorX.roundToPx()
            cursorX += widths[index]
            SolvedItem(
                stableId = item.stableId,
                kind = item.kind,
                bounds = GeometryRect(left = left, top = top, right = cursorX.roundToPx(), bottom = bottom),
                declaredHeightFactor = item.heightFactor,
                declaredVerticalAlignment = item.verticalAlignment,
            )
        }
        return RowPlacement(items, overflowBy = max(0.0, totalRowWidth - contentWidth))
    }

    // -- Validation ------------------------------------------------------------------------

    private fun validate(input: GeometrySolverInput, plan: VerticalPlan): List<String> {
        val reasons = mutableListOf<String>()

        fun requireFiniteNonNegative(value: Double, what: String) {
            if (!value.isFinite() || value < 0.0) reasons += "$what must be finite and non-negative, was $value"
        }

        requireFiniteNonNegative(input.availableWidth, "available width")
        requireFiniteNonNegative(input.insets.left, "left inset")
        requireFiniteNonNegative(input.insets.top, "top inset")
        requireFiniteNonNegative(input.insets.right, "right inset")
        requireFiniteNonNegative(input.insets.bottom, "bottom inset")

        val contentWidth = input.availableWidth - input.insets.horizontal
        if (input.rows.isNotEmpty() && contentWidth <= 0.0) {
            reasons += "insets of ${input.insets.horizontal}px leave no content width in ${input.availableWidth}px"
        }
        input.widthPolicy.sharedReference?.let { ref ->
            requireFiniteNonNegative(ref.insetH, "shared width reference inset")
            if (input.rows.isNotEmpty() && ref.insetH >= contentWidth) {
                reasons += "shared width reference inset ${ref.insetH}px consumes the whole content width"
            }
            val undeclared = ref.measuredRoles - ref.consumerRoles
            if (undeclared.isNotEmpty()) {
                reasons += "roles $undeclared define the shared width reference but do not consume it"
            }
        }

        when (val policy = input.framePolicy) {
            is FramePolicy.Intrinsic -> {
                requireFiniteNonNegative(policy.rowBaseHeight, "row base height")
                policy.maxFrameHeight?.let { requireFiniteNonNegative(it, "max frame height") }
            }
            is FramePolicy.FitToHeight -> requireFiniteNonNegative(policy.frameHeight, "frame height")
        }

        val rowIds = mutableSetOf<String>()
        val itemIds = mutableSetOf<String>()
        for (row in input.rows) {
            if (row.stableId.isBlank()) reasons += "every row needs a non-blank stable ID"
            else if (!rowIds.add(row.stableId)) reasons += "duplicate row ID '${row.stableId}'"

            val units = input.rowHeightPolicy.unitsFor(row.role)
            if (!units.isFinite() || units < 0.0) {
                reasons += "row '${row.stableId}' declares $units height units"
            }
            val heightScale = input.overrides.rowHeightScale(row.role)
            if (!heightScale.isFinite() || heightScale <= 0.0) {
                reasons += "row height override for ${row.role} must be finite and positive, was $heightScale"
            }
            val widthScale = input.overrides.itemWidthScale(row.role)
            if (!widthScale.isFinite() || widthScale <= 0.0) {
                reasons += "item width override for ${row.role} must be finite and positive, was $widthScale"
            }
            for (item in row.items) {
                if (item.stableId.isBlank()) reasons += "every item needs a non-blank stable ID"
                else if (!itemIds.add(item.stableId)) reasons += "duplicate item ID '${item.stableId}'"
                requireFiniteNonNegative(item.widthUnits, "item '${item.stableId}' width units")
                requireFiniteNonNegative(item.growWeight, "item '${item.stableId}' grow weight")
                if (!item.heightFactor.isFinite() || item.heightFactor <= 0.0) {
                    reasons += "item '${item.stableId}' height factor must be finite and positive"
                }
            }
        }

        for (element in plan.elements) {
            if (element is VerticalElement.Gap) {
                requireFiniteNonNegative(element.size, "${element.kind} gap for ${element.role}")
            }
        }
        return reasons
    }

    /** The single rounding site. Edges only — never a width or a height. */
    private fun Double.roundToPx(): Int = this.roundToInt()
}
