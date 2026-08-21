package dev.patrickgold.florisboard.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Environment
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Lossless voice capture for the OmniBoard corpus.
 *
 * Records uncompressed 16-bit PCM via [AudioRecord] and wraps it in a WAV container. The previous
 * implementation used MediaRecorder with AAC at 128 kbps, which is lossy and irreversible — fine for
 * playback, unusable as permanent training material for a voice model.
 *
 * Capture decisions are recorded in [lastCapture] so the caller can persist them alongside the audio.
 * Which microphone path the device actually gave us is not knowable after the fact, so it has to be
 * written down at capture time.
 */
class Recorder(private val context: Context) {

    /** What the device actually did, as opposed to what we asked for. Persist this with the audio. */
    data class CaptureMetadata(
        val audioSource: String,
        val unprocessedSupported: Boolean,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val aecDisabled: Boolean?,
        val nsDisabled: Boolean?,
        val agcDisabled: Boolean?,
        val peakAmplitude: Int,
        val clippedSamples: Long,
        val durationMs: Long,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("audio_source", audioSource)
            put("unprocessed_supported", unprocessedSupported)
            put("sample_rate", sampleRate)
            put("channels", channels)
            put("bits_per_sample", bitsPerSample)
            put("aec_disabled", aecDisabled ?: JSONObject.NULL)
            put("ns_disabled", nsDisabled ?: JSONObject.NULL)
            put("agc_disabled", agcDisabled ?: JSONObject.NULL)
            put("peak_amplitude", peakAmplitude)
            put("clipped_samples", clippedSamples)
            put("duration_ms", durationMs)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BITS_PER_SAMPLE = 16
        const val CHANNEL_COUNT = 1
        const val WAV_HEADER_BYTES = 44
        const val CLIP_THRESHOLD = 32_700
    }

    private var audioRecord: AudioRecord? = null
    private var readerThread: Thread? = null
    private var outputFile: File? = null

    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    // Tri-state, recorded at capture time because the effects are released before metadata is built:
    // true = we turned it off, false = present but refused to disable, null = device has no such effect.
    private var aecResult: Boolean? = null
    private var nsResult: Boolean? = null
    private var agcResult: Boolean? = null

    @Volatile private var isRecording: Boolean = false
    @Volatile private var lastAmplitude: Int = 0
    @Volatile private var peakAmplitude: Int = 0
    @Volatile private var clippedSamples: Long = 0L
    @Volatile private var pcmBytesWritten: Long = 0L

    private var startedAtMs: Long = 0L
    private var chosenSource: String = "UNKNOWN"
    private var unprocessedSupported: Boolean = false

    /** Populated by [stop]. Null until a recording has completed. */
    var lastCapture: CaptureMetadata? = null
        private set

    var isPaused: Boolean = false
        private set

    private fun voiceDir(): File {
        val vaultDir = File(Environment.getExternalStorageDirectory(), "Recordings/Whisper_Vault")
        if (vaultDir.exists() || vaultDir.mkdirs()) {
            return vaultDir
        }
        val fallbackDir = File(context.cacheDir, "voice_recordings")
        if (!fallbackDir.exists()) fallbackDir.mkdirs()
        return fallbackDir
    }

