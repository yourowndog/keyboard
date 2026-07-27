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

package dev.patrickgold.florisboard.ime.keyboard

/**
 * Stage 00 observation seam for the keyboard's vertical geometry arithmetic.
 *
 * The functions here are pure extractions of arithmetic that was previously inlined in
 * [FlorisImeSizing.keyboardUiHeight] and `TextKeyboard.layout`. They are **behaviour-neutral**:
 * each returns exactly what the inlined code computed, so that the two independent height
 * authorities can be observed and compared from unit tests without a Compose runtime, a
 * `Context`, or a device.
 *
 * They intentionally reproduce current behaviour, including behaviour later stages are expected
 * to change (see `docs/architecture/keyboard-geometry-migration-plan.md`). Do not treat these as
 * the target model: the migration replaces both authorities with a single shared solver.
 */

/**
 * How the outer-frame calculation partitions a keyboard's rows.
 *
 * Note that this partition is derived purely from counts, not from row semantics: any row that is
 * not accounted for as "alpha" is treated as a modifier row regardless of its actual contents.
 */
internal data class FrameRowPartition(
    val alphaRows: Int,
    val modRows: Int,
    val topModRows: Int,
)

/**
 * Partitions [rowCount] rows into alpha and modifier rows the way the outer frame calculation does.
 *
 * Rows beyond the standard `3 alpha + bottomModRowCount` are treated as extension rows at the top
 * and counted as modifier rows.
 */
internal fun computeFrameRowPartition(rowCount: Int, bottomModRowCount: Int): FrameRowPartition {
    val topModRows = (rowCount - 3 - bottomModRowCount).coerceAtLeast(0)
    val modRows = bottomModRowCount + topModRows
    val alphaRows = (rowCount - modRows).coerceAtLeast(0)
    return FrameRowPartition(alphaRows = alphaRows, modRows = modRows, topModRows = topModRows)
}

/**
 * Computes the total outer keyboard height, in the same unit as [rowBaseHeight].
 *
 * [rawRowCount] is coerced to at least 4 — a keyboard with fewer rows (including one with no rows
 * at all) is still framed as if it had four.
 */
internal fun computeKeyboardFrameHeight(
    rawRowCount: Int,
    bottomModRowCount: Int,
    rowBaseHeight: Float,
    alphaRowHeightFactor: Float,
    bottomRowHeightFactor: Float,
    gapTotal: Float,
): Float {
    val rowCount = rawRowCount.coerceAtLeast(4)
    val partition = computeFrameRowPartition(rowCount, bottomModRowCount)
    val alphaTotal = rowBaseHeight * partition.alphaRows * alphaRowHeightFactor
    val modTotal = rowBaseHeight * partition.modRows * bottomRowHeightFactor
    val gaps = if (partition.modRows > 0) gapTotal else 0f
    return alphaTotal + modTotal + gaps
}

/**
 * Computes the height allocated to the row at [rowIndex] during inner layout.
 *
 * Keyboards with fewer than 5 rows take a uniform-height branch that ignores both height factors
 * entirely; only keyboards with 5 or more rows apply them.
 */
internal fun computeLayoutRowHeight(
    rowIndex: Int,
    rowCount: Int,
    bottomModRowCount: Int,
    keyboardHeight: Float,
    alphaRowHeightFactor: Float,
    bottomRowHeightFactor: Float,
): Float {
    val hasExtraRows = rowCount >= 5
    if (!hasExtraRows) {
        return keyboardHeight / rowCount.toFloat()
    }
    val topModCount = (rowCount - 3 - bottomModRowCount).coerceAtLeast(0)
    val modRowCount = bottomModRowCount + topModCount
    val alphaRowCount = rowCount - modRowCount
    val effectiveRowCount = (alphaRowCount * alphaRowHeightFactor) + (modRowCount * bottomRowHeightFactor)
    val baseHeight = keyboardHeight / effectiveRowCount
    val isModHeightRow = rowIndex < topModCount || rowIndex >= rowCount - bottomModRowCount
    return if (isModHeightRow) baseHeight * bottomRowHeightFactor else baseHeight * alphaRowHeightFactor
}
