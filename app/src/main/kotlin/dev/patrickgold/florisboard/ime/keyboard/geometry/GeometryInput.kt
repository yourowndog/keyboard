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
import dev.patrickgold.florisboard.ime.keyboard.VerticalAlignment

/**
 * Stage 02 solver input: everything [KeyboardGeometrySolver] is allowed to know.
 *
 * The input is exhaustive on purpose. The solver reads nothing else — no preferences, no
 * `Context`, no keyboard object, no ambient Compose state — so a given input always produces the
 * same output. Anything that structurally matters has to be stated here.
 *
 * Equally deliberate is what the input *cannot* express. There is no `bottomModRowCount`, no row
 * count threshold, and no "contains Space" flag. A row says what it is via
 * [SemanticRowRole]; policies are keyed by that role. Geometry can therefore not be derived from
 * a row's position, and a keyboard cannot silently change character by gaining or losing a row.
 *
 * See `omniboard-artifacts/implementation/keyboard-geometry/02-shared-solver.md`.
 */

/** Whether an item occupies space for its own sake or on behalf of a key. */
enum class GeometryItemKind {
    /** A real key. */
    KEY,

    /** Structural filler: occupies width, receives no touch handling. */
    SPACER,
}

/** Orientation the solve is performed for. Declared, never inferred from the dimensions. */
enum class GeometryOrientation {
    PORTRAIT,
    LANDSCAPE,
}

/** How a row's items are distributed when they are narrower than the content width. */
enum class RowAlignment {
    START,
    CENTER,
    END,
}

/**
 * One key or spacer awaiting placement.
 *
 * @param stableId identifies this item in the solved result. Unique within the whole input.
 * @param widthUnits structural width in units. Neighbours reflow when this changes, because a
 *   row's unit width is derived from the units its items declare.
 * @param heightFactor declared for later stages. Stage 02 does not apply it: the structural
 *   rectangle is the row band, and visual/touch inflation belongs to Stage 03.
 * @param verticalAlignment likewise declared for Stage 03, not applied here.
 */
data class GeometryItem(
    val stableId: String,
    val widthUnits: Double,
    val heightFactor: Double = 1.0,
    val verticalAlignment: VerticalAlignment = VerticalAlignment.BOTTOM,
    val kind: GeometryItemKind = GeometryItemKind.KEY,
)

/**
 * One row awaiting placement, in arrangement order.
 *
 * @param stableId the row's identity, as carried by
 *   [dev.patrickgold.florisboard.ime.keyboard.NormalizedRow]. Survives into the result.
 * @param role what the row is. Every policy below is keyed by this and by nothing else.
 */
data class GeometryRow(
    val stableId: String,
    val role: SemanticRowRole,
    val items: List<GeometryItem>,
) {
    /** Total declared width units, before any scale is applied. */
    val totalWidthUnits: Double
        get() = items.sumOf { it.widthUnits }
}

/**
 * A profile's relative row heights, in units.
 *
 * Units are relative, not absolute: [FramePolicy] decides what one unit is worth in pixels.
 */
data class RowHeightPolicy(
    val heightUnitsByRole: Map<SemanticRowRole, Double> = emptyMap(),
    val defaultHeightUnits: Double = 1.0,
) {
    fun unitsFor(role: SemanticRowRole): Double = heightUnitsByRole[role] ?: defaultHeightUnits
}

/**
 * The gaps a contiguous block of same-role rows declares around and inside itself.
 *
 * A "block" is a maximal run of adjacent rows sharing one role. [above] is emitted once, before
 * the block; [within] once between each adjacent pair inside it; [below] once after it. Two
 * adjacent blocks therefore contribute the first block's [below] *and* the second's [above], as
 * two separate gaps — additive, and visible as such in the result.
 */
data class RoleBlockGaps(
    val above: Double = 0.0,
    val within: Double = 0.0,
    val below: Double = 0.0,
)

