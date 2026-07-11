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

package dev.patrickgold.florisboard.ime.editor

import android.content.ClipDescription
import android.content.ContentUris
import android.content.Context
import android.view.KeyEvent
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.text.composing.Appender
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.florisboard.lib.android.showShortToastSync
import dev.patrickgold.florisboard.ime.nlp.HarvestManager
import dev.patrickgold.florisboard.ime.nlp.AppContext

class EditorInstance(context: Context) : AbstractEditorInstance(context) {
    companion object {
        private const val SPACE = " "
    }

    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val clipboardManager by context.clipboardManager()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()
    private val nlpManager by context.nlpManager()

    private val activeState get() = keyboardManager.activeState
    val autoSpace = AutoSpaceState()
    val phantomSpace = PhantomSpaceState()
    val massSelection = MassSelectionState()

    // True when the last commit was a candidate commit that appended its own trailing
    // space. Unlike phantomSpace this is NOT cleared by async selection updates, so the
    // very next key press can reliably tell a keyboard-inserted space from a typed one.
    private var lastCommitAppendedSpace = false

    // Harvest: word buffer to accumulate chars before logging
    private val currentWordBuffer = StringBuilder()

    // Harvest: literal key trace for the current word, including backspaces ('⌫').
    // Diverges from currentWordBuffer exactly when the user corrected themselves mid-word,
    // which is the raw signal for fat-finger/typo modeling.
    private val currentWordTrace = StringBuilder()

    // Harvest: the auto-correct we already logged as ACCEPTED, so the word-boundary
    // flush doesn't log the same correction twice (commitCompletion vs space flush).
    private var lastLoggedAutoCorrect: AbstractEditorInstance.AutoCorrectUndoState? = null

    // Harvest: tracks the word the user started backspacing into (missed autocorrect detection)
    private var pendingManualCorrect: String? = null

    // Cached AppContext — rebuilt on each new input view, reused per keystroke
    private var cachedAppContext: AppContext? = null

    private fun currentInputConnection() = FlorisImeService.currentInputConnection()

    /**
     * Build AppContext from current EditorInfo for harvest logging.
     * Caches the result so it's only computed once per input session.
     */
    private fun buildAppContext(): AppContext? {
        cachedAppContext?.let { return it }

        val info = activeInfo
        val pkg = info.packageName ?: return null

        // Build flags string
        val flags = buildList {
            if (info.inputAttributes.flagTextNoSuggestions) add("noSuggestions")
            if (info.inputAttributes.flagTextAutoCorrect) add("autoCorrect")
            if (info.inputAttributes.flagTextAutoComplete) add("autoComplete")
            if (info.imeOptions.flagNoPersonalizedLearning) add("noPersonalizedLearning")
            if (info.imeOptions.flagForceAscii) add("forceAscii")
        }.joinToString(",").ifEmpty { "none" }

        val ctx = AppContext(
            packageName = pkg,
            fieldId = info.base.fieldId,
            inputVariation = info.inputAttributes.variation.toString(),
            flags = flags,
            isPassword = info.inputAttributes.isPassword,
        )
        cachedAppContext = ctx
        return ctx
    }

