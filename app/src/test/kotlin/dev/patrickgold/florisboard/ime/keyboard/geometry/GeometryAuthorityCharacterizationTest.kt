package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.SentinelKind
import dev.patrickgold.florisboard.ime.keyboard.computeFrameRowPartition
import dev.patrickgold.florisboard.ime.keyboard.computeKeyboardFrameHeight
import dev.patrickgold.florisboard.ime.keyboard.computeLayoutRowHeight
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Characterizes the two independent vertical-geometry authorities and the ways they disagree.
 *
 * The outer frame is computed by `FlorisImeSizing.keyboardUiHeight` (via
 * [computeKeyboardFrameHeight]) and the inner rows by `TextKeyboard.layout` (via
 * [computeLayoutRowHeight]). Stage 02 replaces both with one solver; these tests record what the
 * split currently produces so that change is visible.
 */
class GeometryAuthorityCharacterizationTest {

    private companion object {
        const val ROW_BASE = 50f
        const val ALPHA_FACTOR = 1.0f
        const val BOTTOM_FACTOR = 0.75f
        const val EPSILON = 0.001f
    }

    private fun assertClose(expected: Float, actual: Float, message: String = "") {
        assertTrue(
            abs(expected - actual) < EPSILON,
            "$message expected≈$expected but was $actual",
        )
    }

    /** Sums the heights the inner layout allocates to each row. */
    private fun innerAllocatedHeight(
        rowCount: Int,
        bottomModRowCount: Int,
        keyboardHeight: Float,
    ): Float {
        var total = 0f
        for (r in 0 until rowCount) {
            total += computeLayoutRowHeight(
                rowIndex = r,
                rowCount = rowCount,
                bottomModRowCount = bottomModRowCount,
                keyboardHeight = keyboardHeight,
                alphaRowHeightFactor = ALPHA_FACTOR,
                bottomRowHeightFactor = BOTTOM_FACTOR,
            )
        }
        return total
    }

    // -- Row partitioning -------------------------------------------------------------------

    @KNOWN_DEFECT(
        "Default Coding is 3 alpha + primary action + 2 utility rows, but the frame partitions it " +
            "as 3 alpha + 3 mod. Because the partition assumes exactly 3 alpha rows, the 6th row " +
            "is charged to topModRows and the FIRST row — a real alpha row — is given modifier " +
            "height. The partition is derived from counts alone and cannot see which row is which.",
    )
    @Test
    fun `default coding misattributes its top alpha row as a modifier row`() {
        val partition = computeFrameRowPartition(rowCount = 6, bottomModRowCount = 2)

        assertEquals(3, partition.alphaRows)
        assertEquals(3, partition.modRows)
        assertEquals(1, partition.topModRows, "an extension row is invented that does not exist")
    }

    @KNOWN_DEFECT(
        "The frame's notion of which rows are modifier rows is positional: topModRows rows at the " +
            "top and bottomModRowCount at the bottom. For default Coding that marks row 0 as a " +
            "mod row, so the inner layout gives an alpha row bottomRowHeightFactor.",
    )
    @Test
    fun `default coding gives its first alpha row modifier height`() {
        val rowCount = 6
        val heights = (0 until rowCount).map {
            computeLayoutRowHeight(it, rowCount, 2, 400f, ALPHA_FACTOR, BOTTOM_FACTOR)
        }

        // Rows 0, 4 and 5 are treated as modifier rows; rows 1-3 as alpha.
        assertClose(heights[0], heights[4], "row 0 is sized like a bottom mod row")
        assertClose(heights[0], heights[5], "row 0 is sized like a bottom mod row")
        assertTrue(heights[1] > heights[0], "the real alpha rows are taller than row 0")
    }

    @COMPATIBILITY("Extension rows above the standard 3 alpha rows are counted as top mod rows.")
    @Test
    fun `extension rows are counted as top mod rows`() {
        val oneExtension = computeFrameRowPartition(rowCount = 7, bottomModRowCount = 2)
        assertEquals(2, oneExtension.topModRows)
        assertEquals(4, oneExtension.modRows)

        val twoExtensions = computeFrameRowPartition(rowCount = 8, bottomModRowCount = 2)
        assertEquals(3, twoExtensions.topModRows)
        assertEquals(5, twoExtensions.modRows)
    }

