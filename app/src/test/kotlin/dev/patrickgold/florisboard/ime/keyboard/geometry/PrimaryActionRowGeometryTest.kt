package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Contract for the independently adjustable primary-action (spacebar) row. */
class PrimaryActionRowGeometryTest {

    private val primary = SemanticRowRole.PRIMARY_ACTION

    private fun input(prefs: GeometryPreferences): GeometrySolverInput =
        KeyboardGeometryPolicy.buildInput(
            rows = SolverFixtures.defaultCoding(),
            prefs = prefs,
            availableWidth = 1000.0,
            framePolicy = FramePolicy.Intrinsic(prefs.rowBaseHeightPx),
        )

    @Test
    fun `primary height inherits a customized alpha height until independently overridden`() {
        val inherited = SolverFixtures.solved(
            input(
                GeometryPreferences(
                    rowBaseHeightPx = 60.0,
                    alphaRowHeightPercent = 125,
                ),
            ),
        )
        assertEquals(75, inherited.row("alpha:0")!!.bounds.height)
        assertEquals(75, inherited.row("primary_action")!!.bounds.height)

        val independent = SolverFixtures.solved(
            input(
                GeometryPreferences(
                    rowBaseHeightPx = 60.0,
                    alphaRowHeightPercent = 125,
                    primaryActionRowHeightPercent = 80,
                ),
            ),
        )
        assertEquals(75, independent.row("alpha:0")!!.bounds.height)
        assertEquals(48, independent.row("primary_action")!!.bounds.height)
    }

    @Test
    fun `primary height changes without changing alpha or utility rows`() {
        val geometry = SolverFixtures.solved(
            input(
                GeometryPreferences(
                    rowBaseHeightPx = 60.0,
                    primaryActionRowHeightPercent = 150,
                ),
            ),
        )

        assertEquals(listOf(60, 60, 60), geometry.rows.take(3).map { it.bounds.height })
        assertEquals(90, geometry.row("primary_action")!!.bounds.height)
        assertEquals(listOf(45, 45), geometry.rows.takeLast(2).map { it.bounds.height })
    }

    @Test
    fun `primary gaps affect only its boundaries and the total frame height`() {
        val baseline = SolverFixtures.solved(input(GeometryPreferences(rowBaseHeightPx = 60.0)))
        val geometry = SolverFixtures.solved(
            input(
                GeometryPreferences(
                    rowBaseHeightPx = 60.0,
                    primaryActionGapAbovePx = 7.0,
                    primaryActionGapBelowPx = 11.0,
                ),
            ),
        )

        assertEquals(baseline.frame.height + 18, geometry.frame.height)
        assertEquals(listOf(7, 11), geometry.gaps.map { it.bounds.height })
        assertTrue(geometry.gaps.all { it.role == primary })
        assertEquals("alpha:2", geometry.gaps.first().rowAbove)
        assertEquals("primary_action", geometry.gaps.first().rowBelow)
        assertEquals("primary_action", geometry.gaps.last().rowAbove)
        assertEquals("coding_utility:0", geometry.gaps.last().rowBelow)
    }

    @Test
    fun `primary spacing is independent and defaults to global spacing`() {
        val independent = SolverFixtures.solved(
            input(
                GeometryPreferences(
                    rowBaseHeightPx = 60.0,
                    keySpacingHorizontalPx = 2.0,
                    keySpacingVerticalPx = 3.0,
                    primaryActionSpacingHorizontalPx = 8.0,
                    primaryActionSpacingVerticalPx = 9.0,
                ),
            ),
        )
        assertEquals(GeometrySpacing(2.0, 3.0), independent.row("alpha:0")!!.declaredSpacing)
        assertEquals(GeometrySpacing(8.0, 9.0), independent.row("primary_action")!!.declaredSpacing)

        val inherited = SolverFixtures.solved(
            input(
                GeometryPreferences(
                    rowBaseHeightPx = 60.0,
                    keySpacingHorizontalPx = 4.0,
                    keySpacingVerticalPx = 5.0,
                ),
            ),
        )
        assertEquals(GeometrySpacing(4.0, 5.0), inherited.row("primary_action")!!.declaredSpacing)
    }

    @Test
    fun `horizontal inset narrows and centres only the primary row while its grower absorbs remainder`() {
        val growablePrimary = GeometryRow(
            stableId = "primary_action",
            role = primary,
            items = listOf(
                GeometryItem("symbols", widthUnits = 1.0),
                GeometryItem("space", widthUnits = 1.0, growWeight = 1.0),
                GeometryItem("enter", widthUnits = 1.0),
            ),
        )
        val rows = listOf(
            SolverFixtures.uniformRow("alpha:0", SemanticRowRole.ALPHA, 10),
            growablePrimary,
        )
        val geometry = SolverFixtures.solved(
            KeyboardGeometryPolicy.buildInput(
                rows = rows,
                prefs = GeometryPreferences(
                    rowBaseHeightPx = 60.0,
                    primaryActionInsetHorizontalPx = 60.0,
                ),
                availableWidth = 1000.0,
                framePolicy = FramePolicy.Intrinsic(60.0),
            ),
        )

        val alpha = geometry.row("alpha:0")!!
        val spaceRow = geometry.row("primary_action")!!
        assertEquals(0, alpha.bounds.left)
        assertEquals(1000, alpha.bounds.right)
        assertEquals(60, spaceRow.bounds.left)
        assertEquals(940, spaceRow.bounds.right)
        assertEquals(spaceRow.bounds.left, spaceRow.items.first().bounds.left)
        assertEquals(spaceRow.bounds.right, spaceRow.items.last().bounds.right)
        assertEquals(680, spaceRow.items[1].bounds.width)
    }

    @Test
    fun `zero role inset preserves the previous solution`() {
        val rows = SolverFixtures.defaultCoding()
        val baseline = SolverFixtures.solved(SolverFixtures.input(rows))
        val explicitZero = SolverFixtures.solved(
            SolverFixtures.input(rows).copy(horizontalInsetByRole = mapOf(primary to 0.0)),
        )
        assertEquals(baseline, explicitZero)
    }

    @Test
    fun `invalid and oversized role insets fail safely`() {
        for (bad in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, 500.0, 600.0)) {
            val reasons = SolverFixtures.unsatisfiable(
                SolverFixtures.input(
                    rows = listOf(SolverFixtures.primaryActionRow()),
                    width = 1000.0,
                ).copy(horizontalInsetByRole = mapOf(primary to bad)),
            )
            assertTrue(reasons.any { it.contains("horizontal row inset") }, "expected inset reason for $bad: $reasons")
        }
    }
}
