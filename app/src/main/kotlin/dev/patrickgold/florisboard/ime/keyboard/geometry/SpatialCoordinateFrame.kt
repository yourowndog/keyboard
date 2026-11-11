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

package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A point in the harvest-v4 alpha-relative, independently normalized coordinate frame. */
data class AlphaNormalizedPoint(val x: Float, val y: Float)

/** A point whose axes use the same physical scale, suitable for distances and angles. */
data class IsotropicPoint(val x: Float, val y: Float)

/** Spatial evidence for one resolved touch. Raw pixels remain the caller's responsibility. */
data class ResolvedSpatialTouch(
    val xn: Float,
    val yn: Float,
    val dx: Float,
    val dy: Float,
    val resolvedKey: TextKey,
)

/**
 * The one canonical spatial frame shared by glide classification and harvest-v4 tap telemetry.
 *
 * The frame is the bounding box of the 26 ASCII letter keys, never the whole keyboard. [normalize]
 * intentionally preserves the schema and FUTO model contract: each axis is independently mapped
 * to 0..1. Consumers which calculate Euclidean distances or angles must use [toIsotropic], whose
 * axes share the same denominator. Keeping these projections explicit prevents a future caller
 * from silently treating a rectangular 0..1 frame as physically square.
 */
class SpatialCoordinateFrame private constructor(
    val boardLeft: Float,
    val boardTop: Float,
    val alphaW: Float,
    val alphaH: Float,
    private val lettersByChar: Map<Char, TextKey>,
) {
    val aspect: Float = alphaW / alphaH

    val representativeKeyWidth: Float = lettersByChar.values
        .map { it.visibleBounds.width }
        .positiveMedian()

    val representativeKeyHeight: Float = lettersByChar.values
        .map { it.visibleBounds.height }
        .positiveMedian()

    /** Changes whenever an alpha key's actual visible rectangle changes. */
    val geometrySignature: String = stableHash(buildString {
        for (letter in LETTERS) {
            val bounds = lettersByChar.getValue(letter).visibleBounds
            append(letter).append(':')
            append(bounds.left.toRawBits()).append(',')
            append(bounds.top.toRawBits()).append(',')
            append(bounds.right.toRawBits()).append(',')
            append(bounds.bottom.toRawBits()).append(';')
        }
    })

    fun normalize(x: Float, y: Float): AlphaNormalizedPoint = AlphaNormalizedPoint(
        x = (x - boardLeft) / alphaW,
        y = (y - boardTop) / alphaH,
    )

    /**
     * Projects a point into a physically isotropic space by measuring both axes in alpha widths.
     * Equivalently, `y == normalize(...).y / aspect`. This is deliberately not the schema's 0..1
     * Y value and must not be serialized into harvest-v4's `yn` field.
     */
    fun toIsotropic(x: Float, y: Float): IsotropicPoint = IsotropicPoint(
        x = (x - boardLeft) / alphaW,
        y = (y - boardTop) / alphaW,
    )

    fun normalizedCenter(letter: Char): AlphaNormalizedPoint? {
        val bounds = lettersByChar[letter.lowercaseChar()]?.visibleBounds ?: return null
        return normalize(bounds.center.x, bounds.center.y)
    }

    /**
     * Resolves a touch with the keyboard's existing hit-test authority and returns schema-ready
     * alpha and key-local coordinates. Coordinates are not clamped: a non-letter key legitimately
     * lies outside the alpha block, and the eventual logger decides whether that event carries
     * spatial evidence.
     */
    fun resolveTouch(
        touchX: Float,
        touchY: Float,
        resolveKey: (Float, Float) -> TextKey?,
    ): ResolvedSpatialTouch? {
        val key = resolveKey(touchX, touchY) ?: return null
        val bounds = key.visibleBounds
        if (bounds.width <= 0f || bounds.height <= 0f) return null
        val normalized = normalize(touchX, touchY)
        return ResolvedSpatialTouch(
            xn = normalized.x,
            yn = normalized.y,
            dx = (touchX - bounds.center.x) / bounds.width,
            dy = (touchY - bounds.center.y) / bounds.height,
            resolvedKey = key,
        )
    }

    /** Geometry-aware replacement for the historical `first key width / 4` sample threshold. */
    fun samplingDistanceThresholdSquared(fractionOfKey: Float = 0.25f): Float {
        val characteristicLength = sqrt(representativeKeyWidth * representativeKeyHeight)
        val threshold = characteristicLength * fractionOfKey
        return threshold * threshold
    }

    companion object {
        private const val LETTERS = "abcdefghijklmnopqrstuvwxyz"

        /** Returns null for a partial/non-Latin surface rather than manufacturing a corrupt frame. */
        fun from(keyViews: Iterable<TextKey>): SpatialCoordinateFrame? {
            val lettersByChar = HashMap<Char, TextKey>(LETTERS.length)
            for (key in keyViews) {
                val code = (key.data as? KeyData)?.code ?: KeyCode.UNSPECIFIED
                if (code in 'a'.code..'z'.code && key.visibleBounds.isNotEmpty()) {
                    lettersByChar[code.toChar()] = key
                }
            }
            if (lettersByChar.size != LETTERS.length) return null

            val letters = lettersByChar.values
            val left = letters.minOf { it.visibleBounds.left }
            val top = letters.minOf { it.visibleBounds.top }
            val width = max(1f, letters.maxOf { it.visibleBounds.right } - left)
            val height = max(1f, letters.maxOf { it.visibleBounds.bottom } - top)
            return SpatialCoordinateFrame(left, top, width, height, lettersByChar)
        }
    }
}

/** One-handed geometry that is not represented by the inner keyboard's local key rectangles. */
data class OneHandedGeometry(
    val side: String,
    val scaleFactor: Float,
    val offsetPx: Float,
)

