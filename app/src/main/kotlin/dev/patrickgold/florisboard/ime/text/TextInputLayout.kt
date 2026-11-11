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

package dev.patrickgold.florisboard.ime.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.InlineSuggestionsStyleCache
import dev.patrickgold.florisboard.ime.smartbar.Smartbar
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionsOverflowPanel
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardLayout
import dev.patrickgold.florisboard.ime.theme.FlorisImeTheme
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.theme.lcars.LcarsChassisGeometry
import dev.patrickgold.florisboard.ime.theme.lcars.drawLcarsChassis
import dev.patrickgold.florisboard.ime.theme.lcars.rememberLcarsChassisThemeColors
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggIcon

@Composable
fun TextInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()

    val prefs by FlorisPreferenceStore

    val state by keyboardManager.activeState.collectAsState()
    val evaluator by keyboardManager.activeEvaluator.collectAsState()

    InlineSuggestionsStyleCache()

    val density = LocalDensity.current
    // Gate the LCARS analytic chassis behind the active theme id so standard
    // (non-LCARS) themes render exactly as before.
    val isLcarTheme = FlorisImeTheme.config.id.contains("lcars", ignoreCase = true)
    val chassisColors = if (isLcarTheme) {
        rememberLcarsChassisThemeColors(
            attributes = mapOf(FlorisImeUi.Attr.Mode to state.keyboardMode.toString()),
        )
    } else null

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SnyggBox(
            elementName = FlorisImeUi.KeyboardChassis.elementName,
            attributes = mapOf(FlorisImeUi.Attr.Mode to state.keyboardMode.toString()),
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            supportsBackgroundImage = true,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .then(
                        if (isLcarTheme && chassisColors != null) {
                            Modifier.drawBehind {
                                val geometry = LcarsChassisGeometry(
                                    viewportWidth = size.width / density.density,
                                    viewportHeight = size.height / density.density,
                                )
                                drawLcarsChassis(
                                    geometry = geometry,
                                    primaryColor = chassisColors.primaryRail,
                                    secondaryColor = chassisColors.secondaryRail,
                                    deckColor = chassisColors.deck,
                                    snapChannelsToPixels = true,
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                ) {
                    Smartbar()
                    if (state.isActionsOverflowVisible) {
                        QuickActionsOverflowPanel()
                    } else {
                        Box {
                            val incognitoDisplayMode by prefs.keyboard.incognitoDisplayMode.observeAsState()
                            val showIncognitoIcon = evaluator.state.isIncognitoMode &&
                                incognitoDisplayMode == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD
                            if (showIncognitoIcon) {
                                SnyggIcon(
                                    FlorisImeUi.IncognitoModeIndicator.elementName,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .align(Alignment.Center),
                                    painter = painterResource(R.drawable.ic_incognito),
                                )
                            }
                            TextKeyboardLayout(evaluator = evaluator)
                        }
                    }
                }
            }
        }
    }
}
