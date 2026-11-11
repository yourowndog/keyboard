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

package dev.patrickgold.florisboard.ime.keyboard.pack

import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData

/**
 * Turns a layout pack's authored `code` string into a [TextKeyData].
 *
 * Extracted from `LayoutManager` in Stage 06 so that validation and composition answer "does this
 * key resolve?" the same way. While the resolver lived privately inside composition, the only
 * possible answer at validation time was "assume yes", and an unresolvable code therefore became an
 * invisible spacer at runtime with nothing but a log line to show for it.
 *
 * The resolution order is unchanged from Stage 05, because packs on disk depend on it:
 *
 * 1. an internal key name, directly or through [ALIASES];
 * 2. a single character;
 * 3. a raw integer key code.
 */
object LayoutPackKeyResolver {

    private val internalKeyMap: Map<String, TextKeyData> =
        TextKeyData.InternalKeys.associateBy { it.label.uppercase() } + mapOf(
            // Enter is a positive code point, so it is intentionally absent from InternalKeys.
            // Packs have always named it as an internal action, not as a literal newline character.
            "ENTER" to TextKeyData(
                type = KeyType.ENTER_EDITING,
                code = KeyCode.ENTER,
                label = "enter",
            ),
        )

    /**
     * Pack-facing names for internal keys.
     *
     * These are the spellings the bundled `layouts/lcars_hacker_en_us.json` and hand-authored packs
     * use. They are part of the persisted vocabulary: removing one stops an existing pack from
     * resolving, which turns its key into a hole in the keyboard.
     */
    val ALIASES: Map<String, String> = mapOf(
        "KEYCODE_TAB" to "TAB",
        "KEYCODE_ENTER" to "ENTER",
        "KEYCODE_SPACE" to "SPACE",
        "KEYCODE_DELETE" to "DELETE",
        "KEYCODE_SHIFT" to "SHIFT",

        "CTRL_MOD" to "CTRL",
        "CTRL" to "CTRL",

        "MODE_SYMBOLS" to "VIEW_SYMBOLS",
        "MODE_SYMBOLS2" to "VIEW_SYMBOLS2",
        "MODE_CHARACTERS" to "VIEW_CHARACTERS",
        "MODE_NUMERIC" to "VIEW_NUMERIC",
        "MODE_NUMERIC_ADVANCED" to "VIEW_NUMERIC_ADVANCED",

        "MENU_TOGGLE" to "TOGGLE_ACTIONS_OVERFLOW",
    )

    /**
     * The key data [rawCode] names, or null when it names nothing this build knows.
     *
     * @param rawLabel what the key shows. An empty label falls back to the code itself, which is how
     *   a pack can write `{"code": "q"}` and get a `q` key.
     * @param style decides the key's popup group and nothing else.
     */
    fun resolve(rawCode: String, rawLabel: String, style: LayoutPackKeyStyle): TextKeyData? {
        val trimmedCode = rawCode.trim()
        if (trimmedCode.isBlank()) {
            return null
        }
        val preferredLabel = rawLabel.ifEmpty { trimmedCode }
        val groupId = when (style) {
            LayoutPackKeyStyle.SPECIAL_LEFT -> KeyData.GROUP_LEFT
            LayoutPackKeyStyle.SPECIAL_RIGHT -> KeyData.GROUP_RIGHT
            else -> KeyData.GROUP_DEFAULT
        }

        val normalizedCode = trimmedCode.uppercase()
        val lookupKey = ALIASES[normalizedCode] ?: normalizedCode
        internalKeyMap[lookupKey]?.let { base ->
            return base.copy(label = preferredLabel, groupId = groupId)
        }

        val codePointCount = trimmedCode.codePointCount(0, trimmedCode.length)
        if (codePointCount == 1) {
            val codePoint = trimmedCode.codePointAt(0)
            return TextKeyData(
                type = KeyType.CHARACTER,
                code = codePoint,
                label = preferredLabel,
                groupId = groupId,
            )
        }

        trimmedCode.toIntOrNull()?.let { numericCode ->
            TextKeyData.getCodeInfoAsTextKeyData(numericCode)?.let { base ->
                return base.copy(label = preferredLabel, groupId = groupId)
            }
        }

        return null
    }

    /** Whether [rawCode] resolves to a key. Used by validation, which does not need the key itself. */
    fun isResolvable(rawCode: String): Boolean =
        resolve(rawCode, rawLabel = "", style = LayoutPackKeyStyle.DEFAULT) != null
}