    override fun handleStartInputView(editorInfo: FlorisEditorInfo, isRestart: Boolean) {
        if (!prefs.correction.rememberCapsLockState.get()) {
            activeState.inputShiftState = InputShiftState.UNSHIFTED
        }
        activeState.isActionsOverflowVisible = false
        activeState.isActionsEditorVisible = false
        cachedAppContext = null       // Invalidate cache on new input view
        pendingManualCorrect = null  // Clear any in-progress manual correction tracking
        currentWordBuffer.setLength(0)  // Word state never carries across fields
        currentWordTrace.setLength(0)
        super.handleStartInputView(editorInfo, isRestart)
        val keyboardMode = when (editorInfo.inputAttributes.type) {
            InputAttributes.Type.NUMBER -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.NUMERIC
            }
            InputAttributes.Type.PHONE -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.PHONE
            }
            InputAttributes.Type.TEXT -> {
                activeState.keyVariation = when (editorInfo.inputAttributes.variation) {
                    InputAttributes.Variation.EMAIL_ADDRESS,
                    InputAttributes.Variation.WEB_EMAIL_ADDRESS,
                    -> {
                        KeyVariation.EMAIL_ADDRESS
                    }
                    InputAttributes.Variation.PASSWORD,
                    InputAttributes.Variation.VISIBLE_PASSWORD,
                    InputAttributes.Variation.WEB_PASSWORD,
                    -> {
                        KeyVariation.PASSWORD
                    }
                    InputAttributes.Variation.URI -> {
                        KeyVariation.URI
                    }
                    else -> {
                        KeyVariation.NORMAL
                    }
                }
                KeyboardMode.CHARACTERS
            }
            else -> {
                activeState.keyVariation = KeyVariation.NORMAL
                KeyboardMode.CHARACTERS
            }
        }
        activeState.keyboardMode = keyboardMode
        activeState.isComposingEnabled = when (keyboardMode) {
            KeyboardMode.NUMERIC,
            KeyboardMode.PHONE,
            KeyboardMode.PHONE2,
            -> false
            else -> activeState.keyVariation != KeyVariation.PASSWORD &&
                prefs.suggestion.enabled.get()// &&
            //!instance.inputAttributes.flagTextAutoComplete &&
            //!instance.inputAttributes.flagTextNoSuggestions
        }
        activeState.isIncognitoMode = when (prefs.suggestion.incognitoMode.get()) {
            IncognitoMode.FORCE_OFF -> false
            IncognitoMode.FORCE_ON -> true
            IncognitoMode.DYNAMIC_ON_OFF -> {
                editorInfo.imeOptions.flagNoPersonalizedLearning || prefs.suggestion.forceIncognitoModeFromDynamic.get()
            }
        }
    }

    override fun handleSelectionUpdate(oldSelection: EditorRange, newSelection: EditorRange, composing: EditorRange) {
        autoSpace.setInactiveFromUpdate()
        phantomSpace.setInactiveFromUpdate()
        if (massSelection.isActive) {
            super.handleMassSelectionUpdate(newSelection, composing)
        } else {
            super.handleSelectionUpdate(oldSelection, newSelection, composing)
        }
    }

    override fun determineComposingEnabled(): Boolean {
        return nlpManager.isSuggestionOn()
    }

    override fun determineComposer(composerName: ExtensionComponentName): Composer {
        return keyboardManager.resources.composers.value?.get(composerName) ?: Appender
    }

    override fun shouldDetermineComposingRegion(editorInfo: FlorisEditorInfo): Boolean {
        return super.shouldDetermineComposingRegion(editorInfo) &&
            (phantomSpace.isInactive || phantomSpace.showComposingRegion)
    }

    /**
     * Sets the selection of the input editor to the specified [start] and [end] values. This method does nothing if
     * the input connection is not valid or if the input editor is raw.
     *
     * @param start The start of the selection (inclusive). May be any value ranging from -1 to positive infinity.
     * @param end The end of the selection (exclusive). May be any value ranging from -1 to positive infinity.
     *
     * @return True on success or if the selection is already at specified position, false otherwise.
     */
    fun setSelection(start: Int, end: Int): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        lastCommitAppendedSpace = false
        val selection = EditorRange.normalized(start, end)
        return super.setSelection(selection)
    }

    private fun shouldInsertAutoSpaceBefore(text: String): Boolean {
        if (!prefs.correction.autoSpacePunctuation.get() || text.isEmpty()) return false
        if (activeInfo.isRawInputEditor) return false
        if (activeState.keyVariation != KeyVariation.NORMAL) return false

        val punctuationRule = nlpManager.getActivePunctuationRule()
        val textBefore = activeContent.getTextBeforeCursor(1)
        return textBefore.isNotEmpty() && !textBefore.last().isWhitespace() &&
            punctuationRule.symbolsFollowingAutoSpace.contains(text.first())
    }

    private fun shouldInsertAutoSpaceAfter(text: String, spaceWillBeEaten: Boolean): Boolean {
        if (!prefs.correction.autoSpacePunctuation.get() || text.isEmpty()) return false
        if (activeInfo.isRawInputEditor) return false
        if (activeState.keyVariation != KeyVariation.NORMAL) return false

        val punctuationRule = nlpManager.getActivePunctuationRule()
        val content = activeContent
        val textBefore = content.getTextBeforeCursor(3).let { textBefore ->
            // A trailing space that is about to be eaten (or that our own auto-space
            // inserted) is transparent here: punctuation eats it and re-adds its own
            // space, so "work" -> auto-commit "Work " -> "." still yields "Work. ".
            // Deliberately NOT based on phantomSpace: async selection updates can clear
            // it between the candidate commit and the punctuation key landing.
            if ((autoSpace.isActive || spaceWillBeEaten) && textBefore.isNotEmpty() && textBefore.last() == ' ') {
                textBefore.dropLast(1)
            } else {
                textBefore
            }
        }
        return textBefore.isNotEmpty() && !textBefore.last().isWhitespace() &&
            content.currentWordText.all { !it.isDigit() } &&
            punctuationRule.symbolsPrecedingAutoSpace.contains(text.first())
    }

    override fun commitChar(char: String): Boolean {
        // Consume the candidate-space signal: it only ever applies to the key pressed
        // immediately after the candidate commit.
        val afterCandidateSpace = lastCommitAppendedSpace
        lastCommitAppendedSpace = false

        // iOS/Gboard behavior: if we're typing punctuation and there's a trailing space,
        // delete the space. This makes "word " + "." = "word." instead of "word ."
        // Works for ANY trailing space before punctuation, not just phantom spaces.
        val punctuationRule = nlpManager.getActivePunctuationRule()
        val isPunctuation = char.isNotEmpty() && punctuationRule.symbolsPrecedingAutoSpace.contains(char.first())

        val hasTrailingSpace = activeContent.getTextBeforeCursor(1).let { it.isNotEmpty() && it.last() == ' ' }
        // Apostrophes/quotes attach to the word before them: eat a trailing space the
        // keyboard itself inserted (candidate/auto-commit appends one), but never a space
        // the user typed deliberately. phantomSpace signals this too, but async selection
        // updates can clear it before the next key lands; the local flag is race-free.
        val attachesToWord = char == "'" || char == "\"" || char == "’"
        val shouldEatTrailingSpace = (isPunctuation || (attachesToWord && (afterCandidateSpace || phantomSpace.isActive))) && hasTrailingSpace

        val isInsertAutoSpaceBeforeChar = shouldInsertAutoSpaceBefore(char)
        val isInsertAutoSpaceAfterChar = shouldInsertAutoSpaceAfter(char, spaceWillBeEaten = shouldEatTrailingSpace)
        val isDeletePreviousSpace = isInsertAutoSpaceAfterChar && autoSpace.isActive

        // SESSION LOGGING: accumulate chars into words
        val isSpace = char == " "
        val isWordBoundary = isPunctuation || char == "\n" || isSpace

        // Context words must be read before the commit mutates editor content
        val (prevWord, prevPrevWord) = if (isWordBoundary) {
            getContextWords(currentWordBuffer.toString())
        } else null to null

        if (!isWordBoundary) {
            // Accumulate char into current word
            currentWordBuffer.append(char)
            currentWordTrace.append(char)
        }

        if (isInsertAutoSpaceAfterChar) {
            autoSpace.setActive()
        } else {
            autoSpace.setInactive()
        }
        val isPhantomSpaceActive = phantomSpace.determine(char)
        phantomSpace.setInactive()
        val result = super.commitChar(
            char = char,
            deletePreviousSpace = isDeletePreviousSpace || shouldEatTrailingSpace,
            insertSpaceBeforeChar = isInsertAutoSpaceBeforeChar || isPhantomSpaceActive,
            insertSpaceAfterChar = isInsertAutoSpaceAfterChar,
        )
        // Flush AFTER the commit so a word-separator-triggered autocorrect
        // (commitTextInternal) has already recorded its undo state and the session
        // logs what actually landed in the editor.
        if (isWordBoundary) {
            flushCurrentWordToSession(buildAppContext(), prevWord, prevPrevWord)
            // Flush session on sentence terminators
            if (isPunctuation || char == "\n") {
                pendingManualCorrect = null  // Abandon tracking at sentence boundary
                HarvestManager.flushSession(char, buildAppContext())
            }
        }
        return result
    }

    /**
     * Flushes the accumulated word (and its key trace) into the harvest session at a
     * word boundary, resolving what actually landed in the editor:
     *  - If the unified autocorrect (commitTextInternal) just replaced the word, log the
     *    ACCEPTED event here — that path applies corrections without notifying the
     *    harvest — and record the corrected form in the session.
     *  - Otherwise record the typed word, with the manual-correction check preserved.
     */
    private fun flushCurrentWordToSession(ctx: AppContext?, prevWord: String?, prevPrevWord: String?) {
        val typed = currentWordBuffer.toString()
        val trace = currentWordTrace.toString()
        currentWordBuffer.setLength(0)
        currentWordTrace.setLength(0)

        val undo = autoCorrectUndoState
        val isFreshCorrection = undo != null && undo !== lastLoggedAutoCorrect &&
            (typed.isEmpty() || undo.originalText.equals(typed, ignoreCase = true))
        if (isFreshCorrection && undo != null) {
            lastLoggedAutoCorrect = undo
            HarvestManager.logAccepted(
                typed = undo.originalText,
                correctedTo = undo.correctedText,
                prevWord = prevWord,
                prevPrevWord = prevPrevWord,
                appContext = ctx,
                candidates = nlpManager.candidatesSnapshotFor(undo.originalText),
                trace = trace.takeIf { it.isNotEmpty() && it != undo.correctedText },
                auto = true,
            )
            pendingManualCorrect = null
            HarvestManager.addToSession(
                undo.correctedText, ctx,
                trace = trace.takeIf { it.isNotEmpty() && it != undo.correctedText },
            )
            return
        }
        if (typed.isEmpty()) return
        // Log manual correction if user backspaced into committed text and retyped differently
        val original = pendingManualCorrect
        if (original != null) {
            if (!typed.equals(original, ignoreCase = true)) {
                HarvestManager.logManualCorrection(original, typed, prevWord, ctx, trace = trace.takeIf { it != typed })
            }
            pendingManualCorrect = null
        }
        HarvestManager.addToSession(typed, ctx, trace = trace.takeIf { it != typed })
    }

    /**
     * Commits the given [text] to this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * This method overwrites any selected text and replaces it with given [text]. If there is no
     * text selected (selection is in cursor mode), then this method will insert the [text] after
     * the cursor, then set the cursor position to the first character after the inserted text.
     *
     * @param text The text to commit.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    override fun commitText(text: String): Boolean {
        val isPhantomSpaceActive = phantomSpace.determine(text)
        autoSpace.setInactive()
        phantomSpace.setInactive()
        lastCommitAppendedSpace = false
        // BUGFIX (session concatenation): the space key ALWAYS arrives here — handleSpace
        // commits via commitText(" "), never commitChar(" ") — so leading whitespace must
        // flush the word accumulated by commitChar, or typed words concatenate in the
        // session log (worst in suggestions-off fields like Termux).
        val isWhitespaceLeading = text.isNotEmpty() && (text.isBlank() || text.first().isWhitespace())
        val needsWordFlush = isWhitespaceLeading || text == "\n"
        // Context words must be read before the commit mutates editor content
        val (prevWord, prevPrevWord) = if (needsWordFlush) {
            getContextWords(currentWordBuffer.toString())
        } else null to null
        return if (isPhantomSpaceActive) {
            super.commitText("$SPACE$text")
        } else {
            super.commitText(text)
        }.also {
            // SESSION LOGGING: commitText is called for autocorrect, swipe, paste
            // Text may be multi-word, so parse it
            val ctx = buildAppContext()
            if (text == "\n") {
                flushCurrentWordToSession(ctx, prevWord, prevPrevWord)
                HarvestManager.flushSession(text, ctx)
            } else {
                if (isWhitespaceLeading) {
                    flushCurrentWordToSession(ctx, prevWord, prevPrevWord)
                }
                // Parse text into words and add each
                val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
                for (word in words) {
                    HarvestManager.addToSession(word, ctx)
                }
            }
        }
    }

    /**
     * Completes the given [candidate] in the current composing region. Does nothing if the current
     * input editor is not rich or if the input connection is invalid.
     *
     * Current phantom space state is respected and a space char will be inserted accordingly.
     * Phantom space will be activated if the text is committed.
     *
     * @param candidate The candidate to complete in this editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun commitCompletion(candidate: SuggestionCandidate): Boolean {
        val text = candidate.text.toString()
        if (text.isEmpty() || activeInfo.isRawInputEditor) return false
        val content = activeContent
        
        // We append a space to the candidate to match iOS/Gboard behavior (immediate separation).
        val textWithSpace = "$text$SPACE"
        lastCommitAppendedSpace = true

        return if (content.composing.isValid) {
            val original = content.composingText
            val (prevWord, prevPrevWord) = getContextWords(original)
            val ctx = buildAppContext()
            val trace = currentWordTrace.toString()
            phantomSpace.setActive(showComposingRegion = false, candidate = candidate)
            super.finalizeComposingText(textWithSpace).also {
                if (original.isNotEmpty() && text != original) {
                    // Track the word WITHOUT the space for the undo state, so our "trailing match" logic handles the space.
                    autoCorrectUndoState = AbstractEditorInstance.AutoCorrectUndoState(text, original)
                    // Mark as logged so the word-boundary flush doesn't log it again
                    lastLoggedAutoCorrect = autoCorrectUndoState
                    // Log accepted autocorrect for harvest
                    HarvestManager.logAccepted(
                        original, text, prevWord, prevPrevWord, ctx,
                        candidates = nlpManager.candidatesSnapshotFor(original),
                        trace = trace.takeIf { it.isNotEmpty() && it != text },
                        auto = false,
                    )
                } else if (original.isNotEmpty() && text.equals(original, ignoreCase = true)) {
                    // User explicitly picked their typed word - log as INSISTED
                    // This is a strong signal this word should be in the dictionary
                    HarvestManager.logInsisted(original, prevWord, ctx)
                }
                // The completed word replaces whatever chars were accumulated
                currentWordBuffer.setLength(0)
                currentWordTrace.setLength(0)
                HarvestManager.addToSession(text, ctx, trace = trace.takeIf { it.isNotEmpty() && it != text })
            }
        } else {
            val isPhantomSpaceActive = phantomSpace.determine(textWithSpace)
            phantomSpace.setActive(showComposingRegion = false, candidate = candidate)
            return if (isPhantomSpaceActive) {
                super.commitText("$SPACE$textWithSpace")
            } else {
                super.commitText(textWithSpace)
            }.also {
                // handled in finalizeComposingText if content.composing.isValid
                updateLastCommitPosition()
            }
        }
    }

    /**
     * Commit a word generated by a gesture.
     *
     * Ignores the current phantom space state and will insert a space depending on the character
     * before selection start. Phantom space will be activated if the text is committed.
     *
     * @param text The text to commit in this editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun commitGesture(text: String): Boolean {
        if (text.isEmpty() || activeInfo.isRawInputEditor) return false
        val isPhantomSpaceActive = phantomSpace.determine(text, forceActive = true)
        phantomSpace.setActive(showComposingRegion = true)
        return if (isPhantomSpaceActive) {
            super.commitText("$SPACE$text")
        } else {
            super.commitText(text)
        }.also {
            updateLastCommitPosition()
        }
    }

    /**
     * Commits the given [ClipboardItem]. If the clip data is text (incl. HTML), it delegates to [commitText].
     * If the item has a content URI (and the EditText supports it), the item is committed as rich data.
     * This allows for committing (e.g) images.
     *
     * @param item The ClipboardItem to commit
     *
     * @return True on success, false if something went wrong.
     */
    fun commitClipboardItem(item: ClipboardItem?): Boolean {
        if (item == null) return false
        val mimeTypes = item.mimeTypes
        return when (item.type) {
            ItemType.TEXT -> {
                commitText(item.text.toString()).also {
                    updateLastCommitPosition()
                }
            }
            ItemType.IMAGE, ItemType.VIDEO -> {
                item.uri ?: return false
                val id = ContentUris.parseId(item.uri)
                val file = ClipboardFileStorage.getFileForId(appContext, id)
                if (!file.exists()) return false
                val inputContentInfo = InputContentInfoCompat(
                    item.uri,
                    ClipDescription("clipboard media file", mimeTypes.toTypedArray()),
                    null,
                )
                val ic = currentInputConnection() ?: return false
                ic.finishComposingText()
                val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                InputConnectionCompat.commitContent(ic, activeInfo.base, inputContentInfo, flags, null)
            }
        }.also {
            if (prefs.clipboard.historyHideOnPaste.get()) {
                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            }
        }
    }

    /**
     * Executes a backward delete on this editor's text. If a text selection is active, all
     * characters inside this selection will be removed, else only the left-most character from
     * the cursor's position.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun deleteBackwards(unit: OperationUnit): Boolean {
        val content = activeContent
        lastCommitAppendedSpace = false
        // iOS-style undo: if the last commit auto-corrected, a single backspace restores the original word.
        if (unit == OperationUnit.CHARACTERS && tryRevertLastAutoCorrect()) {
            return true
        }
        // Harvest: detect when user is backspacing into committed (non-composing) text.
        // composingText is empty when the cursor is between committed words — this is the
        // "missed autocorrect" window: the user has to manually fix what the system should have caught.
        if (unit == OperationUnit.CHARACTERS && pendingManualCorrect == null) {
            if (content.composingText.isEmpty()) {
                pendingManualCorrect = getPreviousWord()
            }
        }
        // Harvest: mirror the deletion into the word buffer and record it in the key
        // trace. The trace ('wpr⌫⌫ord' → committed 'word') is the raw self-labeled
        // typo signal for fat-finger modeling.
        if (unit == OperationUnit.CHARACTERS && !content.selection.isSelectionMode && currentWordBuffer.isNotEmpty()) {
            currentWordBuffer.deleteCharAt(currentWordBuffer.length - 1)
            currentWordTrace.append('⌫')
        }
        if (unit == OperationUnit.WORDS) {
            // Word-level deletes are edits, not typos — reset word tracking
            currentWordBuffer.setLength(0)
            currentWordTrace.setLength(0)
        }
        if (unit == OperationUnit.CHARACTERS) {
            if (phantomSpace.isActive && content.currentWord.isValid && prefs.glide.immediateBackspaceDeletesWord.get()) {
                return deleteBackwards(OperationUnit.WORDS)
            }
        }
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (content.selection.isSelectionMode) {
            commitText("")
        } else runBlocking {
            deleteAroundCursor(unit, OperationScope.BEFORE_CURSOR, n = 1)
        }
    }

    /**
     * Executes a backward delete on this editor's text. If a text selection is active, all
     * characters inside this selection will be removed, else only the left-most character from
     * the cursor's position.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun deleteForwards(unit: OperationUnit): Boolean {
        val content = activeContent
        lastCommitAppendedSpace = false
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (content.selection.isSelectionMode) {
            commitText("")
        } else runBlocking {
            deleteAroundCursor(unit, OperationScope.AFTER_CURSOR, n = 1)
        }
    }

    fun setSelectionSurrounding(n: Int, unit: OperationUnit, scope: OperationScope): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val content = activeContent
        val selection = content.selection
        val safeEditorBounds = content.safeEditorBounds
        if (selection.isNotValid) return false
        when (scope) {
            OperationScope.BEFORE_CURSOR -> {
                if (n <= 0) {
                    return setSelection(selection.end, selection.end)
                }
                val textToAnalyze = content.text.substring(0, content.localSelection.end)
                val length = runBlocking {
                    when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureLastUChars(textToAnalyze, n)
                        OperationUnit.WORDS -> breakIterators.measureLastUWords(textToAnalyze, n)
                    }
                }
                return setSelection((selection.end - length).coerceAtLeast(safeEditorBounds.start), selection.end)
            }
            OperationScope.AFTER_CURSOR -> {
                if (n <= 0) {
                    return setSelection(selection.start, selection.start)
                }
                val textToAnalyze = content.text.substring(content.localSelection.start)
                val length = runBlocking {
                    when (unit) {
                        OperationUnit.CHARACTERS -> breakIterators.measureUChars(textToAnalyze, n)
                        OperationUnit.WORDS -> breakIterators.measureUWords(textToAnalyze, n)
                    }
                }
                return setSelection(selection.start, (selection.start + length).coerceAtMost(safeEditorBounds.end))
            }
        }
    }

    /**
     * Performs a cut command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardCut(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val text = activeContent.selectedText.ifBlank { currentInputConnection()?.getSelectedText(0) }
        if (text != null) {
            clipboardManager.addNewPlaintext(text.toString())
        } else {
            appContext.showShortToastSync("Failed to retrieve selected text requested to cut: Eiter selection state is invalid or an error occurred within the input connection.")
        }
        return deleteBackwards(OperationUnit.CHARACTERS)
    }

    /**
     * Performs a copy command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardCopy(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val text = activeContent.selectedText.ifBlank { currentInputConnection()?.getSelectedText(0) }
        if (text != null) {
            clipboardManager.addNewPlaintext(text.toString())
        } else {
            appContext.showShortToastSync("Failed to retrieve selected text requested to copy: Eiter selection state is invalid or an error occurred within the input connection.")
        }
        val activeSelection = activeContent.selection
        return setSelection(activeSelection.end, activeSelection.end)
    }

    /**
     * Performs a paste command on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardPaste(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return commitClipboardItem(clipboardManager.primaryClip).also { result ->
            if (!result) {
                appContext.showShortToastSync("Failed to paste item.")
            }
        }
    }

    /**
     * Performs a select all on this editor instance and adjusts both the cursor position and
     * composing region, if any.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performClipboardSelectAll(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val ic = currentInputConnection() ?: return false
        ic.finishComposingText()
        return if (activeInfo.isRawInputEditor) {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_A, meta(ctrl = true))
        } else {
            ic.performContextMenuAction(android.R.id.selectAll)
        }
    }

    /**
     * Performs an enter key press on the current input editor.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performEnter(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return if (activeInfo.isRawInputEditor) {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_ENTER)
        } else {
            commitText("\n")
        }
    }

    fun tryPerformEnterCommitRaw(): Boolean {
        return if (subtypeManager.activeSubtype.primaryLocale.language.startsWith("zh") && activeContent.composing.length > 0) {
            finalizeComposingText(activeContent.composingText)
        } else {
            false
        }
    }

    /**
     * Performs a given [action] on the current input editor.
     *
     * @param action The action to be performed on this editor instance.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performEnterAction(action: ImeOptions.Action): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        val ic = currentInputConnection() ?: return false
        return ic.performEditorAction(action.toInt())
    }

    /**
     * Undoes the last action.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performUndo(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return sendDownUpKeyEvent(KeyEvent.KEYCODE_Z, meta(ctrl = true))
    }

    /**
     * Redoes the last Undo action.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun performRedo(): Boolean {
        autoSpace.setInactive()
        phantomSpace.setInactive()
        return sendDownUpKeyEvent(KeyEvent.KEYCODE_Z, meta(ctrl = true, shift = true))
    }

    override fun reset() {
        super.reset()
        autoSpace.setInactive()
        phantomSpace.setInactive()
        massSelection.reset()
        pendingManualCorrect = null
    }

    private fun tryRevertLastAutoCorrect(): Boolean {
        val state = autoCorrectUndoState ?: return false
        val content = activeContent
        if (content.selection.isSelectionMode) return false
        val corrected = state.correctedText
        val before = content.textBeforeSelection
        
        // Check if we are at the end of the corrected word
        val isExactMatch = before.endsWith(corrected)
        
        // Check if we are at the corrected word + 1 char (e.g. space)
        // This allows "win " -> Backspace -> "wint " (restores word AND keeps space)
        val isTrailingMatch = before.length > corrected.length && 
                              before.endsWith(corrected) == false && 
                              before.dropLast(1).endsWith(corrected)

        if (!isExactMatch && !isTrailingMatch) {
             return false
        }
        
        val matchLen = if (isExactMatch) corrected.length else corrected.length + 1
        // If we are reverting a trailing match (word + space), we must restore the space too.
        // We grab the trailing char from 'before' to ensure we restore the exact separator used.
        val textToRestore = if (isExactMatch) state.originalText else state.originalText + before.takeLast(1)
        
        val ic = currentInputConnection() ?: return false
        val newTextBefore = before.dropLast(matchLen) + textToRestore
        val newSelection = EditorRange.cursor(newTextBefore.length)
        runBlocking {
            ic.beginBatchEdit()
            ic.finishComposingText()
            ic.deleteSurroundingText(matchLen, 0)
            ic.commitText(textToRestore, 1)
            ic.endBatchEdit()
            expectedContentQueue.push(
                content.generateCopy(
                    selection = newSelection,
                    textBeforeSelection = newTextBefore,
                    textAfterSelection = content.textAfterSelection,
                    selectedText = "",
                )
            )
        }
        // Mark that the user rejected the last autocorrect so the next commit of the same token won't be re-corrected.
        // Mark that the user rejected the last autocorrect so the next commit of the same token won't be re-corrected.
        // SMART SESSION: Pass original and corrected text so SymSpellManager can infer intent if user types a new char
        dev.patrickgold.florisboard.ime.nlp.SymSpellManager.markNextAsUserRejected(state.originalText, state.correctedText)
        // Learn the ignore pair so it persists
        // Learn the ignore pair so it persists
        dev.patrickgold.florisboard.ime.dictionary.DictionaryManager.default().learnUserIgnore(state.originalText, state.correctedText)
        // Log rejected autocorrect for harvest
        // At revert time the restored typed word is already committed text, so skip it
        // when deriving context — getPreviousWord() would report it as its own prev.
        val (revertPrev, revertPrev2) = getContextWords(state.originalText)
        HarvestManager.logRejected(state.originalText, state.correctedText, revertPrev, revertPrev2, buildAppContext())
        autoCorrectUndoState = null
        return true
    }

    /**
     * Get the previous word from the text before cursor for context in harvest logging.
     */
    private fun getPreviousWord(): String? {
        val textBefore = activeContent.textBeforeSelection.toString().trimEnd()
        if (textBefore.isEmpty()) return null
        val lastSpaceIndex = textBefore.lastIndexOf(' ')
        val lastWord = if (lastSpaceIndex >= 0) {
            textBefore.substring(lastSpaceIndex + 1)
        } else {
            textBefore
        }
        return lastWord.takeIf { it.isNotEmpty() && it.all { c -> c.isLetter() } }
    }

    /**
     * Context words (prev, prevPrev) for harvest logging at a word boundary. At that
     * point the word being flushed is already committed editor text, so it must be
     * skipped — getPreviousWord() would report the word as its own context.
     */
    private fun getContextWords(currentWord: String?): Pair<String?, String?> {
        val textBefore = activeContent.textBeforeSelection.toString().trimEnd()
        if (textBefore.isEmpty()) return null to null
        val words = textBefore.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        if (currentWord != null && currentWord.isNotEmpty() && words.isNotEmpty() &&
            words.last().trimEnd { !it.isLetterOrDigit() }.equals(currentWord, ignoreCase = true)
        ) {
            words.removeAt(words.size - 1)
        }
        fun clean(fromEnd: Int): String? = words.getOrNull(words.size - fromEnd)
            ?.takeIf { w -> w.isNotEmpty() && w.all { it.isLetter() || it == '\'' } }
        return clean(1) to clean(2)
    }

    /**
     * Get the word before the previous word for trigram context in harvest logging.
     */
    private fun getPreviousPreviousWord(): String? {
        val textBefore = activeContent.textBeforeSelection.toString().trimEnd()
        val words = textBefore.split(' ').filter { it.isNotBlank() }
        return if (words.size >= 2) {
            words[words.size - 2].takeIf { it.all { c -> c.isLetter() } }
        } else null
    }

    private fun PhantomSpaceState.determine(text: String, forceActive: Boolean = false): Boolean {
         val content = activeContent
         val selection = content.selection
         if (!(isActive || forceActive) || selection.isNotValid || selection.start <= 0 || text.isEmpty()) return false
         val textBefore = content.getTextBeforeCursor(1)
         val punctuationRule = nlpManager.getActivePunctuationRule()
         if (!subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace) return false;
         return textBefore.isNotEmpty() &&
             (punctuationRule.symbolsPrecedingPhantomSpace.contains(textBefore[textBefore.length - 1]) ||
                 textBefore[textBefore.length - 1].isLetterOrDigit()) &&
             (punctuationRule.symbolsFollowingPhantomSpace.contains(text[0]) || text[0].isLetterOrDigit())
    }

    class AutoSpaceState {
        companion object {
            private const val F_IS_ACTIVE = 0x1
            private const val F_STAY_ACTIVE_NEXT_UPDATE = 0x4
        }

        private val state = AtomicInteger(0)

        val isActive: Boolean
            get() = state.get() and F_IS_ACTIVE != 0

        val isInactive: Boolean
            get() = !isActive

        fun setActive(stayActiveNextUpdate: Boolean = true) {
            state.set(F_IS_ACTIVE or (if (stayActiveNextUpdate) F_STAY_ACTIVE_NEXT_UPDATE else 0))
        }

        fun setInactive() {
            state.set(0)
        }

        fun setInactiveFromUpdate() {
            state.updateAndGet { state ->
                if ((state and F_STAY_ACTIVE_NEXT_UPDATE) != 0) (state and F_STAY_ACTIVE_NEXT_UPDATE.inv()) else 0
            }
        }
    }

    class PhantomSpaceState {
        companion object {
            private const val F_IS_ACTIVE = 0x1
            private const val F_SHOW_COMPOSING_REGION = 0x2
            private const val F_STAY_ACTIVE_NEXT_UPDATE = 0x4
        }

        private val state = AtomicInteger(0)
        var candidateForRevert: SuggestionCandidate? = null
            private set

        val isActive: Boolean
            get() = state.get() and F_IS_ACTIVE != 0

        val isInactive: Boolean
            get() = !isActive

        val showComposingRegion: Boolean
            get() = state.get() and F_SHOW_COMPOSING_REGION != 0

        fun setActive(
            showComposingRegion: Boolean,
            stayActiveNextUpdate: Boolean = true,
            candidate: SuggestionCandidate? = null,
        ) {
            state.set(
                F_IS_ACTIVE
                    or (if (showComposingRegion) F_SHOW_COMPOSING_REGION else 0)
                    or (if (stayActiveNextUpdate) F_STAY_ACTIVE_NEXT_UPDATE else 0)
            )
            candidateForRevert = candidate
        }

        fun setInactive() {
            state.set(0)
            candidateForRevert = null
        }

        fun setInactiveFromUpdate() {
            val prevStateValue = state.getAndUpdate { state ->
                if ((state and F_STAY_ACTIVE_NEXT_UPDATE) != 0) (state and F_STAY_ACTIVE_NEXT_UPDATE.inv()) else 0
            }
            if ((prevStateValue and F_STAY_ACTIVE_NEXT_UPDATE) == 0) {
                candidateForRevert = null
            }
        }
    }

    inner class MassSelectionState {
        private val state = AtomicInteger(0)

        val isActive: Boolean
            get() = state.get() > 0

        val isInactive: Boolean
            get() = !isActive

        fun begin() {
            state.incrementAndGet()
        }

        fun end() {
            if (state.decrementAndGet() == 0) {
                // We need to emulate a selection update to update the content if mass selection has ended
                handleSelectionUpdate(EditorRange.Unspecified, activeContent.selection, EditorRange.Unspecified)
            }
        }

        fun reset() {
            state.set(0)
        }
    }
}
