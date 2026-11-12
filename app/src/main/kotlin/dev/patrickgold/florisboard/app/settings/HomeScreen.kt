
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

package dev.patrickgold.florisboard.app.settings

import dev.patrickgold.florisboard.app.console.ConsolePanel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.ui.Preference
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.console.ConsoleReadout
import dev.patrickgold.florisboard.app.console.ConsoleStatus
import dev.patrickgold.florisboard.app.console.ConsoleTile
import dev.patrickgold.florisboard.app.console.Lcars
import dev.patrickgold.florisboard.ime.keyboard.KeyboardProfile
import dev.patrickgold.florisboard.themeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * Settings home: seven panels grouped by what the user is trying to do, plus a search over
 * [SettingsSearchIndex]. Routes keep their old deep-link paths (settings/theme opens Look, and so
 * on), so shortcuts and the keyboard's SETTINGS key still land in the right place.
 */
@Composable
fun HomeScreen() = FlorisScreen {
    panel = ConsolePanel.HOME
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
        if (!isFlorisBoardEnabled) {
            ConsoleStatus(
                text = stringRes(R.string.settings__home__ime_not_enabled),
                color = Lcars.Coral,
                onClick = { InputMethodUtils.showImeEnablerActivity(context) },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            )
        } else if (!isFlorisBoardSelected) {
            ConsoleStatus(
                text = stringRes(R.string.settings__home__ime_not_selected),
                color = Lcars.Amber,
                onClick = { InputMethodUtils.showImePicker(context) },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            )
        } else {
            ConsoleStatus(
                text = stringRes(R.string.settings__home__ime_online),
                color = Lcars.Teal,
                onClick = null,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            )
        }

        HomeProfileAndReadouts()

        var query by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringRes(R.string.settings__search__hint)) },
        )

        if (query.isNotBlank()) {
            val results = SettingsSearchIndex.search(query) { context.getString(it) }
            if (results.isEmpty()) {
                Text(
                    text = stringRes(R.string.settings__search__no_results, "q" to query.trim()),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            for (entry in results) {
                Preference(
                    title = stringRes(entry.labelRes),
                    summary = stringRes(entry.panelRes),
                    onClick = { navController.navigate(entry.route) },
                )
            }
        } else {
            HomePanels(onOpen = { navController.navigate(it) })
        }
    }
}

private val TileGap = 8.dp

@Composable
private fun HomePanels(onOpen: (Any) -> Unit) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            ConsoleTile(
                label = stringRes(R.string.settings__look__title),
                summary = stringRes(R.string.settings__look__summary),
                color = ConsolePanel.LOOK.color, code = "01", tall = true,
                onClick = { onOpen(Routes.Settings.Theme) },
                modifier = Modifier.weight(1f),
            )
            ConsoleTile(
                label = stringRes(R.string.settings__layout__title),
                summary = stringRes(R.string.settings__layout__summary),
                color = ConsolePanel.LAYOUT.color, code = "02", tall = true,
                onClick = { onOpen(Routes.Settings.Keyboard) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            ConsoleTile(
                label = stringRes(R.string.settings__sound_touch__title),
                summary = stringRes(R.string.settings__sound_touch__summary),
                color = ConsolePanel.SOUND_TOUCH.color, code = "03", tall = false,
                onClick = { onOpen(Routes.Settings.InputFeedback) },
                modifier = Modifier.weight(1f),
            )
            ConsoleTile(
                label = stringRes(R.string.settings__smartbar__title),
                summary = stringRes(R.string.settings__command_strip__summary),
                color = ConsolePanel.COMMAND_STRIP.color, code = "04", tall = false,
                onClick = { onOpen(Routes.Settings.Smartbar) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            ConsoleTile(
                label = stringRes(R.string.settings__typing__title),
                summary = stringRes(R.string.settings__typing__summary),
                color = ConsolePanel.TYPING.color, code = "05", tall = false,
                onClick = { onOpen(Routes.Settings.Typing) },
                modifier = Modifier.weight(1f),
            )
            ConsoleTile(
                label = stringRes(R.string.settings__gestures__title),
                summary = stringRes(R.string.settings__gestures__summary),
                color = ConsolePanel.GESTURES.color, code = "06", tall = false,
                onClick = { onOpen(Routes.Settings.Gestures) },
                modifier = Modifier.weight(1f),
            )
        }
        ConsoleTile(
            label = stringRes(R.string.settings__system__title),
            summary = stringRes(R.string.settings__system__summary),
            color = ConsolePanel.SYSTEM.color, code = "07", tall = false,
            onClick = { onOpen(Routes.Settings.Other) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * TEXT | CODE switch plus readouts of what the keyboard is wearing, so the home screen says what
 * state the keyboard is in before you open anything.
 */
@Composable
private fun HomeProfileAndReadouts() {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeManager by context.themeManager()
    val activeProfileId by prefs.keyboard.activeProfileId.observeAsState()
    val activeProfile = KeyboardProfile.fromId(activeProfileId)
    val dayThemeId by prefs.theme.dayThemeId.observeAsState()
    val nightThemeId by prefs.theme.nightThemeId.observeAsState()
    val configs by themeManager.indexedThemeConfigs.collectAsState()
    val backdropId by prefs.theme.backdropId.observeAsState()

    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (profile in KeyboardProfile.entries) {
            val selected = profile == activeProfile
            val color = ConsolePanel.LAYOUT.color
            Text(
                text = stringRes(profile.labelRes).uppercase(),
                color = if (selected) Lcars.Black else color,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) color else Lcars.PanelRaised)
                    .clickable { scope.launch { prefs.keyboard.activeProfileId.set(profile.id) } }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConsoleReadout(
            label = stringRes(R.string.pref__theme__day),
            value = configs.first[dayThemeId]?.label ?: dayThemeId.componentId,
            color = Lcars.Amber,
            modifier = Modifier.weight(1f),
        )
        ConsoleReadout(
            label = stringRes(R.string.pref__theme__night),
            value = configs.first[nightThemeId]?.label ?: nightThemeId.componentId,
            color = Lcars.Lilac,
            modifier = Modifier.weight(1f),
        )
        ConsoleReadout(
            label = stringRes(R.string.pref__theme__backdrop__label),
            value = if (backdropId.isBlank()) stringRes(R.string.settings__home__readout_none) else backdropId.substringAfterLast('/').substringAfter(':'),
            color = Lcars.Sky,
            modifier = Modifier.weight(1f),
        )
    }
}
