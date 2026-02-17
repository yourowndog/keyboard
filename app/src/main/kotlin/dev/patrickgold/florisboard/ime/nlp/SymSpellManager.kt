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
import dev.patrickgold.florisboard.ime.core.KeyboardLayout
import dev.patrickgold.florisboard.ime.nlp.shared.BigramTable
import dev.patrickgold.florisboard.ime.nlp.shared.CasingUtils
import dev.patrickgold.florisboard.ime.nlp.shared.CandidateScorer

object SymSpellManager {
    private var symSpell: SymSpell? = null
    @Volatile private var isReady = false
    
    fun isReady(): Boolean = isReady

    fun getWordCount(): Int {
        return symSpell?.wordCount ?: 0
    }

    fun getPrefixIndexSize(): Int {
        return prefixIndex.size
    }

    // Prefix index for fast autocomplete - maps prefix (1-3 chars) to words starting with it
    private var prefixIndex: Map<String, List<Pair<String, Long>>> = emptyMap()
    private var appContextRef: Context? = null

    // Config
    private const val MAX_EDIT_DISTANCE = 2
    private const val PREFIX_LENGTH = 7
    private const val DICT_ASSET_PATH = "ime/dict/unified_dictionary.tsv"
    private const val SWIPE_DICT_PATH = "ime/dict/unified_dictionary.tsv"  // Same dict for everything
    private const val BIGRAM_ASSET_PATH = "ime/dict/final_mobile_bigrams.tsv"
    // User overrides to Ensure these specific words/frequencies are respected
    private val USER_OVERRIDES = listOf(
        "kiry" to Double.MAX_VALUE,
        "congrats" to Double.MAX_VALUE,
        "Claira" to Double.MAX_VALUE, 
        "Christmas" to Double.MAX_VALUE,
        "min" to Double.MAX_VALUE,
        "Mom" to Double.MAX_VALUE,
        "Aorus" to Double.MAX_VALUE,
        "GPU" to Double.MAX_VALUE,
        "Hurray" to Double.MAX_VALUE,  // Exclamation (not "Hurrah")
        "Sam" to Double.MAX_VALUE,     // Proper noun - always capitalize
        "Sam's" to Double.MAX_VALUE,   // Possessive form
        "uh" to Double.MAX_VALUE,      // Common hesitation (not "uhuru")
        "oof" to Double.MAX_VALUE,     // Exclamation (not "Ok")
        "bc" to Double.MAX_VALUE,      // Abbreviation for "because"
        "pls" to Double.MAX_VALUE,     // Abbreviation for "please"
        "idk" to Double.MAX_VALUE,     // Abbreviation "I don't know"
        "wtf" to Double.MAX_VALUE,     // Common abbrev
    )
    // Prefer common contractions before running SymSpell so "im" maps to "I'm" instead of "pm".
    private val CONTRACTION_SHORTCUTS = mapOf(
        "im" to "I'm",
        "i'm" to "I'm",
        "ive" to "I've",
        // "id" removed - now handled by context-aware logic in CandidateScorer
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
        // "were" to "we're", // Removed: handled by context logic in fix()
        "lets" to "let's",
        "thats" to "that's",
        "whos" to "who's",
        "whats" to "what's",
        "wheres" to "where's",
        "theres" to "there's",
        // "well" to "we'll", // Removed: handled by context logic in fix()
        "hell" to "he'll",
        "shell" to "she'll",
        "its" to "it's",
        "ac" to "AC",      // air conditioning
        "itd" to "it'd",   // sloppy it'd typing
    )
    val PROPER_OVERRIDES = setOf(
        "kiry", "kiry's",
        "sam", "sam's",
        "I'd",
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
        "claira", "claira's",
        "christmas",
        "aorus",
        "gpu", "gpu's",
        "cr",
    )

    // Tracks whether the last autocorrect was rejected; if so, skip autocorrect once.
    @Volatile private var skipNextAutocorrect = false
    
    // SMART SESSION: Track rejection state to infer intent
    // Stores: Pair(OriginalTyped, RejectedCorrection-aka-what-it-became)
    @Volatile private var lastRejectedState: Pair<String, String>? = null

    // QWERTY Neighbor Map - now uses shared KeyboardLayout
    private val KEYBOARD_NEIGHBORS get() = KeyboardLayout.QWERTY_NEIGHBORS
    
    // Words preceding "were" that imply it should STAY "were" (past tense)
    // e.g. "they were", "we were", "you were"
    private val PREV_WORDS_FOR_WERE = setOf(
        "we", "they", "you", "there", "here", "who", "which", "what", "that", "these", "those"
    )

