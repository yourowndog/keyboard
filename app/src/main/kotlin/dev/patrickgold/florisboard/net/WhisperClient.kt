package dev.patrickgold.florisboard.net

import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.audio.WavTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object WhisperClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    /**
     * A transcription plus everything the engine said about it.
     *
     * [raw] is the untouched response body. Word timings and the per-segment quality fields
     * (avg_logprob, compression_ratio, no_speech_prob) are the standard hallucination
     * detectors, and they are only obtainable at transcription time — regenerating them later
     * means paying for the audio a second time.
     */
    data class Transcription(val text: String, val raw: JSONObject)

    // Recordings queued before the switch to lossless capture are still .mp4, and the retry queue
    // will replay them, so both formats have to be sent with the right content type.
    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        else -> "audio/mp4"
    }

    /**
     * Produces the copy that actually gets uploaded.
     *
     * Returns the file to send and whether it is a temporary file the caller should delete.
     * A failure to downsample is not fatal: the original still transcribes fine as long as it
     * is under the size cap, so we fall back rather than lose the transcript.
     */
    private fun prepareUpload(file: File): Pair<File, Boolean> {
        if (file.extension.lowercase() != "wav") return file to false
        val temp = File(file.parentFile, "${file.nameWithoutExtension}.16k.wav")
        return try {
            if (WavTools.downsampleForUpload(file, temp) && temp.length() in 1..file.length()) {
                temp to true
            } else {
                temp.delete()
                file to false
            }
        } catch (_: Exception) {
            runCatching { temp.delete() }
            file to false
        }
    }

    suspend fun transcribe(file: File): Result<Transcription> = withContext(Dispatchers.IO) {
        var temp: File? = null
        try {
            if (BuildConfig.WHISPER_RELAY_URL.isBlank() || BuildConfig.WHISPER_RELAY_TOKEN.isBlank()) {
                return@withContext Result.failure(Exception("Whisper relay is not configured"))
            }

            val (upload, isTemp) = prepareUpload(file)
            if (isTemp) temp = upload

            if (upload.length() > WavTools.MAX_UPLOAD_BYTES) {
                return@withContext Result.failure(
                    Exception("Recording too long to transcribe (${upload.length() / (1024 * 1024)} MiB, limit 25 MiB)")
                )
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", upload.name, upload.asRequestBody(mimeTypeFor(upload).toMediaType()))
                // verbose_json is the only format that carries timestamps and quality fields,
                // and timestamp_granularities requires it.
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("timestamp_granularities[]", "word")
                .addFormDataPart("timestamp_granularities[]", "segment")
                .build()

            val request = Request.Builder()
                .url(BuildConfig.WHISPER_RELAY_URL)
                .header("Authorization", "Bearer ${BuildConfig.WHISPER_RELAY_TOKEN}")
                .header("Accept", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.take(200) ?: ""
                    return@withContext Result.failure(Exception("Relay error: ${response.code} $errorBody"))
                }
                val body = response.body?.string().orEmpty()
                val json = try {
                    JSONObject(body)
                } catch (_: Exception) {
                    // Older relay builds answered with bare text; still usable as a transcript.
                    return@withContext Result.success(Transcription(body, JSONObject().put("text", body)))
                }
                Result.success(Transcription(json.optString("text"), json))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            temp?.let { runCatching { it.delete() } }
        }
    }
}
