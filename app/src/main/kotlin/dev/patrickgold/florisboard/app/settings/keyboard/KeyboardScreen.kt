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

import dev.patrickgold.florisboard.app.console.ConsolePanel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.keyboard.KeyboardProfile
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.text.key.KeyHintMode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun KeyboardScreen() = FlorisScreen {
    panel = ConsolePanel.LAYOUT
    dockKeyboard = true
    title = stringRes(R.string.settings__layout__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        val activeProfileId by prefs.keyboard.activeProfileId.observeAsState()
        val scope = rememberCoroutineScope()
        val activeProfile = KeyboardProfile.fromId(activeProfileId)
        val profile = prefs.keyboard.profile(activeProfile)

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_rows_and_keys__label)) {
            Preference(
                title = stringRes(R.string.pref__keyboard__active_profile__label),
                summary = stringRes(
                    R.string.pref__keyboard__active_profile__summary_single,
                    "v" to stringRes(activeProfile.labelRes),
                ),
                // Same switch as the command strip's TEXT/CODE key, so the settings below can be
                // tuned for either profile without opening the keyboard first.
                onClick = { scope.launch { prefs.keyboard.activeProfileId.set(activeProfile.toggled().id) } },
            )
            SwitchPreference(
                profile.numberRow,
                title = stringRes(R.string.pref__keyboard__number_row__label),
                summary = stringRes(R.string.pref__keyboard__number_row__summary),
            )
            ListPreference(
                listPref = prefs.keyboard.hintedNumberRowMode,
                switchPref = prefs.keyboard.hintedNumberRowEnabled,
                title = stringRes(R.string.pref__keyboard__hinted_number_row_mode__label),
                summarySwitchDisabled = stringRes(R.string.state__disabled),
                entries = enumDisplayEntriesOf(KeyHintMode::class),
                enabledIf = { profile.numberRow.isFalse() },
            )
            ListPreference(
                listPref = prefs.keyboard.hintedSymbolsMode,
                switchPref = prefs.keyboard.hintedSymbolsEnabled,
                title = stringRes(R.string.pref__keyboard__hinted_symbols_mode__label),
                summarySwitchDisabled = stringRes(R.string.state__disabled),
                entries = enumDisplayEntriesOf(KeyHintMode::class),
            )
            SwitchPreference(
                prefs.keyboard.utilityKeyEnabled,
                title = stringRes(R.string.pref__keyboard__utility_key_enabled__label),
                summary = stringRes(R.string.pref__keyboard__utility_key_enabled__summary),
            )
            ListPreference(
                prefs.keyboard.utilityKeyAction,
                title = stringRes(R.string.pref__keyboard__utility_key_action__label),
                entries = enumDisplayEntriesOf(UtilityKeyAction::class),
                visibleIf = { prefs.keyboard.utilityKeyEnabled isEqualTo true },
            )
            ListPreference(
                prefs.keyboard.spaceBarMode,
                title = stringRes(R.string.pref__keyboard__space_bar_mode__label),
                entries = enumDisplayEntriesOf(SpaceBarMode::class),
            )
            SwitchPreference(
                prefs.keyboard.spaceBarSwitchesToCharacters,
                title = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__label),
                summary = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__summary),
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_customization__label)) {
            Preference(
                title = stringRes(R.string.settings__keyboard_geometry__title),
                summary = stringRes(R.string.settings__keyboard_geometry__summary),
                onClick = { navController.navigate(Routes.Settings.KeyboardGeometry) },
            )
            Preference(
                title = stringRes(R.string.pref__keyboard__key_customization__title),
                summary = stringRes(R.string.pref__keyboard__key_customization__summary),
                onClick = { navController.navigate(Routes.Settings.KeyCustomization) },
            )
            Preference(
                title = stringRes(R.string.settings__localization__title),
                summary = stringRes(R.string.settings__localization__summary),
                onClick = { navController.navigate(Routes.Settings.Localization) },
            )
        }

    }
}
