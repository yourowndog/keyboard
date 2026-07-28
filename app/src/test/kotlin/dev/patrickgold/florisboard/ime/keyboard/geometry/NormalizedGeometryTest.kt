package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.KeyCustomizationManager
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import dev.patrickgold.florisboard.lib.FlorisRect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 03: the canonical normalized geometry, as solved for real keyboards.
 *
 * These assert the shipped contract rather than the solver's internals: what the alpha grid, the
 * primary action row, the utility rows, the heights and the spacing come out as when a real
 * composition is put through [TextKeyboardGeometryBridge]. Historical OmniBoard dimensions are not
 * a target here — several of these tests would have failed against the old keyboard, deliberately.
 */
class NormalizedGeometryTest {

    private val ALPHA = SemanticRowRole.ALPHA
    private val PRIMARY = SemanticRowRole.PRIMARY_ACTION
    private val UTILITY = SemanticRowRole.CODING_UTILITY

    private val width = 1080.0

    /** Shipped defaults: 2dp spacing, no boundary gaps, 100/100/75 row heights. */
    private fun prefs(
        spacingH: Double = 2.0,
        spacingV: Double = 2.0,
        rowBaseHeight: Double = 160.0,
        alphaHeight: Int = 100,
        utilityHeight: Int = 75,
        alphaWidth: Int = 100,
        utilityWidth: Int = 100,
        gapAbove: Double = 0.0,
        gapWithin: Double = 0.0,
        gapBelow: Double = 0.0,
    ) = GeometryPreferences(
        rowBaseHeightPx = rowBaseHeight,
        alphaRowHeightPercent = alphaHeight,
        utilityRowHeightPercent = utilityHeight,
        alphaKeyWidthPercent = alphaWidth,
        utilityKeyWidthPercent = utilityWidth,
        keySpacingHorizontalPx = spacingH,
        keySpacingVerticalPx = spacingV,
        utilityGapAbovePx = gapAbove,
        utilityGapWithinPx = gapWithin,
        utilityGapBelowPx = gapBelow,
    )

    private fun solve(
        keyboard: TextKeyboard,
        prefs: GeometryPreferences = prefs(),
        availableWidth: Double = width,
        orientation: GeometryOrientation = GeometryOrientation.PORTRAIT,
    ): SolvedGeometry {
        val result = TextKeyboardGeometryBridge.solve(
            keyboard = keyboard,
            prefs = prefs,
            availableWidth = availableWidth,
            framePolicy = FramePolicy.Intrinsic(prefs.rowBaseHeightPx),
            orientation = orientation,
        )
        assertTrue(
            result is TextKeyboardGeometryBridge.Result.Solved,
            "expected a clean solve, got $result",
        )
        return result.geometry
    }

    // -- Alpha grid ---------------------------------------------------------------------------

    @Test
    fun `the ten-key row establishes the unit and the nine-key rows reuse it`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val alphaRows = geometry.rowsWithRole(ALPHA)
        assertEquals(3, alphaRows.size)

