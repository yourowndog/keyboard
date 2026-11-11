/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.gestures

import android.content.Context
import android.util.Log
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.keyboard.geometry.SpatialCoordinateFrame
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.futo.ml.inference.SwipeDecoder
import org.futo.ml.inference.VocabTrie
import java.io.File

/**
 * Glide typing backed by FUTO's pretrained swipe models, driven through FUTO's own
 * `swipe-library` engine rather than through a reimplementation of it.
 *
 * The engine owns the whole recognition pipeline: time-uniform resampling of the touch path, the
 * `honorable_sturgeon` encoder, the paired `magic_macaw` decoder, and the lexicon-constrained CTC
 * beam search, with the tuned scoring constants for whichever model combination is loaded compiled
 * into the native library. That last part is the reason to use the engine rather than driving the
 * raw `.pte` files directly: the scoring weights are a property of the *combination* of models
 * loaded, and choosing them by hand is guesswork.
 *
 * The encoder is layout-agnostic. It takes the keyboard's key centres as a runtime input, so
 * OmniBoard's live geometry is handed over per-layout instead of being baked into the weights.
 * Both the trajectory and the key centres must be in the same normalised 0..1 space, measured over
 * the letter block alone -- FUTO's reference QWERTY puts the three letter rows at
 * y = 0.167 / 0.500 / 0.833, which is what normalising over the letter block reproduces.
 * Normalising over the whole keyboard instead would squash every letter into the top of the range
 * and hand the encoder a layout it has never seen.
 *
 * Models: https://huggingface.co/futo-org/futo-swipe (FUTO Source First License 1.0)
 * Engine: https://gitlab.futo.org/keyboard/swipe-library
 */
class FutoGlideTypingClassifier(private val context: Context) : GlideTypingClassifier {
    companion object {
        private const val TAG = "FUTO_SWIPE"

        /** Asset subtree copied into `filesDir`; the engine takes file paths, not asset handles. */
        private const val ASSET_DIR = "futo"

        /** Bump to force re-extraction after the shipped models or vocabulary change. */
        private const val ASSET_REVISION = 1

        private const val ENCODER = "honorable_sturgeon/model_fp32.pte"
        private const val DECODER = "magic_macaw/model_fp32.pte"
        private const val VOCAB = "en.combined"

        /**
         * The context LM (`hungry_jellyfish`) is shipped but not loaded.
         *
         * It contributes P(word | preceding words), which is a real gain, but its vocabulary is
         * 32,768 words against our 161k lexicon, and loading it switches the engine to a scoring
         * profile that weights the LM term at alpha=0.64. Words outside its vocabulary -- the long
         * tail, which is most of our dictionary -- would then be competing against a heavily
         * weighted term that cannot score them fairly. Turning this on should come with feeding
         * real preceding-word context and checking what it does to rare words, not as a flag flip.
         */
        private const val USE_CONTEXT_LM = false

        private const val BEAM_WIDTH = 100
        private const val TOP_K = 8

        /** Alphabet order for the layout arrays; must match the built-in trie's alphabet. */
        private const val LETTERS = "abcdefghijklmnopqrstuvwxyz"

        /**
         * Maximum number of gesture points accepted for recognition. Longer traces are trimmed
         * to this limit before entering the native beam search, preventing heap exhaustion in
         * the allocator (b/1790392060). Normal gestures rarely exceed 200 points at typical
         * touch-sampling rates.
         */
        internal const val MAX_RECOGNITION_POINTS = 2048

        /**
         * Validates that normalized gesture arrays contain only finite values.
         * A NaN or Infinity in any coordinate would propagate through the encoder model
         * and cause the beam search hypothesis vector to grow uncontrollably, exhausting
         * the native allocator (b/1790392060).
         *
         * @return true when every element across all three arrays is finite (not NaN or infinite)
         *         and the arrays have the same length.
         */
        internal fun isValidGestureData(x: FloatArray, y: FloatArray, t: FloatArray): Boolean {
            if (x.size != y.size || x.size != t.size) return false
            for (i in x.indices) {
                if (!x[i].isFinite() || !y[i].isFinite() || !t[i].isFinite()) return false
            }
            return true
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lock = Any()

    private var decoder: SwipeDecoder? = null
    private var vocabTrie: VocabTrie? = null
    private var loadFailed = false
    private var loading = false
    private var layoutGeneration = 0L

    private var keyX: FloatArray? = null
    private var keyY: FloatArray? = null
    private var layoutApplied = false
    private var applyingLayout = false

    private val ptsX = ArrayList<Float>(256)
    private val ptsY = ArrayList<Float>(256)
    private val ptsT = ArrayList<Long>(256)

    private var coordinateFrame: SpatialCoordinateFrame? = null

    val ready: Boolean
        get() = synchronized(lock) { decoder != null && layoutApplied }

    // ------------------------------------------------------------------- setup

    /**
     * Copy the model and vocabulary assets into `filesDir`.
     *
     * The engine reads plain files, and expects each model's `metadata.json` to sit beside its
     * `.pte` -- that metadata is how it identifies which models are loaded, and therefore which
     * tuned scoring profile applies -- so the asset subtree is copied with its structure intact.
     */
    private fun extractAssets(): File {
        val root = File(context.filesDir, ASSET_DIR)
        val stamp = File(root, ".revision")
        if (stamp.isFile && stamp.readText().trim() == ASSET_REVISION.toString()) return root

        root.deleteRecursively()
        root.mkdirs()
        copyAssetTree(ASSET_DIR, root)
        stamp.writeText(ASSET_REVISION.toString())
        return root
    }

    private fun copyAssetTree(assetPath: String, dst: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            dst.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                dst.outputStream().use { input.copyTo(it) }
            }
            return
        }
        dst.mkdirs()
        for (child in children) {
            copyAssetTree("$assetPath/$child", File(dst, child))
        }
    }

