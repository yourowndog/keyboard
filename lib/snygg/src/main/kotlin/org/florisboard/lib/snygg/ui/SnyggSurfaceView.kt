/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package org.florisboard.lib.snygg.ui

import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.Animatable
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.times
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toRect
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.Bitmap
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.Image
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.snygg.SnyggQueryAttributes
import org.florisboard.lib.snygg.SnyggSelector

/**
 * Specialized layout composable rendering a background color/image to a [SurfaceView].
 *
 * This composable infers its style from the current [SnyggTheme][org.florisboard.lib.snygg.SnyggTheme], which is
 * required to be provided by [ProvideSnyggTheme].
 *
 * @param elementName The name of this element. If `null` the style will be inherited from the parent element.
 * @param attributes The attributes of the element used to refine the query.
 * @param selector A specific SnyggSelector to query the style for.
 * @param modifier The modifier to be applied to the layout.
 * @param backgroundImageDescription The content description of the background image.
 *
 * @since 0.5.0-beta04
 *
 * @see [Box]
 * @see [SurfaceView]
 */
@Composable
fun SnyggSurfaceView(
    elementName: String? = null,
    attributes: SnyggQueryAttributes = emptyMap(),
    selector: SnyggSelector? = null,
    modifier: Modifier = Modifier,
    backgroundImageDescription: String? = null,
) {
    ProvideSnyggStyle(elementName, attributes, selector) { style ->
        val assetResolver = LocalSnyggAssetResolver.current
        val context = LocalContext.current
        val imageLoader = SingletonImageLoader.get(context)

        val backgroundColor = style.background(Color.Black)
        val imagePath = remember(style, assetResolver) {
            style.backgroundImage.uriOrNull()?.let { imageUri ->
                assetResolver.resolveAbsolutePath(imageUri).getOrNull()
            }
        }
        var loadedImage by remember { mutableStateOf<Image?>(null) }
        val contentScale = style.contentScale()

        LaunchedEffect(imagePath) {
            if (imagePath == null) {
                loadedImage = null
                return@LaunchedEffect
            }
            val request = ImageRequest.Builder(context)
                .data(imagePath)
                .allowHardware(false)
                .build()
            val imageResult = imageLoader.execute(request)
            loadedImage = when (imageResult) {
                is SuccessResult -> when (val image = imageResult.image) {
                    is BitmapImage -> image.also { it.bitmap.prepareToDraw() }
                    is DrawableImage -> image
                    else -> null
                }
                else -> null
            }
        }

        var showSurfaceView by remember { mutableStateOf(false) }
        LifecycleResumeEffect(Unit) {
            showSurfaceView = true
            onPauseOrDispose {
                showSurfaceView = false
            }
        }

        if (showSurfaceView) {
            var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }
            // Bumped by the holder callback on every `surfaceCreated` and `surfaceChanged`, and used
            // as a redraw key. Surface *size* is deliberately not the key: two resizes that land back
            // on the same dimensions are still two freshly allocated buffers, and each one needs a
            // frame posted into it or it shows whatever the graphics allocator left there. Changing
            // the bottom offset resizes this surface, which is the reported glitch.
            var surfaceGeneration by remember { mutableIntStateOf(0) }
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    Log.d("SnyggSurfaceView", "creating new instance")
                    SurfaceView(context).apply {
                        if (AndroidVersion.ATLEAST_API34_U) {
                            setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT)
                        }
                        setZOrderOnTop(false)
                        holder.setFormat(PixelFormat.TRANSPARENT)
                    }
                },
                update = { surfaceView = it },
            )
            surfaceView?.let { surfaceView ->
                DisposableEffect(surfaceView) {
                    val callback = object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            surfaceGeneration++
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            surfaceGeneration++
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                    }
                    surfaceView.holder.addCallback(callback)
                    onDispose { surfaceView.holder.removeCallback(callback) }
                }
                LaunchedEffect(surfaceView, surfaceGeneration, backgroundColor, loadedImage, contentScale) {
                    val image = loadedImage
                    if (image is DrawableImage && image.drawable is Animatable) {
                        // Slow path, need animation
                        val fps = 30L // TODO: read frame delays from drawable
                        val animatedDrawable = image.drawable as Animatable
                        try {
                            animatedDrawable.start()
                            while (isActive) {
                                surfaceView.drawToSurface(backgroundColor, loadedImage, contentScale)
                                delay(1000L / fps)
                            }
                        } finally {
                            animatedDrawable.stop()
                        }
                    } else {
                        // Fast path: render once, but do not treat an invalid surface as a reason to
                        // stop. `surfaceCreated` is the normal wake-up and re-runs this effect; the
                        // retry covers the gap between the view existing and its surface being ready,
                        // which is exactly when a first frame would otherwise be dropped for good.
                        var attempt = 0
                        while (isActive && !surfaceView.drawToSurface(backgroundColor, loadedImage, contentScale)) {
                            attempt++
                            if (attempt >= INVALID_SURFACE_RETRIES) {
                                Log.w("SnyggSurfaceView", "giving up after $attempt invalid-surface attempts")
                                break
                            }
                            delay(INVALID_SURFACE_RETRY_DELAY_MS)
                        }
                    }
                }
            }
        }
    }
}

