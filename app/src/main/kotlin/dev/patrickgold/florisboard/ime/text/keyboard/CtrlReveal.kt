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

import dev.patrickgold.florisboard.ime.text.key.KeyCode

/**
 * Ctrl reveal: while Ctrl is latched, the hint plates above the letters stop showing symbols and show
 * what Ctrl plus that letter does instead, so the editing shortcuts live on keys the board already has.
 *
 * Only the chords every Android text field understands are named. Ctrl with any other letter still
 * sends the chord (a terminal reads it as a control character); its plate just goes blank, which keeps
 * the five named actions readable at a glance.
 */
internal object CtrlReveal {
    private val labels = mapOf(
        'a'.code to "ALL",
        'c'.code to "COPY",
        'x'.code to "CUT",
        'v'.code to "PASTE",
        'z'.code to "UNDO",
    )

    /**
     * The plate text for a letter key while Ctrl is latched, or null for a letter without a named action.
     * Upper and lower case map alike, because a shifted board sends the same chord.
     */
    fun plateLabel(code: Int): String? {
        if (code !in 'A'.code..'Z'.code && code !in 'a'.code..'z'.code) return null
        return labels[Character.toLowerCase(code)]
    }

    /**
     * Whether holding Shift should latch Ctrl instead of locking caps. Only on a board with no Ctrl key
     * of its own (the Text profile); a board that has one keeps the old hold-for-caps-lock, which is
     * redundant with a double tap of Shift anyway.
     */
    fun shiftHoldLatchesCtrl(keyCodes: Sequence<Int>): Boolean =
        keyCodes.none { it == KeyCode.CTRL || it == KeyCode.CTRL_LOCK }
}
