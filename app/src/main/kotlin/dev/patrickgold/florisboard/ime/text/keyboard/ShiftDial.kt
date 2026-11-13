/*
 * Copyright (C) 2026 The OmniBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.text.gestures.SwipeGesture

/**
 * The held Shift dial on a board without a Ctrl key: holding Shift latches Ctrl (see [CtrlReveal]), and
 * sliding the still-held finger picks a different modifier before letting go.
 */
internal object ShiftDial {
    enum class Choice { CTRL, ALT, ESC }

    /** Right latches Alt, left sends Escape, up keeps Ctrl. Diagonals and down choose nothing yet. */
    fun choiceFor(direction: SwipeGesture.Direction): Choice? = when (direction) {
        SwipeGesture.Direction.RIGHT -> Choice.ALT
        SwipeGesture.Direction.LEFT -> Choice.ESC
        SwipeGesture.Direction.UP -> Choice.CTRL
        else -> null
    }
}
