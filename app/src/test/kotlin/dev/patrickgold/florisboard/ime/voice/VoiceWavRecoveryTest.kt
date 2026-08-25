package dev.patrickgold.florisboard.ime.voice

import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceWavRecoveryTest {
    private fun payloadHash(file: File): ByteArray = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes().copyOfRange(44, file.length().toInt()))

    @Test
    fun zeroHeaderIsRepairedWithoutChangingPcmPayload() {
        val dir = createTempDirectory("voice-wav-recovery").toFile()
        try {
            val file = File(dir, "whisper_1.wav")
            val pcm = ByteArray(96_000) { index -> (index * 31).toByte() }
            file.writeBytes(ByteArray(44) + pcm)
            val hashBefore = payloadHash(file)

            val result = VoiceWavRecovery.inspectAndRepair(file)

            assertTrue(result.repaired)
            assertEquals(1_000L, result.durationMs)
            assertContentEquals(hashBefore, payloadHash(file))
            assertEquals("RIFF", file.readBytes().copyOfRange(0, 4).toString(Charsets.US_ASCII))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun validWavIsLeftByteForByteUntouched() {
        val dir = createTempDirectory("voice-wav-valid").toFile()
        try {
            val file = File(dir, "whisper_2.wav")
            file.writeBytes(ByteArray(44) + ByteArray(9_600) { it.toByte() })
            VoiceWavRecovery.inspectAndRepair(file)
            val before = file.readBytes()

            val result = VoiceWavRecovery.inspectAndRepair(file)

            assertFalse(result.repaired)
            assertContentEquals(before, file.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun unknownNonzeroHeaderIsRefusedAndUntouched() {
        val dir = createTempDirectory("voice-wav-unknown").toFile()
        try {
            val file = File(dir, "whisper_3.wav")
            file.writeBytes(ByteArray(44) { 7 } + ByteArray(9_600) { it.toByte() })
            val before = file.readBytes()

            assertFailsWith<java.io.IOException> { VoiceWavRecovery.inspectAndRepair(file) }
            assertContentEquals(before, file.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }
}
