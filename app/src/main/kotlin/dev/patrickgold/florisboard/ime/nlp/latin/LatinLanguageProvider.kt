/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import android.util.Log
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.NeuralScorer
import dev.patrickgold.florisboard.ime.nlp.SuggestionRequest
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SmolLMClient
import dev.patrickgold.florisboard.ime.nlp.SymSpellManager

import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.shared.BigramTable
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()
    private val prefs by FlorisPreferenceStore


    private var ngramEngine: dev.patrickgold.florisboard.ime.nlp.NgramSuggestionEngine? = null
    private var neuralScorer: NeuralScorer? = null

    /**
     * Last neural shadow decision + ngram top pick, exposed for NlpManager to persist
     * to JSONL through the privacy-safe (editor-aware) harvest path.
     * Cleared after each read to avoid stale repeats.
     */
    data class NeuralSnapshot(
        val typed: String,
        val prevWord: String?,
        val ngramTop: String?,
        val decision: NeuralScorer.Decision,
    )
    @Volatile var lastNeuralSnapshot: NeuralSnapshot? = null
        private set

    /** Consume the snapshot (returns it and clears). */
    fun consumeNeuralSnapshot(): NeuralSnapshot? {
        val snap = lastNeuralSnapshot
        lastNeuralSnapshot = null
        return snap
    }

    fun isNgramEngineReady(): Boolean = ngramEngine != null
    fun getNgramUnigramCount(): Int = ngramEngine?.unigramLogFreq?.size ?: 0

    override val providerId = ProviderId

    private fun String.isDigitsOnly(): Boolean = this.all { it.isDigit() }

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Here we have the chance to preload dictionaries and prepare a neural network for a specific language.
        // Is kept in sync with the active keyboard subtype of the user, however a new preload does not necessary mean
        // the previous language is not needed anymore (e.g. if the user constantly switches between two subtypes)

        // To read a file from the APK assets the following methods can be used:
        // appContext.assets.open()
        // appContext.assets.reader()
        // appContext.assets.bufferedReader()
        // appContext.assets.readText()
        // To copy an APK file/dir to the file system cache (appContext.cacheDir), the following methods are available:
        // appContext.assets.copy()
        // appContext.assets.copyRecursively()

        // The subtype we get here contains a lot of data, however we are only interested in subtype.primaryLocale and
        // subtype.secondaryLocales.

        // SymSpell handles all suggestions/corrections - legacy dictionary code removed

        // Initialize Ngram Engine for Ranking
        // Note: Only loads unigrams here. Bigrams are provided by the shared BigramTable singleton
        // which is loaded once in SymSpellManager.init()
        // preload() fires on every subtype switch; both loads below are subtype-independent,
        // so load once and keep. Reloading here previously leaked the replaced ONNX session
        // and re-parsed the full unified dictionary each time.
        if (ngramEngine == null) {
            dev.patrickgold.florisboard.ime.nlp.MemProfiler.log("provider:ngram_load_start")
            try {
                // Share the repository's log-frequency map by reference instead of
                // re-parsing the unified dictionary into a second 47MB copy.
                dev.patrickgold.florisboard.ime.nlp.shared.DictionaryRepository.ensureLoaded(appContext)
                ngramEngine = dev.patrickgold.florisboard.ime.nlp.NgramSuggestionEngine(
                    unigramLogFreq = dev.patrickgold.florisboard.ime.nlp.shared.DictionaryRepository.logFrequencies,
                )
                dev.patrickgold.florisboard.lib.devtools.flogInfo { "NgramSuggestionEngine loaded successfully" }
            } catch (e: Exception) {
                dev.patrickgold.florisboard.lib.devtools.flogError { "Failed to load NgramEngine: ${e.message}" }
            }
            dev.patrickgold.florisboard.ime.nlp.MemProfiler.log("provider:ngram_load_done")
        }

        if (neuralScorer == null) {
            neuralScorer = NeuralScorer.load(appContext)
            dev.patrickgold.florisboard.ime.nlp.MemProfiler.log("provider:neural_session_loaded")
        }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        return when (word.lowercase()) {
            // Use typo for typing errors
            "typo" -> SpellingResult.typo(arrayOf("typo1", "typo2", "typo3"))
            // Use grammar error if the algorithm can detect this. On Android 11 and lower grammar errors are visually
            // marked as typos due to a lack of support
            "gerror" -> SpellingResult.grammarError(arrayOf("grammar1", "grammar2", "grammar3"))
            // Use valid word for valid input
            else -> SpellingResult.validWord()
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val currentWordRaw = content.composingText.toString().trim()
        val textBeforeSelection = content.textBeforeSelection
        val textBeforeCurrentWord = if (textBeforeSelection.endsWith(currentWordRaw)) {
            textBeforeSelection.dropLast(currentWordRaw.length)
        } else {
            textBeforeSelection
        }
        val previousWords = wordsBefore(textBeforeCurrentWord, max = 2)
        val previousWord = previousWords.lastOrNull()
        val previous2Word = if (previousWord == null) null else previousWords.dropLast(1).lastOrNull()
        // If there is no composing text, surface next-word bigram predictions.
        // For single chars like "i", fall through to engine for correction (i -> I).
        if (currentWordRaw.isBlank()) {
            val nextWords = dev.patrickgold.florisboard.ime.nlp.SymSpellManager.nextWordPredictions(previousWord)
            if (nextWords.isNotEmpty()) {
                return nextWords.map { word ->
                    val casedText = SymSpellManager.applyPredictedCasing(
                        typed = currentWordRaw,
                        suggestion = word,
                        textBeforeSelection = textBeforeCurrentWord
                    )
                    WordSuggestionCandidate(
                        text = casedText,
                        secondaryText = null,
                        isEligibleForAutoCommit = false, // Don't auto-commit predictions
                        sourceProvider = this
                    )
                }
            }
            return emptyList()
        }

        // Special case: lone "i" -> "I" (SymSpell doesn't return "i" as candidate)
        if (currentWordRaw.equals("i", ignoreCase = true)) {
            return listOf(
                WordSuggestionCandidate(
                    text = "I",
                    secondaryText = null,
                    isEligibleForAutoCommit = true,
                    sourceProvider = this
                )
            )
        }
        
        // Fast-path for contractions: dont -> don't, etc.
        // Context-dependent words (were/we're, its/it's) resolve against the previous
        // word; everything else comes from the plain shortcut map.
        // PersonalPreferences wins over the shortcut map: words Sam types intentionally
        // (PERSONAL_VOCAB) and corrections he has explicitly blocked (ANTI_CORRECTIONS)
        // must never blind-fire from here.
        val contractionResult = dev.patrickgold.florisboard.ime.nlp.shared.CasingUtils.resolveContextualContraction(currentWordRaw, previousWord)
            ?: dev.patrickgold.florisboard.ime.nlp.shared.CasingUtils.CONTRACTION_SHORTCUTS[currentWordRaw.lowercase()]
        if (contractionResult != null &&
            !dev.patrickgold.florisboard.ime.nlp.PersonalPreferences.isPersonalVocab(currentWordRaw) &&
            !dev.patrickgold.florisboard.ime.nlp.PersonalPreferences.isAntiCorrection(currentWordRaw, contractionResult)
        ) {
            // Match the typed casing so "WERE" becomes "WE'RE", and capitalize at sentence
            // start even when the typed word is lowercase (auto-caps missed or was defeated):
            // the regular pipeline gets this from applyPredictedCasing, which this fast-path skips.
            val casedContraction = dev.patrickgold.florisboard.ime.nlp.shared.CasingUtils.matchCasingPattern(currentWordRaw, contractionResult)
            return listOf(
                WordSuggestionCandidate(
                    text = if (dev.patrickgold.florisboard.ime.nlp.shared.CasingUtils.isAtSentenceStart(textBeforeCurrentWord)) {
                        casedContraction.replaceFirstChar { it.titlecase() }
                    } else {
                        casedContraction
                    },
                    secondaryText = null,
                    isEligibleForAutoCommit = true,
                    sourceProvider = this
                )
            )
        }

        // DELEGATE TO NEW ENGINE (Brain Transplant)
        // 1. Retrieve candidates from SymSpell (The Retriever)
        //    - Prefix candidates: words starting with what user typed (autocomplete)
        //    - Edit-distance candidates: typo corrections
        val prefixCandidates = dev.patrickgold.florisboard.ime.nlp.SymSpellManager.findPrefixCandidates(
            currentWordRaw, previousWord, limit = 10
        )
        val editCandidates = dev.patrickgold.florisboard.ime.nlp.SymSpellManager.findCandidates(currentWordRaw)
        
        // Merge and deduplicate by term. Edit candidates FIRST: prefix candidates carry
        // distance = 0 ("perfect prefix match"), so if the prefix copy of a word survives
        // dedup it enters the ranker posing as an exact match and junk completions beat
        // real corrections (dure -> Durex over sure). The edit copy has the true distance.
        val seenTerms = mutableSetOf<String>()
        val rawCandidates = (editCandidates + prefixCandidates).filter { candidate ->
            seenTerms.add(candidate.term.lowercase())
        }
        // Completions are predictions, not corrections: they may be shown (and tapped),
        // but only edit-distance candidates may ever auto-commit (iOS/Gboard behavior).
        val editTerms = editCandidates.mapTo(mutableSetOf()) { it.term.lowercase() }
        
        // 2. Rank using NgramEngine (The Judge)
        val engine = ngramEngine
        if (engine != null) {
            // Map SymSpell items to (Term, Distance) pairs
            val mappedCandidates = rawCandidates.map { candidate -> candidate.term to candidate.distance }
            val ngramRanked = engine.rank(mappedCandidates, currentWordRaw, previousWord)
            val editOnlyCandidates = editCandidates.map { candidate -> candidate.term to candidate.distance }
            val neuralDecision = neuralScorer?.scoreCandidates(
                typed = currentWordRaw,
                prevWord = previousWord,
                prev2Word = previous2Word,
                candidates = neuralCandidates(
                    typed = currentWordRaw,
                    rawCandidates = editOnlyCandidates,
                    engine = engine,
                    prevWord = previousWord,
                ),
                threshold = prefs.suggestion.neuralThreshold.get(),
            )
            if (prefs.suggestion.neuralScorerShadow.get() && neuralDecision != null) {
                val ngramTopStr = ngramRanked.firstOrNull()?.text?.toString()
                logNeuralShadow(
                    typed = currentWordRaw,
                    previousWord = previousWord,
                    currentTop = ngramTopStr,
                    decision = neuralDecision,
                )
                // Surface for NlpManager's privacy-safe JSONL logging
                lastNeuralSnapshot = NeuralSnapshot(
                    typed = currentWordRaw,
                    prevWord = previousWord,
                    ngramTop = ngramTopStr,
                    decision = neuralDecision,
                )
            }
            
            // DISABLED: SmolLM neural reranking causes multi-second lag due to blocking HTTP calls
            // TODO: Implement async reranking - show n-gram results instantly, update after SmolLM responds
            // val candidateTexts = ngramRanked.take(5).mapNotNull { 
            //     (it as? WordSuggestionCandidate)?.text?.toString() 
            // }
            // val contextPrefix = "$textBeforeCurrentWord$currentWordRaw".takeLast(50).let {
            //     if (it.endsWith(currentWordRaw)) it.dropLast(currentWordRaw.length) else it
            // }
            // val neuralRanked = SmolLMClient.rerank(contextPrefix, candidateTexts)
            
            // "Valid Word Immunity": Check if the user's raw input is already a valid dictionary word.
            // If it is, we should be VERY conservative about auto-correcting it to something else 
            // (e.g., don't change "baby" -> "Babylon").
            val isInputValidWord = ngramRanked.any {
                it.text.toString().equals(currentWordRaw, ignoreCase = true) 
            }
            val liveNeuralDecision = neuralDecision.takeIf { prefs.suggestion.useNeuralScorer.get() }
            val rankedSuggestions = ngramRanked
            
            return rankedSuggestions.map { candidate ->
                if (candidate is WordSuggestionCandidate) {
                    // Use SymSpellManager's casing logic which handles i→I, proper nouns, sentence start
                    val casedText = SymSpellManager.applyPredictedCasing(
                        typed = currentWordRaw,
                        suggestion = candidate.text.toString(),
                        textBeforeSelection = textBeforeCurrentWord
                    )
                    
                    // Determine if we should auto-commit this candidate
                    // 1. Must be a "change" (otherwise why commit?)
                    val isChange = casedText != currentWordRaw
                    // 2. Is this just a casing fix? (e.g. "english" -> "English")
                    val isCasingFix = casedText.equals(currentWordRaw, ignoreCase = true)
                    
                    // LOGIC: Commit if it's a change AND (it's a typo OR it's just a casing fix)
                    // If input is valid, we ONLY allow casing fixes. We REJECT different words.
                    val neuralAllowsCommit = liveNeuralDecision == null ||
                        (liveNeuralDecision.shouldFire &&
                            candidate.text.toString().equals(liveNeuralDecision.top.term, ignoreCase = true))
                    // ANTI_CORRECTIONS: corrections Sam has explicitly blocked may still be
                    // shown as suggestions, but must never auto-commit.
                    val isBlocked = dev.patrickgold.florisboard.ime.nlp.PersonalPreferences.isAntiCorrection(currentWordRaw, casedText)
                    // PERSONAL_VOCAB: the scorer culls these but culled candidates stay in
                    // the ranked list, so the commit gate must enforce "never corrected" itself.
                    val isProtectedVocab = dev.patrickgold.florisboard.ime.nlp.PersonalPreferences.isPersonalVocab(currentWordRaw)
                    // Single letters committed with space are deliberate; only "i" -> "I"
                    // (handled by its own fast-path above) is a wanted single-char correction.
                    val isLongEnough = currentWordRaw.length >= 2 || isCasingFix
                    // Prefix-only completions never auto-commit (see editTerms above).
                    val isCorrection = candidate.text.toString().lowercase() in editTerms || isCasingFix
                    val shouldCommit = isChange && (!isInputValidWord || isCasingFix) && neuralAllowsCommit && !isBlocked && !isProtectedVocab && isLongEnough && isCorrection
                    
                    // DEBUG: Uncomment to trace casing logic
                    // android.util.Log.d("LatinProvider", "Input: '$currentWordRaw' | Cand: '$casedText' | Valid: $isInputValidWord | Commit: $shouldCommit")

                    candidate.copy(
                        text = casedText,
                        isEligibleForAutoCommit = shouldCommit,
                        sourceProvider = this
                    )
                } else {
                    candidate
                }
            }.let { suggestions ->
                // iOS/Gboard style: Always show typed word as first suggestion
                // When user picks this, it signals they want this exact word (INSISTED)
                val typedWordCandidate = WordSuggestionCandidate(
                    text = currentWordRaw,
                    secondaryText = null,
                    isEligibleForAutoCommit = false,  // Never auto-commit the typed word
                    isEligibleForUserRemoval = false,
                    sourceProvider = this
                )
                // Only add if typed word isn't already in suggestions
                val alreadyPresent = suggestions.any { 
                    it.text.toString().equals(currentWordRaw, ignoreCase = true) 
                }
                if (alreadyPresent || currentWordRaw.isBlank()) {
                    suggestions
                } else {
                    listOf(typedWordCandidate) + suggestions
                }
            }
        }

        // Fallback to old logic if engine failed to load
        val suggestions = dev.patrickgold.florisboard.ime.nlp.SymSpellManager.suggest(
            input = currentWordRaw,
            previousWord = previousWord,
        )
        val upperCount = currentWordRaw.count { it.isUpperCase() }

        return suggestions.map { word ->
            WordSuggestionCandidate(
                text = word,
                secondaryText = null,
                // Avoid auto-commit on uppercase-heavy tokens (acronyms/proper nouns)
                isEligibleForAutoCommit = upperCount < 2,
                sourceProvider = this
            )
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // We can use flogDebug, flogInfo, flogWarning and flogError for debug logging, which is a wrapper for Logcat
        flogDebug { candidate.toString() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        // Use the ngram engine's word list (already loaded in memory)
        val engine = ngramEngine ?: return SymSpellManager.getAllWords(appContext)
        return engine.unigramLogFreq.keys.toList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        // Query the live ngram engine which has AOSP unigram frequencies loaded
        val engine = ngramEngine ?: return 0.0
        return engine.unigramLogFreq[word.lowercase()] ?: 0.0
    }

    override suspend fun scoreWord(subtype: Subtype, word: String, prevWord: String?, editDistance: Double): Double {
        val engine = ngramEngine ?: return 0.0
        return engine.scoreWord(word, prevWord, editDistance)
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
        neuralScorer?.close()
        neuralScorer = null
    }

    private fun wordsBefore(text: String, max: Int): List<String> {
        if (text.isBlank()) return emptyList()
        return Regex("[A-Za-z']+")
            .findAll(text)
            .map { it.value.lowercase() }
            .toList()
            .takeLast(max)
    }

    private fun neuralCandidates(
        typed: String,
        rawCandidates: List<Pair<String, Double>>,
        engine: dev.patrickgold.florisboard.ime.nlp.NgramSuggestionEngine,
        prevWord: String?,
    ): List<NeuralScorer.Candidate> {
        val typedLower = typed.lowercase()
        val normalized = buildList {
            add(typedLower to 0.0)
            rawCandidates.forEach { (term, distance) ->
                if (!term.equals(typedLower, ignoreCase = true)) {
                    add(term.lowercase() to distance)
                }
            }
        }.distinctBy { it.first }.take(NeuralScorer.MAX_CANDIDATES)

        val bigramTable = BigramTable.get()
        return normalized.map { (term, distance) ->
            NeuralScorer.Candidate(
                term = term,
                editDistance = distance,
                lnFreq = engine.unigramLogFreq[term] ?: 0.0,
                bigramCount = if (prevWord == null) 0 else bigramTable?.getFrequency(prevWord, term) ?: 0,
            )
        }
    }

    private fun logNeuralShadow(
        typed: String,
        previousWord: String?,
        currentTop: String?,
        decision: NeuralScorer.Decision,
    ) {
        val neuralTop = decision.top.term
        val agrees = currentTop?.equals(neuralTop, ignoreCase = true) == true
        Log.i(
            "NeuralShadow",
            "model=v1 typed='$typed' prev='$previousWord' current='$currentTop' neural='$neuralTop' " +
                "typedP=${decision.typedProbability} topP=${decision.top.probability} " +
                "margin=${decision.margin} wouldFire=${decision.shouldFire} agrees=$agrees"
        )
    }


}
