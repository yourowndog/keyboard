/*
 * HarvestShipper - keylog relay from the phone to the archive on Titan.
 *
 * The harvest JSONL file is append-only, so shipping it reduces to one number:
 * how many bytes of it Titan already holds. This uploads the bytes after that
 * cursor, in gzipped chunks that end on a line boundary, and advances the
 * cursor only when Titan's receipt carries the same SHA-256 as the bytes sent.
 *
 * Nothing on the phone is deleted or rewritten: the local file stays the
 * source of truth, and an unreachable Titan only means the cursor waits.
 *
 * Server contract: tools/keylog_archive/receiver.py. Pipeline overview:
 * docs/autocorrect/keylog-pipeline.md.
 */
package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.content.SharedPreferences
import dev.patrickgold.florisboard.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

object HarvestShipper {
    private const val TAG = "HarvestShipper"
    private const val PREFS = "keylog_shipper"

    /** Raw bytes per request. ~2 MiB of JSONL gzips to roughly 150 KB. */
    internal const val CHUNK_BYTES = 2 * 1024 * 1024
    /** Longest single line we will send; a slot is capped at 512 keys, far below this. */
    private const val MAX_LINE_BYTES = 8 * 1024 * 1024
    /** Raw bytes per run, so a large backlog drains over several keyboard closes. */
    private const val MAX_BYTES_PER_RUN = 48L * 1024 * 1024
    private const val MIN_INTERVAL_MS = 10L * 60 * 1000
    private const val FAILURE_BACKOFF_MS = 30L * 60 * 1000
    internal const val ANCHOR_BYTES = 4096

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var prefs: SharedPreferences? = null
    private var file: File? = null

    val isConfigured: Boolean
        get() = BuildConfig.KEYLOG_ARCHIVE_URL.isNotBlank()

