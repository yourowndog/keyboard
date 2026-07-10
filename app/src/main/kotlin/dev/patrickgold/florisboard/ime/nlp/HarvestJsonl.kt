/*
 * HarvestJsonl - Structured Usage Harvesting (v3)
 *
 * Machine-readable companion to HarvestManager's markdown log. One JSON object
 * per line, append-only. Designed so every event is a usable training label at
 * write time instead of requiring post-hoc sequence reconstruction:
 *
 *  - Monotonic event ids survive keyboard restarts (recovered from the file tail),
 *    so syncing is "give me everything after id N" and duplicates are impossible.
 *  - Session ids rotate on app change, linking correction events to nearby text.
 *  - REVERTED events carry an "undoes" pointer to the AUTO_APPLIED they undo.
 *  - Candidate lists with confidences are captured at decision time.
 *
 * Output: /sdcard/Documents/usage_harvest.jsonl
 */
package dev.patrickgold.florisboard.ime.nlp

import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

object HarvestJsonl {
    private const val FILENAME = "usage_harvest.jsonl"
    private const val VERSION = 3

    private var file: File? = null
    private val nextId = AtomicLong(1)
    private val tsFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)

    // Single writer thread keeps ids and file order consistent.
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "harvest-jsonl") }

    private var sessionId = randomSessionId()
    private var sessionApp: String? = null

    private data class Applied(val id: Long, val typed: String, val applied: String)
    @Volatile private var lastApplied: Applied? = null

    fun init(dir: File) {
        try {
            val f = File(dir, FILENAME)
            file = f
            nextId.set(recoverLastId(f) + 1)
            android.util.Log.i("HarvestJsonl", "JSONL harvest file: ${f.absolutePath}, next id: ${nextId.get()}")
        } catch (e: Exception) {
            android.util.Log.e("HarvestJsonl", "Failed to init", e)
        }
    }

    /** Resume the id counter from the last "id" field in the file tail. */
    private fun recoverLastId(f: File): Long {
        if (!f.exists() || f.length() == 0L) return 0L
        return try {
            RandomAccessFile(f, "r").use { raf ->
                val len = raf.length()
                val chunk = minOf(len, 65536L)
                raf.seek(len - chunk)
                val bytes = ByteArray(chunk.toInt())
                raf.readFully(bytes)
                val text = String(bytes, Charsets.UTF_8)
                Regex("\"id\":(\\d+)").findAll(text).lastOrNull()?.groupValues?.get(1)?.toLong() ?: 0L
            }
        } catch (e: Exception) {
            android.util.Log.e("HarvestJsonl", "Failed to recover last id", e)
            0L
        }
    }

    private fun randomSessionId(): String {
        return java.util.UUID.randomUUID().toString().substring(0, 8)
    }

    /**
     * Append an event. Returns the event id (usable as an "undoes" pointer),
     * or -1 if logging is unavailable or blocked (password fields).
     * Null field values are omitted from the output.
     */
    fun event(type: String, app: AppContext?, fields: List<Pair<String, Any?>>): Long {
        val f = file ?: return -1L
        if (app?.isPassword == true) return -1L
        val id = nextId.getAndIncrement()
        val ts = tsFormat.format(Date())
        val sess = synchronized(this) {
            val pkg = app?.packageName
            if (pkg != null && pkg != sessionApp) {
                sessionApp = pkg
                sessionId = randomSessionId()
            }
            sessionId
        }
        writer.execute {
            try {
                val sb = StringBuilder(256)
                sb.append("{\"v\":").append(VERSION)
                sb.append(",\"id\":").append(id)
                sb.append(",\"ts\":").append(jsonString(ts))
                sb.append(",\"sess\":").append(jsonString(sess))
                sb.append(",\"type\":").append(jsonString(type))
                if (app != null) {
                    sb.append(",\"app\":").append(jsonString(app.packageName))
                    sb.append(",\"field\":").append(app.fieldId)
                    sb.append(",\"inputType\":").append(jsonString(app.inputVariation))
                    sb.append(",\"flags\":").append(jsonString(app.flags))
                }
                for ((key, value) in fields) {
                    if (value == null) continue
                    sb.append(',').append(jsonString(key)).append(':').append(jsonValue(value))
                }
                sb.append("}\n")
                f.appendText(sb.toString(), Charsets.UTF_8)
            } catch (e: Exception) {
                android.util.Log.e("HarvestJsonl", "Failed to append event $type", e)
            }
        }
        return id
    }

    /** Remember the last auto-applied correction so a revert can reference it. */
    fun rememberApplied(id: Long, typed: String, applied: String) {
        if (id >= 0) lastApplied = Applied(id, typed, applied)
    }

    // Recent NEURAL_SHADOW event ids keyed by lowercased typed word, so outcome
    // events (AUTO_APPLIED, REVERTED, MANUAL_EDIT, INSISTED, USER_PICKED) carry
    // an exact join key back to the shadow prediction they resolve. Bounded LRU:
    // an outcome can arrive several words after its shadow (e.g. a late revert),
    // so keep more than just the last one.
    private val recentShadows = object : LinkedHashMap<String, Long>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 16
    }

    fun rememberShadow(id: Long, typed: String) {
        if (id < 0 || typed.isEmpty()) return
        synchronized(recentShadows) { recentShadows[typed.lowercase(Locale.US)] = id }
    }

    /** Event id of the most recent NEURAL_SHADOW for [typed], or null if none tracked. */
    fun findShadowId(typed: String?): Long? {
        if (typed.isNullOrEmpty()) return null
        return synchronized(recentShadows) { recentShadows[typed.lowercase(Locale.US)] }
    }

    /** Find the event id of the AUTO_APPLIED that a revert of (typed, rejected) undoes. */
    fun findUndoId(typed: String, rejected: String): Long? {
        val last = lastApplied ?: return null
        return if (last.typed.equals(typed, ignoreCase = true) &&
            last.applied.equals(rejected, ignoreCase = true)
        ) last.id else null
    }

    private fun jsonValue(v: Any?): String = when (v) {
        null -> "null"
        is String -> jsonString(v)
        is Boolean -> v.toString()
        is Int -> v.toString()
        is Long -> v.toString()
        is Double -> if (v.isFinite()) String.format(Locale.US, "%.4f", v) else "null"
        is Float -> jsonValue(v.toDouble())
        is Pair<*, *> -> "[" + jsonValue(v.first) + "," + jsonValue(v.second) + "]"
        is List<*> -> v.joinToString(",", "[", "]") { jsonValue(it) }
        else -> jsonString(v.toString())
    }

    private fun jsonString(s: String): String {
        val sb = StringBuilder(s.length + 8)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
