/*
 * DictionaryRepository - single shared owner of the unified dictionary.
 *
 * Replaces two independent loads of unified_dictionary.tsv (SymSpell's
 * InMemoryDictionaryHolder and NgramSuggestionEngine.fromStreams) and, more
 * importantly, replaces SymSpell's precomputed deletion index (~4-5M strings,
 * 100MB+ heap, ~9s build) with typed-word candidate generation: instead of
 * storing every possible typo of every dictionary word, we scan length-bucketed
 * words with a cheap character-mask prefilter and a bounded edit-distance check
 * at lookup time. One lookup costs ~1-3ms; init drops to a single TSV parse.
 *
 * Case handling: matching is case-insensitive (keys lowercased); the original
 * display form is preserved so corrections can surface "OpenClaw" or "I'm".
 */
package dev.patrickgold.florisboard.ime.nlp.shared

import android.content.Context
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

object DictionaryRepository {
    private const val DICT_ASSET_PATH = "ime/dict/unified_dictionary.tsv"
    private const val MAX_WORD_LENGTH = 34
    private const val MAX_EDIT_DISTANCE = 2

    /** Mirrors symspellkt's SuggestionItem shape so call sites keep .term/.distance/.frequency. */
    class Candidate(val term: String, val distance: Double, val frequency: Double)

    private class Bucket(val words: Array<String>, val masks: IntArray, val freqs: LongArray)

    @Volatile private var loaded = false
    private val loadLock = Any()

    // lowercase word -> frequency
    private var freqMap: HashMap<String, Long> = HashMap()
    // lowercase word -> display form; only populated where display differs from lowercase
    private var displayMap: HashMap<String, String> = HashMap()
    // lowercase word -> ln(freq + 1), shared by reference with NgramSuggestionEngine
    private var logFreqMap: HashMap<String, Double> = HashMap()
    // words grouped by length for the edit-distance scan
    private var buckets: Array<Bucket?> = arrayOfNulls(MAX_WORD_LENGTH + 1)
    // must-win personal words layered on top of the TSV (checked first everywhere)
    private val overrides = HashMap<String, Pair<String, Long>>() // lower -> (display, freq)

    val isLoaded: Boolean get() = loaded
    val size: Int get() = freqMap.size + overrides.count { it.key !in freqMap }

    /** Log-space frequencies for ranking engines. Shared by reference; do not mutate. */
    val logFrequencies: Map<String, Double> get() = logFreqMap

    /** Lowercase word -> raw frequency. Shared by reference; do not mutate. */
    val lowercaseFrequencies: Map<String, Long> get() = freqMap

    /**
     * Parse the dictionary TSV once and build all lookup structures.
     * Safe to call from multiple threads; only the first call does work.
     */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            val freq = HashMap<String, Long>(220_000)
            val display = HashMap<String, String>(64_000)
            val logFreq = HashMap<String, Double>(220_000)
            val byLength = Array(MAX_WORD_LENGTH + 1) { ArrayList<Triple<String, Int, Long>>() }

