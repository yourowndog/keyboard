package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.max
import dev.patrickgold.florisboard.ime.nlp.shared.BigramTable
import dev.patrickgold.florisboard.ime.nlp.shared.CasingUtils
import dev.patrickgold.florisboard.ime.nlp.shared.CandidateProvenance
import dev.patrickgold.florisboard.ime.nlp.shared.CandidateScorer
import dev.patrickgold.florisboard.ime.nlp.shared.ContractionRules
import dev.patrickgold.florisboard.ime.nlp.shared.DictionaryRepository
import dev.patrickgold.florisboard.ime.nlp.shared.FallbackCandidate
import dev.patrickgold.florisboard.ime.nlp.shared.FallbackEngineMode

object SymSpellManager {
    @Volatile private var isReady = false
    private var loadedWordCount: Int = 0
    private var lastError: String? = null
    private var initStatus: String = "NOT_STARTED"

    fun isReady(): Boolean = isReady

    fun getWordCount(): Int {
        return loadedWordCount
    }

    fun getPrefixIndexSize(): Int {
        return prefixIndex.size
    }

    fun getLastError(): String? = lastError

    fun getInitStatus(): String = initStatus

    // Prefix index for fast autocomplete - maps prefix (1-3 chars) to words starting with it
    private var prefixIndex: Map<String, List<Pair<String, Long>>> = emptyMap()

    /**
     * Cap for 1- and 2-character prefix buckets only. At that depth the user has
     * given almost no evidence, so frequency order is all there is and a deep
     * bucket is wasted memory. Deeper buckets are kept whole — see buildPrefixIndex.
     */
    private const val SHALLOW_PREFIX_BUCKET_CAP = 300
    private var appContextRef: Context? = null

    // Config
    private const val DICT_ASSET_PATH = "ime/dict/unified_dictionary.tsv"
    private const val BIGRAM_ASSET_PATH = "ime/dict/final_mobile_bigrams.tsv"
    private const val CULLED_SCORE = Double.MAX_VALUE
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
    private val BLACKLIST = setOf("wont", "hows", "cant", "dont", "isnt", "arent", "didnt", "couldnt", "wouldnt", "shouldnt", "wasnt", "werent", "hasnt", "havent", "hadnt")

    // Bigram data now provided by shared BigramTable singleton

