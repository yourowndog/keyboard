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

package dev.patrickgold.florisboard.ime.theme.keyskin

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeySkinTest {
    @Test
    fun idsRoundTripThroughTheirStoredValue() {
        listOf(
            KeySkinId.None,
            KeySkinId.Pack("sfk-07"),
            KeySkinId.Swatch("swatch-021@320.png"),
        ).forEach { assertEquals(it, KeySkinId.parse(it.storedValue)) }
    }

    @Test
    fun unsafeOrUnknownIdsReadAsNone() {
        listOf(
            null, "", "builtin:grid", "pack:", "pack:../x", "pack:swatches", "pack:.hidden",
            "swatch:../../etc.png", "swatch:readme.txt",
        ).forEach { assertEquals(KeySkinId.None, KeySkinId.parse(it), "for $it") }
    }

    @Test
    fun fileNamesCarrySlotDensityAndNinePatch() {
        assertEquals(KeySkinFile("enter", 640, true), KeySkinFiles.parse("enter@640.9.png"))
        assertEquals(KeySkinFile("swatch-003", 320, false), KeySkinFiles.parse("swatch-003@320.png"))
        assertEquals(KeySkinFile("key", null, false), KeySkinFiles.parse("key.webp"))
        assertNull(KeySkinFiles.parse("Key Big.png"))
        assertNull(KeySkinFiles.parse("key@640.9.jpg"))
    }

    @Test
    fun specialKeysGetTheirOwnSlotThenFallBackToTheOrdinaryKey() {
        fun slots(code: Int, role: KeyRole, shift: Boolean = false, pressed: Boolean = false) =
            KeySkinSlot.candidatesFor(code, role, shift, pressed)
        assertEquals(listOf(KeySkinSlot.KEY), slots('a'.code, KeyRole.ALPHA))
        assertEquals(listOf(KeySkinSlot.KEY_PRESSED, KeySkinSlot.KEY), slots('a'.code, KeyRole.ALPHA, pressed = true))
        assertEquals(listOf(KeySkinSlot.ENTER, KeySkinSlot.KEY), slots(KeyCode.ENTER, KeyRole.ENTER, pressed = true))
        assertEquals(listOf(KeySkinSlot.DELETE, KeySkinSlot.KEY), slots(KeyCode.DELETE, KeyRole.DELETE))
        assertEquals(listOf(KeySkinSlot.SPACE, KeySkinSlot.KEY), slots(KeyCode.SPACE, KeyRole.SPACE))
        assertEquals(listOf(KeySkinSlot.SHIFT, KeySkinSlot.KEY), slots(KeyCode.SHIFT, KeyRole.SHIFT))
        assertEquals(
            listOf(KeySkinSlot.SHIFT_ON, KeySkinSlot.SHIFT, KeySkinSlot.KEY),
            slots(KeyCode.SHIFT, KeyRole.SHIFT, shift = true),
        )
        assertEquals(listOf(KeySkinSlot.ARROW_UP, KeySkinSlot.KEY), slots(KeyCode.ARROW_UP, KeyRole.MODIFIER))
        assertEquals(listOf(KeySkinSlot.KEY), slots(KeyCode.CTRL, KeyRole.MODIFIER))
    }

    @Test
    fun swatchesSkinCharacterKeysAndLeaveSpecialKeysToTheTheme() {
        fun slots(code: Int, role: KeyRole, pressed: Boolean = false) =
            KeySkinSlot.candidatesFor(code, role, isShiftOn = false, isPressed = pressed, coversSpecialKeys = false)
        assertEquals(listOf(KeySkinSlot.KEY), slots('q'.code, KeyRole.ALPHA))
        assertEquals(listOf(KeySkinSlot.KEY_PRESSED, KeySkinSlot.KEY), slots('7'.code, KeyRole.NUMBER, pressed = true))
        assertEquals(listOf(KeySkinSlot.KEY), slots(','.code, KeyRole.SYMBOL))
        listOf(
            KeyCode.ENTER to KeyRole.ENTER, KeyCode.DELETE to KeyRole.DELETE, KeyCode.SPACE to KeyRole.SPACE,
            KeyCode.SHIFT to KeyRole.SHIFT, KeyCode.CTRL to KeyRole.MODIFIER, KeyCode.VIEW_SYMBOLS to KeyRole.LAYOUT,
        ).forEach { (code, role) -> assertEquals(emptyList(), slots(code, role), "for $role") }
        assertEquals(false, KeySkinId.Swatch("swatch-001@640.png").coversSpecialKeys)
        assertEquals(true, KeySkinId.Pack("sfk-01").coversSpecialKeys)
    }

    @Test
    fun onlySpecialSlotsCarryTheirOwnGlyph() {
        assertEquals(false, KeySkinSlot.KEY.carriesOwnGlyph)
        assertEquals(false, KeySkinSlot.KEY_PRESSED.carriesOwnGlyph)
        assertEquals(true, KeySkinSlot.DELETE.carriesOwnGlyph)
        assertEquals(true, KeySkinSlot.ENTER.carriesOwnGlyph)
        assertEquals(true, KeySkinSlot.ARROW_LEFT.carriesOwnGlyph)
    }

    @Test
    fun lightTexturesGetDarkLabelsAndDarkOrClearOnesGetLight() {
        assertEquals(KeySkinLabels.DarkLabel, KeySkinLabels.labelColorFor(0.8f))
        assertEquals(KeySkinLabels.LightLabel, KeySkinLabels.labelColorFor(0.2f))
        // A fully transparent key (some packs draw only the label) shows the dark chassis.
        assertEquals(KeySkinLabels.LightLabel, KeySkinLabels.labelColorFor(0f))
    }

    @Test
    fun labelsReadLikeGalleryNames() {
        assertEquals("Sci-Fi 07", KeySkinFiles.labelFor("sfk-07"))
        assertEquals("Swatch 021", KeySkinFiles.labelFor("swatch-021@320.png"))
        assertEquals("my skin", KeySkinFiles.labelFor("my_skin"))
    }
}