    // -- The short-keyboard divergence ------------------------------------------------------

    @KNOWN_DEFECT(
        "Height conservation is violated for 4-row keyboards. The frame applies both height " +
            "factors and the gap total, while inner layout divides the height uniformly and " +
            "ignores the factors entirely.",
    )
    @Test
    fun `four row keyboards allocate rows uniformly while the frame applies height factors`() {
        val rowCount = 4
        val bottomModRowCount = 2

        // Outer authority: 2 alpha at 1.0 + 2 mod at 0.75.
        val frame = computeKeyboardFrameHeight(
            rawRowCount = rowCount,
            bottomModRowCount = bottomModRowCount,
            rowBaseHeight = ROW_BASE,
            alphaRowHeightFactor = ALPHA_FACTOR,
            bottomRowHeightFactor = BOTTOM_FACTOR,
            gapTotal = 0f,
        )
        assertClose(175f, frame, "frame height")

        // Inner authority: four uniform rows, factors ignored.
        val firstRow = computeLayoutRowHeight(0, rowCount, bottomModRowCount, frame, ALPHA_FACTOR, BOTTOM_FACTOR)
        val lastRow = computeLayoutRowHeight(3, rowCount, bottomModRowCount, frame, ALPHA_FACTOR, BOTTOM_FACTOR)
        assertClose(frame / 4f, firstRow, "first row height")
        assertClose(firstRow, lastRow, "last row height")

        // A modifier row is therefore laid out taller than the frame budgeted for it.
        val framedModRowHeight = ROW_BASE * BOTTOM_FACTOR
        assertNotEquals(framedModRowHeight, lastRow)
        assertTrue(lastRow > framedModRowHeight, "inner mod row is taller than the frame allowed")
    }

    @COMPATIBILITY("For 5+ row keyboards the inner allocation conserves the total height exactly.")
    @Test
    fun `five or more rows conserve total height`() {
        for (rowCount in 5..8) {
            val height = 400f
            val allocated = innerAllocatedHeight(rowCount, bottomModRowCount = 2, keyboardHeight = height)
            assertClose(height, allocated, "rowCount=$rowCount total allocation")
        }
    }

    @KNOWN_DEFECT(
        "Four-row keyboards conserve the height they are given, but the height they are given was " +
            "computed with a different rule — so the rows are internally consistent and externally wrong.",
    )
    @Test
    fun `four rows conserve the given height but not the framed row proportions`() {
        val height = 400f
        val allocated = innerAllocatedHeight(rowCount = 4, bottomModRowCount = 2, keyboardHeight = height)
        assertClose(height, allocated, "total allocation")

        // Every row is the same height, even though two of them are framed as 0.75-height mod rows.
        val heights = (0 until 4).map {
            computeLayoutRowHeight(it, 4, 2, height, ALPHA_FACTOR, BOTTOM_FACTOR)
        }
        assertEquals(1, heights.toSet().size, "expected all four rows to share one height")
    }

    // -- The empty / short sentinel divergence -----------------------------------------------

    @KNOWN_DEFECT(
        "The frame coerces the row count to at least 4, so a keyboard with no rows is still " +
            "framed as a four-row keyboard. Inner layout returns early and allocates nothing.",
    )
    @Test
    fun `empty keyboards are framed as four rows`() {
        val frame = computeKeyboardFrameHeight(
            rawRowCount = 0,
            bottomModRowCount = 2,
            rowBaseHeight = ROW_BASE,
            alphaRowHeightFactor = ALPHA_FACTOR,
            bottomRowHeightFactor = BOTTOM_FACTOR,
            gapTotal = 0f,
        )

        assertTrue(frame > 0f, "an empty keyboard still reserves height")
        assertClose(175f, frame, "empty keyboard frame")
    }

