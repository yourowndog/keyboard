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

package dev.patrickgold.florisboard.ime.keyboard

import android.content.Context
import android.os.Build
import dev.patrickgold.florisboard.app.layoutbuilder.LayoutPack
import dev.patrickgold.florisboard.app.layoutbuilder.LayoutPackRepository
import dev.patrickgold.florisboard.app.layoutbuilder.LayoutValidation
import dev.patrickgold.florisboard.audio.Recorder
import dev.patrickgold.florisboard.audio.WavTools
import dev.patrickgold.florisboard.net.WhisperClient
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.android.showLongToast


import android.icu.lang.UCharacter
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.SubtypePreset
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.editor.ImeOptions
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.editor.OperationUnit
import dev.patrickgold.florisboard.ime.input.CapitalizationBehavior
import dev.patrickgold.florisboard.ime.input.InputEventDispatcher
import dev.patrickgold.florisboard.ime.input.InputKeyEventReceiver
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.nlp.ClipboardSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.PunctuationRule
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.onehanded.OneHandedMode
import dev.patrickgold.florisboard.ime.popup.PopupMappingComponent
import dev.patrickgold.florisboard.ime.text.composing.Composer
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardCache
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.lib.titlecase
import dev.patrickgold.florisboard.lib.uppercase
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.ime.nlp.GemmaClient
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.florisboard.voiceManager
import java.io.File
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemService
import org.florisboard.lib.kotlin.collectIn
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicInteger


private val DoubleSpacePeriodMatcher = """([^.!?‽\s]\s)""".toRegex()

internal fun shouldClearTmuxPrefixVisualState(isActive: Boolean, nextKeyCode: Int): Boolean {
    return isActive && nextKeyCode != KeyCode.TMUX_PREFIX
}

