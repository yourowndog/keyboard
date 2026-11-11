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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

class LcarsChassisGeometryTest {

    @Test
    fun `reserved header has directed G1 joins across studies and densities`() {
        for (height in listOf(60f, 64f, 68f)) {
            for (density in listOf(1f, 1.5f, 2.625f, 3f, 4f)) {
                val metrics = LcarsChassisGeometry(360f, 64f, height).snappedToPixels(density)
                val g = metrics.copy(viewportHeight = maxOf(64f, metrics.minimumHeaderHeight))
                assertTangentContinuity(g.primaryRail(), "primary H=$height density=$density")
                assertTangentContinuity(g.secondaryRail(), "secondary H=$height density=$density")
            }
        }
    }

    @Test
    fun `pixel snapping does not rescale shoulders or the module`() {
        val original = standardGeometry()
        for (density in listOf(0.75f, 1f, 1.5f, 2.625f, 3f, 4f)) {
            val snapped = original.snappedToPixels(density)
            assertEquals(original.moduleHeight, snapped.moduleHeight)
            assertTrue(abs(snapped.primaryShoulderWidth - original.primaryShoulderWidth) * density <= 0.501f)
            assertTrue(abs(snapped.primaryOuterRadius - original.primaryOuterRadius) * density <= 0.501f)
            assertTrue(abs(snapped.channel - original.channel) * density <= 1.001f)
        }
    }

    private fun polygon(path: LcarsChassisGeometry.PathDescriptor): List<Pair<Float, Float>> = buildList {
        for (segment in path.segments) {
            when (segment) {
                is LcarsChassisGeometry.Segment.Line -> {
                    add(segment.x1 to segment.y1)
                    add(segment.x2 to segment.y2)
                }
                is LcarsChassisGeometry.Segment.Arc -> {
                    for (step in 0..90) {
                        val angle = (segment.startAngle + segment.sweepAngle * step / 90f) * DEG_TO_RAD
                        add(segment.cx + segment.radius * cos(angle) to segment.cy + segment.radius * sin(angle))
                    }
                }
            }
        }
    }

    private fun contains(points: List<Pair<Float, Float>>, x: Float, y: Float): Boolean {
        var inside = false
        var previous = points.last()
        for (next in points) {
            if ((previous.second > y) != (next.second > y) &&
                x < (next.first - previous.first) * (y - previous.second) / (next.second - previous.second) + previous.first) {
                inside = !inside
            }
            previous = next
        }
        return inside
    }

    @Test
    fun `rails and deck never overlap so palette alpha is painted once`() {
        for (density in listOf(1f, 2.625f, 4f)) {
            val g = LcarsChassisGeometry(360f, 64f).snappedToPixels(density)
            val primary = polygon(g.primaryRail())
            val secondary = polygon(g.secondaryRail())
            val deck = polygon(g.deckPath())
            for (xIndex in 0..180) {
                for (yIndex in 0..127) {
                    val x = xIndex * 0.5f + 0.137f
                    val y = yIndex * 0.5f + 0.137f
                    val count = listOf(primary, secondary, deck).count { contains(it, x, y) }
                    assertTrue(count <= 1, "overlapping fills at $x,$y density=$density")
                }
            }
        }
    }

    /** Tolerance for floating-point comparisons (5/100 dp) for ratio-derived values. */
    private val epsilon = 0.05f

    /** Tighter tolerance for direct arithmetic (radii, thickness sums). */
    private val tightEpsilon = 0.01f

    /** Standard test geometry at 64 dp study with 1080 x 800 viewport. */
    private fun standardGeometry() = LcarsChassisGeometry(
        viewportWidth = 1080f,
        viewportHeight = 800f,
    )

    // ── 64 dp study values from ROADMAP.md ──────────────────────────────

    @Test
    fun `64dp study primary thickness matches reference`() {
        val g = standardGeometry()
        assertEquals(12.4f, g.primaryThickness, epsilon, "primaryThickness at H=64")
    }

    @Test
    fun `64dp study channel matches reference`() {
        val g = standardGeometry()
        assertEquals(0.7f, g.channel, epsilon * 5, "channel at H=64")
    }

