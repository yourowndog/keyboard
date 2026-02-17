package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import dev.patrickgold.florisboard.editorInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceManager(context: Context) {
    private val appContext = context.applicationContext
    
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history = _history.asStateFlow()

    fun addTranscription(text: String) {
        if (text.isBlank()) return
        val current = _history.value.toMutableList()
        current.add(0, text)
        // Keep last 50 transcriptions
        if (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        _history.value = current
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun deleteTranscription(index: Int) {
        val current = _history.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _history.value = current
        }
    }
}