class KeyboardManager(
    context: Context,
    private val layoutPackRepository: LayoutPackRepository,
) : InputKeyEventReceiver {
    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val extensionManager by context.extensionManager()
    private val nlpManager by context.nlpManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val layoutFlow = MutableStateFlow(loadInitialLayout())
    val layoutManager = LayoutManager(context)
    private val keyboardCache = TextKeyboardCache()

    val resources = KeyboardManagerResources()
    val activeState = ObservableKeyboardState.new()
    var smartbarVisibleDynamicActionsCount by mutableIntStateOf(0)
    private var lastToastReference = WeakReference<Toast>(null)





    private var isRecording = false
    private var recorder: Recorder? = null
    private var lastAudioFile: File? = null
    private val archiveQueue by lazy {
        dev.patrickgold.florisboard.ime.voice.ArchiveQueue(appContext)
    }
    private val _whisperAmplitude = MutableStateFlow(0f)
    val whisperAmplitude = _whisperAmplitude.asStateFlow()
    private var amplitudePollingJob: Job? = null

    private fun loadInitialLayout(): LayoutPack {
        // Return a blank layout pack to force the KeyboardManager to use the
        // original, extension-based layout loading logic by default. This bypasses
        // the new, experimental LayoutPackRepository on startup.
        return LayoutPack(id = "dev.florisboard.layoutpacks.empty", label = "Empty")
    }

    fun setLayout(newPack: LayoutPack): Result<Unit> {
        val errors = LayoutValidation.validatePack(newPack)
        if (errors.isNotEmpty()) {
            val message = errors.joinToString(separator = "\n")
            return Result.failure(IllegalArgumentException(message))
        }
        return runCatching {
            layoutPackRepository.save(newPack)
            layoutFlow.value = newPack
            updateActiveEvaluators {
                keyboardCache.clear()
            }
            Unit
        }
    }

    private fun requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                scope.launch {
                    appContext.showLongToast("Please grant microphone permission to use voice input.")
                }
            }
        }
    }

    private fun startVoiceCapture(context: Context) {
        if (recorder == null) {
            recorder = Recorder(context)
        }
        recorder?.start()
        activeState.batchEdit {
            it.isRecording = true
            it.isTranscribing = false
            it.isPaused = false
        }
        amplitudePollingJob?.cancel()
        amplitudePollingJob = scope.launch(Dispatchers.Default) {
            var warnedNearLimit = false
            while (isActive && activeState.isRecording) {
                val amp = recorder?.maxAmplitude() ?: 0
                _whisperAmplitude.value = (amp.toFloat() / 32768f).coerceIn(0f, 1f)

                // The transcription API caps uploads at 25 MiB. Rather than let a long capture
                // fail after the fact — which would cost the transcript entirely — warn, then
                // stop and transcribe what we have. The audio is safe either way; the transcript
                // is the part that would be lost.
                val captured = recorder?.capturedMs() ?: 0L
                if (!warnedNearLimit && captured >= WavTools.MAX_CAPTURE_MS - 90_000L) {
                    warnedNearLimit = true
                    scope.launch {
                        appContext.showLongToast("Voice: about 90 seconds left before the transcription limit")
                    }
                }
                if (captured >= WavTools.MAX_CAPTURE_MS) {
                    scope.launch {
                        appContext.showLongToast(
                            "Voice: reached the ${WavTools.MAX_CAPTURE_MS / 60_000} minute limit — transcribing now"
                        )
                        stopVoiceCapture(appContext)
                    }
                    break
                }
                delay(50)
            }
            _whisperAmplitude.value = 0f
        }
    }

    fun pauseVoiceCapture() {
        if (!activeState.isRecording || activeState.isPaused) return
        recorder?.pause()
        activeState.isPaused = true
    }

    fun resumeVoiceCapture() {
        if (!activeState.isRecording || !activeState.isPaused) return
        recorder?.resume()
        activeState.isPaused = false
    }

    fun submitVoiceCapture() {
        if (!activeState.isRecording) return
        stopVoiceCapture(appContext)
    }

    private fun stopVoiceCapture(context: Context) {
        val audioFile = try {
            recorder?.stop()
        } catch (e: Exception) {
            null
        }
        amplitudePollingJob?.cancel()
        activeState.batchEdit {
            it.isRecording = false
            it.isPaused = false
        }
        if (audioFile != null) {
            lastAudioFile = audioFile
            performTranscription(audioFile)
        } else {
            activeState.imeUiMode = ImeUiMode.TEXT
        }
    }

    fun retryTranscription() {
        val file = lastAudioFile
        if (file != null && !activeState.isRecording && !activeState.isTranscribing) {
            performTranscription(file)
        }
    }

    fun cancelVoiceInput() {
        amplitudePollingJob?.cancel()
        try {
            if (activeState.isRecording) {
                val cancelled = recorder?.stop()
                val capture = recorder?.lastCapture
                // A cancelled take is still the user's voice, and it is already on disk. Give it a
                // sidecar and hand it to the archive queue: without this it would be stranded on
                // the phone forever, transcript-less and invisible to the uploader.
                if (cancelled != null) {
                    val sidecar = writeSidecar(
                        cancelled, "", "", null, "none", "none", org.json.JSONObject(), capture,
                    )
                    if (sidecar != null) {
                        archiveQueue.enqueue(cancelled, sidecar)
                        scope.launch(Dispatchers.IO) { runCatching { archiveQueue.drain() } }
                    }
                }
            }
        } catch (e: Exception) { }
        activeState.batchEdit {
            it.isRecording = false
            it.isTranscribing = false
            it.isPaused = false
            it.imeUiMode = ImeUiMode.TEXT
        }
    }

    /**
     * Corrects known mis-hearings before the text reaches the editor.
     *
     * Applied to the committed text only. The engine's own output is preserved separately in
     * the sidecar, because a corpus label that has been silently edited is not a record of what
     * was said, and there is no way to reconstruct the original once it is overwritten.
     */
    private fun applyDictionaryFixups(text: String): String =
        text.replace(Regex("\\bKiri(s|'s)?\\b", RegexOption.IGNORE_CASE)) { m ->
            val suffix = m.groupValues[1]
            val match = m.value
            val base = when {
                match.startsWith("KIRI") -> "KIRY"
                match.startsWith("Kiri") -> "Kiry"
                else -> "kiry"
            }
            base + suffix
        }

    /**
     * Writes the metadata file that travels with the audio.
     *
     * Everything here is either unrecoverable after the fact (which microphone path the device
     * granted, whether AGC actually turned off, the engine's word timings and confidence) or
     * expensive to regenerate (a second paid transcription). Returns null if the write failed.
     */
    private fun writeSidecar(
        audioFile: File,
        rawText: String,
        displayText: String,
        verbatimText: String?,
        provider: String,
        model: String,
        engineResponse: org.json.JSONObject,
        capture: Recorder.CaptureMetadata?,
    ): File? {
        val metaFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.json")
        return try {
            val capturedEpochMs = audioFile.nameWithoutExtension
                .substringAfter("whisper_", "")
                .toLongOrNull()
            val json = org.json.JSONObject().apply {
                put("schema", 2)
                put("audio", audioFile.name)
                put("bytes", audioFile.length())
                put("captured_epoch_ms", capturedEpochMs ?: org.json.JSONObject.NULL)
                // The archive shards by the day the words were spoken, which is the device's
                // idea of the date, not the server's.
                put(
                    "captured_utc_offset_minutes",
                    java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000,
                )
                put("transcript_raw", rawText)
                put("transcript_display", displayText)
                put("transcript_verbatim", verbatimText ?: org.json.JSONObject.NULL)
                put("transcribed", rawText.isNotEmpty())
                put("capture", capture?.toJson() ?: org.json.JSONObject.NULL)
                put("engine", org.json.JSONObject().apply {
                    put("provider", provider)
                    put("model", model)
                    put("via", "brokentooth-relay")
                    put("response", engineResponse)
                })
                put("written_at", System.currentTimeMillis())
            }
            metaFile.writeText(json.toString(2))
            metaFile
        } catch (e: Exception) {
            // Previously swallowed. A lost sidecar means audio with no transcript attached and
            // no signal that anything went wrong, which is the one failure a corpus cannot
            // absorb — so it is now visible.
            scope.launch {
                appContext.showLongToast("Voice: metadata write failed (${e.message?.take(60)})")
            }
            null
        }
    }

    private fun performTranscription(
        audioFile: File,
        provider: dev.patrickgold.florisboard.ime.voice.VoiceManager.TranscriptionProvider =
            appContext.voiceManager().value.transcriptionProvider.value,
    ) {
        activeState.isTranscribing = true
        val voiceManager = appContext.voiceManager().value
        val capture = recorder?.lastCapture
        scope.launch {
            val result = WhisperClient.transcribe(audioFile, provider)
            result.onSuccess { transcription ->
                val rawText = transcription.text
                val fixed = applyDictionaryFixups(rawText)

                // Tag this as voice input for harvest analysis
                dev.patrickgold.florisboard.ime.nlp.HarvestManager.setSessionSource("VOICE")
                editorInstance.commitText(fixed)
                voiceManager.addTranscription(fixed)
                voiceManager.removePending(audioFile.absolutePath)

                val sidecar = writeSidecar(
                    audioFile, rawText, fixed, transcription.verbatimText,
                    transcription.provider, transcription.model, transcription.raw, capture,
                )
                if (sidecar != null) {
                    archiveQueue.enqueue(audioFile, sidecar)
                    scope.launch(Dispatchers.IO) { runCatching { archiveQueue.drain() } }
                }

                dev.patrickgold.florisboard.ime.nlp.HarvestManager.flushSession()
                dev.patrickgold.florisboard.ime.nlp.HarvestManager.setSessionSource("TYPING")

                activeState.batchEdit {
                    it.isTranscribing = false
                    it.imeUiMode = ImeUiMode.TEXT
                }
            }.onFailure {
                activeState.isTranscribing = false
                // Queue for later retry
                voiceManager.addPending(audioFile, provider)
                // Keep in VOICE mode so user can retry
                scope.launch {
                    appContext.showShortToast("Transcription failed — saved for retry")
                }
            }
        }
    }

    fun retryAllPending() {
        val voiceManager = appContext.voiceManager().value
        val pending = voiceManager.getPendingFiles()
        if (pending.isEmpty() || activeState.isTranscribing) return
        // Retry the first pending item
        val first = pending.first()
        val file = File(first.filePath)
        if (file.exists()) {
            lastAudioFile = file
            performTranscription(file, first.provider)
        } else {
            voiceManager.removePending(first.filePath)
        }
    }






    private val activeEvaluatorGuard = Mutex(locked = false)
    private var activeEvaluatorVersion = AtomicInteger(0)
    private val _activeEvaluator = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val activeEvaluator get() = _activeEvaluator.asStateFlow()
    private val _activeSmartbarEvaluator = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val activeSmartbarEvaluator get() = _activeSmartbarEvaluator.asStateFlow()
    private val _lastCharactersEvaluator = MutableStateFlow<ComputingEvaluator>(DefaultComputingEvaluator)
    val lastCharactersEvaluator get() = _lastCharactersEvaluator.asStateFlow()

    val inputEventDispatcher = InputEventDispatcher.new(
        repeatableKeyCodes = intArrayOf(
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.DELETE,
            KeyCode.FORWARD_DELETE,
            KeyCode.UNDO,
            KeyCode.REDO,
        )
    ).also { it.keyEventReceiver = this }

    init {
        scope.launch(Dispatchers.Main.immediate) {
            resources.anyChanged.observeForever {
                updateActiveEvaluators {
                    keyboardCache.clear()
                }
            }
            // Row visibility became profile-scoped in Stage 04, so the cache must also be dropped
            // when the active profile changes and not only when a row inside it is toggled —
            // otherwise switching profiles would keep serving the previous profile's arrangement.
            //
            // Both profiles are observed rather than re-subscribing to whichever is active. The
            // action is an idempotent cache clear, so a write to the inactive profile costs one
            // wasted invalidation from the settings screen, which is cheaper than a flatMapLatest
            // that has to tear down and rebuild subscriptions on every profile switch.
            val rowVisibilityFlows = KeyboardProfile.entries.flatMap { profile ->
                val profilePrefs = prefs.keyboard.profile(profile)
                listOf(
                    profilePrefs.numberRow.asFlow(),
                    profilePrefs.devRow.asFlow(),
                    profilePrefs.modRowsVisible.asFlow(),
                )
            }
            merge(prefs.keyboard.activeProfileId.asFlow(), *rowVisibilityFlows.toTypedArray())
                .collectIn(scope) {
                    updateActiveEvaluators {
                        keyboardCache.clear(KeyboardMode.CHARACTERS)
                    }
                }
            prefs.keyboard.hintedNumberRowEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.hintedSymbolsEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.utilityKeyEnabled.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            prefs.keyboard.utilityKeyAction.asFlow().collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            activeState.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            subtypeManager.subtypesFlow.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            subtypeManager.activeSubtypeFlow.collectLatestIn(scope) {
                reevaluateInputShiftState()
                updateActiveEvaluators()
                editorInstance.refreshComposing()
                resetSuggestions(editorInstance.activeContent)
            }
            clipboardManager.primaryClipFlow.collectLatestIn(scope) {
                updateActiveEvaluators()
            }
            editorInstance.activeContentFlow.collectIn(scope) { content ->
                resetSuggestions(content)
            }
            prefs.devtools.enabled.asFlow().collectLatestIn(scope) {
                reevaluateDebugFlags()
            }
            prefs.devtools.showDragAndDropHelpers.asFlow().collectLatestIn(scope) {
                reevaluateDebugFlags()
            }
        }
    }

    private fun updateActiveEvaluators(action: () -> Unit = { }) = scope.launch {
        activeEvaluatorGuard.withLock {
            action()
            val editorInfo = editorInstance.activeInfo
            val state = activeState.snapshot()
            val subtype = subtypeManager.activeSubtype
            val mode = state.keyboardMode
            // We need to reset the snapshot input shift state for non-character layouts, because the shift mechanic
            // only makes sense for the character layouts.
            if (mode != KeyboardMode.CHARACTERS) {
                state.inputShiftState = InputShiftState.UNSHIFTED
            }
            val (computedKeyboard, usesLayoutPack) = if (mode == KeyboardMode.CHARACTERS) {
                val pack = layoutFlow.value
                val layoutPackResult = runCatching {
                    layoutManager.computeKeyboardFromLayoutPack(
                        pack,
                        mode,
                        subtype,
                        editorInfo,
                        state,
                    )
                }
                val layoutPackKeyboard = layoutPackResult.getOrNull()
                if (layoutPackKeyboard != null && layoutPackKeyboard.arrangement.isNotEmpty()) {
                    layoutPackKeyboard to true
                } else {
                    val error = layoutPackResult.exceptionOrNull()
                    if (error != null) {
                        flogWarning(LogTopic.LAYOUT_MANAGER) {
                            "Falling back to extension layout for characters: ${error.message}"
                        }
                    } else {
                        flogWarning(LogTopic.LAYOUT_MANAGER) {
                            "Layout pack produced an empty arrangement, falling back to extension layout"
                        }
                    }
                    keyboardCache.getOrElseAsync(mode, subtype) {
                        layoutManager.computeKeyboardAsync(
                            keyboardMode = mode,
                            subtype = subtype,
                        ).await()
                    } to false
                }
            } else {
                keyboardCache.getOrElseAsync(mode, subtype) {
                    layoutManager.computeKeyboardAsync(
                        keyboardMode = mode,
                        subtype = subtype,
                    ).await()
                } to false
            }
            val computingEvaluator = ComputingEvaluatorImpl(
                version = activeEvaluatorVersion.getAndAdd(1),
                keyboard = computedKeyboard,
                editorInfo = editorInfo,
                state = state,
                subtype = subtype,
            )
            for (key in computedKeyboard.keys()) {
                key.compute(computingEvaluator)
                if (usesLayoutPack) {
                    val layoutPackData = key.data as? LayoutPackKeyData
                    if (layoutPackData != null) {
                        // An authored unit is preserved exactly. The geometry policy supplies a
                        // canonical width only where the pack author did not state one, so a pack's
                        // deliberate asymmetry survives normalization.
                        key.authoredWidthUnits = layoutPackData.widthUnits.coerceAtLeast(0f)
                        if (layoutPackData.isSpacer) {
                            key.isEnabled = false
                            key.isVisible = false
                            key.isStructuralSpacer = true
                        }
                    }
                }
                key.computeLabelsAndDrawables(computingEvaluator)
            }
            _activeEvaluator.value = computingEvaluator
            _activeSmartbarEvaluator.value = computingEvaluator.asSmartbarQuickActionsEvaluator()
            if (computedKeyboard.mode == KeyboardMode.CHARACTERS) {
                _lastCharactersEvaluator.value = computingEvaluator
            }
        }
    }

    fun reevaluateInputShiftState() {
        if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
            val shift = prefs.correction.autoCapitalization.get()
                && subtypeManager.activeSubtype.primaryLocale.supportsCapitalization
                && editorInstance.activeCursorCapsMode != InputAttributes.CapsMode.NONE
            activeState.inputShiftState = when {
                shift -> InputShiftState.SHIFTED_AUTOMATIC
                else -> InputShiftState.UNSHIFTED
            }
        }
    }

    fun resetSuggestions(content: EditorContent) {
        if (!(activeState.isComposingEnabled || nlpManager.isSuggestionOn())) {
            nlpManager.clearSuggestions()
            return
        }
        nlpManager.suggest(subtypeManager.activeSubtype, content)
    }

    /**
     * @return If the language switch should be shown.
     */
    fun shouldShowLanguageSwitch(): Boolean {
        return subtypeManager.subtypes.size > 1
    }

    suspend fun toggleOneHandedMode() {
        prefs.keyboard.oneHandedModeEnabled.set(!prefs.keyboard.oneHandedModeEnabled.get())
    }

    fun executeSwipeAction(swipeAction: SwipeAction) {
        val keyData = when (swipeAction) {
            SwipeAction.CYCLE_TO_PREVIOUS_KEYBOARD_MODE -> when (activeState.keyboardMode) {
                KeyboardMode.CHARACTERS -> TextKeyData.VIEW_NUMERIC_ADVANCED
                KeyboardMode.NUMERIC_ADVANCED -> TextKeyData.VIEW_SYMBOLS2
                KeyboardMode.SYMBOLS2 -> TextKeyData.VIEW_SYMBOLS
                else -> TextKeyData.VIEW_CHARACTERS
            }
            SwipeAction.CYCLE_TO_NEXT_KEYBOARD_MODE -> when (activeState.keyboardMode) {
                KeyboardMode.CHARACTERS -> TextKeyData.VIEW_SYMBOLS
                KeyboardMode.SYMBOLS -> TextKeyData.VIEW_SYMBOLS2
                KeyboardMode.SYMBOLS2 -> TextKeyData.VIEW_NUMERIC_ADVANCED
                else -> TextKeyData.VIEW_CHARACTERS
            }
            SwipeAction.DELETE_WORD -> TextKeyData.DELETE_WORD
            SwipeAction.HIDE_KEYBOARD -> TextKeyData.IME_HIDE_UI
            SwipeAction.INSERT_SPACE -> TextKeyData.SPACE
            SwipeAction.MOVE_CURSOR_DOWN -> TextKeyData.ARROW_DOWN
            SwipeAction.MOVE_CURSOR_UP -> TextKeyData.ARROW_UP
            SwipeAction.MOVE_CURSOR_LEFT -> TextKeyData.ARROW_LEFT
            SwipeAction.MOVE_CURSOR_RIGHT -> TextKeyData.ARROW_RIGHT
            SwipeAction.MOVE_CURSOR_START_OF_LINE -> TextKeyData.MOVE_START_OF_LINE
            SwipeAction.MOVE_CURSOR_END_OF_LINE -> TextKeyData.MOVE_END_OF_LINE
            SwipeAction.MOVE_CURSOR_START_OF_PAGE -> TextKeyData.MOVE_START_OF_PAGE
            SwipeAction.MOVE_CURSOR_END_OF_PAGE -> TextKeyData.MOVE_END_OF_PAGE
            SwipeAction.SHIFT -> TextKeyData.SHIFT
            SwipeAction.REDO -> TextKeyData.REDO
            SwipeAction.UNDO -> TextKeyData.UNDO
            SwipeAction.SHOW_INPUT_METHOD_PICKER -> TextKeyData.SYSTEM_INPUT_METHOD_PICKER
            SwipeAction.SHOW_SUBTYPE_PICKER -> TextKeyData.SHOW_SUBTYPE_PICKER
            SwipeAction.SWITCH_TO_CLIPBOARD_CONTEXT -> TextKeyData.IME_UI_MODE_CLIPBOARD
            SwipeAction.SWITCH_TO_PREV_SUBTYPE -> TextKeyData.IME_PREV_SUBTYPE
            SwipeAction.SWITCH_TO_NEXT_SUBTYPE -> TextKeyData.IME_NEXT_SUBTYPE
            SwipeAction.SWITCH_TO_PREV_KEYBOARD -> TextKeyData.SYSTEM_PREV_INPUT_METHOD
            SwipeAction.TOGGLE_SMARTBAR_VISIBILITY -> TextKeyData.TOGGLE_SMARTBAR_VISIBILITY
            else -> null
        }
        if (keyData != null) {
            inputEventDispatcher.sendDownUp(keyData)
        }
    }

    fun commitCandidate(candidate: SuggestionCandidate) {
        scope.launch {
            candidate.sourceProvider?.notifySuggestionAccepted(subtypeManager.activeSubtype, candidate)
        }
        when (candidate) {
            is ClipboardSuggestionCandidate -> editorInstance.commitClipboardItem(candidate.clipboardItem)
            else -> editorInstance.commitCompletion(candidate)
        }
    }

    fun commitGesture(word: String) {
        // Auto-commit word with trailing space for swipe typing
        val textBefore = editorInstance.activeContent.textBeforeSelection.toString()
        val smartCased = dev.patrickgold.florisboard.ime.nlp.SymSpellManager.applyPredictedCasing(word, word, textBefore)
        editorInstance.commitGesture(fixCase(smartCased))
        editorInstance.commitText(" ")  // Auto-insert space after swipe word
    }

    /**
     * Changes a word to the current case.
     * eg if [KeyboardState.isUppercase] is true, abc -> ABC
     *    if [caps]     is true, abc -> Abc
     *    otherwise            , abc -> abc
     */
    fun fixCase(word: String): String {
        return when(activeState.inputShiftState) {
            InputShiftState.CAPS_LOCK -> {
                word.uppercase(subtypeManager.activeSubtype.primaryLocale)
            }
            InputShiftState.SHIFTED_MANUAL, InputShiftState.SHIFTED_AUTOMATIC -> {
                word.titlecase(subtypeManager.activeSubtype.primaryLocale)
            }
            else -> word
        }
    }

    /**
     * Handles [KeyCode] arrow and move events, behaves differently depending on text selection.
     */
    fun handleArrow(code: Int, count: Int = 1) = editorInstance.apply {
        val isShiftPressed = activeState.isManualSelectionMode || inputEventDispatcher.isPressed(KeyCode.SHIFT)
        val isCtrlPressed = activeState.isCtrlPressed || inputEventDispatcher.isPressed(KeyCode.CTRL)
        val content = activeContent
        val selection = content.selection
        when (code) {
            KeyCode.ARROW_LEFT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, meta(shift = isShiftPressed, ctrl = isCtrlPressed), count)
            }
            KeyCode.ARROW_RIGHT -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, meta(shift = isShiftPressed, ctrl = isCtrlPressed), count)
            }
            KeyCode.ARROW_UP -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP, meta(shift = isShiftPressed, ctrl = isCtrlPressed), count)
            }
            KeyCode.ARROW_DOWN -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, meta(shift = isShiftPressed, ctrl = isCtrlPressed), count)
            }
            KeyCode.MOVE_START_OF_PAGE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_UP, meta(alt = true, shift = isShiftPressed, ctrl = isCtrlPressed), count)
            }
            KeyCode.MOVE_END_OF_PAGE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, meta(alt = true, shift = isShiftPressed, ctrl = isCtrlPressed), count)
            }
            KeyCode.MOVE_START_OF_LINE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = true
                    activeState.isManualSelectionModeEnd = false
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_MOVE_HOME, meta(ctrl = true, shift = isShiftPressed), count)
            }
            KeyCode.MOVE_END_OF_LINE -> {
                if (!selection.isSelectionMode && activeState.isManualSelectionMode) {
                    activeState.isManualSelectionModeStart = false
                    activeState.isManualSelectionModeEnd = true
                }
                sendDownUpKeyEvent(KeyEvent.KEYCODE_MOVE_END, meta(ctrl = true, shift = isShiftPressed), count)
            }
        }
    }

    /**
     * Handles a [KeyCode.CLIPBOARD_SELECT] event.
     */
    private fun handleClipboardSelect() {
        val activeSelection = editorInstance.activeContent.selection
        activeState.isManualSelectionMode = if (activeSelection.isSelectionMode) {
            if (activeState.isManualSelectionMode && activeState.isManualSelectionModeStart) {
                editorInstance.setSelection(activeSelection.start, activeSelection.start)
            } else {
                editorInstance.setSelection(activeSelection.end, activeSelection.end)
            }
            false
        } else {
            !activeState.isManualSelectionMode
        }
    }

    private fun revertPreviouslyAcceptedCandidate() {
        editorInstance.phantomSpace.candidateForRevert?.let { candidateForRevert ->
            candidateForRevert.sourceProvider?.let { sourceProvider ->
                scope.launch {
                    sourceProvider.notifySuggestionReverted(
                        subtype = subtypeManager.activeSubtype,
                        candidate = candidateForRevert,
                    )
                }
            }
        }
    }

    /**
     * Handles a [KeyCode.DELETE] event.
     */
    private fun handleBackwardDelete(unit: OperationUnit) {
        if (inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
            return handleForwardDelete(unit)
        }
        activeState.batchEdit {
            it.isManualSelectionMode = false
            it.isManualSelectionModeStart = false
            it.isManualSelectionModeEnd = false
        }
        revertPreviouslyAcceptedCandidate()
        editorInstance.deleteBackwards(unit)
    }

    /**
     * Handles a [KeyCode.FORWARD_DELETE] event.
     */
    private fun handleForwardDelete(unit: OperationUnit) {
        activeState.batchEdit {
            it.isManualSelectionMode = false
            it.isManualSelectionModeStart = false
            it.isManualSelectionModeEnd = false
        }
        revertPreviouslyAcceptedCandidate()
        editorInstance.deleteForwards(unit)
    }

    /**
     * Handles a [KeyCode.ENTER] event.
     */
    private fun handleEnter() {
        val info = editorInstance.activeInfo
        val isShiftPressed = inputEventDispatcher.isPressed(KeyCode.SHIFT)
        if (editorInstance.tryPerformEnterCommitRaw()) {
            return
        }
        if (info.imeOptions.flagNoEnterAction || info.inputAttributes.flagTextMultiLine && isShiftPressed) {
            editorInstance.performEnter()
        } else {
            when (val action = info.imeOptions.action) {
                ImeOptions.Action.DONE,
                ImeOptions.Action.GO,
                ImeOptions.Action.NEXT,
                ImeOptions.Action.PREVIOUS,
                ImeOptions.Action.SEARCH,
                ImeOptions.Action.SEND -> {
                    editorInstance.performEnterAction(action)
                }
                else -> editorInstance.performEnter()
            }
        }
    }

    /**
     * Handles a [KeyCode.LANGUAGE_SWITCH] event. Also handles if the language switch should cycle
     * FlorisBoard internal or system-wide.
     */
    private fun handleLanguageSwitch() {
        when (prefs.keyboard.utilityKeyAction.get()) {
            UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS,
            UtilityKeyAction.SWITCH_LANGUAGE -> subtypeManager.switchToNextSubtype()
            else -> FlorisImeService.switchToNextInputMethod()
        }
    }

    /**
     * Handles a [KeyCode.SHIFT] down event.
     */
    private fun handleShiftDown(data: KeyData) {
        val prefs = prefs.keyboard.capitalizationBehavior
        when (prefs.get()) {
            CapitalizationBehavior.CAPSLOCK_BY_DOUBLE_TAP -> {
                if (inputEventDispatcher.isConsecutiveDown(data)) {
                    activeState.inputShiftState = InputShiftState.CAPS_LOCK
                } else {
                    if (activeState.inputShiftState == InputShiftState.UNSHIFTED) {
                        activeState.inputShiftState = InputShiftState.SHIFTED_MANUAL
                    } else {
                        activeState.inputShiftState = InputShiftState.UNSHIFTED
                    }
                }
            }
            CapitalizationBehavior.CAPSLOCK_BY_CYCLE -> {
                activeState.inputShiftState = when (activeState.inputShiftState) {
                    InputShiftState.UNSHIFTED -> InputShiftState.SHIFTED_MANUAL
                    InputShiftState.SHIFTED_MANUAL -> InputShiftState.CAPS_LOCK
                    InputShiftState.SHIFTED_AUTOMATIC -> InputShiftState.UNSHIFTED
                    InputShiftState.CAPS_LOCK -> InputShiftState.UNSHIFTED
                }
            }
        }
    }

    /**
     * Handles a [KeyCode.SHIFT] up event.
     */
    private fun handleShiftUp(data: KeyData) {
        if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isAnyPressed() &&
            !inputEventDispatcher.isUninterruptedEventSequence(data)) {
            activeState.inputShiftState = InputShiftState.UNSHIFTED
        }
    }

    /**
     * Handles a [KeyCode.CAPS_LOCK] event.
     */
    private fun handleCapsLock() {
        activeState.inputShiftState = InputShiftState.CAPS_LOCK
    }

    /**
     * Handles a [KeyCode.SHIFT] cancel event.
     */
    private fun handleShiftCancel() {
        activeState.inputShiftState = InputShiftState.UNSHIFTED
    }

    /**
     * Handles a [KeyCode.CTRL] down event.
     */
    private fun handleCtrlDown(data: KeyData) {
        if (activeState.isCtrlLocked) {
            activeState.isCtrlLocked = false
            activeState.isCtrlPressed = false
        } else {
            if (inputEventDispatcher.isConsecutiveDown(data)) {
                activeState.isCtrlLocked = true
                activeState.isCtrlPressed = true
            } else {
                activeState.isCtrlPressed = !activeState.isCtrlPressed
            }
        }
    }

    /**
     * Handles a [KeyCode.CTRL] up event.
     */
    private fun handleCtrlUp(data: KeyData) {
        // Keep ctrl latched until the next key press is consumed.
    }

    /**
     * Handles a [KeyCode.CTRL] cancel event.
     */
    private fun handleCtrlCancel() {
        activeState.isCtrlPressed = false
        activeState.isCtrlLocked = false
    }

    private fun meta(
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ): Int = editorInstance.meta(ctrl = ctrl, alt = alt, shift = shift)

    /**
     * Maps a character to a hardware [KeyEvent] code for chord dispatch.
     */
    private fun keyEventCodeForChar(char: Char): Int? {
        val upper = char.uppercaseChar()
        val keyName = when {
            upper.isLetter() -> "KEYCODE_$upper"
            upper.isDigit() -> "KEYCODE_$upper"
            upper == ' ' -> "KEYCODE_SPACE"
            upper == '\n' -> "KEYCODE_ENTER"
            else -> return null
        }
        return KeyEvent.keyCodeFromString(keyName).takeIf { it != KeyEvent.KEYCODE_UNKNOWN }
    }

    /**
     * Sends a ctrl+key chord for character keys and returns true if handled.
     */
    private fun sendCtrlChordIfNeeded(data: KeyData): Boolean {
        if (!activeState.isCtrlPressed) return false
        if (data.code == KeyCode.CTRL) return false
        if (data.code !in KeyCode.Spec.CHARACTERS) return false
        val keyEventCode = keyEventCodeForChar(data.code.toChar()) ?: return false
        val shiftPressed = inputEventDispatcher.isPressed(KeyCode.SHIFT)
        val metaState = meta(ctrl = true, shift = shiftPressed)
        val handled = editorInstance.sendDownUpKeyEvent(keyEventCode, metaState)
        if (handled && !activeState.isCtrlLocked) {
            activeState.isCtrlPressed = false
        }
        return handled
    }

    /**
     * Handles a hardware [KeyEvent.KEYCODE_SPACE] event. Same as [handleSpace],
     * but skips handling changing to characters keyboard and double space periods.
     */
    fun handleHardwareKeyboardSpace() {
        val candidate = nlpManager.getAutoCommitCandidate()
        candidate?.let { commitCandidate(it) }
        // Skip handling changing to characters keyboard and double space periods
        // TODO: this is whether we commit space after selecting candidate. Should be determined by SuggestionProvider
        if (!subtypeManager.activeSubtype.primaryLocale.supportsAutoSpace &&
                candidate != null) { /* Do nothing */ } else {
            editorInstance.commitText(KeyCode.SPACE.toChar().toString())
        }
    }

    /**
     * Handles a [KeyCode.SPACE] event. Also handles the auto-correction of two space taps if
     * enabled by the user.
     */
    fun handleSpaceLongPress() {
        val modRowsVisible = prefs.keyboard.activeProfilePrefs().modRowsVisible
        android.util.Log.i("FlorisBoard_Debug", "Space long press detected! Current modRowsVisible: ${modRowsVisible.get()}")
        scope.launch {
            modRowsVisible.let { it.set(!it.get()) }
            android.util.Log.i("FlorisBoard_Debug", "New modRowsVisible: ${modRowsVisible.get()}")
            updateActiveEvaluators {
                keyboardCache.clear(KeyboardMode.CHARACTERS)
            }
        }
    }

    private fun handleSpace(data: KeyData) {
        // DISABLED: NLP auto-commit - we now use SymSpell autocorrect in commitTextInternal instead
        // val candidate = nlpManager.getAutoCommitCandidate()
        // candidate?.let { commitCandidate(it) }
        
        if (prefs.keyboard.spaceBarSwitchesToCharacters.get()) {
            when (activeState.keyboardMode) {
                KeyboardMode.NUMERIC_ADVANCED,
                KeyboardMode.SYMBOLS,
                KeyboardMode.SYMBOLS2 -> {
                    activeState.keyboardMode = KeyboardMode.CHARACTERS
                }
                else -> { /* Do nothing */ }
            }
        }
        if (prefs.correction.doubleSpacePeriod.get()) {
            if (inputEventDispatcher.isConsecutiveUp(data)) {
                val text = editorInstance.run { activeContent.getTextBeforeCursor(2) }
                if (text.length == 2 && DoubleSpacePeriodMatcher.matches(text)) {
                    editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                    editorInstance.commitText(". ")
                    return
                }
            }
        }
        // Always commit space - autocorrect happens in commitTextInternal
        editorInstance.commitText(KeyCode.SPACE.toChar().toString())
    }

    /**
     * Handles a [KeyCode.TOGGLE_INCOGNITO_MODE] event.
     */
    private suspend fun handleToggleIncognitoMode() {
        prefs.suggestion.forceIncognitoModeFromDynamic.set(!prefs.suggestion.forceIncognitoModeFromDynamic.get())
        val newState = !activeState.isIncognitoMode
        activeState.isIncognitoMode = newState
        lastToastReference.get()?.cancel()
        lastToastReference = WeakReference(
            if (newState) {
                appContext.showLongToast(
                    R.string.incognito_mode__toast_after_enabled,
                    "app_name" to appContext.getString(R.string.floris_app_name),
                )
            } else {
                appContext.showLongToast(
                    R.string.incognito_mode__toast_after_disabled,
                    "app_name" to appContext.getString(R.string.floris_app_name),
                )
            }
        )
    }

    /**
     * Handles a [KeyCode.TOGGLE_AUTOCORRECT] event.
     */
    private fun handleToggleAutocorrect() {
        lastToastReference.get()?.cancel()
        lastToastReference = WeakReference(
            appContext.showLongToastSync("Autocorrect toggle is a placeholder and not yet implemented")
        )
    }

    /**
     * Handles a [KeyCode.KANA_SWITCHER] event
     */
    private fun handleKanaSwitch() {
        activeState.batchEdit {
            it.isKanaKata = !it.isKanaKata
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_HIRA] event
     */
    private fun handleKanaHira() {
        activeState.batchEdit {
            it.isKanaKata = false
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_KATA] event
     */
    private fun handleKanaKata() {
        activeState.batchEdit {
            it.isKanaKata = true
            it.isCharHalfWidth = false
        }
    }

    /**
     * Handles a [KeyCode.KANA_HALF_KATA] event
     */
    private fun handleKanaHalfKata() {
        activeState.batchEdit {
            it.isKanaKata = true
            it.isCharHalfWidth = true
        }
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthSwitch() {
        activeState.isCharHalfWidth = !activeState.isCharHalfWidth
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthFull() {
        activeState.isCharHalfWidth = false
    }

    /**
     * Handles a [KeyCode.CHAR_WIDTH_SWITCHER] event
     */
    private fun handleCharWidthHalf() {
        activeState.isCharHalfWidth = true
    }

    override fun onInputKeyDown(data: KeyData) {
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.begin()
            }
            KeyCode.SHIFT -> handleShiftDown(data)
            KeyCode.CTRL -> handleCtrlDown(data)
        }
    }

    override fun onInputKeyUp(data: KeyData) = activeState.batchEdit {
        if (shouldClearTmuxPrefixVisualState(activeState.isTmuxPrefixActive, data.code)) {
            activeState.isTmuxPrefixActive = false
        }
        val shouldConsumeCtrl = activeState.isCtrlPressed && data.code != KeyCode.CTRL
        if (shouldConsumeCtrl && sendCtrlChordIfNeeded(data)) {
            activeState.isCtrlPressed = false
            return@batchEdit
        }
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.end()
                handleArrow(data.code)
            }
            KeyCode.CAPS_LOCK -> handleCapsLock()
            KeyCode.CHAR_WIDTH_SWITCHER -> handleCharWidthSwitch()
            KeyCode.CHAR_WIDTH_FULL -> handleCharWidthFull()
            KeyCode.CHAR_WIDTH_HALF -> handleCharWidthHalf()
            KeyCode.CLIPBOARD_CUT -> editorInstance.performClipboardCut()
            KeyCode.CLIPBOARD_COPY -> editorInstance.performClipboardCopy()
            KeyCode.CLIPBOARD_PASTE -> editorInstance.performClipboardPaste()
            KeyCode.CLIPBOARD_SELECT -> handleClipboardSelect()
            KeyCode.CLIPBOARD_SELECT_ALL -> editorInstance.performClipboardSelectAll()
            KeyCode.CLIPBOARD_CLEAR_HISTORY -> clipboardManager.clearHistory()
            KeyCode.CLIPBOARD_CLEAR_FULL_HISTORY -> clipboardManager.clearFullHistory()
            KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
                if (prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned.get()) {
                    clipboardManager.primaryClip?.let { clipboardManager.deleteClip(it, onlyIfUnpinned = true) }
                }
                clipboardManager.updatePrimaryClip(null)
                appContext.showShortToastSync(R.string.clipboard__cleared_primary_clip)
            }
            KeyCode.TOGGLE_COMPACT_LAYOUT -> scope.launch { toggleOneHandedMode() }
            KeyCode.COMPACT_LAYOUT_TO_LEFT -> scope.launch {
                prefs.keyboard.oneHandedMode.set(OneHandedMode.START)
                toggleOneHandedMode()
            }
            KeyCode.COMPACT_LAYOUT_TO_RIGHT -> scope.launch {
                prefs.keyboard.oneHandedMode.set(OneHandedMode.END)
                toggleOneHandedMode()
            }
            KeyCode.DELETE -> handleBackwardDelete(OperationUnit.CHARACTERS)
            KeyCode.DELETE_WORD -> handleBackwardDelete(OperationUnit.WORDS)
            KeyCode.ENTER -> handleEnter()
            KeyCode.FORWARD_DELETE -> handleForwardDelete(OperationUnit.CHARACTERS)
            KeyCode.FORWARD_DELETE_WORD -> handleForwardDelete(OperationUnit.WORDS)
            KeyCode.IME_SHOW_UI -> {
                activeState.isKeyboardMinimized = false
                FlorisImeService.showUi()
            }
            KeyCode.IME_HIDE_UI -> activeState.isKeyboardMinimized = true
            KeyCode.TMUX_PREFIX -> {
                activeState.isTmuxPrefixActive =
                    editorInstance.sendDownUpKeyEvent(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON)
            }
            KeyCode.IME_PREV_SUBTYPE -> subtypeManager.switchToPrevSubtype()
            KeyCode.IME_NEXT_SUBTYPE -> subtypeManager.switchToNextSubtype()
            KeyCode.AI_GENERATE -> scope.launch(Dispatchers.IO) {
            appContext.showShortToastSync("Thinking...")
            val activeContent = editorInstance.activeContent
            val selection = activeContent.selection
            val text = activeContent.text
            val selectedText = if (selection.isValid && selection.length > 0) {
                 text.substring(selection.start, selection.end)
            } else {
                 ""
            }
            
            val config = if (selectedText.isNotBlank()) {
                dev.patrickgold.florisboard.ime.nlp.GemmaClient.PromptConfig(
                    mode = dev.patrickgold.florisboard.ime.nlp.GemmaClient.PromptConfig.Mode.REWRITE,
                    originalInput = selectedText
                )
            } else if (activeContent.textBeforeSelection.isBlank()) {
                val clip = clipboardManager.primaryClip?.text?.toString() ?: ""
                if (clip.isNotBlank()) {
                    dev.patrickgold.florisboard.ime.nlp.GemmaClient.PromptConfig(
                        mode = dev.patrickgold.florisboard.ime.nlp.GemmaClient.PromptConfig.Mode.REPLY,
                        originalInput = "", 
                        context = clip
                    )
                } else {
                     dev.patrickgold.florisboard.ime.nlp.GemmaClient.PromptConfig(
                        mode = dev.patrickgold.florisboard.ime.nlp.GemmaClient.PromptConfig.Mode.CONTINUE,
                        originalInput = "Hello"
                     )
                }
            } else {
                dev.patrickgold.florisboard.ime.nlp.GemmaClient.PromptConfig(
                    mode = dev.patrickgold.florisboard.ime.nlp.GemmaClient.PromptConfig.Mode.CONTINUE,
                    originalInput = activeContent.textBeforeSelection.toString()
                )
            }

            val result = GemmaClient.complete(config)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    if (config.mode == dev.patrickgold.florisboard.ime.nlp.GemmaClient.PromptConfig.Mode.REWRITE) {
                         editorInstance.commitText(result)
                    } else {
                         commitGesture(result)
                    }
                } else {
                    Toast.makeText(appContext, "AI Error: Check server", Toast.LENGTH_SHORT).show()
                }
            }
        }
            KeyCode.IME_UI_MODE_TEXT -> activeState.imeUiMode = ImeUiMode.TEXT
            KeyCode.IME_UI_MODE_MEDIA -> activeState.imeUiMode = ImeUiMode.MEDIA
            KeyCode.IME_UI_MODE_CLIPBOARD -> activeState.imeUiMode = ImeUiMode.CLIPBOARD
            KeyCode.VOICE_INPUT -> {
                requestAudioPermission()
                if (activeState.imeUiMode == ImeUiMode.VOICE) {
                    if (activeState.isRecording) {
                        stopVoiceCapture(appContext)
                    } else if (!activeState.isTranscribing) {
                        activeState.imeUiMode = ImeUiMode.TEXT
                    }
                } else {
                    activeState.imeUiMode = ImeUiMode.VOICE
                    startVoiceCapture(appContext)
                }
            }
            KeyCode.KANA_SWITCHER -> handleKanaSwitch()
            KeyCode.KANA_HIRA -> handleKanaHira()
            KeyCode.KANA_KATA -> handleKanaKata()
            KeyCode.KANA_HALF_KATA -> handleKanaHalfKata()
            KeyCode.LANGUAGE_SWITCH -> handleLanguageSwitch()
            KeyCode.REDO -> editorInstance.performRedo()
            KeyCode.SETTINGS -> FlorisImeService.launchSettings()
            KeyCode.SHIFT -> handleShiftUp(data)
            KeyCode.CTRL -> handleCtrlUp(data)
            KeyCode.SPACE -> handleSpace(data)
            KeyCode.SYSTEM_INPUT_METHOD_PICKER -> InputMethodUtils.showImePicker(appContext)
            KeyCode.SHOW_SUBTYPE_PICKER -> {
                appContext.keyboardManager.value.activeState.isSubtypeSelectionVisible = true
            }
            KeyCode.SYSTEM_PREV_INPUT_METHOD -> FlorisImeService.switchToPrevInputMethod()
            KeyCode.SYSTEM_NEXT_INPUT_METHOD -> FlorisImeService.switchToNextInputMethod()
            KeyCode.TAB -> editorInstance.sendDownUpKeyEvent(KeyEvent.KEYCODE_TAB, 0)
            KeyCode.ESCAPE -> {
                editorInstance.sendDownUpKeyEvent(KeyEvent.KEYCODE_ESCAPE, 0)
            }
            KeyCode.TOGGLE_SMARTBAR_VISIBILITY -> scope.launch {
                prefs.smartbar.enabled.let { it.set(!it.get()) }
            }
            KeyCode.TOGGLE_NUMBER_ROW -> scope.launch {
                prefs.keyboard.activeProfilePrefs().numberRow.let { it.set(!it.get()) }
            }
            KeyCode.TOGGLE_DEV_ROW -> scope.launch {
                prefs.keyboard.activeProfilePrefs().devRow.let { it.set(!it.get()) }
            }
            KeyCode.TOGGLE_ACTIONS_OVERFLOW -> {
                activeState.isActionsOverflowVisible = !activeState.isActionsOverflowVisible
            }
            KeyCode.TOGGLE_ACTIONS_EDITOR -> {
                activeState.isActionsEditorVisible = !activeState.isActionsEditorVisible
            }
            KeyCode.TOGGLE_INCOGNITO_MODE -> scope.launch { handleToggleIncognitoMode() }
            KeyCode.TOGGLE_AUTOCORRECT -> handleToggleAutocorrect()
            KeyCode.UNDO -> editorInstance.performUndo()
            KeyCode.VIEW_CHARACTERS -> activeState.keyboardMode = KeyboardMode.CHARACTERS
            KeyCode.VIEW_NUMERIC -> activeState.keyboardMode = KeyboardMode.NUMERIC
            KeyCode.VIEW_NUMERIC_ADVANCED -> activeState.keyboardMode = KeyboardMode.NUMERIC_ADVANCED
            KeyCode.VIEW_PHONE -> activeState.keyboardMode = KeyboardMode.PHONE
            KeyCode.VIEW_PHONE2 -> activeState.keyboardMode = KeyboardMode.PHONE2
            KeyCode.VIEW_SYMBOLS -> activeState.keyboardMode = KeyboardMode.SYMBOLS
            KeyCode.VIEW_SYMBOLS2 -> activeState.keyboardMode = KeyboardMode.SYMBOLS2
            else -> {
                if (activeState.imeUiMode == ImeUiMode.MEDIA) {
                    nlpManager.getAutoCommitCandidate()?.let { commitCandidate(it) }
                    editorInstance.commitText(data.asString(isForDisplay = false))
                    if (shouldConsumeCtrl) {
                        activeState.isCtrlPressed = false
                    }
                    return@batchEdit
                }
                when (activeState.keyboardMode) {
                    KeyboardMode.NUMERIC,
                    KeyboardMode.NUMERIC_ADVANCED,
                    KeyboardMode.PHONE,
                    KeyboardMode.PHONE2 -> when (data.type) {
                        KeyType.CHARACTER,
                        KeyType.NUMERIC -> {
                            val text = data.asString(isForDisplay = false)
                            editorInstance.commitText(text)
                        }
                        else -> when (data.code) {
                            KeyCode.PHONE_PAUSE,
                            KeyCode.PHONE_WAIT -> {
                                val text = data.asString(isForDisplay = false)
                                editorInstance.commitText(text)
                            }
                        }
                    }
                    else -> when (data.type) {
                        KeyType.CHARACTER, KeyType.NUMERIC ->{
                            val text = data.asString(isForDisplay = false)
                            val isNumber = text.isNotEmpty() && text.all { it.isDigit() }
                            if (!UCharacter.isUAlphabetic(UCharacter.codePointAt(text, 0)) && !isNumber) {
                                nlpManager.getAutoCommitCandidate()?.let { commitCandidate(it) }
                            }
                            editorInstance.commitChar(text)
                        }
                        else -> {
                            flogError(LogTopic.KEY_EVENTS) { "Received unknown key: $data" }
                        }
                    }
                }
                if (activeState.inputShiftState != InputShiftState.CAPS_LOCK && !inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                    activeState.inputShiftState = InputShiftState.UNSHIFTED
                }
            }
        }
        if (shouldConsumeCtrl) {
            activeState.isCtrlPressed = false
        }
    }

    override fun onInputKeyCancel(data: KeyData) {
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> {
                editorInstance.massSelection.end()
            }
            KeyCode.SHIFT -> handleShiftCancel()
            KeyCode.CTRL -> handleCtrlCancel()
        }
    }

    override fun onInputKeyRepeat(data: KeyData) {
        FlorisImeService.inputFeedbackController()?.keyRepeatedAction(data)
        when (data.code) {
            KeyCode.ARROW_DOWN,
            KeyCode.ARROW_LEFT,
            KeyCode.ARROW_RIGHT,
            KeyCode.ARROW_UP,
            KeyCode.MOVE_START_OF_PAGE,
            KeyCode.MOVE_END_OF_PAGE,
            KeyCode.MOVE_START_OF_LINE,
            KeyCode.MOVE_END_OF_LINE -> handleArrow(data.code)
            else -> onInputKeyUp(data)
        }
    }

    private fun reevaluateDebugFlags() {
        val devtoolsEnabled = prefs.devtools.enabled.get()
        activeState.batchEdit {
            activeState.debugShowDragAndDropHelpers = devtoolsEnabled && prefs.devtools.showDragAndDropHelpers.get()
        }
    }

    fun onHardwareKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                handleHardwareKeyboardSpace()
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                handleEnter()
                return true
            }
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                inputEventDispatcher.sendDown(TextKeyData.SHIFT)
                return true
            }
            else -> return false
        }
    }

    fun onHardwareKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                inputEventDispatcher.sendUp(TextKeyData.SHIFT)
                return true
            }
            else -> return false
        }
    }

    inner class KeyboardManagerResources {
        val composers = MutableLiveData<Map<ExtensionComponentName, Composer>>(emptyMap())
        val currencySets = MutableLiveData<Map<ExtensionComponentName, CurrencySet>>(emptyMap())
        val layouts = MutableLiveData<Map<LayoutType, Map<ExtensionComponentName, LayoutArrangementComponent>>>(emptyMap())
        val popupMappings = MutableLiveData<Map<ExtensionComponentName, PopupMappingComponent>>(emptyMap())
        val punctuationRules = MutableLiveData<Map<ExtensionComponentName, PunctuationRule>>(emptyMap())
        val subtypePresets = MutableLiveData<List<SubtypePreset>>(emptyList())

        private val anyChangedGuard = Mutex(locked = false)
        val anyChanged = MutableLiveData(Unit)

        init {
            scope.launch(Dispatchers.Main.immediate) {
                extensionManager.keyboardExtensions.observeForever { keyboardExtensions ->
                    scope.launch {
                        anyChangedGuard.withLock {
                            parseKeyboardExtensions(keyboardExtensions)
                        }
                    }
                }
            }
        }

        private fun parseKeyboardExtensions(keyboardExtensions: List<KeyboardExtension>) {
            val localComposers = mutableMapOf<ExtensionComponentName, Composer>()
            val localCurrencySets = mutableMapOf<ExtensionComponentName, CurrencySet>()
            val localLayouts = mutableMapOf<LayoutType, MutableMap<ExtensionComponentName, LayoutArrangementComponent>>()
            val localPopupMappings = mutableMapOf<ExtensionComponentName, PopupMappingComponent>()
            val localPunctuationRules = mutableMapOf<ExtensionComponentName, PunctuationRule>()
            val localSubtypePresets = mutableListOf<SubtypePreset>()
            for (layoutType in LayoutType.entries) {
                localLayouts[layoutType] = mutableMapOf()
            }
            for (keyboardExtension in keyboardExtensions) {
                keyboardExtension.composers.forEach { composer ->
                    localComposers[ExtensionComponentName(keyboardExtension.meta.id, composer.id)] = composer
                }
                keyboardExtension.currencySets.forEach { currencySet ->
                    localCurrencySets[ExtensionComponentName(keyboardExtension.meta.id, currencySet.id)] = currencySet
                }
                keyboardExtension.layouts.forEach { (type, layoutComponents) ->
                    for (layoutComponent in layoutComponents) {
                        localLayouts[LayoutType.entries.first { it.id == type }]!![ExtensionComponentName(keyboardExtension.meta.id, layoutComponent.id)] = layoutComponent
                    }
                }
                keyboardExtension.popupMappings.forEach { popupMapping ->
                    localPopupMappings[ExtensionComponentName(keyboardExtension.meta.id, popupMapping.id)] = popupMapping
                }
                keyboardExtension.punctuationRules.forEach { punctuationRule ->
                    localPunctuationRules[ExtensionComponentName(keyboardExtension.meta.id, punctuationRule.id)] = punctuationRule
                }
                localSubtypePresets.addAll(keyboardExtension.subtypePresets)
            }
            localSubtypePresets.sortBy { it.locale.displayName() }
            for (languageCode in listOf("en-CA", "en-AU", "en-UK", "en-US")) {
                val index: Int = localSubtypePresets.indexOfFirst { it.locale.languageTag() == languageCode }
                if (index > 0) {
                    localSubtypePresets.add(0, localSubtypePresets.removeAt(index))
                }
            }
            subtypePresets.postValue(localSubtypePresets)
            composers.postValue(localComposers)
            currencySets.postValue(localCurrencySets)
            layouts.postValue(localLayouts)
            popupMappings.postValue(localPopupMappings)
            punctuationRules.postValue(localPunctuationRules)
            anyChanged.postValue(Unit)
        }
    }

    private inner class ComputingEvaluatorImpl(
        override val version: Int,
        override val keyboard: Keyboard,
        override val editorInfo: FlorisEditorInfo,
        override val state: KeyboardState,
        override val subtype: Subtype,
    ) : ComputingEvaluator {

        override fun context(): Context = appContext

        val androidKeyguardManager = context().systemService(AndroidKeyguardManager::class)

        override fun displayLanguageNamesIn(): DisplayLanguageNamesIn {
            return prefs.localization.displayLanguageNamesIn.get()
        }

        override fun evaluateEnabled(data: KeyData): Boolean {
            return when (data.code) {
                KeyCode.CLIPBOARD_COPY,
                KeyCode.CLIPBOARD_CUT -> {
                    state.isSelectionMode && editorInfo.isRichInputEditor
                }
                KeyCode.CLIPBOARD_PASTE -> {
                    !androidKeyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
                        && clipboardManager.canBePasted(clipboardManager.primaryClip)
                }
                KeyCode.CLIPBOARD_CLEAR_PRIMARY_CLIP -> {
                    clipboardManager.canBePasted(clipboardManager.primaryClip)
                }
                KeyCode.CLIPBOARD_SELECT_ALL -> {
                    editorInfo.isRichInputEditor
                }
                KeyCode.TOGGLE_INCOGNITO_MODE -> when (prefs.suggestion.incognitoMode.get()) {
                    IncognitoMode.FORCE_OFF, IncognitoMode.FORCE_ON -> false
                    IncognitoMode.DYNAMIC_ON_OFF -> !editorInfo.imeOptions.flagNoPersonalizedLearning
                }
                KeyCode.LANGUAGE_SWITCH -> {
                    subtypeManager.subtypes.size > 1
                }
                else -> true
            }
        }

        override fun evaluateVisible(data: KeyData): Boolean {
            return when (data.code) {
                KeyCode.IME_UI_MODE_TEXT,
                KeyCode.IME_UI_MODE_MEDIA -> {
                    val tempUtilityKeyAction = when {
                        prefs.keyboard.utilityKeyEnabled.get() -> prefs.keyboard.utilityKeyAction.get()
                        else -> UtilityKeyAction.DISABLED
                    }
                    when (tempUtilityKeyAction) {
                        UtilityKeyAction.DISABLED,
                        UtilityKeyAction.SWITCH_LANGUAGE,
                        UtilityKeyAction.SWITCH_KEYBOARD_APP -> false
                        UtilityKeyAction.SWITCH_TO_EMOJIS -> true
                        UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> !shouldShowLanguageSwitch()
                    }
                }
                KeyCode.LANGUAGE_SWITCH -> {
                    val tempUtilityKeyAction = when {
                        prefs.keyboard.utilityKeyEnabled.get() -> prefs.keyboard.utilityKeyAction.get()
                        else -> UtilityKeyAction.DISABLED
                    }
                    when (tempUtilityKeyAction) {
                        UtilityKeyAction.DISABLED,
                        UtilityKeyAction.SWITCH_TO_EMOJIS -> false
                        UtilityKeyAction.SWITCH_LANGUAGE,
                        UtilityKeyAction.SWITCH_KEYBOARD_APP -> true
                        UtilityKeyAction.DYNAMIC_SWITCH_LANGUAGE_EMOJIS -> shouldShowLanguageSwitch()
                    }
                }
                else -> true
            }
        }

        override fun isSlot(data: KeyData): Boolean {
            return CurrencySet.isCurrencySlot(data.code)
        }

        override fun slotData(data: KeyData): KeyData? {
            return subtypeManager.getCurrencySet(subtype).getSlot(data.code)
        }

        fun asSmartbarQuickActionsEvaluator(): ComputingEvaluatorImpl {
            return ComputingEvaluatorImpl(
                version = version,
                keyboard = SmartbarQuickActionsKeyboard,
                editorInfo = editorInfo,
                state = state,
                subtype = Subtype.DEFAULT,
            )
        }
    }
}
