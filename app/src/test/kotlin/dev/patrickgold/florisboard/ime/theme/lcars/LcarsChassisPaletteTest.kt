package dev.patrickgold.florisboard.ime.theme.lcars

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LcarsChassisPaletteTest {
    @Test
    fun `missing semantic colors require the classic fallback`() {
        assertFalse(LcarsChassisThemeColors(Color.Unspecified, Color.Blue, Color.Black).isSpecified)
        assertFalse(LcarsChassisThemeColors(Color.Red, Color.Unspecified, Color.Black).isSpecified)
        assertFalse(LcarsChassisThemeColors(Color.Red, Color.Blue, Color.Unspecified).isSpecified)
    }

    @Test
    fun `explicit transparent colors remain a valid operator palette`() {
        assertTrue(LcarsChassisThemeColors(Color.Transparent, Color.Transparent, Color.Transparent).isSpecified)
    }
}
