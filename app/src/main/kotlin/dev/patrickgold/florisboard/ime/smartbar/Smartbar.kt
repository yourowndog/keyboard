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

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import dev.patrickgold.florisboard.ime.nlp.NlpInlineAutofill
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionButton
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionsRow
import dev.patrickgold.florisboard.ime.smartbar.quickaction.ToggleOverflowPanelAction
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.horizontalTween
import org.florisboard.lib.compose.verticalTween
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

const val AnimationDuration = 200

val VerticalEnterTransition = EnterTransition.verticalTween(AnimationDuration)
val VerticalExitTransition = ExitTransition.verticalTween(AnimationDuration)

private val HorizontalEnterTransition = EnterTransition.horizontalTween(AnimationDuration)
private val HorizontalExitTransition = ExitTransition.horizontalTween(AnimationDuration)

private val NoEnterTransition = EnterTransition.horizontalTween(0)
private val NoExitTransition = ExitTransition.horizontalTween(0)

private val AnimationTween = tween<Float>(AnimationDuration)
private val NoAnimationTween = tween<Float>(0)

@Composable
fun VoiceVisualizer(
    amplitude: Float,
    isTranscribing: Boolean,
    modifier: Modifier = Modifier,
) {
    // Rolling buffer for oscilloscope waveform (recording mode)
    val sampleBuffer = remember { mutableListOf<Float>() }
    val bufferSize = 200

    // Push new amplitude samples during recording
    LaunchedEffect(amplitude, isTranscribing) {
        if (!isTranscribing) {
            sampleBuffer.add(amplitude)
            if (sampleBuffer.size > bufferSize) {
                sampleBuffer.removeAt(0)
            }
        }
    }

    // Clear buffer when switching to transcribing
    LaunchedEffect(isTranscribing) {
        if (isTranscribing) {
            sampleBuffer.clear()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "transcribing")
    // Very slow, graceful sweep for processing cascade
    val cascadePhase by infiniteTransition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cascadePhase",
    )
    // Gentle shimmer
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f

        if (isTranscribing) {
            // === PROCESSING: Slow graceful bar cascade ===
            val maxBarHeight = canvasHeight * 0.8f
            val minBarHeight = canvasHeight * 0.05f
            val barCount = 80
            val totalUnits = barCount * 1.25f
            val unitWidth = canvasWidth / totalUnits
            val barWidth = unitWidth * 1.0f
            val gapWidth = unitWidth * 0.25f

            for (i in 0 until barCount) {
                val x = (i * (barWidth + gapWidth)) + (gapWidth / 2f)
                val fraction = i.toFloat() / barCount

                val dist = (fraction - cascadePhase).let { it * it }
                val pulse = Math.exp((-dist * 12.0)).toFloat()
                val bg = 0.06f + 0.04f * Math.sin((fraction * 4.0 * Math.PI) + shimmer).toFloat()
                val height = minBarHeight + (maxBarHeight - minBarHeight) * (pulse * 0.85f + bg)
                val alpha = 0.25f + 0.55f * pulse

                val y = (canvasHeight - height) / 2f
                drawRect(
                    color = Color.White.copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, height),
                )
            }
        } else {
            // === RECORDING: Oscilloscope waveform ===
            val samples = sampleBuffer.toList()
            if (samples.size >= 2) {
                val path = Path()
                val maxDeflection = canvasHeight * 0.4f

                // First point
                val stepX = canvasWidth / (bufferSize - 1).toFloat()
                val startIndex = bufferSize - samples.size
                val firstY = centerY - samples[0] * maxDeflection
                path.moveTo(startIndex * stepX, firstY)

                // Connect all samples with lines
                for (j in 1 until samples.size) {
                    val x = (startIndex + j) * stepX
                    val y = centerY - samples[j] * maxDeflection
                    path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 2.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            // Flat baseline when no/low amplitude
            drawRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset(0f, centerY - 0.5f),
                size = Size(canvasWidth, 1f),
            )
        }
    }
}


