/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.ClipboardSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggSpacer

val CandidatesRowScrollbarHeight = 2.dp

@Composable
fun CandidatesRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val nlpManager by context.nlpManager()
    val subtypeManager by context.subtypeManager()

    val displayMode by prefs.suggestion.displayMode.observeAsState()
    val candidates by nlpManager.activeCandidatesFlow.collectAsState()
    val maxSuggestions = 5

    val limited = candidates.take(maxSuggestions)
    val list = when (displayMode) {
        CandidatesDisplayMode.CLASSIC -> limited.subList(0, 3.coerceAtMost(limited.size))
        else -> limited
    }

    SnyggRow(
        elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
        modifier = modifier
            .fillMaxSize()
            .conditional(list.size > 1) {
                florisHorizontalScroll(scrollbarHeight = CandidatesRowScrollbarHeight)
            },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        list.forEachIndexed { index, candidate ->
            val candidateModifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth()
            CandidateItem(
                modifier = candidateModifier,
                candidate = candidate,
                displayMode = displayMode,
                onClick = { keyboardManager.commitCandidate(candidate) },
                onLongPress = {
                    if (candidate.isEligibleForUserRemoval) {
                        nlpManager.removeSuggestion(subtypeManager.activeSubtype, candidate)
                    } else {
                        // If it's not eligible for removal (e.g. a dictionary word or raw input),
                        // we treat long-press as "Add to User Dictionary"
                        nlpManager.addToUserDictionary(subtypeManager.activeSubtype, candidate)
                        true
                    }
                },
                longPressDelay = prefs.keyboard.longPressDelay.get().toLong(),
            )
        }
    }
}

@Composable
private fun CandidateItem(
    candidate: SuggestionCandidate,
    displayMode: CandidatesDisplayMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { },
    onLongPress: () -> Boolean = { false },
    longPressDelay: Long,
) = with(LocalDensity.current) {
    var isPressed by remember { mutableStateOf(false) }

    val elementName = if (candidate is ClipboardSuggestionCandidate) {
        FlorisImeUi.SmartbarCandidateClip
    } else {
        FlorisImeUi.SmartbarCandidateWord
    }.elementName
    val attributes = mapOf("auto-commit" to if (candidate.isEligibleForAutoCommit) 1 else 0)
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggRow(
        elementName = elementName,
        attributes = attributes,
        selector = selector,
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .pointerInput(onClick) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    if (down.pressed != down.previousPressed) down.consume()
                    var upOrCancel: PointerInputChange? = null
                    try {
                        upOrCancel = withTimeout(longPressDelay) {
                            waitForUpOrCancellation()
                        }
                        upOrCancel?.let { if (it.pressed != it.previousPressed) it.consume() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (onLongPress()) {
                            upOrCancel = null
                            isPressed = false
                        }
                        waitForUpOrCancellation()?.let { if (it.pressed != it.previousPressed) it.consume() }
                    }
                    if (upOrCancel != null) {
                        onClick()
                    }
                    isPressed = false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (candidate.icon != null) {
            SnyggBox(
                elementName = "$elementName-icon",
                attributes = attributes,
                selector = selector,
            ) {
                SnyggIcon(imageVector = candidate.icon!!)
            }
        }
        SnyggColumn(
            modifier = Modifier.wrapContentWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = candidate.text.toString(),
                maxLines = 1,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
            )
        }
    }
}
