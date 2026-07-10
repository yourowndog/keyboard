/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import androidx.lifecycle.MutableLiveData
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.kotlin.guardedByLock
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"

data class NlpStatus(
    val isSymSpellReady: Boolean,
    val symSpellWordCount: Int,
    val symSpellPrefixIndexSize: Int,
    val symSpellError: String?,
    val symSpellInitStatus: String,
    val isNgramEngineReady: Boolean,
    val ngramUnigramCount: Int,
    val isBigramTableReady: Boolean,
    val bigramFirstWordCount: Int,
    val bigramError: String?,
)

data class NlpLogEvent(
    val timestamp: Long,
    val typed: String,
    val prevWord: String?,
    val suggestions: List<String>,
)

class NlpManager(context: Context) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val _nlpLogs = MutableStateFlow<List<NlpLogEvent>>(emptyList())
    val nlpLogs = _nlpLogs.asStateFlow()

    // Last candidate list produced by the pipeline, keyed by the composing text it was
    // generated for. Lets commit-time harvest events attach the counterfactual candidates.
    @Volatile private var lastCandidatesSnapshot: Pair<String, List<Pair<String, Double>>>? = null

    /** Candidates (text to confidence) last shown for [typed], or null if stale. */
    fun candidatesSnapshotFor(typed: String): List<Pair<String, Double>>? {
        val (snapTyped, candidates) = lastCandidatesSnapshot ?: return null
        return if (snapTyped.equals(typed, ignoreCase = true)) candidates else null
    }

    fun addLogEvent(typed: String, prevWord: String?, suggestions: List<SuggestionCandidate>) {
        val candidatePairs = suggestions.take(8).map { it.text.toString() to it.confidence }
        if (typed.isNotEmpty()) {
            lastCandidatesSnapshot = typed to candidatePairs
        }
        HarvestManager.logSuggestionsShown(typed, prevWord, candidatePairs)

        // Persist neural shadow decision to JSONL through the editor-aware
        // (password-safe) harvest path. The snapshot was set by LatinLanguageProvider
        // during suggest() and is consumed here to avoid stale repeats.
        val latinProvider = runBlocking {
            providers.withLock { it[LatinLanguageProvider.ProviderId]?.provider as? LatinLanguageProvider }
        }
        latinProvider?.consumeNeuralSnapshot()?.let { snap ->
            val agrees = snap.ngramTop?.equals(snap.decision.top.term, ignoreCase = true) == true
            HarvestManager.logNeuralShadow(
                typed = snap.typed,
                prevWord = snap.prevWord,
                ngramTop = snap.ngramTop,
                neuralTop = snap.decision.top.term,
                typedP = snap.decision.typedProbability,
                topP = snap.decision.top.probability,
                margin = snap.decision.margin,
                wouldFire = snap.decision.shouldFire,
                agrees = agrees,
                ranked = snap.decision.ranked.map { it.term to it.probability },
            )
        }

        val event = NlpLogEvent(
            timestamp = System.currentTimeMillis(),
            typed = typed,
            prevWord = prevWord,
            suggestions = suggestions.map { it.text.toString() }
        )
        val currentLogs = _nlpLogs.value.toMutableList()
        currentLogs.add(0, event)
        if (currentLogs.size > 50) {
            currentLogs.removeAt(currentLogs.size - 1)
        }
        _nlpLogs.value = currentLogs
    }

    fun getStatus(): NlpStatus {
        val latinProvider = runBlocking {
            providers.withLock { it[LatinLanguageProvider.ProviderId]?.provider as? LatinLanguageProvider }
        }
        return NlpStatus(
            isSymSpellReady = SymSpellManager.isReady(),
            symSpellWordCount = SymSpellManager.getWordCount(),
            symSpellPrefixIndexSize = SymSpellManager.getPrefixIndexSize(),
            symSpellError = SymSpellManager.getLastError(),
            symSpellInitStatus = SymSpellManager.getInitStatus(),
            isNgramEngineReady = latinProvider?.isNgramEngineReady() ?: false,
            ngramUnigramCount = latinProvider?.getNgramUnigramCount() ?: 0,
            isBigramTableReady = dev.patrickgold.florisboard.ime.nlp.shared.BigramTable.get() != null,
            bigramFirstWordCount = dev.patrickgold.florisboard.ime.nlp.shared.BigramTable.get()?.getBigramCount() ?: 0,
            bigramError = dev.patrickgold.florisboard.ime.nlp.shared.BigramTable.getLastError()
        )
    }

    private val prefs by FlorisPreferenceStore
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val clipboardSuggestionProvider = ClipboardSuggestionProvider(context)
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    private val providers = guardedByLock {
        mapOf(
            LatinLanguageProvider.ProviderId to ProviderInstanceWrapper(LatinLanguageProvider(context)),
            HanShapeBasedLanguageProvider.ProviderId to ProviderInstanceWrapper(HanShapeBasedLanguageProvider(context)),
        )
    }
    // lock unnecessary because values constant
    private val providersForceSuggestionOn = mutableMapOf<String, Boolean>()

    private val internalSuggestionsGuard = Mutex()
    private var internalSuggestions by Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>()) { _, _, _ ->
        scope.launch { assembleCandidates() }
    }

    private val _activeCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val activeCandidatesFlow = _activeCandidatesFlow.asStateFlow()
    inline var activeCandidates
        get() = activeCandidatesFlow.value
        private set(v) {
            _activeCandidatesFlow.value = v
        }

    private val _phraseCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val phraseCandidatesFlow = _phraseCandidatesFlow.asStateFlow()

    fun updatePhraseCandidates(candidates: List<SuggestionCandidate>) {
        _phraseCandidatesFlow.value = candidates
    }

    fun clearPhraseCandidates() {
        _phraseCandidatesFlow.value = emptyList()
    }

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableLiveData(0)
    private val debugOverlayVersionSource = AtomicInteger(0)

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.clipboard.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.emoji.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
            preload(subtype)
        }
    }

    /**
     * Gets the punctuation rule from the currently active subtype and returns it. Falls back to a default one if the
     * subtype does not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getActivePunctuationRule(): PunctuationRule {
        return getPunctuationRule(subtypeManager.activeSubtype)
    }

    /**
     * Gets the punctuation rule from the given subtype and returns it. Falls back to a default one if the subtype does
     * not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getPunctuationRule(subtype: Subtype): PunctuationRule {
        return keyboardManager.resources.punctuationRules.value
            ?.get(subtype.punctuationRule) ?: PunctuationRule.Fallback
    }

    private suspend fun getSpellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    private suspend fun getSuggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            providers.withLock { providers ->
                subtype.nlpProviders.forEach { _, providerId ->
                    providers[providerId]?.let { provider ->
                        provider.createIfNecessary()
                        provider.preload(subtype)
                    }
                }
            }
        }
    }

    /**
     * Spell wrapper helper which calls the spelling provider and returns the result. Coroutine management must be done
     * by the source spell checker service.
     */
    suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
    ): SpellingResult {
        return getSpellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
        )
    }

    suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, breakIterators: BreakIteratorGroup, localLastCommitPosition: Int
    ): EditorRange {
        return getSuggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition
        )
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        // Using a cache because I have no idea how fast the runBlocking is
        return providersForceSuggestionOn.getOrPut(subtype.nlpProviders.suggestion) {
            runBlocking {
                getSuggestionProvider(subtype).forcesSuggestionOn
            }
        }
    }

    fun isSuggestionOn(): Boolean =
        prefs.suggestion.enabled.get()
            || prefs.emoji.suggestionEnabled.get()
            || providerForcesSuggestionOn(subtypeManager.activeSubtype)

    fun suggest(subtype: Subtype, content: EditorContent) {
        val reqTime = SystemClock.uptimeMillis()
        scope.launch {
            val emojiSuggestions = when {
                prefs.emoji.suggestionEnabled.get() -> {
                    emojiSuggestionProvider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
                else -> emptyList()
            }
            val suggestions = when {
                emojiSuggestions.isNotEmpty() && prefs.emoji.suggestionType.get().prefix.isNotEmpty() -> {
                    emptyList()
                }
                else -> {
                    getSuggestionProvider(subtype).suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = 8,
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
            }
            // Generate phrase predictions when user just hit space (blank composing text)
            val composingText = content.composingText.toString().trim()
            if (composingText.isBlank()) {
                // Extract context words from text before cursor
                val textBefore = content.textBeforeSelection.toString()
                val trimmedBefore = textBefore.trimEnd()
                val words = trimmedBefore.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                val word1 = if (words.isNotEmpty()) words.last() else null
                val word2 = if (words.size >= 2) words[words.size - 2] else null

                val phraseTable = dev.patrickgold.florisboard.ime.nlp.shared.PhraseTable.get()
                val seen = mutableSetOf<String>()
                val allPhrases = mutableListOf<String>()

                // TIER 1: PhraseTable with 2-word context (highest quality)
                if (phraseTable != null && word2 != null && word1 != null) {
                    val personalPhrases = phraseTable.predictContinuation(word2, word1)
                    for (p in personalPhrases) {
                        if (seen.add(p.lowercase())) allPhrases.add(p)
                    }
                }

                // TIER 2: BigramTable beam search fills remaining slots (fallback)
                if (allPhrases.size < 3 && word1 != null) {
                    val bigramPhrases = dev.patrickgold.florisboard.ime.nlp.shared.BigramTable.get()
                        ?.predictPhrases(word1, maxPhrases = 3 - allPhrases.size) ?: emptyList()
                    for (p in bigramPhrases) {
                        if (seen.add(p.lowercase())) allPhrases.add(p)
                        if (allPhrases.size >= 3) break
                    }
                }

                if (allPhrases.isNotEmpty()) {
                    val phraseCandidates = allPhrases.take(3).map { phrase ->
                        WordSuggestionCandidate(
                            text = phrase,
                            secondaryText = null,
                            isEligibleForAutoCommit = false,
                            isEligibleForUserRemoval = false,
                            sourceProvider = null,
                        )
                    }
                    updatePhraseCandidates(phraseCandidates)
                } else {
                    clearPhraseCandidates()
                }
            } else {
                // User is mid-word typing - hide phrase row
                clearPhraseCandidates()
            }

            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < reqTime) {
                    internalSuggestions = reqTime to buildList {
                        addAll(emojiSuggestions)
                        addAll(suggestions)
                    }
                    addLogEvent(content.composingText.toString(), getPreviousWord(subtype), suggestions)
                }
            }
        }
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>) {
        val reqTime = SystemClock.uptimeMillis()
        runBlocking {
            internalSuggestions = reqTime to suggestions
        }
    }

    fun clearSuggestions() {
        val reqTime = SystemClock.uptimeMillis()
        runBlocking {
            internalSuggestions = reqTime to emptyList()
        }
        clearPhraseCandidates()
    }

    fun getAutoCommitCandidate(): SuggestionCandidate? {
        // Since we now prepend the raw typed word (which is not eligible for auto-commit)
        // we must search through the candidates to find the best auto-commit correction.
        // We typically only want to look at the top few.
        return activeCandidates.take(3).firstOrNull { it.isEligibleForAutoCommit }
    }

    fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        return runBlocking { candidate.sourceProvider?.removeSuggestion(subtype, candidate) == true }.also { result ->
            if (result) {
                scope.launch {
                    // Need to re-trigger the suggestions algorithm
                    if (candidate is ClipboardSuggestionCandidate) {
                        assembleCandidates()
                    } else {
                        suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                    }
                }
            }
        }
    }

    fun addToUserDictionary(subtype: Subtype, candidate: SuggestionCandidate) {
        val word = candidate.text.toString()
        val locale = subtype.primaryLocale
        val success = dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default().addToUserDictionary(word, locale)
        if (success) {
             val allWords = dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default().queryAllWords(locale)
             dev.patrickgold.florisboard.ime.nlp.SymSpellManager.updateUserDictCache(allWords)
        }
        scope.launch {
            suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
        }
    }



    fun getListOfWords(subtype: Subtype): List<String> {
        return runBlocking { getSuggestionProvider(subtype).getListOfWords(subtype) }
    }

    fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return runBlocking { getSuggestionProvider(subtype).getFrequencyForWord(subtype, word) }
    }

    /**
     * Score a word given context. Unified scoring for tap autocorrect and swipe.
     * Delegates to the active suggestion provider's scoring logic.
     */
    fun scoreWord(subtype: Subtype, word: String, prevWord: String?, editDistance: Double = 0.0): Double {
        return runBlocking { getSuggestionProvider(subtype).scoreWord(subtype, word, prevWord, editDistance) }
    }

    /**
     * Gets the previous word from the editor context for bigram scoring.
     * Returns null if no previous word exists (cursor at start or after non-word characters).
     */
    fun getPreviousWord(@Suppress("UNUSED_PARAMETER") subtype: Subtype): String? {
        val textBefore = editorInstance.activeContent.textBeforeSelection.toString()
        if (textBefore.isBlank()) return null
        
        // Find the last complete word (skip any trailing spaces, then find word boundary)
        val trimmed = textBefore.trimEnd()
        if (trimmed.isEmpty()) return null
        
        // Find the start of the last word
        val lastSpaceIndex = trimmed.lastIndexOf(' ')
        val lastWord = if (lastSpaceIndex >= 0) {
            trimmed.substring(lastSpaceIndex + 1)
        } else {
            trimmed
        }
        
        return lastWord.takeIf { it.isNotEmpty() && it.all { c -> c.isLetter() || c == '\'' } }
    }

    private fun assembleCandidates() {
        runBlocking {
            val candidates = when {
                isSuggestionOn() -> {
                    clipboardSuggestionProvider.suggest(
                        subtype = Subtype.DEFAULT,
                        content = editorInstance.activeContent,
                        maxCandidateCount = 8,
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    ).ifEmpty {
                        buildList {
                            internalSuggestionsGuard.withLock {
                                addAll(internalSuggestions.second)
                            }
                        }
                    }
                }
                else -> emptyList()
            }
            activeCandidates = candidates
            autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
        }
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        if (!prefs.smartbar.enabled.get()) {// || !prefs.smartbar.sharedActionsAutoExpandCollapse.get()) {
            return
        }
        // TODO: this is a mess and needs to be cleaned up in v0.5 with the NLP development
        /*if (keyboardManager.inputEventDispatcher.isRepeatableCodeLastDown()
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.DELETE)
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.FORWARD_DELETE)
            || keyboardManager.activeState.isActionsOverflowVisible
        ) {
            return // We do not auto switch if a repeatable action key was last pressed or if the actions overflow
                   // menu is visible to prevent annoying UI changes
        }*/
        val isSelection = editorInstance.activeContent.selection.isSelectionMode
        val isExpanded = list1.isNullOrEmpty() && list2.isNullOrEmpty() || isSelection
        scope.launch {
            prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
            prefs.smartbar.sharedActionsExpanded.set(isExpanded)
        }
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        val version = debugOverlayVersionSource.incrementAndGet()
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.postValue(version)
    }

    fun clearDebugOverlay() {
        val version = debugOverlayVersionSource.incrementAndGet()
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.postValue(version)
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private var isInstanceAlive = AtomicBoolean(false)

        suspend fun createIfNecessary() {
            if (!isInstanceAlive.getAndSet(true)) provider.create()
        }

        suspend fun preload(subtype: Subtype) {
            provider.preload(subtype)
        }

        suspend fun destroyIfNecessary() {
            if (isInstanceAlive.getAndSet(true)) provider.destroy()
        }
    }

    inner class ClipboardSuggestionProvider internal constructor(private val context: Context) : SuggestionProvider {
        private var lastClipboardItemId: Long = -1

        override val providerId = "org.florisboard.nlp.providers.clipboard"

        override suspend fun create() {
            // Do nothing
        }

        override suspend fun preload(subtype: Subtype) {
            // Do nothing
        }

        override suspend fun suggest(
            subtype: Subtype,
            content: EditorContent,
            maxCandidateCount: Int,
            allowPossiblyOffensive: Boolean,
            isPrivateSession: Boolean,
        ): List<SuggestionCandidate> {
            // Check if enabled
            if (!prefs.clipboard.suggestionEnabled.get()) return emptyList()

            val currentItem = validateClipboardItem(clipboardManager.primaryClip, lastClipboardItemId, content.text)
                ?: return emptyList()

            return buildList {
                val now = System.currentTimeMillis()
                if ((now - currentItem.creationTimestampMs) < prefs.clipboard.suggestionTimeout.get() * 1000) {
                    add(ClipboardSuggestionCandidate(currentItem, sourceProvider = this@ClipboardSuggestionProvider, context = context))
                    if (currentItem.isSensitive) {
                        return@buildList
                    }
                    if (currentItem.type == ItemType.TEXT) {
                        val text = currentItem.stringRepresentation()
                        val matches = buildList {
                            addAll(NetworkUtils.getEmailAddresses(text))
                            addAll(NetworkUtils.getUrls(text))
                            addAll(NetworkUtils.getPhoneNumbers(text))
                        }
                        matches.forEachIndexed { i, match ->
                            val isUniqueMatch = matches.subList(0, i).all { prevMatch ->
                                prevMatch.value != match.value && prevMatch.range.intersect(match.range).isEmpty()
                            }
                            if (match.value != text && isUniqueMatch) {
                                add(ClipboardSuggestionCandidate(
                                    clipboardItem = currentItem.copy(
                                        // TODO: adjust regex of phone number so we don't need to manually strip the
                                        //  parentheses from the match results
                                        text = if (match.value.startsWith("(") && match.value.endsWith(")")) {
                                            match.value.substring(1, match.value.length - 1)
                                        } else {
                                            match.value
                                        }
                                    ),
                                    sourceProvider = this@ClipboardSuggestionProvider,
                                    context = context,
                                ))
                            }
                        }
                    }
                }
            }
        }

        override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
            }
        }

        override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
            // Do nothing
        }

        override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
                return true
            }
            return false
        }

        override suspend fun getListOfWords(subtype: Subtype): List<String> {
            return emptyList()
        }

        override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
            return 0.0
        }

        override suspend fun destroy() {
            // Do nothing
        }

        private fun validateClipboardItem(currentItem: ClipboardItem?, lastItemId: Long, contentText: String) =
            currentItem?.takeIf {
                // Check if already used
                it.id != lastItemId
                    // Check if content is empty
                    && contentText.isBlank()
                    // Check if clipboard content is valid (text or image)
                    && ((!currentItem.text.isNullOrBlank() && !blankStrRegex.matches(currentItem.text))
                        || (currentItem.type == ItemType.IMAGE && currentItem.uri != null))
            }
    }
}
