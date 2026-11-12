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

package dev.patrickgold.florisboard.ime.theme.keyskin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.NinePatch
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One decoded slot texture. Compiled nine-patches (the `npTc` chunk Android's resource compiler
 * writes) keep their corners at a fixed physical size and stretch only the middle, so the same
 * texture fits a narrow symbol key and a wide spacebar. Plain images are stretched to the key.
 */
class KeySkinTexture(private val bitmap: Bitmap, chunk: ByteArray?, private val densityDpi: Int) {
    private val ninePatch: NinePatch? =
        chunk?.takeIf { NinePatch.isNinePatchChunk(it) }?.let { NinePatch(bitmap, it) }

    val isNinePatch: Boolean get() = ninePatch != null

    /** Draws the texture filling `0,0 - width,height` of [canvas], scaled from its source density. */
    fun draw(canvas: Canvas, width: Float, height: Float, targetDpi: Int, paint: Paint?) {
        val patch = ninePatch
        if (patch != null) {
            // Scale the whole canvas so fixed regions keep their designed physical size.
            val scale = targetDpi.toFloat() / densityDpi
            canvas.save()
            canvas.scale(scale, scale)
            patch.draw(canvas, RectF(0f, 0f, width / scale, height / scale), paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width, height), paint)
        }
    }
}

/** A loaded skin: whichever slots its pack provides. */
class KeySkin(val id: KeySkinId, private val textures: Map<KeySkinSlot, KeySkinTexture>) {
    val slots: Set<KeySkinSlot> get() = textures.keys

    operator fun get(slot: KeySkinSlot): KeySkinTexture? = textures[slot]

    /** The first slot in [candidates] this skin has, with its texture. */
    fun resolve(candidates: List<KeySkinSlot>): Pair<KeySkinSlot, KeySkinTexture>? =
        candidates.firstNotNullOfOrNull { slot -> textures[slot]?.let { slot to it } }
}

/** One row in the skin picker. */
data class KeySkinOption(val id: KeySkinId, val label: String)

/**
 * Finds and decodes skins under `assets/keyskins/`. The repository ships none: they appear only in
 * a local build made with a populated, gitignored `private-assets/keyskins/` folder.
 */
class KeySkinStore(private val context: Context) {
    fun options(): List<KeySkinOption> = buildList {
        add(KeySkinOption(KeySkinId.None, "None (theme keys)"))
        packFolders().forEach { add(KeySkinOption(KeySkinId.Pack(it), KeySkinFiles.labelFor(it))) }
        swatchNames().forEach { add(KeySkinOption(KeySkinId.Swatch(it), KeySkinFiles.labelFor(it))) }
    }

    fun packFolders(): List<String> = list(KeySkinFiles.DIR).filter { KeySkinFiles.isSafeFolder(it) }.sorted()

    fun swatchNames(): List<String> =
        list("${KeySkinFiles.DIR}/${KeySkinFiles.SWATCH_DIR}").filter { KeySkinFiles.parse(it) != null }.sorted()

    private fun list(path: String): List<String> =
        runCatching { context.assets.list(path).orEmpty().toList() }.getOrDefault(emptyList())

    /** Decodes a skin, or returns null for [KeySkinId.None] and for a pack with no usable texture. */
    fun load(id: KeySkinId): KeySkin? {
        synchronized(cache) { cache[id] }?.let { return it }
        val textures: Map<KeySkinSlot, KeySkinTexture> = when (id) {
            KeySkinId.None -> return null
            is KeySkinId.Pack -> {
                val dir = "${KeySkinFiles.DIR}/${id.folder}"
                list(dir).mapNotNull { name ->
                    val file = KeySkinFiles.parse(name) ?: return@mapNotNull null
                    val slot = KeySkinSlot.fromId(file.stem) ?: return@mapNotNull null
                    decode("$dir/$name", file)?.let { slot to it }
                }.toMap()
            }
            is KeySkinId.Swatch -> {
                val file = KeySkinFiles.parse(id.fileName) ?: return null
                decode("${KeySkinFiles.DIR}/${KeySkinFiles.SWATCH_DIR}/${id.fileName}", file)
                    ?.let { mapOf(KeySkinSlot.KEY to it) }.orEmpty()
            }
        }
        if (textures.isEmpty()) return null
        return KeySkin(id, textures).also { synchronized(cache) { cache[id] = it } }
    }

    private fun decode(path: String, file: KeySkinFile): KeySkinTexture? = runCatching {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        KeySkinTexture(bitmap, bitmap.ninePatchChunk, file.densityDpi ?: DEFAULT_SOURCE_DPI)
    }.getOrNull()

    companion object {
        /** A texture with no `@dpi` in its name is assumed drawn for xxhdpi. */
        const val DEFAULT_SOURCE_DPI = 480

        /** Decoded skins, kept so the keyboard and the picker share one copy. Small: a pack is ~150 KB. */
        private val cache = object : LinkedHashMap<KeySkinId, KeySkin>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<KeySkinId, KeySkin>?) = size > 6
        }
    }
}

/** The active skin, or null while none is chosen or it is still decoding (keys then draw as themed). */
@Composable
fun rememberActiveKeySkin(): KeySkin? {
    val prefs by FlorisPreferenceStore
    val rawId by prefs.theme.keySkinId.observeAsState()
    val id = remember(rawId) { KeySkinId.parse(rawId) }
    val context = LocalContext.current
    val store = remember(context) { KeySkinStore(context.applicationContext) }
    val skin by produceState<KeySkin?>(initialValue = null, id) {
        value = withContext(Dispatchers.IO) { store.load(id) }
    }
    return skin
}

/** Darkens a texture for the pressed or toggled-on look when the pack has no pressed variant. */
private val PressedPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
    colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.62f, 0.62f, 0.62f, 1f) })
}
private val NormalPaint = Paint(Paint.FILTER_BITMAP_FLAG)

/** Draws [texture] from the origin over [keySize] (the whole draw area by default); [darken] gives the pressed look. */
fun DrawScope.drawKeySkinTexture(texture: KeySkinTexture, darken: Boolean, keySize: Size = size) {
    val targetDpi = (density * 160f).toInt()
    drawIntoCanvas { canvas ->
        texture.draw(canvas.nativeCanvas, keySize.width, keySize.height, targetDpi, if (darken) PressedPaint else NormalPaint)
    }
}
