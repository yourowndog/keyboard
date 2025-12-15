package dev.patrickgold.florisboard.ime.nlp

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.ln

/**
 * Client for SmolLM 135M model running on llama.cpp server.
 * Used for sentence-level scoring to rerank autocomplete suggestions.
 * 
 * Architecture:
 * - SmolLM (port 8080): Fast sentence scoring, runs constantly
 * - Gemma (port 8081): Text generation, on-demand
 * 
 * Scoring Strategy:
 * For each candidate, we construct a complete sentence and measure how
 * "natural" it is by looking at the model's confidence in continuing it.
 */
object SmolLMClient {
    private const val SERVER_URL = "http://127.0.0.1:8080/completion"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    // Short timeouts for scoring - needs to be fast
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Volatile
    private var serverAvailable: Boolean? = null
    private var lastHealthCheck = 0L
    private const val HEALTH_CHECK_INTERVAL_MS = 30000L  // Check every 30s

    @Serializable
    data class CompletionRequest(
        val prompt: String,
        val n_predict: Int = 1,
        val temperature: Double = 0.0,
        val cache_prompt: Boolean = true  // Cache for speed
    )

    @Serializable
    data class CompletionResponse(
        val content: String = "",
        val timings: Timings? = null
    )
    
    @Serializable
    data class Timings(
        val prompt_per_second: Double = 0.0,
        val predicted_per_second: Double = 0.0
    )

    /**
     * Score multiple sentences and return relative rankings.
     * Lower score = more natural/likely sentence.
     * 
     * @param contextPrefix Text before the candidates (e.g., "what are we ")
     * @param candidates List of candidate words to score
     * @return Map of candidate -> score (lower is better), or empty if server unavailable
     */
    fun scoreSentences(contextPrefix: String, candidates: List<String>): Map<String, Double> {
        if (!isAvailable()) return emptyMap()
        if (candidates.isEmpty()) return emptyMap()
        
        val results = mutableMapOf<String, Double>()
        
        for (candidate in candidates.take(5)) {  // Limit to top 5 for speed
            try {
                val sentence = "$contextPrefix$candidate"
                
                // Ask model to predict next token - faster responses = more confident
                // We use response time as a proxy for perplexity (faster = more natural)
                val requestBody = CompletionRequest(
                    prompt = sentence,
                    n_predict = 1,
                    temperature = 0.0,
                    cache_prompt = true
                )
                
                val body = json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder()
                    .url(SERVER_URL)
                    .post(body)
                    .build()

                val startTime = System.currentTimeMillis()
                client.newCall(request).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - startTime
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@use
                        val completionResponse = json.decodeFromString<CompletionResponse>(responseBody)
                        
                        // Use prompt processing speed as confidence signal
                        // Higher tokens/sec = model found it more "easy" = more natural
                        val promptSpeed = completionResponse.timings?.prompt_per_second ?: 100.0
                        
                        // Score: negative speed (so higher speed = lower score = better)
                        results[candidate] = -promptSpeed
                    }
                }
            } catch (e: Exception) {
                // Skip this candidate on error
            }
        }
        
        return results
    }
    
    /**
     * Rerank candidates based on SmolLM scoring.
     * Returns candidates in order from best to worst.
     * 
     * @param contextPrefix Text before candidates
     * @param candidates Current ranked candidates
     * @return Reranked candidates, or original if scoring fails
     */
    fun rerank(contextPrefix: String, candidates: List<String>): List<String> {
        if (candidates.size <= 1) return candidates
        
        val scores = scoreSentences(contextPrefix, candidates)
        if (scores.isEmpty()) return candidates
        
        // Sort by score (lower = better) while preserving unscored at end
        return candidates.sortedBy { candidate ->
            scores[candidate] ?: Double.MAX_VALUE
        }
    }
    
    /**
     * Check if the SmolLM server is available.
     * Caches result to avoid spamming health checks.
     */
    fun isAvailable(): Boolean {
        val now = System.currentTimeMillis()
        if (serverAvailable != null && (now - lastHealthCheck) < HEALTH_CHECK_INTERVAL_MS) {
            return serverAvailable!!
        }
        
        serverAvailable = try {
            val request = Request.Builder()
                .url("http://127.0.0.1:8080/health")
                .get()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
        lastHealthCheck = now
        return serverAvailable!!
    }
}
