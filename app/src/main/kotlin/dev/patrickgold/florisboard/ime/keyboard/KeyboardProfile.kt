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

package dev.patrickgold.florisboard.ime.keyboard

/**
 * The keyboard a user is currently arranging their input around.
 *
 * A profile owns its own geometry, row visibility, and key customization. Everything else — window
 * offsets, one-handed mode, hint behaviour, popup timing — stays global, because those describe the
 * device or the person rather than the keyboard being shown.
 *
 * [id] is persisted. It is deliberately not the enum name: the enum is free to be renamed, and the
 * persisted vocabulary is not.
 */
enum class KeyboardProfile(val id: String) {
    /**
     * Prose. Reserved and not yet selectable — the runtime layout arrives in Stage 08, and
     * [isSelectable] stays false until it does so nothing can route to a keyboard that has no
     * assets behind it.
     */
    TEXT("text"),

    /**
     * Code, shell, and anything with a symbol row. This is what OmniBoard ships today, and it is
     * where an upgrading user's existing settings land.
     */
    CODING("coding");

    /** Whether a user may currently choose this profile. See [TEXT]. */
    val isSelectable: Boolean
        get() = this == CODING

    companion object {
        /**
         * What an install resolves to when nothing has been chosen, and what an unrecognised
         * persisted id falls back to. Must always be [isSelectable].
         */
        val Default = CODING

        /**
         * Resolves a persisted id, tolerating anything. Absent, misspelled, truncated, or written
         * by a newer version all resolve to [Default] rather than throwing — a corrupt preference
         * must not be able to stop the keyboard from coming up.
         *
         * A profile that exists but is not yet selectable also resolves to [Default]; that is what
         * keeps a stale `text` id from selecting a runtime that Stage 08 has not built yet.
         */
        fun fromId(id: String?): KeyboardProfile {
            val match = entries.find { it.id == id } ?: return Default
            return if (match.isSelectable) match else Default
        }
    }
}
