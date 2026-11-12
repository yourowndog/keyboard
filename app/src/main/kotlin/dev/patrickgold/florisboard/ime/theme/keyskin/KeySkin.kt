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

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyRole

/**
 * The fixed inventory of key textures a skin pack paints, the same slots commercial sci-fi
 * keyboards use: one texture for every ordinary key, and its own texture for each special key.
 *
 * [id] is the file stem inside a pack folder (`key@640.9.png`), so it is never renamed.
 */
enum class KeySkinSlot(val id: String) {
    KEY("key"),
    KEY_PRESSED("key_pressed"),
    SPACE("space"),
    ENTER("enter"),
    DELETE("delete"),
    SHIFT("shift"),
    SHIFT_ON("shift_on"),
    ARROW_LEFT("arrow_left"),
    ARROW_RIGHT("arrow_right"),
    ARROW_UP("arrow_up"),
    ARROW_DOWN("arrow_down"),
    POPUP("popup");

    /**
     * Special-key textures carry their own art (a DELETE grille, an ENTER elbow, the shift lamp,
     * arrow marks, the spacebar's readout), so the keyboard does not draw its label over them.
     */
    val carriesOwnGlyph: Boolean
        get() = this != KEY && this != KEY_PRESSED && this != POPUP

    companion object {
        fun fromId(id: String): KeySkinSlot? = entries.firstOrNull { it.id == id }

        private val ORDINARY_ROLES = setOf(KeyRole.ALPHA, KeyRole.NUMBER, KeyRole.SYMBOL)

        /**
         * The slots to try for a key, best first; empty when the skin leaves the key to the theme.
         *
         * A full pack ([coversSpecialKeys]) gives space, enter, delete, shift and the arrows their
         * own slots and every other key the ordinary texture; a pack missing a special slot falls
         * back to the ordinary one. A swatch skins only character keys (letters, digits, symbols):
         * the theme keeps its special keys, so they stay distinct from the field of letters.
         * Pure, so the mapping is unit tested without Android.
         */
        fun candidatesFor(
            code: Int,
            role: KeyRole,
            isShiftOn: Boolean,
            isPressed: Boolean,
            coversSpecialKeys: Boolean = true,
        ): List<KeySkinSlot> {
            if (!coversSpecialKeys) {
                if (role !in ORDINARY_ROLES) return emptyList()
                return if (isPressed) listOf(KEY_PRESSED, KEY) else listOf(KEY)
            }
            val special = when (code) {
                KeyCode.ARROW_LEFT -> ARROW_LEFT
                KeyCode.ARROW_RIGHT -> ARROW_RIGHT
                KeyCode.ARROW_UP -> ARROW_UP
                KeyCode.ARROW_DOWN -> ARROW_DOWN
                else -> when (role) {
                    KeyRole.SPACE -> SPACE
                    KeyRole.ENTER -> ENTER
                    KeyRole.DELETE -> DELETE
                    KeyRole.SHIFT, KeyRole.CAPS_LOCK -> if (isShiftOn) SHIFT_ON else SHIFT
                    else -> null
                }
            }
            return buildList {
                when (special) {
                    null -> if (isPressed) add(KEY_PRESSED)
                    SHIFT_ON -> { add(SHIFT_ON); add(SHIFT) }
                    else -> add(special)
                }
                add(KEY)
            }
        }
    }
}

/**
 * Which skin is active. Stored in `prefs.theme.keySkinId` and always read back through [parse], so
 * a missing pack or a value from a newer build reads as [None] and the theme draws keys as before.
 *
 * | Stored value       | Meaning                                                                |
 * | ------------------ | ---------------------------------------------------------------------- |
 * | `""`               | [None]: the theme's own key surfaces                                   |
 * | `pack:<folder>`    | [Pack]: `assets/keyskins/<folder>/`, one texture per [KeySkinSlot]     |
 * | `swatch:<file>`    | [Swatch]: one texture from `assets/keyskins/swatches/` on every key    |
 *
 * Packs and swatches come from the gitignored `private-assets/` folder of a local build; the
 * repository ships none (docs/theming/key-skins.md).
 */
sealed interface KeySkinId {
    val storedValue: String

    data object None : KeySkinId {
        override val storedValue = ""
    }

    /** Whether this skin replaces special keys too, or only character keys. */
    val coversSpecialKeys: Boolean get() = this is Pack

    data class Pack(val folder: String) : KeySkinId {
        override val storedValue get() = "$PREFIX_PACK$folder"
    }

    data class Swatch(val fileName: String) : KeySkinId {
        override val storedValue get() = "$PREFIX_SWATCH$fileName"
    }

    companion object {
        const val PREFIX_PACK = "pack:"
        const val PREFIX_SWATCH = "swatch:"

        /** Never throws; anything unrecognised or unsafe reads as [None]. */
        fun parse(raw: String?): KeySkinId {
            val value = raw?.trim().orEmpty()
            return when {
                value.startsWith(PREFIX_PACK) ->
                    value.removePrefix(PREFIX_PACK).takeIf { KeySkinFiles.isSafeFolder(it) }?.let { Pack(it) } ?: None
                value.startsWith(PREFIX_SWATCH) ->
                    value.removePrefix(PREFIX_SWATCH).takeIf { KeySkinFiles.parse(it) != null }
                        ?.let { Swatch(it) } ?: None
                else -> None
            }
        }
    }
}

/** A texture file name, decoded: `enter@640.9.png` is the enter slot, drawn for 640 dpi, nine-patch. */
data class KeySkinFile(val stem: String, val densityDpi: Int?, val isNinePatch: Boolean)

/** File-name rules for packs. Pure, so they are unit tested directly. */
object KeySkinFiles {
    /** Folder under `assets/` that holds the packs, and the swatch folder inside it. */
    const val DIR = "keyskins"
    const val SWATCH_DIR = "swatches"

    private val FILE = Regex("""([a-z0-9_-]{1,48})(?:@(\d{2,4}))?(\.9)?\.(png|webp)""")

    fun isSafeFolder(name: String): Boolean =
        name.isNotEmpty() && name.length <= 64 && name != SWATCH_DIR && !name.startsWith(".") &&
            name.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    fun parse(fileName: String): KeySkinFile? {
        val m = FILE.matchEntire(fileName) ?: return null
        return KeySkinFile(
            stem = m.groupValues[1],
            densityDpi = m.groupValues[2].toIntOrNull()?.takeIf { it in 80..960 },
            isNinePatch = m.groupValues[3].isNotEmpty(),
        )
    }

    /** "sfk-07" -> "Sci-Fi 07"; "swatch-021@320.png" -> "Swatch 021"; other names are de-dashed. */
    fun labelFor(name: String): String {
        val stem = name.substringBefore('@').substringBefore('.')
        return when {
            stem.startsWith("sfk-") -> "Sci-Fi ${stem.removePrefix("sfk-")}"
            stem.startsWith("swatch-") -> "Swatch ${stem.removePrefix("swatch-")}"
            else -> stem.replace('_', ' ').replace('-', ' ').trim()
        }
    }
}
