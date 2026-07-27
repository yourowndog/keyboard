package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.geometry.LegacyGeometryComparator.DifferenceKind
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 02 comparison mode: what the shared solver changes relative to the legacy authorities, and
 * what it deliberately does not.
 *
 * Every difference must be classified. A scenario declares the difference kinds it expects, with
 * the defect each one fixes; anything outside that set fails as an unresolved difference, so a
 * change nobody decided about cannot slip through as an intentional one.
 */
class LegacyGeometryComparisonTest {

    private companion object {
        /** The vertical differences every scenario shows, for reasons named on each test below. */
        val VERTICAL = setOf(DifferenceKind.FRAME_HEIGHT, DifferenceKind.ROW_TOP, DifferenceKind.ROW_HEIGHT)

        val SCENARIOS: List<Pair<String, () -> TextKeyboard>> = listOf(
            "defaultCoding" to GeometryFixtures::defaultCoding,
            "codingUtilitiesHidden" to GeometryFixtures::codingUtilitiesHidden,
            "codingWithNumberExtension" to GeometryFixtures::codingWithNumberExtension,
            "codingWithBothExtensions" to GeometryFixtures::codingWithBothExtensions,
            "characters" to GeometryFixtures::characters,
            "wideSymbols" to GeometryFixtures::wideSymbols,
            "numeric" to GeometryFixtures::numeric,
            "numericAdvanced" to GeometryFixtures::numericAdvanced,
            "phone" to GeometryFixtures::phone,
            "layoutPack" to GeometryFixtures::layoutPackWithSpacersAndUnits,
        )
    }

    private fun report(scenario: String, keyboard: TextKeyboard) =
        LegacyGeometryComparator.compare(scenario, keyboard)

    private fun reports() = SCENARIOS.map { (name, build) -> report(name, build()) }

    /** Fails the test naming any difference outside [expected], which is by definition unresolved. */
    private fun assertNoUnresolvedDifferences(
        report: LegacyGeometryComparator.ComparisonReport,
        expected: Set<DifferenceKind>,
    ) {
        val unresolved = report.differences.filter { it.kind !in expected }
        assertTrue(
            unresolved.isEmpty(),
            "unresolved differences in '${report.scenario}': " +
                unresolved.joinToString { "${it.kind} ${it.subject} ${it.legacy} -> ${it.solver}" },
        )
    }

    // -- Horizontal geometry is preserved ------------------------------------------------------

    @COMPATIBILITY(
        "The solver reproduces legacy horizontal allocation exactly across every fixture: the " +
            "shared entry-row grid, the primary action row's immunity to both width sliders, and " +
            "utility rows filling the width on their own units. Horizontal geometry is a " +
            "compatibility target and is not changing in this migration.",
    )
    @Test
    fun `no scenario changes horizontal geometry`() {
        for (report in reports()) {
            val horizontal = report.differences.filter {
                it.kind == DifferenceKind.ITEM_LEFT || it.kind == DifferenceKind.ITEM_WIDTH
            }
            assertTrue(
                horizontal.isEmpty(),
                "'${report.scenario}' moved keys horizontally: " +
                    horizontal.joinToString { "${it.subject} ${it.legacy} -> ${it.solver}" },
            )
        }
    }

    // -- Vertical geometry: the intended fixes -------------------------------------------------

    @EXPECTED_FIX(
        "Legacy partitions default Coding as 3 alpha + 3 mod from counts alone, charging the top " +
            "alpha row modifier height and leaving the primary action row indistinguishable from " +
            "an alpha row. The solver reads the declared roles instead: 3 alpha + 1 primary " +
            "action at full height, 2 utility rows at the short height.",
    )
    @Test
    fun `default coding stops giving an alpha row modifier height`() {
        val report = report("defaultCoding", GeometryFixtures.defaultCoding())
        assertNoUnresolvedDifferences(report, VERTICAL)

        // 3 alpha + primary at 60px, 2 utility at 45px, plus the declared 8 + 4 + 8 of gap.
        assertEquals(350.0, report.solverFrameHeight)
        assertEquals(335.0, report.legacyFrameHeight, "legacy charges one alpha row the short height")
    }

