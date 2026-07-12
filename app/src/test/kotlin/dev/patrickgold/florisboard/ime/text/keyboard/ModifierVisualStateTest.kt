package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.KeyboardState
import dev.patrickgold.florisboard.ime.keyboard.shouldClearTmuxPrefixVisualState
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModifierVisualStateTest {
    @Test
    fun `tmux latch is active until the next non-tmux key`() {
        assertFalse(shouldClearTmuxPrefixVisualState(isActive = false, nextKeyCode = 'a'.code))
        assertFalse(shouldClearTmuxPrefixVisualState(isActive = true, nextKeyCode = KeyCode.TMUX_PREFIX))
        assertTrue(shouldClearTmuxPrefixVisualState(isActive = true, nextKeyCode = 'a'.code))
    }

    @Test
    fun `tmux state flag is independent from Ctrl`() {
        val state = KeyboardState.new()

        state.isTmuxPrefixActive = true

        assertTrue(state.isTmuxPrefixActive)
        assertFalse(state.isCtrlPressed)
        assertFalse(state.isCtrlLocked)
        assertTrue(state.snapshot().isTmuxPrefixActive)
    }

    @Test
    fun `tmux latch selects active toggle styling`() {
        assertTrue(
            isTextKeyToggleActive(
                code = KeyCode.TMUX_PREFIX,
                isCtrlPressed = false,
                isCtrlLocked = false,
                isTmuxPrefixActive = true,
                isNumberRowEnabled = false,
                isDevRowEnabled = false,
            ),
        )
    }
}
