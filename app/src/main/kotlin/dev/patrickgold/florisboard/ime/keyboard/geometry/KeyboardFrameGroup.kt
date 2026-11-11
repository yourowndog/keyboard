/*
 * Copyright (C) 2025 FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode

/**
 * Stage 05: which rows decide the height of the window a mode is drawn into.
 *
 * A keyboard's *frame* is the window height the IME asks for. Its *rows* are what gets drawn inside
 * that frame. For most modes these are the same question asked twice — the rows are measured and the
 * frame is however tall they came out. For the text-entry surfaces they are deliberately different
 * questions, because a user who taps `?123` should get a different keyboard, not a different-sized
 * window under a thumb that is already moving.
 *
 * This declares that difference once, by mode, so no rendering path has to infer it. Before Stage 05
 * the same rule existed as a `when` inside `FlorisImeSizing.keyboardUiHeight()` that reached for
 * "whichever Characters keyboard was computed most recently" — which is not a property of the mode
 * being drawn but of where the user happened to have been, and was wrong whenever the user had not
 * been anywhere yet.
 */
enum class KeyboardFrameGroup {
    /**
     * Characters, Symbols, Symbols2 and Numeric-Advanced.
     *
     * One frame, sized from the group's reference surface — the Characters keyboard for the active
     * subtype and profile. Every member is reachable from every other member by a single key press,
     * so they share a height and the window stays still across the switch.
     *
     * The reference is a *declared* surface rather than a remembered one. It is what Characters
     * would be right now for this subtype and profile, whether or not the user has visited it.
     */
    TEXT_ENTRY,

    /**
     * Everything else, including Numeric, Phone and Phone2.
     *
     * The mode is measured and the frame is whatever its own rows need. These surfaces are entered
     * from the editor rather than from another keyboard layer, so there is no switch to hold still
     * and no reason to inherit a shape from a keyboard the user is not looking at.
     */
    OWN_ROWS,
}

/**
 * The frame group [this] mode belongs to.
 *
 * Exhaustive over [KeyboardMode] with no `else`, so a new mode has to be classified rather than
 * defaulting into a group by omission.
 */
@Suppress("DEPRECATION")
fun KeyboardMode.frameGroup(): KeyboardFrameGroup = when (this) {
    KeyboardMode.CHARACTERS,
    KeyboardMode.SYMBOLS,
    KeyboardMode.SYMBOLS2,
    KeyboardMode.NUMERIC_ADVANCED -> KeyboardFrameGroup.TEXT_ENTRY

    // Numeric and the phone family are opened for an editor that wants digits. They are never one
    // key press away from letters, so they size themselves.
    KeyboardMode.NUMERIC,
    KeyboardMode.PHONE,
    KeyboardMode.PHONE2,

    // The remaining modes are either deprecated, drawn from a custom XML layout with no arrangement
    // at all, or Smartbar surfaces that are not the input view. None of them shares the text-entry
    // frame; each is measured as whatever it is.
    KeyboardMode.UNSPECIFIED,
    KeyboardMode.EDITING,
    KeyboardMode.SMARTBAR_CLIPBOARD_CURSOR_ROW,
    KeyboardMode.SMARTBAR_NUMBER_ROW,
    KeyboardMode.SMARTBAR_QUICK_ACTIONS -> KeyboardFrameGroup.OWN_ROWS
}
