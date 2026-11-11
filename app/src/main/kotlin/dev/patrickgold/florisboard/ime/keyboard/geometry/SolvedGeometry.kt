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
 * Stage 02 solved geometry: the immutable result of one solve.
 *
 * ## Coordinate convention
 *
 * One convention, used everywhere in this file: **integer pixels, origin at the frame's top-left,
 * x growing right, y growing down.** There is no second unit and no second origin. Rectangles are
 * half-open — `right` and `bottom` are the first pixel *outside* the rectangle — so adjacent
 * rectangles share an edge value without overlapping.
 *
 * ## Rounding ownership
 *
 * [KeyboardGeometrySolver] owns rounding, exclusively and at exactly one point. It solves in
 * continuous `Double` space, then rounds **edges** — never widths or heights — from an exact
 * running total. A size is always `right - left`, derived after rounding rather than rounded
 * itself. That is what keeps the allocation conservative: the last edge of a run rounds the exact
 * total, so error cannot accumulate across items or rows.
 *
 * Consumers must not re-round. A consumer that needs a sub-pixel value should read the declared
 * inputs carried alongside the rectangles and derive it there.
 *
 * ## What is not here
 *
 * These are *structural* rectangles: the space each row and item is allocated. Touch, visual, and
 * popup rectangles are derived from them in Stage 03, which is why the declared inputs those
 * derivations need travel with the result rather than being applied to it.
 */

/** A half-open integer-pixel rectangle in frame coordinates. */
data class GeometryRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    /** True when this rectangle lies entirely inside [other]. */
    fun isContainedBy(other: GeometryRect): Boolean =
        left >= other.left && top >= other.top && right <= other.right && bottom <= other.bottom

    /** True when this rectangle shares interior area with [other]. Touching edges do not overlap. */
    fun overlaps(other: GeometryRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/** A placed key or spacer. */
data class SolvedItem(
    val stableId: String,
    val kind: GeometryItemKind,
    val bounds: GeometryRect,
    /** Declared, not applied. Stage 03 inflates the visual rectangle by this. */
    val declaredHeightFactor: Double,
    /** Declared, not applied. Stage 03 resolves the visual rectangle's vertical offset with it. */
    val declaredVerticalAlignment: VerticalAlignment,
)

/** A placed row. [bounds] spans the full content width; [items] sit inside it. */
data class SolvedRow(
    val stableId: String,
    val role: SemanticRowRole,
    val bounds: GeometryRect,
    val items: List<SolvedItem>,
    /** Declared, not applied. Stage 03 insets visual rectangles by this. */
    val declaredSpacing: GeometrySpacing,
)

/** Which declaration in a [RoleBlockGaps] produced a gap. */
enum class GeometryGapKind {
    ABOVE_BLOCK,
    WITHIN_BLOCK,
    BELOW_BLOCK,
}

/**
 * A boundary gap, placed explicitly rather than folded into a row's height.
 *
 * @param role the role of the block that declared it.
 * @param rowAbove stable ID of the row above, or null at the top of the content area.
 * @param rowBelow stable ID of the row below, or null at the bottom of the content area.
 */
data class SolvedGap(
    val kind: GeometryGapKind,
    val role: SemanticRowRole,
    val rowAbove: String?,
    val rowBelow: String?,
    val bounds: GeometryRect,
)

/**
 * The complete solved geometry.
 *
 * @param frame the outer rectangle, including [insets].
 * @param content the rectangle rows and gaps are laid out in: [frame] less [insets].
 */
data class SolvedGeometry(
    val frame: GeometryRect,
    val content: GeometryRect,
    val rows: List<SolvedRow>,
    val gaps: List<SolvedGap>,
    val insets: GeometryInsets,
    val orientation: GeometryOrientation,
) {
    /** The row with [stableId], or null. */
    fun row(stableId: String): SolvedRow? = rows.firstOrNull { it.stableId == stableId }

    /** The item with [stableId], or null. Item IDs are unique across the whole result. */
    fun item(stableId: String): SolvedItem? =
        rows.firstNotNullOfOrNull { row -> row.items.firstOrNull { it.stableId == stableId } }

    /** Every row carrying [role], in arrangement order. */
    fun rowsWithRole(role: SemanticRowRole): List<SolvedRow> = rows.filter { it.role == role }
}

/**
 * The outcome of a solve.
 *
 * A solve either produces geometry that satisfies every invariant, or it explains why it cannot.
 * It never returns a partially valid result: clipping, coercion, and "close enough" are how the
 * legacy authorities lost the ability to state what they meant.
 */
sealed interface GeometrySolution {
    data class Solved(val geometry: SolvedGeometry) : GeometrySolution

    /** @param reasons every failed constraint, not just the first. */
    data class Unsatisfiable(val reasons: List<String>) : GeometrySolution
}
