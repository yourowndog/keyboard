package dev.patrickgold.florisboard.ime.nlp

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Client for SmolLM 135M model running on llama.cpp server.
 * Used for word scoring in autocorrect and swipe ranking.
 * 
 * Architecture:
 * - SmolLM (port 8080): Fast word scoring, runs constantly
 * - Gemma (port 8081): Text generation, on-demand
 */
object SmolLMClient {
    private const val SERVER_URL = "http://127.0.0.1:8080/completion"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    // Shorter timeouts for scoring - needs to be fast
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class CompletionRequest(
        val prompt: String,
        val n_predict: Int = 1,  // Only need 1 token for scoring
        val temperature: Double = 0.0,  // Deterministic for scoring
        val logprobs: Int = 1  // Get log probabilities
    )

    @Serializable
    data class CompletionResponse(
        val content: String = "",
        val completion_probabilities: List<TokenProb>? = null
    )
    
    @Serializable
    data class TokenProb(
        val content: String = "",
        val probs: List<ProbEntry>? = null
    )
    
    @Serializable
    data class ProbEntry(
        val tok_str: String = "",
        val prob: Double = 0.0
    )

    /**
     * Score a word given context. Returns higher score for more likely words.
     * 
     * @param context The preceding text (e.g., "I want to say ")
     * @param word The candidate word to score (e.g., "hello")
     * @return Log probability score (higher = more likely), or null if server unavailable
     */
    fun scoreWord(context: String, word: String): Double? {
        return try {
            // Prompt: context + start of word, ask model to complete
            val prompt = "$context$word"
            
            val requestBody = CompletionRequest(
                prompt = prompt,
                n_predict = 1,
                temperature = 0.0,
                logprobs = 1
            )
            
            val body = json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseBody = response.body?.string() ?: return null
                val completionResponse = json.decodeFromString<CompletionResponse>(responseBody)
                
                // Extract log probability from response
                // Higher probability = more likely continuation = better word
                completionResponse.completion_probabilities?.firstOrNull()?.probs?.firstOrNull()?.prob
            }
        } catch (e: Exception) {
            // Server not running or timeout - fall back to n-gram scoring
            null
        }
    }
    
    /**
     * Batch score multiple words for efficiency.
     * Returns map of word -> score.
     */
    fun scoreWords(context: String, words: List<String>): Map<String, Double> {
        val results = mutableMapOf<String, Double>()
        for (word in words) {
            scoreWord(context, word)?.let { score ->
                results[word] = score
            }
        }
        return results
    }
    
    /**
     * Check if the SmolLM server is available.
     */
    fun isAvailable(): Boolean {
        return try {
            val request = Request.Builder()
                .url("http://127.0.0.1:8080/health")
                .get()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
