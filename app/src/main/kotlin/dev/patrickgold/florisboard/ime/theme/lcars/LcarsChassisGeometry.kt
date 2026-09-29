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

import kotlin.math.min
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pure geometry model for the LCARS asymmetric left-elbow chassis.
 *
 * All measurements are derived from a normalising [moduleHeight] H using
 * ratios recorded from the reference imagery (ROADMAP.md).
 *
 * The chassis produces four named paths:
 * - [primaryRail]    — outer, thicker band (shoulder + horizontal)
 * - [secondaryRail]  — inner, thinner band
 * - [deckPath]       — the keyboard deck area
 * - [viewportClip]   — the content region inside the chassis
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

    /** Combined shoulder extent (widest point of the elbow).  ~79.7 dp at H=64. */
    val combinedShoulderWidth: Float get() = primaryThickness + channel + secondaryThickness +
        max(primaryShoulderWidth, secondaryShoulderWidth) - min(primaryShoulderWidth, secondaryShoulderWidth)

    // ── Derived radii ──────────────────────────────────────────────────

    /** Inner radius of the primary corner. */
    val primaryInnerRadius: Float get() = primaryOuterRadius - primaryThickness

    /** Outside radius of the secondary corner (concentric with primary inner). */
    val secondaryOuterRadius: Float get() = primaryInnerRadius - channel
    // The spec says: secondaryOuterRadius = primaryOuterRadius - primaryThickness - channel

    /** Inner radius of the secondary corner. */
    val secondaryInnerRadius: Float get() = secondaryOuterRadius - secondaryThickness

    // ── Key reference points ───────────────────────────────────────────

    /** Top y-coordinate of the primary rail bottom edge. */
    val primaryRailBottom: Float get() = primaryThickness

    /** Top y-coordinate of the secondary rail top edge. */
    val secondaryRailTop: Float get() = primaryRailBottom + channel

    /** Top y-coordinate of the secondary rail bottom edge. */
    val secondaryRailBottom: Float get() = secondaryRailTop + secondaryThickness

    /** Left-edge inset for the viewport (widest shoulder point). */
    val viewportLeft: Float get() = max(primaryShoulderWidth, secondaryShoulderWidth)

    /** Top-edge inset for the viewport. */
    val viewportTop: Float get() = secondaryRailBottom

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
                        // Conservative: include the full arc bounding box
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

    // ── Factory methods ────────────────────────────────────────────────

    /**
     * Primary (outer) rail path.
     *
     * The primary band forms an L-shaped bracket on the left and top edges:
     * - a wide vertical shoulder on the left (primaryShoulderWidth)
     * - a thin horizontal rail along the top (primaryThickness)
     * - a rounded outer corner (primaryOuterRadius)
     * - the right edge of the horizontal rail clips at viewportWidth
     * - the bottom edge of the shoulder clips at viewportHeight
     */
    fun primaryRail(): PathDescriptor {
        val segs = mutableListOf<Segment>()
        val R = primaryOuterRadius
        val T = primaryThickness
        val SW = primaryShoulderWidth
        val VW = viewportWidth
        val VH = viewportHeight

        // Outer corner center
        val cx = R
        val cy = R
        // Inner corner center (same x, y as outer; inner radius differs)
        val icx = SW
        val icy = R

        // Build the path clockwise starting at the top-right of the horizontal rail.
        // We need to handle the L-shaped band as a filled shape, which means
        // tracing the outer boundary then the inner boundary (reverse direction).

        // --- Outer boundary (clockwise) ---

        // Top edge of horizontal rail: right -> left
        segs.add(Segment.Line(VW, 0f, cx, 0f))

        // Outer corner arc: quarter-circle CW from (cx, 0) to (0, cy)
        // startAngle = -90° (12 o'clock), sweepAngle = 90° CW
        segs.add(Segment.Arc(cx, cy, R, -90f, 90f))

        // Left edge of shoulder: top -> bottom
        segs.add(Segment.Line(0f, cy, 0f, VH))

        // Bottom edge of shoulder: left -> right
        segs.add(Segment.Line(0f, VH, SW, VH))

        // --- Inner boundary (counter-clockwise, upward) ---

        // Right (inner) edge of shoulder: bottom -> top
        segs.add(Segment.Line(SW, VH, SW, R))

        // Inner corner arc: quarter-circle CCW — but since our arc primitive
        // uses startAngle + sweepAngle, we trace from (SW, R) upward to (R, T).
        // The inner corner has center at (SW, cy) = (SW, R), radius = inner radius = R - T.
        // We arc from angle 0° (3 o'clock → right) sweeping -90° (CCW) to reach 9 o'clock.
        val innerR = primaryInnerRadius
        segs.add(Segment.Arc(SW, cy, innerR, 0f, -90f))

        // Bottom edge of horizontal rail: left -> right (from the inner corner endpoint)
        segs.add(Segment.Line(SW - innerR, R - T, VW, R - T))

        return PathDescriptor(segs)
    }

    /**
     * Secondary (inner) rail path.
     *
     * Concentric with the primary, separated by [channel].  The vertical shoulder
     * is narrower (secondaryShoulderWidth).  The outer radius derives from the
     * primary's inner geometry.
     */
    fun secondaryRail(): PathDescriptor {
        val segs = mutableListOf<Segment>()
        val T = primaryThickness
        val C = channel
        val ST = secondaryThickness
        val SW = secondaryShoulderWidth
        val VW = viewportWidth
        val VH = viewportHeight

        val outerR = secondaryOuterRadius
        val innerR = secondaryInnerRadius

        // Secondary corner center: axially offset so the outer arc aligns with
        // the secondary shoulder width at the left edge. Center x = SW + outerR
        // ensures the arc's leftmost extent meets the shoulder, while center y
        // sits at the rail top + outerR so the arc transitions cleanly.
        val sCx = SW + outerR
        val sCy = T + C + outerR

        // Outer boundary (clockwise): horizontal top edge, outer corner arc,
        // left shoulder edge, bottom edge
        segs.add(Segment.Line(VW, T + C, sCx - outerR, T + C))
        segs.add(Segment.Arc(sCx, sCy, outerR, -90f, 90f))
        segs.add(Segment.Line(sCx - outerR, sCy + outerR, 0f, VH))
        segs.add(Segment.Line(0f, VH, SW, VH))

        // Inner boundary (reverse direction): shoulder right edge up, then
        // horizontal rail bottom edge across.  The inner corner arc is skipped
        // for now because the shoulder width may not cleanly meet the inner
        // arc; a straight transition is visually acceptable at C1.
        segs.add(Segment.Line(SW, VH, SW, T + C + ST))
        segs.add(Segment.Line(SW, T + C + ST, VW, T + C + ST))

        return PathDescriptor(segs)
    }

    /**
     * Keyboard deck path — the area behind the keys, below the Smartbar.
     *
     * Defined as a rectangular region covering the lower portion of the viewport,
     * with the left shoulder extending into it.
     */
    fun deckPath(): PathDescriptor {
        val segs = mutableListOf<Segment>()
        // The deck starts below the Smartbar. In the geometry model,
        // the deck is approximately the lower portion (arbitrary split).
        // For a pure shape, the deck is simply the full keyboard area.
        // The distinction is handled at the rendering/layout level.
        // For now: full viewport bounds.
        segs.add(Segment.Line(viewportWidth, 0f, 0f, 0f))
        segs.add(Segment.Line(0f, 0f, 0f, viewportHeight))
        segs.add(Segment.Line(0f, viewportHeight, viewportWidth, viewportHeight))
        segs.add(Segment.Line(viewportWidth, viewportHeight, viewportWidth, 0f))
        return PathDescriptor(segs)
    }

    /**
     * Viewport clip path — the content region inside the chassis.
     *
     * Clipped inside the rails and to the right of the shoulder.
     */
    fun viewportClip(): PathDescriptor {
        val segs = mutableListOf<Segment>()
        // The viewport is the area below the rails and right of the shoulder.
        // Simple rectangular clip:
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
            secondaryShoulderWidth >= secondaryThickness &&
            viewportWidth >= combinedShoulderWidth

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
}