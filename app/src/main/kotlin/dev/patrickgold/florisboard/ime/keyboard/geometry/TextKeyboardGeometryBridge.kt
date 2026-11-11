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
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import dev.patrickgold.florisboard.lib.FlorisRect

/**
 * Stage 03: the only road between a [TextKeyboard] and its pixels.
 *
 * Both geometry consumers come through here. [frameHeight] answers "how tall is the keyboard", the
 * question `FlorisImeSizing` asks; [solve] plus [applyTo] answers "where does each key go", the
 * question the Compose layout asks. They share [KeyboardGeometryPolicy.buildInput], so the two can
 * no longer drift: there is one description of the keyboard and two frame policies over it.
 *
 * The bridge is pure. It reads the keyboard and a [GeometryPreferences] snapshot, returns values,
 * and touches no Compose state, no `Context`, and no preference store — which is why every case
 * below, including the fallbacks, is reachable from a unit test.
 */
object TextKeyboardGeometryBridge {

    /**
     * The outcome of asking for geometry.
     *
     * A solve either succeeded on the requested input, succeeded on the canonical fallback, or
     * could not be performed at all. [diagnostics] always explains a departure from the first case,
     * and the caller is expected to log it. What never happens is the fourth case: partially
     * applied or silently corrected geometry.
     */
    sealed interface Result {
        val diagnostics: List<String>

        /** Solved as asked. */
        data class Solved(
            val geometry: SolvedGeometry,
            override val diagnostics: List<String> = emptyList(),
        ) : Result

        /**
         * The requested preferences were unsatisfiable; this is the canonical baseline instead.
         *
         * Reached by discarding the user's scales, spacing and gaps and re-solving the same rows.
         * The keyboard is usable and looks like a fresh install rather than like whatever the
         * unsatisfiable input half-produced.
         */
        data class Fallback(
            val geometry: SolvedGeometry,
            override val diagnostics: List<String>,
        ) : Result

        /** Not solvable even canonically — an empty or sentinel keyboard, or a degenerate frame. */
        data class Unavailable(override val diagnostics: List<String>) : Result
    }

    // -- Description -----------------------------------------------------------------------------

    /**
     * Describes [keyboard] as solver rows.
     *
     * Roles come from the keyboard's declared semantics, never from a row's index or from what its
     * keys happen to contain. Widths come from [KeyboardGeometryPolicy], except where a Layout Pack
     * author stated a unit explicitly — authored asymmetry is preserved exactly as written, and an
     * authored key never grows, because growth is a policy decision the author did not make.
     */
    fun describeRows(keyboard: TextKeyboard): List<GeometryRow> {
        val semantics = keyboard.semanticRows
        if (semantics.isEmpty()) return emptyList()
        return semantics.mapIndexed { rowIndex, row ->
            val keys = keyboard.arrangement.getOrNull(rowIndex) ?: emptyArray()
            GeometryRow(
                stableId = row.stableId,
                role = row.role,
                items = keys.mapIndexed { keyIndex, key ->
                    val authored = key.authoredWidthUnits
                    val code = key.computedData.code
                    GeometryItem(
                        stableId = "${row.stableId}#$keyIndex",
                        widthUnits = when {
                            authored != null -> authored.toDouble().coerceAtLeast(0.0)
                            // A key the evaluator hid collapses to nothing rather than leaving a
                            // hole its neighbours have to be told about.
                            !key.isVisible -> 0.0
                            else -> KeyboardGeometryPolicy.canonicalWidthUnits(row.role, code)
                        },
                        growWeight = if (authored != null) {
                            0.0
                        } else {
                            KeyboardGeometryPolicy.canonicalGrowWeight(row.role, code)
                        },
                        kind = if (key.isStructuralSpacer) GeometryItemKind.SPACER else GeometryItemKind.KEY,
                    )
                },
            )
        }
    }

    // -- Solving ---------------------------------------------------------------------------------

