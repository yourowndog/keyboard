package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Stage 02 contract for [KeyboardGeometrySolver].
 *
 * These are not characterization tests. Unlike the Stage 00 suite — which records what the legacy
 * authorities currently do, defects included — every assertion here is a requirement the solver is
 * expected to keep meeting. Where a requirement contradicts legacy behaviour, that is intentional
 * and [LegacyGeometryComparisonTest] classifies the difference.
 *
 * See `omniboard-artifacts/implementation/keyboard-geometry/02-shared-solver.md`.
 */
class KeyboardGeometrySolverTest {

    private companion object {
        val ALPHA = SemanticRowRole.ALPHA
        val PRIMARY = SemanticRowRole.PRIMARY_ACTION
        val UTILITY = SemanticRowRole.CODING_UTILITY
        val EXTENSION = SemanticRowRole.EXTENSION

        /** Coding's gap policy: a gap above the utility block, between its rows, and below it. */
        val CODING_GAPS = BoundaryGapPolicy(mapOf(UTILITY to RoleBlockGaps(above = 8.0, within = 4.0, below = 8.0)))

        /** Utility rows are shorter than entry rows. */
        val CODING_HEIGHTS = RowHeightPolicy(mapOf(UTILITY to 0.75, EXTENSION to 0.75))

        /** Entry rows define one grid; the primary action row aligns to it without widening it. */
        val CODING_WIDTHS = RowWidthPolicy(
            sharedReference = SharedWidthReference(
                measuredRoles = setOf(ALPHA, EXTENSION, SemanticRowRole.NUMERIC, SemanticRowRole.SYMBOL),
                consumerRoles = setOf(ALPHA, EXTENSION, SemanticRowRole.NUMERIC, SemanticRowRole.SYMBOL, PRIMARY),
            ),
        )
    }

    // -- Invariants ---------------------------------------------------------------------------

    /**
     * Asserts every invariant Stage 02 requires of a solved result, so each scenario below can
     * assert its own point without restating them.
     */
    private fun assertInvariants(geometry: SolvedGeometry) {
        assertTrue(geometry.frame.width >= 0 && geometry.frame.height >= 0, "frame must be non-negative")
        assertTrue(geometry.content.isContainedBy(geometry.frame), "content must lie inside the frame")

        // Rows and gaps tile the content area exactly: no overlap, no unallocated band, no drift.
        val bands = (geometry.rows.map { it.bounds } + geometry.gaps.map { it.bounds }).sortedBy { it.top }
        if (bands.isEmpty()) {
            assertEquals(geometry.content.top, geometry.content.bottom, "an empty keyboard allocates no content")
        } else {
            assertEquals(geometry.content.top, bands.first().top, "first band must start at the content top")
            assertEquals(geometry.content.bottom, bands.last().bottom, "last band must end at the content bottom")
            bands.zipWithNext { above, below ->
                assertEquals(above.bottom, below.top, "vertical bands must share an edge, not overlap or gap")
            }
        }

        for (row in geometry.rows) {
            assertTrue(row.bounds.height >= 0 && row.bounds.width >= 0, "row '${row.stableId}' must be non-negative")
            assertTrue(row.bounds.isContainedBy(geometry.content), "row '${row.stableId}' must fit the content area")
            for (item in row.items) {
                assertTrue(item.bounds.width >= 0, "item '${item.stableId}' must have non-negative width")
                assertTrue(
                    item.bounds.isContainedBy(row.bounds),
                    "item '${item.stableId}' must fit row '${row.stableId}'",
                )
            }
            row.items.zipWithNext { left, right ->
                assertEquals(
                    left.bounds.right,
                    right.bounds.left,
                    "items must share an edge, not overlap or drift",
                )
            }
        }

        // Frame height is the rows plus the declared gaps, subject only to the declared insets.
        val allocated = geometry.content.height
        val insetTotal = geometry.insets.vertical
        assertTrue(
            abs((geometry.frame.height - allocated) - insetTotal) <= 1.0,
            "frame height ${geometry.frame.height} must be content $allocated plus insets $insetTotal",
        )
    }

    // -- Row counts ---------------------------------------------------------------------------

    @Test
    fun `every row count from zero to eight solves and keeps the invariants`() {
        for (count in 0..8) {
            val geometry = SolverFixtures.solved(SolverFixtures.input(SolverFixtures.rowsOfCount(count)))
            assertInvariants(geometry)
            assertEquals(count, geometry.rows.size, "row count $count")
            assertEquals(
                (count * SolverFixtures.ROW_BASE_HEIGHT).toInt(),
                geometry.frame.height,
                "frame height for $count rows",
            )
        }
    }

