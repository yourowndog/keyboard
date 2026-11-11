package dev.patrickgold.florisboard.ime.core

import kotlin.math.sqrt

/**
 * Single source of truth for keyboard layout data (QWERTY spatial model).
 * Used by CandidateScorer.spatialCost() and swipe gesture scoring.
 *
 * Uses coordinate-based Euclidean distance instead of binary adjacent/far.
 * QWERTY stagger: top row no offset, home row 0.5 offset, bottom row 1.5 offset.
 */
object KeyboardLayout {

    /**
     * QWERTY key positions as (x, y) coordinates.
     * X = column position within row, Y = row number.
     * Rows are staggered to match physical QWERTY layout.
     */
    val QWERTY_POSITIONS: Map<Char, Pair<Float, Float>> = mapOf(
        // Number row (above the top letter row): no offset. Present so that
        // number-row fat-fingers (5 for t, 3 for e) carry real spatial evidence.
        '1' to (0.0f to -1.0f),
        '2' to (1.0f to -1.0f),
        '3' to (2.0f to -1.0f),
        '4' to (3.0f to -1.0f),
        '5' to (4.0f to -1.0f),
        '6' to (5.0f to -1.0f),
        '7' to (6.0f to -1.0f),
        '8' to (7.0f to -1.0f),
        '9' to (8.0f to -1.0f),
        '0' to (9.0f to -1.0f),
        // Row 0 (top): no offset
        'q' to (0.0f to 0.0f),
        'w' to (1.0f to 0.0f),
        'e' to (2.0f to 0.0f),
        'r' to (3.0f to 0.0f),
        't' to (4.0f to 0.0f),
        'y' to (5.0f to 0.0f),
        'u' to (6.0f to 0.0f),
        'i' to (7.0f to 0.0f),
        'o' to (8.0f to 0.0f),
        'p' to (9.0f to 0.0f),
        // Row 1 (home): 0.5 offset (standard QWERTY stagger)
        'a' to (0.5f to 1.0f),
        's' to (1.5f to 1.0f),
        'd' to (2.5f to 1.0f),
        'f' to (3.5f to 1.0f),
        'g' to (4.5f to 1.0f),
        'h' to (5.5f to 1.0f),
        'j' to (6.5f to 1.0f),
        'k' to (7.5f to 1.0f),
        'l' to (8.5f to 1.0f),
        // Row 2 (bottom): 1.5 offset
        'z' to (1.5f to 2.0f),
        'x' to (2.5f to 2.0f),
        'c' to (3.5f to 2.0f),
        'v' to (4.5f to 2.0f),
        'b' to (5.5f to 2.0f),
        'n' to (6.5f to 2.0f),
        'm' to (7.5f to 2.0f),
    )

    /**
     * Keys that span several columns, as (xMin, xMax, y).
     *
     * Distance to a wide key is measured to the nearest point on its extent
     * rather than to a centre point. The spacebar is the whole reason this
     * exists: modelling it as a single point would put 'n' — its single most
     * common mis-hit — further away than 'v', which is backwards. Measured
     * against its real extent, every bottom-row key sits exactly one row
     * above it, which is what the harvest data actually shows.
     */
    private val WIDE_KEYS: Map<Char, Triple<Float, Float, Float>> = mapOf(
        ' ' to Triple(2.5f, 7.5f, 3.0f),
    )

    /** Every key the spatial model knows about, wide keys included. */
    private val ALL_KEYS: Set<Char> = QWERTY_POSITIONS.keys + WIDE_KEYS.keys

    /**
     * Legacy adjacency map - kept for backward compatibility.
     * Built from the spatial model: keys within distance 1.5 are adjacent.
     */
    val QWERTY_NEIGHBORS: Map<Char, String> by lazy {
        val result = mutableMapOf<Char, String>()
        for (key in ALL_KEYS) {
            val neighbors = StringBuilder()
            for (other in ALL_KEYS) {
                if (other == key) continue
                if (isAdjacent(key, other)) neighbors.append(other)
            }
            result[key] = neighbors.toString()
        }
        result
    }

    /**
     * Check if two keys are adjacent on QWERTY layout.
     * Uses Euclidean distance threshold of 1.5 key units.
     */
    fun isAdjacent(a: Char, b: Char): Boolean {
        val dist = keyDistance(a, b)
        return dist > 0.0 && dist < 1.5
    }

    /**
     * Get continuous spatial distance between two characters.
     * Uses Euclidean distance between key centers on the QWERTY layout.
     *
     * @return Distance clamped to [0.0, 2.0]:
     *   - 0.0 = same key
     *   - ~0.5-1.0 = adjacent keys (e.g., 'r' and 't')
     *   - ~1.0-1.5 = nearby keys (e.g., 'r' and 'g')
     *   - ~1.5-2.0 = far keys (e.g., 'q' and 'm')
     *   - 2.0 = unknown characters or max distance
     */
    fun keyDistance(a: Char, b: Char): Double {
        val aLower = a.lowercaseChar()
        val bLower = b.lowercaseChar()
        if (aLower == bLower) return 0.0
        val posA = QWERTY_POSITIONS[aLower]
        val posB = QWERTY_POSITIONS[bLower]
        val raw = when {
            posA != null && posB != null -> {
                val dx = posA.first - posB.first
                val dy = posA.second - posB.second
                sqrt((dx * dx + dy * dy).toDouble())
            }
            posA != null -> spanDistance(posA, WIDE_KEYS[bLower] ?: return 2.0)
            posB != null -> spanDistance(posB, WIDE_KEYS[aLower] ?: return 2.0)
            else -> return 2.0
        }
        return raw.coerceIn(0.0, 2.0)
    }

    /** Distance from a point key to the nearest point on a wide key's extent. */
    private fun spanDistance(point: Pair<Float, Float>, span: Triple<Float, Float, Float>): Double {
        val (xMin, xMax, y) = span
        val nearestX = point.first.coerceIn(xMin, xMax)
        val dx = point.first - nearestX
        val dy = point.second - y
        return sqrt((dx * dx + dy * dy).toDouble())
    }
}
