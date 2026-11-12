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

package dev.patrickgold.florisboard.app.settings.theme

import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.ime.theme.backdrop.BackdropId
import dev.patrickgold.florisboard.ime.theme.backdrop.BackdropStore
import dev.patrickgold.florisboard.ime.theme.keyskin.KeySkinId
import dev.patrickgold.florisboard.ime.theme.keyskin.KeySkinStore
import androidx.compose.material.icons.filled.GridView
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showLongToastSync
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.ext.AddonManagementReferenceBox
import dev.patrickgold.florisboard.app.ext.ExtensionListScreenType
import dev.patrickgold.florisboard.ime.theme.ThemeManager
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.themeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.LocalTimePickerPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.isMaterialYou
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun ThemeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__theme__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val navController = LocalNavController.current
    val themeManager by context.themeManager()

    @Composable
    fun ThemeManager.getThemeLabel(id: ExtensionComponentName): String {
        val configs by indexedThemeConfigs.collectAsState()
        configs.first[id]?.let { return it.label }
        return id.toString()
    }

    content {
        val dayThemeId by prefs.theme.dayThemeId.observeAsState()
        val nightThemeId by prefs.theme.nightThemeId.observeAsState()

        ListPreference(
            prefs.theme.mode,
            icon = Icons.Default.BrightnessAuto,
            title = stringRes(R.string.pref__theme__mode__label),
            entries = enumDisplayEntriesOf(ThemeMode::class),
        )
        Preference(
            icon = Icons.Default.LightMode,
            title = stringRes(R.string.pref__theme__day),
            summary = themeManager.getThemeLabel(dayThemeId),
            enabledIf = { prefs.theme.mode isNotEqualTo ThemeMode.ALWAYS_NIGHT },
            onClick = {
                navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_DAY))
            },
        )
        Preference(
            icon = Icons.Default.DarkMode,
            title = stringRes(R.string.pref__theme__night),
            summary = themeManager.getThemeLabel(nightThemeId),
            enabledIf = { prefs.theme.mode isNotEqualTo ThemeMode.ALWAYS_DAY },
            onClick = {
                navController.navigate(Routes.Settings.ThemeManager(ThemeManagerScreenAction.SELECT_NIGHT))
            },
        )
        LocalTimePickerPreference(
            pref = prefs.theme.sunriseTime,
            title = stringRes(R.string.pref__theme__sunrise_time__label),
            icon = Icons.Default.WbTwilight,
            enabledIf = { prefs.theme.mode isEqualTo ThemeMode.FOLLOW_TIME },
        )
        LocalTimePickerPreference(
            pref = prefs.theme.sunsetTime,
            title = stringRes(R.string.pref__theme__sunset_time__label),
            icon = Icons.Default.Brightness2,
            enabledIf = { prefs.theme.mode isEqualTo ThemeMode.FOLLOW_TIME },
        )
        ColorPickerPreference(
            pref = prefs.theme.accentColor,
            title = stringRes(R.string.pref__theme__theme_accent_color__label),
            defaultValueLabel = stringRes(R.string.action__default),
            icon = Icons.Default.ColorLens,
            defaultColors = ColorMappings.colors,
            showAlphaSlider = false,
            enableAdvancedLayout = false,
            colorOverride = {
                if (it.isMaterialYou(context)) {
                    Color.Unspecified
                } else {
                    it
                }
            }
        )

        DialogSliderPreference(
            prefs.theme.windowBackgroundOpacity,
            icon = Icons.Default.Opacity,
            title = stringRes(R.string.pref__theme__window_background_opacity__label),
            summary = { stringRes(R.string.pref__theme__window_background_opacity__summary, "v" to it) },
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 0,
            max = 100,
            stepIncrement = 5,
        )

        KeyboardBackdropGroup()

        KeySkinGroup()

        AddonManagementReferenceBox(type = ExtensionListScreenType.EXT_THEME)
    }
}

