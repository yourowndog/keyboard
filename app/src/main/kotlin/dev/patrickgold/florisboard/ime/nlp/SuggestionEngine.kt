package dev.patrickgold.florisboard.ime.nlp

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.ln
import dev.patrickgold.florisboard.ime.nlp.shared.BigramTable
import dev.patrickgold.florisboard.ime.nlp.shared.CandidateScorer

data class SuggestionRequest(
    val typed: String,
    val prevWord: String? = null,
    val maxSuggestions: Int = 5,
)

interface SuggestionEngine {
    fun suggest(request: SuggestionRequest): List<SuggestionCandidate>
    fun rank(candidates: List<Pair<String, Double>>, originalInput: String, prevWord: String?): List<SuggestionCandidate> {
        return emptyList() // Default implementation for engines that don't support ranking
    }
    /**
     * Score a single word given context. Used by both tap and swipe for unified ranking.
     * @param word The candidate word to score
     * @param prevWord Previous word for bigram context (nullable)
     * @param editDistance Edit distance penalty (0.0 for swipe, actual distance for tap)
     * @return Score where higher is better
     */
    fun scoreWord(word: String, prevWord: String?, editDistance: Double = 0.0): Double {
        return 0.0 // Default implementation
    }
    fun predictNext(prevWord: String?, max: Int = 3): List<String>
    fun notifySuggestionAccepted(candidate: SuggestionCandidate) { }
    fun notifySuggestionReverted(candidate: SuggestionCandidate) { }
    fun generateAiCompletion(config: GemmaClient.PromptConfig): String? { return null }
}

/**
 * Lightweight n-gram based scorer for ranking spelling candidates.
 *
 * ## Architecture Role:
 * This engine is the "Judge" in the Brain Transplant pattern:
 * - SymSpellManager retrieves candidates (the "Retriever")
 * - NgramSuggestionEngine ranks them by frequency + context (this class)
 * - SymSpellManager applies casing (the "Caser")
 *
 * ## Data Sources:
 * - Unigrams: Loaded from TSV file at construction time (for frequency scoring)
 * - Bigrams: Provided by shared [BigramTable] singleton (for context scoring)
 *
 * ## Future:
 * Inputs/outputs are primitive/flat so we can swap in a JNI/Rust-backed engine
 * without touching UI or Android-specific code.
 */
class NgramSuggestionEngine(
    internal val unigramLogFreq: Map<String, Double>,
    private val userBoosts: Map<String, Double> = emptyMap(),
    private val weights: Weights = Weights(),
) : SuggestionEngine {

    data class Weights(
        val base: Double = 1.0,
        val bigram: Double = 1.0,
        val touchPenalty: Double = 1.0,
        val user: Double = 1.0,
    )

    // Note: suggest() is not used in production - ranking is done via rank()
    // called by LatinLanguageProvider after SymSpellManager retrieves candidates.
    override fun suggest(request: SuggestionRequest): List<SuggestionCandidate> = emptyList()

    /**
     * Rank spelling candidates by frequency, context, and spatial proximity.
     * Uses unified CandidateScorer for consistent behavior with autocorrect.
     *
     * @param candidates List of (term, editDistance) pairs from SymSpellManager
     * @param originalInput What the user actually typed
     * @param prevWord The word before the cursor (for bigram context)
     * @return Ranked list of suggestions, best first
     */
    override fun rank(
        candidates: List<Pair<String, Double>>,
        originalInput: String,
        prevWord: String?
    ): List<SuggestionCandidate> {
        val typedLower = originalInput.lowercase()
        val scoredCandidates = mutableListOf<WordSuggestionCandidate>()

        for ((word, dist) in candidates) {
            val wordLower = word.lowercase()
            
            // Get frequency for this word
            val frequency = unigramLogFreq[wordLower] ?: 0.0
            
            // Use unified scorer (returns penalty-based score: lower = better)
            val penaltyScore = CandidateScorer.score(
                typed = typedLower,
                candidate = wordLower,
                editDistance = dist,
                prevWord = prevWord?.lowercase(),
                isInUserDict = userBoosts.containsKey(wordLower),
                frequency = frequency,
            )
            
            // Convert to confidence (higher = better) for UI display
            val confidence = CandidateScorer.toConfidence(penaltyScore)
            
            // Apply user boosts on top
            val totalConfidence = confidence + (userBoosts[wordLower] ?: 0.0) * weights.user

            val candidate = WordSuggestionCandidate(
                text = word,
                confidence = totalConfidence,
                isEligibleForAutoCommit = false,
                sourceProvider = null,
            )
            scoredCandidates.add(candidate)
        }

        val ranked = scoredCandidates
            .sortedByDescending { it.confidence }
            .toMutableList()

        // Auto-commit: Mark top suggestion for auto-commit if different from input
        if (ranked.isNotEmpty()) {
            val top = ranked[0]
            if (!top.text.toString().equals(originalInput, ignoreCase = true)) {
                ranked[0] = top.copy(isEligibleForAutoCommit = true)
            }
        }

        return ranked
    }

    override fun predictNext(prevWord: String?, max: Int): List<String> {
        return BigramTable.get()?.predictNext(prevWord, max) ?: emptyList()
    }

    override fun generateAiCompletion(config: GemmaClient.PromptConfig): String? {
        return GemmaClient.complete(config)
    }

    override fun notifySuggestionAccepted(candidate: SuggestionCandidate) {
        // No-op: hook for future learning.
    }

    override fun notifySuggestionReverted(candidate: SuggestionCandidate) {
        // No-op: stateless engine; nothing to revert.
    }

    /**
     * Score a single word given context. Unified scoring for tap and swipe.
     * Uses CandidateScorer for consistency with autocorrect.
     *
     * @param word The candidate word to score
     * @param prevWord Previous word for bigram context
     * @param editDistance Edit distance penalty (0.0 for swipe which has its own geometry penalty)
     * @return Score where higher is better (confidence-based)
     */
    override fun scoreWord(word: String, prevWord: String?, editDistance: Double): Double {
        val wordLower = word.lowercase()
        val frequency = unigramLogFreq[wordLower] ?: 0.0
        
        // Use unified scorer (returns penalty-based: lower = better)
        val penaltyScore = CandidateScorer.score(
            typed = wordLower, // For swipe, typed = candidate (no typos, just path matching)
            candidate = wordLower,
            editDistance = editDistance,
            prevWord = prevWord?.lowercase(),
            isInUserDict = userBoosts.containsKey(wordLower),
            frequency = frequency,
        )
        
        // Convert to confidence (higher = better)
        return CandidateScorer.toConfidence(penaltyScore)
    }

    companion object {
        private const val TAG = "NgramSuggestionEngine"

        /**
         * Create an engine from a unigram TSV stream.
         * Bigram data is provided by the shared [BigramTable] singleton.
         */
        fun fromStreams(
            unigramStream: InputStream,
            userBoosts: Map<String, Double> = emptyMap(),
            weights: Weights = Weights(),
        ): NgramSuggestionEngine {
            val unigrams = loadUnigrams(unigramStream)
            return NgramSuggestionEngine(unigrams, userBoosts, weights)
        }

        private fun loadUnigrams(stream: InputStream): Map<String, Double> {
            val map = mutableMapOf<String, Double>()
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size < 2) return@forEach
                    val word = parts[0].lowercase()
                    val freq = parts[1].toDoubleOrNull() ?: return@forEach
                    map[word] = ln(freq + 1.0) // store in log-space
                }
            }
            return map
        }
    }
}

