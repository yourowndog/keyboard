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

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.LayoutPackKeyData
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.VerticalAlignment
import dev.patrickgold.florisboard.ime.keyboard.geometry.BoundaryGapPolicy
import dev.patrickgold.florisboard.ime.keyboard.geometry.FramePolicy
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryItem
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryItemKind
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryOrientation
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryOverrides
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryRect
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryRow
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometrySolution
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometrySolverInput
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometrySpacing
import dev.patrickgold.florisboard.ime.keyboard.geometry.KeyboardGeometrySolver
import dev.patrickgold.florisboard.ime.keyboard.geometry.RoleBlockGaps
import dev.patrickgold.florisboard.ime.keyboard.geometry.RowHeightPolicy
import dev.patrickgold.florisboard.ime.keyboard.geometry.RowWidthPolicy
import dev.patrickgold.florisboard.ime.keyboard.geometry.SharedWidthReference
import dev.patrickgold.florisboard.ime.keyboard.geometry.SolvedGeometry
import dev.patrickgold.florisboard.ime.keyboard.geometry.SolvedItem
import dev.patrickgold.florisboard.ime.keyboard.geometry.SolvedRow
import dev.patrickgold.florisboard.lib.FlorisRect
import kotlin.math.max

internal const val TEXT_KEYBOARD_GEOMETRY_REVISION: Int = 3

private val ENTRY_ROLES = setOf(
    SemanticRowRole.ALPHA,
    SemanticRowRole.EXTENSION,
    SemanticRowRole.NUMERIC,
    SemanticRowRole.SYMBOL,
)

private val SHORT_ROLES = setOf(
    SemanticRowRole.EXTENSION,
    SemanticRowRole.CODING_UTILITY,
)

data class TextKeyboardGeometryConfig(
    val rowBaseHeight: Double,
    val bottomRowHeightFactor: Double,
    val alphaRowHeightFactor: Double,
    val alphaKeyWidthFactor: Double,
    val modKeyWidthFactor: Double,
    val alphaSpacingHorizontal: Double,
    val alphaSpacingVertical: Double,
    val modSpacingHorizontal: Double,
    val modSpacingVertical: Double,
    val modRowUpperGap: Double,
    val modRowInnerGap: Double,
    val modRowLowerGap: Double,
    val orientation: GeometryOrientation,
)

data class GeometryFloatRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun toFlorisRect(): FlorisRect = FlorisRect.new(left, top, right, bottom)

    companion object {
        val Empty = GeometryFloatRect(0f, 0f, 0f, 0f)
    }
}

/**
 * One key's three inspectable layers. Structural bounds are the solver allocation; touch and
 * visual bounds are pure derivations from that allocation and the declarations carried with it.
 */
data class ResolvedKeyGeometry(
    val stableId: String,
    val structuralBounds: GeometryRect,
    val touchBounds: GeometryFloatRect,
    val visibleBounds: GeometryFloatRect,
)

/**
 * The immutable owner consumed by both frame sizing and key placement.
 */
data class TextKeyboardGeometry(
    val structural: SolvedGeometry,
    val keys: List<List<ResolvedKeyGeometry>>,
    val popupReferenceBounds: GeometryFloatRect,
) {
    /**
     * Derives a popup rectangle from the solved frame, the representative solved/visual key size,
     * and the actual anchor key's visual bounds. Horizontal clamping keeps edge previews on-frame.
     */
    fun popupBoundsFor(keyVisibleBounds: FlorisRect): FlorisRect {
        val widthFactor = if (structural.orientation == GeometryOrientation.LANDSCAPE) 1.0f else 1.1f
        val heightFactor = if (structural.orientation == GeometryOrientation.LANDSCAPE) 3.0f else 2.5f
        val popupWidth = popupReferenceBounds.width * widthFactor
        val popupHeight = popupReferenceBounds.height * heightFactor
        val centeredLeft = keyVisibleBounds.left + (keyVisibleBounds.width - popupWidth) / 2f
        val frameLeft = structural.frame.left.toFloat()
        val maxLeft = structural.frame.right.toFloat() - popupWidth
        val left = if (maxLeft >= frameLeft) {
            centeredLeft.coerceIn(frameLeft, maxLeft)
        } else {
            frameLeft
        }
        val bottom = keyVisibleBounds.bottom
        return FlorisRect.new(
            left = left,
            top = bottom - popupHeight,
            right = left + popupWidth,
            bottom = bottom,
        )
    }
}

/**
 * Sole Compose memoization key for structural geometry.
 *
 * Visual-only legacy key customizations deliberately do not participate.
 */
