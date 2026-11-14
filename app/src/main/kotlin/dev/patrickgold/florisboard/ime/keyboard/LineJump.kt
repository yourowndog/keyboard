package dev.patrickgold.florisboard.ime.keyboard

/**
 * How the « and » keys (MOVE_START_OF_LINE, MOVE_END_OF_LINE) send Home and End.
 *
 * In an ordinary text field they send Ctrl+Home and Ctrl+End, which Android's text views read as
 * "jump to the start or end of the whole field". That stays as it is.
 *
 * A terminal is different. Termux turns the key event into an escape sequence, and Ctrl+Home
 * becomes `ESC[1;5H`, which bash, zsh and readline leave unbound, so the keys did nothing
 * there. Plain Home and End become `ESC[H` / `ESC[F` (or `ESCOH` / `ESCOF` in application
 * cursor mode), which every shell and most full-screen programs read as start and end of line.
 * Terminals report a null input type, so [isRawInputEditor] is how they are recognised.
 *
 * A Ctrl the user latched on purpose still goes out with the key in both cases, because `meta()`
 * adds the latched Ctrl itself.
 */
internal object LineJump {
    fun addsCtrl(isRawInputEditor: Boolean): Boolean = !isRawInputEditor
}
