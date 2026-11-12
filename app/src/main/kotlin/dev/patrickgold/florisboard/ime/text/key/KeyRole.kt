/*
 * Copyright (C) 2026 The OmniBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.key

import dev.patrickgold.florisboard.ime.keyboard.KeyData

/**
 * What a key is for, independent of where it sits or which layout it came from.
 *
 * One classification serves every per-category treatment, so a key is never "delete" to the theme
 * and "symbol" to the sound pack:
 * - themes select on it through the `role` key attribute, e.g. `key[role=`enter`]`, which is how a
 *   skin gives each slot of the fixed key inventory its own texture (docs/theming/key-skins.md);
 * - [SoundEvent.forKey][dev.patrickgold.florisboard.ime.sound.SoundEvent.forKey] maps it to a sound.
 *
 * [id] is a stable contract written into stylesheets: add roles, never rename one.
 */
enum class KeyRole(val id: String) {
    ALPHA("alpha"),
    NUMBER("number"),
    SYMBOL("symbol"),
    SPACE("space"),
    DELETE("delete"),
    ENTER("enter"),
    SHIFT("shift"),
    CAPS_LOCK("capslock"),
    /** Ctrl, Alt, Fn, Tab, Esc, the tmux prefix and the cursor keys: the coding cluster. */
    MODIFIER("modifier"),
    /** Switches between character, symbol and numeric layouts. */
    LAYOUT("layout"),
    /** The Text/Code profile toggle and the language key. */
    PROFILE("profile"),
    VOICE("voice"),
    CLIPBOARD("clipboard"),
    /** Any other internal action: settings, media, undo and the like. */
    COMMAND("command");

    companion object {
        /** Pure, so the classification is unit-tested without Android. */
        fun forKey(data: KeyData): KeyRole {
            val code = data.code
            return when (code) {
                KeyCode.SPACE -> SPACE
                KeyCode.ENTER -> ENTER
                KeyCode.DELETE, KeyCode.DELETE_WORD,
                KeyCode.FORWARD_DELETE, KeyCode.FORWARD_DELETE_WORD -> DELETE
                KeyCode.SHIFT -> SHIFT
                KeyCode.CAPS_LOCK -> CAPS_LOCK
                KeyCode.CTRL, KeyCode.CTRL_LOCK, KeyCode.ALT, KeyCode.ALT_LOCK,
                KeyCode.FN, KeyCode.FN_LOCK, KeyCode.TAB, KeyCode.ESCAPE, KeyCode.TMUX_PREFIX,
                in KeyCode.MOVE_END_OF_LINE..KeyCode.ARROW_LEFT -> MODIFIER
                in KeyCode.VIEW_PHONE2..KeyCode.VIEW_CHARACTERS -> LAYOUT
                KeyCode.TOGGLE_KEYBOARD_PROFILE, KeyCode.LANGUAGE_SWITCH -> PROFILE
                KeyCode.VOICE_INPUT -> VOICE
                KeyCode.CLIPBOARD_PASTE -> CLIPBOARD
                // Emoji, multi-code-point and unspecified keys behave like a plain character key.
                KeyCode.UNSPECIFIED, KeyCode.MULTIPLE_CODE_POINTS -> ALPHA
                else -> when {
                    code < 0 -> COMMAND
                    Character.isDigit(code) -> NUMBER
                    Character.isLetter(code) -> ALPHA
                    else -> SYMBOL
                }
            }
        }
    }
}
