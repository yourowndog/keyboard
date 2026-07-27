package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.computeKeyboardFrameHeight
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import kotlin.math.abs

/**
 * Stage 02 comparison mode: runs the legacy geometry authorities and [KeyboardGeometrySolver] over
 * the same keyboard and reports where they disagree.
 *
 * Test-only, deliberately. Stage 02 performs no production cutover, so nothing ships that could
 * accidentally consult the solver; this comparator exists to make the differences legible *before*
 * a later stage moves any pixels.
 *
 * Two rules shape what it does:
 *
 * - **It does not distort semantic roles to reproduce known defects.** The legacy frame decides
 *   which rows are "modifier" rows by counting positions, which is why default Coding's first
 *   alpha row is charged modifier height. The bridge below maps roles to policies the way the
 *   architecture says they should map, and lets the difference show.
 * - **It compares structural allocation, not touch or visual rectangles.** Legacy touch bounds
 *   carry an alpha hitbox expansion and a spacing inset; those are Stage 03's subject, so the
 *   comparator removes the expansion and the scenarios run with zero spacing.
 */
object LegacyGeometryComparator {

    /** Sub-pixel slack. Legacy solves in `Float`, the solver emits rounded integer edges. */
    private const val TOLERANCE = 1.0

    /** The legacy preference set, in the units the legacy functions expect. */
    data class LegacyPrefs(
        val rowBaseHeight: Float = 60f,
        val alphaRowHeightFactor: Float = 1.0f,
        val bottomRowHeightFactor: Float = 0.75f,
        val alphaKeyWidthFactor: Float = 1.0f,
        val modKeyWidthFactor: Float = 1.0f,
        val alphaSpacingH: Float = 0f,
        val alphaSpacingV: Float = 0f,
        val modSpacingH: Float = 0f,
        val modSpacingV: Float = 0f,
        val modRowUpperGap: Float = 8f,
        val modRowInnerGap: Float = 4f,
        val modRowLowerGap: Float = 8f,
    ) {
        val gapTotal: Float get() = modRowUpperGap + modRowInnerGap + modRowLowerGap
    }

    /** What kind of quantity disagreed. */
    enum class DifferenceKind {
        FRAME_HEIGHT,
        ROW_TOP,
        ROW_HEIGHT,
        ITEM_LEFT,
        ITEM_WIDTH,
    }

    data class GeometryDifference(
        val kind: DifferenceKind,
        val subject: String,
        val legacy: Double,
        val solver: Double,
    ) {
        val delta: Double get() = solver - legacy
    }

    data class ComparisonReport(
        val scenario: String,
        val legacyFrameHeight: Double,
        val solverFrameHeight: Double,
        val differences: List<GeometryDifference>,
    ) {
        val kinds: Set<DifferenceKind> get() = differences.map { it.kind }.toSet()
    }

    // -- Role → policy bridge --------------------------------------------------------------------

    /** Roles whose rows are entry rows: they define the width grid and take the alpha row height. */
    private val ENTRY_ROLES = setOf(
        SemanticRowRole.ALPHA,
        SemanticRowRole.EXTENSION,
        SemanticRowRole.NUMERIC,
        SemanticRowRole.SYMBOL,
    )

    /**
     * Roles that take the legacy `bottomRowHeightFactor`.
     *
     * Extension rows are here, and alpha rows are not, which is the whole point: legacy charges
     * that factor to whichever rows fall outside a positional window.
     */
    private val SHORT_ROLES = setOf(SemanticRowRole.EXTENSION, SemanticRowRole.CODING_UTILITY)