            context.assets.open(DICT_ASSET_PATH).bufferedReader().useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val raw = line.substring(0, tab)
                    if (raw.isEmpty() || raw.length > MAX_WORD_LENGTH) continue
                    val f = line.substring(tab + 1).trim().toLongOrNull() ?: continue
                    val lower = raw.lowercase()
                    val existing = freq[lower]
                    if (existing != null && existing >= f) continue
                    freq[lower] = f
                    logFreq[lower] = ln(f + 1.0)
                    if (raw != lower) display[lower] = raw else display.remove(lower)
                    if (existing == null) {
                        byLength[lower.length].add(Triple(lower, charMask(lower), f))
                    }
                }
            }

            // Frequencies may have been updated after bucket insertion for duplicate
            // lowercase forms; rebuild bucket freqs from the final map.
            buckets = Array(MAX_WORD_LENGTH + 1) { len ->
                val entries = byLength[len]
                if (entries.isEmpty()) null else Bucket(
                    words = Array(entries.size) { entries[it].first },
                    masks = IntArray(entries.size) { entries[it].second },
                    freqs = LongArray(entries.size) { freq[entries[it].first] ?: entries[it].third },
                )
            }
            freqMap = freq
            displayMap = display
            logFreqMap = logFreq
            loaded = true
            android.util.Log.i("DictionaryRepository", "Loaded ${freq.size} words from $DICT_ASSET_PATH")
        }
    }

    /**
     * Layer a must-win personal word over the dictionary. Default frequency matches the
     * top of the AOSP table ("the" = 10M) rather than an unbounded value: the log-freq
     * feeds ranking and neural features that were trained on the real distribution.
     */
    fun addOverride(word: String, frequency: Long = 10_000_000L) {
        val lower = word.lowercase()
        overrides[lower] = word to frequency
        logFreqMap[lower] = ln(frequency + 1.0)
    }

    fun contains(word: String): Boolean {
        val lower = word.lowercase()
        return lower in overrides || lower in freqMap
    }

    /** Display form for an exact (case-insensitive) match, or null if absent. */
    fun exactMatch(word: String): String? {
        val lower = word.lowercase()
        overrides[lower]?.let { return it.first }
        if (!freqMap.containsKey(lower)) return null
        return displayMap[lower] ?: lower
    }

    fun frequencyOf(word: String): Long {
        val lower = word.lowercase()
        return overrides[lower]?.second ?: freqMap[lower] ?: 0L
    }

    /**
     * All dictionary words within edit distance [MAX_EDIT_DISTANCE] of [typed],
     * sorted by (distance, -frequency), capped at [maxResults]. Includes the
     * typed word itself at distance 0 when it is a dictionary word — same
     * contract as SymSpell's Verbosity.All lookup.
     */
    private val lookupCounter = java.util.concurrent.atomic.AtomicInteger()

    fun findWithinTwoEdits(typed: String, maxResults: Int = 50): List<Candidate> {
        if (!loaded) return emptyList()
        val start = System.nanoTime()
        val result = scanWithinTwoEdits(typed, maxResults)
        // Sample lookup latency so regressions show up in logcat without profiling
        if (lookupCounter.incrementAndGet() % 32 == 1) {
            val micros = (System.nanoTime() - start) / 1000
            android.util.Log.i("DictionaryRepository", "findWithinTwoEdits('$typed') -> ${result.size} candidates in ${micros}us")
        }
        return result
    }

    private fun scanWithinTwoEdits(typed: String, maxResults: Int): List<Candidate> {
        val query = typed.lowercase()
        val n = query.length
        if (n == 0 || n > MAX_WORD_LENGTH - MAX_EDIT_DISTANCE) return emptyList()

        val queryChars = query.toCharArray()
        val queryMask = charMask(query)
        val results = ArrayList<Candidate>(64)
        // Reused DP rows (three needed for the transposition term)
        val maxLen = n + MAX_EDIT_DISTANCE
        val row0 = IntArray(maxLen + 1)
        val row1 = IntArray(maxLen + 1)
        val row2 = IntArray(maxLen + 1)

        val lenLo = maxOf(1, n - MAX_EDIT_DISTANCE)
        val lenHi = minOf(MAX_WORD_LENGTH, n + MAX_EDIT_DISTANCE)
        for (len in lenLo..lenHi) {
            val bucket = buckets[len] ?: continue
            val words = bucket.words
            val masks = bucket.masks
            val freqs = bucket.freqs
            for (i in words.indices) {
                // One edit changes at most 2 character-presence bits, so within
                // 2 edits the masks differ by at most 4 bits. ~2ns reject.
                if (Integer.bitCount(queryMask xor masks[i]) > 4) continue
                val word = words[i]
                val d = osaDistanceAtMost(queryChars, word, MAX_EDIT_DISTANCE, row0, row1, row2)
                if (d < 0) continue
                results.add(Candidate(displayMap[word] ?: word, d.toDouble(), freqs[i].toDouble()))
            }
        }
        // Overrides participate too (they may not be in the TSV at all)
        for ((lower, entry) in overrides) {
            if (abs(lower.length - n) > MAX_EDIT_DISTANCE) continue
            if (results.any { it.term.equals(entry.first, ignoreCase = true) }) continue
            val d = osaDistanceAtMost(queryChars, lower, MAX_EDIT_DISTANCE, row0, row1, row2)
            if (d < 0) continue
            results.add(Candidate(entry.first, d.toDouble(), entry.second.toDouble()))
        }

        results.sortWith(compareBy({ it.distance }, { -it.frequency }))
        return if (results.size <= maxResults) results else results.subList(0, maxResults)
    }

    /**
     * Optimal-string-alignment (Damerau-Levenshtein) distance between [a] and [b],
     * early-abandoned: returns 99.0 when the distance exceeds [MAX_EDIT_DISTANCE].
     * Drop-in for the previous symspellkt DamerauLevenshteinDistance usage.
     */
    fun distance(a: String, b: String): Double {
        val aChars = a.lowercase().toCharArray()
        val bLower = b.lowercase()
        if (aChars.size > MAX_WORD_LENGTH || bLower.length > MAX_WORD_LENGTH) return 99.0
        val cap = maxOf(aChars.size, bLower.length) + 1
        val d = osaDistanceAtMost(aChars, bLower, MAX_EDIT_DISTANCE, IntArray(cap), IntArray(cap), IntArray(cap))
        return if (d < 0) 99.0 else d.toDouble()
    }

    /** 26 bits for a-z, bit 26 for apostrophe, bit 27 for anything else. */
    private fun charMask(word: String): Int {
        var mask = 0
        for (c in word) {
            mask = mask or when {
                c in 'a'..'z' -> 1 shl (c - 'a')
                c == '\'' -> 1 shl 26
                else -> 1 shl 27
            }
        }
        return mask
    }

    /**
     * OSA distance with per-row early abandon. Returns the distance if <= [maxDist],
     * else -1. Rows are caller-provided so the bucket scan is allocation-free.
     */
    private fun osaDistanceAtMost(a: CharArray, b: String, maxDist: Int, r0: IntArray, r1: IntArray, r2: IntArray): Int {
        val n = a.size
        val m = b.length
        if (abs(n - m) > maxDist) return -1
        if (n == 0) return m
        if (m == 0) return n
        var prev2 = r0 // row i-2
        var prev = r1  // row i-1
        var curr = r2  // row i
        for (j in 0..m) prev[j] = j
        for (i in 1..n) {
            curr[0] = i
            var rowMin = i
            val ai = a[i - 1]
            for (j in 1..m) {
                val cost = if (ai == b[j - 1]) 0 else 1
                var v = min(min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost)
                if (i > 1 && j > 1 && ai == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = min(v, prev2[j - 2] + 1)
                }
                curr[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > maxDist) return -1
            val tmp = prev2
            prev2 = prev
            prev = curr
            curr = tmp
        }
        val d = prev[m]
        return if (d <= maxDist) d else -1
    }
}