    @Test
    fun `a keyboard with no rows is not coerced into having four`() {
        val geometry = SolverFixtures.solved(SolverFixtures.input(rows = emptyList()))
        assertInvariants(geometry)
        assertEquals(0, geometry.rows.size)
        assertEquals(0, geometry.frame.height, "no rows means no height, not four rows' worth")
    }

    @Test
    fun `row count alone does not determine geometry`() {
        val fourEntryRows = List(4) { SolverFixtures.uniformRow("alpha:$it", ALPHA, 10) }
        val threeEntryPlusUtility =
            List(3) { SolverFixtures.uniformRow("alpha:$it", ALPHA, 10) } +
                SolverFixtures.uniformRow("coding_utility:0", UTILITY, 7)

        fun solve(rows: List<GeometryRow>) = SolverFixtures.solved(
            SolverFixtures.input(rows).copy(rowHeightPolicy = CODING_HEIGHTS, gapPolicy = CODING_GAPS),
        )

        val uniform = solve(fourEntryRows)
        val mixed = solve(threeEntryPlusUtility)
        assertEquals(uniform.rows.size, mixed.rows.size, "both keyboards have four rows")
        assertNotEquals(
            uniform.frame.height,
            mixed.frame.height,
            "the same row count must not produce the same geometry when the roles differ",
        )
        assertTrue(uniform.gaps.isEmpty(), "no utility rows means no utility gaps")
        assertTrue(mixed.gaps.isNotEmpty(), "a utility row brings its declared gaps with it")
    }

    // -- Coding: utilities visible and hidden --------------------------------------------------

    private fun codingInput(rows: List<GeometryRow>) = SolverFixtures.input(rows).copy(
        rowHeightPolicy = CODING_HEIGHTS,
        gapPolicy = CODING_GAPS,
        widthPolicy = CODING_WIDTHS,
    )

    @Test
    fun `default coding places its gaps at the declared utility boundaries`() {
        val geometry = SolverFixtures.solved(codingInput(SolverFixtures.defaultCoding()))
        assertInvariants(geometry)

        // 3 alpha + primary at 1.0 units, 2 utility at 0.75, plus 8 + 4 + 8 of declared gap.
        assertEquals((4 * 60 + 2 * 45 + 20), geometry.frame.height)

        val gaps = geometry.gaps.sortedBy { it.bounds.top }
        assertEquals(
            listOf(GeometryGapKind.ABOVE_BLOCK, GeometryGapKind.WITHIN_BLOCK, GeometryGapKind.BELOW_BLOCK),
            gaps.map { it.kind },
        )
        assertEquals("primary_action", gaps[0].rowAbove)
        assertEquals("coding_utility:0", gaps[0].rowBelow)
        assertEquals("coding_utility:0", gaps[1].rowAbove)
        assertEquals("coding_utility:1", gaps[1].rowBelow)
        assertEquals("coding_utility:1", gaps[2].rowAbove)
        assertNull(gaps[2].rowBelow, "the trailing gap has no row below it")
        assertEquals(listOf(8, 4, 8), gaps.map { it.bounds.height })
    }

    @Test
    fun `hiding the utility rows produces compact coding, not text`() {
        val geometry = SolverFixtures.solved(codingInput(SolverFixtures.compactCoding()))
        assertInvariants(geometry)

        assertEquals(
            listOf(ALPHA, ALPHA, ALPHA, PRIMARY),
            geometry.rows.map { it.role },
            "the primary action row survives; it is neither alpha nor utility",
        )
        assertTrue(geometry.gaps.isEmpty(), "no utility rows means the utility gap budget is not charged")
        assertEquals(4 * 60, geometry.frame.height)
    }

    @Test
    fun `surviving row IDs are stable when utility visibility changes`() {
        val full = SolverFixtures.solved(codingInput(SolverFixtures.defaultCoding()))
        val compact = SolverFixtures.solved(codingInput(SolverFixtures.compactCoding()))

        val survivors = compact.rows.map { it.stableId }
        assertEquals(listOf("alpha:0", "alpha:1", "alpha:2", "primary_action"), survivors)
        assertTrue(
            full.rows.map { it.stableId }.containsAll(survivors),
            "hiding utilities must not renumber the rows that remain",
        )
        for (id in survivors) {
            assertEquals(full.row(id)!!.role, compact.row(id)!!.role, "role of '$id' must survive the change")
        }
    }

