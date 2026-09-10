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
import org.florisboard.lib.snygg.SnyggSinglePropertySetEditor
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.value.SnyggDefinedVarValue
import org.florisboard.lib.snygg.value.SnyggStaticColorValue

/** The opacity value that means "leave the theme exactly as the author wrote it". */
const val WINDOW_BACKGROUND_FULLY_OPAQUE = 100

/**
 * Returns this stylesheet with the `window` element's background alpha scaled by [opacityPercent].
 *
 * The keyboard sits on a plate painted by `window.background`, and every bundled theme paints it
 * with an opaque colour — `lcars_tactical` resolves it to `#000000`. Snygg has been able to express
 * a translucent colour all along (`#RRGGBBAA` and `rgba()` both carry alpha), but saying so meant
 * hand-editing a stylesheet. This scales the authored value instead, so the theme keeps its identity
 * and the plate stops being solid.
 *
 * Done here, at the stylesheet, rather than at any of the three places that read the window
 * background. `SnyggBox` paints it, `SnyggSurfaceView` paints it onto its own surface when there is
 * a background image, and `SystemUiIme` reads its luminance to pick navigation-icon colours. Scaling
 * the value once before the theme is compiled is the only way all three see the same colour.
 *
 * What this deliberately does not do:
 * - It does not touch any element other than `window`. Keys, popups and the Smartbar keep their own
 *   backgrounds, so a translucent window shows the app behind the *gaps*, not through the keys.
 * - It does not touch the user's saved theme. The transform runs on the way into the renderer; the
 *   theme editor still shows what the author wrote.
 * - It does not affect touch. `FlorisImeService.onComputeInsets` claims the keyboard region as
 *   touchable and obscuring regardless of what colour is painted there, and nothing here changes
 *   that. Visual transparency and touch pass-through are separate concerns and only the visual one
 *   is on offer.
 *
 * @param opacityPercent 0..100. [WINDOW_BACKGROUND_FULLY_OPAQUE] returns this stylesheet unchanged,
 *   including identity, so the common case allocates nothing. Values outside the range are clamped.
 */
fun SnyggStylesheet.withWindowBackgroundOpacity(opacityPercent: Int): SnyggStylesheet {
    val percent = opacityPercent.coerceIn(0, WINDOW_BACKGROUND_FULLY_OPAQUE)
    if (percent == WINDOW_BACKGROUND_FULLY_OPAQUE) return this
    val scale = percent / WINDOW_BACKGROUND_FULLY_OPAQUE.toFloat()

    val editor = this.edit()
    // `window.background` is normally a `var(--…)` reference, and variables are only resolved later,
    // when the stylesheet is compiled into a theme. Resolving one here is a read of the same
    // `@defines` block that compilation would use — a single hop, not a chain, which is exactly what
    // `SnyggTheme.compileFrom` does.
    val defines = editor.rules[SnyggAnnotationRule.Defines] as? SnyggSinglePropertySetEditor

    var changedAny = false
    for ((rule, propertySet) in editor.rules) {
        if (rule !is SnyggElementRule || rule.elementName != FlorisImeUi.Window.elementName) continue
        if (propertySet !is SnyggSinglePropertySetEditor) continue
        val declared = propertySet.properties[Snygg.Background] ?: continue
        val resolved = when (declared) {
            is SnyggDefinedVarValue -> defines?.properties?.get(declared.key)
            else -> declared
        }
        val color = (resolved as? SnyggStaticColorValue)?.color ?: continue
        // Multiply rather than assign, so a theme that already asked for translucency keeps its
        // intent and the preference reads as "this much of whatever the theme wanted".
        propertySet.properties[Snygg.Background] =
            SnyggStaticColorValue(color.copy(alpha = color.alpha * scale))
        changedAny = true
    }

    // A theme whose window background is a dynamic colour, an image-only rule, or simply absent has
    // nothing to scale. Rebuilding in that case would hand the renderer an equal-but-not-identical
    // stylesheet and invalidate every `remember` keyed on it, for no visual difference.
    return if (changedAny) editor.build() else this
}
