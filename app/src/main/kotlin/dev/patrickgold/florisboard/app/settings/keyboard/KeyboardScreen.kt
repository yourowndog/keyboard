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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.input.CapitalizationBehavior
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.landscapeinput.LandscapeInputUiMode
import dev.patrickgold.florisboard.ime.onehanded.OneHandedMode
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.text.key.KeyHintMode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun KeyboardScreen() = FlorisScreen {
    title = stringRes(R.string.settings__keyboard__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        SwitchPreference(
            prefs.keyboard.numberRow,
            title = stringRes(R.string.pref__keyboard__number_row__label),
            summary = stringRes(R.string.pref__keyboard__number_row__summary),
        )
        ListPreference(
            listPref = prefs.keyboard.hintedNumberRowMode,
            switchPref = prefs.keyboard.hintedNumberRowEnabled,
            title = stringRes(R.string.pref__keyboard__hinted_number_row_mode__label),
            summarySwitchDisabled = stringRes(R.string.state__disabled),
            entries = enumDisplayEntriesOf(KeyHintMode::class),
            enabledIf = { prefs.keyboard.numberRow.isFalse() }
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
        ListPreference(
            prefs.keyboard.capitalizationBehavior,
            title = stringRes(R.string.pref__keyboard__capitalization_behavior__label),
            entries = enumDisplayEntriesOf(CapitalizationBehavior::class),
        )
        DialogSliderPreference(
            primaryPref = prefs.keyboard.fontSizeMultiplierPortrait,
            secondaryPref = prefs.keyboard.fontSizeMultiplierLandscape,
            title = stringRes(R.string.pref__keyboard__font_size_multiplier__label),
            primaryLabel = stringRes(R.string.screen_orientation__portrait),
            secondaryLabel = stringRes(R.string.screen_orientation__landscape),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 50,
            max = 150,
            stepIncrement = 5,
        )
        ListPreference(
            listPref = prefs.keyboard.incognitoDisplayMode,
            title = stringRes(R.string.pref__keyboard__incognito_indicator__label),
            entries = enumDisplayEntriesOf(IncognitoDisplayMode::class),
        )

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_layout__label)) {
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
                primaryPref = prefs.keyboard.heightFactorPortrait,
                secondaryPref = prefs.keyboard.heightFactorLandscape,
                title = stringRes(R.string.pref__keyboard__height_factor__label),
                primaryLabel = stringRes(R.string.screen_orientation__portrait),
                secondaryLabel = stringRes(R.string.screen_orientation__landscape),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 150,
                stepIncrement = 5,
            )
            DialogSliderPreference(
                primaryPref = prefs.keyboard.keySpacingVertical,
                secondaryPref = prefs.keyboard.keySpacingHorizontal,
                title = stringRes(R.string.pref__keyboard__key_spacing__label),
                primaryLabel = stringRes(R.string.screen_orientation__vertical),
                secondaryLabel = stringRes(R.string.screen_orientation__horizontal),
                valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
                min = 0.0f,
                max = 10.0f,
                stepIncrement = 0.5f,
            )
            val lcarsGeometryEnabled by prefs.keyboard.lcarsGeometryEnabled.observeAsState()
            SwitchPreference(
                prefs.keyboard.lcarsGeometryEnabled,
                title = stringRes(R.string.pref__keyboard__lcars_geometry_enabled__label),
                summary = stringRes(R.string.pref__keyboard__lcars_geometry_enabled__summary),
            )
            if (lcarsGeometryEnabled) {
                PreferenceGroup(title = stringRes(R.string.pref__keyboard__lcars_geometry__label)) {
                    LCARSValueSlider(
                        preference = prefs.keyboard.lcarsDigitsHeightScale,
                        enabledPref = prefs.keyboard.lcarsDigitsHeightEnabled,
                        title = "Digits – Height scale",
                        defaultValue = LcarsDefaults.DIGITS_HEIGHT,
                        valueRange = 0.10f..2.00f,
                        valueFormatter = { value -> String.format(Locale.US, "%.2f", value) },
                    )
                    LCARSValueSlider(
                        preference = prefs.keyboard.lcarsDigitsPillRatio,
                        enabledPref = prefs.keyboard.lcarsDigitsPillEnabled,
                        title = "Digits – Pill ratio",
                        defaultValue = LcarsDefaults.DIGITS_PILL,
                        valueRange = 1.00f..3.00f,
                        valueFormatter = { value -> String.format(Locale.US, "%.2f", value) },
                    )
                    LCARSValueSlider(
                        preference = prefs.keyboard.lcarsOthersHeightScale,
                        enabledPref = prefs.keyboard.lcarsOthersHeightEnabled,
                        title = "Others – Height scale",
                        defaultValue = LcarsDefaults.OTHERS_HEIGHT,
                        valueRange = 0.10f..2.00f,
                        valueFormatter = { value -> String.format(Locale.US, "%.2f", value) },
                    )
                    LCARSValueSlider(
                        preference = prefs.keyboard.lcarsOthersPillRatio,
                        enabledPref = prefs.keyboard.lcarsOthersPillEnabled,
                        title = "Others – Pill ratio",
                        defaultValue = LcarsDefaults.OTHERS_PILL,
                        valueRange = 1.00f..3.00f,
                        valueFormatter = { value -> String.format(Locale.US, "%.2f", value) },
                    )
                }
                PreferenceGroup(title = stringRes(R.string.pref__keyboard__lcars_spacing__label)) {
                    LCARSValueSlider(
                        preference = prefs.keyboard.lcarsGapHorizontalDp,
                        enabledPref = prefs.keyboard.lcarsAdvancedSpacingEnabled,
                        title = "Horizontal gap",
                        defaultValue = LcarsDefaults.ADV_SPACING_DP,
                        valueRange = -8.0f..16.0f,
                        valueFormatter = { value -> String.format(Locale.US, "%.2f dp", value) },
                    )
                    LCARSValueSlider(
                        preference = prefs.keyboard.lcarsGapVerticalDp,
                        enabledPref = prefs.keyboard.lcarsAdvancedSpacingEnabled,
                        title = "Vertical gap",
                        defaultValue = LcarsDefaults.ADV_SPACING_DP,
                        valueRange = -4.0f..12.0f,
                        valueFormatter = { value -> String.format(Locale.US, "%.2f dp", value) },
                    )
                }
            }
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

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_keypress__label)) {
            Preference(
                title = stringRes(R.string.settings__input_feedback__title),
                onClick = { navController.navigate(Routes.Settings.InputFeedback) },
            )
            SwitchPreference(
                prefs.keyboard.popupEnabled,
                title = stringRes(R.string.pref__keyboard__popup_enabled__label),
                summary = stringRes(R.string.pref__keyboard__popup_enabled__summary),
            )
            SwitchPreference(
                prefs.keyboard.mergeHintPopupsEnabled,
                title = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__label),
                summary = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__summary),
            )
            DialogSliderPreference(
                prefs.keyboard.longPressDelay,
                title = stringRes(R.string.pref__keyboard__long_press_delay__label),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = 100,
                max = 700,
                stepIncrement = 10,
            )
            SwitchPreference(
                prefs.keyboard.spaceBarSwitchesToCharacters,
                title = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__label),
                summary = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__summary),
            )
        }
    }
}

