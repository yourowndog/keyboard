package dev.patrickgold.florisboard.ime.nlp.shared

import android.content.Context
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.ln
import kotlin.math.max

/**
 * Shared bigram data loaded once and used by both SymSpellManager and NgramSuggestionEngine.
 * Eliminates duplicate loading and ~3x memory savings.
 */
class BigramTable private constructor(
    private val table: Map<String, Map<String, Int>>,
    private val maxByPrev: Map<String, Int>,
    private val topFollowers: Map<String, List<String>>,
) {
    /**
     * Calculate bigram bonus for a candidate given the previous word.
     * @return Normalized log-frequency bonus in ~[0,1] range
     */
    fun bonus(prev: String?, candidate: String): Double {
        val p = prev?.lowercase() ?: return 0.0
        val c = candidate.lowercase()
        val row = table[p] ?: return 0.0
        val freq = row[c] ?: return 0.0
        val maxFreq = max(1, maxByPrev[p] ?: 1)
        return ln(freq + 1.0) / ln(maxFreq + 1.0)
    }

    /**
     * Check if there's a bigram hit for prev -> candidate.
     */
    fun hasHit(prev: String?, candidate: String): Boolean {
        val p = prev?.lowercase() ?: return false
        return table[p]?.containsKey(candidate.lowercase()) == true
    }

    /**
     * Get top predicted next words for a given previous word.
     */
    fun predictNext(prev: String?, max: Int = 5): List<String> {
        val p = prev?.lowercase() ?: return emptyList()
        return topFollowers[p]?.take(max) ?: emptyList()
    }

    /**
     * Get raw frequency for a bigram pair.
     */
    fun getFrequency(prev: String, candidate: String): Int {
        return table[prev.lowercase()]?.get(candidate.lowercase()) ?: 0
    }

    /**
     * Get max frequency for any follower of prev word.
     */
    fun getMaxFrequency(prev: String): Int {
        return maxByPrev[prev.lowercase()] ?: 0
    }

    fun getBigramCount(): Int = table.size

    /**
     * Predict multi-word phrase continuations using Beam Search.
     * Explores multiple paths simultaneously to find the most coherent phrases.
     *
     * IMPORTANT: This is a fallback when PhraseTable has no match.
     * Bigram chaining is inherently noisy — keep phrases short and quality high.
     *
     * @param prev The word to start from.
     * @param maxPhrases How many unique phrases to return.
     * @param beamWidth How many paths to explore at each step.
     */
    fun predictPhrases(prev: String?, maxPhrases: Int = 3, beamWidth: Int = 4): List<String> {
        val p = prev?.lowercase() ?: return emptyList()
        val initialMax = maxByPrev[p]?.toDouble() ?: return emptyList()
        if (initialMax <= 0.0) return emptyList()

        // A "Candidate Path" in our beam
        data class Path(val words: List<String>, val visited: Set<String>, val score: Double)

        // Initialize beam with top followers of the starting word
        var beam = table[p]?.map { (word, freq) ->
            Path(listOf(word), setOf(p, word), freq / initialMax)
        }?.sortedByDescending { it.score }?.take(beamWidth) ?: return emptyList()

        val results = mutableListOf<Path>()
        val maxWords = 3 // Keep short — bigram chains degrade fast past 2-3 words

        // Expand the beam
        repeat(maxWords - 1) {
            val nextBeam = mutableListOf<Path>()
            for (path in beam) {
                val lastWord = path.words.last()
                val followers = table[lastWord]
                val pathMax = maxByPrev[lastWord]?.toDouble() ?: 0.0

                if (followers == null || pathMax <= 0.0) {
                    // Path terminated — no further expansion possible
                    if (path.words.size >= 2) results.add(path)
                    continue
                }

                var expanded = false
                for ((word, freq) in followers) {
                    // CYCLE DETECTION: skip words already in this path
                    if (word in path.visited) continue

                    val newScore = path.score * (freq / pathMax)
                    // Higher threshold — bigram chains get noisy fast
                    if (newScore > 0.15) {
                        nextBeam.add(Path(
                            path.words + word,
                            path.visited + word,
                            newScore
                        ))
                        expanded = true
                    }
                }

                // Only add to results if no expansions passed threshold (dead end)
                if (!expanded && path.words.size >= 2) {
                    results.add(path)
                }
            }
            beam = nextBeam.sortedByDescending { it.score }.take(beamWidth)
        }
        
        // Add remaining beam paths (reached max depth)
        results.addAll(beam.filter { it.words.size >= 2 })

        val ranked = results
            .sortedByDescending { it.score }
            .map { it.words.joinToString(" ") }
            .distinct()

        // Remove phrases that are strict prefixes of longer phrases in results
        val filtered = ranked.filter { phrase ->
            ranked.none { other -> other != phrase && other.startsWith("$phrase ") }
        }

        return filtered.take(maxPhrases)
    }

    companion object {
        private const val TAG = "BigramTable"
        private const val BIGRAM_ASSET_PATH = "ime/dict/final_mobile_bigrams.tsv"

        @Volatile
        private var instance: BigramTable? = null
        private var lastError: String? = null

        /**
         * Get the singleton instance. Returns null if not yet loaded.
         */
        fun get(): BigramTable? = instance

        /**
         * Get the last error message if loading failed.
         */
        fun getLastError(): String? = lastError

        /**
         * Load bigrams from the default asset path. Call once during app init.
         */
        fun load(context: Context) {
            try {
                val stream = context.assets.open(BIGRAM_ASSET_PATH)
                instance = fromStream(stream)
                android.util.Log.i(TAG, "Loaded shared BigramTable with ${instance?.table?.size ?: 0} first-words")
            } catch (e: Exception) {
                lastError = "${e::class.simpleName}: ${e.message}"
                android.util.Log.e(TAG, "Failed to load BigramTable", e)
            }
        }

        /**
         * Load bigrams from an input stream.
         */
        fun fromStream(stream: InputStream): BigramTable {
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

            // Build top followers list for quick "next word" predictions
            val topFollowers = mutableMapOf<String, List<String>>()
            for ((prev, followers) in table) {
                val top = followers.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { it.key }
                topFollowers[prev] = top
            }

            return BigramTable(table, maxByPrev, topFollowers)
        }
    }
}
