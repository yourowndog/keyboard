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
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.SuggestionRequest
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SymSpellManager

import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
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


    private var ngramEngine: dev.patrickgold.florisboard.ime.nlp.NgramSuggestionEngine? = null

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
        try {
            val unigrams = appContext.assets.open("ime/dict/unified_dictionary.tsv")
            ngramEngine = dev.patrickgold.florisboard.ime.nlp.NgramSuggestionEngine.fromStreams(unigrams)
            dev.patrickgold.florisboard.lib.devtools.flogInfo { "NgramSuggestionEngine loaded successfully" }
        } catch (e: Exception) {
            dev.patrickgold.florisboard.lib.devtools.flogError { "Failed to load NgramEngine: ${e.message}" }
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
        val previousWord = lastWordBefore(textBeforeCurrentWord)
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

        // DELEGATE TO NEW ENGINE (Brain Transplant)
        // 1. Retrieve candidates from SymSpell (The Retriever)
        val rawCandidates = dev.patrickgold.florisboard.ime.nlp.SymSpellManager.findCandidates(currentWordRaw)
        
        // 2. Rank using NgramEngine (The Judge)
        val engine = ngramEngine
        if (engine != null) {
            // Map SymSpell items to (Term, Distance) pairs
            val mappedCandidates = rawCandidates.map { candidate -> candidate.term to candidate.distance }
            val rankedSuggestions = engine.rank(mappedCandidates, currentWordRaw, previousWord)
            return rankedSuggestions.map { candidate ->
                if (candidate is WordSuggestionCandidate) {
                    // Use SymSpellManager's casing logic which handles i→I, proper nouns, sentence start
                    val casedText = SymSpellManager.applyPredictedCasing(
                        typed = currentWordRaw,
                        suggestion = candidate.text.toString(),
                        textBeforeSelection = textBeforeCurrentWord
                    )
                    val shouldCommit = candidate.isEligibleForAutoCommit || casedText != currentWordRaw
                    
                    // DEBUG: Uncomment to trace casing logic
                    // android.util.Log.d("LatinProvider", "Input: '$currentWordRaw' | Cased: '$casedText' | Commit: $shouldCommit")

                    candidate.copy(
                        text = casedText,
                        isEligibleForAutoCommit = shouldCommit,
                        sourceProvider = this
                    )
                } else {
                    candidate
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
    }

    private fun lastWordBefore(text: String): String? {
        if (text.isBlank()) return null
        val trimmed = text.trimEnd()
        val match = Regex("([A-Za-z']+)[^A-Za-z']*$").find(trimmed) ?: return null
        return match.groupValues.getOrNull(1)
    }


}