    fun init(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                MemProfiler.log("dict:init_start")
                initStatus = "STEP1_DICT_MGR"
                // Ensure DictionaryManager is ready
                dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.init(context)
                
                // Initialize PersonalPreferences with context to load protected forms asset
                dev.patrickgold.florisboard.ime.nlp.PersonalPreferences.init(context)

                initStatus = "STEP2_LOAD_DICTIONARY"
                // Single shared dictionary load; replaces SymSpell's deletion index
                // (which cost ~9s and 100MB+ of heap) with scan-based candidate lookup.
                DictionaryRepository.ensureLoaded(context)
                MemProfiler.log("dict:repository_loaded")

                initStatus = "STEP6_BIGRAM_TABLE"
                BigramTable.load(context)
                MemProfiler.log("symspell:bigram_table_loaded")

                initStatus = "STEP6b_PHRASE_TABLE"
                try {
                    dev.patrickgold.florisboard.ime.nlp.shared.PhraseTable.load(context)
                } catch (e: Exception) {
                    android.util.Log.w("SymSpellManager", "PhraseTable loading failed (non-fatal): ${e.message}")
                }

                initStatus = "STEP7_USER_OVERRIDES"
                // Inject must-win personal words until we wire user dictionary
                USER_OVERRIDES.forEach { (word, _) -> DictionaryRepository.addOverride(word) }

                initStatus = "STEP8_PREFIX_INDEX"
                // 4. Build prefix index for autocomplete
                appContextRef = context
                buildPrefixIndex()
                MemProfiler.log("dict:prefix_index_built")

                val loadedWords = DictionaryRepository.size
                loadedWordCount = loadedWords
                isReady = loadedWords > 0
                initStatus = if (isReady) "DONE($loadedWords words, ${prefixIndex.size} prefixes)" else "DONE_EMPTY"
                android.util.Log.i(
                    "SymSpellManager",
                    "Reflexes Ready: Loaded $loadedWords words from $DICT_ASSET_PATH with bigrams from $BIGRAM_ASSET_PATH, prefix index: ${prefixIndex.size} prefixes"
                )
                if (!isReady) {
                    lastError = "Dictionary loaded 0 words from $DICT_ASSET_PATH"
                    android.util.Log.w("SymSpellManager", "Reflexes dictionary is empty; keeping autocorrect disabled")
                }
            } catch (e: Exception) {
                lastError = "${e::class.simpleName}: ${e.message}"
                initStatus = "FAILED@$initStatus"
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

    /**
     * Check if a word exists in the dictionary (including user cache).
     */
    fun hasWord(word: String): Boolean {
        if (!isReady) return false

        // Check user cache first
        if (userWordsCache.any { it.equals(word, ignoreCase = true) }) return true

        // Check main dictionary
        return DictionaryRepository.contains(word)
    }

    // Cache of user dictionary words for the current locale
    @Volatile private var userWordsCache: List<String> = emptyList()

    fun updateUserDictCache(words: List<String>) {
        userWordsCache = words
    }

    /**
     * SymSpell-only fallback retrieval used when the primary ngram/neural engine
     * is unavailable. Returns structured [FallbackCandidate]s that retain each
     * candidate's true provenance, real edit distance, contraction license, and
     * engine mode, instead of collapsing everything to a bare string. The Gate
     * decision is made downstream by [FallbackCorrection]; this method only
     * establishes truthful retrieval evidence.
     */
    fun suggest(input: String, previousWord: String? = null): List<FallbackCandidate> {
         android.util.Log.d("SymSpell_Debug", "suggest() called: input='$input', prev='$previousWord'")

         if (!isReady) {
             android.util.Log.e("SymSpell_Debug", "NOT READY! isReady=false")
             return emptyList()
         }
         if (input.length == 1) {
             // Keep exactly what the user typed (single chars are never corrected here).
             // No correction evidence exists — represent it as a verbatim candidate.
             android.util.Log.d("SymSpell_Debug", "Single char input, returning as-is: '$input'")
             return listOf(literalCandidate(input))
         }

        val normalized = input.lowercase()
        val upperCount = input.count { it.isUpperCase() }
        val suggestions = DictionaryRepository.findWithinTwoEdits(normalized)
            .filterNot { candidate -> PersonalPreferences.isAntiCorrection(input, candidate.term) }
        android.util.Log.d("SymSpell_Debug", "Dictionary lookup returned ${suggestions.size} raw candidates")
        val prev = previousWord?.lowercase()
        val ignoreManager = dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default()

        // The contraction shortcut is a CONTRACTION_RULE candidate, not an edit.
        // Attach the exact static license only when ContractionRules can issue one;
        // shortcuts without a licensed form (e.g. wheres) stay unlicensed rather
        // than borrowing fabricated edit-distance authority.
        val contractionRaw = ContractionRules.LEGACY_FALLBACK_SHORTCUTS[normalized]
        val contractionTop = contractionRaw
            ?.let { raw ->
                val cased = applyCasingPattern(input, raw)
                if (PersonalPreferences.isAntiCorrection(input, cased)) return@let null
                FallbackCandidate(
                    rawCandidate = raw,
                    casedCandidate = cased,
                    provenance = CandidateProvenance.CONTRACTION_RULE,
                    editDistance = null,
                    engineMode = FallbackEngineMode.SYMSPELL_ONLY,
                    contractionLicense = ContractionRules.resolveStatic(normalized)?.license,
                )
            }
        val mapped = suggestions
            .sortedBy { candidate ->
                val term = candidate.term
                val lowerTerm = term.lowercase()

                // CULLING: Filter out 2-letter words not in whitelist
                if (lowerTerm.length == 2 && !TWO_LETTER_WHITELIST.contains(lowerTerm)) {
                    return@sortedBy CULLED_SCORE
                }

                // Filter by ignore list and blacklist
                if (ignoreManager.isUserIgnored(input, term)) {
                    return@sortedBy CULLED_SCORE
                }
                if (BLACKLIST.contains(lowerTerm)) {
                    return@sortedBy CULLED_SCORE
                }

                // Use unified scorer
                val isUserWord = userWordsCache.any { it.equals(term, ignoreCase = true) }
                val score = CandidateScorer.score(
                    typed = normalized,
                    candidate = lowerTerm,
                    editDistance = candidate.distance,
                    prevWord = prev,
                    isInUserDict = isUserWord,
                    frequency = candidate.frequency
                )
                if (score >= CULLED_SCORE - 0.1) {
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
                    // Genuine edit-distance correction: retain the real distance.
                    FallbackCandidate(
                        rawCandidate = term,
                        casedCandidate = applyCasingPattern(input, term),
                        provenance = CandidateProvenance.EDIT_DISTANCE,
                        editDistance = candidate.distance,
                        engineMode = FallbackEngineMode.SYMSPELL_ONLY,
                    )
                }
            }
        android.util.Log.d("SymSpell_Debug", "After filtering: ${mapped.size} candidates")
        val withContraction = buildList {
            if (contractionTop != null) {
                add(contractionTop)
                if (mapped.isNotEmpty()) {
                    // If the top mapped suggestion is the same word as the contraction, skip it
                    if (mapped.first().casedCandidate != contractionTop.casedCandidate) {
                        addAll(mapped)
                    } else {
                        addAll(mapped.drop(1))
                    }
                }
            } else if (mapped.isNotEmpty()) {
                addAll(mapped)
            }
        }

        val finalResult = if (withContraction.isNotEmpty()) withContraction else listOf(literalCandidate(input))
        android.util.Log.d(
            "SymSpell_Debug",
            "suggest() returning ${finalResult.size} suggestions: ${finalResult.map { it.casedCandidate }}",
        )
        return finalResult
    }

    /**
     * A verbatim candidate: the fallback engine had no correction to offer, so it
     * returns the typed word unchanged. Carries no correction provenance — the
     * Gate treats it as non-committable, which is truthful rather than a fabricated
     * edit.
     */
    private fun literalCandidate(input: String) = FallbackCandidate(
        rawCandidate = input,
        casedCandidate = input,
        provenance = CandidateProvenance.LEGACY_FALLBACK,
        editDistance = null,
        engineMode = FallbackEngineMode.SYMSPELL_ONLY,
    )

    data class RawCandidate(val term: String, val distance: Double)

    /**
     * Returns raw candidates from SymSpell without applying the internal hardcoded ranking/fixing logic.
     * This allows external engines (like NgramSuggestionEngine) to apply their own scoring.
     * 
     * Note: 2-letter words are filtered to whitelist only.
     */
    fun findCandidates(input: String): List<RawCandidate> {
        if (!isReady) return emptyList()

        // All dictionary words within edit distance 2
        val suggestions = DictionaryRepository.findWithinTwoEdits(input.lowercase())
        
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
        val words = DictionaryRepository.lowercaseFrequencies.keys.filter { it.length >= 2 }
        android.util.Log.i("SymSpellManager", "Extracted ${words.size} words for swipe typing")
        return words
    }
    
    /**
     * Build prefix index from dictionary for fast autocomplete lookups.
     * Maps 1-3 character prefixes to words and their frequencies.
     */
    private fun buildPrefixIndex() {
        try {
            val indexMap = mutableMapOf<String, MutableList<Pair<String, Long>>>()

            for ((word, freq) in DictionaryRepository.lowercaseFrequencies) {
                // Skip very short or blank words
                if (word.length < 2 || word.isBlank()) continue

                // Filter 2-letter words to whitelist only
                if (word.length == 2 && !TWO_LETTER_WHITELIST.contains(word)) continue

                // Index by 1, 2, and 3 character prefixes
                for (prefixLen in 1..minOf(3, word.length)) {
                    val prefix = word.take(prefixLen)
                    indexMap.getOrPut(prefix) { mutableListOf() }.add(word to freq)
                }
            }

            // Sort each prefix's words by frequency (descending).
            //
            // Only the 1- and 2-character buckets are capped. Capping the
            // 3-character buckets (which this used to do, at 100) silently broke
            // completion: the bucket is chosen by the first 3 characters but
            // filtered by everything the user typed, so a word cut from the
            // 3-char bucket could never be recovered by typing more of it.
            // Measured over the shipped dictionary, that cap left completion
            // stuck near 67% no matter how much of a word was typed; without it,
            // six typed characters reach 93% and seven reach 98%.
            prefixIndex = indexMap.mapValues { (prefix, words) ->
                val sorted = words.sortedByDescending { it.second }
                if (prefix.length <= 2) sorted.take(SHALLOW_PREFIX_BUCKET_CAP) else sorted
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
