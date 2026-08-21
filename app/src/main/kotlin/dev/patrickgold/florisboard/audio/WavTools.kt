package dev.patrickgold.florisboard.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Helpers for turning an archived capture into something the transcription API will accept.
 *
 * The archive copy is 48 kHz lossless and stays that way. Whisper resamples everything to
 * 16 kHz before building its mel spectrogram, so the copy we upload is downsampled here
 * instead: it is the same audio the model would have seen anyway, at a third of the bytes.
 * That matters because OpenAI caps uploads at 25 MiB, which is only ~4.5 minutes of 48 kHz
 * PCM but ~13.5 minutes at 16 kHz.
 */
object WavTools {

    const val API_SAMPLE_RATE = 16_000
    const val MAX_UPLOAD_BYTES = 25 * 1024 * 1024

    /** Longest capture that still fits the upload cap once downsampled, in milliseconds. */
    val MAX_CAPTURE_MS: Long =
        ((MAX_UPLOAD_BYTES - 44).toLong() * 1000L) / (API_SAMPLE_RATE * 2L)

    private const val HEADER_BYTES = 44

    /**
     * Writes a 16 kHz mono copy of [source] to [target].
     *
     * Decimation is 3:1 and exact for our 48 kHz capture. Each output sample is the mean of
     * the three input samples it replaces, which is a crude but real low-pass — dropping two
     * of every three samples outright would fold everything above 8 kHz back down into the
     * speech band as aliasing noise, which would cost accuracy for no reason.
     *
     * Returns false if the source is not the mono 16-bit PCM we wrote, so the caller can fall
     * back to uploading the original rather than sending something malformed.
     */
    fun downsampleForUpload(source: File, target: File): Boolean {
        RandomAccessFile(source, "r").use { input ->
            if (input.length() <= HEADER_BYTES) return false

            val header = ByteArray(HEADER_BYTES)
            input.readFully(header)
            val hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val channels = hb.getShort(22).toInt()
            val sampleRate = hb.getInt(24)
            val bits = hb.getShort(34).toInt()

            if (channels != 1 || bits != 16) return false
            if (sampleRate % API_SAMPLE_RATE != 0) return false
            val factor = sampleRate / API_SAMPLE_RATE
            if (factor < 1) return false
            if (factor == 1) return source.copyTo(target, overwrite = true).exists()

            target.outputStream().buffered().use { out ->
                out.write(ByteArray(HEADER_BYTES)) // patched below

                val inBuf = ByteArray(factor * 2 * 4096)
                val outBuf = ByteBuffer.allocate(4096 * 2).order(ByteOrder.LITTLE_ENDIAN)
                var written = 0L

                while (true) {
                    val read = input.read(inBuf)
                    if (read <= 0) break
                    val frames = read / (factor * 2)
                    if (frames == 0) break

                    outBuf.clear()
                    for (f in 0 until frames) {
                        var sum = 0
                        for (k in 0 until factor) {
                            val idx = (f * factor + k) * 2
                            val lo = inBuf[idx].toInt() and 0xFF
                            val hi = inBuf[idx + 1].toInt()
                            sum += ((hi shl 8) or lo).toShort().toInt()
                        }
                        outBuf.putShort((sum / factor).toShort())
                    }
                    out.write(outBuf.array(), 0, frames * 2)
                    written += frames * 2
                }
                out.flush()
                patchHeader(target, written)
            }
        }
        return target.length() > HEADER_BYTES
    }

    private fun patchHeader(file: File, pcmBytes: Long) {
        val channels = 1
        val bits = 16
        val byteRate = API_SAMPLE_RATE * channels * bits / 8
        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((36 + pcmBytes).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(API_SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort((channels * bits / 8).toShort())
        header.putShort(bits.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmBytes.toInt())
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header.array())
        }
    }
}