    @Test
    fun `an extension row does not renumber the alpha rows below it`() {
        val withExtension = SolverFixtures.solved(codingInput(SolverFixtures.codingWithExtension()))
        val without = SolverFixtures.solved(codingInput(SolverFixtures.defaultCoding()))
        assertInvariants(withExtension)

        assertEquals(EXTENSION, withExtension.rows.first().role)
        assertEquals(
            without.rows.map { it.stableId },
            withExtension.rows.drop(1).map { it.stableId },
            "inserting an extension row must not change any other row's ID",
        )
        assertEquals(45, withExtension.row("extension:0")!!.bounds.height, "extension rows take the declared 0.75")
    }

    // -- Specialized modes ---------------------------------------------------------------------

    @Test
    fun `the numeric family keeps its own role and uniform heights`() {
        val geometry = SolverFixtures.solved(codingInput(SolverFixtures.numeric()))
        assertInvariants(geometry)

        assertTrue(geometry.rows.all { it.role == SemanticRowRole.NUMERIC }, "numeric rows are not alpha rows")
        assertTrue(geometry.gaps.isEmpty(), "numeric keyboards declare no utility gaps")
        assertEquals(listOf(60, 60, 60, 60), geometry.rows.map { it.bounds.height })
    }

    @Test
    fun `the primary action row aligns to the entry grid without widening it`() {
        val geometry = SolverFixtures.solved(codingInput(SolverFixtures.defaultCoding()))
        val unitWidth = geometry.row("alpha:0")!!.items.first().bounds.width

        // The widest entry row has 10 units, so one unit is a tenth of the content width; the
        // 6-unit primary row is measured against the same unit rather than stretched to fill.
        val primary = geometry.row("primary_action")!!
        assertEquals(unitWidth, primary.items.first().bounds.width, "one unit is one unit in both rows")
        assertTrue(
            primary.items.last().bounds.right - primary.items.first().bounds.left < geometry.content.width,
            "the primary row is narrower than the content area, and centred within it",
        )

        // A utility row is not a consumer of the shared reference, so it fills the width itself.
        val utility = geometry.row("coding_utility:0")!!
        assertEquals(geometry.content.left, utility.items.first().bounds.left)
        assertEquals(geometry.content.right, utility.items.last().bounds.right)
    }

    // -- Orientation ---------------------------------------------------------------------------

    @Test
    fun `landscape widens the rows without changing their heights`() {
        val portrait = SolverFixtures.solved(codingInput(SolverFixtures.defaultCoding()))
        val landscape = SolverFixtures.solved(
            codingInput(SolverFixtures.defaultCoding())
                .copy(availableWidth = SolverFixtures.LANDSCAPE_WIDTH, orientation = GeometryOrientation.LANDSCAPE),
        )
        assertInvariants(landscape)

        assertEquals(GeometryOrientation.LANDSCAPE, landscape.orientation, "orientation is declared, not inferred")
        assertEquals(portrait.frame.height, landscape.frame.height, "width does not drive height")
        assertEquals(
            portrait.row("alpha:0")!!.items.first().bounds.width * 2,
            landscape.row("alpha:0")!!.items.first().bounds.width,
            "twice the width means twice the unit",
        )
    }

    // -- Preferences and overrides ---------------------------------------------------------------

    @Test
    fun `row height overrides scale only the roles they name`() {
        val geometry = SolverFixtures.solved(
            codingInput(SolverFixtures.defaultCoding()).copy(
                overrides = GeometryOverrides(rowHeightScaleByRole = mapOf(UTILITY to 0.5)),
            ),
        )
        assertInvariants(geometry)
        assertEquals(60, geometry.row("alpha:0")!!.bounds.height, "alpha rows are untouched")
        assertEquals(60, geometry.row("primary_action")!!.bounds.height, "the primary row is untouched")
        assertEquals(23, geometry.row("coding_utility:0")!!.bounds.height, "0.75 units at half scale of 60px")
    }

    @Test
    fun `a modest width override narrows and re-centres a row`() {
        val geometry = SolverFixtures.solved(
            codingInput(SolverFixtures.defaultCoding()).copy(
                overrides = GeometryOverrides(itemWidthScaleByRole = mapOf(ALPHA to 0.8)),
            ),
        )
        assertInvariants(geometry)

        val row = geometry.row("alpha:0")!!
        val band = row.items.last().bounds.right - row.items.first().bounds.left
        assertEquals((geometry.content.width * 0.8).toInt(), band, "the row is scaled to 80%")
        assertEquals(
            row.items.first().bounds.left - geometry.content.left,
            geometry.content.right - row.items.last().bounds.right,
            "and centred, so the side margins match",
        )
    }

