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
import dev.patrickgold.florisboard.nlpManager
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

private fun TextKey.baseCode(): Int = (data as? KeyData)?.code ?: KeyCode.UNSPECIFIED

/**
 * Glide typing backed by FUTO's pretrained `honorable_sturgeon` encoder.
 *
 * The encoder is layout-agnostic: it takes the swipe trajectory *and* the current
 * keyboard's key centres as separate runtime inputs, so OmniBoard's live geometry is
 * handed over per-layout rather than baked into the weights. Output is a per-timestep
 * CTC distribution over the supplied keys, which a lexicon-constrained prefix beam
 * search turns into words.
 *
 * Model: https://huggingface.co/futo-org/futo-swipe (FUTO Model Weights License 1.0)
 */
class FutoGlideTypingClassifier(private val context: Context) : GlideTypingClassifier {
    companion object {
        private const val TAG = "FUTO_SWIPE"
        private const val ASSET = "futo/honorable_sturgeon.pte"
        private const val TRAJ_POINTS = 64      // encoder input length
        private const val MAX_KEYS = 64         // export-time key padding bound
        private const val BEAM_WIDTH = 24
        private const val BRANCH_PER_STEP = 6   // top letters considered per frame
        private const val NEG = -1e30f
        private val LETTERS = ('a'..'z').toList()
    }

    private val nlpManager by context.nlpManager()

    private var module: Module? = null
    private var loadFailed = false

    /** Sorted lexicon; prefix queries are answered by binary search, so no trie allocation. */
    private var lexicon: Array<String> = emptyArray()
    private var wordDataSubtype: Subtype? = null
    private var layoutSubtype: Subtype? = null

    /** Normalised [0,1] key centres, index-aligned with [LETTERS]. */
    private var keyX = FloatArray(MAX_KEYS)
    private var keyY = FloatArray(MAX_KEYS)
    private var keyCount = 0

    private val ptsX = ArrayList<Float>(256)
    private val ptsY = ArrayList<Float>(256)
    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardW = 1f
    private var boardH = 1f

    val ready: Boolean
        get() = !loadFailed && keyCount > 0 && lexicon.isNotEmpty() &&
            wordDataSubtype != null && wordDataSubtype == layoutSubtype

    // ---------------------------------------------------------------- model

    private fun ensureModule(): Module? {
        module?.let { return it }
        if (loadFailed) return null
        return try {
            val dst = File(context.filesDir, "honorable_sturgeon.pte")
            if (!dst.exists() || dst.length() == 0L) {
                context.assets.open(ASSET).use { input ->
                    dst.outputStream().use { input.copyTo(it) }
                }
            }
            val t0 = System.currentTimeMillis()
            val m = Module.load(dst.absolutePath)
            Log.i(TAG, "encoder loaded from ${dst.name} (${dst.length()} B) in ${System.currentTimeMillis() - t0} ms")
            module = m
            m
        } catch (t: Throwable) {
            Log.e(TAG, "encoder load FAILED - falling back", t)
            loadFailed = true
            null
        }
    }

    // ------------------------------------------------------------- geometry

    override fun setLayout(keyViews: List<TextKey>, subtype: Subtype) {
        setWordData(subtype)
        val byChar = HashMap<Char, TextKey>()
        for (k in keyViews) {
            val c = k.baseCode()
            if (c in 'a'.code..'z'.code) byChar[c.toChar()] = k
        }
        if (byChar.isEmpty()) return

        // FUTO normalises over the letter block alone -- its reference layout puts the
        // three letter rows at y ~= 0.19 / 0.53 / 0.87. Normalising over the whole
        // keyboard instead would squash every letter into the upper part of the range
        // and hand the encoder a layout it has never seen.
        val letters = byChar.values
        boardLeft = letters.minOf { it.visibleBounds.left }
        boardTop = letters.minOf { it.visibleBounds.top }
        boardW = max(1f, letters.maxOf { it.visibleBounds.right } - boardLeft)
        boardH = max(1f, letters.maxOf { it.visibleBounds.bottom } - boardTop)
        keyX = FloatArray(MAX_KEYS); keyY = FloatArray(MAX_KEYS)
        var n = 0
        for (ch in LETTERS) {
            val k = byChar[ch] ?: continue
            keyX[n] = ((k.visibleBounds.left + k.visibleBounds.right) / 2f - boardLeft) / boardW
            keyY[n] = ((k.visibleBounds.top + k.visibleBounds.bottom) / 2f - boardTop) / boardH
            n++
        }
        keyCount = n
        layoutSubtype = subtype
        Log.d(TAG, "layout set: $n letter keys, board ${boardW.toInt()}x${boardH.toInt()}px, ready=$ready")
    }

    override fun setWordData(subtype: Subtype) {
        if (wordDataSubtype == subtype && lexicon.isNotEmpty()) return
        val words = nlpManager.getListOfWords(subtype)
            .asSequence()
            .map { it.lowercase() }
            .filter { w -> w.length > 1 && w.all { it in 'a'..'z' } }
            .distinct()
            .toMutableList()
        words.sort()
        lexicon = words.toTypedArray()
        wordDataSubtype = subtype
        Log.d(TAG, "lexicon built: ${lexicon.size} words, ready=$ready")
    }

    // -------------------------------------------------------------- gesture

    override fun addGesturePoint(position: GlideTypingGesture.Detector.Position) {
        ptsX.add(position.x); ptsY.add(position.y)
    }

