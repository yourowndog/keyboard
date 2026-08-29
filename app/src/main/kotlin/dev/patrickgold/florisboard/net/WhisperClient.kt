package dev.patrickgold.florisboard.net

import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.audio.WavTools
import dev.patrickgold.florisboard.ime.voice.VoiceManager.TranscriptionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object WhisperClient {
    private const val MAX_RATE_LIMIT_RETRIES = 4
    private const val MAX_RETRY_AFTER_SECONDS = 300L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    /**
     * A transcription plus everything the engine said about it.
     *
     * [raw] is the untouched response body. Word timings and the per-segment quality fields
     * (avg_logprob, compression_ratio, no_speech_prob) are the standard hallucination
     * detectors, and they are only obtainable at transcription time — regenerating them later
     * means paying for the audio a second time.
     */
    data class Transcription(
        val text: String,
        val verbatimText: String?,
        val provider: String,
        val model: String,
        val raw: JSONObject,
    )

    // Recordings queued before the switch to lossless capture are still .mp4, and the retry queue
    // will replay them, so both formats have to be sent with the right content type.
    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        else -> "audio/mp4"
    }

    /**
     * Produces the copy that actually gets uploaded.
     *
     * Returns the file to send and whether it is a temporary file the caller should delete.
     * A failure to downsample is not fatal: the original still transcribes fine as long as it
     * is under the size cap, so we fall back rather than lose the transcript.
     */
    private fun prepareUpload(file: File): Pair<File, Boolean> {
        if (file.extension.lowercase() != "wav") return file to false
        val temp = File(file.parentFile, "${file.nameWithoutExtension}.16k.wav")
        return try {
            if (WavTools.downsampleForUpload(file, temp) && temp.length() in 1..file.length()) {
                temp to true
            } else {
                temp.delete()
                file to false
            }
        } catch (_: Exception) {
            runCatching { temp.delete() }
            file to false
        }
    }

    suspend fun transcribe(file: File, provider: TranscriptionProvider): Result<Transcription> = withContext(Dispatchers.IO) {
        val temporaryFiles = mutableListOf<File>()
        try {
            if (BuildConfig.WHISPER_RELAY_URL.isBlank() || BuildConfig.WHISPER_RELAY_TOKEN.isBlank()) {
                return@withContext Result.failure(Exception("Whisper relay is not configured"))
            }

            val (upload, isTemp) = prepareUpload(file)
            if (isTemp) temporaryFiles.add(upload)
            val isWavUpload = upload.extension.equals("wav", ignoreCase = true)
            val maxChunkBytes = WavTools.SAFE_UPLOAD_BYTES
            val uploads = if (upload.length() > maxChunkBytes) {
                if (!isWavUpload) {
                    return@withContext Result.failure(
                        Exception("Recording too long to transcribe (${upload.length() / (1024 * 1024)} MiB)"),
                    )
                }
                WavTools.splitForUpload(upload, maxChunkBytes).also { chunks ->
                    temporaryFiles.addAll(chunks.map { it.file })
                }
            } else {
                listOf(WavTools.UploadChunk(upload, 0L))
            }

            val parts = mutableListOf<Pair<WavTools.UploadChunk, Transcription>>()
            uploads.forEach { chunk ->
                val result = requestTranscription(chunk.file, provider)
                if (result.isFailure) {
                    return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Transcription failed"))
                }
                parts.add(chunk to result.getOrThrow())
            }
            Result.success(combineTranscriptions(parts))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            temporaryFiles.distinct().forEach { runCatching { it.delete() } }
        }
    }

    internal fun retryDelayMillis(retryAfter: String?, retryIndex: Int): Long {
        val fallbackSeconds = 1L shl retryIndex.coerceIn(0, 8)
        val seconds = retryAfter
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?: fallbackSeconds
        return seconds.coerceAtMost(MAX_RETRY_AFTER_SECONDS) * 1_000L
    }

    private suspend fun requestTranscription(file: File, provider: TranscriptionProvider): Result<Transcription> {
        if (file.length() > WavTools.MAX_UPLOAD_BYTES) {
            return Result.failure(Exception("Upload chunk exceeds the 25 MiB limit"))
        }
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mimeTypeFor(file).toMediaType()))
            // verbose_json is the only format that carries timestamps and quality fields,
            // and timestamp_granularities requires it.
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "word")
            .addFormDataPart("timestamp_granularities[]", "segment")
            .addFormDataPart("transcription_provider", provider.wireName)
            .build()
        val request = Request.Builder()
            .url(BuildConfig.WHISPER_RELAY_URL)
            .header("Authorization", "Bearer ${BuildConfig.WHISPER_RELAY_TOKEN}")
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        for (retryIndex in 0..MAX_RATE_LIMIT_RETRIES) {
            var retryDelay: Long? = null
            val outcome = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()?.take(200) ?: ""
                        if (response.code == 429 && retryIndex < MAX_RATE_LIMIT_RETRIES) {
                            retryDelay = retryDelayMillis(response.header("Retry-After"), retryIndex)
                            null
                        } else {
                            Result.failure(Exception("Relay error: ${response.code} $errorBody"))
                        }
                    } else {
                        val body = response.body?.string().orEmpty()
                        val json = runCatching { JSONObject(body) }.getOrNull()
                            ?: return Result.success(
                                Transcription(body, null, provider.wireName, "unknown", JSONObject().put("text", body)),
                            )
                        Result.success(
                            Transcription(
                                text = json.optString("text"),
                                verbatimText = json.optString("verbatim_text").takeIf { it.isNotBlank() },
                                provider = json.optString("provider", provider.wireName),
                                model = json.optString("model", "unknown"),
                                raw = json,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                return Result.failure(e)
            }
            if (outcome != null) return outcome
            // The response is closed by use{} before this suspension begins.
            delay(checkNotNull(retryDelay))
        }
        return Result.failure(Exception("Relay rate limit retry budget exhausted"))
    }

    private fun combineTranscriptions(
        parts: List<Pair<WavTools.UploadChunk, Transcription>>,
    ): Transcription {
        require(parts.isNotEmpty())
        if (parts.size == 1) return parts.first().second

        fun joinText(values: List<String>): String = values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        val cleaned = joinText(parts.map { it.second.text })
        val verbatim = parts.mapNotNull { it.second.verbatimText }.let { values ->
            // A silent chunk legitimately has no verbatim text. Preserve the spoken chunks;
            // only cloud responses with no verbatim extension at all should remain null.
            if (values.isNotEmpty()) joinText(values) else null
        }
        val first = parts.first().second
        val raw = JSONObject().apply {
            put("text", cleaned)
            put("verbatim_text", verbatim ?: JSONObject.NULL)
            put("provider", first.provider)
            put("model", first.model)
            put("chunked", true)
            put("chunks", JSONArray().apply {
                parts.forEachIndexed { index, (chunk, transcription) ->
                    put(JSONObject().apply {
                        put("index", index)
                        put("offset_ms", chunk.offsetMs)
                        put("response", transcription.raw)
                    })
                }
            })
            listOf("words", "verbatim_words", "segments").forEach { key ->
                val merged = mergeTimedArrays(parts, key)
                if (merged.length() > 0) put(key, merged)
            }
        }
        return Transcription(cleaned, verbatim, first.provider, first.model, raw)
    }

    private fun mergeTimedArrays(
        parts: List<Pair<WavTools.UploadChunk, Transcription>>,
        key: String,
    ): JSONArray = JSONArray().apply {
        parts.forEach { (chunk, transcription) ->
            val offsetSeconds = chunk.offsetMs / 1_000.0
            val values = transcription.raw.optJSONArray(key) ?: return@forEach
            for (index in 0 until values.length()) {
                val source = values.optJSONObject(index) ?: continue
                val shifted = JSONObject(source.toString())
                if (shifted.has("start")) shifted.put("start", shifted.optDouble("start") + offsetSeconds)
                if (shifted.has("end")) shifted.put("end", shifted.optDouble("end") + offsetSeconds)
                put(shifted)
            }
        }
    }
}