/**
 * Harvest-v4 layout identity plus its readable geometry fields.
 *
 * [fp] includes both configuration inputs and every alpha-key rectangle. Hashing the solved
 * rectangles is important: layout-pack widths and future per-key controls can move keys without
 * gaining a new preference field here, and those samples must still receive a different join key.
 */
data class LayoutFingerprint(
    val fp: String,
    val alphaW: Int,
    val alphaH: Int,
    val aspect: Float,
    val alphaRows: Int,
    val modRowsTop: Int,
    val modRowsBottom: Int,
    val baseKeyH: Int,
    val dpi: Int,
    val orientation: String,
    val oneHanded: String?,
    val numberRowPresent: Boolean,
) {
    companion object {
        fun create(
            keyboard: TextKeyboard,
            preferences: GeometryPreferences,
            frame: SpatialCoordinateFrame,
            densityDpi: Int,
            orientation: GeometryOrientation,
            oneHanded: OneHandedGeometry? = null,
        ): LayoutFingerprint {
            val roles = keyboard.semanticRows.map { it.role }
            val firstAlpha = roles.indexOf(SemanticRowRole.ALPHA)
            val lastAlpha = roles.lastIndexOf(SemanticRowRole.ALPHA)
            val modRoles = setOf(SemanticRowRole.CODING_UTILITY, SemanticRowRole.EXTENSION)
            val modRowsTop = roles.take(firstAlpha.coerceAtLeast(0)).count { it in modRoles }
            val modRowsBottom = if (lastAlpha < 0) {
                0
            } else {
                roles.drop(lastAlpha + 1).count { it in modRoles }
            }
            val numberRowPresent = SemanticRowRole.NUMBER_ROW in roles
            val safe = preferences.sanitized()
            val normalizedOneHandedSide = oneHanded?.side?.trim()?.lowercase(Locale.ROOT)

            val payload = buildString {
                append("spatial-layout-v1|")
                keyboard.semanticRows.forEach { row ->
                    append(row.stableId).append(':').append(row.role.name).append(';')
                }
                append("alphaRows=").append(roles.count { it == SemanticRowRole.ALPHA }).append('|')
                append("numberRow=").append(numberRowPresent).append('|')
                append("modTop=").append(modRowsTop).append('|')
                append("modBottom=").append(modRowsBottom).append('|')
                append("orientation=").append(orientation.name).append('|')
                append("dpi=").append(densityDpi).append('|')
                append("oneHanded=").append(normalizedOneHandedSide).append('|')
                append("oneHandedScale=").append(oneHanded?.scaleFactor?.toRawBits()).append('|')
                append("oneHandedOffset=").append(oneHanded?.offsetPx?.toRawBits()).append('|')
                append("rowBaseHeight=").append(safe.rowBaseHeightPx.toRawBits()).append('|')
                append("alphaRowHeight=").append(safe.alphaRowHeightPercent).append('|')
                append("utilityRowHeight=").append(safe.utilityRowHeightPercent).append('|')
                append("numberRowHeight=").append(safe.numberRowHeightPercent).append('|')
                append("primaryRowHeight=").append(safe.primaryActionRowHeightPercent).append('|')
                append("alphaKeyWidth=").append(safe.alphaKeyWidthPercent).append('|')
                append("utilityKeyWidth=").append(safe.utilityKeyWidthPercent).append('|')
                append("spacingH=").append(safe.keySpacingHorizontalPx.toRawBits()).append('|')
                append("spacingV=").append(safe.keySpacingVerticalPx.toRawBits()).append('|')
                append("primaryInsetH=").append(safe.primaryActionInsetHorizontalPx.toRawBits()).append('|')
                append("primarySpacingH=").append(safe.primaryActionSpacingHorizontalPx?.toRawBits()).append('|')
                append("primarySpacingV=").append(safe.primaryActionSpacingVerticalPx?.toRawBits()).append('|')
                append("primaryGapAbove=").append(safe.primaryActionGapAbovePx.toRawBits()).append('|')
                append("primaryGapBelow=").append(safe.primaryActionGapBelowPx.toRawBits()).append('|')
                append("utilityGapAbove=").append(safe.utilityGapAbovePx.toRawBits()).append('|')
                append("utilityGapWithin=").append(safe.utilityGapWithinPx.toRawBits()).append('|')
                append("utilityGapBelow=").append(safe.utilityGapBelowPx.toRawBits()).append('|')
                append("frame=").append(frame.geometrySignature)
            }

            return LayoutFingerprint(
                fp = stableHash(payload),
                alphaW = frame.alphaW.roundToInt(),
                alphaH = frame.alphaH.roundToInt(),
                aspect = frame.aspect,
                alphaRows = roles.count { it == SemanticRowRole.ALPHA },
                modRowsTop = modRowsTop,
                modRowsBottom = modRowsBottom,
                baseKeyH = frame.representativeKeyHeight.roundToInt(),
                dpi = densityDpi,
                orientation = orientation.name.lowercase(Locale.ROOT),
                oneHanded = normalizedOneHandedSide,
                numberRowPresent = numberRowPresent,
            )
        }
    }
}

private fun List<Float>.positiveMedian(): Float {
    val sorted = filter { it > 0f && it.isFinite() }.sorted()
    require(sorted.isNotEmpty()) { "spatial frame has no positive key dimensions" }
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middle - 1] + sorted[middle]) / 2f
    } else {
        sorted[middle]
    }
}

private fun stableHash(payload: String): String = MessageDigest.getInstance("SHA-256")
    .digest(payload.toByteArray(Charsets.UTF_8))
    .take(4)
    .joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
