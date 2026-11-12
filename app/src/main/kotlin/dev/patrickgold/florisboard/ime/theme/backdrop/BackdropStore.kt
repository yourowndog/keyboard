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

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** One row in the backdrop picker. */
data class BackdropOption(val id: BackdropId, val label: String)

/**
 * Finds and imports backdrop images. Nothing here runs on the key path: the picker calls it from
 * settings, and the keyboard only resolves the one selected id to a model for Coil.
 */
class BackdropStore(private val context: Context) {
    private val importDir: File get() = File(context.filesDir, BackdropFiles.DIR)

    /** Every selectable backdrop, in picker order: none, built-ins, private assets, imports. */
    fun options(): List<BackdropOption> = buildList {
        add(BackdropOption(BackdropId.None, "None (theme background)"))
        BuiltinBackdrop.entries.forEach { add(BackdropOption(BackdropId.Builtin(it), it.label)) }
        privateAssetNames().forEach {
            add(BackdropOption(BackdropId.PrivateAsset(it), "${BackdropFiles.labelFor(it)} (bundled)"))
        }
        importedNames().forEach {
            add(BackdropOption(BackdropId.Imported(it), BackdropFiles.labelFor(it)))
        }
    }

    /**
     * Images under `assets/backdrops/`. The repository ships none; they appear only in a build
     * made with a populated, gitignored `private-assets/backdrops/` folder.
     */
    fun privateAssetNames(): List<String> = runCatching {
        context.assets.list(BackdropFiles.DIR).orEmpty().filter { BackdropFiles.isSafeName(it) }.sorted()
    }.getOrDefault(emptyList())

    fun importedNames(): List<String> =
        importDir.listFiles().orEmpty()
            .filter { it.isFile && BackdropFiles.isSafeName(it.name) }
            .map { it.name }
            .sorted()

    /**
     * Copies a picked image into app storage and returns its id. Refuses anything that is not a
     * decodable image or is larger than [BackdropFiles.MAX_IMPORT_BYTES], so a bad pick fails here
     * in settings rather than as a blank keyboard.
     */
    fun import(uri: Uri): Result<BackdropId.Imported> = runCatching {
        val resolver = context.contentResolver
        var displayName: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(0)
                    size = if (cursor.isNull(1)) null else cursor.getLong(1)
                }
            }
        size?.let { require(it <= BackdropFiles.MAX_IMPORT_BYTES) { "Image is larger than 8 MB" } }

        importDir.mkdirs()
        val name = BackdropFiles.sanitize(displayName, resolver.getType(uri), importedNames().toSet())
        val target = File(importDir, name)
        val partial = File(importDir, ".$name.partial")
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open the image" }
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= BackdropFiles.MAX_IMPORT_BYTES) { "Image is larger than 8 MB" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(partial.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image Android can decode" }
            check(partial.renameTo(target)) { "Could not save the image" }
        } finally {
            partial.delete()
        }
        BackdropId.Imported(name)
    }

    fun delete(id: BackdropId.Imported): Boolean = File(importDir, id.fileName).delete()

    /** The model Coil loads for an image backdrop, or null for [BackdropId.None] and built-ins. */
    fun imageModel(id: BackdropId): Any? = when (id) {
        is BackdropId.Imported -> File(importDir, id.fileName).takeIf { it.isFile }
        is BackdropId.PrivateAsset -> "file:///android_asset/${BackdropFiles.DIR}/${id.fileName}"
        else -> null
    }
}
