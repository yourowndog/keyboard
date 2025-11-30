package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import com.darkrockstudios.symspellkt.api.loadBigramTxtFile
import com.darkrockstudios.symspellkt.api.loadUnigramTxtFile
import com.darkrockstudios.symspellkt.common.DamerauLevenshteinDistance
import com.darkrockstudios.symspellkt.common.Murmur3HashFunction
import com.darkrockstudios.symspellkt.impl.SymSpell
import com.darkrockstudios.symspellkt.impl.InMemoryDictionaryHolder
import com.darkrockstudios.symspellkt.common.SpellCheckSettings
import com.darkrockstudios.symspellkt.common.Verbosity
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.max

object SymSpellManager {
    private var symSpell: SymSpell? = null
    @Volatile private var isReady = false

    // Config
    private const val MAX_EDIT_DISTANCE = 2
    private const val PREFIX_LENGTH = 7
    private const val DICT_ASSET_PATH = "ime/dict/frequency_dictionary_en.txt"
    private const val BIGRAM_ASSET_PATH = "ime/dict/frequency_bigram_en.txt"
    private const val BIGRAM_WEIGHT = 1.1
    private val USER_OVERRIDES = listOf("kiry" to Double.MAX_VALUE)
    // Prefer common contractions before running SymSpell so "im" maps to "I'm" instead of "pm".
    private val CONTRACTION_SHORTCUTS = mapOf(
        "im" to "I'm",
        "ive" to "I've",
        "id" to "I'd",
        "ill" to "I'll",
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "doesnt" to "doesn't",
        "didnt" to "didn't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "youre" to "you're",
        "theyre" to "they're",
        "were" to "we're",
        "lets" to "let's",
        "thats" to "that's",
        "whos" to "who's",
        "whats" to "what's",
        "wheres" to "where's",
        "theres" to "there's",
    )
    private val PROPER_OVERRIDES = setOf(
        "kiry", "kiry's",
        "sam", "sam's",
        "elijah", "elijah's",
        "dad", "dad's",
        "mom", "mom's",
        "violet", "violet's",
        "levi", "levi's",
        "pepa", "pepa's",
        "mike", "mike's",
        "tom", "tom's",
        "tony", "tony's",
        "ellie", "ellie's",
        "otis", "otis's",
        "rupert", "rupert's",
        "dan", "dan's",
        "tim", "tim's",
    )

    // Tracks whether the last autocorrect was rejected; if so, skip autocorrect once.
    @Volatile private var skipNextAutocorrect = false

    // Bigram bonus tables for reranking suggestions.
    private val bigramCounts = mutableMapOf<String, MutableMap<String, Int>>()
    private val bigramMaxByPrev = mutableMapOf<String, Int>()

