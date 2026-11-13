package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.text.gestures.SwipeGesture.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShiftDialTest {
    @Test
    fun `slides pick alt right, escape left, ctrl up`() {
        assertEquals(ShiftDial.Choice.ALT, ShiftDial.choiceFor(Direction.RIGHT))
        assertEquals(ShiftDial.Choice.ESC, ShiftDial.choiceFor(Direction.LEFT))
        assertEquals(ShiftDial.Choice.CTRL, ShiftDial.choiceFor(Direction.UP))
    }

    @Test
    fun `diagonals and down pick nothing`() {
        assertNull(ShiftDial.choiceFor(Direction.UP_RIGHT))
        assertNull(ShiftDial.choiceFor(Direction.DOWN))
    }
}
