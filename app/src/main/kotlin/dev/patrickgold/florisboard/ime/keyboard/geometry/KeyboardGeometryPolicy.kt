/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.text.key.KeyCode

/**
 * Stage 03: the single authoritative statement of what OmniBoard's geometry *is*.
 *
 * Everything the shipped keyboard measures comes from here. There is no second table of per-key
 * widths, no positional row rule, and no branch that recognises a key by its code outside
 * [canonicalWidthUnits] and [canonicalGrowWeight]. Both frame sizing and inner layout call
 * [buildInput]; they differ only in which [FramePolicy] they hand it, which is what makes them one
 * authority rather than two that happen to agree.
 *
 * ## The canonical layout
 *
 * OmniBoard's shipped baseline is mathematically normalized. The historical tuned geometry — the
 * `2.68`/`1.56`/`1.26` specialized width tables, the `0.72` arrow keys, the `0.8` undo/redo, the
 * `1.25` Ctrl/Tmux/Escape cells, the Space height compensation and the Escape/Σ padding trick — is
 * forensic evidence of controls that no longer exist. None of it is a default, a visual target, or
 * a migration seed. Ergonomic specialization is reapplied afterwards, explicitly, through per-key
 * customization.
 *
 * - **Alpha.** Every alpha key is one unit. The ten-key row establishes the unit; the nine-key rows
 *   consume that same unit and are centred rather than stretched. Shift and Delete are ordinary
 *   one-unit cells that keep their actions and presentation.
 * - **Primary action.** `Tab | comma | Space | period | Enter`. Tab and Enter are 1.5 units, comma
 *   and period 1.0, Space 1.0 *plus growth*. Space is wide because it declares a grow weight, not
 *   because it is the third of five and not because `5.0` is written down anywhere. Against a
 *   ten-unit alpha grid the fixed demand is five units and the remainder is five units, so Space
 *   lands at ~5.0 — a consequence, not an input.
 * - **Coding utility.** Nine equal one-unit cells per row, each row filling the content width on
 *   its own. The utility grid is deliberately independent of the ten-column alpha grid.
 * - **Specialized surfaces.** Numeric, Phone, Symbols and extension rows keep their own roles and
 *   fill their own width with equal units. No specialized row borrows the alpha or utility grid for
 *   convenient sizing, and none of them receives Coding boundary gaps.
 *
 * Height is stated once: 100% is one normalized full row. Alpha and primary are 100%; coding
 * utility rows carry a 75% *role* adjustment on a 100% base, so a utility row set to 100% is
 * exactly as tall as an alpha row. No individual key compensates for its row's height.
 */
object KeyboardGeometryPolicy {

    /** One normalized cell. The width every ordinary key gets, in every region. */
    const val CANONICAL_UNIT: Double = 1.0

    /**
     * Tab and Enter in the primary action row.
     *
     * A semantic default for that row, not a saved per-key customization: a 100% per-key override
     * means 100% *of this*, and Enter outside the primary action row is an ordinary unit cell.
     */
    const val PRIMARY_ACTION_EDGE_UNITS: Double = 1.5

    /** Space's share of the primary row's leftover width. It is the only grower by default. */
    const val SPACE_GROW_WEIGHT: Double = 1.0

    // -- Per-item defaults -----------------------------------------------------------------------

    /**
     * The structural width of an ordinary key, in units.
     *
     * Role-scoped on purpose. `Enter` is 1.5 units in the primary action row and one unit
     * everywhere else, because 1.5 is a property of that row's composition rather than of the Enter
     * action.
     */
    fun canonicalWidthUnits(role: SemanticRowRole, code: Int): Double = when (role) {
        SemanticRowRole.PRIMARY_ACTION -> when (code) {
            KeyCode.TAB, KeyCode.ENTER -> PRIMARY_ACTION_EDGE_UNITS
            else -> CANONICAL_UNIT
        }
        else -> CANONICAL_UNIT
    }

