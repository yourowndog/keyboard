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

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode

/**
 * The momentary symbol layer: press 123, slide onto a key and lift to type it from the symbol page, after
 * which the board snaps back to letters. A plain tap on 123 still switches pages as before.
 */
internal object MomentarySymbols {
    /** Whether leaving [code]'s key while it is held on a board in [mode] opens the symbol page. */
    fun starts(code: Int, mode: KeyboardMode): Boolean =
        code == KeyCode.VIEW_SYMBOLS && mode == KeyboardMode.CHARACTERS

    /** Whether lifting on [code] types it. Lifting on a page switch (ABC, =\<) just goes back to letters. */
    fun types(code: Int): Boolean = code !in KeyCode.VIEW_PHONE2..KeyCode.VIEW_CHARACTERS
}
