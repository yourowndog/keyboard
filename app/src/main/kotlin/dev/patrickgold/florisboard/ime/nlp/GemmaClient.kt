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
    private const val SERVER_URL = "http://127.0.0.1:8081/completion"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private var persona: String = "You are a helpful assistant."

    fun loadPersona(context: android.content.Context) {
        try {
            persona = context.assets.open("ime/nlp/gemma_persona.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // Fallback if file missing
            e.printStackTrace()
        }
    }

    @Serializable
    data class CompletionRequest(
        val prompt: String,
        val n_predict: Int = 128, // Increased for slightly longer replies
        val temperature: Double = 0.8, // Slightly more creative
        val stop: List<String> = listOf("<end_of_turn>")
    )

    @Serializable
    data class CompletionResponse(
        val content: String
    )

    data class PromptConfig(
        val mode: Mode,
        val originalInput: String,
        val context: String? = null
    ) {
        enum class Mode {
            REPLY,
            REWRITE,
            CONTINUE
        }
    }

    fun complete(config: PromptConfig): String? {
        return try {
            val systemPrompt = "<start_of_turn>model\n$persona<end_of_turn>\n"
            
            val userPrompt = when (config.mode) {
                PromptConfig.Mode.REPLY -> {
                    "Context (Message to reply to):\n${config.context}\n\nTask: Draft a reply in my style."
                }
                PromptConfig.Mode.REWRITE -> {
                    "Original Text:\n${config.originalInput}\n\nTask: Rewrite/Fix this in my style."
                }
                PromptConfig.Mode.CONTINUE -> {
                    "Current Text:\n${config.originalInput}\n\nTask: Complete this thought in my style."
                }
            }

            val fullPrompt = "$systemPrompt<start_of_turn>user\n$userPrompt<end_of_turn>\n<start_of_turn>model\n"
            
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
                
                reply
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