    /**
     * Solves [keyboard] under [prefs], falling back to the canonical baseline if it cannot.
     *
     * [framePolicy] is the caller's only degree of freedom: `Intrinsic` decides a frame height from
     * the row base height, `FitToHeight` shares a height that has already been decided.
     */
    fun solve(
        keyboard: TextKeyboard,
        prefs: GeometryPreferences,
        availableWidth: Double,
        framePolicy: FramePolicy,
        orientation: GeometryOrientation = GeometryOrientation.PORTRAIT,
    ): Result {
        val rows = describeRows(keyboard)
        if (rows.isEmpty()) {
            return Result.Unavailable(listOf("keyboard '${keyboard.mode}' declares no rows"))
        }
        if (!availableWidth.isFinite() || availableWidth <= 0.0) {
            return Result.Unavailable(listOf("available width ${availableWidth}px is not usable"))
        }

        val requested = KeyboardGeometryPolicy.buildInput(rows, prefs, availableWidth, framePolicy, orientation)
        when (val solution = KeyboardGeometrySolver.solve(requested)) {
            is GeometrySolution.Solved -> return Result.Solved(solution.geometry)
            is GeometrySolution.Unsatisfiable -> {
                val canonical = KeyboardGeometryPolicy.buildInput(
                    rows = rows,
                    prefs = canonicalPreferences(prefs),
                    availableWidth = availableWidth,
                    framePolicy = framePolicy,
                    orientation = orientation,
                )
                return when (val retry = KeyboardGeometrySolver.solve(canonical)) {
                    is GeometrySolution.Solved -> Result.Fallback(
                        geometry = retry.geometry,
                        diagnostics = listOf(
                            "geometry preferences are unsatisfiable, using the canonical baseline",
                        ) + solution.reasons,
                    )
                    is GeometrySolution.Unsatisfiable -> Result.Unavailable(
                        listOf("even the canonical baseline is unsatisfiable") + retry.reasons,
                    )
                }
            }
        }
    }

    /** [prefs] stripped back to the shipped defaults, keeping only the row base height. */
    private fun canonicalPreferences(prefs: GeometryPreferences) =
        GeometryPreferences(rowBaseHeightPx = prefs.rowBaseHeightPx)

    /**
     * The keyboard's intrinsic frame height, or null if it has no solvable geometry.
     *
     * This is the same solve the layout performs, asked a different question — not a parallel
     * arithmetic that has to be kept in step by hand.
     */
    fun frameHeight(
        keyboard: TextKeyboard,
        prefs: GeometryPreferences,
        availableWidth: Double,
        orientation: GeometryOrientation = GeometryOrientation.PORTRAIT,
    ): Int? {
        val result = solve(
            keyboard = keyboard,
            prefs = prefs,
            availableWidth = availableWidth,
            framePolicy = FramePolicy.Intrinsic(prefs.sanitized().rowBaseHeightPx),
            orientation = orientation,
        )
        return when (result) {
            is Result.Solved -> result.geometry.frame.height
            is Result.Fallback -> result.geometry.frame.height
            is Result.Unavailable -> null
        }
    }

    // -- Application -----------------------------------------------------------------------------

    /**
     * Writes [geometry] onto [keyboard]'s keys as touch and visible rectangles.
     *
     * Nothing is computed here that was not solved: this walks the solved result and calls
     * [KeyBoundsDerivation] for each layer. Rows and items are matched by position, which is safe
     * because [describeRows] produced the solved result from this same arrangement.
     */
    fun applyTo(
        keyboard: TextKeyboard,
        geometry: SolvedGeometry,
        extendTouchBoundariesDownwards: Boolean,
    ) {
        val lastRowIndex = geometry.rows.lastIndex
        for ((rowIndex, solvedRow) in geometry.rows.withIndex()) {
            val keys = keyboard.arrangement.getOrNull(rowIndex) ?: continue
            val extendToBottomEdge = extendTouchBoundariesDownwards && rowIndex == lastRowIndex
            for ((keyIndex, solvedItem) in solvedRow.items.withIndex()) {
                val key = keys.getOrNull(keyIndex) ?: continue
                KeyBoundsDerivation.touchBounds(
                    item = solvedItem,
                    rowHeight = solvedRow.bounds.height,
                    extendToBottomEdge = extendToBottomEdge,
                    into = key.touchBounds,
                )
                KeyBoundsDerivation.visibleBounds(
                    item = solvedItem,
                    spacing = solvedRow.declaredSpacing,
                    into = key.visibleBounds,
                )
            }
        }
    }

    // -- Derived reference -----------------------------------------------------------------------

    /**
     * The reference keycap other UI is sized against — popups, key labels, and the debug overlay.
     *
     * It is a real solved cell rather than an independently guessed rectangle: the first cell of
     * the first alpha row, or of the first row if the surface has no alpha rows. Popup size and
     * anchoring therefore move with the solved geometry instead of drifting from it.
     */
    fun referenceCell(geometry: SolvedGeometry, into: FlorisRect): FlorisRect? {
        val row = geometry.rowsWithRole(SemanticRowRole.ALPHA).firstOrNull()
            ?: geometry.rows.firstOrNull()
            ?: return null
        val item = row.items.firstOrNull() ?: return null
        return KeyBoundsDerivation.visibleBounds(item, row.declaredSpacing, into)
    }
}
