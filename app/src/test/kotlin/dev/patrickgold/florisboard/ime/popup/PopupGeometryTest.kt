package dev.patrickgold.florisboard.ime.popup

import dev.patrickgold.florisboard.lib.FlorisRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PopupGeometryTest {
    // A plated LCARS letter key: 100 wide, 110 tall (plate + cap), on a 1000-wide layout.
    private val desired = FlorisRect.new(left = 0f, top = 0f, right = 100f, bottom = 110f)
    private val layoutWidth = 1000f
    private val headroom = 60f // smartbar height
    private val gap = 4f

    private fun key(left: Float, top: Float) = FlorisRect.new(left = left, top = top, right = left + 100f, bottom = top + 110f)

    @Test
    fun `preview is a pill wider than the key and shorter than it`() {
        val pill = PopupGeometry.previewPill(key(450f, 300f), desired, layoutWidth, headroom, gap)
        assertTrue(pill.width > 100f)
        assertTrue(pill.width > pill.height)
        assertTrue(pill.height < 110f)
        // Centred on the key, sitting just above it.
        assertEquals(500f, (pill.left + pill.right) / 2f, 0.01f)
        assertEquals(300f - gap, pill.bottom, 0.01f)
    }

    @Test
    fun `top-row preview never starts above the visible headroom`() {
        val pill = PopupGeometry.previewPill(key(450f, 0f), desired, layoutWidth, headroom, gap)
        assertTrue(pill.top >= -headroom, "pill top ${pill.top} is clipped by the IME window")
        // It keeps its full height rather than being squashed.
        assertEquals(110f * PopupGeometry.PILL_HEIGHT_FACTOR, pill.height, 0.01f)
    }

    @Test
    fun `edge key previews stay inside the layout`() {
        val left = PopupGeometry.previewPill(key(0f, 300f), desired, layoutWidth, headroom, gap)
        val right = PopupGeometry.previewPill(key(900f, 300f), desired, layoutWidth, headroom, gap)
        assertEquals(0f, left.left, 0.01f)
        assertEquals(layoutWidth, right.right, 0.01f)
    }

    @Test
    fun `wide keys get a pill wider than themselves`() {
        val wide = FlorisRect.new(left = 300f, top = 300f, right = 600f, bottom = 410f)
        val pill = PopupGeometry.previewPill(wide, desired, layoutWidth, headroom, gap)
        assertTrue(pill.width > wide.width)
    }

    @Test
    fun `two-row strip slides down rather than leaving the window`() {
        val top = PopupGeometry.clampTop(bottom = -4f, height = 2 * 68f, headroom = headroom)
        assertEquals(-headroom, top, 0.01f)
        val free = PopupGeometry.clampTop(bottom = 300f, height = 2 * 68f, headroom = headroom)
        assertEquals(300f - 136f, free, 0.01f)
    }
}