    @Test
    fun `an extreme width override is reported rather than clipped`() {
        val reasons = SolverFixtures.unsatisfiable(
            codingInput(SolverFixtures.defaultCoding()).copy(
                overrides = GeometryOverrides(itemWidthScaleByRole = mapOf(UTILITY to 1.5)),
            ),
        )
        assertTrue(reasons.any { it.contains("coding_utility:0") }, "the offending row is named: $reasons")
    }

    @Test
    fun `an extreme height override is reported when it exceeds the frame cap`() {
        val reasons = SolverFixtures.unsatisfiable(
            codingInput(SolverFixtures.defaultCoding()).copy(
                framePolicy = FramePolicy.Intrinsic(SolverFixtures.ROW_BASE_HEIGHT, maxFrameHeight = 400.0),
                overrides = GeometryOverrides(rowHeightScaleByRole = mapOf(ALPHA to 2.0)),
            ),
        )
        assertTrue(reasons.single().contains("capped"), "the cap is named: $reasons")
    }

    // -- Asymmetry -------------------------------------------------------------------------------

    @Test
    fun `spacers occupy their declared units and keep their kind`() {
        val row = SolverFixtures.rowWithSpacer("alpha:0", ALPHA)
        val geometry = SolverFixtures.solved(SolverFixtures.input(listOf(row)))
        assertInvariants(geometry)

        val items = geometry.rows.single().items
        assertEquals(
            listOf(GeometryItemKind.KEY, GeometryItemKind.KEY, GeometryItemKind.SPACER, GeometryItemKind.KEY),
            items.map { it.kind },
        )
        // 1.5 : 0.5 : 2 : 1 of 1080px.
        assertEquals(listOf(324, 108, 432, 216), items.map { it.bounds.width })
    }

    @Test
    fun `changing one item's units reflows its neighbours`() {
        val baseline = SolverFixtures.solved(
            SolverFixtures.input(listOf(SolverFixtures.unitRow("alpha:0", ALPHA, listOf(1.0, 1.0, 1.0, 1.0)))),
        )
        val widened = SolverFixtures.solved(
            SolverFixtures.input(listOf(SolverFixtures.unitRow("alpha:0", ALPHA, listOf(2.0, 1.0, 1.0, 1.0)))),
        )
        assertInvariants(widened)

        assertNotEquals(
            baseline.item("alpha:0#1")!!.bounds.left,
            widened.item("alpha:0#1")!!.bounds.left,
            "widening the first item must move the second",
        )
        assertEquals(
            baseline.rows.single().items.last().bounds.right,
            widened.rows.single().items.last().bounds.right,
            "reflow redistributes the width; it does not add any",
        )
    }

    @Test
    fun `asymmetrical insets shift the content area without unbalancing the rows`() {
        val geometry = SolverFixtures.solved(
            SolverFixtures.input(SolverFixtures.defaultCoding())
                .copy(insets = GeometryInsets(left = 10.0, top = 6.0, right = 30.0, bottom = 14.0)),
        )
        assertInvariants(geometry)

        assertEquals(10, geometry.content.left)
        assertEquals(6, geometry.content.top)
        assertEquals(1050, geometry.content.right)
        assertEquals(geometry.frame.bottom - 14, geometry.content.bottom)
        assertEquals(1040, geometry.rows.first().bounds.width, "rows span the inset content area")
    }

    // -- Rounding and conservation -----------------------------------------------------------------

    @Test
    fun `widths that do not divide evenly still conserve the content width`() {
        val geometry = SolverFixtures.solved(
            SolverFixtures.input(
                rows = listOf(SolverFixtures.uniformRow("alpha:0", ALPHA, 3)),
                width = 1000.0,
            ),
        )
        assertInvariants(geometry)

        val items = geometry.rows.single().items
        assertEquals(listOf(0, 333, 667), items.map { it.bounds.left })
        assertEquals(1000, items.last().bounds.right, "the final edge rounds the exact total, so error cannot build")
        assertEquals(1000, items.sumOf { it.bounds.width }, "widths sum to the content width exactly")
    }

    @Test
    fun `heights that do not divide evenly still conserve the frame height`() {
        val geometry = SolverFixtures.solved(
            SolverFixtures.input(
                rows = SolverFixtures.rowsOfCount(7),
                framePolicy = FramePolicy.FitToHeight(1000.0),
            ),
        )
        assertInvariants(geometry)

        assertEquals(1000, geometry.frame.height)
        assertEquals(1000, geometry.rows.sumOf { it.bounds.height }, "seven rows share 1000px without drift")
    }

