package dev.patrickgold.florisboard.ime.nlp

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.ln
import dev.patrickgold.florisboard.ime.nlp.shared.BigramTable

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
        val typedNoApos = originalInput.replace("'", "").lowercase()
        val typedLower = originalInput.lowercase()
        val scoredCandidates = mutableListOf<WordSuggestionCandidate>()

        for ((word, dist) in candidates) {
            val wordNoApos = word.replace("'", "")
            val wordLower = word.lowercase()
            
            // Base Score: Log frequency
            val baseScore = (unigramLogFreq[word] ?: 0.0) * weights.base
            
            // Bigram Bonus: Context from shared BigramTable
            val bigramBonus = bigramBonus(prevWord, word) * weights.bigram
            
            // Penalty: Use SymSpellManager's spatialCost which handles transpositions
            // This ensures consistent scoring between autocorrect and suggestions
            val spatialPenalty = SymSpellManager.spatialCost(typedLower, wordLower)
            val distPenalty = (dist + spatialPenalty) * weights.touchPenalty
            
            val userBonus = (userBoosts[word] ?: 0.0) * weights.user

            var total = baseScore + bigramBonus + userBonus - distPenalty

            // Perfect match bonus: Boost exact matches that are valid words
            // CRITICAL: Only apply if word is in dictionary OR is a known proper noun!
            // This ensures "sam" (valid override) beats "same", but "teh" still corrects to "the".
            if (typedNoApos == wordNoApos) {
                if (unigramLogFreq.containsKey(word) || SymSpellManager.PROPER_OVERRIDES.contains(word)) {
                    total += 25.0
                }
            }

            // Note: Casing is NOT applied here - it's delegated to SymSpellManager.applyPredictedCasing()
            // which is called by LatinLanguageProvider after ranking.
            val candidate = WordSuggestionCandidate(
                text = word,
                confidence = total,
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
     * 
     * Scoring Strategy:
     * Currently using n-gram scoring (frequency + bigram).
     * SmolLM integration is disabled until we have a model trained for word scoring.
     *
     * @param word The candidate word to score
     * @param prevWord Previous word for bigram context
     * @param editDistance Edit distance penalty (0.0 for swipe which has its own geometry penalty)
     * @return Score where higher is better
     */
    override fun scoreWord(word: String, prevWord: String?, editDistance: Double): Double {
        val wordLower = word.lowercase()
        
        // N-gram scoring: frequency + bigram context
        val baseScore = (unigramLogFreq[wordLower] ?: 0.0) * weights.base
        val bigramBonus = bigramBonus(prevWord, word) * weights.bigram
        val userBonus = (userBoosts[wordLower] ?: 0.0) * weights.user
        val distPenalty = editDistance * weights.touchPenalty
        
        return baseScore + bigramBonus + userBonus - distPenalty
    }

    /**
     * Calculate bigram bonus for context-aware ranking.
     * Uses shared [BigramTable] singleton for data.
     */
    private fun bigramBonus(prev: String?, cand: String): Double {
        return BigramTable.get()?.bonus(prev, cand) ?: 0.0
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

