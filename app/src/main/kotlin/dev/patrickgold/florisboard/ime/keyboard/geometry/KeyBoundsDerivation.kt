/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

import dev.patrickgold.florisboard.lib.FlorisRect

/**
 * Stage 03: the three bounds layers, derived as named steps.
 *
 * The solver produces exactly one thing — the **structural** rectangle, the space a row or item is
 * allocated. Everything else is derived from it here, in the open, so that each layer can be tested
 * on its own and so that no consumer is left guessing which rectangle it is looking at.
 *
 * ```
 *   structural   what the solver allocated. Adjacent rectangles share an edge and never overlap.
 *      │
 *      ├─ touch  the whole structural allocation, so there are no dead strips between keys,
 *      │         plus the deliberate bottom-edge extension below.
 *      │
 *      └─ visible the keycap the user sees: structural, inset by half the declared spacing on
 *                 every side.
 * ```
 *
 * ## Why half the spacing
 *
 * Spacing is stated once, as the gap the user wants to *see* between two keycaps. Each of the two
 * neighbours contributes half of it, so a preference of 2dp yields a 2dp gap between keycaps and a
 * 1dp margin where a row meets the outer edge. The historical layout applied the whole preference
 * to both sides of both keys and produced double the requested gap; deriving the inset rather than
 * declaring it is what makes that class of mistake unrepresentable.
 *
 * Visual spacing never moves a structural edge, so it can neither create a dead strip the touch
 * layer does not cover nor push a keycap outside its allocation.
 */
object KeyBoundsDerivation {

    /**
     * The bottom-edge touch policy, named.
     *
     * A thumb aiming at the bottom row of a keyboard that sits flush against the device's bottom
     * edge routinely lands below the row — on the navigation area, the bezel, or the window's own
     * bottom offset. The last row's *touch* rectangle therefore extends one further row height past
     * the frame, so those presses still reach the key they visually cover.
     *
     * This is a touch-only allowance. Nothing visible moves, no structural rectangle changes, and
     * the service/window bottom offset stays outside solved row geometry entirely — the extension
     * is expressed in row heights precisely so it does not need to know about it.
     */
    const val BOTTOM_EDGE_TOUCH_EXTENSION_ROWS: Float = 1.0f

    /** The structural rectangle: what the solver allocated, unchanged. */
    fun structuralBounds(item: SolvedItem): GeometryRect = item.bounds

    /**
     * The touch rectangle for [item].
     *
     * Deliberately forgiving: it is the entire structural allocation, so every pixel between two
     * keycaps belongs to one of them and the visible gap is not a hole. [extendToBottomEdge] adds
     * the bottom-edge allowance for the last row.
     */
    fun touchBounds(
        item: SolvedItem,
        rowHeight: Int,
        extendToBottomEdge: Boolean,
        into: FlorisRect,
    ): FlorisRect = into.apply {
        val structural = structuralBounds(item)
        left = structural.left.toFloat()
        top = structural.top.toFloat()
        right = structural.right.toFloat()
        bottom = structural.bottom.toFloat() +
            if (extendToBottomEdge) rowHeight * BOTTOM_EDGE_TOUCH_EXTENSION_ROWS else 0f
    }

    /**
     * The visible keycap for [item]: the structural rectangle inset by half [spacing] on each side.
     *
     * The inset is clamped so a hostile spacing preference cannot invert a small key into a
     * negative rectangle; an over-wide spacing collapses the keycap to a zero-width sliver instead,
     * which is visibly wrong and harmless rather than invisibly wrong and corrupt.
     */
    fun visibleBounds(
        item: SolvedItem,
        spacing: GeometrySpacing,
        into: FlorisRect,
    ): FlorisRect = into.apply {
        val structural = structuralBounds(item)
        val insetH = halfInset(spacing.horizontal, structural.width)
        val insetV = halfInset(spacing.vertical, structural.height)
        left = structural.left + insetH
        top = structural.top + insetV
        right = structural.right - insetH
        bottom = structural.bottom - insetV
    }

    /** Half the declared spacing, never more than half the rectangle it is being taken out of. */
    private fun halfInset(spacing: Double, extent: Int): Float {
        if (!spacing.isFinite() || spacing <= 0.0 || extent <= 0) return 0f
        return (spacing / 2.0).coerceAtMost(extent / 2.0).toFloat()
    }
}
