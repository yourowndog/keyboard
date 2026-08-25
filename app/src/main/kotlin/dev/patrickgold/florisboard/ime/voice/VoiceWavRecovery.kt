package dev.patrickgold.florisboard.ime.voice

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.text.Charsets

/** Repairs only the 44-byte header written as a placeholder by Recorder.start(). */
internal object VoiceWavRecovery {
    private const val HEADER_BYTES = 44L
    private const val SAMPLE_RATE = 48_000
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)

    data class Result(val durationMs: Long, val repaired: Boolean)

    fun inspectAndRepair(file: File): Result {
        if (!file.isFile || file.length() <= HEADER_BYTES) {
            throw IOException("WAV has no recoverable PCM payload")
        }
        val pcmBytes = file.length() - HEADER_BYTES
        if (file.length() - 8L > UInt.MAX_VALUE.toLong() || pcmBytes % 2L != 0L) {
            throw IOException("WAV payload length is invalid")
        }

        val header = ByteArray(HEADER_BYTES.toInt())
        RandomAccessFile(file, "r").use { it.readFully(header) }
        val riff = header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII))
        val wave = header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray(Charsets.US_ASCII))
        val sizes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val canonicalPcm = riff && wave &&
            header.copyOfRange(12, 16).contentEquals("fmt ".toByteArray(Charsets.US_ASCII)) &&
            sizes.getInt(16) == 16 &&
            sizes.getShort(20).toInt() == 1 &&
            sizes.getShort(22).toInt() == CHANNELS &&
            sizes.getInt(24) == SAMPLE_RATE &&
            sizes.getInt(28) == BYTES_PER_SECOND &&
            sizes.getShort(32).toInt() == CHANNELS * BITS_PER_SAMPLE / 8 &&
            sizes.getShort(34).toInt() == BITS_PER_SAMPLE &&
            header.copyOfRange(36, 40).contentEquals("data".toByteArray(Charsets.US_ASCII))
        val sizesMatch = canonicalPcm &&
            sizes.getInt(4).toUInt().toLong() == file.length() - 8L &&
            sizes.getInt(40).toUInt().toLong() == pcmBytes
        if (sizesMatch) {
            return Result(durationMs = pcmBytes * 1_000L / BYTES_PER_SECOND, repaired = false)
        }

        val isPlaceholder = header.all { it == 0.toByte() }
        if (!isPlaceholder && !canonicalPcm) {
            throw IOException("Unrecognized WAV header; refusing to alter audio")
        }

        val repairedHeader = ByteBuffer.allocate(HEADER_BYTES.toInt()).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt((file.length() - 8L).toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(CHANNELS.toShort())
            putInt(SAMPLE_RATE)
            putInt(BYTES_PER_SECOND)
            putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort())
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmBytes.toInt())
        }.array()
        RandomAccessFile(file, "rw").use {
            it.seek(0L)
            it.write(repairedHeader)
            it.fd.sync()
        }
        return Result(durationMs = pcmBytes * 1_000L / BYTES_PER_SECOND, repaired = true)
    }
}
