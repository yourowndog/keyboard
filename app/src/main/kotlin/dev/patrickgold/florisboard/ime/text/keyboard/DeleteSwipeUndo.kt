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

import kotlin.math.abs

/**
 * Undo for swipe deletions: swipe up from Delete to put back what the last Delete or Shift swipe removed.
 * Only swipe deletions are remembered, because those are the ones that can take out a whole phrase by
 * accident; a single backspace is its own undo.
 */
internal object DeleteSwipeUndo {
    /** Swipe units (a quarter of the swipe distance threshold each) the finger must travel up. */
    const val ARM_UNITS_UP = 4

    /** Sideways drift tolerated while swiping up, in swipe units. */
    const val MAX_UNITS_SIDEWAYS = 1

    private var lastDeleted: String = ""
    private var lastWasForward: Boolean = false

    val hasText: Boolean get() = lastDeleted.isNotEmpty()

    /** The length the spacebar gauge shows while an undo is armed: what will come back. */
    val restoreLength: Int get() = lastDeleted.length

    /** Whether a Delete swipe at these unit counts is an undo rather than a backward selection. */
    fun isUndoGesture(absUnitCountX: Int, absUnitCountY: Int): Boolean =
        absUnitCountY <= -ARM_UNITS_UP && abs(absUnitCountX) <= MAX_UNITS_SIDEWAYS

    /** Records text a swipe is about to delete. [isForward] when it was after the cursor (Shift). */
    fun remember(text: String, isForward: Boolean) {
        if (text.isEmpty()) return
        lastDeleted = text
        lastWasForward = isForward
    }

    /**
     * Hands back the remembered deletion once and forgets it, so a second swipe up cannot paste it twice.
     * The flag says whether the cursor belongs before the restored text (it was deleted forward).
     */
    fun take(): Pair<String, Boolean>? {
        if (lastDeleted.isEmpty()) return null
        val result = lastDeleted to lastWasForward
        lastDeleted = ""
        return result
    }

    /** Forgets the remembered deletion, e.g. when the input field changes. */
    fun clear() {
        lastDeleted = ""
    }
}
