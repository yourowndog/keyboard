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

package dev.patrickgold.florisboard.ime.popup

import dev.patrickgold.florisboard.lib.FlorisRect

/**
 * Where key popups sit, in text-keyboard layout coordinates (origin at the layout's top-left).
 *
 * The preview is a horizontal pill that floats just above the pressed key, wider than the key and
 * about a key cap tall. The upstream preview was a tall box (2.5 key heights) anchored to the key's
 * bottom edge, and the LCARS letter keys carry their hint plate inside their bounds, so for the
 * top letter row that box reached far past the smartbar and the IME window cut off its top half,
 * label included.
 *
 * Nothing may start higher than [headroom] above the layout: that is the space the smartbar gives
 * us inside the IME window. Above it the window clips. When a pill would cross that line it is
 * pushed down instead, so it overlaps the top of the key rather than losing its label.
 */
object PopupGeometry {
    /** Preview pill width, in units of the layout's typical letter key width. */
    const val PILL_WIDTH_FACTOR = 1.7f

    /** Preview pill width relative to the pressed key itself, so wide keys still get a wider pill. */
    const val PILL_MIN_KEY_WIDTH_FACTOR = 1.15f

    /** Preview pill height, in units of the typical letter key height (about the cap without its plate). */
    const val PILL_HEIGHT_FACTOR = 0.62f

    /** Width of one alternate in the long-press strip, in units of the pressed key's width. */
    const val EXT_ELEMENT_WIDTH_FACTOR = 1.1f

    /**
     * @param key The pressed key's visible bounds.
     * @param desiredKey The visible bounds of the layout's reference letter key.
     * @param layoutWidth Width of the keyboard layout; the pill is kept inside it.
     * @param headroom How far above the layout's top edge drawing is still visible.
     * @param gap Space left between the pill's bottom edge and the key's top edge.
     */
    fun previewPill(
        key: FlorisRect,
        desiredKey: FlorisRect,
        layoutWidth: Float,
        headroom: Float,
        gap: Float,
    ): FlorisRect {
        val width = maxOf(desiredKey.width * PILL_WIDTH_FACTOR, key.width * PILL_MIN_KEY_WIDTH_FACTOR)
        val height = desiredKey.height * PILL_HEIGHT_FACTOR
        val left = clampSpan(key.left + (key.width - width) / 2f, width, layoutWidth)
        val top = maxOf(key.top - gap - height, -headroom)
        return FlorisRect.new(left = left, top = top, right = left + width, bottom = top + height)
    }

    /**
     * Keeps a vertical extent of [height] ending at [bottom] from starting above -[headroom]. Returns
     * the top edge.
     */
    fun clampTop(bottom: Float, height: Float, headroom: Float): Float {
        return maxOf(bottom - height, -headroom)
    }

    /** Shifts a horizontal span starting at [left] so it stays within [0, layoutWidth] where it fits. */
    fun clampSpan(left: Float, width: Float, layoutWidth: Float): Float {
        if (layoutWidth <= 0f || width >= layoutWidth) return left
        return left.coerceIn(0f, layoutWidth - width)
    }
}