import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.patrickgold.florisboard.app.LcarsDefaults

@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.LCARSValueSlider(
    preference: PreferenceData<Float>,
    enabledPref: PreferenceData<Boolean>,
    title: String,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String,
) {
    val scope = rememberCoroutineScope()
    val prefValue by preference.observeAsState()
    val isEnabled by enabledPref.observeAsState()

    fun clampAndRound(value: Float): Float {
        val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
        return (clamped * 100f).roundToInt() / 100f
    }

    var sliderValue by rememberSaveable(prefValue) {
        mutableFloatStateOf(clampAndRound(prefValue))
    }

    LaunchedEffect(prefValue) {
        val rounded = clampAndRound(prefValue)
        if (sliderValue != rounded) {
            sliderValue = rounded
        }
    }

    val formattedValue = if (isEnabled) {
        valueFormatter(sliderValue)
    } else {
        "${valueFormatter(defaultValue)} (default)"
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { contentDescription = "LCARS $title $formattedValue" }
        ) {
            Checkbox(
                checked = isEnabled,
                onCheckedChange = { newIsEnabled ->
                    scope.launch {
                        enabledPref.set(newIsEnabled)
                        if (!newIsEnabled) {
                            preference.set(defaultValue)
                        }
                    }
                }
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = formattedValue,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        Slider(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            value = sliderValue,
            onValueChange = { value ->
                sliderValue = clampAndRound(value)
            },
            valueRange = valueRange,
            steps = 0,
            enabled = isEnabled,
            onValueChangeFinished = {
                scope.launch {
                    preference.set(sliderValue)
                }
            },
        )
    }
}

@Composable
private fun ValueChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    SuggestionChip(
        modifier = modifier,
        onClick = {},
        enabled = false,
        shape = MaterialTheme.shapes.small,
        colors = SuggestionChipDefaults.suggestionChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = SuggestionChipDefaults.suggestionChipBorder(
            enabled = false,
        ),
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        },
    )
}
