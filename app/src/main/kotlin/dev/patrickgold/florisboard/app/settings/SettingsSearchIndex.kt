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

package dev.patrickgold.florisboard.app.settings

import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.Routes

/**
 * One searchable place in settings: the screen [route] opens, the label shown in results, which
 * panel it belongs to, and extra words people might type for it (synonyms, old FlorisBoard names).
 *
 * Search matches on the label and the keywords, so a result always names something the user can
 * see on the screen it opens. Keep labels as string resources so a rename shows up in search too.
 */
data class SettingsSearchEntry(
    val labelRes: Int,
    val panelRes: Int,
    val route: Any,
    val keywords: List<String> = emptyList(),
)

object SettingsSearchIndex {
    val entries: List<SettingsSearchEntry> = listOf(
        // Look
        SettingsSearchEntry(R.string.pref__theme__day, R.string.settings__look__title, Routes.Settings.Theme,
            listOf("theme", "colour", "color", "lcars", "skin", "light")),
        SettingsSearchEntry(R.string.pref__theme__night, R.string.settings__look__title, Routes.Settings.Theme,
            listOf("theme", "dark", "jefferies", "night")),
        SettingsSearchEntry(R.string.pref__theme__mode__label, R.string.settings__look__title, Routes.Settings.Theme,
            listOf("day", "night", "sunrise", "sunset", "auto")),
        SettingsSearchEntry(R.string.pref__theme__backdrop__label, R.string.settings__look__title, Routes.Settings.Theme,
            listOf("background", "wallpaper", "gif", "animation", "image")),
        SettingsSearchEntry(R.string.pref__theme__backdrop__hue, R.string.settings__look__title, Routes.Settings.Theme,
            listOf("hue", "tint", "recolour", "recolor", "colour shift", "color shift", "gif")),
        SettingsSearchEntry(R.string.pref__theme__window_background_opacity__label, R.string.settings__look__title,
            Routes.Settings.Theme, listOf("transparent", "opacity", "see through")),
        SettingsSearchEntry(R.string.pref__keyboard__font_size_multiplier__label, R.string.settings__look__title,
            Routes.Settings.Theme, listOf("text size", "label", "font")),
        SettingsSearchEntry(R.string.pref__keyboard__popup_enabled__label, R.string.settings__look__title,
            Routes.Settings.Theme, listOf("preview", "bubble", "highlight")),
        SettingsSearchEntry(R.string.pref__theme__key_skin__label, R.string.settings__look__title, Routes.Settings.Theme,
            listOf("key skin", "texture", "keycap", "sci-fi", "plate")),
        SettingsSearchEntry(R.string.ext__home__title, R.string.settings__system__title, Routes.Ext.Home,
            listOf("import", "theme extension", "addon", "plugin", "skin")),
        // Sound & touch
        SettingsSearchEntry(R.string.pref__input_feedback__group_audio__label, R.string.settings__sound_touch__title,
            Routes.Settings.InputFeedback, listOf("sound", "click", "volume", "audio", "beep")),
        SettingsSearchEntry(R.string.pref__input_feedback__group_haptic__label, R.string.settings__sound_touch__title,
            Routes.Settings.InputFeedback, listOf("vibration", "haptic", "buzz")),
        SettingsSearchEntry(R.string.pref__keyboard__long_press_delay__label, R.string.settings__sound_touch__title,
            Routes.Settings.InputFeedback, listOf("hold", "delay", "long press")),
        // Layout
        SettingsSearchEntry(R.string.pref__keyboard__number_row__label, R.string.settings__layout__title,
            Routes.Settings.Keyboard, listOf("numbers", "digits", "rows")),
        SettingsSearchEntry(R.string.pref__keyboard__space_bar_mode__label, R.string.settings__layout__title,
            Routes.Settings.Keyboard, listOf("space")),
        SettingsSearchEntry(R.string.settings__keyboard_geometry__title, R.string.settings__layout__title,
            Routes.Settings.KeyboardGeometry,
            listOf("height", "size", "spacing", "gap", "width", "geometry", "mod row", "space row", "one-handed")),
        SettingsSearchEntry(R.string.pref__keyboard__key_customization__title, R.string.settings__layout__title,
            Routes.Settings.KeyCustomization, listOf("key size", "padding", "per key", "space bar width")),
        SettingsSearchEntry(R.string.settings__localization__title, R.string.settings__layout__title,
            Routes.Settings.Localization, listOf("language", "layout", "subtype", "qwerty", "locale")),
        SettingsSearchEntry(R.string.pref__keyboard__active_profile__label, R.string.settings__layout__title,
            Routes.Settings.Keyboard, listOf("text", "code", "coding", "profile")),
        // Command strip
        SettingsSearchEntry(R.string.settings__smartbar__title, R.string.settings__smartbar__title,
            Routes.Settings.Smartbar, listOf("smartbar", "menu", "voice", "emoji", "toolbar", "strip")),
        SettingsSearchEntry(R.string.pref__suggestion__display_mode__label, R.string.settings__smartbar__title,
            Routes.Settings.Smartbar, listOf("candidates", "suggestion row")),
        // Typing
        SettingsSearchEntry(R.string.pref__suggestion__enabled__label, R.string.settings__typing__title,
            Routes.Settings.Typing, listOf("suggestions", "prediction", "candidates")),
        SettingsSearchEntry(R.string.pref__correction__auto_capitalization__label, R.string.settings__typing__title,
            Routes.Settings.Typing, listOf("capital", "caps")),
        SettingsSearchEntry(R.string.pref__keyboard__capitalization_behavior__label, R.string.settings__typing__title,
            Routes.Settings.Typing, listOf("caps lock", "shift")),
        SettingsSearchEntry(R.string.pref__correction__double_space_period__label, R.string.settings__typing__title,
            Routes.Settings.Typing, listOf("period", "full stop")),
        SettingsSearchEntry(R.string.settings__dictionary__title, R.string.settings__typing__title,
            Routes.Settings.Dictionary, listOf("words", "user dictionary", "vocabulary")),
        SettingsSearchEntry(R.string.pref__suggestion__incognito_mode__label, R.string.settings__typing__title,
            Routes.Settings.Typing, listOf("private", "incognito")),
        // Gestures
        SettingsSearchEntry(R.string.pref__glide__enabled__label, R.string.settings__gestures__title,
            Routes.Settings.Gestures, listOf("swipe typing", "glide", "trail")),
        SettingsSearchEntry(R.string.pref__gestures__space_bar_title, R.string.settings__gestures__title,
            Routes.Settings.Gestures, listOf("cursor", "space swipe")),
        SettingsSearchEntry(R.string.pref__gestures__delete_key_swipe_left__label, R.string.settings__gestures__title,
            Routes.Settings.Gestures, listOf("backspace", "delete word")),
        // System
        SettingsSearchEntry(R.string.settings__clipboard__title, R.string.settings__system__title,
            Routes.Settings.Clipboard, listOf("copy", "paste", "history")),
        SettingsSearchEntry(R.string.settings__media__title, R.string.settings__system__title,
            Routes.Settings.Media, listOf("emoji", "skin tone", "emoticon")),
        SettingsSearchEntry(R.string.backup_and_restore__back_up__title, R.string.settings__system__title,
            Routes.Settings.Backup, listOf("export", "save")),
        SettingsSearchEntry(R.string.backup_and_restore__restore__title, R.string.settings__system__title,
            Routes.Settings.Restore, listOf("import")),
        SettingsSearchEntry(R.string.settings__nlp_debug__title, R.string.settings__system__title,
            Routes.Settings.NlpDebug, listOf("nlp", "debug", "logs", "symspell", "ngram")),
        SettingsSearchEntry(R.string.devtools__title, R.string.settings__system__title, Routes.Devtools.Home,
            listOf("developer", "debug")),
        SettingsSearchEntry(R.string.physical_keyboard__title, R.string.settings__system__title,
            Routes.Settings.PhysicalKeyboard, listOf("hardware", "bluetooth")),
        SettingsSearchEntry(R.string.about__title, R.string.settings__system__title, Routes.Settings.About,
            listOf("version", "licence", "license", "uninstall")),
    )

    /**
     * Entries whose label or keywords contain every word of [query], case-insensitively. [label]
     * resolves a string resource so this stays testable without Android resources.
     */
    fun search(query: String, label: (Int) -> String): List<SettingsSearchEntry> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries.filter { entry ->
            val haystack = (listOf(label(entry.labelRes)) + entry.keywords).joinToString(" ").lowercase()
            words.all { it in haystack }
        }
    }
}
