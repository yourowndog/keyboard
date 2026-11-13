package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.editor.OperationUnit
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.ime.text.gestures.SwipeGesture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShiftForwardDeleteTest {
    private fun engages(
        isMove: Boolean = true,
        direction: SwipeGesture.Direction = SwipeGesture.Direction.RIGHT,
        isStillOnShift: Boolean = true,
        hasLongPressed: Boolean = false,
        isRawInputEditor: Boolean = false,
    ) = ShiftForwardDelete.engages(isMove, direction, isStillOnShift, hasLongPressed, isRawInputEditor)

    @Test
    fun `a drag right from shift engages`() {
        assertTrue(engages())
    }

    @Test
    fun `other drags, flings, held shift and terminals do not`() {
        assertFalse(engages(direction = SwipeGesture.Direction.UP_RIGHT))
        assertFalse(engages(direction = SwipeGesture.Direction.LEFT))
        assertFalse(engages(isMove = false))
        assertFalse(engages(isStillOnShift = false))
        assertFalse(engages(hasLongPressed = true))
        assertFalse(engages(isRawInputEditor = true))
    }

    @Test
    fun `selection mirrors the delete swipe`() {
        // Delete selects -absUnitCountX - 1 characters, so 2 units left selects one; right is the mirror.
        assertEquals(0, ShiftForwardDelete.selectedUnits(1, OperationUnit.CHARACTERS))
        assertEquals(1, ShiftForwardDelete.selectedUnits(2, OperationUnit.CHARACTERS))
        assertEquals(5, ShiftForwardDelete.selectedUnits(6, OperationUnit.CHARACTERS))
        assertEquals(2, ShiftForwardDelete.selectedUnits(6, OperationUnit.WORDS))
        assertEquals(0, ShiftForwardDelete.selectedUnits(-4, OperationUnit.CHARACTERS))
    }

    @Test
    fun `unit follows the delete swipe setting`() {
        assertEquals(OperationUnit.WORDS, ShiftForwardDelete.unitFor(SwipeAction.DELETE_WORDS_PRECISELY))
        assertEquals(OperationUnit.WORDS, ShiftForwardDelete.unitFor(SwipeAction.SELECT_WORDS_PRECISELY))
        assertEquals(OperationUnit.CHARACTERS, ShiftForwardDelete.unitFor(SwipeAction.DELETE_CHARACTERS_PRECISELY))
        assertEquals(OperationUnit.CHARACTERS, ShiftForwardDelete.unitFor(SwipeAction.DELETE_WORD))
    }

    @Test
    fun `cursor light walks with the cursor and wraps`() {
        assertEquals(4, cursorTravelDot(1))
        assertEquals(2, cursorTravelDot(-1))
        assertEquals(3, cursorTravelDot(8))
        assertEquals(3, cursorTravelDot(-8))
    }
}