@Composable
fun WhisperBar(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val state by keyboardManager.activeState.collectAsState()
    val amplitudeState = keyboardManager.whisperAmplitude.collectAsState()
    val amplitude = amplitudeState.value

    SnyggRow(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (state.isRecording) {
            // === RECORDING STATE ===
            // Left: Pause/Resume + Cancel
            SnyggIconButton(
                elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                onClick = {
                    if (state.isPaused) {
                        keyboardManager.resumeVoiceCapture()
                    } else {
                        keyboardManager.pauseVoiceCapture()
                    }
                },
                modifier = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
            ) {
                SnyggIcon(
                    imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause
                )
            }

            SnyggIconButton(
                elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                onClick = { keyboardManager.cancelVoiceInput() },
                modifier = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
            ) {
                SnyggIcon(imageVector = Icons.Default.Close)
            }

            // Center: Visualizer
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                VoiceVisualizer(
                    amplitude = amplitude,
                    isTranscribing = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Right: Submit
            SnyggIconButton(
                elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                onClick = { keyboardManager.submitVoiceCapture() },
                modifier = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
            ) {
                SnyggIcon(imageVector = Icons.Default.Send)
            }
        } else if (state.isTranscribing) {
            // === TRANSCRIBING STATE ===
            // Full-width sine wave animation
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                VoiceVisualizer(
                    amplitude = 0f,
                    isTranscribing = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // === IDLE/ERROR STATE (voice mode but not recording) ===
            SnyggIconButton(
                elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.VOICE_HISTORY },
                modifier = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
            ) {
                SnyggIcon(imageVector = Icons.Default.History)
            }

            SnyggText(
                text = "Voice Input",
                modifier = Modifier.weight(1f)
            )

            SnyggIconButton(
                elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                onClick = { keyboardManager.retryTranscription() },
                modifier = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
            ) {
                SnyggIcon(imageVector = Icons.Default.Refresh)
            }

            SnyggIconButton(
                elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
                onClick = { keyboardManager.cancelVoiceInput() },
                modifier = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
            ) {
                SnyggIcon(imageVector = Icons.Default.Close)
            }
        }
    }
}

@Composable
fun Smartbar() {
    val prefs by FlorisPreferenceStore
    val smartbarEnabled by prefs.smartbar.enabled.observeAsState()
    val extendedActionsPlacement by prefs.smartbar.extendedActionsPlacement.observeAsState()

    AnimatedVisibility(
        visible = smartbarEnabled,
        enter = VerticalEnterTransition,
        exit = VerticalExitTransition,
    ) {
        when (extendedActionsPlacement) {
            ExtendedActionsPlacement.ABOVE_CANDIDATES -> {
                SnyggColumn(FlorisImeUi.Smartbar.elementName) {
                    SmartbarSecondaryRow()
                    SmartbarMainRow()
                    SmartbarPhraseRow()
                }
            }

            ExtendedActionsPlacement.BELOW_CANDIDATES -> {
                SnyggColumn(FlorisImeUi.Smartbar.elementName) {
                    SmartbarMainRow()
                    SmartbarPhraseRow()
                    SmartbarSecondaryRow()
                }
            }

            ExtendedActionsPlacement.OVERLAY_APP_UI -> {
                SnyggBox(FlorisImeUi.Smartbar.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FlorisImeSizing.smartbarHeight),
                    allowClip = false,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(FlorisImeSizing.smartbarHeight * 2)
                            .absoluteOffset(y = -FlorisImeSizing.smartbarHeight),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        SmartbarSecondaryRow()
                    }
                    Column {
                        SmartbarMainRow()
                        SmartbarPhraseRow()
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartbarMainRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val nlpManager by context.nlpManager()
    val scope = rememberCoroutineScope()

    val state by keyboardManager.activeState.collectAsState()

    val inlineSuggestions by NlpInlineAutofill.suggestions.collectAsState()
    LaunchedEffect(inlineSuggestions) {
        nlpManager.autoExpandCollapseSmartbarActions(null, inlineSuggestions)
    }
    val shouldShowInlineSuggestionsUi = AndroidVersion.ATLEAST_API30_R && inlineSuggestions.isNotEmpty()

    val smartbarLayout by prefs.smartbar.layout.observeAsState()
    val flipToggles by prefs.smartbar.flipToggles.observeAsState()
    val sharedActionsExpanded by prefs.smartbar.sharedActionsExpanded.observeAsState()
    val extendedActionsExpanded by prefs.smartbar.extendedActionsExpanded.observeAsState()

    val shouldAnimate by prefs.smartbar.sharedActionsExpandWithAnimation.observeAsState()

    @Composable
    fun SharedActionsToggle() {
        SnyggIconButton(
            elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
            onClick = {
                if (/* was */ sharedActionsExpanded) {
                    keyboardManager.activeState.isActionsOverflowVisible = false
                }
                scope.launch {
                    prefs.smartbar.sharedActionsExpanded.set(!sharedActionsExpanded)
                }
            },
            modifier = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
        ) {
            val transition = updateTransition(sharedActionsExpanded, label = "sharedActionsExpandedToggleBtn")
            val rotation by transition.animateFloat(
                transitionSpec = {
                    if (shouldAnimate) AnimationTween else NoAnimationTween
                },
                label = "rotation",
            ) {
                if (it) 180f else 0f
            }
            val arrowIcon = if (flipToggles) {
                Icons.AutoMirrored.Default.KeyboardArrowLeft
            } else {
                Icons.AutoMirrored.Default.KeyboardArrowRight
            }
            val incognitoIcon = ImageVector.vectorResource(id = R.drawable.ic_incognito)
            val incognitoDisplayMode = prefs.keyboard.incognitoDisplayMode.observeAsState()
            val isIncognitoMode = state.isIncognitoMode
            val icon = if (isIncognitoMode) {
                when (incognitoDisplayMode.value) {
                    IncognitoDisplayMode.REPLACE_SHARED_ACTIONS_TOGGLE -> incognitoIcon!!
                    IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD -> arrowIcon
                }
            } else {
                arrowIcon
            }
            SnyggIcon(
                modifier = Modifier.rotate(if (incognitoDisplayMode.value == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD) rotation else 0f),
                imageVector = icon,
            )
        }
    }

    @Composable
    fun RowScope.CenterContent() {
        val uiMode = state.imeUiMode
        val expanded = sharedActionsExpanded && smartbarLayout == SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED
        Box(
            modifier = Modifier
                .weight(1f)
                .height(FlorisImeSizing.smartbarHeight),
        ) {
            val enterTransition = if (shouldAnimate) HorizontalEnterTransition else NoEnterTransition
            val exitTransition = if (shouldAnimate) HorizontalExitTransition else NoExitTransition
            
            if (uiMode == ImeUiMode.VOICE) {
                WhisperBar()
            } else {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !expanded,
                    enter = enterTransition,
                    exit = exitTransition,
                ) {
                    if (shouldShowInlineSuggestionsUi) {
                        InlineSuggestionsUi(inlineSuggestions)
                    } else {
                        CandidatesRow()
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = expanded,
                    enter = enterTransition,
                    exit = exitTransition,
                ) {
                    QuickActionsRow(
                        FlorisImeUi.SmartbarSharedActionsRow.elementName,
                        modifier = modifier
                            .fillMaxWidth()
                            .height(FlorisImeSizing.smartbarHeight),
                    )
                }
            }
        }
    }

    @Composable
    fun ExtendedActionsToggle() {
        SnyggIconButton(
            FlorisImeUi.SmartbarExtendedActionsToggle.elementName,
            onClick = {
                if (/* was */ extendedActionsExpanded) {
                    keyboardManager.activeState.isActionsOverflowVisible = false
                }
                scope.launch {
                    prefs.smartbar.extendedActionsExpanded.set(!extendedActionsExpanded)
                }
            },
            modifier = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
        ) {
            val transition = updateTransition(extendedActionsExpanded, label = "smartbarSecondaryRowToggleBtn")
            val alpha by transition.animateFloat(label = "alpha") { if (it) 1f else 0f }
            val rotation by transition.animateFloat(label = "rotation") { if (it) 180f else 0f }
            // Expanded icon
            SnyggIcon(
                FlorisImeUi.SmartbarExtendedActionsToggle.elementName,
                modifier = Modifier
                    .alpha(alpha)
                    .rotate(rotation),
                imageVector = Icons.Default.UnfoldLess,
            )
            // Not expanded icon
            SnyggIcon(
                FlorisImeUi.SmartbarExtendedActionsToggle.elementName,
                modifier = Modifier
                    .alpha(1f - alpha)
                    .rotate(rotation - 180f),
                imageVector = Icons.Default.UnfoldMore,
            )
        }
    }

    @Composable
    fun StickyAction() {
        val actionArrangement by prefs.smartbar.actionArrangement.observeAsState()
        val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()

        val action = when {
            actionArrangement.stickyAction != null -> {
                actionArrangement.stickyAction
            }

            smartbarLayout == SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED && sharedActionsExpanded -> {
                ToggleOverflowPanelAction
            }

            else -> null
        }

        if (action != null) {
            QuickActionButton(
                modifier = Modifier.padding(horizontal = 4.dp),
                action = action,
                evaluator = evaluator,
            )
        } else {
            Spacer(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .aspectRatio(1f),
            )
        }
    }

    SideEffect {
        if (!shouldAnimate) {
            scope.launch {
                prefs.smartbar.sharedActionsExpandWithAnimation.set(true)
            }
        }
    }

    SnyggRow(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight),
    ) {
        val uiMode = state.imeUiMode
        when (smartbarLayout) {
            SmartbarLayout.SUGGESTIONS_ONLY -> {
                if (uiMode == ImeUiMode.VOICE) {
                    WhisperBar()
                } else if (shouldShowInlineSuggestionsUi) {
                    InlineSuggestionsUi(inlineSuggestions)
                } else {
                    CandidatesRow()
                }
            }

            SmartbarLayout.ACTIONS_ONLY -> {
                if (uiMode == ImeUiMode.VOICE) {
                    WhisperBar()
                } else if (shouldShowInlineSuggestionsUi) {
                    InlineSuggestionsUi(inlineSuggestions)
                } else {
                    QuickActionsRow(FlorisImeUi.SmartbarSharedActionsRow.elementName)
                }
            }

            SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED -> {
                if (!flipToggles) {
                    if (uiMode != ImeUiMode.VOICE) SharedActionsToggle()
                    CenterContent()
                    if (uiMode != ImeUiMode.VOICE) StickyAction()
                } else {
                    if (uiMode != ImeUiMode.VOICE) StickyAction()
                    CenterContent()
                    if (uiMode != ImeUiMode.VOICE) SharedActionsToggle()
                }
            }

            SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED -> {
                if (!flipToggles) {
                    if (uiMode != ImeUiMode.VOICE) ExtendedActionsToggle()
                    CenterContent()
                    if (uiMode != ImeUiMode.VOICE) StickyAction()
                } else {
                    if (uiMode != ImeUiMode.VOICE) StickyAction()
                    CenterContent()
                    if (uiMode != ImeUiMode.VOICE) ExtendedActionsToggle()
                }
            }
        }
    }
}

@Composable
private fun SmartbarSecondaryRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val smartbarLayout by prefs.smartbar.layout.observeAsState()
    val secondaryRowStyle = rememberSnyggThemeQuery(FlorisImeUi.SmartbarExtendedActionsRow.elementName)
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val extendedActionsExpanded by prefs.smartbar.extendedActionsExpanded.observeAsState()
    val extendedActionsPlacement by prefs.smartbar.extendedActionsPlacement.observeAsState()
    val background = secondaryRowStyle.background().let { color ->
        if (extendedActionsPlacement == ExtendedActionsPlacement.OVERLAY_APP_UI) {
            if (color.isUnspecified || color.alpha == 0f) {
                windowStyle.background(default = Color.Black)
            } else {
                color
            }
        } else {
            color
        }
    }

    AnimatedVisibility(
        visible = smartbarLayout == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED && extendedActionsExpanded,
        enter = VerticalEnterTransition,
        exit = VerticalExitTransition,
    ) {
        QuickActionsRow(
            FlorisImeUi.SmartbarExtendedActionsRow.elementName,
            modifier = modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight)
                .background(background),
        )
    }
}

/**
 * SmartbarPhraseRow — a second suggestion row that shows multi-word phrase predictions.
 * Animates in when phrase candidates are available (e.g., after typing "what's ").
 * Tapping a phrase commits the entire phrase with a trailing space.
 */
@Composable
private fun SmartbarPhraseRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val nlpManager by context.nlpManager()
    val keyboardManager by context.keyboardManager()
    val phraseCandidates by nlpManager.phraseCandidatesFlow.collectAsState()

    AnimatedVisibility(
        visible = phraseCandidates.isNotEmpty(),
        enter = VerticalEnterTransition,
        exit = VerticalExitTransition,
    ) {
        SnyggRow(
            elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
            modifier = modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            phraseCandidates.forEach { candidate ->
                SnyggBox(
                    elementName = FlorisImeUi.SmartbarCandidateWord.elementName,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            keyboardManager.commitCandidate(candidate)
                            nlpManager.clearPhraseCandidates()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = candidate.text.toString(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