    /**
     * Build the engine off the main thread. First run copies ~14 MB of assets and parses a
     * 161k-word vocabulary into a trie, so callers simply see [ready] flip once it is up.
     */
    private fun ensureDecoderAsync() {
        val capturedGeneration: Long
        synchronized(lock) {
            if (decoder != null || loadFailed || loading) return
            loading = true
            capturedGeneration = layoutGeneration
        }
        scope.launch {
            try {
                val t0 = System.currentTimeMillis()
                val root = extractAssets()
                val tAssets = System.currentTimeMillis()

                val trie = VocabTrie(File(root, VOCAB).absolutePath)
                val tVocab = System.currentTimeMillis()

                val lmDir = File(root, "hungry_jellyfish")
                val engine = SwipeDecoder(
                    encoderPath = File(root, ENCODER).absolutePath,
                    decoderPath = File(root, DECODER).absolutePath,
                    beamWidth = BEAM_WIDTH,
                    topK = TOP_K,
                    lmModelPath = if (USE_CONTEXT_LM) File(lmDir, "context_lm.pte").absolutePath else null,
                    lmVocabPath = if (USE_CONTEXT_LM) File(lmDir, "vocab.txt").absolutePath else null,
                )
                val tEngine = System.currentTimeMillis()

                synchronized(lock) {
                    vocabTrie = trie
                    decoder = engine
                    loading = false
                }
                Log.i(
                    TAG,
                    "engine ready: assets ${tAssets - t0} ms, vocab ${tVocab - tAssets} ms, " +
                        "models ${tEngine - tVocab} ms, decoder=${engine.hasDecoder()} lm=${engine.hasLm()}",
                )
                applyLayout(capturedGeneration)
            } catch (t: Throwable) {
                Log.e(TAG, "engine load FAILED - glide typing disabled", t)
                synchronized(lock) {
                    loadFailed = true
                    loading = false
                }
            }
        }
    }

    /**
     * Hand the engine the current key geometry and the vocabulary in one call.
     *
     * The trie goes across as a borrowed native pointer, so [vocabTrie] has to outlive every
     * recognition that uses it; it is released only in [close], after the decoder.
     */
    private fun applyLayout(loadGeneration: Long = layoutGeneration) {
        // Guard: no concurrent or superseded calls.
        synchronized(lock) {
            if (layoutGeneration != loadGeneration) return
            if (applyingLayout) return
            applyingLayout = true
        }
        try {
            val ok: Boolean
            synchronized(lock) {
                val trie = vocabTrie
                val eng = decoder
                val cx = keyX
                val cy = keyY
                if (trie == null || eng == null || cx == null || cy == null) {
                    applyingLayout = false
                    return
                }
                if (layoutGeneration != loadGeneration) {
                    applyingLayout = false
                    return
                }
                ok = eng.setMode(
                    letters = LETTERS,
                    cx = cx,
                    cy = cy,
                    tries = longArrayOf(trie.itriePtr),
                )
                layoutApplied = ok
            }
            Log.d(TAG, "setMode(layout + vocabulary) -> $ok")
        } finally {
            synchronized(lock) { applyingLayout = false }
        }
    }

    // ---------------------------------------------------------------- geometry