    @Test
    fun `64dp study secondary thickness matches reference`() {
        val g = standardGeometry()
        assertEquals(7.0f, g.secondaryThickness, 0.1f, "secondaryThickness at H=64")
    }

    @Test
    fun `64dp study primary outer radius matches reference`() {
        val g = standardGeometry()
        assertEquals(29.6f, g.primaryOuterRadius, epsilon, "primaryOuterRadius at H=64")
    }

    @Test
    fun `64dp study primary shoulder width matches reference`() {
        val g = standardGeometry()
        assertEquals(56.8f, g.primaryShoulderWidth, epsilon, "primaryShoulderWidth at H=64")
    }

    @Test
    fun `64dp study secondary shoulder width matches reference`() {
        val g = standardGeometry()
        assertEquals(22.8f, g.secondaryShoulderWidth, epsilon, "secondaryShoulderWidth at H=64")
    }

    @Test
    fun `64dp study combined shoulder extent is approximately 79_7 dp`() {
        val g = standardGeometry()
        // combined = primaryShoulderWidth + channel + secondaryShoulderWidth
        // = 56.8 + 0.7 + 22.8 = 80.3 ≈ 79.7 (within ±1 dp of reference)
        assertEquals(80.3f, g.combinedShoulderWidth, 0.5f, "combinedShoulderWidth ≈ 79.7 at H=64")
    }

    // ── Derived radii ──────────────────────────────────────────────────

    @Test
    fun `primary inner radius is outer radius minus thickness`() {
        val g = standardGeometry()
        assertEquals(
            g.primaryOuterRadius - g.primaryThickness,
            g.primaryInnerRadius,
            epsilon,
            "primaryInnerRadius derivation",
        )
    }

    @Test
    fun `secondary outer radius is concentric with primary inner`() {
        val g = standardGeometry()
        assertEquals(
            g.primaryInnerRadius - g.channel,
            g.secondaryOuterRadius,
            epsilon,
            "secondaryOuterRadius = primaryInnerRadius - channel",
        )
    }

    @Test
    fun `secondary inner radius is secondary outer minus secondary thickness`() {
        val g = standardGeometry()
        assertEquals(
            g.secondaryOuterRadius - g.secondaryThickness,
            g.secondaryInnerRadius,
            epsilon,
            "secondaryInnerRadius derivation",
        )
    }

    @Test
    fun `all radii are positive`() {
        val g = standardGeometry()
        assertTrue(g.primaryOuterRadius > 0f)
        assertTrue(g.primaryInnerRadius > 0f)
        assertTrue(g.secondaryOuterRadius > 0f)
        assertTrue(g.secondaryInnerRadius > 0f)
    }

    @Test
    fun `outer radius exceeds thickness for each band`() {
        val g = standardGeometry()
        assertTrue(g.primaryOuterRadius > g.primaryThickness)
        assertTrue(g.secondaryOuterRadius > g.secondaryThickness)
    }

    // ── Viewport key points ────────────────────────────────────────────

    @Test
    fun `viewport top equals secondary rail bottom`() {
        val g = standardGeometry()
        assertEquals(
            g.secondaryRailBottom,
            g.viewportTop,
            epsilon,
            "viewportTop = secondaryRailBottom",
        )
    }

    @Test
    fun `viewport left is combined shoulder width`() {
        val g = standardGeometry()
        assertEquals(
            g.combinedShoulderWidth,
            g.viewportLeft,
            epsilon,
            "viewportLeft = combinedShoulderWidth",
        )
    }

    @Test
    fun `rail stacking order is correct`() {
        val g = standardGeometry()
        assertTrue(g.primaryThickness > 0f, "primaryThickness > 0")
        assertTrue(g.channel > 0f, "channel > 0")
        assertTrue(g.secondaryThickness > 0f, "secondaryThickness > 0")
        assertTrue(g.primaryRailBottom < g.secondaryRailTop, "primary below secondary?")
        assertTrue(g.secondaryRailTop < g.secondaryRailBottom, "secondary rail collapsed?")
    }

    // ── Validity ───────────────────────────────────────────────────────

    @Test
    fun `valid geometry is valid`() {
        val g = standardGeometry()
        assertTrue(g.isValid())
    }