    @KNOWN_DEFECT("The row-count coercion applies to 1-, 2- and 3-row keyboards too.")
    @Test
    fun `short keyboards are framed as if they had four rows`() {
        val frames = (0..4).map { rowCount ->
            computeKeyboardFrameHeight(
                rawRowCount = rowCount,
                bottomModRowCount = 2,
                rowBaseHeight = ROW_BASE,
                alphaRowHeightFactor = ALPHA_FACTOR,
                bottomRowHeightFactor = BOTTOM_FACTOR,
                gapTotal = 0f,
            )
        }
        assertEquals(1, frames.toSet().size, "row counts 0..4 should all frame identically")
    }

    // -- Gaps ---------------------------------------------------------------------------------

    @KNOWN_DEFECT(
        "Gaps are added to the outer frame but the inner row allocation has no knowledge of them, " +
            "so the rows overflow the frame by exactly the gap total.",
    )
    @Test
    fun `gap total inflates the frame but not the inner allocation`() {
        val gapTotal = 18f
        val withGaps = computeKeyboardFrameHeight(6, 2, ROW_BASE, ALPHA_FACTOR, BOTTOM_FACTOR, gapTotal)
        val withoutGaps = computeKeyboardFrameHeight(6, 2, ROW_BASE, ALPHA_FACTOR, BOTTOM_FACTOR, 0f)

        assertClose(gapTotal, withGaps - withoutGaps, "gap contribution")

        // Inner layout consumes the whole frame, gaps included, leaving no room for them.
        val allocated = innerAllocatedHeight(rowCount = 6, bottomModRowCount = 2, keyboardHeight = withGaps)
        assertClose(withGaps, allocated, "inner allocation swallows the gap budget")
    }

    @KNOWN_DEFECT(
        "Setting bottomModRowCount = 0 does not produce a keyboard with no modifier rows. The " +
            "partition still charges the 4th row to topModRows, so compact Coding is billed for a " +
            "modifier row it does not have AND receives the full gap budget.",
    )
    @Test
    fun `zero bottom mod rows still yields a mod row and a gap budget`() {
        val partition = computeFrameRowPartition(rowCount = 4, bottomModRowCount = 0)
        assertEquals(1, partition.modRows, "a modifier row is invented")
        assertEquals(3, partition.alphaRows)

        val frame = computeKeyboardFrameHeight(
            rawRowCount = 4,
            bottomModRowCount = 0,
            rowBaseHeight = ROW_BASE,
            alphaRowHeightFactor = ALPHA_FACTOR,
            bottomRowHeightFactor = BOTTOM_FACTOR,
            gapTotal = 18f,
        )
        // 3 alpha at 1.0 + 1 mod at 0.75 + the full 18 of gaps.
        assertClose(ROW_BASE * 3 + ROW_BASE * BOTTOM_FACTOR + 18f, frame, "compact Coding frame")
        assertNotEquals(ROW_BASE * 4 * ALPHA_FACTOR, frame, "not four plain alpha rows")
    }

    @COMPATIBILITY("A 3-row keyboard genuinely has no mod rows and so receives no gap budget.")
    @Test
    fun `three row keyboards receive no gap budget`() {
        val partition = computeFrameRowPartition(rowCount = 3, bottomModRowCount = 0)
        assertEquals(0, partition.modRows)
        assertEquals(3, partition.alphaRows)
    }

    // -- Numeric / phone families ---------------------------------------------------------------

    @KNOWN_DEFECT(
        "Numeric and phone keyboards are four main-only rows whose keys all read as alpha, but " +
            "the frame splits them 2 alpha / 2 mod while inner layout makes them uniform.",
    )
    @Test
    fun `numeric and phone families are framed as two alpha plus two mod rows`() {
        val families = listOf(
            "numeric" to GeometryFixtures.numeric(),
            "numericAdvanced" to GeometryFixtures.numericAdvanced(),
            "phone" to GeometryFixtures.phone(),
            "phone2" to GeometryFixtures.phone2(),
        )

        for ((name, keyboard) in families) {
            assertEquals(4, keyboard.rowCount, "$name row count")

            val partition = computeFrameRowPartition(keyboard.rowCount, keyboard.bottomModRowCount)
            assertEquals(2, partition.alphaRows, "$name alpha rows")
            assertEquals(2, partition.modRows, "$name mod rows")

            // Every key reads as alpha regardless.
            assertTrue(
                keyboard.arrangement.all { row -> row.all { it.isAlpha } },
                "$name should report every key as alpha",
            )
        }
    }

