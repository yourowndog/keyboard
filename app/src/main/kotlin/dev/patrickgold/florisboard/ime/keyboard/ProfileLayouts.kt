/*
 * Copyright (C) 2026 The OmniBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName

/**
 * Which bundled layouts the active [KeyboardProfile] puts in front of a subtype.
 *
 * A subtype names one layout per mode, and that is the Coding board: `qwerty_wide` letters with
 * its utility rows, and the `western_wide` symbol pages behind the atom key. The Text profile is
 * the conventional board from the LCARS Jefferies mockup, so for those same subtypes it swaps in
 * its own letters and symbol pages. The symbol pages also feed the hint plates above the letters
 * (`LayoutManager` pairs symbol rows with letter rows), which is how each profile gets the plates
 * the mockup shows for it.
 *
 * Only the Coding family is swapped. A subtype on any other layout, such as ЙЦУКЕН, keeps its own
 * layouts in both profiles; Text has nothing better to offer it.
 */
internal object ProfileLayouts {
    private const val EXTENSION = "org.florisboard.layouts"

    /** The Coding letter layouts the Text profile replaces. */
    private val CODING_CHARACTERS = setOf("qwerty_wide", "qwerty_wide_full", "qwerty_wide_swipe")

    const val TEXT_CHARACTERS = "qwerty_text"
    const val TEXT_SYMBOLS = "western_text"

    /**
     * The layout [mode] should load for a subtype whose letter layout is [subtypeCharacters] and
     * whose own layout for [mode] is [subtypeLayout].
     */
    fun resolve(
        profile: KeyboardProfile,
        mode: KeyboardMode,
        subtypeCharacters: ExtensionComponentName,
        subtypeLayout: ExtensionComponentName,
    ): ExtensionComponentName {
        if (profile != KeyboardProfile.TEXT || !isCodingFamily(subtypeCharacters)) return subtypeLayout
        return when (mode) {
            KeyboardMode.CHARACTERS -> ExtensionComponentName(EXTENSION, TEXT_CHARACTERS)
            KeyboardMode.SYMBOLS,
            KeyboardMode.SYMBOLS2 -> ExtensionComponentName(EXTENSION, TEXT_SYMBOLS)
            else -> subtypeLayout
        }
    }

    private fun isCodingFamily(characters: ExtensionComponentName): Boolean =
        characters.extensionId == EXTENSION && characters.componentId in CODING_CHARACTERS
}
