/*
 * Copyright (C) 2026 The OmniBoard Contributors
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.theme.backdrop.BackdropHue
import dev.patrickgold.florisboard.ime.theme.backdrop.BackdropId
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * Inline hue slider for the selected image backdrop. While the thumb moves the keyboard follows
 * through [BackdropHue.preview]; the per-backdrop preference is written once, when the drag ends.
 */
@Composable
internal fun BackdropHueControl(selectedId: BackdropId) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val rawShifts by prefs.theme.backdropHueShifts.observeAsState()
    val enabled = BackdropHue.supports(selectedId)
    val saved = BackdropHue.shiftFor(rawShifts, selectedId)
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(saved.toFloat()) }
    LaunchedEffect(saved, selectedId) { if (!dragging) value = saved.toFloat() }
    // Leaving the screen mid-drag must not leave the keyboard on an unsaved preview.
    DisposableEffect(Unit) { onDispose { BackdropHue.preview.value = null } }

    fun save(degrees: Int) {
        scope.launch {
            prefs.theme.backdropHueShifts.set(BackdropHue.withShift(rawShifts, selectedId, degrees))
            BackdropHue.preview.value = null
        }
    }

    val degrees = BackdropHue.fromSlider(value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = null,
            modifier = Modifier.padding(top = 4.dp).size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringRes(R.string.pref__theme__backdrop__hue),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (degrees == 0) stringRes(R.string.pref__theme__backdrop__hue_original)
                    else "%+d°".format(degrees),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(
                    onClick = {
                        value = 0f
                        save(0)
                    },
                    enabled = enabled && degrees != 0,
                ) {
                    Text(stringRes(R.string.pref__theme__backdrop__hue_reset))
                }
            }
            Text(
                text = stringRes(
                    if (enabled) R.string.pref__theme__backdrop__hue_summary
                    else R.string.pref__theme__backdrop__hue_unsupported,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HueStrip(degrees)
            Slider(
                value = value,
                onValueChange = {
                    dragging = true
                    value = it
                    BackdropHue.preview.value = selectedId.storedValue to BackdropHue.fromSlider(it)
                },
                onValueChangeFinished = {
                    dragging = false
                    save(BackdropHue.fromSlider(value))
                },
                valueRange = BackdropHue.MIN_DEGREES.toFloat()..BackdropHue.MAX_DEGREES.toFloat(),
                enabled = enabled,
            )
        }
    }
}

/** The colour wheel as it lands after the shift: red..red, rotated by [degrees]. */
@Composable
private fun HueStrip(degrees: Int) {
    val stops = remember(degrees) {
        List(13) { i -> Color.hsv(((i * 30 + degrees) % 360 + 360) % 360f, 0.75f, 0.95f) }
    }
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Brush.horizontalGradient(stops)),
    )
}
