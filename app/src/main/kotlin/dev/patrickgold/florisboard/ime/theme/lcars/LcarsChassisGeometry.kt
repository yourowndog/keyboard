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

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure geometry model for the LCARS asymmetric left-elbow chassis.
 *
 * All measurements are derived from a normalising [moduleHeight] H using
 * ratios recorded from the reference imagery (ROADMAP / LCARS study).
 *
 * The chassis produces four named paths:
 * - [primaryRail]    — outer, thicker band (shoulder + horizontal)
 * - [secondaryRail]  — inner, thinner band, concentric with the primary
 * - [deckPath]       — the keyboard deck area behind the key field
 * - [viewportClip]   — the content region inside the chassis
 *
 * **Tangent continuity.** Every shoulder/elbow/arc transition is G1: the
 * joint between a straight segment and an arc, and between two arcs, has a
 * matching tangent direction (it leaves exactly where it arrived, as
 * smoothly as a straight line meets a circle at right angles to its
 * radius). This is guaranteed by construction — verticals are joined
 * through quarter-circle fillets and the arcs are concentric with the
 * elbow centre — rather than by post-processing endpoint coordinates.
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
    private val pixelDensity: Float? = null,
) {
    private fun feature(value: Float): Float = pixelDensity?.let {
        (value * it).roundToInt().coerceAtLeast(1) / it
    } ?: value
    // ── Ratio-derived measurements ─────────────────────────────────────

    /** Thickness of the primary (outer) rail band.  12.4 dp at H=64. */
    val primaryThickness: Float get() = feature(moduleHeight * 0.194f)

    /** Gap between primary and secondary rails.  ~0.7 dp at H=64. */
    val channel: Float get() = feature(moduleHeight * 0.011f)

    /** Thickness of the secondary (inner) rail band.  7.0 dp at H=64. */
    val secondaryThickness: Float get() = feature(moduleHeight * 0.109f)

    /** Outside bend radius of the primary corner.  ~29.6 dp at H=64. */
    val primaryOuterRadius: Float get() = feature(moduleHeight * 0.463f)

    /** Width of the vertical shoulder for the primary band.  ~56.8 dp at H=64. */
    val primaryShoulderWidth: Float get() = feature(moduleHeight * 0.887f)

    /** Width of the vertical shoulder for the secondary band.  ~22.8 dp at H=64. */
    val secondaryShoulderWidth: Float get() = feature(moduleHeight * 0.357f)

    /**
     * Combined shoulder extent (widest point of the elbow assembly).  ~79.7 dp at H=64.
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

    /** The shared x-coordinate of the concentric arc center. */
    val arcCenterX: Float get() = primaryOuterRadius

    /** The shared y-coordinate of the concentric arc center. */
    val arcCenterY: Float get() = primaryOuterRadius

    // ── Key reference points ───────────────────────────────────────────

    /** Top y-coordinate of the primary rail bottom edge. */
    val primaryRailBottom: Float get() = primaryThickness

    /** Top y-coordinate of the secondary rail top edge. */
    val secondaryRailTop: Float get() = primaryRailBottom + channel

    /** Bottom y-coordinate of the secondary rail. */
    val secondaryRailBottom: Float get() = secondaryRailTop + secondaryThickness

    /** Left-edge inset for the viewport (widest point of left-side structure). */
    val viewportLeft: Float get() = combinedShoulderWidth

    /** Top-edge inset for the viewport. */
    val viewportTop: Float get() = secondaryRailBottom

    // ── Internal construction parameters ──────────────────────────────

    /** Left edge of the secondary shoulder vertical portion. */
    private val secondaryShoulderLeft: Float get() = primaryShoulderWidth + channel

    /** Right edge of the secondary shoulder. */
    private val secondaryShoulderRight: Float get() = secondaryShoulderLeft + secondaryShoulderWidth

    /** Fillet radius used to join verticals to horizontals without a kink. */
    private val filletRadius: Float get() = feature(moduleHeight * 0.15625f)

    /** Fillet radius for the lower (deck-side) corners of the band. */
    private val lowerCornerRadius: Float get() = feature(moduleHeight * 0.125f)

    /** Minimum reserved header height before a shoulder segment reverses. */
    val minimumHeaderHeight: Float get() = arcCenterY + 2f * filletRadius + lowerCornerRadius + 1f


    /** X at 9 o'clock of the primary inner arc: arcCenterX - primaryInnerRadius = primaryThickness. */
    private val primaryInnerArcLeftX: Float get() = arcCenterX - primaryInnerRadius

    /** X at 9 o'clock of the secondary outer arc. */
    private val secondaryOuterArcLeftX: Float get() = arcCenterX - secondaryOuterRadius

    /** X at 9 o'clock of the secondary inner arc. */
    private val secondaryInnerArcLeftX: Float get() = arcCenterX - secondaryInnerRadius

    // ── Path segment descriptors ────────────────────────────────────────

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
    }

    // ── Primary rail path ──────────────────────────────────────────────

    /**
     * Primary (outer) rail path — a filled L-shaped band.
     *
     * Every straight-to-arc transition is tangent (G1) by construction:
     * the top edge meets the outer arc at its 12 o'clock point, the left
     * edge meets it at 9 o'clock, and the shoulder vertical is joined to
     * the inner arc through two quarter-circle fillets.
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
        val rf = filletRadius
        val r0 = lowerCornerRadius

        // ── Outer boundary (clockwise) ──
        // 1. Top edge: right → left, from (VW, 0) to (cx, 0)
        segs.add(Segment.Line(VW, 0f, cx, 0f))
        // 2. Outer arc: center (cx, cy), radius R, 12 o'clock → 9 o'clock (CCW)
        segs.add(Segment.Arc(cx, cy, R, -90f, -90f))
        // 3. Left edge: (0, cy) down to the lower fillet
        segs.add(Segment.Line(0f, cy, 0f, VH - r0))
        // 4. Lower-left fillet: turns vertical-down into horizontal-right
        segs.add(Segment.Arc(r0, VH - r0, r0, 180f, -90f))
        // 5. Bottom of shoulder: (r0, VH) right to the shoulder-corner fillet
        segs.add(Segment.Line(r0, VH, SW - r0, VH))
        // 6. Lower-right (shoulder-corner) fillet: turns horizontal-right into vertical-up
        segs.add(Segment.Arc(SW - r0, VH - r0, r0, 90f, -90f))

        // ── Inner boundary (counter-clockwise, back up) ──
        // 7. Right (inner) edge of shoulder: (SW, VH - r0) up to the upper fillet
        segs.add(Segment.Line(SW, VH - r0, SW, cy + 2f * rf))
        // Fillet A: vertical-up → horizontal-left, below the main elbow.
        segs.add(Segment.Arc(SW - rf, cy + 2f * rf, rf, 0f, -90f))
        // 9. Horizontal connector: left to the second fillet
        segs.add(Segment.Line(SW - rf, cy + rf, T + rf, cy + rf))
        // Fillet B: horizontal-left → vertical-up into the concentric inner arc.
        segs.add(Segment.Arc(T + rf, cy, rf, 90f, 90f))

        // 12. Inner arc: 9 o'clock → 12 o'clock (CW)
        segs.add(Segment.Arc(cx, cy, R - T, -180f, 90f))
        // 13. Top rail bottom edge: (cx, T) right to (VW, T)
        segs.add(Segment.Line(cx, T, VW, T))

        return PathDescriptor(segs)
    }

    // ── Secondary rail path ────────────────────────────────────────────

    /**
     * Secondary (inner) rail path — a filled L-shaped band concentric with
     * the primary and offset inward by [channel].
     *
     * Shoulders are narrower and sit to the right of the primary band;
     * the same fillet construction keeps every transition tangent (G1).
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
        val rf = filletRadius
        val r0 = lowerCornerRadius

        val outerR = secondaryOuterRadius
        val innerR = secondaryInnerRadius
        val secLeft = secondaryShoulderLeft
        val secRight = secondaryShoulderRight
        val x9 = secondaryOuterArcLeftX
        val x9in = secondaryInnerArcLeftX

        // Top ys
        val topOut = T + C
        val topIn = T + C + ST

        // ── Outer boundary (clockwise) ──
        // 1. Top edge of secondary rail: right → arc start
        segs.add(Segment.Line(VW, topOut, cx, topOut))
        // 2. Outer arc: 12 o'clock → 9 o'clock
        segs.add(Segment.Arc(cx, cy, outerR, -90f, -90f))
        // 3. From arc 9 o'clock point down the left edge of the band
        // Follow the primary inner boundary at a channel-width normal offset.
        // This widens the outer edge into the secondary shoulder instead of
        // filling across (and overlapping) the primary shoulder.
        segs.add(Segment.Arc(T + rf, cy, rf - C, 180f, -90f))
        segs.add(Segment.Line(T + rf, cy + rf - C, primaryShoulderWidth - rf, cy + rf - C))
        segs.add(Segment.Arc(primaryShoulderWidth - rf, cy + 2f * rf, rf + C, -90f, 90f))
        segs.add(Segment.Line(secLeft, cy + 2f * rf, secLeft, VH - r0))
        // 4. Lower-left fillet
        segs.add(Segment.Arc(secLeft + r0, VH - r0, r0, 180f, -90f))
        // 5. Bottom of secondary band (to the shoulder right edge corner)
        segs.add(Segment.Line(secLeft + r0, VH, secRight - r0, VH))
        // 6. Lower-right (shoulder-corner) fillet
        segs.add(Segment.Arc(secRight - r0, VH - r0, r0, 90f, -90f))

        // ── Inner boundary (counter-clockwise, back up) ──
        // 7. Right edge of secondary shoulder up to the fillet
        val innerFillet = rf - C - ST
        segs.add(Segment.Line(secRight, VH - r0, secRight, cy + innerFillet + rf))
        // 8. Fillet A
        segs.add(Segment.Arc(secRight - rf, cy + innerFillet + rf, rf, 0f, -90f))
        // 9. Horizontal connector
        segs.add(Segment.Line(secRight - rf, cy + innerFillet, T + rf, cy + innerFillet))
        // 10. Fillet B
        segs.add(Segment.Arc(T + rf, cy, innerFillet, 90f, 90f))

        // 12. Inner arc: 9 o'clock → 12 o'clock
        segs.add(Segment.Arc(cx, cy, innerR, -180f, 90f))
        // 13. Bottom of secondary rail (top-in y) right to the viewport edge
        segs.add(Segment.Line(cx, topIn, VW, topIn))

        return PathDescriptor(segs)
    }

    // ── Deck path ──────────────────────────────────────────────────────

    /**
     * Keyboard deck path — the opaque backing behind the rail bands and keys.
     *
     * Intentionally **shaped**, not a plain rectangle: its top-left corner
     * follows the secondary-inner elbow arc so the deck hugs the chassis
     * contour instead of cutting across it with a square corner.
     */
    fun deckPath(): PathDescriptor {
        val segs = mutableListOf<Segment>()
        val cx = arcCenterX
        val cy = arcCenterY
        val VW = viewportWidth
        val VH = viewportHeight
        val x9in = secondaryInnerArcLeftX
        val topIn = secondaryRailBottom

        segs.add(Segment.Line(VW, topIn, cx, topIn))
        // Contoured top-left: sweep the secondary-inner arc CCW from 12 o'clock to 9 o'clock
        segs.add(Segment.Arc(cx, cy, secondaryInnerRadius, -90f, -90f))
        // The backing stops at the inner shoulder; never paint deck alpha
        // underneath the primary/secondary rail bands.
        val rf = filletRadius
        val innerFillet = rf - channel - secondaryThickness
        segs.add(Segment.Arc(primaryThickness + rf, cy, innerFillet, 180f, -90f))
        segs.add(Segment.Line(primaryThickness + rf, cy + innerFillet,
            secondaryShoulderRight - rf, cy + innerFillet))
        segs.add(Segment.Arc(secondaryShoulderRight - rf, cy + innerFillet + rf, rf, -90f, 90f))
        segs.add(Segment.Line(secondaryShoulderRight, cy + innerFillet + rf, secondaryShoulderRight, VH))
        segs.add(Segment.Line(secondaryShoulderRight, VH, VW, VH))
        segs.add(Segment.Line(VW, VH, VW, topIn))
        return PathDescriptor(segs)
    }

    // ── Viewport clip path ─────────────────────────────────────────────

    /**
     * Viewport clip path — the content region inside the chassis rails.
     *
     * Intentionally shaped: the top-left boundary follows the secondary-inner
     * elbow arc, so child content is clipped to the chassis silhouette rather
     * than a rectangular placeholder.
     */
    fun viewportClip(): PathDescriptor {
        return deckPath()
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

    /** True when the viewport is wide enough to contain the full shoulder assembly. */
    fun isWideEnoughForFullAssembly(): Boolean =
        viewportWidth >= combinedShoulderWidth

    // ── Narrow-width degradation ───────────────────────────────────────

    /**
     * Returns a degraded version of this geometry for narrow viewports.
     */
    fun narrowWidthVariant(): LcarsChassisGeometry? {
        if (viewportWidth >= combinedShoulderWidth) return null
        val requiredWidth = primaryShoulderWidth + channel + secondaryShoulderWidth
        if (viewportWidth <= channel + primaryThickness + secondaryThickness) return null
        val scale = viewportWidth / requiredWidth
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

    // ── Pixel snapping ─────────────────────────────────────────────────

    /**
     * Converts this logical geometry to a pixel-aligned geometry at [density].
     *
     * Round named dimensions independently, then derive cumulative boundaries.
     * Thin channels occupy at least one physical pixel. The normalizing module
     * height stays unchanged: snapping a small channel must not resize the
     * whole shoulder assembly or alter the reserved content allocation.
     *
     * @return a geometry with pixel-aligned feature dimensions, or `this` for
     *         invalid density/geometry inputs.
     */
    fun snappedToPixels(density: Float): LcarsChassisGeometry {
        if (density <= 0f || !isValid()) return this
        if (!density.isFinite()) return this
        // Round the named dimensions, then derive cumulative boundaries from
        // them. Never rescale the whole chassis just to snap a sub-dp channel.
        return copy(pixelDensity = density)
    }

    companion object {
        /** Degrees-to-radians factor. */
        internal val DEG_TO_RAD: Float = (Math.PI / 180.0).toFloat()

        /**
         * Snap a dimension (in dp) to a whole number of physical pixels at
         * the given [density].
         */
        fun snapToPixel(dp: Float, density: Float): Float {
            if (density <= 0f) return dp
            val snapped = (dp * density).roundToInt().toFloat()
            return kotlin.math.max(0f, snapped / density)
        }
    }
}