    @Test
    fun `a fitted frame absorbs the gaps rather than growing`() {
        val geometry = SolverFixtures.solved(
            codingInput(SolverFixtures.defaultCoding()).copy(framePolicy = FramePolicy.FitToHeight(500.0)),
        )
        assertInvariants(geometry)

        assertEquals(500, geometry.frame.height)
        assertEquals(
            500,
            geometry.rows.sumOf { it.bounds.height } + geometry.gaps.sumOf { it.bounds.height },
            "rows and gaps together are the frame; gaps are not charged twice",
        )
    }

    // -- Determinism ---------------------------------------------------------------------------------

    @Test
    fun `identical inputs produce identical results`() {
        val input = codingInput(SolverFixtures.codingWithExtension())
        assertEquals(KeyboardGeometrySolver.solve(input), KeyboardGeometrySolver.solve(input))
        assertEquals(
            KeyboardGeometrySolver.solve(input),
            KeyboardGeometrySolver.solve(codingInput(SolverFixtures.codingWithExtension())),
            "an equal input is the same input",
        )
    }

    // -- Unsatisfiable inputs ---------------------------------------------------------------------------

    @Test
    fun `non-finite and negative dimensions are rejected`() {
        assertTrue(
            SolverFixtures.unsatisfiable(SolverFixtures.input(SolverFixtures.defaultCoding(), width = Double.NaN))
                .single().contains("available width"),
        )
        assertTrue(
            SolverFixtures.unsatisfiable(SolverFixtures.input(SolverFixtures.defaultCoding(), width = -1.0))
                .any { it.contains("available width") },
        )
    }

    @Test
    fun `insets that leave no content width are rejected`() {
        val reasons = SolverFixtures.unsatisfiable(
            SolverFixtures.input(SolverFixtures.defaultCoding(), width = 100.0)
                .copy(insets = GeometryInsets(left = 60.0, right = 60.0)),
        )
        assertTrue(reasons.any { it.contains("no content width") }, reasons.toString())
    }

    @Test
    fun `duplicate row and item IDs are rejected`() {
        val duplicateRows = SolverFixtures.unsatisfiable(
            SolverFixtures.input(
                listOf(
                    SolverFixtures.uniformRow("alpha:0", ALPHA, 3),
                    SolverFixtures.uniformRow("alpha:0", ALPHA, 3),
                ),
            ),
        )
        assertTrue(duplicateRows.any { it.contains("duplicate row ID") }, duplicateRows.toString())

        val duplicateItems = SolverFixtures.unsatisfiable(
            SolverFixtures.input(
                listOf(
                    GeometryRow("alpha:0", ALPHA, listOf(GeometryItem("k", 1.0), GeometryItem("k", 1.0))),
                ),
            ),
        )
        assertTrue(duplicateItems.any { it.contains("duplicate item ID") }, duplicateItems.toString())
    }

    @Test
    fun `a fitted frame too small for its own gaps is rejected`() {
        val reasons = SolverFixtures.unsatisfiable(
            codingInput(SolverFixtures.defaultCoding()).copy(framePolicy = FramePolicy.FitToHeight(10.0)),
        )
        assertTrue(reasons.single().contains("gaps"), reasons.toString())
    }

    @Test
    fun `a width reference that is defined by a role it does not lay out is rejected`() {
        val reasons = SolverFixtures.unsatisfiable(
            SolverFixtures.input(SolverFixtures.defaultCoding()).copy(
                widthPolicy = RowWidthPolicy(
                    sharedReference = SharedWidthReference(
                        measuredRoles = setOf(ALPHA),
                        consumerRoles = setOf(PRIMARY),
                    ),
                ),
            ),
        )
        assertTrue(reasons.any { it.contains("do not consume it") }, reasons.toString())
    }

    @Test
    fun `a non-positive override is rejected rather than silently collapsing a row`() {
        val reasons = SolverFixtures.unsatisfiable(
            SolverFixtures.input(SolverFixtures.defaultCoding()).copy(
                overrides = GeometryOverrides(rowHeightScaleByRole = mapOf(ALPHA to 0.0)),
            ),
        )
        assertTrue(reasons.all { it.contains("must be finite and positive") }, reasons.toString())
    }

    @Test
    fun `every failed constraint is reported, not just the first`() {
        val reasons = SolverFixtures.unsatisfiable(
            SolverFixtures.input(
                listOf(
                    SolverFixtures.uniformRow("", ALPHA, 3),
                    GeometryRow("alpha:1", ALPHA, listOf(GeometryItem("", Double.NaN))),
                ),
            ),
        )
        assertTrue(reasons.size >= 3, "expected several reasons but got $reasons")
    }
}
