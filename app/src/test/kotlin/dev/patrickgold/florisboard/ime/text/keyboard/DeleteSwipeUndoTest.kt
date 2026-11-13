package dev.patrickgold.florisboard.ime.text.keyboard

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeleteSwipeUndoTest {
    @BeforeTest
    fun reset() = DeleteSwipeUndo.clear()

    @Test
    fun `a straight swipe up arms the undo, a sideways or short one does not`() {
        assertTrue(DeleteSwipeUndo.isUndoGesture(absUnitCountX = 0, absUnitCountY = -4))
        assertTrue(DeleteSwipeUndo.isUndoGesture(absUnitCountX = -1, absUnitCountY = -7))
        assertFalse(DeleteSwipeUndo.isUndoGesture(absUnitCountX = 0, absUnitCountY = -3))
        assertFalse(DeleteSwipeUndo.isUndoGesture(absUnitCountX = -3, absUnitCountY = -6))
        assertFalse(DeleteSwipeUndo.isUndoGesture(absUnitCountX = 0, absUnitCountY = 6))
    }

    @Test
    fun `the last deletion comes back once`() {
        DeleteSwipeUndo.remember("hello", isForward = true)
        assertEquals(5, DeleteSwipeUndo.restoreLength)
        assertEquals("hello" to true, DeleteSwipeUndo.take())
        assertFalse(DeleteSwipeUndo.hasText)
        assertNull(DeleteSwipeUndo.take())
    }

    @Test
    fun `empty deletions are not remembered and clear forgets`() {
        DeleteSwipeUndo.remember("world", isForward = false)
        DeleteSwipeUndo.remember("", isForward = true)
        assertEquals("world" to false, DeleteSwipeUndo.take())
        DeleteSwipeUndo.remember("x", isForward = false)
        DeleteSwipeUndo.clear()
        assertNull(DeleteSwipeUndo.take())
    }
}
