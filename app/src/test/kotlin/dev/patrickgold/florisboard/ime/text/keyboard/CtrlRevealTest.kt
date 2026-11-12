package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CtrlRevealTest {
    @Test
    fun `plates name the five text editing chords in either case`() {
        assertEquals("ALL", CtrlReveal.plateLabel('a'.code))
        assertEquals("COPY", CtrlReveal.plateLabel('c'.code))
        assertEquals("CUT", CtrlReveal.plateLabel('x'.code))
        assertEquals("PASTE", CtrlReveal.plateLabel('V'.code))
        assertEquals("UNDO", CtrlReveal.plateLabel('Z'.code))
    }

    @Test
    fun `other letters and non letters get a blank plate`() {
        assertNull(CtrlReveal.plateLabel('q'.code))
        assertNull(CtrlReveal.plateLabel('1'.code))
        assertNull(CtrlReveal.plateLabel(KeyCode.SHIFT))
    }

    @Test
    fun `holding shift latches ctrl only on a board without a ctrl key`() {
        val textBoard = sequenceOf(KeyCode.SHIFT, 'a'.code, KeyCode.DELETE, KeyCode.ENTER)
        val codingBoard = textBoard + KeyCode.CTRL
        assertTrue(CtrlReveal.shiftHoldLatchesCtrl(textBoard))
        assertFalse(CtrlReveal.shiftHoldLatchesCtrl(codingBoard))
    }
}
