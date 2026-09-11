package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import kotlin.test.Test
import kotlin.test.assertEquals

/** Contract for the independently adjustable optional number row. */
class NumberRowGeometryTest {

    private val number = SemanticRowRole.NUMBER_ROW
    private val extension = SemanticRowRole.EXTENSION

    private fun rows() = listOf(
        SolverFixtures.uniformRow("number_row", number, 10),
        SolverFixtures.uniformRow("extension:0", extension, 9),
        SolverFixtures.uniformRow("alpha:0", SemanticRowRole.ALPHA, 10),
        SolverFixtures.primaryActionRow(),
        SolverFixtures.uniformRow("coding_utility:0", SemanticRowRole.CODING_UTILITY, 9),
    )

    private fun solve(prefs: GeometryPreferences) = SolverFixtures.solved(
        KeyboardGeometryPolicy.buildInput(
            rows = rows(),
            prefs = prefs,
            availableWidth = 1000.0,
            framePolicy = FramePolicy.Intrinsic(prefs.rowBaseHeightPx),
        ),
    )

    @Test
    fun `number row follows utility height until independently overridden`() {
        val inherited = solve(
            GeometryPreferences(
                rowBaseHeightPx = 60.0,
                utilityRowHeightPercent = 80,
            ),
        )
        assertEquals(48, inherited.row("number_row")!!.bounds.height)
        assertEquals(48, inherited.row("extension:0")!!.bounds.height)

        val independent = solve(
            GeometryPreferences(
                rowBaseHeightPx = 60.0,
                utilityRowHeightPercent = 80,
                numberRowHeightPercent = 125,
            ),
        )
        assertEquals(75, independent.row("number_row")!!.bounds.height)
        assertEquals(48, independent.row("extension:0")!!.bounds.height)
    }

    @Test
    fun `number row height does not change letter space or utility rows`() {
        val geometry = solve(
            GeometryPreferences(
                rowBaseHeightPx = 60.0,
                numberRowHeightPercent = 50,
            ),
        )

        assertEquals(30, geometry.row("number_row")!!.bounds.height)
        assertEquals(60, geometry.row("alpha:0")!!.bounds.height)
        assertEquals(60, geometry.row("primary_action")!!.bounds.height)
        assertEquals(45, geometry.row("coding_utility:0")!!.bounds.height)
    }
}