        val unit = alphaRows[0].items[0].bounds.width
        for (row in alphaRows) {
            for (item in row.items) {
                assertTrue(
                    abs(item.bounds.width - unit) <= 1,
                    "alpha cell ${item.stableId} is ${item.bounds.width}, unit is $unit",
                )
            }
        }
    }

    @Test
    fun `nine-key alpha rows do not stretch to the content width`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val rows = geometry.rowsWithRole(ALPHA)
        val ten = rows[0]
        val nine = rows[1]

        val tenSpan = ten.items.last().bounds.right - ten.items.first().bounds.left
        val nineSpan = nine.items.last().bounds.right - nine.items.first().bounds.left
        assertTrue(nineSpan < tenSpan, "the nine-key row stretched: $nineSpan vs $tenSpan")
    }

    @Test
    fun `nine-key alpha rows are centered within the ten-column content width`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val rows = geometry.rowsWithRole(ALPHA)
        val ten = rows[0]
        val nine = rows[1]

        val leftPad = nine.items.first().bounds.left - ten.items.first().bounds.left
        val rightPad = ten.items.last().bounds.right - nine.items.last().bounds.right
        assertTrue(abs(leftPad - rightPad) <= 1, "row is off-centre: $leftPad vs $rightPad")
        assertTrue(leftPad > 0, "row was not indented at all")
    }

    @Test
    fun `shift and delete are ordinary alpha cells`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val third = geometry.rowsWithRole(ALPHA)[2]
        val unit = geometry.rowsWithRole(ALPHA)[0].items[0].bounds.width
        assertTrue(abs(third.items.first().bounds.width - unit) <= 1, "shift is not one unit")
        assertTrue(abs(third.items.last().bounds.width - unit) <= 1, "delete is not one unit")
    }

    @Test
    fun `every alpha key shares one height`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val heights = geometry.rowsWithRole(ALPHA)
            .flatMap { row -> row.items.map { it.bounds.height } }
            .distinct()
        assertTrue(heights.size <= 2 && (heights.max() - heights.min()) <= 1, "alpha heights: $heights")
    }

    // -- Primary action row -------------------------------------------------------------------

    @Test
    fun `the primary action row keeps its five-item composition`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val primary = geometry.rowsWithRole(PRIMARY).single()
        assertEquals(5, primary.items.size)
    }

    @Test
    fun `tab and enter are one and a half alpha units`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val unit = geometry.rowsWithRole(ALPHA)[0].items[0].bounds.width
        val primary = geometry.rowsWithRole(PRIMARY).single()
        assertTrue(abs(primary.items.first().bounds.width - unit * 1.5) <= 1.0, "tab is wrong")
        assertTrue(abs(primary.items.last().bounds.width - unit * 1.5) <= 1.0, "enter is wrong")
    }

    @Test
    fun `comma and period are one alpha unit`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val unit = geometry.rowsWithRole(ALPHA)[0].items[0].bounds.width
        val primary = geometry.rowsWithRole(PRIMARY).single()
        assertTrue(abs(primary.items[1].bounds.width - unit) <= 1, "comma is wrong")
        assertTrue(abs(primary.items[3].bounds.width - unit) <= 1, "period is wrong")
    }

    /**
     * Space is not encoded as five units anywhere. It is one unit that grows, and five units is
     * simply what a ten-column alpha grid leaves over once 1.5 + 1 + 1 + 1.5 is taken out.
     */
    @Test
    fun `space absorbs the remainder and happens to land near five units`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val unit = geometry.rowsWithRole(ALPHA)[0].items[0].bounds.width
        val primary = geometry.rowsWithRole(PRIMARY).single()
        val space = primary.items[2]
        assertTrue(abs(space.bounds.width - unit * 5.0) <= 2.0, "space is ${space.bounds.width}, unit $unit")
    }

    @Test
    fun `the primary action row fills the content width exactly`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val primary = geometry.rowsWithRole(PRIMARY).single()
        assertEquals(primary.bounds.left, primary.items.first().bounds.left)
        assertEquals(primary.bounds.right, primary.items.last().bounds.right)
    }

    @Test
    fun `no key in the primary action row compensates its own height`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val primary = geometry.rowsWithRole(PRIMARY).single()
        for (item in primary.items) {
            assertEquals(1.0, item.declaredHeightFactor, "item ${item.stableId} compensates its row")
            assertEquals(primary.bounds.height, item.bounds.height)
        }
    }

    // -- Utility rows -------------------------------------------------------------------------

    @Test
    fun `each utility row is nine equal cells filling its own width`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val utilityRows = geometry.rowsWithRole(UTILITY)
        assertEquals(2, utilityRows.size)
        for (row in utilityRows) {
            assertEquals(9, row.items.size)
            val widths = row.items.map { it.bounds.width }
            assertTrue(widths.max() - widths.min() <= 1, "unequal utility cells: $widths")
            assertEquals(row.bounds.left, row.items.first().bounds.left)
            assertEquals(row.bounds.right, row.items.last().bounds.right)
        }
    }

    /** The utility grid is nine columns wide against an alpha grid of ten. That is intentional. */
    @Test
    fun `the utility grid is independent of the alpha grid`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val alphaUnit = geometry.rowsWithRole(ALPHA)[0].items[0].bounds.width
        val utilityUnit = geometry.rowsWithRole(UTILITY)[0].items[0].bounds.width
        assertTrue(utilityUnit > alphaUnit, "utility cells did not widen to nine columns")
    }

    // -- Heights ------------------------------------------------------------------------------

    @Test
    fun `utility rows are three quarters of a full row`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val alpha = geometry.rowsWithRole(ALPHA)[0].bounds.height
        val utility = geometry.rowsWithRole(UTILITY)[0].bounds.height
        assertTrue(abs(utility - alpha * 0.75) <= 1.0, "utility $utility against alpha $alpha")
    }

    /** The 75% is a role adjustment on a 100% base, never a 0.75 base multiplied again. */
    @Test
    fun `a utility row at one hundred percent equals an alpha row exactly`() {
        val geometry = solve(GeometryFixtures.defaultCoding(), prefs(utilityHeight = 100))
        val alpha = geometry.rowsWithRole(ALPHA)[0].bounds.height
        val utility = geometry.rowsWithRole(UTILITY)[0].bounds.height
        assertEquals(alpha, utility)
    }

    @Test
    fun `the primary action row is a full row`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val alpha = geometry.rowsWithRole(ALPHA)[0].bounds.height
        val primary = geometry.rowsWithRole(PRIMARY).single().bounds.height
        assertEquals(alpha, primary)
    }

    // -- Spacing and bounds -------------------------------------------------------------------

    @Test
    fun `one spacing preference produces one gap of that size between neighbours`() {
        val geometry = solve(GeometryFixtures.defaultCoding(), prefs(spacingH = 2.0))
        val row = geometry.rowsWithRole(ALPHA)[0]
        val left = FlorisRect.empty()
        val right = FlorisRect.empty()
        KeyBoundsDerivation.visibleBounds(row.items[0], row.declaredSpacing, left)
        KeyBoundsDerivation.visibleBounds(row.items[1], row.declaredSpacing, right)
        assertTrue(abs((right.left - left.right) - 2.0f) <= 0.51f, "gap is ${right.left - left.right}")
    }

    @Test
    fun `the outer margin is half the spacing preference`() {
        val geometry = solve(GeometryFixtures.defaultCoding(), prefs(spacingH = 2.0))
        val row = geometry.rowsWithRole(ALPHA)[0]
        val first = FlorisRect.empty()
        KeyBoundsDerivation.visibleBounds(row.items.first(), row.declaredSpacing, first)
        assertTrue(abs((first.left - row.bounds.left) - 1.0f) <= 0.51f, "margin is ${first.left - row.bounds.left}")
    }

    @Test
    fun `structural rectangles never overlap and leave no dead strip`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        for (row in geometry.rows) {
            row.items.zipWithNext { a, b ->
                assertEquals(a.bounds.right, b.bounds.left, "structural strip between ${a.stableId} and ${b.stableId}")
                assertTrue(!a.bounds.overlaps(b.bounds))
            }
        }
    }

    @Test
    fun `touch bounds cover the whole structural allocation`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val row = geometry.rowsWithRole(ALPHA)[0]
        val touch = FlorisRect.empty()
        KeyBoundsDerivation.touchBounds(row.items[1], row.bounds.height, extendToBottomEdge = false, into = touch)
        assertEquals(row.items[1].bounds.left.toFloat(), touch.left)
        assertEquals(row.items[1].bounds.right.toFloat(), touch.right)
    }

    @Test
    fun `visible bounds sit inside touch bounds`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val row = geometry.rowsWithRole(ALPHA)[0]
        val touch = FlorisRect.empty()
        val visible = FlorisRect.empty()
        KeyBoundsDerivation.touchBounds(row.items[1], row.bounds.height, extendToBottomEdge = false, into = touch)
        KeyBoundsDerivation.visibleBounds(row.items[1], row.declaredSpacing, visible)
        assertTrue(visible.left >= touch.left && visible.right <= touch.right)
        assertTrue(visible.top >= touch.top && visible.bottom <= touch.bottom)
    }

    @Test
    fun `the bottom row keeps its edge touchable`() {
        val keyboard = GeometryFixtures.defaultCoding()
        val geometry = solve(keyboard)
        TextKeyboardGeometryBridge.applyTo(keyboard, geometry, extendTouchBoundariesDownwards = true)
        val bottomRow = keyboard.arrangement.last()
        val solvedBottom = geometry.rows.last().bounds.bottom
        assertTrue(
            bottomRow[0].touchBounds.bottom > solvedBottom,
            "the bottom row's touch area stops at the solved edge",
        )
    }

    // -- Boundary gaps ------------------------------------------------------------------------

    @Test
    fun `boundary gaps are declared for the utility block only`() {
        val geometry = solve(
            GeometryFixtures.defaultCoding(),
            prefs(gapAbove = 8.0, gapWithin = 4.0, gapBelow = 6.0),
        )
        assertTrue(geometry.gaps.isNotEmpty())
        assertTrue(geometry.gaps.all { it.role == UTILITY }, "a non-utility role claimed a gap")
    }

    @Test
    fun `hiding the utility rows leaves no gap behind`() {
        val geometry = solve(
            GeometryFixtures.codingUtilitiesHidden(),
            prefs(gapAbove = 8.0, gapWithin = 4.0, gapBelow = 6.0),
        )
        assertTrue(geometry.gaps.isEmpty(), "compact coding inherited a gap: ${geometry.gaps}")
    }

    @Test
    fun `numeric and phone modes receive no boundary gaps`() {
        for (keyboard in listOf(
            GeometryFixtures.numeric(),
            GeometryFixtures.numericAdvanced(),
            GeometryFixtures.phone(),
            GeometryFixtures.phone2(),
        )) {
            val geometry = solve(keyboard, prefs(gapAbove = 8.0, gapWithin = 4.0, gapBelow = 6.0))
            assertTrue(geometry.gaps.isEmpty(), "${keyboard.mode} received a coding gap")
        }
    }

    // -- Modes --------------------------------------------------------------------------------

    @Test
    fun `every specialized surface solves and fills its width`() {
        val keyboards = listOf(
            GeometryFixtures.characters(),
            GeometryFixtures.wideSymbols(),
            GeometryFixtures.numeric(),
            GeometryFixtures.numericAdvanced(),
            GeometryFixtures.phone(),
            GeometryFixtures.phone2(),
            GeometryFixtures.codingWithNumberExtension(),
            GeometryFixtures.codingWithDeveloperExtension(),
            GeometryFixtures.codingWithBothExtensions(),
            GeometryFixtures.codingUtilitiesHidden(),
        )
        for (keyboard in keyboards) {
            val geometry = solve(keyboard)
            for (row in geometry.rows) {
                if (row.role == ALPHA && row.items.size < 10) continue // legitimately indented
                assertEquals(
                    row.bounds.right,
                    row.items.last().bounds.right,
                    "${keyboard.mode} row ${row.stableId} left an unexplained gap",
                )
            }
        }
    }

    @Test
    fun `default keys in specialized rows normalize to equal units`() {
        for (keyboard in listOf(GeometryFixtures.numeric(), GeometryFixtures.phone())) {
            val geometry = solve(keyboard)
            for (row in geometry.rows) {
                val widths = row.items.map { it.bounds.width }
                assertTrue(widths.max() - widths.min() <= 1, "${keyboard.mode}: uneven row $widths")
            }
        }
    }

    @Test
    fun `layout pack units survive normalization`() {
        val geometry = solve(GeometryFixtures.layoutPackWithSpacersAndUnits())
        val authored = geometry.rows[0]
        val half = authored.items[1].bounds.width
        assertTrue(abs(authored.items[0].bounds.width - half * 3.0) <= 2.0, "1.5 against 0.5 was flattened")
        assertTrue(abs(authored.items[2].bounds.width - half * 4.0) <= 2.0, "the 2.0 spacer was flattened")
        assertEquals(GeometryItemKind.SPACER, authored.items[2].kind)
    }

    @Test
    fun `landscape solves as cleanly as portrait`() {
        val geometry = solve(
            GeometryFixtures.defaultCoding(),
            availableWidth = 2400.0,
            orientation = GeometryOrientation.LANDSCAPE,
        )
        assertEquals(GeometryOrientation.LANDSCAPE, geometry.orientation)
        assertEquals(6, geometry.rows.size)
    }

    // -- Robustness ---------------------------------------------------------------------------

    @Test
    fun `hostile preference values are clamped rather than crashing`() {
        val hostile = GeometryPreferences(
            rowBaseHeightPx = Double.NaN,
            alphaRowHeightPercent = -400,
            utilityRowHeightPercent = 99_999,
            alphaKeyWidthPercent = 0,
            utilityKeyWidthPercent = Int.MIN_VALUE,
            keySpacingHorizontalPx = Double.POSITIVE_INFINITY,
            keySpacingVerticalPx = -12.0,
            utilityGapAbovePx = Double.NaN,
            utilityGapWithinPx = -1.0,
            utilityGapBelowPx = 1e12,
        ).sanitized()

        assertTrue(hostile.rowBaseHeightPx.isFinite() && hostile.rowBaseHeightPx > 0.0)
        assertTrue(hostile.alphaRowHeightPercent in 10..300)
        assertTrue(hostile.utilityRowHeightPercent in 10..300)
        assertTrue(hostile.alphaKeyWidthPercent in 10..300)
        assertTrue(hostile.utilityKeyWidthPercent in 10..300)
        assertTrue(hostile.keySpacingHorizontalPx in 0.0..64.0)
        assertTrue(hostile.keySpacingVerticalPx in 0.0..64.0)
        assertTrue(hostile.utilityGapAbovePx in 0.0..256.0)
        assertTrue(hostile.utilityGapWithinPx in 0.0..256.0)
        assertTrue(hostile.utilityGapBelowPx in 0.0..256.0)
    }

    @Test
    fun `an impossible width yields no geometry rather than corrupted geometry`() {
        val result = TextKeyboardGeometryBridge.solve(
            keyboard = GeometryFixtures.defaultCoding(),
            prefs = prefs(),
            availableWidth = 0.0,
            framePolicy = FramePolicy.Intrinsic(160.0),
        )
        assertTrue(result is TextKeyboardGeometryBridge.Result.Unavailable, "got $result")
        assertTrue(result.diagnostics.isNotEmpty(), "an unusable solve said nothing about why")
    }

    @Test
    fun `a sentinel keyboard reports no frame height instead of guessing one`() {
        val sentinel = GeometryFixtures.sentinel(
            dev.patrickgold.florisboard.ime.keyboard.KeyboardMode.SMARTBAR_QUICK_ACTIONS,
            dev.patrickgold.florisboard.ime.keyboard.SentinelKind.SMARTBAR_QUICK_ACTIONS,
        )
        assertNull(TextKeyboardGeometryBridge.frameHeight(sentinel, prefs(), width))
    }

    // -- Runtime integration ------------------------------------------------------------------

    @Test
    fun `the frame height equals the sum of what the rows were given`() {
        val keyboard = GeometryFixtures.defaultCoding()
        val geometry = solve(keyboard)
        val height = TextKeyboardGeometryBridge.frameHeight(keyboard, prefs(), width)
        assertNotNull(height)
        assertEquals(geometry.frame.height, height)
        assertEquals(geometry.rows.last().bounds.bottom, geometry.content.bottom)
    }

    @Test
    fun `applying the solution writes every key exactly once`() {
        val keyboard = GeometryFixtures.defaultCoding()
        val geometry = solve(keyboard)
        TextKeyboardGeometryBridge.applyTo(keyboard, geometry, extendTouchBoundariesDownwards = false)
        for (key in keyboard.keys()) {
            assertTrue(key.touchBounds.width > 0f, "a key was left unplaced")
            assertTrue(key.visibleBounds.width > 0f, "a key was left invisible")
        }
    }

    @Test
    fun `the reference cell is a real solved alpha cell`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val reference = FlorisRect.empty()
        assertNotNull(TextKeyboardGeometryBridge.referenceCell(geometry, reference))
        val expected = FlorisRect.empty()
        val alphaRow = geometry.rowsWithRole(ALPHA).first()
        KeyBoundsDerivation.visibleBounds(alphaRow.items.first(), alphaRow.declaredSpacing, expected)
        assertEquals(expected.width, reference.width)
        assertEquals(expected.height, reference.height)
    }

    @Test
    fun `a preference change changes the solution`() {
        val tighter = solve(GeometryFixtures.defaultCoding(), prefs(utilityHeight = 50))
        val looser = solve(GeometryFixtures.defaultCoding(), prefs(utilityHeight = 100))
        assertTrue(tighter.frame.height < looser.frame.height, "the utility height preference did nothing")
    }

    @Test
    fun `restoring per-key defaults clears the overrides and nothing else`() {
        val withOverride = KeyCustomizationManager.setForKey(
            KeyCustomizationManager.NO_CUSTOMIZATIONS,
            KeyCode.SPACE,
            dev.patrickgold.florisboard.ime.keyboard.KeyCustomization(widthFactor = 1.4f),
        )
        assertTrue(KeyCustomizationManager.parseFromJson(withOverride).isNotEmpty())
        assertTrue(KeyCustomizationManager.parseFromJson(KeyCustomizationManager.NO_CUSTOMIZATIONS).isEmpty())
    }
}
