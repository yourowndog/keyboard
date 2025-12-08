package dev.patrickgold.florisboard.ime.nlp

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object GemmaClient {
    private const val SERVER_URL = "http://127.0.0.1:8080/completion"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class CompletionRequest(
        val prompt: String,
        val n_predict: Int = 50,
        val temperature: Double = 0.7,
        val stop: List<String> = listOf("\n")
    )

    @Serializable
    data class CompletionResponse(
        val content: String
    )

    fun complete(prompt: String): String? {
        return try {
            val requestBody = CompletionRequest(prompt)
            val body = json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseBody = response.body?.string() ?: return null
                val completionResponse = json.decodeFromString<CompletionResponse>(responseBody)
                completionResponse.content
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