/** How many times a dropped frame is retried before the surface is written off. */
private const val INVALID_SURFACE_RETRIES = 5

/** How long to wait between retries. Five of these is well under one perceptible pause. */
private const val INVALID_SURFACE_RETRY_DELAY_MS = 16L

/**
 * Posts one frame of [color] and [image] to this view's surface.
 *
 * @return `true` if a frame was posted, `false` if the surface was not valid and the caller should
 *   try again. Returning the outcome rather than swallowing it is what lets the caller retry; the
 *   previous version logged a warning and left the surface holding whatever it already had.
 */
private fun SurfaceView.drawToSurface(
    color: Color,
    image: Image?,
    contentScale: ContentScale,
): Boolean {
    Log.d("SnyggSurfaceView", "drawToSurface(color=$color, image=$image)")
    val surface = holder.surface
    if (!surface.isValid) {
        Log.w("SnyggSurfaceView", "drawToSurface: surface.isValid=false, may indicate state issue")
        return false
    }
    val canvas = surface.lockCanvas(null)
    try {
        // SRC, not the default SRC_OVER. `lockCanvas(null)` hands back one of the swap chain's
        // buffers, which still holds an earlier frame; compositing onto that is invisible for an
        // opaque colour and wrong for a translucent one, where each redraw would blend on top of the
        // last and creep towards opaque. SRC replaces the buffer, so the posted frame is exactly the
        // colour the theme asked for, alpha included.
        canvas.drawColor(color.toArgb(), PorterDuff.Mode.SRC)
        when (image) {
            is BitmapImage -> image.bitmap.drawToSurface(canvas, contentScale)
            is DrawableImage -> image.drawToSurface(canvas, contentScale)
        }
    } finally {
        surface.unlockCanvasAndPost(canvas)
    }
    return true
}

private fun Bitmap.drawToSurface(canvas: Canvas, contentScale: ContentScale) {
    val bitmap = this
    val srcSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
    val canvasSize = Size(canvas.width.toFloat(), canvas.height.toFloat())
    val scaleFactor = contentScale.computeScaleFactor(srcSize, canvasSize)
    Log.d("SnyggSurfaceView",
        "drawToSurface: srcSize=$srcSize, dstSize=$canvasSize, scaleFactor=$scaleFactor")
    val dstSize = srcSize.times(scaleFactor)
    val srcRect = srcSize.toRect().toAndroidRectF().toRect()
    val dstRect = dstSize.toRect().let {
        // Align center behavior
        it.translate(canvasSize.center - it.center)
    }.toAndroidRectF().toRect()
    canvas.drawBitmap(bitmap, srcRect, dstRect, null)
}

private fun DrawableImage.drawToSurface(canvas: Canvas, contentScale: ContentScale) {
    this.toBitmap().drawToSurface(canvas, contentScale)
}

