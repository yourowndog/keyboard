/*
 * Copyright (C) 2026 The OmniBoard Contributors
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

package dev.patrickgold.florisboard.ime.theme.backdrop

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hue shift for image backdrops: rotates the backdrop's colours around the colour wheel so a GIF
 * or still can be matched to (or set against) the theme, without touching the file.
 *
 * The shift is a colour matrix handed to the image painter, so it is applied when each frame is
 * drawn. Nothing is decoded again when the shift changes, and the stored file is never rewritten.
 * Keys, labels and the command strip are drawn on top of the backdrop and are not affected.
 *
 * Shifts are kept per backdrop in `prefs.theme.backdropHueShifts` as `id=degrees` pairs joined by
 * `;`, for example `asset:warp.gif=40;file:bridge.png=-90`. Backdrop ids never contain `=` or `;`
 * (see [BackdropFiles.isSafeName]), so the format needs no escaping. A backdrop with no entry, or a
 * zero entry, shows its original colours.
 */
object BackdropHue {
    const val MIN_DEGREES = -180
    const val MAX_DEGREES = 180

    /** Within this many degrees of zero the slider snaps to zero, so "original" is easy to hit. */
    const val SNAP_TO_ZERO = 3

    fun clamp(degrees: Int): Int = degrees.coerceIn(MIN_DEGREES, MAX_DEGREES)

    /** Slider value to stored degrees: rounded, clamped, and snapped to zero near the middle. */
    fun fromSlider(value: Float): Int {
        val degrees = clamp(value.roundToInt())
        return if (kotlin.math.abs(degrees) <= SNAP_TO_ZERO) 0 else degrees
    }

    /**
     * The 4x5 row-major colour matrix (Android `ColorMatrix` layout) that rotates hue by [degrees].
     *
     * This is the luminance-preserving hue rotation from the SVG/CSS `hueRotate` filter: greys stay
     * grey and each colour keeps roughly its brightness, so the backdrop's contrast against the keys
     * does not change. Alpha passes through untouched.
     */
    fun matrix(degrees: Float): FloatArray {
        val radians = Math.toRadians(degrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Never throws: malformed or out-of-range entries are skipped or clamped. */
    fun parse(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(';').mapNotNull { entry ->
            val id = entry.substringBeforeLast('=', "").trim()
            val degrees = entry.substringAfterLast('=', "").trim().toIntOrNull()
            if (id.isEmpty() || degrees == null) null else id to clamp(degrees)
        }.toMap().filterValues { it != 0 }
    }

    fun encode(shifts: Map<String, Int>): String =
        shifts.filter { (id, degrees) -> id.isNotEmpty() && degrees != 0 }
            .toSortedMap()
            .entries.joinToString(";") { (id, degrees) -> "$id=${clamp(degrees)}" }

    fun shiftFor(raw: String?, id: BackdropId): Int =
        if (supports(id)) parse(raw)[id.storedValue] ?: 0 else 0

    /** [raw] with [id]'s shift set to [degrees]; zero removes the entry. */
    fun withShift(raw: String?, id: BackdropId, degrees: Int): String {
        val shifts = parse(raw).toMutableMap()
        if (degrees == 0) shifts.remove(id.storedValue) else shifts[id.storedValue] = clamp(degrees)
        return encode(shifts)
    }

    /** Only image backdrops are recoloured; the code-drawn ones are already in the theme's palette. */
    fun supports(id: BackdropId): Boolean = id is BackdropId.PrivateAsset || id is BackdropId.Imported

    /**
     * The shift being dragged in settings, before it is saved: the keyboard follows it at frame
     * rate while the slider moves, and the preference is written once when the drag ends. The
     * settings screen and the keyboard service share one process, so this needs no IPC.
     */
    val preview = MutableStateFlow<Pair<String, Int>?>(null)
}