    /**
     * Picks the least-processed microphone path the device admits to supporting.
     *
     * UNPROCESSED may be passed on devices that do not advertise it, but the platform then makes no
     * guarantee about what processing is applied — which would silently mix processed and unprocessed
     * audio into the same corpus. So we only use it when the device says yes, and fall back to
     * VOICE_RECOGNITION, which at least skips automatic gain control.
     */
    private fun resolveAudioSource(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        unprocessedSupported = audioManager
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            ?.equals("true", ignoreCase = true) == true

        return if (unprocessedSupported) {
            chosenSource = "UNPROCESSED"
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            chosenSource = "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

    /**
     * Turns off any platform DSP attached to our capture session. Automatic gain control is the one
     * that matters most here: it flattens the difference between shouting and near-whispering, and
     * that difference is signal we want to keep.
     */
    private fun disableEffects(sessionId: Int) {
        aec = runCatching {
            AcousticEchoCanceler.create(sessionId)?.apply { enabled = false }
        }.getOrNull()
        ns = runCatching {
            NoiseSuppressor.create(sessionId)?.apply { enabled = false }
        }.getOrNull()
        agc = runCatching {
            AutomaticGainControl.create(sessionId)?.apply { enabled = false }
        }.getOrNull()

        aecResult = aec?.let { !it.enabled }
        nsResult = ns?.let { !it.enabled }
        agcResult = agc?.let { !it.enabled }
    }

    private fun releaseEffects() {
        runCatching { aec?.release() }
        runCatching { ns?.release() }
        runCatching { agc?.release() }
        aec = null
        ns = null
        agc = null
    }

    @Suppress("MissingPermission")
    fun start(): File {
        isPaused = false
        peakAmplitude = 0
        clippedSamples = 0L
        pcmBytesWritten = 0L
        lastAmplitude = 0
        lastCapture = null

        val file = File(voiceDir(), "whisper_${System.currentTimeMillis()}.wav")
        outputFile = file

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            throw IOException("Device rejected ${SAMPLE_RATE}Hz mono 16-bit capture")
        }
        // Oversized on purpose: an IME competes with whatever else is running, and a dropout in the
        // buffer is a permanent hole in the recording.
        val bufferBytes = minBuffer * 8

        val record = AudioRecord(resolveAudioSource(), SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferBytes)
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IOException("AudioRecord failed to initialise (source=$chosenSource)")
        }
        audioRecord = record
        disableEffects(record.audioSessionId)

        val stream = BufferedOutputStream(FileOutputStream(file), bufferBytes)
        stream.write(ByteArray(WAV_HEADER_BYTES)) // placeholder, patched in stop()

        isRecording = true
        startedAtMs = System.currentTimeMillis()
        record.startRecording()

        readerThread = Thread({ readLoop(record, stream, bufferBytes) }, "OmniBoardRecorder").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return file
    }

    private fun readLoop(record: AudioRecord, stream: BufferedOutputStream, bufferBytes: Int) {
        val samples = ShortArray(bufferBytes / 2)
        val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        try {
            while (isRecording) {
                val read = record.read(samples, 0, samples.size)
                if (read <= 0) continue

                var framePeak = 0
                for (i in 0 until read) {
                    val magnitude = abs(samples[i].toInt())
                    if (magnitude > framePeak) framePeak = magnitude
                    if (magnitude >= CLIP_THRESHOLD) clippedSamples++
                }
                lastAmplitude = framePeak
                if (framePeak > peakAmplitude) peakAmplitude = framePeak

                // Paused means "stop appending", not "stop reading" — we keep draining the hardware
                // buffer so resuming does not splice in a chunk of stale audio.
                if (isPaused) continue

                bytes.clear()
                for (i in 0 until read) bytes.putShort(samples[i])
                stream.write(bytes.array(), 0, read * 2)
                pcmBytesWritten += read * 2
            }
        } catch (_: Exception) {
            // Fall through: stop() still finalises whatever made it to disk.
        } finally {
            runCatching { stream.flush() }
            runCatching { stream.close() }
        }
    }

    fun pause() {
        if (isRecording) isPaused = true
    }

    fun resume() {
        if (isRecording) isPaused = false
    }

    fun stop(): File {
        val file = outputFile ?: throw IOException("Output file not set")
        val durationMs = if (startedAtMs == 0L) 0L else System.currentTimeMillis() - startedAtMs

        isRecording = false
        isPaused = false

        runCatching { readerThread?.join(2_000) }
        readerThread = null

        audioRecord?.let { record ->
            runCatching { if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop() }
            runCatching { record.release() }
        }
        audioRecord = null
        releaseEffects()

        writeWavHeader(file, pcmBytesWritten)

        lastCapture = CaptureMetadata(
            audioSource = chosenSource,
            unprocessedSupported = unprocessedSupported,
            sampleRate = SAMPLE_RATE,
            channels = CHANNEL_COUNT,
            bitsPerSample = BITS_PER_SAMPLE,
            aecDisabled = aecResult,
            nsDisabled = nsResult,
            agcDisabled = agcResult,
            peakAmplitude = peakAmplitude,
            clippedSamples = clippedSamples,
            durationMs = durationMs,
        )

        if (file.length() <= WAV_HEADER_BYTES) {
            throw IOException("Recording contained no audio")
        }
        return file
    }

    private fun writeWavHeader(file: File, pcmBytes: Long) {
        val byteRate = SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNEL_COUNT * BITS_PER_SAMPLE / 8
        val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((36 + pcmBytes).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                       // PCM subchunk size
        header.putShort(1)                      // format = PCM
        header.putShort(CHANNEL_COUNT.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(BITS_PER_SAMPLE.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmBytes.toInt())

        runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(header.array())
            }
        }
    }

    /** Peak magnitude of the most recent buffer, scaled 0..32767 to match the previous contract. */
    fun maxAmplitude(): Int {
        if (isPaused) return 0
        return lastAmplitude
    }
}
