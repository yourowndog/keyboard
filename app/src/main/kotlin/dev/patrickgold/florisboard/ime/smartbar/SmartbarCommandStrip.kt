/*
 * Copyright (C) 2026 The OmniBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickAction
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionButton
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox

/**
 * The commands on the LCARS command strip, in reading order, with the share of the bar's width
 * each takes. Widths follow the design reference: MENU 14%, EMOJI and LANG 15%, VOICE 14%.
 */
internal enum class SmartbarCommand(val data: TextKeyData, val labelRes: Int, val widthWeight: Float) {
    MENU(TextKeyData.TOGGLE_ACTIONS_OVERFLOW, R.string.smartbar__command__menu, 0.14f),
    EMOJI(TextKeyData.IME_UI_MODE_MEDIA, R.string.smartbar__command__emoji, 0.15f),
    LANG(TextKeyData.LANGUAGE_SWITCH, R.string.smartbar__command__lang, 0.15f),
    VOICE(TextKeyData.VOICE_INPUT, R.string.smartbar__command__voice, 0.14f);

    val code: Int get() = data.code
    val action: QuickAction = QuickAction.InsertKey(data)
}

private const val RULE_WEIGHT = 0.028f
private const val CAP_WEIGHT = 0.075f

/**
 * Weights are shares of the whole bar, so the free middle takes exactly what the commands leave.
 * Giving it weight 1 instead made Row normalise over ~1.7 and shrank every command to about 8% of
 * the bar, which wrapped MENU, EMOJI and VOICE onto two lines.
 */
private val IDLE_GAP_WEIGHT = 1f - (
    SmartbarCommand.entries.sumOf { it.widthWeight.toDouble() }.toFloat() + 2 * RULE_WEIGHT + CAP_WEIGHT
)
private val TYPING_CANDIDATES_WEIGHT =
    1f - SmartbarCommand.MENU.widthWeight - SmartbarCommand.VOICE.widthWeight - 2 * RULE_WEIGHT

/**
 * The fixed MENU | EMOJI | LANG ... VOICE strip from the LCARS Jefferies reference. While the user
 * types (candidates or inline autofill present), [candidates] takes the middle and only MENU and
 * VOICE stay, so suggestions are never hidden behind commands. Voice mode is handled by the caller,
 * which shows the existing WhisperBar unchanged.
 *
 * Buttons are ordinary quick actions, so haptics, enabled state and VOICE's long press for voice
 * history behave exactly as on the square action buttons.
 */
@Composable
internal fun RowScope.SmartbarCommandStrip(
    isTyping: Boolean,
    candidates: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()

    @Composable
    fun Command(command: SmartbarCommand) {
        // The weight goes on a plain Box: QuickActionButton wraps its SnyggBox in a tooltip
        // container, so a RowScope weight passed through would not reach the Row.
        Box(
            modifier = Modifier
                .weight(command.widthWeight)
                .fillMaxHeight(),
        ) {
            QuickActionButton(
                action = command.action,
                evaluator = evaluator,
                modifier = Modifier.fillMaxSize(),
                square = false,
                label = stringRes(command.labelRes).uppercase(),
            )
        }
    }

    @Composable
    fun Rule(beside: SmartbarCommand) {
        SnyggBox(
            elementName = FlorisImeUi.SmartbarCommandRule.elementName,
            attributes = mapOf(FlorisImeUi.Attr.Code to beside.code),
            modifier = Modifier
                .weight(RULE_WEIGHT)
                .fillMaxHeight(),
        ) {}
    }

    @Composable
    fun Cap(beside: SmartbarCommand) {
        SnyggBox(
            elementName = FlorisImeUi.SmartbarCommandCap.elementName,
            attributes = mapOf(FlorisImeUi.Attr.Code to beside.code),
            modifier = Modifier
                .weight(CAP_WEIGHT)
                .fillMaxHeight(),
        ) {}
    }

    Command(SmartbarCommand.MENU)
    // The coral and cyan rules stay in both states, so the strip keeps its frame while the
    // candidates take the middle.
    Rule(beside = SmartbarCommand.MENU)
    if (isTyping) {
        Box(modifier = Modifier.weight(TYPING_CANDIDATES_WEIGHT).fillMaxHeight()) {
            candidates()
        }
        Rule(beside = SmartbarCommand.VOICE)
    } else {
        Command(SmartbarCommand.EMOJI)
        Command(SmartbarCommand.LANG)
        Spacer(modifier = Modifier.weight(IDLE_GAP_WEIGHT))
        Cap(beside = SmartbarCommand.VOICE)
        Rule(beside = SmartbarCommand.VOICE)
    }
    Command(SmartbarCommand.VOICE)
}
