/*
 * Copyright (C) 2026 The OmniBoard Contributors
 */
package dev.patrickgold.florisboard.ime.nlp.shared

/**
 * Where the finger actually landed for each letter of the word being typed.
 *
 * The scorer otherwise only knows which key registered. In the 2026-11 harvest, clean taps
 * scatter about 0.19 key widths around the key centre, while about half of the taps that hit
 * the wrong neighbour land more than 0.3 key widths toward the key that was meant. A tap on
 * the shared border is weak evidence for either key; a tap in the middle of a key is strong
 * evidence for it. Feeding the offsets to [CandidateScorer.spatialCost] lets the ranking tell
 * those apart.
 *
 * Offsets are in key units relative to the centre of the key that registered: +x right,
 * +y down, a full key width or height being 1.0.
 */
data class TouchSample(val char: Char, val dx: Float, val dy: Float)

object ComposingTouchTrace {
    private const val MAX_TAPS = 48

    private val taps = ArrayList<TouchSample?>()

    /** A letter key was released. [dx]/[dy] are null when no spatial frame was available. */
    @Synchronized
    fun onLetter(char: Char, dx: Float?, dy: Float?) {
        taps += if (dx != null && dy != null && dx.isFinite() && dy.isFinite()) {
            TouchSample(char.lowercaseChar(), dx, dy)
        } else {
            TouchSample(char.lowercaseChar(), Float.NaN, Float.NaN)
        }
        if (taps.size > MAX_TAPS) taps.subList(0, taps.size - MAX_TAPS).clear()
    }

    @Synchronized
    fun onBackspace() {
        if (taps.isNotEmpty()) taps.removeAt(taps.lastIndex)
    }

    /** Space, enter, punctuation, a cursor jump: the next letter starts a new word. */
    @Synchronized
    fun reset() {
        taps.clear()
    }

    /**
     * The samples behind [word], one per character, or null when the trace cannot be trusted
     * to describe it (the word was edited by other means, pasted, or reached by moving the
     * cursor). Only the trailing samples are compared, so a stale prefix left by a held
     * backspace does not poison the next word.
     */
    @Synchronized
    fun snapshotFor(word: String): List<TouchSample?>? {
        if (word.isEmpty() || taps.size < word.length) return null
        val tail = taps.subList(taps.size - word.length, taps.size)
        for (i in word.indices) {
            val sample = tail[i] ?: return null
            if (sample.char != word[i].lowercaseChar()) return null
        }
        return tail.map { sample -> sample?.takeIf { !it.dx.isNaN() } }
    }
}