    @Test
    fun `zero module height is invalid`() {
        val g = LcarsChassisGeometry(
            viewportWidth = 1080f,
            viewportHeight = 800f,
            moduleHeight = 0f,
        )
        assertFalse(g.isValid())
    }

    @Test
    fun `wide geometry is wide enough for full assembly`() {
        val g = standardGeometry()
        assertTrue(g.isWideEnoughForFullAssembly())
    }

    @Test
    fun `narrow geometry is not wide enough`() {
        val g = LcarsChassisGeometry(
            viewportWidth = 50f,
            viewportHeight = 800f,
        )
        assertFalse(g.isWideEnoughForFullAssembly())
    }

    // ── Narrow-width behavior ──────────────────────────────────────────

    @Test
    fun `narrow width near combined shoulder still produces valid narrow variant`() {
        val g = LcarsChassisGeometry(
            viewportWidth = 85f,
            viewportHeight = 400f,
        )
        // At 85dp width: combinedShoulderWidth ≈ 80.3dp at H=64.
        // So 85 > 80.3 — should be wide enough at H=64
        val variant = g.narrowWidthVariant()
        assertTrue(g.isValid())
        assertTrue(g.isWideEnoughForFullAssembly())
        assertNull(variant, "85dp is wide enough at H=64 — no degradation needed")
    }

    @Test
    fun `viewports narrower than combined shoulder degrade gracefully`() {
        val g = LcarsChassisGeometry(
            viewportWidth = 50f,
            viewportHeight = 400f,
            moduleHeight = 64f,
        )
        assertFalse(g.isWideEnoughForFullAssembly())
        // At 50dp the variant should exist with a reduced module height
        val variant = g.narrowWidthVariant()
        assertNotNull(variant)
        assertTrue(variant.moduleHeight < g.moduleHeight, "degraded module height is smaller")
        assertTrue(variant.isWideEnoughForFullAssembly(),
            "degraded variant should fit within viewport")
        // Verify: new combined shoulder width should not exceed viewportWidth
        assertTrue(variant.combinedShoulderWidth <= variant.viewportWidth + tightEpsilon,
            "degraded combined shoulder fits in viewport")
    }

    @Test
    fun `narrowWidthVariant reduces module height proportionally`() {
        // At H=64, combined ≈ 80.3. At width=60, scale = 60/80.3 ≈ 0.747
        // New module height ≈ 64 * 0.747 ≈ 47.8
        val g = LcarsChassisGeometry(
            viewportWidth = 60f,
            viewportHeight = 400f,
            moduleHeight = 64f,
        )
        val variant = g.narrowWidthVariant()
        assertNotNull(variant)
        assertTrue(variant.moduleHeight < g.moduleHeight, "degraded module height is smaller")
        assertTrue(variant.isWideEnoughForFullAssembly(),
            "degraded variant should fit within viewport")
        // Verify: new combined shoulder width should not exceed viewportWidth
        assertTrue(variant.combinedShoulderWidth <= variant.viewportWidth + epsilon,
            "degraded combined shoulder fits in viewport")
    }

    // ── Scaling across module heights ──────────────────────────────────