    /**
     * Builds the solver input that corresponds to [keyboard] under [prefs].
     *
     * Every legacy quantity lands on a role, never on a position: the alpha/bottom height factors
     * become role-scoped height overrides, the alpha/mod width factors become role-scoped width
     * overrides, and the three `modRow*Gap` preferences become the coding-utility block's declared
     * boundary gaps.
     */
    fun solverInput(
        keyboard: TextKeyboard,
        prefs: LegacyPrefs,
        availableWidth: Double,
        orientation: GeometryOrientation = GeometryOrientation.PORTRAIT,
    ): GeometrySolverInput {
        val rows = keyboard.semanticRows.mapIndexed { index, semantic ->
            GeometryRow(
                stableId = semantic.stableId,
                role = semantic.role,
                items = keyboard.arrangement[index].mapIndexed { keyIndex, key ->
                    GeometryItem(
                        stableId = "${semantic.stableId}#$keyIndex",
                        widthUnits = key.flayWidthFactor.toDouble(),
                        heightFactor = key.flayHeightFactor.toDouble(),
                        verticalAlignment = key.flayVerticalAlignment,
                    )
                },
            )
        }
        val heightScales = SemanticRowRole.entries.associateWith { role ->
            if (role in SHORT_ROLES) prefs.bottomRowHeightFactor.toDouble()
            else prefs.alphaRowHeightFactor.toDouble()
        }
        val widthScales = SemanticRowRole.entries.associateWith { role ->
            when {
                role in ENTRY_ROLES -> prefs.alphaKeyWidthFactor.toDouble()
                // The primary action row is immune to both sliders, in Text and in Coding alike.
                role == SemanticRowRole.PRIMARY_ACTION -> 1.0
                else -> prefs.modKeyWidthFactor.toDouble()
            }
        }
        val spacing = SemanticRowRole.entries.associateWith { role ->
            if (role == SemanticRowRole.CODING_UTILITY) {
                GeometrySpacing(prefs.modSpacingH.toDouble(), prefs.modSpacingV.toDouble())
            } else {
                GeometrySpacing(prefs.alphaSpacingH.toDouble(), prefs.alphaSpacingV.toDouble())
            }
        }
        return GeometrySolverInput(
            availableWidth = availableWidth,
            rows = rows,
            framePolicy = FramePolicy.Intrinsic(prefs.rowBaseHeight.toDouble()),
            gapPolicy = BoundaryGapPolicy(
                mapOf(
                    SemanticRowRole.CODING_UTILITY to RoleBlockGaps(
                        above = prefs.modRowUpperGap.toDouble(),
                        within = prefs.modRowInnerGap.toDouble(),
                        below = prefs.modRowLowerGap.toDouble(),
                    ),
                ),
            ),
            widthPolicy = RowWidthPolicy(
                sharedReference = SharedWidthReference(
                    measuredRoles = ENTRY_ROLES,
                    consumerRoles = ENTRY_ROLES + SemanticRowRole.PRIMARY_ACTION,
                    insetH = prefs.alphaSpacingH.toDouble() * 2.0,
                ),
            ),
            overrides = GeometryOverrides(
                rowHeightScaleByRole = heightScales,
                itemWidthScaleByRole = widthScales,
            ),
            spacingByRole = spacing,
            orientation = orientation,
        )
    }

    // -- Comparison ------------------------------------------------------------------------------