    // -- isAlpha row inference --------------------------------------------------------------------

    @KNOWN_DEFECT(
        "A row is treated as alpha when any single key in it is alpha, so one stray alpha key " +
            "changes the whole row's width reference and spacing.",
    )
    @Test
    fun `a single alpha key makes an entire row alpha`() {
        val mostlyMod = GeometryFixtures.modRow(5).toMutableList()
        mostlyMod[2] = GeometryFixtures.key('a'.code, isAlpha = true)

        assertTrue(mostlyMod.any { it.isAlpha }, "row qualifies as alpha via one key")
        assertEquals(1, mostlyMod.count { it.isAlpha }, "only one key is actually alpha")
    }

    // -- Layout: ordered row/key sequence and bounds ------------------------------------------------

    private fun layoutOf(keyboard: TextKeyboard, width: Float = 1000f, height: Float = 400f): TextKeyboard {
        keyboard.layout(
            keyboardWidth = width,
            keyboardHeight = height,
            desiredKey = keyboard.arrangement.first().first(),
            extendTouchBoundariesDownwards = false,
            bottomRowHeightFactor = BOTTOM_FACTOR,
            alphaRowHeightFactor = ALPHA_FACTOR,
        )
        return keyboard
    }

    @COMPATIBILITY("Row order and per-row key counts survive layout unchanged.")
    @Test
    fun `layout preserves row and key ordering`() {
        val keyboard = GeometryFixtures.defaultCoding()
        val shapeBefore = keyboard.arrangement.map { it.size }

        layoutOf(keyboard)

        assertEquals(shapeBefore, keyboard.arrangement.map { it.size })
        assertEquals(listOf(10, 9, 9, 3, 7, 7), shapeBefore)
    }

    @COMPATIBILITY("Rows are stacked top to bottom without vertical overlap between row bands.")
    @Test
    fun `rows stack without vertical gaps or overlap`() {
        val keyboard = layoutOf(GeometryFixtures.defaultCoding())

        var expectedTop = 0f
        for (row in keyboard.arrangement) {
            // Use a key with default height factor so the band equals the row height.
            val plain = row.first { it.flayHeightFactor == 1.0f }
            assertClose(expectedTop, plain.touchBounds.top, "row top")
            expectedTop = plain.touchBounds.bottom
        }
        assertClose(400f, expectedTop, "rows fill the keyboard height")
    }

    @COMPATIBILITY("Visible bounds are inset from touch bounds by the configured spacing.")
    @Test
    fun `visible bounds are inset within touch bounds`() {
        val keyboard = GeometryFixtures.defaultCoding()
        keyboard.layout(
            keyboardWidth = 1000f,
            keyboardHeight = 400f,
            desiredKey = keyboard.arrangement.first().first(),
            extendTouchBoundariesDownwards = false,
            bottomRowHeightFactor = BOTTOM_FACTOR,
            alphaRowHeightFactor = ALPHA_FACTOR,
            alphaSpacingH = 4f,
            alphaSpacingV = 3f,
        )

        val key = keyboard.arrangement[1][0]
        assertTrue(key.visibleBounds.top > key.touchBounds.top, "visible top inset")
        assertTrue(key.visibleBounds.bottom < key.touchBounds.bottom, "visible bottom inset")
        assertTrue(key.visibleBounds.width <= key.touchBounds.width, "visible width within touch width")
    }

    @KNOWN_DEFECT(
        "Alpha keys receive an undocumented horizontal touch expansion of 20% of the horizontal " +
            "spacing, so touch bounds diverge from the structural allocation.",
    )
    @Test
    fun `alpha keys get a hidden horizontal touch expansion`() {
        val spacing = 10f
        val keyboard = GeometryFixtures.characters()
        keyboard.layout(
            keyboardWidth = 1000f,
            keyboardHeight = 400f,
            desiredKey = keyboard.arrangement.first().first(),
            extendTouchBoundariesDownwards = false,
            bottomRowHeightFactor = BOTTOM_FACTOR,
            alphaRowHeightFactor = ALPHA_FACTOR,
            alphaSpacingH = spacing,
        )

        val first = keyboard.arrangement[0][0]
        val second = keyboard.arrangement[0][1]

        // The row is centred within a margin of alphaSpacingH * 2, putting the first key at x=10,
        // then the expansion pulls it 20% of the spacing to the left.
        val expansion = spacing * 0.2f
        assertClose(spacing - expansion, first.touchBounds.left, "left key expands toward the edge")
        assertTrue(
            first.touchBounds.right > second.touchBounds.left,
            "adjacent alpha touch bounds overlap by the expansion",
        )
        assertClose(
            expansion * 2f,
            first.touchBounds.right - second.touchBounds.left,
            "overlap is twice the expansion",
        )
    }

