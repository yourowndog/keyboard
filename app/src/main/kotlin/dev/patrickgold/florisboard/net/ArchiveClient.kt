package dev.patrickgold.florisboard.net

import dev.patrickgold.florisboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Uploads finished captures to the corpus archive on Titan.
 *
 * Unlike the transcription path this talks to Titan directly rather than through the relay.
 * The relay exists to keep a paid API key off the device; there is no secret to protect when
 * the phone talks to its owner's own machine across Tailscale, so routing through Weakling
 * would add a hop and a failure mode without buying anything.
 *
 * The server replies with the SHA-256 of the bytes it actually wrote. The caller must compare
 * that against [sha256] of the local file before deleting anything — that check is the only
 * thing standing between a truncated upload and permanently lost audio.
 */
object ArchiveClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean
        get() = BuildConfig.ARCHIVE_URL.isNotBlank()

    data class Receipt(val sha256: String, val path: String, val id: String, val duplicate: Boolean)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun upload(audio: File, sidecar: JSONObject): Result<Receipt> = withContext(Dispatchers.IO) {
        try {
            if (!isConfigured) return@withContext Result.failure(Exception("Archive endpoint not configured"))
            if (!audio.exists()) return@withContext Result.failure(Exception("Audio file missing: ${audio.name}"))

            val localDigest = sha256(audio)
            val mime = when (audio.extension.lowercase()) {
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                else -> "audio/mp4"
            }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audio.name, audio.asRequestBody(mime.toMediaType()))
                .addFormDataPart("meta", sidecar.toString())
                .build()

            val builder = Request.Builder()
                .url(BuildConfig.ARCHIVE_URL)
                .header("Accept", "application/json")
                .post(body)
            if (BuildConfig.ARCHIVE_TOKEN.isNotBlank()) {
                builder.header("X-Archive-Token", BuildConfig.ARCHIVE_TOKEN)
            }

            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Archive error ${response.code}: ${text.take(200)}")
                    )
                }
                val json = JSONObject(text)
                val remoteDigest = json.optString("sha256")
                if (!remoteDigest.equals(localDigest, ignoreCase = true)) {
                    // Server stored something other than what we sent. Treat as a failure so the
                    // file stays queued and stays on disk.
                    return@withContext Result.failure(
                        Exception("Checksum mismatch: local=$localDigest remote=$remoteDigest")
                    )
                }
                Result.success(
                    Receipt(
                        sha256 = remoteDigest,
                        path = json.optString("path"),
                        id = json.optString("id"),
                        duplicate = json.optBoolean("duplicate", false),
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
