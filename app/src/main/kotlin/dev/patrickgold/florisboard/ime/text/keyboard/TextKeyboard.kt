/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

import dev.patrickgold.florisboard.ime.keyboard.Key
import dev.patrickgold.florisboard.ime.keyboard.Keyboard
import dev.patrickgold.florisboard.ime.keyboard.KeyboardSemantics
import dev.patrickgold.florisboard.ime.keyboard.NormalizedRow
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.SentinelKind
import dev.patrickgold.florisboard.ime.keyboard.VerticalAlignment
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.computeLayoutRowHeight
import dev.patrickgold.florisboard.ime.keyboard.validateAgainst
import dev.patrickgold.florisboard.ime.popup.PopupMapping
import kotlin.math.abs

/**
 * @param semantics what each row of [arrangement] is, as known by whichever code composed it. Every
 *   construction site declares this explicitly — there is no default, because a keyboard that
 *   inherits semantics it never stated is exactly the failure this model exists to prevent.
 * @param bottomModRowCount **deprecated compatibility projection.** Despite the name it is not a
 *   count of the keyboard's bottom modifier rows; it is the legacy number that sizing and layout
 *   still divide by. Read [semantics] for what rows actually are. Removed once the geometry
 *   authorities consume semantic roles.
 */
class TextKeyboard(
    val arrangement: Array<Array<TextKey>>,
    override val mode: KeyboardMode,
    val extendedPopupMapping: PopupMapping?,
    val extendedPopupMappingDefault: PopupMapping?,
    val bottomModRowCount: Int = 2,
    val semantics: KeyboardSemantics,
) : Keyboard() {
    init {
        semantics.validateAgainst(arrangement.size)
    }

    val rowCount: Int
        get() = arrangement.size

    val keyCount: Int
        get() = arrangement.sumOf { it.size }

    /** The semantic rows, parallel to [arrangement]. Empty for a sentinel keyboard. */
    val semanticRows: List<NormalizedRow>
        get() = when (val s = semantics) {
            is KeyboardSemantics.Rows -> s.rows
            is KeyboardSemantics.Sentinel -> emptyList()
        }

    /** Which sentinel this keyboard is, or null if it carries rows. */
    val sentinelKind: SentinelKind?
        get() = (semantics as? KeyboardSemantics.Sentinel)?.kind

    /** The semantics of the row at [index], or null if [index] is out of range. */
    fun rowSemantics(index: Int): NormalizedRow? = semanticRows.getOrNull(index)

    /** The rows carrying [role], in arrangement order. */
    fun rowsWithRole(role: SemanticRowRole): List<NormalizedRow> = semanticRows.filter { it.role == role }

    override fun getKeyForPos(pointerX: Float, pointerY: Float): TextKey? {
        for (key in keys()) {
            if (key.touchBounds.contains(pointerX, pointerY)) {
                return key
            }
        }
        return null
    }

    override fun layout(
        keyboardWidth: Float,
        keyboardHeight: Float,
        desiredKey: Key,
        extendTouchBoundariesDownwards: Boolean,
        bottomRowHeightFactor: Float,
        alphaRowHeightFactor: Float,
        alphaKeyWidthFactor: Float,
        modKeyWidthFactor: Float,
        alphaSpacingH: Float,
        alphaSpacingV: Float,
        modSpacingH: Float,
        modSpacingV: Float,
    ) {
        if (arrangement.isEmpty()) return

        if (keyboardWidth.isNaN() || keyboardHeight.isNaN()) return

        // 1. Compute base unit widths WITHOUT slider multipliers.
        //    The sliders are applied only when computing actual key pixel widths.
        //    This prevents the factor from cancelling out (numerator / denominator = 1).
        //
        //    baseAlphaUnitWidth: reference width for a single "unit" in the widest alpha row
        //      at factor=1.0. Scaling alphaKeyWidthFactor above/below 1.0 shrinks/grows
        //      alpha keys, centering the row when keys are smaller than the screen.
        //
        //    baseModUnitWidth: computed per-row so each mod row fills the screen at factor=1.0.
        //      modKeyWidthFactor scales all mod keys uniformly without affecting alpha rows.
        val rowMarginH = alphaSpacingH * 2.0f
        var maxAlphaBaseUnits = 0.0f
        for (row in rows()) {
            if (!row.any { it.isAlpha }) continue
            var rowUnits = 0.0f
            for (key in row) { rowUnits += key.flayWidthFactor }
            maxAlphaBaseUnits = maxOf(maxAlphaBaseUnits, rowUnits)
        }
        val baseAlphaUnitWidth = if (maxAlphaBaseUnits > 0f) (keyboardWidth - rowMarginH) / maxAlphaBaseUnits else keyboardWidth

        var currentPosY = 0.0f
        for ((r, row) in rows().withIndex()) {
            val rowHeight = computeLayoutRowHeight(
                rowIndex = r,
                rowCount = rowCount,
                bottomModRowCount = bottomModRowCount,
                keyboardHeight = keyboardHeight,
                alphaRowHeightFactor = alphaRowHeightFactor,
                bottomRowHeightFactor = bottomRowHeightFactor,
            )

            // Per-row base unit width (no slider multiplier, so sliders don't self-cancel).
            // Alpha rows share baseAlphaUnitWidth. Mod-only rows each compute their own so
            // their proportions are preserved at factor=1.0 and scaled uniformly by modKeyWidthFactor.
            val isAlphaRow = row.any { it.isAlpha }
            // Space row: contains the spacebar. Uses alpha unit width + alpha spacing so it
            // is immune to modKeyWidthFactor. Centering naturally gives it slightly more side
            // margin than alpha rows (fewer total units, same reference width).
            val isSpaceRow = !isAlphaRow && row.any { it.computedData.code == 32 }
            val baseRowUnitWidth = when {
                isAlphaRow -> baseAlphaUnitWidth
                isSpaceRow -> baseAlphaUnitWidth  // immune to mod slider
                else -> {
                    var baseModRowUnits = 0.0f
                    for (key in row) { baseModRowUnits += key.flayWidthFactor }
                    if (baseModRowUnits > 0f) keyboardWidth / baseModRowUnits else baseAlphaUnitWidth
                }
            }

            // Apply slider multipliers only here — they affect actual pixel widths, not the reference.
            // Space row keys use factor=1.0 so neither slider affects them.
            var totalRowWidth = 0.0f
            for (key in row) {
                val widthFactor = when {
                    key.isAlpha -> alphaKeyWidthFactor
                    isSpaceRow -> 1.0f
                    else -> modKeyWidthFactor
                }
                totalRowWidth += key.flayWidthFactor * widthFactor * baseRowUnitWidth
            }

            // Centering: if factor < 1.0 keys shrink and the row centers; > 1.0 they overflow and clip.
            var posX = (keyboardWidth - totalRowWidth) / 2.0f

            for ((k, key) in row.withIndex()) {
                val widthFactor = when {
                    key.isAlpha -> alphaKeyWidthFactor
                    isSpaceRow -> 1.0f
                    else -> modKeyWidthFactor
                }
                val keyWidth = key.flayWidthFactor * widthFactor * baseRowUnitWidth
                
                // Vertical alignment and height calculation
                val keyHeight = rowHeight * key.flayHeightFactor
                val heightDelta = keyHeight - rowHeight
                val verticalOffset = when (key.flayVerticalAlignment) {
                    VerticalAlignment.TOP -> -heightDelta
                    VerticalAlignment.CENTER -> -heightDelta / 2.0f
                    VerticalAlignment.BOTTOM -> 0.0f
                }
                
                key.touchBounds.apply {
                    left = posX
                    top = currentPosY + verticalOffset
                    right = posX + keyWidth
                    bottom = currentPosY + rowHeight + (keyHeight - rowHeight) + verticalOffset
                }

                // Spacing logic: Alpha vs Space row vs Mod
                val mH = when {
                    key.isAlpha -> alphaSpacingH
                    isSpaceRow -> alphaSpacingH
                    else -> modSpacingH
                }
                val mV = when {
                    key.isAlpha -> alphaSpacingV
                    isSpaceRow -> alphaSpacingV
                    else -> modSpacingV
                }

                val kw = key.touchBounds.right - key.touchBounds.left
                key.visibleBounds.apply {
                    left = key.touchBounds.left + mH + key.flayPaddingLeft * kw
                    top = key.touchBounds.top + mV
                    right = key.touchBounds.right - mH - key.flayPaddingRight * kw
                    bottom = key.touchBounds.bottom - mV
                }

                // Hitbox expansion (Touch Target Expansion)
                // We expand the touch hitbox horizontally for alpha keys
                // by a small amount (e.g., 20% of the horizontal spacing)
                if (key.isAlpha && mH > 0) {
                    val expansion = mH * 0.2f
                    key.touchBounds.left -= expansion
                    key.touchBounds.right += expansion
                }

                posX += keyWidth
                
                // After-adjust touch bounds for the last main row to extend into the bezel if needed
                if (extendTouchBoundariesDownwards && r + 1 == arrangement.size) {
                    key.touchBounds.bottom += keyboardHeight / rowCount.toFloat()
                }
            }
            currentPosY += rowHeight
        }
    }

    override fun keys(): Iterator<TextKey> {
        return TextKeyboardIterator(arrangement)
    }

    fun rows(): Iterator<Array<TextKey>> {
        return arrangement.iterator()
    }

    class TextKeyboardIterator internal constructor(
        private val arrangement: Array<Array<TextKey>>
    ) : Iterator<TextKey> {
        private var rowIndex: Int = 0
        private var keyIndex: Int = 0

        override fun hasNext(): Boolean {
            return rowIndex < arrangement.size && keyIndex < arrangement[rowIndex].size
        }

        override fun next(): TextKey {
            val next = arrangement[rowIndex][keyIndex]
            if (keyIndex + 1 == arrangement[rowIndex].size) {
                rowIndex++
                keyIndex = 0
            } else {
                keyIndex++
            }
            return next
        }
    }
}
