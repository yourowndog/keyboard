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

import dev.patrickgold.florisboard.ime.keyboard.Keyboard
import dev.patrickgold.florisboard.ime.keyboard.KeyboardSemantics
import dev.patrickgold.florisboard.ime.keyboard.NormalizedRow
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.SentinelKind
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.validateAgainst
import dev.patrickgold.florisboard.ime.popup.PopupMapping

/**
 * @param semantics what each row of [arrangement] is, as known by whichever code composed it. Every
 *   construction site declares this explicitly — there is no default, because a keyboard that
 *   inherits semantics it never stated is exactly the failure this model exists to prevent.
 * @param bottomModRowCount **deprecated compatibility projection, no longer consulted by geometry.**
 *   Despite the name it is not a count of the keyboard's bottom modifier rows. Nothing in the
 *   solved geometry pipeline reads it; it survives only for the construction sites that still pass
 *   it, and Stage 04 removes it. Read [semantics] for what rows actually are.
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
