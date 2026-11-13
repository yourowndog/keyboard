package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MomentarySymbolsTest {
    @Test
    fun `only 123 on the letter page starts the peek`() {
        assertTrue(MomentarySymbols.starts(KeyCode.VIEW_SYMBOLS, KeyboardMode.CHARACTERS))
        assertFalse(MomentarySymbols.starts(KeyCode.VIEW_SYMBOLS, KeyboardMode.SYMBOLS))
        assertFalse(MomentarySymbols.starts(KeyCode.SHIFT, KeyboardMode.CHARACTERS))
    }

    @Test
    fun `lifting on a symbol types it, on a page switch does not`() {
        assertTrue(MomentarySymbols.types('('.code))
        assertTrue(MomentarySymbols.types(KeyCode.DELETE))
        assertFalse(MomentarySymbols.types(KeyCode.VIEW_CHARACTERS))
        assertFalse(MomentarySymbols.types(KeyCode.VIEW_SYMBOLS2))
    }
}