    /**
     * How eagerly a key absorbs its row's leftover width.
     *
     * Only Space, and only in the primary action row. Every other key is exactly its declared
     * width, so a row with no Space is placed as if growth did not exist.
     */
    fun canonicalGrowWeight(role: SemanticRowRole, code: Int): Double = when {
        role != SemanticRowRole.PRIMARY_ACTION -> 0.0
        code == KeyCode.SPACE || code == KeyCode.CJK_SPACE -> SPACE_GROW_WEIGHT
        else -> 0.0
    }

    // -- Role → policy ---------------------------------------------------------------------------

    /** Roles that establish and consume the shared alpha unit grid. */
    private val WIDTH_REFERENCE_ROLES = setOf(SemanticRowRole.ALPHA)

    /**
     * Roles laid out against the alpha grid.
     *
     * The primary action row aligns to it without being able to widen it. Coding utility, numeric,
     * symbol and extension rows are absent, so each derives its own unit width from the full
     * content area and fills it independently.
     */
    private val WIDTH_CONSUMER_ROLES = WIDTH_REFERENCE_ROLES + SemanticRowRole.PRIMARY_ACTION

    /** Roles that take the utility row-height adjustment rather than the alpha one. */
    private val SHORT_ROW_ROLES = setOf(SemanticRowRole.CODING_UTILITY, SemanticRowRole.EXTENSION)

    /** Roles the utility key-width control applies to. */
    private val UTILITY_WIDTH_ROLES = setOf(SemanticRowRole.CODING_UTILITY)

    /**
     * Roles that are immune to both width controls.
     *
     * The primary action row is a fixed semantic composition whose only flexible member is Space;
     * scaling its cells would move the one row users rely on being where they left it.
     */
    private val UNSCALED_WIDTH_ROLES = setOf(SemanticRowRole.PRIMARY_ACTION, SemanticRowRole.PLACEHOLDER)

    // -- Input construction ----------------------------------------------------------------------

    /**
     * Builds the solver input for [rows] under [prefs].
     *
     * The only caller-supplied difference between frame sizing and inner layout is [framePolicy]:
     * `Intrinsic` when the frame's height is being decided, `FitToHeight` when it has already been
     * decided and the rows are sharing it. Everything else — roles, units, growth, heights,
     * spacing, gaps — is stated once, here.
     */
    fun buildInput(
        rows: List<GeometryRow>,
        prefs: GeometryPreferences,
        availableWidth: Double,
        framePolicy: FramePolicy,
        orientation: GeometryOrientation = GeometryOrientation.PORTRAIT,
    ): GeometrySolverInput {
        val safe = prefs.sanitized()
        val alphaHeightScale = safe.alphaRowHeightPercent / 100.0
        val utilityHeightScale = safe.utilityRowHeightPercent / 100.0
        val alphaWidthScale = safe.alphaKeyWidthPercent / 100.0
        val utilityWidthScale = safe.utilityKeyWidthPercent / 100.0

        val heightScales = SemanticRowRole.entries.associateWith { role ->
            if (role in SHORT_ROW_ROLES) utilityHeightScale else alphaHeightScale
        }
        val widthScales = SemanticRowRole.entries.associateWith { role ->
            when (role) {
                in UNSCALED_WIDTH_ROLES -> 1.0
                in UTILITY_WIDTH_ROLES -> utilityWidthScale
                else -> alphaWidthScale
            }
        }
        // Spacing is declared, never applied to a structural rectangle. KeyBoundsDerivation insets
        // the *visible* keycap by half of it on each participating side, so one preference of 2dp
        // produces a 2dp gap between neighbours and a 1dp margin at the row's outer edge — rather
        // than being charged twice and producing 4dp, as the historical layout did.
        val spacing = GeometrySpacing(
            horizontal = safe.keySpacingHorizontalPx,
            vertical = safe.keySpacingVerticalPx,
        )

        return GeometrySolverInput(
            availableWidth = availableWidth,
            rows = rows,
            framePolicy = framePolicy,
            // 100% is one normalized full row, for every role. The utility region's 75% is the
            // role adjustment above, applied to this 1.0 base — never a 0.75 base that a 75%
            // preference would compound into 56.25%.
            rowHeightPolicy = RowHeightPolicy(defaultHeightUnits = 1.0),
            gapPolicy = BoundaryGapPolicy(
                mapOf(
                    SemanticRowRole.CODING_UTILITY to RoleBlockGaps(
                        above = safe.utilityGapAbovePx,
                        within = safe.utilityGapWithinPx,
                        below = safe.utilityGapBelowPx,
                    ),
                ),
            ),
            widthPolicy = RowWidthPolicy(
                sharedReference = SharedWidthReference(
                    measuredRoles = WIDTH_REFERENCE_ROLES,
                    consumerRoles = WIDTH_CONSUMER_ROLES,
                    // Structural rows partition the available width conservatively; the outer
                    // margin is a visual inset, not a structural one.
                    insetH = 0.0,
                ),
                alignment = RowAlignment.CENTER,
            ),
            overrides = GeometryOverrides(
                rowHeightScaleByRole = heightScales,
                itemWidthScaleByRole = widthScales,
            ),
            spacingByRole = SemanticRowRole.entries.associateWith { spacing },
            orientation = orientation,
        )
    }
}

