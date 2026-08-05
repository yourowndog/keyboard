/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.settings.localization

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.core.addSubtypeToList
import dev.patrickgold.florisboard.lib.FlorisLocale
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsLegibilityTest {
    private val repoRoot: File by lazy {
        val workingDirectory = System.getProperty("user.dir") ?: error("Missing user.dir")
        generateSequence(File(workingDirectory).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/kotlin").isDirectory }
    }

    @Test
    fun `fresh install presents the active implicit subtype`() {
        val entries = localizationSubtypeEntries(
            configuredSubtypes = emptyList(),
            activeSubtype = Subtype.DEFAULT,
        )

        assertEquals(1, entries.size)
        assertEquals(Subtype.DEFAULT, entries.single().subtype)
        assertTrue(entries.single().isImplicitDefault)
    }

    @Test
    fun `configured subtype lists preserve one several and many entries`() {
        for (count in listOf(1, 3, 50)) {
            val configured = List(count) { index ->
                Subtype.DEFAULT.copy(id = index.toLong() + 1L)
            }

            val entries = localizationSubtypeEntries(configured, configured.first())

            assertEquals(configured, entries.map { it.subtype })
            assertTrue(entries.none { it.isImplicitDefault })
        }
    }

    @Test
    fun `implicit default opens prefilled and saves through the add path`() {
        var lookupCalled = false

        val initial = subtypeEditorInitialSubtype(Subtype.DEFAULT.id) {
            lookupCalled = true
            null
        }

        assertEquals(Subtype.DEFAULT, initial)
        assertFalse(lookupCalled)
        assertTrue(subtypeEditorAddsSubtype(Subtype.DEFAULT.id))
        assertTrue(subtypeEditorAddsSubtype(null))
        assertFalse(subtypeEditorAddsSubtype(42L))
    }

    @Test
    fun `factory default recovery preserves the subtype being edited`() {
        assertEquals(Subtype.DEFAULT, subtypeEditorFactoryDefault(null))
        assertEquals(Subtype.DEFAULT, subtypeEditorFactoryDefault(Subtype.DEFAULT.id))

        val restored = subtypeEditorFactoryDefault(42L)
        assertEquals(42L, restored.id)
        assertTrue(restored.equalsExcludingId(Subtype.DEFAULT))
    }

    @Test
    fun `materializing implicit default appends a real id without disturbing others`() {
        val others = listOf(
            Subtype.DEFAULT.copy(
                id = 11L,
                secondaryLocales = listOf(FlorisLocale.from("de", "DE")),
            ),
            Subtype.DEFAULT.copy(
                id = 12L,
                secondaryLocales = listOf(FlorisLocale.from("fr", "FR")),
            ),
        )

        val materialized = assertNotNull(
            addSubtypeToList(
                subtypeList = others,
                subtype = Subtype.DEFAULT,
                generatedId = 99L,
            )
        )

        assertEquals(others, materialized.dropLast(1))
        assertEquals(99L, materialized.last().id)
        assertTrue(materialized.last().equalsExcludingId(Subtype.DEFAULT))
    }

    @Test
    fun `materialization keeps duplicate protection`() {
        val configured = listOf(Subtype.DEFAULT.copy(id = 11L))

        assertNull(
            addSubtypeToList(
                subtypeList = configured,
                subtype = Subtype.DEFAULT,
                generatedId = 99L,
            )
        )
    }

    @Test
    fun `localization controls retain their bindings in Devtools only`() {
        val localizationSource = repoRoot.resolve(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/localization/LocalizationScreen.kt"
        ).readText()
        val devtoolsSource = repoRoot.resolve(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/devtools/DevtoolsScreen.kt"
        ).readText()

        assertFalse(localizationSource.contains("ListPreference("))
        assertFalse(localizationSource.contains("SwitchPreference("))
        assertFalse(localizationSource.contains("Routes.Settings.LanguagePackManager("))
        assertTrue(devtoolsSource.contains("prefs.localization.displayLanguageNamesIn"))
        assertTrue(devtoolsSource.contains("prefs.localization.displayKeyboardLabelsInSubtypeLanguage"))
        assertTrue(devtoolsSource.contains("enumDisplayEntriesOf(DisplayLanguageNamesIn::class)"))
        assertTrue(devtoolsSource.contains("Routes.Settings.LanguagePackManager(LanguagePackManagerScreenAction.MANAGE)"))
    }

    @Test
    fun `layout builder is routed from Devtools only`() {
        val homeSource = repoRoot.resolve(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt"
        ).readText()
        val routesSource = repoRoot.resolve(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/Routes.kt"
        ).readText()
        val devtoolsSource = repoRoot.resolve(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/devtools/DevtoolsScreen.kt"
        ).readText()

        assertFalse(homeSource.contains("Routes.Settings.LayoutBuilder"))
        assertFalse(routesSource.contains("settings/layout-builder"))
        assertFalse(routesSource.contains("Settings.LayoutBuilder::class"))
        assertTrue(routesSource.contains("devtools/layout-builder"))
        assertTrue(routesSource.contains("Devtools.LayoutBuilder::class"))
        assertTrue(devtoolsSource.contains("Routes.Devtools.LayoutBuilder"))
    }
}
