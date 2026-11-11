/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.settings.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.keyboard.KeyboardProfile
import dev.patrickgold.florisboard.ime.landscapeinput.LandscapeInputUiMode
import dev.patrickgold.florisboard.ime.onehanded.OneHandedMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.stringRes

/** Detailed geometry controls, split from [KeyboardScreen] so the main menu stays scannable. */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun KeyboardGeometryScreen() = FlorisScreen {
    title = stringRes(R.string.settings__keyboard_geometry__title)
    previewFieldVisible = true

    content {
        val activeProfileId by prefs.keyboard.activeProfileId.observeAsState()
        val activeProfile = KeyboardProfile.fromId(activeProfileId)
        val profile = prefs.keyboard.profile(activeProfile)

        Preference(
            title = stringRes(R.string.pref__keyboard__active_profile__label),
            summary = stringRes(
                R.string.pref__keyboard__active_profile__summary_single,
                "v" to stringRes(activeProfile.labelRes),
            ),
        )

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_overall_size__label)) {
            DialogSliderPreference(
                primaryPref = profile.heightFactorPortrait,
                secondaryPref = profile.heightFactorLandscape,
                title = stringRes(R.string.pref__keyboard__height_factor__label),
                primaryLabel = stringRes(R.string.screen_orientation__portrait),
                secondaryLabel = stringRes(R.string.screen_orientation__landscape),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 150,
                stepIncrement = 5,
            )
            DialogSliderPreference(
                primaryPref = profile.keySpacingVertical,
                secondaryPref = profile.keySpacingHorizontal,
                title = stringRes(R.string.pref__keyboard__key_spacing__label),
                primaryLabel = stringRes(R.string.screen_orientation__vertical),
                secondaryLabel = stringRes(R.string.screen_orientation__horizontal),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 0.0f,
                max = 10.0f,
                stepIncrement = 0.5f,
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_letter_rows__label)) {
            DialogSliderPreference(
                pref = profile.alphaRowHeightFactor,
                title = stringRes(R.string.pref__keyboard__alpha_row_height__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 150,
                stepIncrement = 5,
            )
            DialogSliderPreference(
                pref = profile.alphaKeyWidth,
                title = stringRes(R.string.pref__keyboard__alpha_key_width__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 80,
                max = 140,
                stepIncrement = 5,
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_number_row__label)) {
            SwitchPreference(
                pref = profile.numberRowHeightIndependent,
                title = stringRes(R.string.pref__keyboard__number_row_height_independent__label),
                summary = stringRes(R.string.pref__keyboard__number_row_height_independent__summary),
            )
            DialogSliderPreference(
                pref = profile.numberRowHeightFactor,
                title = stringRes(R.string.pref__keyboard__number_row_height__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 150,
                stepIncrement = 5,
                enabledIf = { profile.numberRowHeightIndependent.isTrue() },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_space_row__label)) {
            SwitchPreference(
                pref = profile.primaryActionRowHeightIndependent,
                title = stringRes(R.string.pref__keyboard__primary_action_row_height_independent__label),
                summary = stringRes(R.string.pref__keyboard__primary_action_row_height_independent__summary),
            )
            DialogSliderPreference(
                pref = profile.primaryActionRowHeightFactor,
                title = stringRes(R.string.pref__keyboard__primary_action_row_height__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 150,
                stepIncrement = 5,
                enabledIf = { profile.primaryActionRowHeightIndependent.isTrue() },
            )
            DialogSliderPreference(
                pref = profile.primaryActionRowInsetHorizontal,
                title = stringRes(R.string.pref__keyboard__primary_action_row_inset__label),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 0,
                max = 80,
                stepIncrement = 1,
            )
            SwitchPreference(
                pref = profile.primaryActionKeySpacingIndependent,
                title = stringRes(R.string.pref__keyboard__primary_action_key_spacing_independent__label),
                summary = stringRes(R.string.pref__keyboard__primary_action_key_spacing_independent__summary),
            )
            DialogSliderPreference(
                primaryPref = profile.primaryActionKeySpacingVertical,
                secondaryPref = profile.primaryActionKeySpacingHorizontal,
                title = stringRes(R.string.pref__keyboard__primary_action_key_spacing__label),
                primaryLabel = stringRes(R.string.screen_orientation__vertical),
                secondaryLabel = stringRes(R.string.screen_orientation__horizontal),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 0.0f,
                max = 10.0f,
                stepIncrement = 0.5f,
                enabledIf = { profile.primaryActionKeySpacingIndependent.isTrue() },
            )
            DialogSliderPreference(
                pref = profile.primaryActionRowGapAbove,
                title = stringRes(R.string.pref__keyboard__primary_action_row_gap_above__label),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 0,
                max = 40,
                stepIncrement = 1,
            )
            DialogSliderPreference(
                pref = profile.primaryActionRowGapBelow,
                title = stringRes(R.string.pref__keyboard__primary_action_row_gap_below__label),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 0,
                max = 40,
                stepIncrement = 1,
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_utility_rows__label)) {
            DialogSliderPreference(
                pref = profile.bottomRowHeightFactor,
                title = stringRes(R.string.pref__keyboard__bottom_row_height__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 100,
                stepIncrement = 5,
            )
            DialogSliderPreference(
                pref = profile.modKeyWidth,
                title = stringRes(R.string.pref__keyboard__mod_key_width__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 80,
                max = 140,
                stepIncrement = 5,
            )
            DialogSliderPreference(
                profile.modRowUpperGap,
                title = stringRes(R.string.pref__keyboard__mod_row_upper_gap__label),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 0,
                max = 40,
                stepIncrement = 1,
            )
            DialogSliderPreference(
                profile.modRowInnerGap,
                title = stringRes(R.string.pref__keyboard__mod_row_inner_gap__label),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 0,
                max = 20,
                stepIncrement = 1,
            )
            DialogSliderPreference(
                profile.modRowLowerGap,
                title = stringRes(R.string.pref__keyboard__mod_row_lower_gap__label),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 0,
                max = 40,
                stepIncrement = 1,
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_positioning__label)) {
            ListPreference(
                prefs.keyboard.oneHandedMode,
                prefs.keyboard.oneHandedModeEnabled,
                title = stringRes(R.string.pref__keyboard__one_handed_mode__label),
                entries = enumDisplayEntriesOf(OneHandedMode::class),
                summarySwitchDisabled = stringRes(R.string.state__disabled),
            )
            DialogSliderPreference(
                prefs.keyboard.oneHandedModeScaleFactor,
                title = stringRes(R.string.pref__keyboard__one_handed_mode_scale_factor__label),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 70,
                max = 90,
                stepIncrement = 1,
                enabledIf = { prefs.keyboard.oneHandedModeEnabled.isTrue() },
            )
            ListPreference(
                prefs.keyboard.landscapeInputUiMode,
                title = stringRes(R.string.pref__keyboard__landscape_input_ui_mode__label),
                entries = enumDisplayEntriesOf(LandscapeInputUiMode::class),
            )
            DialogSliderPreference(
                primaryPref = prefs.keyboard.bottomOffsetPortrait,
                secondaryPref = prefs.keyboard.bottomOffsetLandscape,
                title = stringRes(R.string.pref__keyboard__bottom_offset__label),
                primaryLabel = stringRes(R.string.screen_orientation__portrait),
                secondaryLabel = stringRes(R.string.screen_orientation__landscape),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 0,
                max = 60,
                stepIncrement = 1,
            )
        }
    }
}
