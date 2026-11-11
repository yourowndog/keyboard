package dev.patrickgold.florisboard.ime.voice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceCaptureLifecyclePolicyTest {
    @Test
    fun rotationStyleViewRestartPreservesActiveVoiceUi() {
        assertTrue(VoiceCaptureLifecyclePolicy.preserveVoiceUi(isRecording = true, isTranscribing = false))
        assertFalse(
            VoiceCaptureLifecyclePolicy.finalizeOnFinishInputView(
                finishingInput = false,
                isRecording = true,
            ),
        )
    }

    @Test
    fun lostInputWindowFinalizesRecording() {
        assertTrue(
            VoiceCaptureLifecyclePolicy.finalizeOnFinishInputView(
                finishingInput = true,
                isRecording = true,
            ),
        )
        assertTrue(VoiceCaptureLifecyclePolicy.finalizeOnWindowHidden(isRecording = true))
        assertTrue(VoiceCaptureLifecyclePolicy.finalizeOnServiceDestroyed(isRecording = true))
    }

    @Test
    fun transcriptionUiSurvivesViewReattachment() {
        assertTrue(VoiceCaptureLifecyclePolicy.preserveVoiceUi(isRecording = false, isTranscribing = true))
        assertFalse(VoiceCaptureLifecyclePolicy.finalizeOnWindowHidden(isRecording = false))
    }
}
