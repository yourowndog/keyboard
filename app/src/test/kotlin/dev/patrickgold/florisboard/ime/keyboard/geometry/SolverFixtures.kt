package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole

/**
 * Solver-input fixtures for the Stage 02 contract tests.
 *
 * These describe keyboards the way [KeyboardGeometrySolver] sees them: ordered semantic rows and
 * ordered items with width units. They deliberately mirror the *shapes* in [GeometryFixtures] —
 * default Coding, Coding without utilities, extensions, the numeric family — so the same
 * scenarios can be reasoned about on both sides of the migration.
 *
 * The legacy-equivalent policies live in [LegacyGeometryBridge]; these fixtures are policy-free on
 * purpose, so a test can state exactly which policy it is exercising.
 */
object SolverFixtures {

    const val PORTRAIT_WIDTH = 1080.0
    const val LANDSCAPE_WIDTH = 2160.0
    const val ROW_BASE_HEIGHT = 60.0

    private val ALPHA = SemanticRowRole.ALPHA
    private val PRIMARY = SemanticRowRole.PRIMARY_ACTION
    private val UTILITY = SemanticRowRole.CODING_UTILITY
    private val EXTENSION = SemanticRowRole.EXTENSION

    /** A row of [count] one-unit items. */
    fun uniformRow(stableId: String, role: SemanticRowRole, count: Int): GeometryRow =
        GeometryRow(
            stableId = stableId,
            role = role,
            items = List(count) { GeometryItem(stableId = "$stableId#$it", widthUnits = 1.0) },
        )

    /** A row whose items declare [units] one by one, so widths are asymmetrical. */
    fun unitRow(stableId: String, role: SemanticRowRole, units: List<Double>): GeometryRow =
        GeometryRow(
            stableId = stableId,
            role = role,
            items = units.mapIndexed { index, u ->
                GeometryItem(stableId = "$stableId#$index", widthUnits = u)
            },
        )

    /** The primary action row: a symbols key, a wide space key, and enter. */
    fun primaryActionRow(spaceUnits: Double = 4.0): GeometryRow = unitRow(
        stableId = SemanticRowRole.PRIMARY_ACTION.name.lowercase(),
        role = PRIMARY,
        units = listOf(1.0, spaceUnits, 1.0),
    )

    /** Default Coding: 3 alpha rows, the primary action row, 2 coding utility rows. */
    fun defaultCoding(): List<GeometryRow> = listOf(
        uniformRow("alpha:0", ALPHA, 10),
        uniformRow("alpha:1", ALPHA, 9),
        uniformRow("alpha:2", ALPHA, 9),
        primaryActionRow(),
        uniformRow("coding_utility:0", UTILITY, 7),
        uniformRow("coding_utility:1", UTILITY, 7),
    )

    /**
     * Coding with the utility rows hidden. Still Coding: the primary action row is untouched and
     * the alpha rows keep their IDs, so nothing about this is Text.
     */
    fun compactCoding(): List<GeometryRow> = defaultCoding().filter { it.role != UTILITY }

    /** Coding with a number extension row on top. */
    fun codingWithExtension(): List<GeometryRow> =
        listOf(uniformRow("extension:0", EXTENSION, 10)) + defaultCoding()

    /** The numeric family: four numeric-entry rows, each three units wide. */
    fun numeric(): List<GeometryRow> =
        List(4) { uniformRow("numeric:$it", SemanticRowRole.NUMERIC, 3) }

    /** A row carrying a structural spacer between keys. */
    fun rowWithSpacer(stableId: String, role: SemanticRowRole): GeometryRow = GeometryRow(
        stableId = stableId,
        role = role,
        items = listOf(
            GeometryItem("$stableId#0", widthUnits = 1.5),
            GeometryItem("$stableId#1", widthUnits = 0.5),
            GeometryItem("$stableId#2", widthUnits = 2.0, kind = GeometryItemKind.SPACER),
            GeometryItem("$stableId#3", widthUnits = 1.0),
        ),
    )

    /** [count] rows, alternating roles, for row-count sweeps. */
    fun rowsOfCount(count: Int): List<GeometryRow> = List(count) { index ->
        val role = when (index % 3) {
            0 -> ALPHA
            1 -> PRIMARY
            else -> UTILITY
        }
        uniformRow("row:$index", role, count = 4)
    }

    /** An input with no policies beyond the frame, so a test can add exactly one. */
    fun input(
        rows: List<GeometryRow>,
        width: Double = PORTRAIT_WIDTH,
        framePolicy: FramePolicy = FramePolicy.Intrinsic(ROW_BASE_HEIGHT),
        orientation: GeometryOrientation = GeometryOrientation.PORTRAIT,
    ): GeometrySolverInput = GeometrySolverInput(
        availableWidth = width,
        rows = rows,
        framePolicy = framePolicy,
        orientation = orientation,
    )

    /** Solves [input] and fails the test if it could not be solved. */
    fun solved(input: GeometrySolverInput): SolvedGeometry =
        when (val solution = KeyboardGeometrySolver.solve(input)) {
            is GeometrySolution.Solved -> solution.geometry
            is GeometrySolution.Unsatisfiable -> throw AssertionError(
                "expected a solvable input but got: ${solution.reasons}",
            )
        }

    /** Solves [input] and fails the test if it *was* solvable. */
    fun unsatisfiable(input: GeometrySolverInput): List<String> =
        when (val solution = KeyboardGeometrySolver.solve(input)) {
            is GeometrySolution.Unsatisfiable -> solution.reasons
            is GeometrySolution.Solved -> throw AssertionError("expected an unsatisfiable input but it solved")
        }
}
