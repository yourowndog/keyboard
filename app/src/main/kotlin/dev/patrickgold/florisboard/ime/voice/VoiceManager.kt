package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.os.Environment
import dev.patrickgold.florisboard.audio.Recorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun extractVerbatimTranscript(sidecar: JSONObject): String? {
    if (!sidecar.isNull("transcript_verbatim")) {
        sidecar.optString("transcript_verbatim").trim().takeIf { it.isNotEmpty() }?.let { return it }
    }
    val chunks = sidecar.optJSONObject("engine")
        ?.optJSONObject("response")
        ?.optJSONArray("chunks")
        ?: return null
    val values = mutableListOf<String>()
    for (index in 0 until chunks.length()) {
        chunks.optJSONObject(index)
            ?.optJSONObject("response")
            ?.optString("verbatim_text")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(values::add)
    }
    return values.joinToString(" ").takeIf { it.isNotBlank() }
}

class VoiceManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("voice_history", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao = VoiceDatabase.new(appContext).voiceTakeDao()
    private val initializeMutex = Mutex()
    private var initialized = false

    val takes = dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _pendingFiles = MutableStateFlow<List<PendingRecording>>(emptyList())
    val pendingFiles = _pendingFiles.asStateFlow()

    enum class TranscriptionProvider(val wireName: String, val label: String) {
        OPENAI_CLOUD("openai", "OpenAI Cloud"),
        TITAN_LOCAL("titan", "Titan Local"),
    }

    private val _transcriptionProvider = MutableStateFlow(
        prefs.getString("transcription_provider", TranscriptionProvider.OPENAI_CLOUD.wireName)
            ?.let { name -> TranscriptionProvider.entries.firstOrNull { it.wireName == name } }
            ?: TranscriptionProvider.OPENAI_CLOUD,
    )
    val transcriptionProvider = _transcriptionProvider.asStateFlow()

    private val _outputMode = MutableStateFlow(
        prefs.getString("output_mode", VoiceOutputMode.CLEANED.name)
            ?.let { name -> VoiceOutputMode.entries.firstOrNull { it.name == name } }
            ?: VoiceOutputMode.CLEANED,
    )
    val outputMode = _outputMode.asStateFlow()

    data class PendingRecording(
        val filePath: String,
        val timestamp: Long,
        val provider: TranscriptionProvider,
    )

    init {
        loadPending()
    }

    fun setTranscriptionProvider(provider: TranscriptionProvider) {
        _transcriptionProvider.value = provider
        prefs.edit().putString("transcription_provider", provider.wireName).apply()
    }

    fun setOutputMode(mode: VoiceOutputMode) {
        _outputMode.value = mode
        prefs.edit().putString("output_mode", mode.name).apply()
    }

    fun recordCaptureStarted(file: File, provider: TranscriptionProvider) = runBlocking(Dispatchers.IO) {
        dao.upsert(baseTake(file, provider).copy(state = VoiceTakeState.RECORDING.name))
    }

    fun recordCaptureSaved(file: File, capture: Recorder.CaptureMetadata?) = runBlocking(Dispatchers.IO) {
        val current = dao.getById(file.nameWithoutExtension) ?: baseTake(file, _transcriptionProvider.value)
        dao.upsert(
            current.copy(
                durationMs = capture?.durationMs ?: current.durationMs,
                state = VoiceTakeState.SAVED.name,
                error = null,
            ),
        )
    }

    suspend fun markTranscribing(file: File, provider: TranscriptionProvider) {
        val current = dao.getById(file.nameWithoutExtension) ?: baseTake(file, provider)
        dao.upsert(current.copy(state = VoiceTakeState.TRANSCRIBING.name, provider = provider.wireName, error = null))
    }

    suspend fun markReady(
        file: File,
        provider: String,
        cleaned: String,
        verbatim: String?,
        raw: String,
        durationMs: Long? = null,
    ) {
        val fallbackProvider = TranscriptionProvider.entries.firstOrNull { it.wireName == provider }
            ?: TranscriptionProvider.OPENAI_CLOUD
        val current = dao.getById(file.nameWithoutExtension) ?: baseTake(file, fallbackProvider)
        dao.upsert(
            current.copy(
                durationMs = durationMs ?: current.durationMs,
                state = VoiceTakeState.READY.name,
                provider = provider,
                cleanedText = cleaned,
                verbatimText = verbatim,
                rawText = raw,
                error = null,
            ),
        )
    }

    suspend fun markFailed(file: File, provider: TranscriptionProvider, error: String) {
        val current = dao.getById(file.nameWithoutExtension) ?: baseTake(file, provider)
        dao.upsert(
            current.copy(
                state = VoiceTakeState.FAILED.name,
                provider = provider.wireName,
                error = error.take(240),
            ),
        )
    }

    /** Imports existing sidecars and queues any recoverable WAV with no sidecar or pending entry. */
    suspend fun initializeDurableStateAndRecover(): List<File> = initializeMutex.withLock {
        if (initialized) return@withLock emptyList()
        val vault = File(Environment.getExternalStorageDirectory(), "Recordings/Whisper_Vault")
        if (!vault.isDirectory) return@withLock emptyList()

        importSidecars(vault)
        migrateLegacyHistory()
        val pendingByPath = getPendingFiles().associateBy { it.filePath }
        pendingByPath.values.forEach { pending ->
            val file = File(pending.filePath)
            if (!file.exists()) {
                return@forEach
            }
            val sidecar = File(file.parentFile, "${file.nameWithoutExtension}.json")
            val hasTranscript = sidecar.takeIf { it.isFile }
                ?.let { runCatching { JSONObject(it.readText()).optBoolean("transcribed", false) }.getOrDefault(false) }
                ?: false
            if (dao.getByAudioPath(file.absolutePath)?.takeState == VoiceTakeState.READY && hasTranscript) {
                removePending(file.absolutePath)
                return@forEach
            }
            // Legacy cancelled takes already have a corpus sidecar and were handled by Titan's
            // archive path. Corpus backfill is a Titan job; do not replay those uploads from the
            // phone or leave them as misleading Saved entries in the retrieval Inbox.
            if (sidecar.isFile && !hasTranscript) {
                removePending(file.absolutePath)
                dao.deleteById(file.nameWithoutExtension)
                return@forEach
            }
            val inspection = runCatching { VoiceWavRecovery.inspectAndRepair(file) }.getOrNull()
            dao.upsert(
                (dao.getById(file.nameWithoutExtension) ?: baseTake(file, pending.provider)).copy(
                    durationMs = inspection?.durationMs ?: 0L,
                    state = VoiceTakeState.SAVED.name,
                    provider = pending.provider.wireName,
                ),
            )
        }

        val recovered = mutableListOf<File>()
        val captureName = Regex("whisper_\\d+\\.wav", RegexOption.IGNORE_CASE)
        vault.listFiles { file -> file.isFile && captureName.matches(file.name) }
            ?.sortedBy { it.lastModified() }
            ?.forEach { audio ->
                val sidecar = File(audio.parentFile, "${audio.nameWithoutExtension}.json")
                if (sidecar.exists() || pendingByPath.containsKey(audio.absolutePath)) return@forEach
                val inspection = runCatching { VoiceWavRecovery.inspectAndRepair(audio) }.getOrNull()
                    ?: return@forEach
                val provider = TranscriptionProvider.TITAN_LOCAL
                dao.upsert(
                    baseTake(audio, provider).copy(
                        durationMs = inspection.durationMs,
                        state = VoiceTakeState.SAVED.name,
                    ),
                )
                addPending(audio, provider)
                recovered.add(audio)
            }
        initialized = true
        recovered
    }

    private suspend fun importSidecars(vault: File) {
        vault.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            ?.forEach { sidecar ->
                val json = runCatching { JSONObject(sidecar.readText()) }.getOrNull() ?: return@forEach
                if (!json.optBoolean("transcribed", false)) return@forEach
                val audioName = json.optString("audio", "${sidecar.nameWithoutExtension}.wav")
                val audio = File(vault, audioName)
                val capture = json.optJSONObject("capture")
                val engine = json.optJSONObject("engine")
                val provider = engine?.optString("provider").orEmpty().ifBlank { "unknown" }
                val verbatim = extractVerbatimTranscript(json)
                val durationMs = capture?.optLong("duration_ms")?.takeIf { it > 0L }
                    ?: audio.takeIf { it.isFile && it.extension.equals("wav", ignoreCase = true) }
                        ?.let { runCatching { VoiceWavRecovery.inspectAndRepair(it).durationMs }.getOrNull() }
                    ?: 0L
                dao.upsert(
                    VoiceTake(
                        id = sidecar.nameWithoutExtension,
                        audioPath = audio.absolutePath.takeIf { audio.exists() },
                        capturedAtMs = json.optLong("captured_epoch_ms", capturedAt(audio)),
                        durationMs = durationMs,
                        state = VoiceTakeState.READY.name,
                        provider = provider,
                        cleanedText = json.optString("transcript_display"),
                        verbatimText = verbatim,
                        rawText = json.optString("transcript_raw"),
                        error = null,
                    ),
                )
            }
    }

    private suspend fun migrateLegacyHistory() {
        if (prefs.getBoolean("room_history_migrated", false)) return
        val legacy = runCatching { JSONArray(prefs.getString("transcriptions", "[]")) }.getOrNull()
            ?: JSONArray()
        val now = System.currentTimeMillis()
        for (i in 0 until legacy.length()) {
            val text = legacy.optString(i)
            if (text.isBlank() || dao.findByCleanedText(text) != null) continue
            dao.upsert(
                VoiceTake(
                    id = "legacy_${text.hashCode()}_$i",
                    audioPath = null,
                    capturedAtMs = now - i,
                    durationMs = 0L,
                    state = VoiceTakeState.READY.name,
                    provider = "legacy",
                    cleanedText = text,
                    verbatimText = null,
                    rawText = text,
                    error = null,
                ),
            )
        }
        prefs.edit().putBoolean("room_history_migrated", true).apply()
    }

    @Synchronized
    fun addPending(file: File, provider: TranscriptionProvider) {
        val current = _pendingFiles.value.toMutableList()
        val existing = current.firstOrNull { it.filePath == file.absolutePath }
        current.removeAll { it.filePath == file.absolutePath }
        current.add(PendingRecording(file.absolutePath, existing?.timestamp ?: capturedAt(file), provider))
        _pendingFiles.value = current
        savePending()
    }

    @Synchronized
    fun removePending(filePath: String) {
        _pendingFiles.value = _pendingFiles.value.filterNot { it.filePath == filePath }
        savePending()
    }

    fun getPendingFiles(): List<PendingRecording> = _pendingFiles.value

    private fun loadPending() {
        val raw = prefs.getString("pending_recordings", null) ?: return
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val list = mutableListOf<PendingRecording>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val path = obj.optString("path")
            if (!File(path).exists()) continue
            val provider = obj.optString("provider", TranscriptionProvider.OPENAI_CLOUD.wireName)
                .let { name -> TranscriptionProvider.entries.firstOrNull { it.wireName == name } }
                ?: TranscriptionProvider.OPENAI_CLOUD
            list.add(PendingRecording(path, obj.optLong("timestamp"), provider))
        }
        _pendingFiles.value = list
    }

    private fun savePending() {
        val arr = JSONArray()
        _pendingFiles.value.forEach { pending ->
            arr.put(JSONObject().apply {
                put("path", pending.filePath)
                put("timestamp", pending.timestamp)
                put("provider", pending.provider.wireName)
            })
        }
        prefs.edit().putString("pending_recordings", arr.toString()).apply()
    }

    private fun baseTake(file: File, provider: TranscriptionProvider) = VoiceTake(
        id = file.nameWithoutExtension,
        audioPath = file.absolutePath,
        capturedAtMs = capturedAt(file),
        durationMs = 0L,
        state = VoiceTakeState.SAVED.name,
        provider = provider.wireName,
        cleanedText = "",
        verbatimText = null,
        rawText = "",
        error = null,
    )

    private fun capturedAt(file: File): Long = file.nameWithoutExtension
        .substringAfter("whisper_", "")
        .toLongOrNull()
        ?: file.lastModified().takeIf { it > 0L }
        ?: System.currentTimeMillis()
}
