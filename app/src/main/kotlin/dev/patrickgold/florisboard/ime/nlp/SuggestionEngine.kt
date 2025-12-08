package dev.patrickgold.florisboard.ime.nlp

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.ln
import kotlin.math.max

 data class SuggestionRequest(
    val typed: String,
    val prevWord: String? = null,
    val maxSuggestions: Int = 5,
)

 data class SuggestionCandidate(
    val word: String,
    val score: Double,
    val source: String = "ngram",
)

 interface SuggestionEngine {
    fun suggest(request: SuggestionRequest): List<SuggestionCandidate>
    fun predictNext(prevWord: String?, max: Int = 3): List<String>
}

/**
 * Lightweight n-gram based scorer (SymSpell-free).
 *
 * Inputs/outputs are primitive/flat so we can swap in a JNI/Rust-backed engine later
 * without touching UI or Android-specific code.
 */
 class NgramSuggestionEngine(
    private val unigramLogFreq: Map<String, Double>,
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
        val typed = request.typed.lowercase()
        if (typed.isEmpty()) return emptyList()

        val candidates = mutableListOf<SuggestionCandidate>()
        val pool = bucketedWords[typed[0]] ?: emptyList()
        for (word in pool) {
            if (!word.startsWith(typed)) continue
            val baseScore = (unigramLogFreq[word] ?: continue) * weights.base
            val bigramBonus = bigramBonus(request.prevWord, word) * weights.bigram
            val touchPenalty = touchPenalty(typed, word) * weights.touchPenalty
            val userBonus = (userBoosts[word] ?: 0.0) * weights.user
            val total = baseScore + bigramBonus + userBonus - touchPenalty
            candidates.add(SuggestionCandidate(word = word, score = total))
        }
        return candidates.sortedByDescending { it.score }.take(request.maxSuggestions)
    }

    override fun predictNext(prevWord: String?, max: Int): List<String> {
        val prev = prevWord?.lowercase() ?: return emptyList()
        val row = bigramTable[prev] ?: return emptyList()
        return row.entries
            .sortedByDescending { it.value }
            .take(max)
            .map { it.key }
    }

    private fun bigramBonus(prev: String?, cand: String): Double {
        val p = prev?.lowercase() ?: return 0.0
        val row = bigramTable[p] ?: return 0.0
        val freq = row[cand] ?: return 0.0
        val maxFreq = max(1, bigramMaxByPrev[p] ?: 1)
        return ln(freq + 1.0) / ln(maxFreq + 1.0)
    }

    private fun touchPenalty(typed: String, cand: String): Double {
        val lenPenalty = kotlin.math.abs(typed.length - cand.length) * 0.2
        val limit = minOf(typed.length, cand.length)
        var penalty = 0.0
        for (i in 0 until limit) {
            val t = typed[i]
            val c = cand[i]
            if (t == c) continue
            val neighbors = neighborMap[t] ?: ""
            penalty += if (c in neighbors) 0.3 else 1.0
        }
        return penalty + lenPenalty
    }

    companion object {
        fun fromStreams(
            unigramStream: InputStream,
            bigramStream: InputStream,
            userBoosts: Map<String, Double> = emptyMap(),
            weights: Weights = Weights(),
        ): NgramSuggestionEngine {
            val unigrams = loadUnigrams(unigramStream)
            val (bigrams, bigramMax) = loadBigrams(bigramStream)
            return NgramSuggestionEngine(unigrams, bigrams, bigramMax, userBoosts, weights)
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
    }
}
