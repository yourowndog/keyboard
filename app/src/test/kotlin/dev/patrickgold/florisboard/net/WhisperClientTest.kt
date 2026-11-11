package dev.patrickgold.florisboard.net

import kotlin.test.Test
import kotlin.test.assertEquals

class WhisperClientTest {
    @Test
    fun rateLimitRetryHonorsIntegerRetryAfter() {
        assertEquals(30_000L, WhisperClient.retryDelayMillis("30", retryIndex = 0))
        assertEquals(0L, WhisperClient.retryDelayMillis("0", retryIndex = 3))
    }

    @Test
    fun rateLimitRetryFallsBackToBoundedExponentialDelay() {
        assertEquals(1_000L, WhisperClient.retryDelayMillis(null, retryIndex = 0))
        assertEquals(4_000L, WhisperClient.retryDelayMillis("not-a-number", retryIndex = 2))
        assertEquals(300_000L, WhisperClient.retryDelayMillis("999999", retryIndex = 0))
    }
}
