package dev.patrickgold.florisboard.ime.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceOutputModeTest {
    @Test
    fun cleanedSelectsCleanedTranscript() {
        assertEquals("clean", VoiceOutputMode.CLEANED.select("clean", "verbatim"))
    }

    @Test
    fun verbatimSelectsVerbatimWhenAvailable() {
        assertEquals("verbatim", VoiceOutputMode.VERBATIM.select("clean", "verbatim"))
    }

    @Test
    fun verbatimFallsBackToCleanedWhenUnavailable() {
        assertEquals("clean", VoiceOutputMode.VERBATIM.select("clean", null))
        assertEquals("clean", VoiceOutputMode.VERBATIM.select("clean", "  "))
    }
}