    fun init(context: Context, harvestFile: File) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        file = harvestFile
        poke()
    }

    /**
     * Ask for a sync. Cheap and safe to call often (every time the keyboard is
     * dismissed): it runs at most every [MIN_INTERVAL_MS], backs off after a
     * failure, and never overlaps itself.
     */
    fun poke() {
        val p = prefs ?: return
        if (!isConfigured || job?.isActive == true) return
        val now = System.currentTimeMillis()
        val lastRun = p.getLong("last_run_at", 0L)
        val failed = p.getString("last_error", "").orEmpty().isNotEmpty()
        val wait = if (failed) FAILURE_BACKOFF_MS else MIN_INTERVAL_MS
        if (now - lastRun < wait && now >= lastRun) return
        job = scope.launch { runCatching { sync() }.onFailure { recordError(it.message ?: it.javaClass.simpleName) } }
    }

    private fun recordError(msg: String) {
        android.util.Log.w(TAG, "keylog sync failed: $msg")
        prefs?.edit()?.putString("last_error", msg.take(300))?.putLong("last_error_at", System.currentTimeMillis())?.apply()
    }

    private fun sync() {
        val p = prefs ?: return
        val f = file ?: return
        p.edit().putLong("last_run_at", System.currentTimeMillis()).apply()
        if (!f.exists() || f.length() == 0L) return

        RandomAccessFile(f, "r").use { raf ->
            val head = readFirstLine(raf) ?: return
            val stream = streamIdFor(head)

            var offset = if (p.getString("stream", null) == stream) p.getLong("offset", -1L) else -1L
            if (offset < 0) {
                // Fresh install or a new harvest file: ask Titan where this stream stands
                // rather than re-sending hundreds of megabytes.
                offset = fetchCommittedOffset(stream, raf) ?: return
                p.edit().putString("stream", stream).putLong("offset", offset).apply()
            }

            var sent = 0L
            var resyncs = 0
            while (sent < MAX_BYTES_PER_RUN) {
                val end = raf.length()
                if (offset >= end) break
                val data = readChunk(raf, offset, end) ?: break
                val anchor = if (offset > 0) sha256Hex(readRange(raf, maxOf(0L, offset - ANCHOR_BYTES), offset)) else null
                val result = postChunk(stream, offset, data, anchor)
                when (result) {
                    is PostResult.Stored -> {
                        offset = result.committedOffset
                        sent += data.size
                        p.edit().putLong("offset", offset).putLong("last_success_at", System.currentTimeMillis())
                            .putString("last_error", "").putLong("shipped_bytes", p.getLong("shipped_bytes", 0L) + data.size)
                            .apply()
                    }
                    is PostResult.Resync -> {
                        if (++resyncs > 3) { recordError("too many resyncs at $offset"); return }
                        offset = result.committedOffset
                        p.edit().putLong("offset", offset).apply()
                    }
                    is PostResult.Failed -> { recordError(result.message); return }
                }
            }
            android.util.Log.i(TAG, "keylog sync: stream=$stream offset=$offset sent=$sent")
        }
    }

    private sealed class PostResult {
        data class Stored(val committedOffset: Long) : PostResult()
        data class Resync(val committedOffset: Long) : PostResult()
        data class Failed(val message: String) : PostResult()
    }

    private fun fetchCommittedOffset(stream: String, raf: RandomAccessFile): Long? {
        val req = Request.Builder().url("${BuildConfig.KEYLOG_ARCHIVE_URL}/streams/$stream").withToken().get().build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) { recordError("stream state ${resp.code}: ${text.take(120)}"); return null }
            val json = JSONObject(text)
            val committed = json.optLong("committed_offset", 0L)
            if (committed > raf.length()) { recordError("Titan holds more of this stream than the phone file"); return null }
            val serverAnchor = json.optString("tail_anchor_sha256")
            if (committed > 0 && serverAnchor.isNotEmpty()) {
                val local = sha256Hex(readRange(raf, maxOf(0L, committed - ANCHOR_BYTES), committed))
                if (!local.equals(serverAnchor, ignoreCase = true)) {
                    recordError("harvest file diverges from Titan's copy at $committed"); return null
                }
            }
            return committed
        }
    }

    private fun postChunk(stream: String, offset: Long, data: ByteArray, anchor: String?): PostResult {
        val digest = sha256Hex(data)
        val builder = Request.Builder()
            .url("${BuildConfig.KEYLOG_ARCHIVE_URL}/chunk")
            .withToken()
            .header("X-Keylog-Stream", stream)
            .header("X-Keylog-Offset", offset.toString())
            .header("X-Keylog-Sha256", digest)
            .header("X-Keylog-Tz", TimeZone.getDefault().id)
            .post(gzip(data).toRequestBody("application/gzip".toMediaType()))
        if (anchor != null) builder.header("X-Keylog-Anchor", anchor)
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                when {
                    resp.isSuccessful && json != null -> {
                        val remote = json.optString("sha256")
                        if (!remote.equals(digest, ignoreCase = true)) {
                            PostResult.Failed("checksum mismatch: local=$digest remote=$remote")
                        } else {
                            PostResult.Stored(json.optLong("committed_offset", offset + data.size))
                        }
                    }
                    resp.code == 409 && json != null && json.has("committed_offset") &&
                        !json.optString("error").contains("diverged") ->
                        PostResult.Resync(json.getLong("committed_offset"))
                    else -> PostResult.Failed("keylog archive ${resp.code}: ${text.take(160)}")
                }
            }
        } catch (e: Exception) {
            PostResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun Request.Builder.withToken(): Request.Builder = apply {
        header("Accept", "application/json")
        if (BuildConfig.ARCHIVE_TOKEN.isNotBlank()) header("X-Archive-Token", BuildConfig.ARCHIVE_TOKEN)
    }

    /** Bytes [offset, end) cut back to the last newline, at most [CHUNK_BYTES] unless one line is longer. */
    private fun readChunk(raf: RandomAccessFile, offset: Long, end: Long): ByteArray? {
        var want = CHUNK_BYTES
        while (true) {
            val buf = readRange(raf, offset, minOf(end, offset + want))
            val cut = completeLinesLength(buf)
            if (cut > 0) return buf.copyOf(cut)
            // No newline yet: either the writer is mid-line (wait for next run)
            // or a single line is longer than the window.
            if (offset + want >= end || want >= MAX_LINE_BYTES) return null
            want *= 2
        }
    }

    private fun readRange(raf: RandomAccessFile, from: Long, to: Long): ByteArray {
        val buf = ByteArray((to - from).toInt())
        raf.seek(from)
        raf.readFully(buf)
        return buf
    }

    private fun readFirstLine(raf: RandomAccessFile): ByteArray? {
        val buf = readRange(raf, 0, minOf(raf.length(), 1024L * 1024))
        val nl = buf.indexOf('\n'.code.toByte())
        return if (nl < 0) null else buf.copyOf(nl + 1)
    }

    /** Length of the prefix of [buf] that ends on a newline, or 0 when there is none. */
    internal fun completeLinesLength(buf: ByteArray): Int {
        for (i in buf.indices.reversed()) if (buf[i] == '\n'.code.toByte()) return i + 1
        return 0
    }

    /** Must match keylog_store.stream_id_for_head: sha256(first line incl. newline)[:16]. */
    internal fun streamIdFor(firstLine: ByteArray): String = sha256Hex(firstLine).substring(0, 16)

    internal fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size / 8 + 64)
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }
}
