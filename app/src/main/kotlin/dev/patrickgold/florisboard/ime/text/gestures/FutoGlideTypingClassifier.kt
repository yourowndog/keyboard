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
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.futo.ml.inference.SwipeDecoder
import org.futo.ml.inference.VocabTrie
import java.io.File
import kotlin.math.max

private fun TextKey.baseCode(): Int = (data as? KeyData)?.code ?: KeyCode.UNSPECIFIED

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
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lock = Any()

    private var decoder: SwipeDecoder? = null
    private var vocabTrie: VocabTrie? = null
    private var loadFailed = false
    private var loading = false

    private var keyX: FloatArray? = null
    private var keyY: FloatArray? = null
    private var layoutApplied = false

    private val ptsX = ArrayList<Float>(256)
    private val ptsY = ArrayList<Float>(256)
    private val ptsT = ArrayList<Long>(256)

    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardW = 1f
    private var boardH = 1f

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
        synchronized(lock) {
            if (decoder != null || loadFailed || loading) return
            loading = true
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
                applyLayout()
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
    private fun applyLayout() {
        val engine: SwipeDecoder
        val trie: VocabTrie
        val cx: FloatArray
        val cy: FloatArray
        synchronized(lock) {
            engine = decoder ?: return
            trie = vocabTrie ?: return
            cx = keyX ?: return
            cy = keyY ?: return
        }
        val ok = engine.setMode(
            letters = LETTERS,
            cx = cx,
            cy = cy,
            tries = longArrayOf(trie.itriePtr),
        )
        synchronized(lock) { layoutApplied = ok }
        Log.d(TAG, "setMode(layout + vocabulary) -> $ok")
    }

    // ---------------------------------------------------------------- geometry

    override fun setLayout(keyViews: List<TextKey>, subtype: Subtype) {
        val byChar = HashMap<Char, TextKey>()
        for (k in keyViews) {
            val c = k.baseCode()
            if (c in 'a'.code..'z'.code) byChar[c.toChar()] = k
        }
        // The encoder needs a centre for every letter it might emit; a partial layout would
        // misplace the ones that are missing.
        if (byChar.size < LETTERS.length) {
            Log.d(TAG, "layout ignored: only ${byChar.size}/${LETTERS.length} letter keys")
            return
        }

        val letters = byChar.values
        boardLeft = letters.minOf { it.visibleBounds.left }
        boardTop = letters.minOf { it.visibleBounds.top }
        boardW = max(1f, letters.maxOf { it.visibleBounds.right } - boardLeft)
        boardH = max(1f, letters.maxOf { it.visibleBounds.bottom } - boardTop)

        val cx = FloatArray(LETTERS.length)
        val cy = FloatArray(LETTERS.length)
        for ((i, ch) in LETTERS.withIndex()) {
            val k = byChar.getValue(ch)
            cx[i] = ((k.visibleBounds.left + k.visibleBounds.right) / 2f - boardLeft) / boardW
            cy[i] = ((k.visibleBounds.top + k.visibleBounds.bottom) / 2f - boardTop) / boardH
        }

        synchronized(lock) {
            val unchanged = keyX?.contentEquals(cx) == true && keyY?.contentEquals(cy) == true
            if (unchanged && layoutApplied) return
            keyX = cx
            keyY = cy
            layoutApplied = false
        }
        Log.d(TAG, "layout set: ${LETTERS.length} keys, board ${boardW.toInt()}x${boardH.toInt()}px")

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
        ptsX.add(position.x); ptsY.add(position.y); ptsT.add(position.t)
    }

    override fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData) {
        for (p in pointerData.positions) addGesturePoint(p)
    }

    override fun clear() {
        ptsX.clear(); ptsY.clear(); ptsT.clear()
    }

    override fun getSuggestions(maxSuggestionCount: Int, gestureCompleted: Boolean): List<String> {
        val engine = synchronized(lock) { if (layoutApplied) decoder else null } ?: return emptyList()

        val n = ptsX.size
        if (n < 2) return emptyList()

        // Normalise into the same 0..1 letter-block space as the key centres, and pass the real
        // event times: the models were trained on time-resampled paths, so dwelling on a key is
        // what separates a deliberate or doubled letter from one merely passed over.
        val x = FloatArray(n)
        val y = FloatArray(n)
        val t = FloatArray(n)
        val t0 = ptsT[0]
        for (i in 0 until n) {
            x[i] = ((ptsX[i] - boardLeft) / boardW).coerceIn(0f, 1f)
            y[i] = ((ptsY[i] - boardTop) / boardH).coerceIn(0f, 1f)
            t[i] = (ptsT[i] - t0).toFloat()
        }

        return try {
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
