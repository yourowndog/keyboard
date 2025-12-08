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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val history = mutableListOf<Pair<String, String>>()
    private const val MAX_HISTORY = 4

    @Serializable
    data class CompletionRequest(
        val prompt: String,
        val n_predict: Int = 20,
        val temperature: Double = 0.7,
        val stop: List<String> = listOf("\n")
    )

    @Serializable
    data class CompletionResponse(
        val content: String
    )

    fun complete(inputText: String): String? {
        return try {
            val context = history.joinToString("\n") { (u, a) -> "User: $u\nAI: $a" }
            val fullPrompt = "<start_of_turn>user\nContext:\n$context\n\nCurrent Input: \"$inputText\"\n\nRespond naturally as a helpful assistant. Do not be too chatty.<end_of_turn>\n<start_of_turn>model\n"
            
            val requestBody = CompletionRequest(fullPrompt)
            val body = json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseBody = response.body?.string() ?: return null
                val completionResponse = json.decodeFromString<CompletionResponse>(responseBody)
                val reply = completionResponse.content.trim()
                
                if (history.size >= MAX_HISTORY) history.removeAt(0)
                history.add(inputText to reply)
                
                reply
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