data class TextKeyboardGeometryCacheKey(
    val evaluatorVersion: Int,
    val frameSourceVersion: Int,
    val availableWidthPx: Int,
    val config: TextKeyboardGeometryConfig,
    val solverRevision: Int = TEXT_KEYBOARD_GEOMETRY_REVISION,
)

sealed interface TextKeyboardGeometrySolution {
    data class Solved(val geometry: TextKeyboardGeometry) : TextKeyboardGeometrySolution
    data class Unsatisfiable(val reasons: List<String>) : TextKeyboardGeometrySolution
}

object TextKeyboardGeometryResolver {
    fun solve(
        keyboard: TextKeyboard,
        availableWidth: Double,
        config: TextKeyboardGeometryConfig,
        fitToHeight: Double? = null,
    ): TextKeyboardGeometrySolution {
        val input = solverInput(keyboard, availableWidth, config, fitToHeight)
        return when (val solution = KeyboardGeometrySolver.solve(input)) {
            is GeometrySolution.Unsatisfiable -> TextKeyboardGeometrySolution.Unsatisfiable(solution.reasons)
            is GeometrySolution.Solved -> TextKeyboardGeometrySolution.Solved(
                deriveGeometry(solution.geometry),
            )
        }
    }

    internal fun solverInput(
        keyboard: TextKeyboard,
        availableWidth: Double,
        config: TextKeyboardGeometryConfig,
        fitToHeight: Double? = null,
    ): GeometrySolverInput {
        val spacingByRole = SemanticRowRole.entries.associateWith { role ->
            if (role == SemanticRowRole.CODING_UTILITY) {
                GeometrySpacing(config.modSpacingHorizontal, config.modSpacingVertical)
            } else {
                GeometrySpacing(config.alphaSpacingHorizontal, config.alphaSpacingVertical)
            }
        }
        val rows = keyboard.semanticRows.mapIndexed { rowIndex, semanticRow ->
            val spacing = spacingByRole.getValue(semanticRow.role)
            GeometryRow(
                stableId = semanticRow.stableId,
                role = semanticRow.role,
                items = keyboard.arrangement[rowIndex].mapIndexed { keyIndex, key ->
                    val packData = key.data as? LayoutPackKeyData
                    GeometryItem(
                        stableId = "${semanticRow.stableId}#$keyIndex",
                        widthUnits = key.flayWidthFactor.toDouble(),
                        heightFactor = key.flayHeightFactor.toDouble(),
                        verticalAlignment = key.flayVerticalAlignment,
                        kind = if (packData?.isSpacer == true) {
                            GeometryItemKind.SPACER
                        } else {
                            GeometryItemKind.KEY
                        },
                        touchExpansionHorizontal = if (key.isAlpha) {
                            spacing.horizontal * 0.2
                        } else {
                            0.0
                        },
                        visualPaddingLeftRatio = key.flayPaddingLeft.toDouble(),
                        visualPaddingRightRatio = key.flayPaddingRight.toDouble(),
                    )
                },
            )
        }
        val heightScales = SemanticRowRole.entries.associateWith { role ->
            if (role in SHORT_ROLES) config.bottomRowHeightFactor else config.alphaRowHeightFactor
        }
        val widthScales = SemanticRowRole.entries.associateWith { role ->
            when {
                // The shared solver guarantees containment. Legacy values above 100% intentionally
                // overflowed and clipped; until Stage 07 migrates structural customization, the
                // validated production override is capped at the largest contained value.
                role in ENTRY_ROLES -> config.alphaKeyWidthFactor.coerceAtMost(1.0)
                role == SemanticRowRole.PRIMARY_ACTION -> 1.0
                else -> config.modKeyWidthFactor.coerceAtMost(1.0)
            }
        }
        return GeometrySolverInput(
            availableWidth = availableWidth,
            rows = rows,
            framePolicy = fitToHeight?.let { FramePolicy.FitToHeight(it) }
                ?: FramePolicy.Intrinsic(config.rowBaseHeight),
            rowHeightPolicy = RowHeightPolicy(),
            gapPolicy = BoundaryGapPolicy(
                mapOf(
                    SemanticRowRole.CODING_UTILITY to RoleBlockGaps(
                        above = config.modRowUpperGap,
                        within = config.modRowInnerGap,
                        below = config.modRowLowerGap,
                    ),
                ),
            ),
            widthPolicy = RowWidthPolicy(
                sharedReference = SharedWidthReference(
                    measuredRoles = ENTRY_ROLES,
                    consumerRoles = ENTRY_ROLES + SemanticRowRole.PRIMARY_ACTION,
                    insetH = config.alphaSpacingHorizontal * 2.0,
                ),
            ),
            overrides = GeometryOverrides(
                rowHeightScaleByRole = heightScales,
                itemWidthScaleByRole = widthScales,
            ),
            spacingByRole = spacingByRole,
            orientation = config.orientation,
        )
    }