    override fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData) {
        clear()
        for (p in pointerData.positions) addGesturePoint(p)
    }

    override fun clear() {
        ptsX.clear(); ptsY.clear()
    }

    // ------------------------------------------------------------- decoding

    override fun getSuggestions(maxSuggestionCount: Int, gestureCompleted: Boolean): List<String> {
        val m = ensureModule() ?: return emptyList()
        if (keyCount == 0 || lexicon.isEmpty() || ptsX.size < 3) return emptyList()

        val t0 = System.nanoTime()
        val emissions = try {
            runEncoder(m) ?: return emptyList()
        } catch (t: Throwable) {
            Log.e(TAG, "inference failed", t)
            return emptyList()
        }
        val words = beamSearch(emissions, maxSuggestionCount)
        Log.d(TAG, "swipe ${ptsX.size} pts -> $words (${(System.nanoTime() - t0) / 1_000_000} ms)")
        return words
    }

    /** Resample the raw trajectory to [TRAJ_POINTS] and run one forward pass. */
    private fun runEncoder(m: Module): Array<FloatArray>? {
        val n = ptsX.size
        val feats = FloatArray(2 * TRAJ_POINTS)
        for (i in 0 until TRAJ_POINTS) {
            val pos = i.toFloat() * (n - 1) / (TRAJ_POINTS - 1)
            val lo = pos.toInt().coerceIn(0, n - 1)
            val hi = (lo + 1).coerceAtMost(n - 1)
            val f = pos - lo
            val x = ptsX[lo] + (ptsX[hi] - ptsX[lo]) * f
            val y = ptsY[lo] + (ptsY[hi] - ptsY[lo]) * f
            feats[i] = ((x - boardLeft) / boardW).coerceIn(0f, 1f)
            feats[TRAJ_POINTS + i] = ((y - boardTop) / boardH).coerceIn(0f, 1f)
        }

        val keys = FloatArray(MAX_KEYS * 2)
        for (i in 0 until keyCount) { keys[i * 2] = keyX[i]; keys[i * 2 + 1] = keyY[i] }
        val out = m.forward(
            EValue.from(Tensor.fromBlob(feats, longArrayOf(1, 2, TRAJ_POINTS.toLong()))),
            EValue.from(Tensor.fromBlob(keys, longArrayOf(1, MAX_KEYS.toLong(), 2))),
            EValue.from(FutoEtCompat.boolTensor(MAX_KEYS, keyCount, longArrayOf(1, MAX_KEYS.toLong()))),
        )
        if (out.isEmpty() || !out[0].isTensor) return null

        val t = out[0].toTensor()
        val flat = FutoEtCompat.floatData(t)
        val shape = t.shape()                       // [1, T, C]
        val steps = shape[1].toInt()
        val classes = shape[2].toInt()
        return Array(steps) { s -> FloatArray(classes) { c -> flat[s * classes + c] } }
    }

    // ------------------------------------------------- lexicon prefix lookup

    /** Insertion point of [p] in the sorted lexicon. */
    private fun lowerBound(p: String): Int {
        var lo = 0; var hi = lexicon.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (lexicon[mid] < p) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun isPrefix(p: String): Boolean {
        val i = lowerBound(p)
        return i < lexicon.size && lexicon[i].startsWith(p)
    }

    private fun isWord(p: String): Boolean {
        val i = lowerBound(p)
        return i < lexicon.size && lexicon[i] == p
    }

    private fun logAdd(a: Float, b: Float): Float {
        if (a <= NEG) return b
        if (b <= NEG) return a
        val hi = max(a, b)
        return hi + ln(1f + exp(-kotlin.math.abs(a - b)))
    }

    /**
     * CTC prefix beam search, pruned to prefixes that can still reach a real word.
     * The lexicon is a runtime argument -- adding vocabulary needs no retraining.
     */
    private fun beamSearch(logp: Array<FloatArray>, topN: Int): List<String> {
        val classes = logp[0].size
        val blank = classes - 1
        var beams = HashMap<String, FloatArray>()   // prefix -> [logPblank, logPnonBlank]
        beams[""] = floatArrayOf(0f, NEG)

        val order = IntArray(keyCount) { it }
        for (frame in logp) {
            // consider only the most probable few keys this frame
            val idx = order.sortedByDescending { frame[it] }.take(BRANCH_PER_STEP)
            val next = HashMap<String, FloatArray>(beams.size * 2)

            fun bump(key: String, b: Float, nb: Float) {
                val e = next.getOrPut(key) { floatArrayOf(NEG, NEG) }
                e[0] = logAdd(e[0], b); e[1] = logAdd(e[1], nb)
            }

            for ((pre, pr) in beams) {
                val total = logAdd(pr[0], pr[1])
                // emit blank, or repeat the last character (both keep the prefix)
                var nb = NEG
                if (pre.isNotEmpty()) {
                    val li = LETTERS.indexOf(pre.last())
                    if (li in 0 until keyCount) nb = pr[1] + frame[li]
                }
                bump(pre, total + frame[blank], nb)

                for (ci in idx) {
                    val ch = LETTERS[ci]
                    val ext = pre + ch
                    if (!isPrefix(ext)) continue
                    val src = if (pre.isNotEmpty() && ch == pre.last()) pr[0] else total
                    bump(ext, NEG, src + frame[ci])
                }
            }
            beams = HashMap(
                next.entries.sortedByDescending { logAdd(it.value[0], it.value[1]) }
                    .take(BEAM_WIDTH).associate { it.key to it.value }
            )
        }

        return beams.entries
            .filter { isWord(it.key) }
            .sortedByDescending { logAdd(it.value[0], it.value[1]) }
            .take(topN)
            .map { it.key }
    }
}
