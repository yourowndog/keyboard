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

    companion object {
        private const val TAG = "BigramTable"
        private const val BIGRAM_ASSET_PATH = "ime/dict/final_mobile_bigrams.tsv"

        @Volatile
        private var instance: BigramTable? = null

        /**
         * Get the singleton instance. Returns null if not yet loaded.
         */
        fun get(): BigramTable? = instance

        /**
         * Load bigrams from the default asset path. Call once during app init.
         */
        fun load(context: Context) {
            try {
                val stream = context.assets.open(BIGRAM_ASSET_PATH)
                instance = fromStream(stream)
                android.util.Log.i(TAG, "Loaded shared BigramTable with ${instance?.table?.size ?: 0} first-words")
            } catch (e: Exception) {
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
