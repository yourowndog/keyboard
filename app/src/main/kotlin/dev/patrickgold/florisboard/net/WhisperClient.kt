package dev.patrickgold.florisboard.net

import dev.patrickgold.florisboard.BuildConfig
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
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    // Recordings queued before the switch to lossless capture are still .mp4, and the retry queue will
    // replay them, so both formats have to be sent with the right content type.
    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        else -> "audio/mp4"
    }

    suspend fun transcribe(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (BuildConfig.WHISPER_RELAY_URL.isBlank() || BuildConfig.WHISPER_RELAY_TOKEN.isBlank()) {
                return@withContext Result.failure(Exception("Whisper relay is not configured"))
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody(mimeTypeFor(file).toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(BuildConfig.WHISPER_RELAY_URL)
                .header("Authorization", "Bearer ${BuildConfig.WHISPER_RELAY_TOKEN}")
                .header("Accept", "application/json")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val text = responseBody?.let { JSONObject(it).getString("text") } ?: ""
                    Result.success(text)
                } else {
                    val errorBody = response.body?.string()?.take(200) ?: ""
                    Result.failure(Exception("Relay error: ${response.code} $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
