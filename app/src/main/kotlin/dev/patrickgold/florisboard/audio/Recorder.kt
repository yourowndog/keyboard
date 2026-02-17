package dev.patrickgold.florisboard.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException

class Recorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    var isPaused: Boolean = false
        private set

    private fun voiceDir(): File {
        val dir = File(context.cacheDir, "voice_recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun start(): File {
        isPaused = false
        val timestamp = System.currentTimeMillis()
        val file = File(voiceDir(), "whisper_${timestamp}.mp4")
        outputFile = file

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            try {
                prepare()
                start()
            } catch (e: IOException) {
                // Handle exception
            }
        }
        return file
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isPaused) {
            try {
                mediaRecorder?.pause()
                isPaused = true
            } catch (e: Exception) {
                // Handle exception — recorder may not be in a valid state
            }
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isPaused) {
            try {
                mediaRecorder?.resume()
                isPaused = false
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    fun stop(): File {
        isPaused = false
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: IllegalStateException) {
            // Handle exception
        } finally {
            mediaRecorder = null
        }
        val file = outputFile ?: throw IOException("Output file not set")
        if (file.length() < 1024) {
            throw IOException("File size is less than 1024 bytes")
        }
        return file
    }

    fun maxAmplitude(): Int {
        if (isPaused) return 0
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