    fun init(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Initialize Engine Configuration
                val settings = SpellCheckSettings(
                    maxEditDistance = MAX_EDIT_DISTANCE.toDouble(), // FIX: Compiler wants Double
                    prefixLength = PREFIX_LENGTH,
                    countThreshold = 1L                             // FIX: Compiler wants Long
                )

                // 2. Create Instance with explicit holder so we can load unigrams + bigrams
                val holder = InMemoryDictionaryHolder(settings, Murmur3HashFunction())
                // Constructor order is (SpellCheckSettings, StringDistance, DictionaryHolder)
                val instance = SymSpell(settings, DamerauLevenshteinDistance(), holder)

                // 3. Load Real Dictionary from Assets (unigram + bigram)
                holder.loadUnigramTxtFile(context.assets.open(DICT_ASSET_PATH).use { it.readBytes() })
                holder.loadBigramTxtFile(context.assets.open(BIGRAM_ASSET_PATH).use { it.readBytes() })
                loadBigramTable(context)

                // Inject must-win personal words until we wire user dictionary
                USER_OVERRIDES.forEach { (word, freq) -> instance.createDictionaryEntry(word, freq) }

                val loadedWords = holder.wordCount
                symSpell = instance
                isReady = loadedWords > 0
                android.util.Log.i(
                    "SymSpellManager",
                    "Reflexes Ready: Loaded $loadedWords words from $DICT_ASSET_PATH with bigrams from $BIGRAM_ASSET_PATH"
                )
                if (!isReady) {
                    android.util.Log.w("SymSpellManager", "Reflexes dictionary is empty; keeping autocorrect disabled")
                }
            } catch (e: Exception) {
                android.util.Log.e("SymSpellManager", "Reflexes Failed to Load Dictionary", e)
            }
        }
    }

    private fun loadBigramTable(context: Context) {
        bigramCounts.clear()
        bigramMaxByPrev.clear()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open(BIGRAM_ASSET_PATH)))
            reader.useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size != 2) return@forEach
                    val pair = parts[0]
                    val freq = parts[1].toIntOrNull() ?: return@forEach
                    val spaceIdx = pair.indexOf(' ')
                    if (spaceIdx <= 0) return@forEach
                    val w1 = pair.substring(0, spaceIdx).lowercase()
                    val w2 = pair.substring(spaceIdx + 1).lowercase()
                    val row = bigramCounts.getOrPut(w1) { mutableMapOf() }
                    row[w2] = freq
                    val currentMax = bigramMaxByPrev[w1] ?: 0
                    if (freq > currentMax) {
                        bigramMaxByPrev[w1] = freq
                    }
                }
            }
            android.util.Log.i(
                "SymSpellManager",
                "Loaded bigram table for reranking with ${bigramCounts.size} first-words"
            )
        } catch (e: Exception) {
            android.util.Log.w("SymSpellManager", "Failed to load bigram table for reranking", e)
        }
    }

    private fun bigramBonus(prev: String, cand: String): Double {
        val row = bigramCounts[prev] ?: return 0.0
        val freq = row[cand] ?: return 0.0
        val maxFreq = max(1, bigramMaxByPrev[prev] ?: 1)
        // Normalized log freq in [0,1] range-ish
        return ln(freq + 1.0) / ln(maxFreq + 1.0)
    }

    fun markNextAsUserRejected() {
        skipNextAutocorrect = true
    }

    fun fix(input: String, previousWord: String? = null): String {
        if (!isReady) return input
        if (skipNextAutocorrect) {
            skipNextAutocorrect = false
            return input
        }
        val instance = symSpell ?: return input
        // Handle single-letter inputs explicitly to avoid over-correcting every keystroke.
        // Do not auto-capitalize lone "i"; keep exactly what the user typed.
        if (input.length == 1) return input

        // Fast-path for contractions so missing apostrophes don't divert to unrelated words.
        CONTRACTION_SHORTCUTS[input.lowercase()]?.let { contraction ->
            return applyCasingPattern(input, contraction)
        }

        // Reflexes: Fast correction
        val normalized = input.lowercase()
        val normalizedNoApos = normalized.replace("'", "")
        val suggestions = instance.lookup(normalized, Verbosity.Top, MAX_EDIT_DISTANCE.toDouble())
        val prev = previousWord?.lowercase()

        // Prefer candidates that only differ by a missing apostrophe (treat apostrophes as zero-cost).
        val suggestion = suggestions.minByOrNull { candidate ->
            val candidateNorm = candidate.term.lowercase().replace("'", "")
            val apostropheBonus = if (candidate.term.contains('\'') && candidateNorm == normalizedNoApos) -1 else 0
            val bigramBoost = if (prev != null) -BIGRAM_WEIGHT * bigramBonus(prev, candidate.term.lowercase()) else 0.0
            candidate.distance + apostropheBonus + bigramBoost
        }?.term ?: return input

        // Heuristic: if the user typed multiple uppercase letters (likely an acronym/proper noun)
        // and the suggestion doesn't match the same lowercase letters, keep the original.
        val upperCount = input.count { it.isUpperCase() }
        if (upperCount >= 2 && suggestion.lowercase() != normalized) {
            return input
        }

        return applyCasingPattern(input, suggestion)
    }

    fun suggest(input: String, previousWord: String? = null): List<String> {
         if (!isReady) return emptyList()
         val instance = symSpell ?: return emptyList()

         if (input.length == 1) {
             // Mirror the fix() behavior: keep exactly what the user typed.
             return listOf(input)
         }

         val normalized = input.lowercase()
         val normalizedNoApos = normalized.replace("'", "")
         val upperCount = input.count { it.isUpperCase() }
         val suggestions = instance.lookup(normalized, Verbosity.Closest, MAX_EDIT_DISTANCE.toDouble())
        val prev = previousWord?.lowercase()
         val mapped = suggestions
             .sortedBy { candidate ->
                 val candidateNorm = candidate.term.lowercase().replace("'", "")
                 val apostropheBonus = if (candidate.term.contains('\'') && candidateNorm == normalizedNoApos) -1 else 0
                val bigramBoost = if (prev != null) -BIGRAM_WEIGHT * bigramBonus(prev, candidate.term.lowercase()) else 0.0
                 candidate.distance + apostropheBonus + bigramBoost
             }
             .mapNotNull { candidate ->
                 val term = candidate.term
                 if (upperCount >= 2 && term.lowercase() != normalized) {
                     // Skip suggestions that would mangle acronyms/proper nouns
                     null
                 } else {
                     applyCasingPattern(input, term)
                 }
             }
         return if (mapped.isNotEmpty()) mapped else listOf(input)
    }

    private fun applyCasingPattern(original: String, suggestion: String): String {
        if (original.isEmpty()) return suggestion
        if (original.length == 1 && original.equals("i", ignoreCase = true) && suggestion.equals("i", ignoreCase = true)) {
            return "I"
        }
        if (suggestion.lowercase() in PROPER_OVERRIDES) {
            return suggestion.replaceFirstChar { it.titlecase() }
        }
        val isAllUpper = original.all { it.isUpperCase() }
        if (isAllUpper) return suggestion.uppercase()

        val isTitle = original.first().isUpperCase() && original.drop(1).all { it.isLowerCase() }
        if (isTitle) return suggestion.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // Preserve leading capital if user started with one (e.g., names like iPhone stay mixed)
        if (original.first().isUpperCase()) {
            return suggestion.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return suggestion
    }

}