    @EXPECTED_FIX(
        "Legacy adds the gap budget to the frame but the inner row allocation knows nothing about " +
            "it, so rows overflow the frame by exactly the gap total. The solver places gaps as " +
            "explicit bands inside the content area, so rows and gaps together are the frame.",
    )
    @Test
    fun `gaps are allocated rather than added to the frame and forgotten`() {
        val keyboard = GeometryFixtures.defaultCoding()
        val input = LegacyGeometryComparator.solverInput(
            keyboard,
            LegacyGeometryComparator.LegacyPrefs(),
            availableWidth = 1080.0,
        )
        val geometry = SolverFixtures.solved(input)

        assertEquals(20, geometry.gaps.sumOf { it.bounds.height }, "the declared gap budget is 8 + 4 + 8")
        assertEquals(
            geometry.frame.height,
            geometry.rows.sumOf { it.bounds.height } + geometry.gaps.sumOf { it.bounds.height },
            "rows plus gaps are the frame; nothing overflows it",
        )
    }

    @EXPECTED_FIX(
        "Legacy still charges compact Coding a modifier row and the full gap budget when " +
            "bottomModRowCount drops to 0, because the count-based partition assigns the fourth " +
            "row to topModRows. With no utility rows there is nothing to declare gaps, so the " +
            "solver charges none — and the keyboard is still Coding, not Text.",
    )
    @Test
    fun `hiding the utility rows stops charging for a modifier row that is not there`() {
        val report = report("codingUtilitiesHidden", GeometryFixtures.codingUtilitiesHidden())
        assertNoUnresolvedDifferences(report, VERTICAL)

        assertEquals(240.0, report.solverFrameHeight, "four full-height rows, no gaps")
        assertEquals(245.0, report.legacyFrameHeight, "legacy bills a phantom mod row and the whole gap budget")
    }

    @EXPECTED_FIX(
        "Legacy coerces any keyboard with fewer than four rows up to four for framing, then lets " +
            "the inner layout allocate the real row count into that height. The solver frames " +
            "the rows the keyboard actually has.",
    )
    @Test
    fun `short keyboards are no longer framed as if they had four rows`() {
        val report = report("layoutPack", GeometryFixtures.layoutPackWithSpacersAndUnits())
        assertNoUnresolvedDifferences(report, VERTICAL)

        assertEquals(180.0, report.solverFrameHeight, "three rows at 60px")
        assertEquals(230.0, report.legacyFrameHeight, "legacy frames three rows as four, with a gap budget")
    }

    @EXPECTED_FIX(
        "Numeric and phone keyboards are four numeric-entry rows, but legacy splits them 2 alpha " +
            "/ 2 mod from counts and then allocates them uniformly, so the frame and the rows " +
            "disagree. The solver reads NUMERIC on every row and sizes all four alike.",
    )
    @Test
    fun `the numeric family is framed as what it is`() {
        for (name in listOf("numeric", "numericAdvanced", "phone")) {
            val keyboard = SCENARIOS.first { it.first == name }.second()
            val report = report(name, keyboard)
            assertNoUnresolvedDifferences(report, VERTICAL)
            assertEquals(240.0, report.solverFrameHeight, "$name: four numeric rows at 60px")
            assertEquals(230.0, report.legacyFrameHeight, "$name: legacy shortens two of the four")
        }
    }

    @EXPECTED_FIX(
        "Extension rows already take the short height under legacy, but only because they land " +
            "outside the positional alpha window. The solver gives them the short height because " +
            "they declare the EXTENSION role, so inserting one no longer reshapes the rows below.",
    )
    @Test
    fun `extension rows take the short height by role, not by position`() {
        val withOne = report("codingWithNumberExtension", GeometryFixtures.codingWithNumberExtension())
        val withTwo = report("codingWithBothExtensions", GeometryFixtures.codingWithBothExtensions())
        assertNoUnresolvedDifferences(withOne, VERTICAL)
        assertNoUnresolvedDifferences(withTwo, VERTICAL)

        assertEquals(395.0, withOne.solverFrameHeight, "default Coding plus one 45px extension row")
        assertEquals(440.0, withTwo.solverFrameHeight, "and plus a second")
        assertEquals(
            45.0,
            withTwo.solverFrameHeight - withOne.solverFrameHeight,
            "each extension row costs exactly one short row, with no effect on any other row",
        )
    }

    // -- Every scenario is classified ------------------------------------------------------------

    @Test
    fun `every scenario's differences are classified`() {
        for (report in reports()) {
            assertNoUnresolvedDifferences(report, VERTICAL)
        }
    }

    @Test
    fun `difference table`() {
        println(LegacyGeometryComparator.differenceTable(reports()))
    }
}