    override fun setLayout(keyViews: List<TextKey>, subtype: Subtype) {
        val frame = SpatialCoordinateFrame.from(keyViews)
        if (frame == null) {
            Log.d(TAG, "layout ignored: a complete 26-letter spatial frame is unavailable")
            return
        }

        val cx = FloatArray(LETTERS.length)
        val cy = FloatArray(LETTERS.length)
        for ((i, ch) in LETTERS.withIndex()) {
            val center = frame.normalizedCenter(ch) ?: return
            cx[i] = center.x
            cy[i] = center.y
        }

        synchronized(lock) {
            // Update the physical frame even when the model-space centres are unchanged. Uniform
            // row scaling keeps centres at 0.167/0.500/0.833 but changes how raw points reach them.
            coordinateFrame = frame
            val unchanged = keyX?.contentEquals(cx) == true && keyY?.contentEquals(cy) == true
            if (unchanged && layoutApplied) return
            layoutGeneration++
            keyX = cx
            keyY = cy
            layoutApplied = false
        }
        Log.d(
            TAG,
            "layout set: ${LETTERS.length} keys, alpha ${frame.alphaW.toInt()}x" +
                "${frame.alphaH.toInt()}px aspect=${"%.3f".format(frame.aspect)}",
        )

        ensureDecoderAsync()
        applyLayout()
    }

    /**
     * No-op: the swipe vocabulary is the shipped `en.combined` asset, parsed once into the
     * engine's own trie, rather than the runtime dictionary map. It is generated from the same
     * unified dictionary by `tools/build_futo_swipe_vocab.py`, but keeps the surface forms, so
     * apostrophes and capitalisation survive into the suggestions instead of being flattened.
     */
    override fun setWordData(subtype: Subtype) = Unit

    // ----------------------------------------------------------------- gesture

    override fun addGesturePoint(position: GlideTypingGesture.Detector.Position) {
        synchronized(lock) {
            ptsX.add(position.x); ptsY.add(position.y); ptsT.add(position.t)
        }
    }

    override fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData) {
        for (p in pointerData.positions) addGesturePoint(p)
    }

    override fun clear() {
        synchronized(lock) {
            ptsX.clear(); ptsY.clear(); ptsT.clear()
        }
    }

    override fun getSuggestions(maxSuggestionCount: Int, gestureCompleted: Boolean): List<String> {
        return synchronized(lock) {
            val engine = decoder ?: return@synchronized emptyList()
            val frame = coordinateFrame ?: return@synchronized emptyList()
            if (!layoutApplied) return@synchronized emptyList()

            val n = ptsX.size
            if (n < 2) return@synchronized emptyList()

            // Cap gesture length: extremely long traces (thousands of points) can cause
            // the native beam search to exhaust its allocator. Most normal gestures are
            // 20-200 points; MAX_RECOGNITION_POINTS covers an entire multi-second drag at
            // high polling rate.
            val m = n.coerceAtMost(MAX_RECOGNITION_POINTS)

            // Normalise into the same 0..1 letter-block space as the key centres, and pass the
            // real event times: the models were trained on time-resampled paths, so dwelling on
            // a key is what separates a deliberate or doubled letter from one merely passed over.
            val x = FloatArray(m)
            val y = FloatArray(m)
            val t = FloatArray(m)
            val t0 = ptsT[0]
            for (i in 0 until m) {
                val normalized = frame.normalize(ptsX[i], ptsY[i])
                x[i] = normalized.x.coerceIn(0f, 1f)
                y[i] = normalized.y.coerceIn(0f, 1f)
                t[i] = (ptsT[i] - t0).toFloat()
            }

            // Reject input with non-finite values: a NaN or Infinity in the arrays would
            // propagate through the encoder model and cause the beam search hypothesis vector
            // to grow uncontrollably, exhausting the native allocator (b/1790392060).
            if (!isValidGestureData(x, y, t)) {
                Log.w(TAG, "recognise: non-finite value found, aborting gesture")
                return@synchronized emptyList()
            }

            try {
                val started = System.currentTimeMillis()
                val results = engine.recognize(
                    x = x,
                    y = y,
                    t = t,
                    topK = maxSuggestionCount.coerceIn(1, TOP_K),
                    beamWidth = BEAM_WIDTH,
                )
                Log.d(
                    TAG,
                    "recognise: $n pts in ${System.currentTimeMillis() - started} ms -> " +
                        results.take(4).joinToString { "${it.word}(${"%.2f".format(it.score)})" },
                )
                results.map { it.word }
            } catch (t: Throwable) {
                Log.e(TAG, "recognise failed", t)
                emptyList()
            }
        }
    }

    /** Release native resources. The trie must outlive the decoder that borrows it. */
    fun close() {
        synchronized(lock) {
            decoder?.close()
            decoder = null
            vocabTrie?.close()
            vocabTrie = null
            layoutApplied = false
        }
    }
}
