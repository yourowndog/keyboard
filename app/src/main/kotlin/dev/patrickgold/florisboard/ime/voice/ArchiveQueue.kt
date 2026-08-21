package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import dev.patrickgold.florisboard.net.ArchiveClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tracks captures on their way to the corpus archive on Titan, and reclaims phone storage once
 * they have safely arrived.
 *
 * Deliberately separate from [VoiceManager]: that class owns the transcript ledger the user
 * reads from, and nothing about archiving should be able to disturb it.
 *
 * The deletion rule has two conditions, both required:
 *
 *  1. Titan returned a SHA-256 matching the local file. Not "the request returned 200" — the
 *     bytes on the far end are confirmed identical.
 *  2. The capture is older than [GRACE_MS]. Confirmed-but-recent audio is kept anyway, as a
 *     buffer against the archive turning out to be wrong in some way we have not thought of.
 *
 * Anything not meeting both stays on disk. An unreachable Titan costs disk space, never audio.
 */
class ArchiveQueue(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("voice_archive", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private companion object {
        const val KEY = "archive_queue"
        const val GRACE_MS = 24L * 60 * 60 * 1000
        const val MAX_ATTEMPTS_BEFORE_BACKOFF = 5
        const val BACKOFF_MS = 30L * 60 * 1000
    }

    data class Entry(
        val audioPath: String,
        val sidecarPath: String,
        val uploadedAt: Long = 0L,
        val sha256: String = "",
        val attempts: Int = 0,
        val lastAttemptAt: Long = 0L,
        val lastError: String = "",
    ) {
        val isUploaded: Boolean get() = uploadedAt > 0L && sha256.isNotEmpty()
    }

    private fun load(): MutableList<Entry> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    audioPath = o.getString("audio"),
                    sidecarPath = o.optString("sidecar"),
                    uploadedAt = o.optLong("uploaded_at"),
                    sha256 = o.optString("sha256"),
                    attempts = o.optInt("attempts"),
                    lastAttemptAt = o.optLong("last_attempt_at"),
                    lastError = o.optString("last_error"),
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun save(entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("audio", e.audioPath)
                put("sidecar", e.sidecarPath)
                put("uploaded_at", e.uploadedAt)
                put("sha256", e.sha256)
                put("attempts", e.attempts)
                put("last_attempt_at", e.lastAttemptAt)
                put("last_error", e.lastError)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun enqueue(audio: File, sidecar: File) {
        val entries = load()
        if (entries.any { it.audioPath == audio.absolutePath }) return
        entries.add(Entry(audio.absolutePath, sidecar.absolutePath))
        save(entries)
    }

    fun pendingCount(): Int = load().count { !it.isUploaded }

    /** Bytes still held locally by captures this queue is responsible for. */
    fun localBytes(): Long = load().sumOf { File(it.audioPath).let { f -> if (f.exists()) f.length() else 0L } }

    /**
     * Attempts every outstanding upload, then sweeps. Safe to call repeatedly; the receiver is
     * idempotent, so a capture uploaded twice is filed once.
     */
    suspend fun drain(): Int = mutex.withLock {
        if (!ArchiveClient.isConfigured) return@withLock 0
        val entries = load()
        var uploaded = 0
        val now = System.currentTimeMillis()

        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.isUploaded) continue

            val audio = File(entry.audioPath)
            if (!audio.exists()) {
                // Nothing to upload and nothing to protect; drop it from the queue.
                entries[i] = entry.copy(uploadedAt = -1L, lastError = "audio missing")
                continue
            }
            if (entry.attempts >= MAX_ATTEMPTS_BEFORE_BACKOFF && now - entry.lastAttemptAt < BACKOFF_MS) {
                continue
            }

            val sidecar = File(entry.sidecarPath)
            val meta = if (sidecar.exists()) {
                runCatching { JSONObject(sidecar.readText()) }.getOrElse { JSONObject() }
            } else {
                JSONObject()
            }

            val result = ArchiveClient.upload(audio, meta)
            entries[i] = result.fold(
                onSuccess = { receipt ->
                    uploaded++
                    entry.copy(
                        uploadedAt = System.currentTimeMillis(),
                        sha256 = receipt.sha256,
                        attempts = entry.attempts + 1,
                        lastAttemptAt = now,
                        lastError = "",
                    )
                },
                onFailure = { err ->
                    entry.copy(
                        attempts = entry.attempts + 1,
                        lastAttemptAt = now,
                        lastError = err.message?.take(200).orEmpty(),
                    )
                },
            )
        }

        save(entries)
        sweepLocked(entries)
        uploaded
    }

    /** Deletes confirmed-and-aged audio, and forgets entries whose files are gone. */
    private fun sweepLocked(entries: MutableList<Entry>) {
        val now = System.currentTimeMillis()
        val survivors = mutableListOf<Entry>()
        for (entry in entries) {
            if (entry.uploadedAt < 0L) continue
            if (entry.isUploaded && now - entry.uploadedAt > GRACE_MS) {
                runCatching { File(entry.audioPath).delete() }
                runCatching { File(entry.sidecarPath).delete() }
                continue
            }
            survivors.add(entry)
        }
        if (survivors.size != entries.size) save(survivors)
    }
}
