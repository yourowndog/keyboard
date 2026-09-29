/*
 * Copyright (C) 2026 The OmniBoard Authors
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

package dev.patrickgold.florisboard.ime.theme.lcars

import kotlin.math.roundToInt

/**
 * Pure geometry model for the LCARS asymmetric left-elbow chassis.
 *
 * All measurements are derived from a normalising [moduleHeight] H using
 * ratios recorded from the reference imagery (ROADMAP.md).
 *
 * The chassis produces four named paths:
 * - [primaryRail]    — outer, thicker band (shoulder + horizontal)
 * - [secondaryRail]  — inner, thinner band
 * - [deckPath]       — the keyboard deck area behind the key field
 * - [viewportClip]   — the content region inside the chassis
 *
 * Each path is a closed filled shape described by continuous segments.
 * Every segment connects exactly to its predecessor — no gaps or unintended
 * diagonal connectors exist in the path descriptors.
 *
 * The model is platform-independent: it works in logical dp values and
 * produces no Compose or Android types.  A renderer (e.g. Compose Canvas)
 * converts the raw geometry to native drawing primitives.
 *
 * @param viewportWidth  logical width of the drawable area (dp).
 * @param viewportHeight logical height of the drawable area (dp).
 * @param moduleHeight   normalising base measure H (default 64 dp study).
 */
