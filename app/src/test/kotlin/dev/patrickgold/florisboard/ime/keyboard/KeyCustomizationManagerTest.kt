package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyCustomizationManagerTest {
    @Test
    fun `source customization defaults are a neutral override`() {
        assertEquals(
            KeyCustomization(
                paddingTop = 0f,
                paddingBottom = 0f,
                paddingLeft = 0f,
                paddingRight = 0f,
                heightFactor = 1f,
                widthFactor = 1f,
            ),
            KeyCustomization(),
        )
    }

    @Test
    fun `neutral reset preserves exact legacy payload for restoration`() {
        val legacyJson =
            """{"10":{"paddingTop":20.0},"32":{"paddingTop":20.0,"heightFactor":1.1,"widthFactor":0.8}}"""

        val reset = KeyCustomizationManager.neutralReset(legacyJson)

        assertEquals(KeyCustomizationManager.NEUTRAL_JSON, reset.activeJson)
        assertEquals(legacyJson, reset.backupJson)
        assertEquals(emptyMap(), KeyCustomizationManager.parseFromJson(reset.activeJson))
        assertEquals(
            KeyCustomization(paddingTop = 20f),
            KeyCustomizationManager.getForKey(reset.backupJson, 10),
        )
    }

    @Test
    fun `malformed persisted legacy payload is neutral in memory without rewriting source data`() {
        val malformedJson = """{"32":{"heightFactor":"""

        val parsed = KeyCustomizationManager.parseFromJson(malformedJson)
        val reset = KeyCustomizationManager.neutralReset(malformedJson)

        assertTrue(parsed.isEmpty())
        assertEquals(malformedJson, reset.backupJson)
        assertEquals(KeyCustomizationManager.NEUTRAL_JSON, reset.activeJson)
    }

    @Test
    fun `legacy code persistence can represent Primary Action punctuation and action keys`() {
        val codes = listOf(
            ','.code,
            '.'.code,
            KeyCode.SPACE,
            KeyCode.TAB,
            KeyCode.ENTER,
        )
        val customization = KeyCustomization(paddingTop = 3f)
        val json = codes.fold(KeyCustomizationManager.NEUTRAL_JSON) { currentJson, code ->
            KeyCustomizationManager.setForKey(currentJson, code, customization)
        }

        codes.forEach { code ->
            assertEquals(customization, KeyCustomizationManager.getForKey(json, code))
        }
    }
}
