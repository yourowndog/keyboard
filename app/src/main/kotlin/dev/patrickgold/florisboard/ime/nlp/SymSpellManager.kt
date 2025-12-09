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
    private const val DICT_ASSET_PATH = "ime/dict/frequency_dictionary_en.cleaned.txt"
    private const val BIGRAM_ASSET_PATH = "ime/dict/final_mobile_bigrams.tsv"
    private const val BIGRAM_WEIGHT = 1.5
    private const val BIGRAM_NO_HIT_PENALTY = 0.2
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
        "well" to "we'll",
        "hell" to "he'll",
        "shell" to "she'll",
        "its" to "it's",
    )
    private val PROPER_OVERRIDES = setOf(
        "kiry", "kiry's",
        "sam", "sam's",
        "i'd",
        "mike", "mike's",
        "john", "john's",
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

    // QWERTY Neighbor Map (Hardcoded for robustness)
    private val KEYBOARD_NEIGHBORS = mapOf(
        'q' to "wa", 'w' to "qase", 'e' to "wsdfr", 'r' to "edft", 't' to "rfgy", 'y' to "tghu", 'u' to "yhij", 'i' to "ujko", 'o' to "iklp", 'p' to "ol",
        'a' to "qwsz", 's' to "qweadzx", 'd' to "ersfcx", 'f' to "rtdgcv", 'g' to "tyfhvb", 'h' to "yugjbn", 'j' to "uikhnm", 'k' to "iojlm", 'l' to "opk",
        'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb", 'b' to "vghn", 'n' to "bhjm", 'm' to "njk"
    )
    
    private val BLACKLIST = setOf("wont", "hows", "cant", "dont", "isnt", "arent", "didnt", "couldnt", "wouldnt", "shouldnt", "wasnt", "werent", "hasnt", "havent", "hadnt")

    // Bigram bonus tables for reranking suggestions.
    private val bigramCounts = mutableMapOf<String, MutableMap<String, Int>>()
    private val bigramMaxByPrev = mutableMapOf<String, Int>()
    private val bigramTopFollowers = mutableMapOf<String, List<String>>() // sorted by freq desc

    private data class BigramScore(val bonus: Double, val hasHit: Boolean)

    fun init(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                // Ensure DictionaryManager is ready
                dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.init(context)

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
            // Build top followers list for quick "next word" predictions
            bigramTopFollowers.clear()
            for ((prev, followers) in bigramCounts) {
                val top = followers.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .map { it.key }
                bigramTopFollowers[prev] = top
            }
            android.util.Log.i(
                "SymSpellManager",
                "Loaded bigram table for reranking with ${bigramCounts.size} first-words"
            )
        } catch (e: Exception) {
            android.util.Log.w("SymSpellManager", "Failed to load bigram table for reranking", e)
        }
    }

    fun nextWordPredictions(prev: String?, max: Int = 3): List<String> {
        if (prev == null) return emptyList()
        return bigramTopFollowers[prev.lowercase()]?.take(max) ?: emptyList()
    }

    private fun bigramBonus(prev: String?, cand: String?): BigramScore {
        val p = prev ?: return BigramScore(0.0, false)
        val c = cand ?: return BigramScore(0.0, false)
        val row = bigramCounts[p] ?: return BigramScore(0.0, false)
        val freq = row[c] ?: return BigramScore(0.0, false)
        val maxFreq = max(1, bigramMaxByPrev[p] ?: 1)
        // Normalized log freq in [0,1] range-ish
        val bonus = ln(freq + 1.0) / ln(maxFreq + 1.0)
        return BigramScore(bonus, true)
    }

    // 0.0 = perfect neighbor match, 2.0 = far away
    private fun spatialCost(typed: String, candidate: String): Double {
        // Penalize length differences (insertions/deletions) so substitutions are preferred.
        // A substitution with a neighbor (0.5) should be cheaper than a deletion (1.0).
        if (typed.length != candidate.length) return 1.0 
        
        var cost = 0.0
        for (i in typed.indices) {
            val t = typed[i]
            val c = candidate[i]
            if (t == c) continue
            val neighbors = KEYBOARD_NEIGHBORS[t] ?: ""
            if (neighbors.contains(c)) {
                cost += 0.5 // Close miss
            } else {
                cost += 2.0 // Far miss
            }
        }
        return cost
    }

    fun markNextAsUserRejected() {
        skipNextAutocorrect = true
    }

    fun fix(input: String, previousWord: String? = null): String {
        // Fast-path for contractions so missing apostrophes don't divert to unrelated words.
        CONTRACTION_SHORTCUTS[input.lowercase()]?.let { contraction ->
            return applyCasingPattern(input, contraction)
        }

        if (!isReady) return input
        if (skipNextAutocorrect) {
            skipNextAutocorrect = false
            return input
        }
        val instance = symSpell ?: return input
        // Handle single-letter inputs explicitly to avoid over-correcting every keystroke.
        // Do not auto-capitalize lone "i"; keep exactly what the user typed.
        if (input.length == 1) return input

        // Reflexes: Fast correction
        val normalized = input.lowercase()
        val normalizedNoApos = normalized.replace("'", "")
        // Use Verbosity.All to ensure we find distance 2 candidates (like won't from wint) even if distance 1 candidates exist.
        val suggestions = instance.lookup(normalized, Verbosity.All, MAX_EDIT_DISTANCE.toDouble())
        val prev = previousWord?.lowercase()
        
        // Check against ignore list
        val ignoreManager = dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()
        
        // If there is an apostrophe variant that is the same letters without apostrophe, prefer it.
        val apostropheCandidate = suggestions.firstOrNull { cand ->
            val candLower = cand.term.lowercase()
            candLower.contains('\'') && candLower.replace("'", "") == normalizedNoApos
        }

        // Prefer candidates that only differ by a missing apostrophe (treat apostrophes as zero-cost).
        val suggestion = suggestions.minByOrNull { candidate ->
            val term = candidate.term
            val lowerTerm = term.lowercase()
            val candidateNorm = lowerTerm.replace("'", "")
            
            if (ignoreManager.isUserIgnored(input, term)) return@minByOrNull Double.MAX_VALUE
            if (BLACKLIST.contains(lowerTerm)) return@minByOrNull Double.MAX_VALUE
            
            var apostropheBonus = 0.0
            if (term.contains('\'')) {
                if (candidateNorm == normalizedNoApos) {
                     apostropheBonus = -20.0 // Exact letter match (im -> I'm)
                } else {
                     // Allow typo + apostrophe (wint -> won't). 
                     // If the normalized form (wont) is close to input (wint), give bonus.
                     // We use spatialCost to check closeness of the non-apostrophe parts.
                     val spatial = spatialCost(normalizedNoApos, candidateNorm)
                     if (spatial < 2.0) { // If keys are close
                         apostropheBonus = -10.0
                     }
                }
            }

            val bigram = bigramBonus(prev, term.lowercase())
            val bigramBoost = -BIGRAM_WEIGHT * bigram.bonus // Removed 5.0x multiplier
            val noHitPenalty = if (prev != null && !bigram.hasHit) BIGRAM_NO_HIT_PENALTY else 0.0
            
            // Spatial cost: cheaper if keys are close
            val spatial = spatialCost(normalized, term.lowercase())
            
            // Exact matches (distance 0) must effectively win against everything except explicit user overrides
            if (candidate.distance == 0.0 && spatial == 0.0) {
                return@minByOrNull -100.0
            }
            
            candidate.distance + apostropheBonus + bigramBoost + noHitPenalty + spatial
        }?.term ?: return input

        // If we landed on the original input but have a matching apostrophe candidate, pick that instead.
        val finalSuggestion = when {
            suggestion == input && apostropheCandidate != null -> apostropheCandidate.term
            else -> suggestion
        }

        // Check against ignore list
        if (dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default().isUserIgnored(input, finalSuggestion)) {
            return input
        }

        // Heuristic: if the user typed multiple uppercase letters (likely an acronym/proper noun)
        // and the suggestion doesn't match the same lowercase letters, keep the original.
        val upperCount = input.count { it.isUpperCase() }
        if (upperCount >= 2 && finalSuggestion.lowercase() != normalized) {
            return input
        }

        return applyCasingPattern(input, finalSuggestion)
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
        val suggestions = instance.lookup(normalized, Verbosity.All, MAX_EDIT_DISTANCE.toDouble())
        val prev = previousWord?.lowercase()
        val ignoreManager = dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()

        val contractionTop = CONTRACTION_SHORTCUTS[normalized]?.let { applyCasingPattern(input, it) }
        val mapped = suggestions
            .sortedBy { candidate ->
                val term = candidate.term
                val lowerTerm = term.lowercase()
                val candidateNorm = lowerTerm.replace("'", "")
                
                if (ignoreManager.isUserIgnored(input, term)) Double.MAX_VALUE 
                else if (BLACKLIST.contains(lowerTerm)) Double.MAX_VALUE
                else {
                    var apostropheBonus = 0.0
                    if (term.contains('\'')) {
                        if (candidateNorm == normalizedNoApos) {
                             apostropheBonus = -20.0 
                        } else {
                             val spatial = spatialCost(normalizedNoApos, candidateNorm)
                             if (spatial < 2.0) { 
                                 apostropheBonus = -10.0
                             }
                        }
                    }
                    
                    val bigram = bigramBonus(prev, lowerTerm)
                    val bigramBoost = -BIGRAM_WEIGHT * bigram.bonus // Removed 5.0x multiplier
                    val noHitPenalty = if (prev != null && !bigram.hasHit) BIGRAM_NO_HIT_PENALTY else 0.0
                    val spatial = spatialCost(normalized, lowerTerm)
                    
                    if (candidate.distance == 0.0 && spatial == 0.0) {
                        -100.0
                    } else {
                        candidate.distance + apostropheBonus + bigramBoost + noHitPenalty + spatial
                    }
                }
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
        val withContraction = buildList {
            if (contractionTop != null) {
                add(contractionTop)
                if (mapped.isNotEmpty()) {
                    // If the top mapped suggestion is the same as contraction, skip it
                    if (mapped.first() != contractionTop) {
                        addAll(mapped)
                    } else {
                        addAll(mapped.drop(1))
                    }
                }
            } else if (mapped.isNotEmpty()) {
                addAll(mapped)
            }
        }
        return if (withContraction.isNotEmpty()) withContraction else listOf(input)
    }

    data class RawCandidate(val term: String, val distance: Double)

    /**
     * Returns raw candidates from SymSpell without applying the internal hardcoded ranking/fixing logic.
     * This allows external engines (like NgramSuggestionEngine) to apply their own scoring.
     */
    fun findCandidates(input: String): List<RawCandidate> {
        if (!isReady) return emptyList()
        val instance = symSpell ?: return emptyList()
        
        // Use Verbosity.All to get all candidates within edit distance
        val suggestions = instance.lookup(input.lowercase(), Verbosity.All, MAX_EDIT_DISTANCE.toDouble())
        return suggestions.map { RawCandidate(it.term, it.distance) }
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

    /**
     * Public casing function that checks sentence context (start of text, after period)
     * before applying normal casing rules.
     */
    fun applyPredictedCasing(typed: String, suggestion: String, textBeforeSelection: String): String {
        // Special case: lone "i" should always become "I"
        if (typed.equals("i", ignoreCase = true) && suggestion.equals("i", ignoreCase = true)) {
            return "I"
        }
        
        // Apply contraction shortcuts (im -> I'm, etc.)
        val contractionResult = CONTRACTION_SHORTCUTS[typed.lowercase()]
        if (contractionResult != null && suggestion.replace("'", "").equals(typed, ignoreCase = true)) {
            return contractionResult
        }
        
        // Check if we're at sentence start (empty or after period/newline)
        val trimmed = textBeforeSelection.trimEnd()
        val atSentenceStart = trimmed.isEmpty() || 
                             trimmed.endsWith('.') || 
                             trimmed.endsWith('!') || 
                             trimmed.endsWith('?') ||
                             trimmed.endsWith('\n')

        if (atSentenceStart && typed.firstOrNull()?.isLowerCase() == true) {
            // At sentence start, force capitalize first letter
            val cased = applyCasingPattern(typed, suggestion)
            return if (cased.firstOrNull()?.isLowerCase() == true) {
                cased.replaceFirstChar { it.titlecase() }
            } else {
                cased
            }
        }
        
        // Otherwise use normal casing rules
        return applyCasingPattern(typed, suggestion)
    }



    /**
     * Get all words from the dictionary for swipe/glide typing.
     * Returns words in lowercase (as stored in SymSpell).
     */
    fun getAllWords(context: Context): List<String> {
        if (!isReady) return emptyList()
        
        return try {
            val words = mutableListOf<String>()
            BufferedReader(InputStreamReader(context.assets.open(DICT_ASSET_PATH))).useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.isNotEmpty()) {
                        val word = parts[0].lowercase()
                        if (word.isNotBlank() && word.length >= 2) {
                            words.add(word)
                        }
                    }
                }
            }
            android.util.Log.i("SymSpellManager", "Extracted ${words.size} words for swipe typing")
            words
        } catch (e: Exception) {
            android.util.Log.w("SymSpellManager", "Failed to extract words for swipe", e)
            emptyList()
        }
    }
}
