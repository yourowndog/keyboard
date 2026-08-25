package dev.patrickgold.florisboard.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WavToolsTest {
    @Test
    fun oversizedUploadIsSplitWithoutDroppingOrReorderingPcm() {
        val dir = createTempDirectory("wav-tools-split").toFile()
        try {
            val source = File(dir, "voice.16k.wav")
            val pcm = ByteArray(16_000 * 2 * 3) { index -> ((index % 251) + 1).toByte() }
            // Give each search range an unmistakable quiet point before its hard size boundary.
            pcm.fill(0, 24_000, 27_200)
            pcm.fill(0, 51_200, 54_400)
            write16kWav(source, pcm)
            val sourceBefore = source.readBytes()
            val maxBytes = 44 + 16_000 * 2

            val chunks = WavTools.splitForUpload(source, maxBytes)

            assertTrue(chunks.size >= 3)
            assertTrue(chunks.all { it.file.length() <= maxBytes })
            assertEquals(0L, chunks.first().offsetMs)
            val reassembled = chunks.flatMap { chunk ->
                chunk.file.readBytes().drop(44)
            }.toByteArray()
            assertContentEquals(pcm, reassembled)
            assertContentEquals(sourceBefore, source.readBytes())
            chunks.forEach { it.file.delete() }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun write16kWav(file: File, pcm: ByteArray) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(16_000)
            putInt(32_000)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }.array()
        file.writeBytes(header + pcm)
    }
}
