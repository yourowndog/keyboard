/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * What MENU on the command strip opens: an LCARS console in three labelled bands instead of
 * FlorisBoard's unordered action grid.
 *
 * - EDIT: the editing commands worth having at the thumb.
 * - CONSOLE: quick changes to how the keyboard behaves, without leaving the app being typed in.
 * - SYSTEM: clipboard history, language and full settings.
 *
 * The bands are fixed so the same command is always in the same place. Each tile is an ordinary
 * quick action, so haptics, enabled state and theming match the rest of the strip.
 */
internal object MenuConsoleBands {
    val edit = listOf(
        TextKeyData.CLIPBOARD_SELECT_ALL,
        TextKeyData.CLIPBOARD_COPY,
        TextKeyData.CLIPBOARD_CUT,
        TextKeyData.CLIPBOARD_PASTE,
        TextKeyData.UNDO,
        TextKeyData.REDO,
    )
    val console = listOf(
        TextKeyData.TOGGLE_KEYBOARD_PROFILE,
        TextKeyData.TOGGLE_COMPACT_LAYOUT,
        TextKeyData.TOGGLE_AUTOCORRECT,
        TextKeyData.TOGGLE_INCOGNITO_MODE,
        TextKeyData.ESCAPE,
        TextKeyData.IME_HIDE_UI,
    )
    val system = listOf(
        TextKeyData.IME_UI_MODE_CLIPBOARD,
        TextKeyData.LANGUAGE_SWITCH,
        TextKeyData.SETTINGS,
    )
}

private const val TilesPerRow = 3

@Composable
fun QuickActionsOverflowPanel() {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()

    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            MenuBand(stringRes(R.string.menu_console__band_edit), MenuConsoleBands.edit, evaluator)
            MenuBand(stringRes(R.string.menu_console__band_console), MenuConsoleBands.console, evaluator)
            MenuBand(stringRes(R.string.menu_console__band_system), MenuConsoleBands.system, evaluator)
        }
    }
}

@Composable
private fun MenuBand(title: String, keys: List<TextKeyData>, evaluator: ComputingEvaluator) {
    SnyggText(
        elementName = FlorisImeUi.SmartbarActionsOverflowCustomizeButton.elementName,
        text = title,
        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
    )
    for (row in keys.chunked(TilesPerRow)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            for (key in row) {
                QuickActionButton(
                    action = QuickAction.InsertKey(key),
                    evaluator = evaluator,
                    type = QuickActionBarType.INTERACTIVE_TILE,
                    square = false,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
            }
            repeat(TilesPerRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
