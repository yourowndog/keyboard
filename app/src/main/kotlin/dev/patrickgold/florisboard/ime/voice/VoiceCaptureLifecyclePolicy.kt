package dev.patrickgold.florisboard.ime.voice

/**
 * Keeps voice capture decisions independent from the disposable IME view lifecycle.
 *
 * A view restart (notably rotation) may reattach to an active capture. Losing the
 * input session/window, or destroying the service, must instead finalize the take
 * while the application-owned [dev.patrickgold.florisboard.ime.keyboard.KeyboardManager]
 * is still alive to persist and queue it.
 */
internal object VoiceCaptureLifecyclePolicy {
    fun preserveVoiceUi(isRecording: Boolean, isTranscribing: Boolean): Boolean =
        isRecording || isTranscribing

    fun finalizeOnFinishInputView(finishingInput: Boolean, isRecording: Boolean): Boolean =
        finishingInput && isRecording

    fun finalizeOnWindowHidden(isRecording: Boolean): Boolean = isRecording

    fun finalizeOnServiceDestroyed(isRecording: Boolean): Boolean = isRecording
}