/** Boundary gaps, declared per role. Roles absent from [byRole] declare no gaps. */
data class BoundaryGapPolicy(
    val byRole: Map<SemanticRowRole, RoleBlockGaps> = emptyMap(),
) {
    fun gapsFor(role: SemanticRowRole): RoleBlockGaps = byRole[role] ?: NONE

    private companion object {
        val NONE = RoleBlockGaps()
    }
}

/**
 * A unit width shared across rows, so that rows of different lengths still align to one grid.
 *
 * [measuredRoles] decide how wide one unit is: the widest row carrying one of those roles is
 * fitted to the content width (less [insetH]), and that row's unit width becomes the shared one.
 * [consumerRoles] are the rows laid out against it — normally a superset, so that e.g. a primary
 * action row aligns to the alpha grid without being able to widen it.
 *
 * Rows whose role is not in [consumerRoles] derive their own unit width from the full content
 * width, so they fill it regardless of how long they are.
 */
data class SharedWidthReference(
    val measuredRoles: Set<SemanticRowRole>,
    val consumerRoles: Set<SemanticRowRole>,
    val insetH: Double = 0.0,
)

/** How rows are fitted horizontally. */
data class RowWidthPolicy(
    val sharedReference: SharedWidthReference? = null,
    val alignment: RowAlignment = RowAlignment.CENTER,
)

/**
 * How the frame's height relates to its rows.
 *
 * Both variants satisfy the same invariant — frame height is the rows plus the declared gaps plus
 * the declared outer insets — they differ only in which side is known first.
 */
sealed interface FramePolicy {
    /**
     * Rows are sized from [rowBaseHeight] and the frame is however tall that makes it.
     *
     * @param maxFrameHeight optional ceiling. A solve that would exceed it is unsatisfiable rather
     *   than silently clipped.
     */
    data class Intrinsic(
        val rowBaseHeight: Double,
        val maxFrameHeight: Double? = null,
    ) : FramePolicy

    /**
     * The frame height is fixed and the rows share what the gaps and insets leave, in proportion
     * to their height units.
     */
    data class FitToHeight(val frameHeight: Double) : FramePolicy
}

/** Outer insets. The only documented reason a frame may be larger than its rows plus gaps. */
data class GeometryInsets(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
) {
    val horizontal: Double get() = left + right
    val vertical: Double get() = top + bottom
}

/**
 * Per-role spacing. Declared for Stage 03, which derives visual and touch rectangles from it;
 * Stage 02 carries it into the result untouched and never insets a structural rectangle by it.
 */
data class GeometrySpacing(
    val horizontal: Double = 0.0,
    val vertical: Double = 0.0,
)

/**
 * Validated user overrides, applied on top of the profile's policies.
 *
 * These are the user-facing sliders. They are multiplicative and role-scoped: nothing here can
 * introduce a new row, reorder rows, or change what a row is.
 */
data class GeometryOverrides(
    val rowHeightScaleByRole: Map<SemanticRowRole, Double> = emptyMap(),
    val itemWidthScaleByRole: Map<SemanticRowRole, Double> = emptyMap(),
) {
    fun rowHeightScale(role: SemanticRowRole): Double = rowHeightScaleByRole[role] ?: 1.0

    fun itemWidthScale(role: SemanticRowRole): Double = itemWidthScaleByRole[role] ?: 1.0
}

/** The complete, immutable input to one solve. */
data class GeometrySolverInput(
    val availableWidth: Double,
    val rows: List<GeometryRow>,
    val framePolicy: FramePolicy,
    val rowHeightPolicy: RowHeightPolicy = RowHeightPolicy(),
    val gapPolicy: BoundaryGapPolicy = BoundaryGapPolicy(),
    val widthPolicy: RowWidthPolicy = RowWidthPolicy(),
    val overrides: GeometryOverrides = GeometryOverrides(),
    val insets: GeometryInsets = GeometryInsets(),
    val spacingByRole: Map<SemanticRowRole, GeometrySpacing> = emptyMap(),
    val orientation: GeometryOrientation = GeometryOrientation.PORTRAIT,
) {
    fun spacingFor(role: SemanticRowRole): GeometrySpacing = spacingByRole[role] ?: GeometrySpacing()
}
