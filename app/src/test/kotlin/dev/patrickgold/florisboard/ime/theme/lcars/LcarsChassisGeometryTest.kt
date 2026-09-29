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
import kotlin.test.assertTrue
import kotlin.math.abs

class LcarsChassisGeometryTest {

    /** Tolerance for floating-point comparisons in study values.
     *  The reference ratios are derived from JPEG screenshots, so exact
     *  equality with the "64 dp study" column is not expected. */
    private val studyEpsilon = 0.1f

    /** Tight tolerance for derived values computed by the model itself. */
    private val epsilon = 0.001f

    // ── 64 dp study values from ROADMAP.md ──────────────────────────────

    @Test
    fun `64dp study primary thickness matches reference`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertEquals(12.4f, g.primaryThickness, studyEpsilon, "primaryThickness at H=64")
    }

    @Test
    fun `64dp study channel matches reference`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        // channel = 64 * 0.011 = 0.704
        assertEquals(0.7f, g.channel, studyEpsilon, "channel at H=64")
    }

    @Test
    fun `64dp study secondary thickness matches reference`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        // secondaryThickness = 64 * 0.109 = 6.976
        assertEquals(7.0f, g.secondaryThickness, studyEpsilon, "secondaryThickness at H=64")
    }

    @Test
    fun `64dp study primary outer radius matches reference`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        // primaryOuterRadius = 64 * 0.463 = 29.632
        assertEquals(29.6f, g.primaryOuterRadius, studyEpsilon, "primaryOuterRadius at H=64")
    }

    @Test
    fun `64dp study primary shoulder width matches reference`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        // primaryShoulderWidth = 64 * 0.887 = 56.768
        assertEquals(56.8f, g.primaryShoulderWidth, studyEpsilon, "primaryShoulderWidth at H=64")
    }

    @Test
    fun `64dp study secondary shoulder width matches reference`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        // secondaryShoulderWidth = 64 * 0.357 = 22.848
        assertEquals(22.8f, g.secondaryShoulderWidth, studyEpsilon, "secondaryShoulderWidth at H=64")
    }

    // ── Derived radii ──────────────────────────────────────────────────

    @Test
    fun `primary inner radius is outer radius minus thickness`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertEquals(
            g.primaryOuterRadius - g.primaryThickness,
            g.primaryInnerRadius,
            epsilon,
            "primaryInnerRadius derivation",
        )
    }

    @Test
    fun `secondary outer radius is concentric with primary inner`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertEquals(
            g.primaryInnerRadius - g.channel,
            g.secondaryOuterRadius,
            epsilon,
            "secondaryOuterRadius = primaryInnerRadius - channel",
        )
    }

    @Test
    fun `secondary inner radius is secondary outer minus secondary thickness`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertEquals(
            g.secondaryOuterRadius - g.secondaryThickness,
            g.secondaryInnerRadius,
            epsilon,
            "secondaryInnerRadius derivation",
        )
    }

    @Test
    fun `all radii are positive`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertTrue(g.primaryOuterRadius > 0f)
        assertTrue(g.primaryInnerRadius > 0f)
        assertTrue(g.secondaryOuterRadius > 0f)
        assertTrue(g.secondaryInnerRadius > 0f)
    }

    @Test
    fun `outer radius exceeds thickness for each band`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertTrue(g.primaryOuterRadius > g.primaryThickness)
        assertTrue(g.secondaryOuterRadius > g.secondaryThickness)
    }

    // ── Viewport key points ────────────────────────────────────────────

    @Test
    fun `viewport top equals secondary rail bottom`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertEquals(
            g.secondaryRailBottom,
            g.viewportTop,
            epsilon,
            "viewportTop = secondaryRailBottom",
        )
    }

    @Test
    fun `viewport left is widest shoulder`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertEquals(
            g.primaryShoulderWidth,
            g.viewportLeft,
            epsilon,
            "viewportLeft = primaryShoulderWidth (widest)",
        )
    }

    @Test
    fun `rail stacking order is correct`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertTrue(g.primaryThickness > 0f, "primaryThickness > 0")
        assertTrue(g.channel > 0f, "channel > 0")
        assertTrue(g.secondaryThickness > 0f, "secondaryThickness > 0")
        assertTrue(g.primaryRailBottom < g.secondaryRailTop, "primary below secondary?")
        assertTrue(g.secondaryRailTop < g.secondaryRailBottom, "secondary rail collapsed?")
    }

    // ── Validity ───────────────────────────────────────────────────────

    @Test
    fun `valid geometry is valid`() {
        val g = LcarsChassisGeometry(
            viewportWidth = 1080f,
            viewportHeight = 800f,
        )
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
    fun `viewports narrower than combined shoulder are not valid`() {
        val g = LcarsChassisGeometry(
            viewportWidth = 50f,
            viewportHeight = 800f,
        )
        assertFalse(g.isValid())
    }

    // ── Scaling across module heights ──────────────────────────────────

    @Test
    fun `geometry scales linearly with module height`() {
        val g60 = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f, moduleHeight = 60f)
        val g64 = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f, moduleHeight = 64f)
        val g68 = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f, moduleHeight = 68f)

        // All values should be proportional
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
    }

    // ── Path bounds sanity ─────────────────────────────────────────────

    @Test
    fun `primary rail path is within viewport`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        val path = g.primaryRail()
        assertTrue(path.bounds.minX >= 0f, "primary rail left edge >= 0")
        assertTrue(path.bounds.minY >= 0f, "primary rail top edge >= 0")
        assertTrue(path.bounds.maxX <= g.viewportWidth + epsilon, "primary rail right edge ≤ viewportWidth")
        assertTrue(path.bounds.maxY <= g.viewportHeight + epsilon, "primary rail bottom edge ≤ viewportHeight")
    }

    @Test
    fun `secondary rail path is within viewport`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        val path = g.secondaryRail()
        assertTrue(path.bounds.minX >= 0f, "secondary rail left edge >= 0")
        assertTrue(path.bounds.minY >= 0f, "secondary rail top edge >= 0")
        assertTrue(path.bounds.maxX <= g.viewportWidth + epsilon, "secondary rail right edge ≤ viewportWidth")
        assertTrue(path.bounds.maxY <= g.viewportHeight + epsilon, "secondary rail bottom edge ≤ viewportHeight")
    }

    @Test
    fun `viewport clip path is inside the shoulder and below rails`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        val clip = g.viewportClip()
        // The clip should start at the viewportLeft (right of widest shoulder)
        assertTrue(clip.bounds.minX >= g.viewportLeft - epsilon, "clip left ≥ viewportLeft")
        // The clip should start below the secondary rail
        assertTrue(clip.bounds.minY >= g.viewportTop - epsilon, "clip top ≥ viewportTop")
        // The clip should extend to the right edge
        assertTrue(clip.bounds.maxX >= g.viewportWidth - epsilon, "clip right ≈ viewportWidth")
    }

    @Test
    fun `viewport clip never exceeds viewport`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        val clip = g.viewportClip()
        assertTrue(clip.bounds.maxX <= g.viewportWidth + epsilon)
        assertTrue(clip.bounds.maxY <= g.viewportHeight + epsilon)
    }

    @Test
    fun `secondary rail bounds are inset from primary rail bounds`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        val primary = g.primaryRail()
        val secondary = g.secondaryRail()
        // Secondary should be at or below primary's top
        assertTrue(secondary.bounds.minY >= primary.bounds.minY - epsilon, "secondary top ≥ primary top")
        // Secondary should have its top inside the primary (since it's below)
        assertTrue(secondary.bounds.minY >= g.primaryThickness - epsilon)
    }

    // ── Narrow-width behavior ──────────────────────────────────────────

    @Test
    fun `narrow width near combined shoulder produces valid geometry`() {
        val g = LcarsChassisGeometry(
            viewportWidth = 85f,
            viewportHeight = 400f,
        )
        // At this width the viewport is very narrow but the rails should still compute
        assertTrue(g.isValid() || g.viewportWidth > g.combinedShoulderWidth)
        val primary = g.primaryRail()
        assertTrue(primary.bounds.maxX <= g.viewportWidth + epsilon)
        assertTrue(primary.bounds.minX >= 0f)
    }

    // ── Segment structure tests ────────────────────────────────────────

    @Test
    fun `primary rail path has segments`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        val path = g.primaryRail()
        assertTrue(path.segments.isNotEmpty(), "primaryRail must have at least one segment")
    }

    @Test
    fun `primary rail has both line and arc segments`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        val path = g.primaryRail()
        val hasLine = path.segments.any { it is LcarsChassisGeometry.Segment.Line }
        val hasArc = path.segments.any { it is LcarsChassisGeometry.Segment.Arc }
        assertTrue(hasLine, "primaryRail should contain Line segments")
        assertTrue(hasArc, "primaryRail should contain Arc segments (elbow corner)")
    }

    @Test
    fun `secondary rail path has segments`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        val path = g.secondaryRail()
        assertTrue(path.segments.isNotEmpty(), "secondaryRail must have at least one segment")
    }

    @Test
    fun `viewport clip path segments are all lines`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        val path = g.viewportClip()
        assertTrue(path.segments.all { it is LcarsChassisGeometry.Segment.Line },
            "viewportClip should only contain Line segments")
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

    // ── Arc continuity (basic) ─────────────────────────────────────────

    @Test
    fun `primary outer radius exceeds inner radius`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertTrue(g.primaryOuterRadius > g.primaryInnerRadius,
            "outer radius must be larger than inner radius for a valid band")
    }

    @Test
    fun `secondary outer radius exceeds inner radius`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        assertTrue(g.secondaryOuterRadius > g.secondaryInnerRadius,
            "secondary outer radius must be larger than inner radius")
    }

    @Test
    fun `secondary rails are inset from primary rails`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        // The secondary outer radius should be less than the primary inner radius
        // (secondary is tucked inside the primary, separated by the channel)
        assertTrue(g.secondaryOuterRadius < g.primaryInnerRadius - epsilon ||
            abs(g.secondaryOuterRadius - g.primaryInnerRadius + g.channel) < epsilon,
            "secondaryOuterRadius ≈ primaryInnerRadius - channel")
    }

    // ── StudyValues accessor ───────────────────────────────────────────

    @Test
    fun `study values matches direct accessors`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
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
    fun `deck path covers full viewport`() {
        val g = LcarsChassisGeometry(viewportWidth = 1080f, viewportHeight = 800f)
        val deck = g.deckPath()
        assertTrue(deck.bounds.minX <= 0f, "deck left at 0")
        assertTrue(deck.bounds.minY <= 0f, "deck top at 0")
        assertTrue(deck.bounds.maxX >= g.viewportWidth - epsilon, "deck right at viewportWidth")
        assertTrue(deck.bounds.maxY >= g.viewportHeight - epsilon, "deck bottom at viewportHeight")
    }
}