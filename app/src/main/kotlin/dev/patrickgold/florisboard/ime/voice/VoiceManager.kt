package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class VoiceManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("voice_history", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history = _history.asStateFlow()

    private val _pendingFiles = MutableStateFlow<List<PendingRecording>>(emptyList())
    val pendingFiles = _pendingFiles.asStateFlow()

    enum class TranscriptionProvider(val wireName: String, val label: String) {
        OPENAI_CLOUD("openai", "OpenAI Cloud"),
        TITAN_LOCAL("titan", "Titan Local"),
    }

    private val _transcriptionProvider = MutableStateFlow(
        prefs.getString("transcription_provider", TranscriptionProvider.OPENAI_CLOUD.wireName)
            ?.let { wireName -> TranscriptionProvider.entries.firstOrNull { it.wireName == wireName } }
            ?: TranscriptionProvider.OPENAI_CLOUD,
    )
    val transcriptionProvider = _transcriptionProvider.asStateFlow()

    data class PendingRecording(
        val filePath: String,
        val timestamp: Long,
        val provider: TranscriptionProvider,
    )

    init {
        loadHistory()
        loadPending()
    }

    // --- Transcription History ---

    fun addTranscription(text: String) {
        if (text.isBlank()) return
        val current = _history.value.toMutableList()
        current.add(0, text)
        if (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        _history.value = current
        saveHistory()
    }

    fun clearHistory() {
        _history.value = emptyList()
        saveHistory()
    }

    fun deleteTranscription(index: Int) {
        val current = _history.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _history.value = current
            saveHistory()
        }
    }

    private fun loadHistory() {
        val json = prefs.getString("transcriptions", null) ?: return
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            _history.value = list
        } catch (e: Exception) {
            // Corrupted data — start fresh
        }
    }

    private fun saveHistory() {
        val arr = JSONArray()
        _history.value.forEach { arr.put(it) }
        prefs.edit().putString("transcriptions", arr.toString()).apply()
    }

    // --- Pending Recordings Queue ---

    fun addPending(file: File, provider: TranscriptionProvider) {
        val current = _pendingFiles.value.toMutableList()
        current.removeAll { it.filePath == file.absolutePath }
        current.add(PendingRecording(file.absolutePath, System.currentTimeMillis(), provider))
        _pendingFiles.value = current
        savePending()
    }

    fun removePending(filePath: String) {
        val current = _pendingFiles.value.toMutableList()
        current.removeAll { it.filePath == filePath }
        _pendingFiles.value = current
        savePending()
    }

    fun getPendingFiles(): List<PendingRecording> = _pendingFiles.value

    fun setTranscriptionProvider(provider: TranscriptionProvider) {
        _transcriptionProvider.value = provider
        prefs.edit().putString("transcription_provider", provider.wireName).apply()
    }

    private fun loadPending() {
        val json = prefs.getString("pending_recordings", null) ?: return
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<PendingRecording>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.getString("path")
                val ts = obj.getLong("timestamp")
                val provider = obj.optString("provider", TranscriptionProvider.OPENAI_CLOUD.wireName)
                    .let { wireName -> TranscriptionProvider.entries.firstOrNull { it.wireName == wireName } }
                    ?: TranscriptionProvider.OPENAI_CLOUD
                // Only keep entries whose files still exist
                if (File(path).exists()) {
                    list.add(PendingRecording(path, ts, provider))
                }
            }
            _pendingFiles.value = list
        } catch (e: Exception) {
            // Corrupted data — start fresh
        }
    }

    private fun savePending() {
        val arr = JSONArray()
        _pendingFiles.value.forEach {
            val obj = JSONObject()
            obj.put("path", it.filePath)
            obj.put("timestamp", it.timestamp)
            obj.put("provider", it.provider.wireName)
            arr.put(obj)
        }
        prefs.edit().putString("pending_recordings", arr.toString()).apply()
    }
}
