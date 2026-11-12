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

package dev.patrickgold.florisboard.ime.sound

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundEventTest {
    private fun key(code: Int) = TextKeyData(code = code)

    @Test
    fun printableKeysSplitIntoLettersDigitsAndSymbols() {
        assertEquals(SoundEvent.KEY_ALPHA, SoundEvent.forKey(key('q'.code)))
        assertEquals(SoundEvent.KEY_ALPHA, SoundEvent.forKey(key('ж'.code)))
        assertEquals(SoundEvent.KEY_NUMBER, SoundEvent.forKey(key('7'.code)))
        assertEquals(SoundEvent.KEY_SYMBOL, SoundEvent.forKey(key('{'.code)))
        assertEquals(SoundEvent.KEY_SYMBOL, SoundEvent.forKey(key('='.code)))
    }

    @Test
    fun theFourStockClicksKeepTheirOwnEvents() {
        assertEquals(SoundEvent.KEY_SPACE, SoundEvent.forKey(key(KeyCode.SPACE)))
        assertEquals(SoundEvent.KEY_ENTER, SoundEvent.forKey(key(KeyCode.ENTER)))
        assertEquals(SoundEvent.KEY_DELETE, SoundEvent.forKey(key(KeyCode.DELETE)))
        assertEquals(SoundEvent.KEY_DELETE, SoundEvent.forKey(key(KeyCode.DELETE_WORD)))
        assertEquals(SoundEvent.KEY_ALPHA, SoundEvent.forKey(TextKeyData.UNSPECIFIED))
    }

    @Test
    fun onlyDeleteHasARepeatSound() {
        assertEquals(SoundEvent.KEY_DELETE_REPEAT, SoundEvent.forKey(key(KeyCode.DELETE), repeated = true))
        assertEquals(SoundEvent.KEY_ALPHA, SoundEvent.forKey(key('a'.code), repeated = true))
        assertEquals(SoundEvent.KEY_MODIFIER, SoundEvent.forKey(key(KeyCode.ARROW_LEFT), repeated = true))
    }

    @Test
    fun controlKeysMapByRole() {
        assertEquals(SoundEvent.KEY_SHIFT, SoundEvent.forKey(key(KeyCode.SHIFT)))
        assertEquals(SoundEvent.KEY_CAPS_LOCK, SoundEvent.forKey(key(KeyCode.CAPS_LOCK)))
        for (code in listOf(KeyCode.CTRL, KeyCode.ALT, KeyCode.TAB, KeyCode.ESCAPE, KeyCode.ARROW_UP,
            KeyCode.MOVE_END_OF_LINE, KeyCode.TMUX_PREFIX)) {
            assertEquals(SoundEvent.KEY_MODIFIER, SoundEvent.forKey(key(code)), "code $code")
        }
        assertEquals(SoundEvent.LAYOUT_SWITCH, SoundEvent.forKey(key(KeyCode.VIEW_SYMBOLS)))
        assertEquals(SoundEvent.LAYOUT_SWITCH, SoundEvent.forKey(key(KeyCode.VIEW_CHARACTERS)))
        assertEquals(SoundEvent.PROFILE_SWITCH, SoundEvent.forKey(key(KeyCode.TOGGLE_KEYBOARD_PROFILE)))
        assertEquals(SoundEvent.VOICE_START, SoundEvent.forKey(key(KeyCode.VOICE_INPUT)))
        assertEquals(SoundEvent.CLIPBOARD_PASTE, SoundEvent.forKey(key(KeyCode.CLIPBOARD_PASTE)))
        assertEquals(SoundEvent.COMMAND, SoundEvent.forKey(key(KeyCode.IME_UI_MODE_MEDIA)))
        assertEquals(SoundEvent.COMMAND, SoundEvent.forKey(key(KeyCode.SETTINGS)))
    }

    @Test
    fun keyEventsAreMarkedFromKeyAndFeatureEventsAreNot() {
        assertTrue(SoundEvent.KEY_ALPHA.fromKey)
        assertTrue(SoundEvent.LAYOUT_SWITCH.fromKey)
        assertFalse(SoundEvent.CANDIDATE_PICK.fromKey)
        assertFalse(SoundEvent.DENY.fromKey)
    }

    @Test
    fun manifestParsesRotatesAndReportsUnknownNames() {
        val manifest = SoundPackManifest.parse(
            """{"id":"t","events":{"KEY_ALPHA":["a1.ogg","a2.ogg"],"KEY_ENTERR":["x.ogg"]},
               "gainDb":{"KEY_ALPHA":-3},"future":true}""",
        ).getOrThrow()
        assertEquals(-3f, manifest.gainFor(SoundEvent.KEY_ALPHA))
        assertEquals(0f, manifest.gainFor(SoundEvent.KEY_ENTER))
        assertEquals(setOf("KEY_ENTERR"), manifest.unknownEvents())

        val rotation = SoundClipRotation(manifest)
        assertEquals("a1.ogg", rotation.nextClip(SoundEvent.KEY_ALPHA))
        assertEquals("a2.ogg", rotation.nextClip(SoundEvent.KEY_ALPHA))
        assertEquals("a1.ogg", rotation.nextClip(SoundEvent.KEY_ALPHA))
        assertNull(rotation.nextClip(SoundEvent.KEY_ENTER))
    }

    @Test
    fun brokenManifestIsAFailureNotACrash() {
        assertTrue(SoundPackManifest.parse("{not json").isFailure)
    }
}