    /**
     * Lays [keyboard] out both ways and reports the differences.
     *
     * The legacy inner layout is handed the legacy frame height, exactly as production does, so
     * the two authorities are compared as they actually compose.
     */
    fun compare(
        scenario: String,
        keyboard: TextKeyboard,
        prefs: LegacyPrefs = LegacyPrefs(),
        availableWidth: Double = 1080.0,
        orientation: GeometryOrientation = GeometryOrientation.PORTRAIT,
    ): ComparisonReport {
        val legacyFrameHeight = computeKeyboardFrameHeight(
            rawRowCount = keyboard.rowCount,
            bottomModRowCount = keyboard.bottomModRowCount,
            rowBaseHeight = prefs.rowBaseHeight,
            alphaRowHeightFactor = prefs.alphaRowHeightFactor,
            bottomRowHeightFactor = prefs.bottomRowHeightFactor,
            gapTotal = prefs.gapTotal,
        )
        keyboard.layout(
            keyboardWidth = availableWidth.toFloat(),
            keyboardHeight = legacyFrameHeight,
            desiredKey = keyboard.arrangement.first().first(),
            extendTouchBoundariesDownwards = false,
            bottomRowHeightFactor = prefs.bottomRowHeightFactor,
            alphaRowHeightFactor = prefs.alphaRowHeightFactor,
            alphaKeyWidthFactor = prefs.alphaKeyWidthFactor,
            modKeyWidthFactor = prefs.modKeyWidthFactor,
            alphaSpacingH = prefs.alphaSpacingH,
            alphaSpacingV = prefs.alphaSpacingV,
            modSpacingH = prefs.modSpacingH,
            modSpacingV = prefs.modSpacingV,
        )

        val input = solverInput(keyboard, prefs, availableWidth, orientation)
        val solved = when (val solution = KeyboardGeometrySolver.solve(input)) {
            is GeometrySolution.Solved -> solution.geometry
            is GeometrySolution.Unsatisfiable -> throw AssertionError(
                "scenario '$scenario' is unsolvable: ${solution.reasons}",
            )
        }

        val differences = mutableListOf<GeometryDifference>()
        fun record(kind: DifferenceKind, subject: String, legacy: Double, solver: Double) {
            if (abs(solver - legacy) > TOLERANCE) {
                differences += GeometryDifference(kind, subject, legacy, solver)
            }
        }

        record(
            DifferenceKind.FRAME_HEIGHT,
            scenario,
            legacyFrameHeight.toDouble(),
            solved.frame.height.toDouble(),
        )

        for ((index, solvedRow) in solved.rows.withIndex()) {
            val legacyRow = keyboard.arrangement[index]
            val legacyTop = legacyRow.minOf { it.touchBounds.top }.toDouble()
            val legacyBottom = legacyRow.maxOf { it.touchBounds.bottom }.toDouble()
            record(DifferenceKind.ROW_TOP, solvedRow.stableId, legacyTop, solvedRow.bounds.top.toDouble())
            record(
                DifferenceKind.ROW_HEIGHT,
                solvedRow.stableId,
                legacyBottom - legacyTop,
                solvedRow.bounds.height.toDouble(),
            )

            for ((keyIndex, solvedItem) in solvedRow.items.withIndex()) {
                val key = legacyRow[keyIndex]
                // Undo the alpha hitbox expansion so the structural allocation is compared.
                val expansion = if (key.isAlpha && prefs.alphaSpacingH > 0f) prefs.alphaSpacingH * 0.2f else 0f
                val legacyLeft = (key.touchBounds.left + expansion).toDouble()
                val legacyRight = (key.touchBounds.right - expansion).toDouble()
                record(DifferenceKind.ITEM_LEFT, solvedItem.stableId, legacyLeft, solvedItem.bounds.left.toDouble())
                record(
                    DifferenceKind.ITEM_WIDTH,
                    solvedItem.stableId,
                    legacyRight - legacyLeft,
                    solvedItem.bounds.width.toDouble(),
                )
            }
        }

        return ComparisonReport(
            scenario = scenario,
            legacyFrameHeight = legacyFrameHeight.toDouble(),
            solverFrameHeight = solved.frame.height.toDouble(),
            differences = differences,
        )
    }

    /** A compact table of [reports], for the stage report-back. */
    fun differenceTable(reports: List<ComparisonReport>): String {
        val header = "%-32s %10s %10s %8s  %s".format("scenario", "legacy", "solver", "delta", "differing")
        val rows = reports.map { report ->
            "%-32s %10.1f %10.1f %+8.1f  %s".format(
                report.scenario,
                report.legacyFrameHeight,
                report.solverFrameHeight,
                report.solverFrameHeight - report.legacyFrameHeight,
                if (report.differences.isEmpty()) "none" else report.kinds.sorted().joinToString(","),
            )
        }
        return (listOf(header) + rows).joinToString("\n")
    }
}