    private fun deriveGeometry(structural: SolvedGeometry): TextKeyboardGeometry {
        val lastRowId = structural.rows.lastOrNull()?.stableId
        val keys = structural.rows.map { row ->
            row.items.map { item ->
                deriveKeyGeometry(structural, row, item, row.stableId == lastRowId)
            }
        }
        val popupCandidates = structural.rows.zip(keys)
            .filter { (row, _) -> row.role in ENTRY_ROLES }
            .flatMap { (_, rowKeys) -> rowKeys }
            .filter { key -> key.visibleBounds.width > 0f && key.visibleBounds.height > 0f }
            .ifEmpty {
                keys.flatten().filter { key ->
                    key.visibleBounds.width > 0f && key.visibleBounds.height > 0f
                }
            }
        val popupReference = if (popupCandidates.isEmpty()) {
            GeometryFloatRect.Empty
        } else {
            val widths = popupCandidates.map { it.visibleBounds.width }.sorted()
            val heights = popupCandidates.map { it.visibleBounds.height }.sorted()
            GeometryFloatRect(0f, 0f, widths[widths.size / 2], heights[heights.size / 2])
        }
        return TextKeyboardGeometry(structural, keys, popupReference)
    }

    private fun deriveKeyGeometry(
        geometry: SolvedGeometry,
        row: SolvedRow,
        item: SolvedItem,
        isFinalRow: Boolean,
    ): ResolvedKeyGeometry {
        if (item.kind == GeometryItemKind.SPACER) {
            return ResolvedKeyGeometry(
                stableId = item.stableId,
                structuralBounds = item.bounds,
                touchBounds = GeometryFloatRect.Empty,
                visibleBounds = GeometryFloatRect.Empty,
            )
        }
        val rowHeight = item.bounds.height.toFloat()
        val keyHeight = rowHeight * item.declaredHeightFactor.toFloat()
        val heightDelta = keyHeight - rowHeight
        val verticalOffset = when (item.declaredVerticalAlignment) {
            VerticalAlignment.TOP -> -heightDelta
            VerticalAlignment.CENTER -> -heightDelta / 2f
            VerticalAlignment.BOTTOM -> 0f
        }
        val allocated = GeometryFloatRect(
            left = item.bounds.left.toFloat(),
            top = item.bounds.top + verticalOffset,
            right = item.bounds.right.toFloat(),
            bottom = item.bounds.bottom + heightDelta + verticalOffset,
        )
        val itemWidth = allocated.width
        val visible = GeometryFloatRect(
            left = allocated.left + row.declaredSpacing.horizontal.toFloat() +
                item.declaredVisualPaddingLeftRatio.toFloat() * itemWidth,
            top = allocated.top + row.declaredSpacing.vertical.toFloat(),
            right = allocated.right - row.declaredSpacing.horizontal.toFloat() -
                item.declaredVisualPaddingRightRatio.toFloat() * itemWidth,
            bottom = allocated.bottom - row.declaredSpacing.vertical.toFloat(),
        )
        val touchExpansion = item.declaredTouchExpansionHorizontal.toFloat()
        val touch = GeometryFloatRect(
            left = allocated.left - touchExpansion,
            top = allocated.top,
            right = allocated.right + touchExpansion,
            bottom = if (isFinalRow) {
                max(allocated.bottom, geometry.frame.bottom.toFloat())
            } else {
                allocated.bottom
            },
        )
        return ResolvedKeyGeometry(item.stableId, item.bounds, touch, visible)
    }
}

fun TextKeyboard.applyGeometry(geometry: TextKeyboardGeometry) {
    require(arrangement.size == geometry.keys.size) {
        "keyboard rows (${arrangement.size}) do not match solved rows (${geometry.keys.size})"
    }
    arrangement.forEachIndexed { rowIndex, row ->
        val solvedRow = geometry.keys[rowIndex]
        require(row.size == solvedRow.size) {
            "keyboard row $rowIndex keys (${row.size}) do not match solved keys (${solvedRow.size})"
        }
        row.forEachIndexed { keyIndex, key ->
            val solvedKey = solvedRow[keyIndex]
            key.structuralBounds.apply {
                left = solvedKey.structuralBounds.left.toFloat()
                top = solvedKey.structuralBounds.top.toFloat()
                right = solvedKey.structuralBounds.right.toFloat()
                bottom = solvedKey.structuralBounds.bottom.toFloat()
            }
            key.touchBounds.applyFrom(solvedKey.touchBounds.toFlorisRect())
            key.visibleBounds.applyFrom(solvedKey.visibleBounds.toFlorisRect())
        }
    }
}
