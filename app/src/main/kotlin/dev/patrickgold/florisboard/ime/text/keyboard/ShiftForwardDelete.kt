/*
 * Copyright (C) 2026 The OmniBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.editor.OperationUnit
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.ime.text.gestures.SwipeGesture

/**
 * Shift's forward delete: tap Shift and drag right to delete the text after the cursor, the mirror image
 * of dragging left from Delete. The rules live here so they can be tested without a touch pipeline.
 */
internal object ShiftForwardDelete {
    /**
     * Whether a swipe event that started on Shift turns into a forward delete. It must be a drag (not a
     * fling on release) heading right while the finger is still on Shift, before the long press has turned
     * the dial to Ctrl. Raw input editors (terminals) have no selection to grow, so they keep the old
     * slide-to-capital behaviour.
     */
    fun engages(
        isMove: Boolean,
        direction: SwipeGesture.Direction,
        isStillOnShift: Boolean,
        hasLongPressed: Boolean,
        isRawInputEditor: Boolean,
    ): Boolean = isMove && direction == SwipeGesture.Direction.RIGHT && isStillOnShift &&
        !hasLongPressed && !isRawInputEditor

    /** Words when the Delete swipe works in words, characters otherwise. */
    fun unitFor(deleteSwipe: SwipeAction): OperationUnit = when (deleteSwipe) {
        SwipeAction.DELETE_WORDS_PRECISELY, SwipeAction.SELECT_WORDS_PRECISELY -> OperationUnit.WORDS
        else -> OperationUnit.CHARACTERS
    }

    /**
     * How many units after the cursor a drag of [absUnitCountX] swipe units selects. Mirrors the Delete
     * swipe exactly (one character per unit, one word per two units, the first unit only arms it).
     */
    fun selectedUnits(absUnitCountX: Int, unit: OperationUnit): Int {
        val steps = when (unit) {
            OperationUnit.WORDS -> absUnitCountX / 2
            OperationUnit.CHARACTERS -> absUnitCountX
        }
        return (steps - 1).coerceAtLeast(0)
    }
}