/**
 * Backdrop picker: what shows through the gutters between keys. See docs/theming/backdrops.md.
 * Choosing applies at once, like the reference keyboard's one-tap galleries.
 */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.KeyboardBackdropGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { BackdropStore(context) }
    // Bumped after an import so the list picks up the new file.
    var generation by remember { mutableIntStateOf(0) }
    val options = remember(generation) { store.options() }
    val rawBackdropId by prefs.theme.backdropId.observeAsState()
    val selectedId = remember(rawBackdropId) { BackdropId.parse(rawBackdropId) }
    var showGallery by remember { mutableStateOf(false) }
    val importImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) { store.import(uri) }
                .onSuccess { id ->
                    prefs.theme.backdropId.set(id.storedValue)
                    generation++
                    context.showLongToastSync(R.string.pref__theme__backdrop__import_success)
                }
                .onFailure { error ->
                    context.showLongToastSync("${error.message}")
                }
        }
    }

    PreferenceGroup(title = stringRes(R.string.pref__theme__backdrop__group)) {
        Preference(
            icon = Icons.Default.Wallpaper,
            title = stringRes(R.string.pref__theme__backdrop__label),
            summary = options.firstOrNull { it.id == selectedId }?.label ?: options.first().label,
            onClick = { showGallery = true },
        )
        if (showGallery) {
            BackdropGalleryDialog(
                store = store,
                options = options,
                selected = selectedId,
                onSelect = { id -> scope.launch { prefs.theme.backdropId.set(id.storedValue) } },
                onDismiss = { showGallery = false },
            )
        }
        Preference(
            icon = Icons.Default.AddPhotoAlternate,
            title = stringRes(R.string.pref__theme__backdrop__import),
            summary = stringRes(R.string.pref__theme__backdrop__import_summary),
            onClick = { importImage.launch("image/*") },
        )
        DialogSliderPreference(
            prefs.theme.backdropOpacity,
            icon = Icons.Default.Opacity,
            title = stringRes(R.string.pref__theme__backdrop__opacity),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 0,
            max = 100,
            stepIncrement = 5,
            enabledIf = { prefs.theme.backdropId isNotEqualTo "" },
        )
        SwitchPreference(
            prefs.theme.backdropAnimate,
            icon = Icons.Default.Animation,
            title = stringRes(R.string.pref__theme__backdrop__animate),
            summary = stringRes(R.string.pref__theme__backdrop__animate_summary),
            enabledIf = { prefs.theme.backdropId isNotEqualTo "" },
        )
    }
}

/**
 * Key skin picker: a texture per key slot over the theme's layout, as in the Sci-Fi Keyboard's
 * Themes and Keys tabs. Choosing applies at once. See docs/theming/key-skins.md.
 */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.KeySkinGroup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { KeySkinStore(context.applicationContext) }
    val options = remember { store.options() }
    val rawId by prefs.theme.keySkinId.observeAsState()
    val selectedId = remember(rawId) { KeySkinId.parse(rawId) }
    var showGallery by remember { mutableStateOf(false) }
    val hasSkins = options.size > 1

    PreferenceGroup(title = stringRes(R.string.pref__theme__key_skin__group)) {
        Preference(
            icon = Icons.Default.GridView,
            title = stringRes(R.string.pref__theme__key_skin__label),
            summary = if (hasSkins) {
                options.firstOrNull { it.id == selectedId }?.label ?: options.first().label
            } else {
                stringRes(R.string.pref__theme__key_skin__empty)
            },
            onClick = { showGallery = true },
            enabledIf = { hasSkins },
        )
        if (showGallery) {
            KeySkinGalleryDialog(
                store = store,
                options = options,
                selected = selectedId,
                onSelect = { id -> scope.launch { prefs.theme.keySkinId.set(id.storedValue) } },
                onDismiss = { showGallery = false },
            )
        }
    }
}