    // Words preceding "well" that imply it should STAY "well" (adverb/interjection)
    // e.g. "oh well", "very well", "doing well"
    private val PREV_WORDS_FOR_WELL = setOf(
        "oh", "ah", "very", "quite", "as", "doing", "went", "worked", "done", "known", "start", "damn"
    )
    
    private val BLACKLIST = setOf("wont", "hows", "cant", "dont", "isnt", "arent", "didnt", "couldnt", "wouldnt", "shouldnt", "wasnt", "werent", "hasnt", "havent", "hadnt")

    // Bigram data now provided by shared BigramTable singleton

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
                BigramTable.load(context)  // Load shared bigram table once

                // Inject must-win personal words until we wire user dictionary
                USER_OVERRIDES.forEach { (word, freq) -> instance.createDictionaryEntry(word, freq) }

                // 4. Build prefix index for autocomplete
                appContextRef = context
                buildPrefixIndex(context)

                val loadedWords = holder.wordCount
                symSpell = instance
                isReady = loadedWords > 0
                android.util.Log.i(
                    "SymSpellManager",
                    "Reflexes Ready: Loaded $loadedWords words from $DICT_ASSET_PATH with bigrams from $BIGRAM_ASSET_PATH, prefix index: ${prefixIndex.size} prefixes"
                )
                if (!isReady) {
                    android.util.Log.w("SymSpellManager", "Reflexes dictionary is empty; keeping autocorrect disabled")
                }
            } catch (e: Exception) {
                android.util.Log.e("SymSpellManager", "Reflexes Failed to Load Dictionary", e)
            }
        }
    }

    fun nextWordPredictions(prev: String?, max: Int = 3): List<String> {
        return BigramTable.get()?.predictNext(prev, max) ?: emptyList()
    }

    // Whitelist for 2-letter words. All others are culled to prevent "si", "da", "yo" etc.
    private val TWO_LETTER_WHITELIST = setOf(
        "am", "an", "as", "at", "be", "by", "do", "go", "ha", "he", "hi", "if", "in", "is", "it", 
        "me", "my", "no", "of", "oh", "ok", "on", "or", "ox", "so", "to", "up", "us", "we", "yo",
        "bb", "rq", "fr", "ac"  // Sam's custom slang/abbreviations
    )

    // Delegate to CandidateScorer for unified spatial cost calculation
    fun spatialCost(typed: String, candidate: String): Double {
        return CandidateScorer.spatialCost(typed, candidate)
    }

    fun markNextAsUserRejected(originalTyped: String, rejectedCorrection: String) {
        skipNextAutocorrect = true
        lastRejectedState = Pair(originalTyped, rejectedCorrection)
    }
    
    /**
     * Check if a word exists in the dictionary (including user cache).
     */
    fun hasWord(word: String): Boolean {
        if (!isReady) return false
        val instance = symSpell ?: return false
        
        // Check user cache first
        if (userWordsCache.any { it.equals(word, ignoreCase = true) }) return true
        
        // Check main dictionary
        val matches = instance.lookup(word.lowercase(), Verbosity.Top, 0.0)
        return matches.isNotEmpty() && matches.first().distance == 0.0
    }

    // Cache of user dictionary words for the current locale
    @Volatile private var userWordsCache: List<String> = emptyList()

    fun updateUserDictCache(words: List<String>) {
        userWordsCache = words
    }

    fun fix(input: String, previousWord: String? = null): String {
        // SMART SESSION: Check if this input follows a rejection
        lastRejectedState?.let { (originalTyped, rejectedCorrection) ->
            // If user typed something new (input) immediately after rejecting,
            // we assume 'originalTyped' was meant to be 'input'.
            HarvestManager.logIntent(originalTyped, rejectedCorrection, input)
            lastRejectedState = null
        }
    
        val lower = input.lowercase()
        // Fast-path for contractions so missing apostrophes don't divert to unrelated words.
        CONTRACTION_SHORTCUTS[lower]?.let { contraction ->
            return applyCasingPattern(input, contraction)
        }
        
        // Context-aware contraction logic
        if (lower == "were") {
            val prev = previousWord?.lowercase() ?: ""
            // If prev word is NOT in the list of "words that precede 'were'", assume "we're"
            // Default to "we're" at start of sentence (prev is empty/null)
            if (prev.isEmpty() || !PREV_WORDS_FOR_WERE.contains(prev)) {
                return applyCasingPattern(input, "we're")
            }
        }
        if (lower == "well") {
            val prev = previousWord?.lowercase() ?: ""
            // If prev word is NOT in list, assume "we'll"
            // But "well" is common at start of sentence ("Well, ..."), so if prev is empty, keep "Well"
            if (prev.isNotEmpty() && !PREV_WORDS_FOR_WELL.contains(prev)) {
                return applyCasingPattern(input, "we'll")
            }
        }
        
        // Typo fixes
        if (lower == "ir") return "it"
        if (lower == "s") return "a"
        if (lower == "km") {
             // 95% case: "km" -> "I'm". Exception: if prev word is a number?
             // Simple heuristic: if prev word is NOT a number, do correcting.
             // If we don't have number detection handy, just correct it as requested.
             return applyCasingPattern(input, "I'm")
        }

        if (!isReady) return input
        if (skipNextAutocorrect) {
            skipNextAutocorrect = false
            return input
        }
        val instance = symSpell ?: return input
        // Handle single-letter inputs explicitly to avoid over-correcting every keystroke.
        // Special case: lone "i" should become "I"
        if (input.length == 1) {
            return if (input == "i") "I" else input
        }

        // Reflexes: Fast correction
        val normalized = input.lowercase()
        val normalizedNoApos = normalized.replace("'", "")
        
        // Check against ignore list
        val dictManager = dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()
        
        // Check User Dictionary Cache (Highest Priority)
        // If the user has explicitly added this word, we MUST respect it.
        if (userWordsCache.any { it.equals(input, ignoreCase = true) }) {
             return input
        }

        // TRUST REAL WORDS: If the user typed a valid dictionary word, we generally keep it.
        // HOWEVER, we must still check if it needs capitalization (christmas -> Christmas).
        val exactMatches = instance.lookup(normalized, Verbosity.Top, 0.0)
        android.util.Log.d("SymSpell", "[$input] exactMatches(dist=0): ${exactMatches.map { "${it.term}:${it.distance}" }}")
        
        if (exactMatches.isNotEmpty() && exactMatches.first().distance == 0.0) {
            val match = exactMatches.first()
            android.util.Log.d("SymSpell", "[$input] -> TRUST (exact match found) but applying casing")
            // Apply casing logic to the exact match (e.g. christmas -> Christmas)
            return applyCasingPattern(input, match.term)
        }

        // Use Verbosity.All to ensure we find distance 2 candidates
        val suggestions = instance.lookup(normalized, Verbosity.All, MAX_EDIT_DISTANCE.toDouble())
        android.util.Log.d("SymSpell", "[$input] suggestions(dist<=2): ${suggestions.take(5).map { "${it.term}:${it.distance}" }}")
        
        // Also check if any user dictionary word is a close match and should win
        val bestUserMatch = userWordsCache.firstOrNull { userWord ->
            val dist = DamerauLevenshteinDistance().getDistance(normalized, userWord.lowercase())
            dist <= MAX_EDIT_DISTANCE
        }
        if (bestUserMatch != null) {
            // User added this word, prioritize it
            return applyCasingPattern(input, bestUserMatch)
        }

        val prev = previousWord?.lowercase()

        // If there is an apostrophe variant that is the same letters without apostrophe, prefer it.
        val apostropheCandidate = suggestions.firstOrNull { cand ->
            val candLower = cand.term.lowercase()
            candLower.contains('\'') && candLower.replace("'", "") == normalizedNoApos
        }

        // Score and rank candidates using unified CandidateScorer
        val scoredCandidates = suggestions.map { candidate ->
            val term = candidate.term
            val lowerTerm = term.lowercase()
            
            // CULLING: Filter out 2-letter words not in whitelist
            if (lowerTerm.length == 2 && !TWO_LETTER_WHITELIST.contains(lowerTerm)) {
                return@map Triple(candidate, CandidateScorer.CULLED_SCORE, "2-letter cull")
            }
            
            // Filter by ignore list and blacklist
            if (dictManager.isUserIgnored(input, term)) {
                return@map Triple(candidate, CandidateScorer.CULLED_SCORE, "ignored")
            }
            if (BLACKLIST.contains(lowerTerm)) {
                return@map Triple(candidate, CandidateScorer.CULLED_SCORE, "blacklist")
            }
            
            // Use unified scorer for all other scoring
            val isUserWord = userWordsCache.any { it.equals(term, ignoreCase = true) }
            val score = CandidateScorer.score(
                typed = normalized,
                candidate = lowerTerm,
                editDistance = candidate.distance,
                prevWord = prev,
                isInUserDict = isUserWord,
            )
            
            Triple(candidate, score, "score=${"%.2f".format(score)}")
        }.sortedBy { it.second }
        
        // Log top 3 candidates for debugging
        if (scoredCandidates.isNotEmpty()) {
            android.util.Log.d("SymSpell", "[$input] Top candidates: ${scoredCandidates.take(3).map { "${it.first.term}:${it.second}(${it.third})" }}")
        }
        
        val suggestion = scoredCandidates.firstOrNull()?.first?.term ?: return input

        // If we landed on the original input but have a matching apostrophe candidate, pick that instead.
        val finalSuggestion = when {
            suggestion == input && apostropheCandidate != null -> apostropheCandidate.term
            else -> suggestion
        }

        // Check against ignore list
        if (dictManager.isUserIgnored(input, finalSuggestion)) {
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
         android.util.Log.d("SymSpell_Debug", "suggest() called: input='$input', prev='$previousWord'")

         if (!isReady) {
             android.util.Log.e("SymSpell_Debug", "NOT READY! isReady=false")
             return emptyList()
         }
         val instance = symSpell ?: run {
             android.util.Log.e("SymSpell_Debug", "symSpell instance is NULL!")
             return emptyList()
         }

         if (input.length == 1) {
             // Mirror the fix() behavior: keep exactly what the user typed.
             android.util.Log.d("SymSpell_Debug", "Single char input, returning as-is: '$input'")
             return listOf(input)
         }

        val normalized = input.lowercase()
        val upperCount = input.count { it.isUpperCase() }
        val suggestions = instance.lookup(normalized, Verbosity.All, MAX_EDIT_DISTANCE.toDouble())
        android.util.Log.d("SymSpell_Debug", "SymSpell.lookup returned ${suggestions.size} raw candidates")
        val prev = previousWord?.lowercase()
        val ignoreManager = dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()

        val contractionTop = CONTRACTION_SHORTCUTS[normalized]?.let { applyCasingPattern(input, it) }
        val mapped = suggestions
            .sortedBy { candidate ->
                val term = candidate.term
                val lowerTerm = term.lowercase()

                // CULLING: Filter out 2-letter words not in whitelist
                if (lowerTerm.length == 2 && !TWO_LETTER_WHITELIST.contains(lowerTerm)) {
                    return@sortedBy CandidateScorer.CULLED_SCORE
                }

                // Filter by ignore list and blacklist
                if (ignoreManager.isUserIgnored(input, term)) {
                    return@sortedBy CandidateScorer.CULLED_SCORE
                }
                if (BLACKLIST.contains(lowerTerm)) {
                    return@sortedBy CandidateScorer.CULLED_SCORE
                }

                // Use unified scorer
                val isUserWord = userWordsCache.any { it.equals(term, ignoreCase = true) }
                val score = CandidateScorer.score(
                    typed = normalized,
                    candidate = lowerTerm,
                    editDistance = candidate.distance,
                    prevWord = prev,
                    isInUserDict = isUserWord,
                )
                if (score >= CandidateScorer.CULLED_SCORE - 0.1) {
                    android.util.Log.d("SymSpell_Debug", "CULLED: '$lowerTerm' (score=$score)")
                }
                score
            }
            .mapNotNull { candidate ->
                val term = candidate.term
                if (upperCount >= 2 && term.lowercase() != normalized) {
                    // Skip suggestions that would mangle acronyms/proper nouns
                    android.util.Log.d("SymSpell_Debug", "Filtered uppercase: '$term'")
                    null
                } else {
                    applyCasingPattern(input, term)
                }
            }
        android.util.Log.d("SymSpell_Debug", "After filtering: ${mapped.size} candidates")
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

        val finalResult = if (withContraction.isNotEmpty()) withContraction else listOf(input)
        android.util.Log.d("SymSpell_Debug", "suggest() returning ${finalResult.size} suggestions: $finalResult")
        return finalResult
    }

    data class RawCandidate(val term: String, val distance: Double)

    /**
     * Returns raw candidates from SymSpell without applying the internal hardcoded ranking/fixing logic.
     * This allows external engines (like NgramSuggestionEngine) to apply their own scoring.
     * 
     * Note: 2-letter words are filtered to whitelist only.
     */
    fun findCandidates(input: String): List<RawCandidate> {
        if (!isReady) return emptyList()
        val instance = symSpell ?: return emptyList()
        
        // Use Verbosity.All to get all candidates within edit distance
        val suggestions = instance.lookup(input.lowercase(), Verbosity.All, MAX_EDIT_DISTANCE.toDouble())
        
        // Filter out 2-letter garbage words
        return suggestions
            .filter { candidate ->
                val term = candidate.term.lowercase()
                term.length != 2 || TWO_LETTER_WHITELIST.contains(term)
            }
            .map { RawCandidate(it.term, it.distance) }
    }

    /**
     * Match casing pattern of original input to suggestion.
     * Delegates to shared [CasingUtils] for consistency.
     */
    private fun applyCasingPattern(original: String, suggestion: String): String {
        return CasingUtils.matchCasingPattern(original, suggestion)
    }

    /**
     * Apply context-aware casing to a suggestion.
     * Delegates to shared [CasingUtils] for consistency.
     *
     * @param typed What the user typed
     * @param suggestion The raw suggestion
     * @param textBeforeSelection Text before cursor for sentence-start detection
     */
    fun applyPredictedCasing(typed: String, suggestion: String, textBeforeSelection: String): String {
        return CasingUtils.applyPredictedCasing(typed, suggestion, textBeforeSelection)
    }



    /**
     * Get all words from the dictionary for swipe/glide typing.
     * Returns words in lowercase (as stored in SymSpell).
     */
    fun getAllWords(context: Context): List<String> {
        if (!isReady) return emptyList()
        
        return try {
            val words = mutableListOf<String>()
            BufferedReader(InputStreamReader(context.assets.open(SWIPE_DICT_PATH))).useLines { lines ->
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
    
    /**
     * Build prefix index from dictionary for fast autocomplete lookups.
     * Maps 1-3 character prefixes to words and their frequencies.
     */
    private fun buildPrefixIndex(context: Context) {
        try {
            val indexMap = mutableMapOf<String, MutableList<Pair<String, Long>>>()
            
            BufferedReader(InputStreamReader(context.assets.open(DICT_ASSET_PATH))).useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size >= 2) {
                        val word = parts[0].lowercase()
                        val freq = parts[1].toLongOrNull() ?: 0L
                        
                        // Skip very short or blank words
                        if (word.length < 2 || word.isBlank()) return@forEach
                        
                        // Filter 2-letter words to whitelist only
                        if (word.length == 2 && !TWO_LETTER_WHITELIST.contains(word)) return@forEach
                        
                        // Index by 1, 2, and 3 character prefixes
                        for (prefixLen in 1..minOf(3, word.length)) {
                            val prefix = word.take(prefixLen)
                            indexMap.getOrPut(prefix) { mutableListOf() }.add(word to freq)
                        }
                    }
                }
            }
            
            // Sort each prefix's words by frequency (descending) and limit to top 100
            prefixIndex = indexMap.mapValues { (_, words) ->
                words.sortedByDescending { it.second }.take(100)
            }
            
            android.util.Log.i("SymSpellManager", "Built prefix index: ${prefixIndex.size} prefixes")
        } catch (e: Exception) {
            android.util.Log.e("SymSpellManager", "Failed to build prefix index", e)
            prefixIndex = emptyMap()
        }
    }
    
    /**
     * Find words starting with given prefix, ranked by bigram context.
     * This is how we get "going", "gonna", "getting" when typing "g" after "we are".
     * 
     * @param prefix What the user typed (e.g., "g")
     * @param previousWord Previous word for bigram context (e.g., "are")
     * @param limit Max candidates to return
     * @return Words starting with prefix, ranked by frequency + bigram bonus
     */
    fun findPrefixCandidates(prefix: String, previousWord: String?, limit: Int = 10): List<RawCandidate> {
        if (prefix.isBlank() || !isReady) return emptyList()
        
        val normalizedPrefix = prefix.lowercase()
        val lookupKey = normalizedPrefix.take(3)  // Use at most 3 chars for lookup
        
        val candidates = prefixIndex[lookupKey] ?: return emptyList()
        
        // Filter to exact prefix match and score by bigram
        val prevLower = previousWord?.lowercase()
        val scored = candidates
            .filter { (word, _) -> word.startsWith(normalizedPrefix) }
            .map { (word, freq) ->
                val bigramResult = CandidateScorer.bigramScore(prevLower, word)
                // Score: base frequency log + bigram boost (higher = better, but we negate for sorting)
                val score = -(ln(freq.toDouble() + 1.0) + bigramResult.bonus * 2.0)
                Triple(word, freq, score)
            }
            .sortedBy { it.third }
            .take(limit)
        
        // Convert to RawCandidate with distance = 0 (perfect prefix match)
        return scored.map { (word, _, score) -> RawCandidate(word, 0.0) }
    }
}
