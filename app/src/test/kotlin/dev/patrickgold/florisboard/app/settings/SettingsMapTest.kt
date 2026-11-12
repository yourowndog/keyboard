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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the settings map: every preference control lives on exactly one screen, so moving a
 * setting between panels cannot leave a stale copy behind, and search finds the obvious words.
 */
class SettingsMapTest {
    private val settingsDir: File by lazy {
        val workingDirectory = System.getProperty("user.dir") ?: error("Missing user.dir")
        val root = generateSequence(File(workingDirectory).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/kotlin").isDirectory }
        File(root, "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings")
    }

    private val controlPattern = Regex(
        """(?:SwitchPreference|ListPreference|DialogSliderPreference|ColorPickerPreference|""" +
            """LocalTimePickerPreference)\(\s*(?:pref\s*=\s*|primaryPref\s*=\s*|listPref\s*=\s*)?""" +
            """(prefs\.[A-Za-z]+\.[A-Za-z]+)\b""",
    )

    @Test
    fun `every preference control appears on exactly one settings screen`() {
        val owners = mutableMapOf<String, MutableSet<String>>()
        settingsDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            controlPattern.findAll(file.readText()).forEach { match ->
                owners.getOrPut(match.groupValues[1]) { mutableSetOf() }.add(file.name)
            }
        }
        assertTrue(owners.size > 60, "expected the scan to find the settings controls, found ${owners.size}")
        val duplicated = owners.filterValues { it.size > 1 }
        assertEquals(emptyMap(), duplicated, "preferences shown on more than one screen")
    }

    @Test
    fun `search finds settings by their everyday words`() {
        val labels = mapOf<Int, String>()
        fun hits(query: String) = SettingsSearchIndex.search(query) { labels[it] ?: "" }.map { it.route }
        assertTrue(dev.patrickgold.florisboard.app.Routes.Settings.KeyboardGeometry in hits("height"))
        assertTrue(dev.patrickgold.florisboard.app.Routes.Settings.InputFeedback in hits("vibration"))
        assertTrue(dev.patrickgold.florisboard.app.Routes.Settings.Smartbar in hits("smartbar"))
        assertTrue(dev.patrickgold.florisboard.app.Routes.Settings.Theme in hits("wallpaper"))
        assertTrue(hits("   ").isEmpty())
    }
}
