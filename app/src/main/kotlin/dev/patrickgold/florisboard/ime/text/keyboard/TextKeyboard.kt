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
import dev.patrickgold.florisboard.ime.keyboard.VerticalAlignment
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.popup.PopupMapping
import kotlin.math.abs

class TextKeyboard(
    val arrangement: Array<Array<TextKey>>,
    override val mode: KeyboardMode,
    val extendedPopupMapping: PopupMapping?,
    val extendedPopupMappingDefault: PopupMapping?,
    val bottomModRowCount: Int = 2,
) : Keyboard() {
    val rowCount: Int
        get() = arrangement.size

    val keyCount: Int
        get() = arrangement.sumOf { it.size }

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

        // 1. Compute alphaUnitWidth from the widest alpha-containing row only.
        //    Mod keys inside alpha rows (shift, backspace) use their base flayWidthFactor
        //    so that alphaKeyWidthFactor and modKeyWidthFactor stay fully independent.
        val rowMarginH = alphaSpacingH * 2.0f
        var maxAlphaRowUnits = 0.0f
        for (row in rows()) {
            val hasAlpha = row.any { it.isAlpha }
            if (!hasAlpha) continue
            var rowUnits = 0.0f
            for (key in row) {
                rowUnits += if (key.isAlpha) key.flayWidthFactor * alphaKeyWidthFactor
                            else key.flayWidthFactor
            }
            maxAlphaRowUnits = maxOf(maxAlphaRowUnits, rowUnits)
        }
        val alphaUnitWidth = if (maxAlphaRowUnits > 0f) (keyboardWidth - rowMarginH) / maxAlphaRowUnits else keyboardWidth

        var currentPosY = 0.0f
        for ((r, row) in rows().withIndex()) {
            val hasExtraRows = rowCount >= 5
            val rowHeight = if (hasExtraRows) {
                val topModCount = (rowCount - 3 - bottomModRowCount).coerceAtLeast(0)
                val modRowCount = bottomModRowCount + topModCount
                val alphaRowCount = rowCount - modRowCount
                val effectiveRowCount = (alphaRowCount * alphaRowHeightFactor) + (modRowCount * bottomRowHeightFactor)
                val baseHeight = keyboardHeight / effectiveRowCount

                val isModHeightRow = r < topModCount || r >= rowCount - bottomModRowCount
                if (isModHeightRow) baseHeight * bottomRowHeightFactor else baseHeight * alphaRowHeightFactor
            } else {
                keyboardHeight / rowCount.toFloat()
            }

            // Determine the effective unit width for this row.
            // Alpha rows use the shared alphaUnitWidth (consistent key size across all alpha rows).
            // Pure mod rows compute their own unit width so they fill the screen independently,
            // meaning modKeyWidthFactor has no effect on alpha key sizing and vice versa.
            val isAlphaRow = row.any { it.isAlpha }
            val rowUnitWidth = if (isAlphaRow) {
                alphaUnitWidth
            } else {
                var modRowUnits = 0.0f
                for (key in row) { modRowUnits += key.flayWidthFactor * modKeyWidthFactor }
                if (modRowUnits > 0f) keyboardWidth / modRowUnits else alphaUnitWidth
            }

            // Calculate total width of this specific row
            var totalRowWidth = 0.0f
            for (key in row) {
                val factor = if (key.isAlpha) {
                    key.flayWidthFactor * alphaKeyWidthFactor
                } else {
                    key.flayWidthFactor * modKeyWidthFactor
                }
                totalRowWidth += factor * rowUnitWidth
            }

            // Centering logic: start at half the leftover space
            var posX = (keyboardWidth - totalRowWidth) / 2.0f

            for ((k, key) in row.withIndex()) {
                val factor = if (key.isAlpha) {
                    key.flayWidthFactor * alphaKeyWidthFactor
                } else {
                    key.flayWidthFactor * modKeyWidthFactor
                }
                val keyWidth = factor * rowUnitWidth
                
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

                // Spacing logic: Alpha vs Mod
                val mH = if (key.isAlpha) alphaSpacingH else modSpacingH
                val mV = if (key.isAlpha) alphaSpacingV else modSpacingV

                key.visibleBounds.apply {
                    left = key.touchBounds.left + mH
                    top = key.touchBounds.top + mV
                    right = key.touchBounds.right - mH
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