/**
 * Every preference the geometry pipeline reads, snapshotted into plain data.
 *
 * Pixel values are already resolved from dp; percentages are as stored. Holding them as a value
 * rather than reading them at the point of use is what lets the whole pipeline be tested without a
 * `Context`, and what lets [sanitized] be the one boundary where hostile values are dealt with.
 */
data class GeometryPreferences(
    val rowBaseHeightPx: Double,
    val alphaRowHeightPercent: Int = 100,
    val utilityRowHeightPercent: Int = 75,
    val alphaKeyWidthPercent: Int = 100,
    val utilityKeyWidthPercent: Int = 100,
    val keySpacingHorizontalPx: Double = 0.0,
    val keySpacingVerticalPx: Double = 0.0,
    val utilityGapAbovePx: Double = 0.0,
    val utilityGapWithinPx: Double = 0.0,
    val utilityGapBelowPx: Double = 0.0,
) {
    /**
     * Clamps every value into a range the solver can satisfy.
     *
     * Validation happens here, at the preference boundary, so that an out-of-range or corrupt
     * stored value becomes a slightly odd keyboard rather than an unsatisfiable solve or a crash.
     * Non-finite values fall back to the canonical default for that field.
     */
    fun sanitized(): GeometryPreferences = GeometryPreferences(
        rowBaseHeightPx = rowBaseHeightPx.clampFinite(MIN_ROW_BASE_HEIGHT_PX, MAX_ROW_BASE_HEIGHT_PX, 65.0),
        alphaRowHeightPercent = alphaRowHeightPercent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT),
        utilityRowHeightPercent = utilityRowHeightPercent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT),
        alphaKeyWidthPercent = alphaKeyWidthPercent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT),
        utilityKeyWidthPercent = utilityKeyWidthPercent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT),
        keySpacingHorizontalPx = keySpacingHorizontalPx.clampFinite(0.0, MAX_SPACING_PX, 0.0),
        keySpacingVerticalPx = keySpacingVerticalPx.clampFinite(0.0, MAX_SPACING_PX, 0.0),
        utilityGapAbovePx = utilityGapAbovePx.clampFinite(0.0, MAX_GAP_PX, 0.0),
        utilityGapWithinPx = utilityGapWithinPx.clampFinite(0.0, MAX_GAP_PX, 0.0),
        utilityGapBelowPx = utilityGapBelowPx.clampFinite(0.0, MAX_GAP_PX, 0.0),
    )

    companion object {
        const val MIN_SCALE_PERCENT = 10
        const val MAX_SCALE_PERCENT = 300
        const val MIN_ROW_BASE_HEIGHT_PX = 1.0
        const val MAX_ROW_BASE_HEIGHT_PX = 4096.0
        const val MAX_SPACING_PX = 64.0
        const val MAX_GAP_PX = 256.0

        private fun Double.clampFinite(min: Double, max: Double, fallback: Double): Double =
            if (isFinite()) coerceIn(min, max) else fallback
    }
}
