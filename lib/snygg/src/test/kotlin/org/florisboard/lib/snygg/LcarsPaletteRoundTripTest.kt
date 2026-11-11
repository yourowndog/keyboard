package org.florisboard.lib.snygg

import androidx.compose.ui.graphics.Color
import org.florisboard.lib.color.schemes.yellowDarkScheme
import org.florisboard.lib.color.schemes.yellowLightScheme
import org.florisboard.lib.snygg.value.SnyggStaticColorValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LcarsPaletteRoundTripTest {
    private val roles = listOf("keyboard-chassis", "keyboard-chassis-rail-primary", "keyboard-chassis-rail-secondary")

    @Test
    fun `native element properties include background for all chassis roles`() {
        for (role in roles) {
            assertTrue(Snygg.Background in SnyggSpec.propertiesOf(SnyggElementRule(role)))
        }
    }

    @Test
    fun `editing saving and recompiling preserve independent chassis color alpha`() {
        val original = SnyggStylesheet.v2 {
            for (role in roles) {
                role { background = rgbaColor(20, 40, 60, 1f) }
            }
        }
        val editor = original.edit()
        val alphas = listOf(0.25f, 0.5f, 0.75f)
        for ((index, role) in roles.withIndex()) {
            val properties = assertIs<SnyggSinglePropertySetEditor>(editor.rules[SnyggElementRule(role)])
            properties.background = SnyggStaticColorValue(Color(20f / 255f, 40f / 255f, 60f / 255f, alphas[index]))
        }
        val saved = editor.build().toJson().getOrThrow()
        val reloaded = SnyggStylesheet.fromJson(saved).getOrThrow()
        val theme = SnyggTheme.compileFrom(reloaded)
        for ((index, role) in roles.withIndex()) {
            val style = theme.query(
                role, emptyMap(), SnyggSelector.NONE, SnyggSinglePropertySet(),
                yellowLightScheme, yellowDarkScheme, 1f,
            )
            val color = assertIs<SnyggStaticColorValue>(style.background).color
            assertEquals(alphas[index], color.alpha, 0.005f)
            assertEquals(20f / 255f, color.red, 0.005f)
        }
    }
}
