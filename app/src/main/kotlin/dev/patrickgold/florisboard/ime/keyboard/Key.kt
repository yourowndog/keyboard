/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.patrickgold.florisboard.lib.FlorisRect

/**
 * Enum defining vertical alignment options for keys with custom heights.
 */
enum class VerticalAlignment {
    /** Key extends upward from the row baseline */
    TOP,
    /** Key is centered vertically within its extended bounds */
    CENTER,
    /** Key extends downward from the row baseline (default) */
    BOTTOM
}

/**
 * Abstract class describing the smallest computed unit in a computed keyboard. Each key represents exactly one key
 * displayed in the UI. It allows to save the absolute location within the parent keyboard, save touch and visual
 * bounds, managing the state (enabled, pressed, visibility) as well as layout sizing factors. Each key in this IME
 * inherits from this base key class. This allows for a inter-operable usage of a key without knowing the exact
 * subclass upfront.
 *
 * @property data The base key data this key represents.This can be anything - from a basic text key to an emoji key
 *  to a complex selector.
 */
abstract class Key(open val data: AbstractKeyData) {
    /**
     * Specifies whether this key is enabled or not.
     */
    open var isEnabled: Boolean by mutableStateOf(true)

    /**
     * Specifies whether this key is actively pressed or not. Is used by the parent keyboard view to draw the key
     * differently to indicate this state.
     */
    open var isPressed: Boolean by mutableStateOf(false)

    /**
     * Specifies whether this key is visible or not. Is used by the parent keyboard view to omit this key in the
     * layout and drawing process. A `false`-value is equivalent to `VISIBILITY_GONE` on Android's View class.
     */
    open var isVisible: Boolean by mutableStateOf(true)

    /**
     * The touch bounds of this key. All bounds defined here are absolute coordinates within the parent keyboard.
     */
    open val touchBounds: FlorisRect = FlorisRect.empty()

    /**
     * The visible bounds of this key. All bounds defined here are absolute coordinates within the parent keyboard.
     */
    open val visibleBounds: FlorisRect = FlorisRect.empty()

    /**
     * The structural width this key was *authored* with, in units, or null if it has none.
     *
     * Only a user Layout Pack sets this: a pack author who wrote an asymmetric row meant it, and
     * that asymmetry survives untouched. Every other key leaves this null and is measured by
     * [dev.patrickgold.florisboard.ime.keyboard.geometry.KeyboardGeometryPolicy], which is the sole
     * authority on what an ordinary key is worth.
     *
     * There is deliberately no general-purpose per-key width, grow, shrink, height or padding field
     * here any more. Those were the intrinsic table that let key geometry be decided in five places
     * at once; geometry is now stated once, by role, and derived.
     */
    open var authoredWidthUnits: Float? = null

    /**
     * True when this key is a Layout Pack spacer: it occupies structural width but is not a key.
     *
     * Distinct from `!isVisible`, which means "this key exists but the evaluator hid it" and
     * collapses the cell to zero width.
     */
    open var isStructuralSpacer: Boolean = false

    /**
     * The computed UI label of this key. This value is used by the keyboard view to temporarily save the label string
     * for UI rendering and should not be set manually.
     */
    open var label: String? = null

    /**
     * The computed UI hint label of this key. This value is used by the keyboard view to temporarily save the hint
     * label string for UI rendering and should not be set manually.
     */
    open var hintedLabel: String? = null

    /**
     * The computed ImageVector of this key. This value is used by the keyboard view to temporarily save the
     * ImageVector for UI rendering and should not be set manually.
     */
    open var foregroundImageVector: ImageVector? = null
}
