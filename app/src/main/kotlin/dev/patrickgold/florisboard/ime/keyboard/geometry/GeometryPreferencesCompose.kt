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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.KeyboardProfile
import dev.patrickgold.jetpref.datastore.model.observeAsState

/**
 * Reads the geometry preferences once, into one value.
 *
 * Both geometry consumers call this, so a preference cannot be observed by one of them and missed
 * by the other — the failure mode that let the frame and the inner layout disagree about what the
 * user had asked for. Because the result is a data class, downstream `remember` blocks can key on
 * this single value: a preference change produces a different [GeometryPreferences], which
 * invalidates exactly the caches that depend on geometry and nothing else.
 *
 * @param rowBaseHeight one normalized full row, in whatever unit the caller wants its geometry in.
 * @param convertLength maps a stored dp length into that same unit. The frame works in dp and the
 *   inner layout in pixels; nothing else about the two calls differs.
 */
@Composable
fun rememberGeometryPreferences(
    rowBaseHeight: Double,
    convertLength: (Float) -> Double,
): GeometryPreferences {
    val prefs by FlorisPreferenceStore
    // Geometry is profile-scoped as of Stage 04. Observing the active profile id here is what makes
    // a profile switch behave like any other geometry change: `observeAsState` is `asFlow()` fed
    // into `collectAsState`, which keys on the flow, so pointing these reads at a different
    // profile's preferences re-subscribes them rather than stranding the previous values.
    val activeProfileId by prefs.keyboard.activeProfileId.observeAsState()
    val profile = prefs.keyboard.profile(KeyboardProfile.fromId(activeProfileId))

    val alphaRowHeight by profile.alphaRowHeightFactor.observeAsState()
    val utilityRowHeight by profile.bottomRowHeightFactor.observeAsState()
    val alphaKeyWidth by profile.alphaKeyWidth.observeAsState()
    val utilityKeyWidth by profile.modKeyWidth.observeAsState()
    val spacingH by profile.keySpacingHorizontal.observeAsState()
    val spacingV by profile.keySpacingVertical.observeAsState()
    val gapAbove by profile.modRowUpperGap.observeAsState()
    val gapWithin by profile.modRowInnerGap.observeAsState()
    val gapBelow by profile.modRowLowerGap.observeAsState()

    return GeometryPreferences(
        rowBaseHeightPx = rowBaseHeight,
        alphaRowHeightPercent = alphaRowHeight,
        utilityRowHeightPercent = utilityRowHeight,
        alphaKeyWidthPercent = alphaKeyWidth,
        utilityKeyWidthPercent = utilityKeyWidth,
        // One preference, one visible gap. KeyBoundsDerivation charges half of it to each of the
        // two keycaps that share the gap, rather than the whole of it to both.
        keySpacingHorizontalPx = convertLength(spacingH),
        keySpacingVerticalPx = convertLength(spacingV),
        utilityGapAbovePx = convertLength(gapAbove.toFloat()),
        utilityGapWithinPx = convertLength(gapWithin.toFloat()),
        utilityGapBelowPx = convertLength(gapBelow.toFloat()),
    )
}
