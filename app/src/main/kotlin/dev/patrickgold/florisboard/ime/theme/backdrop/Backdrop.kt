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

/**
 * What is drawn behind the key field, chosen by the user independently of the theme.
 *
 * Stored in `prefs.theme.backdropId` as a short string so an unknown or stale value can always be
 * parsed into *something* and the keyboard still comes up:
 *
 * | Stored value      | Meaning                                                                 |
 * | ----------------- | ----------------------------------------------------------------------- |
 * | `""`              | [None]: the theme's own background, exactly as before backdrops existed |
 * | `builtin:<id>`    | [Builtin]: drawn in code, original art, ships in the APK                |
 * | `asset:<file>`    | [PrivateAsset]: an image under `assets/backdrops/` that came from the   |
 * |                   | gitignored `private-assets/` folder of a local build                    |
 * | `file:<file>`     | [Imported]: an image the user imported, kept in `files/backdrops/`      |
 *
 * The ids are written to preferences and to backups, so they are never renamed.
 */
sealed interface BackdropId {
    val storedValue: String

    data object None : BackdropId {
        override val storedValue = ""
    }

    data class Builtin(val kind: BuiltinBackdrop) : BackdropId {
        override val storedValue get() = "$PREFIX_BUILTIN${kind.id}"
    }

    data class PrivateAsset(val fileName: String) : BackdropId {
        override val storedValue get() = "$PREFIX_ASSET$fileName"
    }

    data class Imported(val fileName: String) : BackdropId {
        override val storedValue get() = "$PREFIX_FILE$fileName"
    }

    companion object {
        const val PREFIX_BUILTIN = "builtin:"
        const val PREFIX_ASSET = "asset:"
        const val PREFIX_FILE = "file:"

        /**
         * Never throws. Anything unrecognised, including a builtin id from a newer build or a
         * file name that would escape its folder, reads as [None].
         */
        fun parse(raw: String?): BackdropId {
            val value = raw?.trim().orEmpty()
            return when {
                value.startsWith(PREFIX_BUILTIN) ->
                    BuiltinBackdrop.fromId(value.removePrefix(PREFIX_BUILTIN))?.let { Builtin(it) } ?: None
                value.startsWith(PREFIX_ASSET) ->
                    value.removePrefix(PREFIX_ASSET).takeIf { BackdropFiles.isSafeName(it) }
                        ?.let { PrivateAsset(it) } ?: None
                value.startsWith(PREFIX_FILE) ->
                    value.removePrefix(PREFIX_FILE).takeIf { BackdropFiles.isSafeName(it) }
                        ?.let { Imported(it) } ?: None
                else -> None
            }
        }
    }
}

/**
 * Backdrops drawn in code. They are original line art in the LCARS idiom (no licensed material), so
 * every build has something to pick even without `private-assets/` or an import.
 */
enum class BuiltinBackdrop(val id: String, val label: String) {
    /** A faint engineering grid, like the readout field behind an LCARS panel. */
    GRID("grid", "LCARS grid"),

    /** Sparse fixed stars on black. Deterministic, so it never shimmers between frames. */
    STARFIELD("starfield", "Starfield"),

    /** Horizontal scan lines that fade towards the top, a dim viewscreen. */
    SCANLINES("scanlines", "Viewscreen scan lines");

    companion object {
        fun fromId(id: String): BuiltinBackdrop? = entries.firstOrNull { it.id == id }
    }
}

/** File-name rules shared by imports and private assets. Pure, so it is unit tested directly. */
object BackdropFiles {
    /** Folder under `filesDir` for imports, and under `assets/` for private-asset backdrops. */
    const val DIR = "backdrops"

    /** Formats Android decodes natively. GIF decodes as its first frame until animation lands. */
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif")

    /** 8 MiB: a full-screen PNG is well under this, and a mistaken video pick is refused. */
    const val MAX_IMPORT_BYTES = 8L * 1024 * 1024

    fun isSafeName(name: String): Boolean =
        name.isNotEmpty() &&
            name.length <= 96 &&
            !name.startsWith(".") &&
            name.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' } &&
            name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /**
     * Turns a picker's display name into a safe, unique file name. [taken] holds the names already
     * in the folder; a clash gets a numeric suffix rather than overwriting the earlier import.
     */
    fun sanitize(displayName: String?, mimeType: String?, taken: Set<String>): String {
        val raw = displayName?.substringAfterLast('/')?.trim().orEmpty()
        val extFromName = raw.substringAfterLast('.', "").lowercase().takeIf { it in IMAGE_EXTENSIONS }
        val ext = extFromName ?: extensionForMime(mimeType) ?: "png"
        val stem = raw.substringBeforeLast('.', raw)
            .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
            .joinToString("")
            .trim('-', '_', '.')
            .take(80)
            .ifEmpty { "backdrop" }
        var candidate = "$stem.$ext"
        var n = 2
        while (candidate in taken) {
            candidate = "$stem-$n.$ext"
            n++
        }
        return candidate
    }

    fun extensionForMime(mimeType: String?): String? = when (mimeType?.lowercase()) {
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> null
    }

    /** "lcars_bridge-panel.png" -> "lcars bridge panel", for the picker. */
    fun labelFor(fileName: String): String =
        fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
}
