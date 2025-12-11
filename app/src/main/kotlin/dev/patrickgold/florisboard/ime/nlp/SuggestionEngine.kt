package dev.patrickgold.florisboard.ime.nlp

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

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
    fun predictNext(prevWord: String?, max: Int = 3): List<String>
    fun notifySuggestionAccepted(candidate: SuggestionCandidate) { }
    fun notifySuggestionReverted(candidate: SuggestionCandidate) { }
    fun generateAiCompletion(config: GemmaClient.PromptConfig): String? { return null }
}

/**
 * Lightweight n-gram based scorer (SymSpell-free).
 *
 * Inputs/outputs are primitive/flat so we can swap in a JNI/Rust-backed engine later
 * without touching UI or Android-specific code.
 */
 class NgramSuggestionEngine(
    internal val unigramLogFreq: Map<String, Double>,
    private val bigramTable: Map<String, Map<String, Int>>,
    private val bigramMaxByPrev: Map<String, Int>,
    private val userBoosts: Map<String, Double> = emptyMap(),
    private val weights: Weights = Weights(),
) : SuggestionEngine {

    data class Weights(
        val base: Double = 1.0,
        val bigram: Double = 1.0,
        val touchPenalty: Double = 1.0,
        val user: Double = 1.0,
    )

    private val neighborMap: Map<Char, String> = mapOf(
        'q' to "wa", 'w' to "qase", 'e' to "wsdfr", 'r' to "edft", 't' to "rfgy", 'y' to "tghu",
        'u' to "yhij", 'i' to "ujko", 'o' to "iklp", 'p' to "ol",
        'a' to "qwsz", 's' to "qweadzx", 'd' to "ersfcx", 'f' to "rtdgcv", 'g' to "tyfhvb",
        'h' to "yugjbn", 'j' to "uikhnm", 'k' to "iojlm", 'l' to "opk",
        'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb", 'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
    )

    // Basic bucket for prefix search to avoid scanning entire lexicon.
    private val bucketedWords: Map<Char, List<String>> = run {
        val buckets = mutableMapOf<Char, MutableList<String>>()
        for (word in unigramLogFreq.keys) {
            if (word.isNotEmpty()) {
                buckets.getOrPut(word[0]) { mutableListOf() }.add(word)
            }
        }
        buckets.mapValues { it.value.sorted() }
    }

    override fun suggest(request: SuggestionRequest): List<SuggestionCandidate> {
        // ... existing suggest implementation ...
        return emptyList() // We are moving to rank() for the main logic, keeping this for fallback/testing
    }

    override fun rank(
        candidates: List<Pair<String, Double>>, // term to edit distance
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
            
            // Bigram Bonus: Context
            val bigramBonus = bigramBonus(prevWord, word) * weights.bigram
            
            // Penalty: Use SymSpellManager's spatialCost which handles transpositions
            // This ensures consistent scoring between autocorrect and suggestions
            val spatialPenalty = dev.patrickgold.florisboard.ime.nlp.SymSpellManager.spatialCost(typedLower, wordLower)
            val distPenalty = (dist + spatialPenalty) * weights.touchPenalty
            
            val userBonus = (userBoosts[word] ?: 0.0) * weights.user

            var total = baseScore + bigramBonus + userBonus - distPenalty

            // Perfect match bonus (if typed exactly)
            if (typedNoApos == wordNoApos) {
                total += 2.0
            }

            val casedText = applyCasing(word, originalInput)
            val candidate = WordSuggestionCandidate(
                text = casedText,
                confidence = total,
                isEligibleForAutoCommit = false,
                sourceProvider = null,
            )
            scoredCandidates.add(candidate)
        }

        val ranked = scoredCandidates
            .sortedByDescending { it.confidence }
            .toMutableList()

        // Auto-commit logic: Always commit top suggestion if it's different from what was typed
        if (ranked.isNotEmpty()) {
            val top = ranked[0]
            // Auto-commit if top suggestion differs from input
            if (!top.text.toString().equals(originalInput, ignoreCase = true)) {
                ranked[0] = top.copy(isEligibleForAutoCommit = true)
            }
        }

        return ranked
    }

    override fun predictNext(prevWord: String?, max: Int): List<String> {
        val prev = prevWord?.lowercase() ?: return emptyList()
        val row = bigramTable[prev] ?: return emptyList()
        return row.entries
            .sortedByDescending { it.value }
            .take(max)
            .map { it.key }
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

    private fun bigramBonus(prev: String?, cand: String): Double {
        val p = prev?.lowercase() ?: return 0.0
        val row = bigramTable[p] ?: return 0.0
        val freq = row[cand] ?: return 0.0
        val maxFreq = max(1, bigramMaxByPrev[p] ?: 1)
        return ln(freq + 1.0) / ln(maxFreq + 1.0)
    }



    internal fun applyCasing(word: String, rawInput: String): String {
        if (rawInput.isEmpty()) return word
        val isAllUpper = rawInput.all { it.isUpperCase() }
        val isTitle = rawInput.length > 1 && rawInput[0].isUpperCase() && rawInput.drop(1).all { it.isLowerCase() }
        return when {
            isAllUpper -> word.uppercase()
            isTitle -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            else -> word
        }
    }

    companion object {
        fun fromStreams(
            unigramStream: InputStream,
            bigramStream: InputStream,
            userBoosts: Map<String, Double> = emptyMap(),
            weights: Weights = Weights(),
        ): NgramSuggestionEngine {
            val unigrams = loadUnigrams(unigramStream)
            val bigramPair = loadBigrams(bigramStream)
            return NgramSuggestionEngine(unigrams, bigramPair.first, bigramPair.second, userBoosts, weights)
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

        private fun loadBigrams(stream: InputStream): Pair<Map<String, Map<String, Int>>, Map<String, Int>> {
            val table = mutableMapOf<String, MutableMap<String, Int>>()
            val maxByPrev = mutableMapOf<String, Int>()
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size < 2) return@forEach
                    val pair = parts[0]
                    val freq = parts[1].toIntOrNull() ?: return@forEach
                    val spaceIdx = pair.indexOf(' ')
                    if (spaceIdx <= 0) return@forEach
                    val w1 = pair.substring(0, spaceIdx).lowercase()
                    val w2 = pair.substring(spaceIdx + 1).lowercase()
                    val row = table.getOrPut(w1) { mutableMapOf() }
                    row[w2] = freq
                    val currentMax = maxByPrev[w1] ?: 0
                    if (freq > currentMax) {
                        maxByPrev[w1] = freq
                    }
                }
            }
            return table to maxByPrev
        }

        private const val TAG = "NgramSuggestionEngine"
    }
}