    @KNOWN_DEFECT(
        "The space row is detected by literal key code 32 rather than by row semantics, and the " +
            "detection only applies to rows that are not already alpha rows.",
    )
    @Test
    fun `space row detection keys off literal code 32`() {
        val keyboard = GeometryFixtures.characters()
        val primaryRow = keyboard.arrangement.last()

        assertTrue(primaryRow.none { it.isAlpha }, "primary row has no alpha keys")
        assertTrue(primaryRow.any { it.computedData.code == 32 }, "primary row is found via code 32")

        // If any key in the row were alpha, the row would be treated as an alpha row instead and
        // the space-row branch would never run.
        primaryRow[0].isAlpha = true
        assertTrue(primaryRow.any { it.isAlpha }, "one alpha key reclassifies the space row")
    }

    @COMPATIBILITY("Downward touch extension grows only the last row's touch bounds.")
    @Test
    fun `downward touch extension applies to the last row only`() {
        val extended = GeometryFixtures.characters()
        extended.layout(
            keyboardWidth = 1000f,
            keyboardHeight = 400f,
            desiredKey = extended.arrangement.first().first(),
            extendTouchBoundariesDownwards = true,
            bottomRowHeightFactor = BOTTOM_FACTOR,
            alphaRowHeightFactor = ALPHA_FACTOR,
        )
        val plain = GeometryFixtures.characters()
        layoutOf(plain)

        val extendedLast = extended.arrangement.last().first()
        val plainLast = plain.arrangement.last().first()
        assertTrue(
            extendedLast.touchBounds.bottom > plainLast.touchBounds.bottom,
            "last row extends downwards",
        )

        val extendedFirst = extended.arrangement.first().first()
        val plainFirst = plain.arrangement.first().first()
        assertClose(plainFirst.touchBounds.bottom, extendedFirst.touchBounds.bottom, "first row unchanged")
    }

    @COMPATIBILITY("Popup anchoring consumes the key's visible bounds, which layout sets.")
    @Test
    fun `popup anchor inputs are populated from visible bounds`() {
        val keyboard = layoutOf(GeometryFixtures.defaultCoding())

        for (row in keyboard.arrangement) {
            for (key in row) {
                assertTrue(key.visibleBounds.width > 0f, "popup anchor needs a positive width")
                assertTrue(key.visibleBounds.height > 0f, "popup anchor needs a positive height")
            }
        }
    }

    @COMPATIBILITY("layout() is a no-op for keyboards with no rows.")
    @Test
    fun `layout leaves empty keyboards untouched`() {
        val sentinel = GeometryFixtures.sentinel(KeyboardMode.EDITING, SentinelKind.EDITING)

        sentinel.layout(
            keyboardWidth = 1000f,
            keyboardHeight = 400f,
            desiredKey = GeometryFixtures.key('a'.code, isAlpha = true),
            extendTouchBoundariesDownwards = false,
        )

        assertEquals(0, sentinel.rowCount)
    }

    @COMPATIBILITY("NaN dimensions are rejected without mutating any bounds.")
    @Test
    fun `layout ignores NaN dimensions`() {
        val keyboard = GeometryFixtures.characters()

        keyboard.layout(
            keyboardWidth = Float.NaN,
            keyboardHeight = 400f,
            desiredKey = keyboard.arrangement.first().first(),
            extendTouchBoundariesDownwards = false,
        )

        val key = keyboard.arrangement[0][0]
        assertClose(0f, key.touchBounds.right, "bounds remain untouched")
    }
}
