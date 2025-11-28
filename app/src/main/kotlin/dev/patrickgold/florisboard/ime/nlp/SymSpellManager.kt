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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SymSpellManager {
    private var symSpell: SymSpell? = null
    @Volatile private var isReady = false

    // Config
    private const val MAX_EDIT_DISTANCE = 2
    private const val PREFIX_LENGTH = 7
    private const val DICT_ASSET_PATH = "ime/dict/frequency_dictionary_en.txt"
    private const val BIGRAM_ASSET_PATH = "ime/dict/frequency_bigram_en.txt"
    private val USER_OVERRIDES = listOf("kiry" to Double.MAX_VALUE)

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

                // 3. Load Real Dictionary from Assets (unigram + bigram). Normalize spacing to the tab-delimited format
                // expected by SymSpell loaders.
                val unigramBytes = context.assets.open(DICT_ASSET_PATH)
                    .bufferedReader()
                    .useLines { normalizeForSymSpell(it) }
                val bigramBytes = context.assets.open(BIGRAM_ASSET_PATH)
                    .bufferedReader()
                    .useLines { normalizeForSymSpell(it) }

                holder.loadUnigramTxtFile(unigramBytes)
                holder.loadBigramTxtFile(bigramBytes)

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

    fun fix(input: String): String {
        if (!isReady) return input
        val instance = symSpell ?: return input
        if (input.length < 2) return input

        // Reflexes: Fast correction
        val normalized = input.lowercase()
        val suggestions = instance.lookup(normalized, Verbosity.Top, MAX_EDIT_DISTANCE.toDouble())
        val suggestion = suggestions.firstOrNull()?.term ?: return input

        // Heuristic: if the user typed multiple uppercase letters (likely an acronym/proper noun)
        // and the suggestion doesn't match the same lowercase letters, keep the original.
        val upperCount = input.count { it.isUpperCase() }
        if (upperCount >= 2 && suggestion.lowercase() != normalized) {
            return input
        }

        return applyCasingPattern(input, suggestion)
    }

    fun suggest(input: String): List<String> {
         if (!isReady) return emptyList()
         val instance = symSpell ?: return emptyList()

         val normalized = input.lowercase()
         val upperCount = input.count { it.isUpperCase() }
         val suggestions = instance.lookup(normalized, Verbosity.Closest, MAX_EDIT_DISTANCE.toDouble())
         val mapped = suggestions.mapNotNull { candidate ->
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

    private fun normalizeForSymSpell(lines: Sequence<String>): ByteArray {
        val sb = StringBuilder()
        lines.forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@forEach
            val cleaned = trimmed.trimStart { it == '\uFEFF' || it.isWhitespace() }
            val sep = cleaned.lastIndexOf(' ')
            if (sep <= 0 || sep == cleaned.lastIndex) return@forEach
            sb.append(cleaned, 0, sep)
            sb.append('\t')
            sb.append(cleaned.substring(sep + 1))
            sb.append('\n')
        }
        return sb.toString().toByteArray()
    }
}
