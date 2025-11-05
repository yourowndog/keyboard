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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import dev.patrickgold.florisboard.ime.text.keyboard.LcarsRuntime
import dev.patrickgold.florisboard.ime.text.keyboard.LocalLcarsRuntime
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import java.util.Locale
import kotlin.math.floor

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
                val scope = rememberCoroutineScope()
                val digitsHeight by prefs.keyboard.lcarsDigitsHeightScale.observeAsState()
                val digitsPill by prefs.keyboard.lcarsDigitsPillRatio.observeAsState()
                val lettersHeight by prefs.keyboard.lcarsOthersHeightScale.observeAsState()
                val lettersPill by prefs.keyboard.lcarsOthersPillRatio.observeAsState()
                val spacingHorizontal by prefs.keyboard.lcarsGapHorizontalDp.observeAsState()
                val spacingVertical by prefs.keyboard.lcarsGapVerticalDp.observeAsState()

                val persistedDigitsHeightEnabled by prefs.keyboard.lcarsDigitsHeightEnabled.observeAsState()
                val persistedLettersHeightEnabled by prefs.keyboard.lcarsOthersHeightEnabled.observeAsState()
                val persistedDigitsPillEnabled by prefs.keyboard.lcarsDigitsPillEnabled.observeAsState()
                val persistedLettersPillEnabled by prefs.keyboard.lcarsOthersPillEnabled.observeAsState()
                val persistedSpacingEnabled by prefs.keyboard.lcarsAdvancedSpacingEnabled.observeAsState()

                var digitsHeightEnabled by rememberSaveable { mutableStateOf(persistedDigitsHeightEnabled) }
                var lettersHeightEnabled by rememberSaveable { mutableStateOf(persistedLettersHeightEnabled) }
                var digitsPillEnabled by rememberSaveable { mutableStateOf(persistedDigitsPillEnabled) }
                var lettersPillEnabled by rememberSaveable { mutableStateOf(persistedLettersPillEnabled) }
                var spacingEnabled by rememberSaveable { mutableStateOf(persistedSpacingEnabled) }

                val digitsHeightRange = 0.10f..2.00f
                val lettersHeightRange = 0.10f..2.00f
                val pillRange = 1.00f..3.00f
                val spacingRange = -20.0f..20.0f
                val spacingValue = ((spacingHorizontal + spacingVertical) / 2.0f)
                    .coerceIn(spacingRange.start, spacingRange.endInclusive)

                val runtimeOverrides = LcarsRuntime(
                    digitsHeight = if (digitsHeightEnabled) {
                        digitsHeight.coerceIn(digitsHeightRange.start, digitsHeightRange.endInclusive)
                    } else {
                        1.0f
                    },
                    othersHeight = if (lettersHeightEnabled) {
                        lettersHeight.coerceIn(lettersHeightRange.start, lettersHeightRange.endInclusive)
                    } else {
                        1.0f
                    },
                    digitsPill = if (digitsPillEnabled) {
                        digitsPill.coerceIn(pillRange.start, pillRange.endInclusive)
                    } else {
                        1.6f
                    },
                    othersPill = if (lettersPillEnabled) {
                        lettersPill.coerceIn(pillRange.start, pillRange.endInclusive)
                    } else {
                        1.6f
                    },
                    spacingDp = if (spacingEnabled) spacingValue else 8.0f,
                )

                CompositionLocalProvider(LocalLcarsRuntime provides runtimeOverrides) {
                    PreferenceGroup(title = stringRes(R.string.pref__keyboard__lcars_geometry__label)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LabeledSliderRow(
                            title = "Digits height",
                            value = digitsHeight.coerceIn(digitsHeightRange.start, digitsHeightRange.endInclusive),
                            onChange = { value ->
                                val clamped = value.coerceIn(digitsHeightRange.start, digitsHeightRange.endInclusive)
                                scope.launch { prefs.keyboard.lcarsDigitsHeightScale.set(clamped) }
                            },
                            valueRange = digitsHeightRange,
                            enabled = digitsHeightEnabled,
                            onEnabledChange = { digitsHeightEnabled = it },
                            format = { value -> String.format(Locale.US, "%.2f", value) },
                            resetTo = 1.0f,
                        )
                        LabeledSliderRow(
                            title = "Letters height",
                            value = lettersHeight.coerceIn(lettersHeightRange.start, lettersHeightRange.endInclusive),
                            onChange = { value ->
                                val clamped = value.coerceIn(lettersHeightRange.start, lettersHeightRange.endInclusive)
                                scope.launch { prefs.keyboard.lcarsOthersHeightScale.set(clamped) }
                            },
                            valueRange = lettersHeightRange,
                            enabled = lettersHeightEnabled,
                            onEnabledChange = { lettersHeightEnabled = it },
                            format = { value -> String.format(Locale.US, "%.2f", value) },
                            resetTo = 1.0f,
                        )
                        LabeledSliderRow(
                            title = "Digits pill ratio",
                            value = digitsPill.coerceIn(pillRange.start, pillRange.endInclusive),
                            onChange = { value ->
                                val clamped = value.coerceIn(pillRange.start, pillRange.endInclusive)
                                scope.launch { prefs.keyboard.lcarsDigitsPillRatio.set(clamped) }
                            },
                            valueRange = pillRange,
                            enabled = digitsPillEnabled,
                            onEnabledChange = { digitsPillEnabled = it },
                            format = { value -> String.format(Locale.US, "%.2f", value) },
                            resetTo = 1.6f,
                        )
                        LabeledSliderRow(
                            title = "Letters pill ratio",
                            value = lettersPill.coerceIn(pillRange.start, pillRange.endInclusive),
                            onChange = { value ->
                                val clamped = value.coerceIn(pillRange.start, pillRange.endInclusive)
                                scope.launch { prefs.keyboard.lcarsOthersPillRatio.set(clamped) }
                            },
                            valueRange = pillRange,
                            enabled = lettersPillEnabled,
                            onEnabledChange = { lettersPillEnabled = it },
                            format = { value -> String.format(Locale.US, "%.2f", value) },
                            resetTo = 1.6f,
                        )
                    }
                    }
                    PreferenceGroup(title = stringRes(R.string.pref__keyboard__lcars_spacing__label)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            LabeledSliderRow(
                            title = "Advanced spacing (dp)",
                            value = spacingValue,
                            onChange = { value ->
                                val clamped = value.coerceIn(spacingRange.start, spacingRange.endInclusive)
                                scope.launch {
                                    prefs.keyboard.lcarsGapHorizontalDp.set(clamped)
                                    prefs.keyboard.lcarsGapVerticalDp.set(clamped)
                                }
                            },
                            valueRange = spacingRange,
                            enabled = spacingEnabled,
                            onEnabledChange = { spacingEnabled = it },
                            format = { value -> String.format(Locale.US, "%.1f dp", value) },
                            resetTo = 8.0f,
                        )
                    }
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



@Composable
private fun LabeledSliderRow(
    title: String,
    value: Float,
    onChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    format: (Float) -> String = { value -> String.format(Locale.US, "%.2f", value) },
    step: Float? = null,
    resetTo: Float? = null,
) {
    val onColor = MaterialTheme.colorScheme.onBackground
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = onColor, modifier = Modifier.weight(1f))
        Text(format(value), color = onColor, modifier = Modifier.padding(end = 8.dp))
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
        if (resetTo != null) {
            IconButton(onClick = { onEnabledChange(true); onChange(resetTo) }) {
                Icon(Icons.Filled.Restore, contentDescription = "Reset")
            }
        }
    }
    val rawSteps = step?.let {
        val intervalCount = floor((valueRange.endInclusive - valueRange.start) / it).toInt() - 1
        intervalCount.coerceAtLeast(0)
    } ?: 0
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = valueRange,
        enabled = enabled,
        steps = rawSteps,
        modifier = Modifier.fillMaxWidth()
    )
}
