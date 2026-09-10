/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.theme

import org.florisboard.lib.snygg.Snygg
import org.florisboard.lib.snygg.SnyggAnnotationRule
import org.florisboard.lib.snygg.SnyggElementRule
import org.florisboard.lib.snygg.SnyggSinglePropertySet
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.value.SnyggDefinedVarValue
import org.florisboard.lib.snygg.value.SnyggStaticColorValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Compose packs an sRGB colour with eight bits of alpha, so a scaled alpha lands on the nearest
 * 1/255 rather than the exact float. Compare against that granularity, not against zero.
 */
private const val ALPHA_TOLERANCE = 1f / 255f

class WindowBackgroundOpacityTest {
    private fun SnyggStylesheet.windowBackground() =
        (rules[SnyggElementRule(FlorisImeUi.Window.elementName)] as? SnyggSinglePropertySet)
            ?.properties?.get(Snygg.Background)

    private fun SnyggStylesheet.background(elementName: String) =
        (rules[SnyggElementRule(elementName)] as? SnyggSinglePropertySet)
            ?.properties?.get(Snygg.Background)

    private fun SnyggStylesheet.define(key: String) =
        (rules[SnyggAnnotationRule.Defines] as? SnyggSinglePropertySet)?.properties?.get(key)

    /** A stand-in for the bundled themes: an opaque window background behind a `var()`. */
    private fun opaqueVarTheme() = SnyggStylesheet.v2 {
        defines {
            "--void-prime" to rgbaColor(0, 0, 0)
        }
        FlorisImeUi.Window.elementName {
            "background" to `var`("--void-prime")
        }
    }

    @Test
    fun `scales a window background declared as a var() through the defines block`() {
        val scaled = opaqueVarTheme().withWindowBackgroundOpacity(50)

        val background = assertIs<SnyggStaticColorValue>(scaled.windowBackground())
        assertEquals(0.5f, background.color.alpha, ALPHA_TOLERANCE, "window alpha")
        assertEquals(0f, background.color.red, ALPHA_TOLERANCE, "window red is untouched")
    }

    @Test
    fun `leaves the defines block itself alone`() {
        val scaled = opaqueVarTheme().withWindowBackgroundOpacity(50)

        // The variable may be shared with keys, popups or the smartbar. Scaling it there would fade
        // the whole keyboard rather than the plate behind it.
        val define = assertIs<SnyggStaticColorValue>(scaled.define("--void-prime"))
        assertEquals(1f, define.color.alpha, ALPHA_TOLERANCE, "--void-prime alpha")
    }

    @Test
    fun `scales a window background declared inline`() {
        val stylesheet = SnyggStylesheet.v2 {
            FlorisImeUi.Window.elementName {
                "background" to rgbaColor(255, 128, 0)
            }
        }

        val background = assertIs<SnyggStaticColorValue>(
            stylesheet.withWindowBackgroundOpacity(25).windowBackground()
        )
        assertEquals(0.25f, background.color.alpha, ALPHA_TOLERANCE, "window alpha")
    }

    @Test
    fun `multiplies an already translucent theme colour instead of overwriting it`() {
        val stylesheet = SnyggStylesheet.v2 {
            FlorisImeUi.Window.elementName {
                "background" to rgbaColor(0, 0, 0, 0.5f)
            }
        }

        // The preference reads as "this much of whatever the theme asked for", so a theme that
        // already wanted half opacity ends up at a quarter, not back up at a half.
        val background = assertIs<SnyggStaticColorValue>(
            stylesheet.withWindowBackgroundOpacity(50).windowBackground()
        )
        assertEquals(0.25f, background.color.alpha, ALPHA_TOLERANCE, "window alpha")
    }

    @Test
    fun `touches no element other than window`() {
        val stylesheet = SnyggStylesheet.v2 {
            defines {
                "--void-prime" to rgbaColor(0, 0, 0)
            }
            FlorisImeUi.Window.elementName {
                "background" to `var`("--void-prime")
            }
            FlorisImeUi.Key.elementName {
                "background" to `var`("--void-prime")
            }
            FlorisImeUi.Smartbar.elementName {
                "background" to rgbaColor(0, 0, 0)
            }
        }

        val scaled = stylesheet.withWindowBackgroundOpacity(0)

        val key = assertIs<SnyggDefinedVarValue>(scaled.background(FlorisImeUi.Key.elementName))
        assertEquals("--void-prime", key.key, "key background is still the unresolved var")
        val smartbar = assertIs<SnyggStaticColorValue>(
            scaled.background(FlorisImeUi.Smartbar.elementName)
        )
        assertEquals(1f, smartbar.color.alpha, ALPHA_TOLERANCE, "smartbar alpha")
    }

    @Test
    fun `returns the same instance at full opacity`() {
        val stylesheet = opaqueVarTheme()

        // Identity matters beyond avoiding work: the stylesheet is a `remember` key, so returning an
        // equal-but-distinct instance would invalidate the compiled theme on every recomposition.
        assertSame(stylesheet, stylesheet.withWindowBackgroundOpacity(100), "default is a no-op")
    }

    @Test
    fun `clamps out of range values`() {
        val stylesheet = opaqueVarTheme()

        assertSame(stylesheet, stylesheet.withWindowBackgroundOpacity(150), "above the maximum")
        val floored = assertIs<SnyggStaticColorValue>(
            stylesheet.withWindowBackgroundOpacity(-20).windowBackground()
        )
        assertEquals(0f, floored.color.alpha, ALPHA_TOLERANCE, "below the minimum")
    }

    @Test
    fun `returns the same instance when there is nothing to scale`() {
        // A window rule whose background is a var() with no matching define, and a theme with no
        // window rule at all. Neither has a resolvable colour, and rebuilding would only churn.
        val danglingVar = SnyggStylesheet.v2 {
            FlorisImeUi.Window.elementName {
                "background" to `var`("--not-defined-anywhere")
            }
        }
        assertSame(danglingVar, danglingVar.withWindowBackgroundOpacity(50), "dangling var")

        val noWindowRule = SnyggStylesheet.v2 {
            FlorisImeUi.Key.elementName {
                "background" to rgbaColor(0, 0, 0)
            }
        }
        assertSame(noWindowRule, noWindowRule.withWindowBackgroundOpacity(50), "no window rule")
    }

    @Test
    fun `scales every window rule variant`() {
        // The transform matches on element name alone, so a `window` rule narrowed by attributes or
        // a selector is covered too. No bundled theme writes one today; this pins the behaviour so a
        // theme that starts doing so does not end up with one scaled plate and one opaque one.
        val stylesheet = SnyggStylesheet.v2 {
            FlorisImeUi.Window.elementName {
                "background" to rgbaColor(0, 0, 0)
            }
            FlorisImeUi.Window.elementName("mode" to listOf("incognito")) {
                "background" to rgbaColor(32, 0, 0)
            }
        }

        val scaled = stylesheet.withWindowBackgroundOpacity(40)
        val windowRules = scaled.rules.entries.filter { (rule, _) ->
            rule is SnyggElementRule && rule.elementName == FlorisImeUi.Window.elementName
        }
        assertEquals(2, windowRules.size, "window rule count")
        for ((rule, propertySet) in windowRules) {
            val background = assertIs<SnyggSinglePropertySet>(propertySet).properties[Snygg.Background]
            assertNotNull(background, "background of $rule")
            val alpha = assertIs<SnyggStaticColorValue>(background).color.alpha
            assertEquals(0.4f, alpha, ALPHA_TOLERANCE, "alpha of $rule")
        }
    }
}