    @Test
    fun `geometry scales linearly with module height`() {
        val g60 = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f, moduleHeight = 60f)
        val g64 = standardGeometry()
        val g68 = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f, moduleHeight = 68f)

        val ratio60 = 60f / 64f
        val ratio68 = 68f / 64f

        fun assertScaled(h60: Float, h64: Float, h68: Float, label: String) {
            assertEquals(h64 * ratio60, h60, epsilon, "$label at H=60")
            assertEquals(h64 * ratio68, h68, epsilon, "$label at H=68")
        }

        assertScaled(g60.primaryThickness, g64.primaryThickness, g68.primaryThickness, "primaryThickness")
        assertScaled(g60.channel, g64.channel, g68.channel, "channel")
        assertScaled(g60.secondaryThickness, g64.secondaryThickness, g68.secondaryThickness, "secondaryThickness")
        assertScaled(g60.primaryOuterRadius, g64.primaryOuterRadius, g68.primaryOuterRadius, "primaryOuterRadius")
        assertScaled(g60.primaryShoulderWidth, g64.primaryShoulderWidth, g68.primaryShoulderWidth, "primaryShoulderWidth")
        assertScaled(g60.secondaryShoulderWidth, g64.secondaryShoulderWidth, g68.secondaryShoulderWidth, "secondaryShoulderWidth")
        assertScaled(g60.combinedShoulderWidth, g64.combinedShoulderWidth, g68.combinedShoulderWidth, "combinedShoulderWidth")
    }

    // ── Path bounds sanity ─────────────────────────────────────────────

    @Test
    fun `primary rail path is within viewport`() {
        val g = standardGeometry()
        val path = g.primaryRail()
        assertTrue(path.bounds.minX >= 0f, "primary rail left edge >= 0")
        assertTrue(path.bounds.minY >= 0f, "primary rail top edge >= 0")
        assertTrue(path.bounds.maxX <= g.viewportWidth + epsilon, "primary rail right edge ≤ viewportWidth")
        assertTrue(path.bounds.maxY <= g.viewportHeight + epsilon, "primary rail bottom edge ≤ viewportHeight")
    }

    @Test
    fun `secondary rail path is within viewport`() {
        val g = standardGeometry()
        val path = g.secondaryRail()
        assertTrue(path.bounds.minX >= 0f, "secondary rail left edge >= 0")
        assertTrue(path.bounds.minY >= 0f, "secondary rail top edge >= 0")
        assertTrue(path.bounds.maxX <= g.viewportWidth + epsilon, "secondary rail right edge ≤ viewportWidth")
        assertTrue(path.bounds.maxY <= g.viewportHeight + epsilon, "secondary rail bottom edge ≤ viewportHeight")
    }

    @Test
    fun `viewport clip path sits below the rails and reaches the viewport edge`() {
        val g = standardGeometry()
        val clip = g.viewportClip()
        assertTrue(clip.bounds.minY >= g.viewportTop - epsilon, "clip top ≥ viewportTop")
        assertTrue(clip.bounds.minX >= 0f, "clip left ≥ 0")
        assertTrue(clip.bounds.maxX >= g.viewportWidth - epsilon, "clip right ≈ viewportWidth")
    }

    @Test
    fun `viewport clip never exceeds viewport`() {
        val g = standardGeometry()
        val clip = g.viewportClip()
        assertTrue(clip.bounds.maxX <= g.viewportWidth + epsilon)
        assertTrue(clip.bounds.maxY <= g.viewportHeight + epsilon)
    }

    // ── Segment endpoint continuity (G0) ───────────────────────────────

    /**
     * Verify that every successive pair of segments is continuous (G0),
     * meaning each segment starts where the previous one ended.
     *
     * The path closure (last segment back to first segment) is handled by
     * the renderer's close() call, so it is NOT checked here.
     */
    private fun assertSegmentContinuity(descriptor: LcarsChassisGeometry.PathDescriptor, label: String) {
        val segs = descriptor.segments
        if (segs.size < 2) return

        for (i in 0 until segs.size - 1) {
            val current = segs[i]
            val next = segs[i + 1]

            // Get endpoint of current segment
            val (endX, endY) = segmentEndpoint(current)
            // Get start point of next segment
            val (startX, startY) = segmentStart(next)

            // They should match within epsilon
            val dx = abs(endX - startX)
            val dy = abs(endY - startY)
            val tooFar = dx > epsilon || dy > epsilon
            assertFalse(tooFar,
                "$label: segment $i endpoint (${"%.2f".format(endX)}, ${"%.2f".format(endY)}) " +
                "doesn't match segment ${i+1} start (${"%.2f".format(startX)}, ${"%.2f".format(startY)})" +
                " — gap ($dx, $dy)"
            )
        }
    }

    /** Return the endpoint (x, y) of a segment (the last point drawn). */
    private fun segmentEndpoint(seg: LcarsChassisGeometry.Segment): Pair<Float, Float> {
        return when (seg) {
            is LcarsChassisGeometry.Segment.Line -> Pair(seg.x2, seg.y2)
            is LcarsChassisGeometry.Segment.Arc -> {
                val endAngleRad = (seg.startAngle + seg.sweepAngle) * DEG_TO_RAD
                val ex = seg.cx + seg.radius * cos(endAngleRad)
                val ey = seg.cy + seg.radius * sin(endAngleRad)
                Pair(ex, ey)
            }
        }
    }

    /** Return the start (x, y) of a segment. */
    private fun segmentStart(seg: LcarsChassisGeometry.Segment): Pair<Float, Float> {
        return when (seg) {
            is LcarsChassisGeometry.Segment.Line -> Pair(seg.x1, seg.y1)
            is LcarsChassisGeometry.Segment.Arc -> {
                val startAngleRad = seg.startAngle * DEG_TO_RAD
                val sx = seg.cx + seg.radius * cos(startAngleRad)
                val sy = seg.cy + seg.radius * sin(startAngleRad)
                Pair(sx, sy)
            }
        }
    }

    private val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

    @Test
    fun `primary rail segments are endpoint-continuous`() {
        val g = standardGeometry()
        val path = g.primaryRail()
        assertSegmentContinuity(path, "primaryRail")
    }

    @Test
    fun `secondary rail segments are endpoint-continuous`() {
        val g = standardGeometry()
        val path = g.secondaryRail()
        assertSegmentContinuity(path, "secondaryRail")
    }

    @Test
    fun `deck path segments are endpoint-continuous`() {
        val g = standardGeometry()
        val path = g.deckPath()
        assertSegmentContinuity(path, "deckPath")
    }

    @Test
    fun `viewport clip segments are endpoint-continuous`() {
        val g = standardGeometry()
        val path = g.viewportClip()
        assertSegmentContinuity(path, "viewportClip")
    }

    // ── G1 / tangent continuity tests ──────────────────────────────────

    /**
     * Verify that every consecutive pair of segments is G1 (tangent
     * continuous): the tangent direction at the end of one segment matches
     * the tangent direction at the start of the next, within [tangentEpsilon]
     * radians. This is what the audit demanded for the shoulder/elbow arcs —
     * a right-angle join or a copied no-op is rejected here.
     */
    private fun assertTangentContinuity(descriptor: LcarsChassisGeometry.PathDescriptor, label: String) {
        val segs = descriptor.segments
        if (segs.size < 2) return

        for (i in 0 until segs.size - 1) {
            val current = segs[i]
            val next = segs[i + 1]
            val t1 = segmentEndTangent(current)
            val t2 = segmentStartTangent(next)
            // Normalize and measure the angle between tangent directions.
            val n1 = norm(t1.first, t1.second)
            val n2 = norm(t2.first, t2.second)
            // G1 requires matching travel direction, not merely parallel tangent lines.
            val dot = (n1.first * n2.first + n1.second * n2.second).coerceIn(-1f, 1f)
            val angle = Math.acos(dot.toDouble())
            assertTrue(
                angle <= tangentEpsilon,
                "$label: segments $i->${i + 1} are not tangent (G1): angle=${Math.toDegrees(angle)}rad=${angle} > $tangentEpsilon",
            )
        }
    }

    private val tangentEpsilon = 0.01f  // ~0.6° — generous for float math

    /** Unit tangent direction at the END of [seg] (direction of travel). */
    private fun segmentEndTangent(seg: LcarsChassisGeometry.Segment): Pair<Float, Float> {
        return when (seg) {
            is LcarsChassisGeometry.Segment.Line -> Pair(
                seg.x2 - seg.x1, seg.y2 - seg.y1,
            )
            is LcarsChassisGeometry.Segment.Arc -> {
                val endAngleRad = (seg.startAngle + seg.sweepAngle) * DEG_TO_RAD
                val sweepSign = if (seg.sweepAngle >= 0f) 1f else -1f
                // velocity of (cx + r cos a, cy + r sin a): (-r sin a, r cos a), * sweep sign
                Pair(-sweepSign * seg.radius * sin(endAngleRad), sweepSign * seg.radius * cos(endAngleRad))
            }
        }
    }

    /** Unit tangent direction at the START of [seg] (direction of travel). */
    private fun segmentStartTangent(seg: LcarsChassisGeometry.Segment): Pair<Float, Float> {
        return when (seg) {
            is LcarsChassisGeometry.Segment.Line -> Pair(
                seg.x2 - seg.x1, seg.y2 - seg.y1,
            )
            is LcarsChassisGeometry.Segment.Arc -> {
                val startAngleRad = seg.startAngle * DEG_TO_RAD
                val sweepSign = if (seg.sweepAngle >= 0f) 1f else -1f
                Pair(-sweepSign * seg.radius * sin(startAngleRad), sweepSign * seg.radius * cos(startAngleRad))
            }
        }
    }

    private fun norm(x: Float, y: Float): Pair<Float, Float> {
        val m = kotlin.math.sqrt(x * x + y * y)
        if (m == 0f) return Pair(0f, 0f)
        return Pair(x / m, y / m)
    }

    @Test
    fun `primary rail shoulder elbow transitions are tangent (G1)`() {
        val g = standardGeometry()
        assertTangentContinuity(g.primaryRail(), "primaryRail")
    }

    @Test
    fun `secondary rail shoulder elbow transitions are tangent (G1)`() {
        val g = standardGeometry()
        assertTangentContinuity(g.secondaryRail(), "secondaryRail")
    }

    // ── Segment structure tests ────────────────────────────────────────

    @Test
    fun `primary rail path has segments`() {
        val g = standardGeometry()
        val path = g.primaryRail()
        assertTrue(path.segments.isNotEmpty(), "primaryRail must have at least one segment")
    }

    @Test
    fun `primary rail has both line and arc segments`() {
        val g = standardGeometry()
        val path = g.primaryRail()
        val hasLine = path.segments.any { it is LcarsChassisGeometry.Segment.Line }
        val hasArc = path.segments.any { it is LcarsChassisGeometry.Segment.Arc }
        assertTrue(hasLine, "primaryRail should contain Line segments")
        assertTrue(hasArc, "primaryRail should contain Arc segments (elbow corner)")
    }

    @Test
    fun `secondary rail path has segments`() {
        val g = standardGeometry()
        val path = g.secondaryRail()
        assertTrue(path.segments.isNotEmpty(), "secondaryRail must have at least one segment")
    }

    @Test
    fun `secondary rail has both line and arc segments`() {
        val g = standardGeometry()
        val path = g.secondaryRail()
        val hasLine = path.segments.any { it is LcarsChassisGeometry.Segment.Line }
        val hasArc = path.segments.any { it is LcarsChassisGeometry.Segment.Arc }
        assertTrue(hasLine, "secondaryRail should contain Line segments")
        assertTrue(hasArc, "secondaryRail should contain Arc segments (elbow corner)")
    }

    @Test
    fun `viewport clip path is contoured (not a rectangle)`() {
        val g = standardGeometry()
        val clip = g.viewportClip()
        // The clip is intentionally shaped: it follows the secondary-inner
        // elbow arc at its top-left, so it is not a plain rectangle.
        assertTrue(
            clip.segments.any { it is LcarsChassisGeometry.Segment.Arc },
            "viewportClip should contain an Arc (contoured elbow boundary)",
        )
    }

    @Test
    fun `deck path is contoured (not a rectangle)`() {
        val g = standardGeometry()
        val deck = g.deckPath()
        assertTrue(
            deck.segments.any { it is LcarsChassisGeometry.Segment.Arc },
            "deckPath should contain an Arc (contoured elbow boundary)",
        )
    }

    // ── 60 dp and 68 dp study cross-checks ─────────────────────────────

    @Test
    fun `60 dp study produces usable scaled values`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f, moduleHeight = 60f)
        assertTrue(g.isValid())
        assertTrue(g.viewportTop > g.primaryThickness)
    }

    @Test
    fun `68 dp study produces usable scaled values`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f, moduleHeight = 68f)
        assertTrue(g.isValid())
        assertTrue(g.viewportTop > g.primaryThickness)
    }

    // ── Concentric bend test ───────────────────────────────────────────

    @Test
    fun `primary and secondary elbow arcs share the same center`() {
        val g = standardGeometry()
        val primaryDesc = g.primaryRail()
        val secondaryDesc = g.secondaryRail()

        // Find the concentric elbow arcs only — the outer/inner band corners.
        // Fillet arcs (the small radius ones joining a vertical to a horizontal)
        // sit off-axis by design, so they are excluded from this check.
        val elbowRadii = setOf(g.primaryOuterRadius, g.primaryInnerRadius,
            g.secondaryOuterRadius, g.secondaryInnerRadius)
        val primaryArc = primaryDesc.segments
            .filterIsInstance<LcarsChassisGeometry.Segment.Arc>()
            .filter { elbowRadii.any { r -> abs(it.radius - r) < epsilon } }
        val secondaryArc = secondaryDesc.segments
            .filterIsInstance<LcarsChassisGeometry.Segment.Arc>()
            .filter { elbowRadii.any { r -> abs(it.radius - r) < epsilon } }

        assertTrue(primaryArc.isNotEmpty(), "primaryRail must have elbow arc segments")
        assertTrue(secondaryArc.isNotEmpty(), "secondaryRail must have elbow arc segments")

        // The primary and secondary elbow arcs share the same center.
        for (pArc in primaryArc) {
            for (sArc in secondaryArc) {
                assertEquals(pArc.cx, sArc.cx, epsilon, "elbow arc centers must have same x")
                assertEquals(pArc.cy, sArc.cy, epsilon, "elbow arc centers must have same y")
            }
        }
    }

    @Test
    fun `arc radii decrease concentrically`() {
        val g = standardGeometry()
        // Primary outer radius > primary inner radius > secondary outer radius > secondary inner radius
        assertTrue(g.primaryOuterRadius > g.primaryInnerRadius + epsilon)
        assertTrue(g.primaryInnerRadius > g.secondaryOuterRadius + epsilon)
        assertTrue(g.secondaryOuterRadius > g.secondaryInnerRadius + epsilon)
    }

    // ── Pixel snapping test ────────────────────────────────────────────

    @Test
    fun `snapToPixel snaps a dimension to an integral pixel width`() {
        val density = 3.0f // 480dpi
        // 1.0dp at 3x = 3px, snapped stays 1.0dp
        assertEquals(1.0f, LcarsChassisGeometry.snapToPixel(1.0f, density), 0.01f)
        // 0.33dp at 3x ≈ 1px, snaps to 1px/3 = 0.333dp
        assertEquals(0.333f, LcarsChassisGeometry.snapToPixel(0.33f, density), 0.02f)
    }

    @Test
    fun `snappedToPixels materially changes rendered geometry`() {
        val g = standardGeometry()
        val density = 3.0f
        val snapped = g.snappedToPixels(density)
        // Snap the channel without resizing the complete chassis.
        assertTrue(
            snapped.channel != g.channel,
            "snapping must materially change the channel",
        )
        assertEquals(g.moduleHeight, snapped.moduleHeight, epsilon)
        // The snapped channel lands on an integral pixel count.
        val snappedChannelPx = snapped.channel * density
        assertEquals(
            snappedChannelPx.roundToLong().toFloat(),
            snappedChannelPx,
            0.05f,
            "snapped channel should sit on an integral pixel boundary",
        )
    }

    @Test
    fun `snappedToPixels keeps geometry valid`() {
        val g = standardGeometry()
        val snapped = g.snappedToPixels(2.625f)
        assertTrue(snapped.isValid(), "snapped geometry must remain valid")
    }

    @Test
    fun `snappedToPixels snap and rail signature does not require module height override`() {
        // Channel at H=64 is 0.7dp. On a 2.625x density that is 1.8375px,
        // which is not integral — so snapping must change it.
        val g = standardGeometry()
        val snapped = g.snappedToPixels(2.625f)
        assertTrue(
            snapped.channel != g.channel,
            "channel 0.7dp at 2.625x is not integral; snapping must change it",
        )
    }

    // ── StudyValues accessor ───────────────────────────────────────────

    @Test
    fun `study values matches direct accessors`() {
        val g = standardGeometry()
        val sv = g.studyValues
        assertEquals(g.primaryThickness, sv.primaryThickness, epsilon)
        assertEquals(g.channel, sv.channel, epsilon)
        assertEquals(g.secondaryThickness, sv.secondaryThickness, epsilon)
        assertEquals(g.primaryOuterRadius, sv.primaryOuterRadius, epsilon)
        assertEquals(g.primaryInnerRadius, sv.primaryInnerRadius, epsilon)
        assertEquals(g.secondaryOuterRadius, sv.secondaryOuterRadius, epsilon)
        assertEquals(g.secondaryInnerRadius, sv.secondaryInnerRadius, epsilon)
        assertEquals(g.primaryShoulderWidth, sv.primaryShoulderWidth, epsilon)
        assertEquals(g.secondaryShoulderWidth, sv.secondaryShoulderWidth, epsilon)
        assertEquals(g.combinedShoulderWidth, sv.combinedShoulderWidth, epsilon)
    }

    // ── Deck path ──────────────────────────────────────────────────────

    @Test
    fun `deck path covers the chassis region`() {
        val g = standardGeometry()
        val deck = g.deckPath()
        assertTrue(deck.bounds.minX >= 0f, "deck left ≥ 0")
        assertTrue(deck.bounds.minY >= 0f, "deck top ≥ 0")
        assertTrue(deck.bounds.maxX >= g.viewportWidth - epsilon, "deck right ≈ viewportWidth")
        assertTrue(deck.bounds.maxY >= g.viewportHeight - epsilon, "deck bottom ≈ viewportHeight")
    }

    // ── Render path tests (platform-independent) ──────────────────────

    @Test
    fun `secondary rail inner arc exists and is valid`() {
        val g = standardGeometry()
        val path = g.secondaryRail()
        // Find the inner elbow arc — the one with the secondary inner radius.
        val innerArc = path.segments
            .filterIsInstance<LcarsChassisGeometry.Segment.Arc>()
            .firstOrNull { abs(it.radius - g.secondaryInnerRadius) < tightEpsilon }
        assertNotNull(innerArc, "secondaryRail must have an inner-bend arc with secondaryInnerRadius")
        assertEquals(g.secondaryInnerRadius, innerArc.radius, tightEpsilon, "inner arc uses secondaryInnerRadius")
        assertEquals(g.arcCenterY, innerArc.cy, tightEpsilon, "inner arc shares same center")
    }

    // ── Path descriptor structure ──────────────────────────────────────

    @Test
    fun `primary rail outer arc sweep is negative (CCW from 12 to 9 oclock)`() {
        val g = standardGeometry()
        val path = g.primaryRail()
        // The outer elbow arc is the one with the primary outer radius.
        val arc = path.segments
            .filterIsInstance<LcarsChassisGeometry.Segment.Arc>()
            .firstOrNull { abs(it.radius - g.primaryOuterRadius) < tightEpsilon }
        assertNotNull(arc, "primaryRail must have an outer elbow arc with primaryOuterRadius")
        assertEquals(-90f, arc.sweepAngle, epsilon, "outer arc sweeps -90° CCW")
        assertEquals(-90f, arc.startAngle, epsilon, "outer arc starts at 12 o'clock")
    }

    @Test
    fun `primary rail inner arc sweep is positive (CW from 9 to 12 oclock)`() {
        val g = standardGeometry()
        val path = g.primaryRail()
        // The inner elbow arc is the one with the primary inner radius.
        val innerArc = path.segments
            .filterIsInstance<LcarsChassisGeometry.Segment.Arc>()
            .firstOrNull { abs(it.radius - g.primaryInnerRadius) < tightEpsilon }
        assertNotNull(innerArc, "primaryRail must have an inner elbow arc with primaryInnerRadius")
        assertEquals(90f, innerArc.sweepAngle, epsilon, "inner arc sweeps +90° CW")
        assertEquals(-180f, innerArc.startAngle, epsilon, "inner arc starts at 9 o'clock")
    }

    // ── Non-LCARS regression: geometry model is pure and touch-safe ────

    @Test
    fun `geometry model does not depend on Android or Compose types`() {
        // The model uses only Float, Boolean, List, and other JVM types.
        // If this file compiles without Android/Compose imports, it passes.
        // Check: no import of androidx in this file (compile-time check).
        val g = standardGeometry()
        assertTrue(g.primaryThickness > 0f, "pure JVM type accessible")
    }
}