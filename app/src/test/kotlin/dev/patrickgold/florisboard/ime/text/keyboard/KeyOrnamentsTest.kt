package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyOrnamentsTest {
    @Test
    fun `ornaments are registered for exactly the keys they draw on`() {
        assertEquals(KeyOrnament.SHIFT_DIAL, KeyOrnament.forCode(KeyCode.SHIFT))
        assertEquals(KeyOrnament.DELETE_GRILLE, KeyOrnament.forCode(KeyCode.DELETE))
        assertEquals(KeyOrnament.SPACE_DOT_RUN, KeyOrnament.forCode(KeyCode.SPACE))
        assertEquals(KeyOrnament.ENTER_CORNER, KeyOrnament.forCode(KeyCode.ENTER))
        assertNull(KeyOrnament.forCode('a'.code))
    }

    @Test
    fun `shift dial reads off, one capital and caps lock`() {
        assertEquals(0f, shiftDialFill(InputShiftState.UNSHIFTED))
        assertEquals(0.5f, shiftDialFill(InputShiftState.SHIFTED_MANUAL))
        assertEquals(0.5f, shiftDialFill(InputShiftState.SHIFTED_AUTOMATIC))
        assertEquals(1f, shiftDialFill(InputShiftState.CAPS_LOCK))
    }
}
