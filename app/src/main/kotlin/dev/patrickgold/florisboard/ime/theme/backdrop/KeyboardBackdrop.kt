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

package dev.patrickgold.florisboard.ime.theme.backdrop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlin.random.Random
import org.florisboard.lib.android.AndroidVersion

/**
 * The user's chosen backdrop, drawn behind the key field and above the theme's chassis colour.
 *
 * Draws nothing for [BackdropId.None], so a keyboard without a backdrop renders exactly as before.
 * It is purely visual: no pointer input, so it can never steal a touch from a key.
 */
@Composable
fun KeyboardBackdrop(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val rawId by prefs.theme.backdropId.observeAsState()
    val opacity by prefs.theme.backdropOpacity.observeAsState()
    val animate by prefs.theme.backdropAnimate.observeAsState()
    val id = remember(rawId) { BackdropId.parse(rawId) }
    if (id == BackdropId.None || opacity <= 0) return

    val alpha = opacity.coerceIn(0, 100) / 100f
    when (id) {
        is BackdropId.Builtin -> Canvas(modifier.alpha(alpha)) { drawBuiltin(id.kind) }
        is BackdropId.PrivateAsset, is BackdropId.Imported -> {
            val context = LocalContext.current
            val store = remember(context) { BackdropStore(context) }
            val model = remember(id) { store.imageModel(id) } ?: return
            Box(modifier.alpha(alpha)) {
                AsyncImage(
                    model = remember(model, animate) {
                        ImageRequest.Builder(context)
                            .data(model)
                            // Matches SnyggBox: hardware bitmaps have crashed inside the IME window.
                            .allowHardware(false)
                            .apply {
                                // Animated GIF/WebP play only when asked; otherwise the first frame.
                                if (animate) {
                                    decoderFactory(
                                        if (AndroidVersion.ATLEAST_API28_P) AnimatedImageDecoder.Factory()
                                        else GifDecoder.Factory()
                                    )
                                }
                            }
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        BackdropId.None -> Unit
    }
}

private val LcarsCyan = Color(0xFF6FD3E8)
private val LcarsAmber = Color(0xFFFF9C3A)

internal fun DrawScope.drawBuiltin(kind: BuiltinBackdrop) = when (kind) {
    BuiltinBackdrop.GRID -> drawGrid()
    BuiltinBackdrop.STARFIELD -> drawStarfield()
    BuiltinBackdrop.SCANLINES -> drawScanlines()
}

private fun DrawScope.drawGrid() {
    val pitch = 18.dp.toPx()
    val minor = LcarsCyan.copy(alpha = 0.10f)
    val major = LcarsCyan.copy(alpha = 0.22f)
    val stroke = 1.dp.toPx()
    var i = 0
    var x = 0f
    while (x <= size.width) {
        drawLine(if (i % 5 == 0) major else minor, Offset(x, 0f), Offset(x, size.height), stroke)
        x += pitch; i++
    }
    i = 0
    var y = size.height
    while (y >= 0f) {
        drawLine(if (i % 5 == 0) major else minor, Offset(0f, y), Offset(size.width, y), stroke)
        y -= pitch; i++
    }
}

private fun DrawScope.drawStarfield() {
    // Fixed seed: the same stars every frame and every launch.
    val random = Random(1701)
    val count = (size.width * size.height / 9000f).toInt().coerceIn(40, 400)
    repeat(count) {
        val bright = random.nextFloat()
        val color = if (bright > 0.97f) LcarsAmber else Color.White
        drawCircle(
            color = color.copy(alpha = 0.25f + bright * 0.55f),
            radius = (0.6f + bright * 1.2f).dp.toPx(),
            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
        )
    }
}

private fun DrawScope.drawScanlines() {
    val pitch = 3.dp.toPx()
    val stroke = 1.dp.toPx()
    var y = 0f
    while (y <= size.height) {
        val fade = y / size.height
        drawLine(LcarsCyan.copy(alpha = 0.04f + 0.14f * fade), Offset(0f, y), Offset(size.width, y), stroke)
        y += pitch
    }
}
