package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 03: flexible item growth.
 *
 * Growth is a declared property of an item, not of its position in a row. These tests pin that
 * distinction, because the historical layout expressed "the spacebar is wide" as a hard-coded
 * width of `5.00` on whichever key happened to carry code 32.
 */
class ItemGrowthTest {

    private val ALPHA = SemanticRowRole.ALPHA
    private val PRIMARY = SemanticRowRole.PRIMARY_ACTION

    /** Alpha establishes the unit; the primary action row consumes it and may grow into the rest. */
    private fun sharedReferenceInput(primary: GeometryRow, width: Double = 1000.0) =
        SolverFixtures.input(
            rows = listOf(SolverFixtures.uniformRow("alpha:0", ALPHA, 10), primary),
            width = width,
        ).copy(
            widthPolicy = RowWidthPolicy(
                sharedReference = SharedWidthReference(
                    measuredRoles = setOf(ALPHA),
                    consumerRoles = setOf(ALPHA, PRIMARY),
                ),
            ),
        )

    private fun row(vararg items: GeometryItem) =
        GeometryRow(stableId = "primary_action:0", role = PRIMARY, items = items.toList())

    private fun item(id: String, units: Double, grow: Double = 0.0) =
        GeometryItem(stableId = "primary_action:0#$id", widthUnits = units, growWeight = grow)

    private fun widths(geometry: SolvedGeometry, rowId: String): List<Int> =
        geometry.row(rowId)!!.items.map { it.bounds.right - it.bounds.left }

    @Test
    fun `a row with no growers is placed exactly as before`() {
        val fixed = row(item("a", 1.5), item("b", 1.0), item("c", 1.0))
        val geometry = SolverFixtures.solved(sharedReferenceInput(fixed))
        val row = geometry.row("primary_action:0")!!

        // 3.5 units of a 100px alpha unit, centered in 1000px of content.
        assertEquals(listOf(150, 100, 100), widths(geometry, "primary_action:0"))
        assertEquals(325, row.items.first().bounds.left)
    }

    @Test
    fun `a single grower absorbs the exact remaining width`() {
        val primary = row(
            item("tab", 1.5),
            item("comma", 1.0),
            item("space", 1.0, grow = 1.0),
            item("period", 1.0),
            item("enter", 1.5),
        )
        val geometry = SolverFixtures.solved(sharedReferenceInput(primary))
        val row = geometry.row("primary_action:0")!!

        // Fixed demand is 6 units of 100px. The remaining 400px all lands on Space, which is
        // therefore 500px wide — without 5.0 ever being written down as its width.
        assertEquals(listOf(150, 100, 500, 100, 150), widths(geometry, "primary_action:0"))
        assertEquals(row.bounds.left, row.items.first().bounds.left)
        assertEquals(row.bounds.right, row.items.last().bounds.right)
    }

    @Test
    fun `the grown row fills the content width exactly and shares every edge`() {
        val primary = row(
            item("tab", 1.5),
            item("comma", 1.0),
            item("space", 1.0, grow = 1.0),
            item("period", 1.0),
            item("enter", 1.5),
        )
        val geometry = SolverFixtures.solved(sharedReferenceInput(primary, width = 1081.0))
        val row = geometry.row("primary_action:0")!!

        assertEquals(geometry.content.left, row.items.first().bounds.left)
        assertEquals(geometry.content.right, row.items.last().bounds.right)
        row.items.zipWithNext { left, right ->
            assertEquals(left.bounds.right, right.bounds.left, "adjacent items must share an edge")
        }
        assertEquals(
            geometry.content.right - geometry.content.left,
            widths(geometry, "primary_action:0").sum(),
            "odd widths must still conserve the row",
        )
    }

    @Test
    fun `growth follows the weight, not the index`() {
        // The same five slots, but the grower moved from position 2 to position 0.
        val moved = row(
            item("space", 1.0, grow = 1.0),
            item("tab", 1.5),
            item("comma", 1.0),
            item("period", 1.0),
            item("enter", 1.5),
        )
        val geometry = SolverFixtures.solved(sharedReferenceInput(moved))
        assertEquals(listOf(500, 150, 100, 100, 150), widths(geometry, "primary_action:0"))
    }

    @Test
    fun `a changed composition reallocates the remainder`() {
        // A synthetic four-key primary row: the remainder differs, and Space still absorbs it.
        val swapped = row(item("tab", 1.5), item("space", 1.0, grow = 1.0), item("enter", 1.5))
        val geometry = SolverFixtures.solved(sharedReferenceInput(swapped))
        assertEquals(listOf(150, 700, 150), widths(geometry, "primary_action:0"))
    }

    @Test
    fun `multiple growers split the remainder in proportion to their weights`() {
        val twoGrowers = row(
            item("tab", 1.5),
            item("space", 1.0, grow = 3.0),
            item("pad", 1.0, grow = 1.0),
            item("enter", 1.5),
        )
        val geometry = SolverFixtures.solved(sharedReferenceInput(twoGrowers))

        // Fixed demand 5 units = 500px, leaving 500px split 3:1 → +375 and +125.
        assertEquals(listOf(150, 475, 225, 150), widths(geometry, "primary_action:0"))
        assertEquals(1000, widths(geometry, "primary_action:0").sum())
    }

    @Test
    fun `equal weights split the remainder evenly`() {
        val even = row(
            item("a", 1.0, grow = 2.0),
            item("b", 1.0, grow = 2.0),
        )
        val geometry = SolverFixtures.solved(sharedReferenceInput(even))
        assertEquals(listOf(500, 500), widths(geometry, "primary_action:0"))
    }

    @Test
    fun `fixed demand beyond the row budget is unsatisfiable rather than grown`() {
        val tooWide = row(
            item("a", 6.0),
            item("b", 6.0),
            item("space", 1.0, grow = 1.0),
        )
        val reasons = SolverFixtures.unsatisfiable(sharedReferenceInput(tooWide))
        assertTrue(
            reasons.any { it.contains("primary_action:0") },
            "the overflowing row must be named: $reasons",
        )
    }

    @Test
    fun `a negative grow weight is rejected`() {
        val reasons = SolverFixtures.unsatisfiable(
            sharedReferenceInput(row(item("a", 1.0), item("bad", 1.0, grow = -1.0))),
        )
        assertTrue(
            reasons.any { it.contains("grow weight") },
            "expected a grow-weight reason, got: $reasons",
        )
    }

    @Test
    fun `a non-finite grow weight is rejected`() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY)) {
            val reasons = SolverFixtures.unsatisfiable(
                sharedReferenceInput(row(item("a", 1.0), item("bad", 1.0, grow = bad))),
            )
            assertTrue(
                reasons.any { it.contains("grow weight") },
                "expected a grow-weight reason for $bad, got: $reasons",
            )
        }
    }

    @Test
    fun `a grower in a row that already fills its width is left alone`() {
        // No shared reference: the row's unit width is derived to fit, so there is no remainder.
        val input = SolverFixtures.input(
            rows = listOf(
                GeometryRow(
                    stableId = "primary_action:0",
                    role = PRIMARY,
                    items = listOf(
                        item("a", 1.0),
                        item("space", 1.0, grow = 1.0),
                        item("b", 1.0),
                    ),
                ),
            ),
            width = 900.0,
        )
        assertEquals(listOf(300, 300, 300), widths(SolverFixtures.solved(input), "primary_action:0"))
    }
}
