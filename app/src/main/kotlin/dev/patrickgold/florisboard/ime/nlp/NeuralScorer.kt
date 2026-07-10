package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

class NeuralScorer private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
) : AutoCloseable {
    data class Candidate(
        val term: String,
        val editDistance: Double,
        val lnFreq: Double,
        val bigramCount: Int,
    )

    data class ScoredCandidate(
        val term: String,
        val probability: Float,
    )

    data class Decision(
        val typed: String,
        val top: ScoredCandidate,
        val typedProbability: Float,
        val margin: Float,
        val shouldFire: Boolean,
        val ranked: List<ScoredCandidate>,
    )

    fun scoreCandidates(
        typed: String,
        prevWord: String?,
        prev2Word: String?,
        candidates: List<Candidate>,
        threshold: Float,
    ): Decision? {
        if (typed.length < MIN_TYPED_LENGTH || candidates.isEmpty()) return null

        val typedLower = typed.lowercase(Locale.US)
        val limited = candidates
            .distinctBy { it.term.lowercase(Locale.US) }
            .take(MAX_CANDIDATES)

        val typedIndex = limited.indexOfFirst { it.term.equals(typedLower, ignoreCase = true) }
        if (typedIndex < 0) {
            Log.w(TAG, "Typed word missing from neural candidate set: $typed")
            return null
        }

        return try {
            val inputs = createInputs(typedLower, prevWord, prev2Word, limited)
            try {
                session.run(inputs).use { result ->
                    val logits = extractLogits(result[0].value, limited.size)
                    val probabilities = softmax(logits)
                    val ranked = limited.indices
                        .map { index -> ScoredCandidate(limited[index].term, probabilities[index]) }
                        .sortedByDescending { it.probability }
                    val top = ranked.first()
                    val typedProbability = probabilities[typedIndex]
                    val margin = top.probability - typedProbability
                    Decision(
                        typed = typedLower,
                        top = top,
                        typedProbability = typedProbability,
                        margin = margin,
                        shouldFire = !top.term.equals(typedLower, ignoreCase = true) && margin > threshold,
                        ranked = ranked,
                    )
                }
            } finally {
                inputs.values.forEach { it.close() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Neural scoring failed", e)
            null
        }
    }

    private fun createInputs(
        typed: String,
        prevWord: String?,
        prev2Word: String?,
        candidates: List<Candidate>,
    ): Map<String, OnnxTensor> {
        val candidateIds = candidates.map { encodeWordPadded(it.term) }.toTypedArray()
        val scalars = candidates.map { candidate ->
            scalarRow(
                typed = typed,
                term = candidate.term.lowercase(Locale.US),
                editDistance = candidate.editDistance,
                lnFreq = candidate.lnFreq,
                bigramCount = candidate.bigramCount,
            )
        }.toTypedArray()
        val mask = FloatArray(candidates.size) { 1.0f }
        val ctxIds = longArrayOf(fnvBucket(prevWord), fnvBucket(prev2Word))

        return mapOf(
            "typed_ids" to OnnxTensor.createTensor(env, arrayOf(encodeWordPadded(typed))),
            "cand_ids" to OnnxTensor.createTensor(env, arrayOf(candidateIds)),
            "scalars" to OnnxTensor.createTensor(env, arrayOf(scalars)),
            "ctx_ids" to OnnxTensor.createTensor(env, arrayOf(ctxIds)),
            "cand_mask" to OnnxTensor.createTensor(env, arrayOf(mask)),
        )
    }

    override fun close() {
        session.close()
    }

    companion object {
        private const val TAG = "NeuralScorer"
        private const val MODEL_ASSET_PATH = "ime/nn/autocorrect_v1.int8.onnx"
        private const val MIN_TYPED_LENGTH = 3
        const val MAX_WORD_IDS = 22
        const val MAX_CANDIDATES = 12

        private const val PAD = 0L
        private const val APOS = 27L
        private const val BOW = 28L
        private const val EOW = 29L
        private const val UNK = 30L

        fun load(context: Context): NeuralScorer? {
            return try {
                val modelBytes = context.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
                val env = OrtEnvironment.getEnvironment()
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(1)
                    setInterOpNumThreads(1)
                }
                val session = env.createSession(modelBytes, options)
                Log.i(TAG, "Loaded $MODEL_ASSET_PATH inputs=${session.inputNames} outputs=${session.outputNames}")
                NeuralScorer(env, session)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $MODEL_ASSET_PATH", e)
                null
            }
        }

        fun encodeWordPadded(word: String): LongArray {
            val ids = LongArray(MAX_WORD_IDS) { PAD }
            ids[0] = BOW
            val normalized = word.lowercase(Locale.US)
            val maxChars = MAX_WORD_IDS - 2
            val charsToEncode = normalized.take(maxChars)
            charsToEncode.forEachIndexed { index, ch ->
                ids[index + 1] = when (ch) {
                    in 'a'..'z' -> (ch.code - 'a'.code + 1).toLong()
                    '\'' -> APOS
                    else -> UNK
                }
            }
            ids[charsToEncode.length + 1] = EOW
            return ids
        }

        fun fnvBucket(word: String?): Long {
            if (word.isNullOrEmpty()) return 0L
            var hash = 2166136261L
            for (byte in word.lowercase(Locale.US).toByteArray(Charsets.UTF_8)) {
                hash = ((hash xor (byte.toInt() and 0xFF).toLong()) * 16777619L) and 0xFFFFFFFFL
            }
            return (hash % 29999L) + 1L
        }

        fun scalarRow(
            typed: String,
            term: String,
            editDistance: Double,
            lnFreq: Double,
            bigramCount: Int,
        ): FloatArray {
            return floatArrayOf(
                (editDistance / 2.0).toFloat(),
                (lnFreq / 16.0).toFloat(),
                (ln(bigramCount + 1.0) / 12.0).toFloat(),
                (typed.length / 20.0).toFloat(),
                if (term == typed) 1.0f else 0.0f,
            )
        }

        private fun extractLogits(value: Any, expectedSize: Int): FloatArray {
            val logits = when (value) {
                is FloatArray -> value
                is Array<*> -> {
                    val first = value.firstOrNull()
                    when (first) {
                        is FloatArray -> first
                        is Array<*> -> first.filterIsInstance<Float>().toFloatArray()
                        else -> throw IllegalStateException("Unsupported logits row: ${first?.javaClass?.name}")
                    }
                }
                else -> throw IllegalStateException("Unsupported logits output: ${value.javaClass.name}")
            }
            if (logits.size < expectedSize) {
                throw IllegalStateException("Logit count ${logits.size} < candidate count $expectedSize")
            }
            return logits.copyOf(expectedSize)
        }

        private fun softmax(logits: FloatArray): FloatArray {
            val max = logits.maxOrNull() ?: 0.0f
            val exps = logits.map { exp((it - max).toDouble()) }
            val sum = exps.sum().takeIf { it > 0.0 } ?: 1.0
            return FloatArray(logits.size) { index -> (exps[index] / sum).toFloat() }
        }
    }
}
