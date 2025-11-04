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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.observeAsTransformingState
import dev.patrickgold.jetpref.datastore.model.observeAsState

data class KeyboardDimens(
    val digitsHeight: Dp,
    val digitsPill: Float,
    val othersHeight: Dp,
    val othersPill: Float,
)

@Composable
fun rememberKeyboardDimens(): KeyboardDimens {
    val prefs by FlorisPreferenceStore
    val baseHeight = FlorisImeSizing.keyboardRowBaseHeight
    val geometryEnabled by prefs.keyboard.lcarsGeometryEnabled.observeAsState()
    val digitsHeightScale by prefs.keyboard.lcarsDigitsHeightScale.observeAsTransformingState {
        it.coerceIn(0.10f, 2.00f)
    }
    val digitsPillRatio by prefs.keyboard.lcarsDigitsPillRatio.observeAsTransformingState {
        it.coerceIn(1.00f, 3.00f)
    }
    val othersHeightScale by prefs.keyboard.lcarsOthersHeightScale.observeAsTransformingState {
        it.coerceIn(0.10f, 2.00f)
    }
    val othersPillRatio by prefs.keyboard.lcarsOthersPillRatio.observeAsTransformingState {
        it.coerceIn(1.00f, 3.00f)
    }

    val digitsHeight = if (geometryEnabled) baseHeight * digitsHeightScale else baseHeight
    val othersHeight = if (geometryEnabled) baseHeight * othersHeightScale else baseHeight
    val digitsPill = if (geometryEnabled) digitsPillRatio else 1.0f
    val othersPill = if (geometryEnabled) othersPillRatio else 1.0f

    return KeyboardDimens(
        digitsHeight = digitsHeight,
        digitsPill = digitsPill,
        othersHeight = othersHeight,
        othersPill = othersPill,
    )
}
