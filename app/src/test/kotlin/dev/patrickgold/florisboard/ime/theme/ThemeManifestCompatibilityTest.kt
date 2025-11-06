package dev.patrickgold.florisboard.ime.theme

import dev.patrickgold.florisboard.lib.ext.ExtensionJsonConfig
import dev.patrickgold.florisboard.ime.theme.ThemeExtension
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ThemeManifestCompatibilityTest {

    @Test
    fun `parses themes with legacy name field and missing authors`() {
        val json = """
            {
              "\$": "ime.extension.theme",
              "meta": {
                "id": "dev.test.theme",
                "version": "1.0.0",
                "title": "Test",
                "maintainers": ["Tester"],
                "license": "MIT"
              },
              "themes": [
                {
                  "id": "legacy-dark",
                  "name": "Legacy Dark",
                  "isNight": true,
                  "stylesheet": "stylesheets/legacy.json"
                },
                {
                  "id": "modern-day",
                  "label": "Modern Day",
                  "authors": [],
                  "isNight": false
                }
              ]
            }
        """.trimIndent()

        val extension = ExtensionJsonConfig.decodeFromString(ThemeExtension.serializer(), json)

        val legacy = extension.themes.first { it.id == "legacy-dark" }
        assertEquals("Legacy Dark", legacy.label)
        assertTrue(legacy.authors.isEmpty())

        val modern = extension.themes.first { it.id == "modern-day" }
        assertEquals("Modern Day", modern.label)
        assertTrue(modern.authors.isEmpty())
    }
}