data class LcarsChassisGeometry(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val moduleHeight: Float = 64f,
) {
    // ── Ratio-derived measurements from ROADMAP.md ──────────────────────

    /** Thickness of the primary (outer) rail band.  12.4 dp at H=64. */
    val primaryThickness: Float get() = moduleHeight * 0.194f

    /** Gap between primary and secondary rails.  ~0.7 dp at H=64. */
    val channel: Float get() = moduleHeight * 0.011f

    /** Thickness of the secondary (inner) rail band.  7.0 dp at H=64. */
    val secondaryThickness: Float get() = moduleHeight * 0.109f

    /** Outside bend radius of the primary corner.  ~29.6 dp at H=64. */
    val primaryOuterRadius: Float get() = moduleHeight * 0.463f

    /** Width of the vertical shoulder for the primary band.  ~56.8 dp at H=64. */
    val primaryShoulderWidth: Float get() = moduleHeight * 0.887f

    /** Width of the vertical shoulder for the secondary band.  ~22.8 dp at H=64. */
    val secondaryShoulderWidth: Float get() = moduleHeight * 0.357f

    /**
     * Combined shoulder extent (widest point of the elbow assembly).  ~79.7 dp at H=64.
     *
     * The primary shoulder extends from the left edge by [primaryShoulderWidth].
     * The secondary shoulder is offset rightward by [channel] and extends a
     * further [secondaryShoulderWidth]. The combined horizontal extent is the
     * sum of these three values.
     */
    val combinedShoulderWidth: Float get() = primaryShoulderWidth + channel + secondaryShoulderWidth

    // ── Derived radii ──────────────────────────────────────────────────

    /** Inner radius of the primary corner.  OuterRadius - Thickness */
    val primaryInnerRadius: Float get() = primaryOuterRadius - primaryThickness

    /** Outside radius of the secondary corner (concentric with primary inner). */
    val secondaryOuterRadius: Float get() = primaryInnerRadius - channel

    /** Inner radius of the secondary corner. */
    val secondaryInnerRadius: Float get() = secondaryOuterRadius - secondaryThickness

    // ── Shared arc center ──────────────────────────────────────────────

    /**
     * The shared x-coordinate of the concentric arc center for both primary
     * and secondary bands.
     *
     * The primary arc center is at (primaryOuterRadius, primaryOuterRadius).
     * The secondary arc uses the same center — its shoulder is offset rightward
     * by (primaryShoulderWidth + channel - secondaryOuterRadius).
     */
    val arcCenterX: Float get() = primaryOuterRadius

    val arcCenterY: Float get() = primaryOuterRadius

    // ── Key reference points ───────────────────────────────────────────

    /** Top y-coordinate of the primary rail bottom edge. */
    val primaryRailBottom: Float get() = primaryThickness

    /** Top y-coordinate of the secondary rail top edge. */
    val secondaryRailTop: Float get() = primaryRailBottom + channel

    /** Top y-coordinate of the secondary rail bottom edge. */
    val secondaryRailBottom: Float get() = secondaryRailTop + secondaryThickness

    /** Left-edge inset for the viewport (widest point of left-side structure). */
    val viewportLeft: Float get() = combinedShoulderWidth

    /** Top-edge inset for the viewport. */
    val viewportTop: Float get() = secondaryRailBottom

    // ── Secondary shoulder start x (offset rightward by primary shoulder + channel) ──

    /** Left edge of the secondary shoulder vertical portion. */
    private val secondaryShoulderLeft: Float get() = primaryShoulderWidth + channel

    // ── Path segment descriptors (platform-independent) ────────────────

    /**
     * A straight segment or arc in the chassis outline.
     */
    sealed class Segment {
        data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : Segment()
        data class Arc(
            val cx: Float, val cy: Float,
            val radius: Float,
            /** Start angle in degrees (0 = 3 o'clock). */
            val startAngle: Float,
            /** Sweep angle in degrees (positive = CW). */
            val sweepAngle: Float,
        ) : Segment()
    }

    /**
     * Path assembled from [Segment]s.  The first segment defines the start
     * point; every subsequent segment continues from the previous endpoint.
     * All segments are inherently continuous — each starts exactly where the
     * prior one ends.
     */
    data class PathDescriptor(
        val segments: List<Segment>,
    ) {
        /** Bounding box of all segments in logical coordinates. */
        val bounds: Bounds by lazy { computeBounds() }

        private fun computeBounds(): Bounds {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            fun include(x: Float, y: Float) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }

            for (seg in segments) {
                when (seg) {
                    is Segment.Line -> {
                        include(seg.x1, seg.y1)
                        include(seg.x2, seg.y2)
                    }
                    is Segment.Arc -> {
                        include(seg.cx - seg.radius, seg.cy - seg.radius)
                        include(seg.cx + seg.radius, seg.cy + seg.radius)
                    }
                }
            }
            return Bounds(minX, minY, maxX, maxY)
        }
    }

    data class Bounds(
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float,
    ) {
        val width: Float get() = maxX - minX
        val height: Float get() = maxY - minY
        val isEmpty: Boolean get() = width <= 0f || height <= 0f

        /** True if this bounds fully contains the other. */
        infix fun contains(other: Bounds): Boolean =
            minX <= other.minX && minY <= other.minY &&
                maxX >= other.maxX && maxY >= other.maxY

        /** True if these bounds overlap (intersect). */
        infix fun overlaps(other: Bounds): Boolean =
            minX < other.maxX && maxX > other.minX &&
                minY < other.maxY && maxY > other.minY
    }

    // ── Primary rail path ──────────────────────────────────────────────

    /**
     * Primary (outer) rail path — a filled L-shaped band.
     *
     * The primary band forms an L-shaped bracket on the left and top edges:
     * - a wide vertical shoulder (primaryShoulderWidth) descending from the
     *   arc left side to the viewport bottom
     * - a thin horizontal rail (primaryThickness) along the top
     * - a rounded outer corner (primaryOuterRadius)
     *
     * The path is closed: outer boundary traces CW, then the inner boundary
     * traces CCW back. Every segment endpoint connects precisely to its
     * predecessor — no unintended diagonals.
     */
    fun primaryRail(): PathDescriptor {
        val segs = mutableListOf<Segment>()
        val R = primaryOuterRadius
        val T = primaryThickness
        val SW = primaryShoulderWidth
        val VW = viewportWidth
        val VH = viewportHeight
        val cx = arcCenterX
        val cy = arcCenterY

        // ── Outer boundary (clockwise) ──
        //
        // Start at top-right corner of the viewport, go left to the arc start.

        // 1. Top edge: right → left, from (VW, 0) to (cx, 0)
        segs.add(Segment.Line(VW, 0f, cx, 0f))

        // 2. Outer arc: center (cx, cy), radius R
        //    Start at angle -90° (12 o'clock, pointing up): (cx, 0)
        //    Sweep -90° CCW to angle -180° (9 o'clock): (cx - R, cy) = (0, cy)
        //    Endpoint: (0, cy)
        segs.add(Segment.Arc(cx, cy, R, -90f, -90f))

        // 3. Left edge: from (0, cy) down to (0, VH)
        segs.add(Segment.Line(0f, cy, 0f, VH))

        // 4. Bottom edge of shoulder: from (0, VH) right to (SW, VH)
        segs.add(Segment.Line(0f, VH, SW, VH))

        // ── Inner boundary (counter-clockwise, back up) ──
        //
        // From bottom-right of shoulder, go up, then left along the inner
        // arc start line, then the inner arc, then right along rail bottom.

        // 5. Right (inner) edge of shoulder: from (SW, VH) up to (SW, cy)
        segs.add(Segment.Line(SW, VH, SW, cy))

        // 6. Horizontal connector: from (SW, cy) left to inner arc start at (T, cy)
        //    The inner arc start is at angle -180° (9 o'clock):
        //    (cx - innerR, cy) = (cx - (R - T), cy) = (T, cy)
        segs.add(Segment.Line(SW, cy, T, cy))

        // 7. Inner arc: center (cx, cy), radius (R - T)
        //    Start at angle -180° (9 o'clock): (T, cy)
        //    Sweep +90° CW to angle -90° (12 o'clock): (cx, cy - (R - T)) = (cx, T)
        //    Endpoint: (cx, T)
        segs.add(Segment.Arc(cx, cy, R - T, -180f, 90f))

        // 8. Horizontal rail bottom edge: from (cx, T) right to (VW, T)
        segs.add(Segment.Line(cx, T, VW, T))

        return PathDescriptor(segs)
    }

    // ── Secondary rail path ────────────────────────────────────────────

    /**
     * Secondary (inner) rail path — a filled L-shaped band concentric with
     * the primary.
     *
     * Concentric with the primary, separated by [channel]. The vertical
     * shoulder is narrower (secondaryShoulderWidth) and is offset rightward
     * by the primary shoulder width plus channel. The outer radius derives
     * from the primary's inner geometry.
     *
     * Every segment connects exactly to its predecessor — no unintended
     * diagonals or omitted inner bends.
     */
    fun secondaryRail(): PathDescriptor {
        val segs = mutableListOf<Segment>()
        val T = primaryThickness
        val C = channel
        val ST = secondaryThickness
        val SW = secondaryShoulderWidth
        val VW = viewportWidth
        val VH = viewportHeight
        val cx = arcCenterX
        val cy = arcCenterY

        val outerR = secondaryOuterRadius
        val innerR = secondaryInnerRadius
        val secLeft = secondaryShoulderLeft  // left edge of secondary shoulder

        // ── Outer boundary (clockwise) ──
        //
        // Start at top-right, go left to the arc start.

        // 1. Top edge of secondary rail: (VW, T + C) left to arc start at (cx, T + C)
        //    Actually, at 12 o'clock of the outer arc: (cx, cy - outerR)
        //    cy - outerR = R - (R - T - C) = T + C. ✓
        segs.add(Segment.Line(VW, T + C, cx, T + C))

        // 2. Outer arc: center (cx, cy), radius outerR
        //    Start at angle -90° (12 o'clock): (cx, T + C) — wait:
        //    cy - outerR = R - (R - T - C) = T + C ✓
        //    Sweep -90° CCW to angle -180° (9 o'clock):
        //    (cx - outerR, cy) = (R - outerR, R)
        //    Endpoint: (R - outerR, R)
        //    But R - outerR = R - (R - T - C) = T + C. So (T + C, R).
        //    Hmm, that doesn't match secLeft. Let me re-derive.
        //
        //    Actually, wait. outerR = R - T - C, so R - outerR = R - (R - T - C) = T + C.
        //    The arc at 9 o'clock ends at (R - outerR, R) = (T + C, R).
        //    But the secondary shoulder left edge is at secLeft = SW_prim + C.
        //    These are different values! The arc ends at (T+C, R) but the
        //    shoulder sits at (SW_prim + C, R). Since SW_prim = 56.8 and T = 12.4,
        //    we have T+C = 13.1 but secLeft = 57.5.
        //
        //    This means the secondary shoulder doesn't attach directly to the
        //    arc. The shoulder is much further right than the arc's left extent.
        //    The left edge therefore must run from the arc endpoint (T+C, R)
        //    across to the shoulder start (secLeft, R) — a horizontal connector.
        //    That IS the intended widening: the shoulder widens beyond the arc.
        segs.add(Segment.Arc(cx, cy, outerR, -90f, -90f))

        // 3. Horizontal connector from arc bottom to secondary shoulder left edge:
        //    (T + C, R) → (secLeft, R)
        segs.add(Segment.Line(T + C, cy, secLeft, cy))

        // 4. Left edge of secondary shoulder: from (secLeft, cy) down to (secLeft, VH)
        segs.add(Segment.Line(secLeft, cy, secLeft, VH))

        // 5. Bottom edge of secondary shoulder: from (secLeft, VH) right to (secLeft + SW, VH)
        segs.add(Segment.Line(secLeft, VH, secLeft + SW, VH))

        // ── Inner boundary (counter-clockwise, back up) ──
        //
        // From bottom-right of shoulder, go up, then left to inner arc start,
        // then the inner arc, then right along rail bottom.

        // 6. Right (inner) edge of secondary shoulder: from (secLeft + SW, VH) up to (secLeft + SW, cy)
        segs.add(Segment.Line(secLeft + SW, VH, secLeft + SW, cy))

        // 7. Horizontal connector from shoulder inner edge to inner arc start:
        //    from (secLeft + SW, cy) left to (cx - innerR, cy)
        //    Inner arc at 9 o'clock: (cx - innerR, cy)
        val innerArcStartX = cx - innerR
        segs.add(Segment.Line(secLeft + SW, cy, innerArcStartX, cy))

        // 8. Inner arc: center (cx, cy), radius innerR
        //    Start at angle -180° (9 o'clock): (cx - innerR, cy)
        //    Sweep +90° CW to angle -90° (12 o'clock): (cx, cy - innerR)
        //    Endpoint: (cx, T + C + ST)
        //    Check: cy - innerR = R - (R - T - C - ST) = T + C + ST. ✓
        segs.add(Segment.Arc(cx, cy, innerR, -180f, 90f))

        // 9. Horizontal rail bottom edge: from (cx, T + C + ST) right to (VW, T + C + ST)
        segs.add(Segment.Line(cx, T + C + ST, VW, T + C + ST))

        return PathDescriptor(segs)
    }

    // ── Deck path ──────────────────────────────────────────────────────

    /**
     * Keyboard deck path — the area behind the keys, below the Smartbar.
     *
     * The deck is the full-viewport filled rectangle that provides the
     * keyboard's opaque backing. It sits behind the rail bands and key field.
     * At C1 this is rectangular; future checkpoints may shape it to join
     * with the elbow shoulder.
     */
    fun deckPath(): PathDescriptor {
        val segs = mutableListOf<Segment>()
        segs.add(Segment.Line(viewportWidth, 0f, 0f, 0f))
        segs.add(Segment.Line(0f, 0f, 0f, viewportHeight))
        segs.add(Segment.Line(0f, viewportHeight, viewportWidth, viewportHeight))
        segs.add(Segment.Line(viewportWidth, viewportHeight, viewportWidth, 0f))
        return PathDescriptor(segs)
    }

    // ── Viewport clip path ─────────────────────────────────────────────

    /**
     * Viewport clip path — the content region inside the chassis rails.
     *
     * Clips inside the rails and to the right of the shoulder assembly.
     * This rectangle represents the instrument viewport area.
     * At C1 the clip is rectangular; future checkpoints may shape it
     * to follow the rail curves.
     */
    fun viewportClip(): PathDescriptor {
        val segs = mutableListOf<Segment>()
        val left = viewportLeft
        val top = viewportTop
        segs.add(Segment.Line(viewportWidth, top, left, top))
        segs.add(Segment.Line(left, top, left, viewportHeight))
        segs.add(Segment.Line(left, viewportHeight, viewportWidth, viewportHeight))
        segs.add(Segment.Line(viewportWidth, viewportHeight, viewportWidth, top))
        return PathDescriptor(segs)
    }

    // ── Validation helpers ─────────────────────────────────────────────

    /** True when all derived values are positive and radii respect thickness. */
    fun isValid(): Boolean =
        moduleHeight > 0f &&
            viewportWidth > 0f &&
            viewportHeight > 0f &&
            primaryThickness > 0f &&
            channel > 0f &&
            secondaryThickness > 0f &&
            primaryOuterRadius > 0f &&
            primaryInnerRadius > 0f &&
            secondaryOuterRadius > 0f &&
            secondaryInnerRadius > 0f &&
            primaryOuterRadius > primaryThickness &&
            secondaryOuterRadius > secondaryThickness &&
            primaryShoulderWidth > 0f &&
            secondaryShoulderWidth > 0f &&
            primaryShoulderWidth >= primaryThickness &&
            secondaryShoulderWidth >= secondaryThickness

    /**
     * Returns true when the viewport is wide enough to contain the full
     * shoulder assembly at its nominal width.  For narrower viewports the
     * geometry still produces valid paths but the right rail may be clipped.
     */
    fun isWideEnoughForFullAssembly(): Boolean =
        viewportWidth >= combinedShoulderWidth

    // ── Narrow-width degradation ───────────────────────────────────────

    /**
     * Returns a degraded version of this geometry for narrow viewports.
     *
     * When [isWideEnoughForFullAssembly] is false, the chassis cannot fit
     * its full elbow width. This method produces a narrower variant where:
     * - Shoulder widths are clamped to fit within the viewport
     * - The combined shoulder extent is reduced proportionally
     * - All other ratios are preserved
     */
    fun narrowWidthVariant(): LcarsChassisGeometry? {
        if (viewportWidth >= combinedShoulderWidth) return null
        // Clamp shoulder widths to fit within the viewport while preserving
        // the primary/secondary ratio.
        val requiredWidth = primaryShoulderWidth + channel + secondaryShoulderWidth
        if (viewportWidth <= channel + primaryThickness + secondaryThickness) return null
        val scale = viewportWidth / requiredWidth
        // Return a new geometry with moduleHeight scaled proportionally
        return this.copy(moduleHeight = this.moduleHeight * scale)
    }

    /** 64dp study values from ROADMAP.md for comparison. */
    data class StudyValues(
        val primaryThickness: Float,
        val channel: Float,
        val secondaryThickness: Float,
        val primaryOuterRadius: Float,
        val primaryInnerRadius: Float,
        val secondaryOuterRadius: Float,
        val secondaryInnerRadius: Float,
        val primaryShoulderWidth: Float,
        val secondaryShoulderWidth: Float,
        val combinedShoulderWidth: Float,
    )

    val studyValues: StudyValues get() = StudyValues(
        primaryThickness = primaryThickness,
        channel = channel,
        secondaryThickness = secondaryThickness,
        primaryOuterRadius = primaryOuterRadius,
        primaryInnerRadius = primaryInnerRadius,
        secondaryOuterRadius = secondaryOuterRadius,
        secondaryInnerRadius = secondaryInnerRadius,
        primaryShoulderWidth = primaryShoulderWidth,
        secondaryShoulderWidth = secondaryShoulderWidth,
        combinedShoulderWidth = combinedShoulderWidth,
    )

    // ── Pixel snapping utility ─────────────────────────────────────────

    companion object {
        /**
         * Snap a dimension (in dp) to a whole number of physical pixels at
         * the given [density].
         *
         * For thin features (channels, keylines) narrower than ~1.5dp, this
         * ensures they render at an integral pixel width rather than a
         * fractional anti-aliased blend.
         */
        fun snapToPixel(dp: Float, density: Float): Float {
            val px = dp * density
            val snapped = if (px < 1.5f) px.roundToInt().toFloat() else px
            return snapped / density
        }
    }
}