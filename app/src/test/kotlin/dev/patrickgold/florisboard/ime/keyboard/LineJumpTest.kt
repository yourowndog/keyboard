package dev.patrickgold.florisboard.ime.keyboard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LineJumpTest {
    @Test
    fun `a text field keeps Ctrl+Home and Ctrl+End for start and end of the field`() {
        assertTrue(LineJump.addsCtrl(isRawInputEditor = false))
    }

    @Test
    fun `a terminal gets plain Home and End, which shells bind to start and end of line`() {
        assertFalse(LineJump.addsCtrl(isRawInputEditor = true))
    }
}
