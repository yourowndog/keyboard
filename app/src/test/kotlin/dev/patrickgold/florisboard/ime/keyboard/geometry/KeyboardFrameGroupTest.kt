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
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stage 05: the frame group declaration, and what it means once the solver acts on it.
 *
 * The rule these tests describe used to live as an unnamed `when` inside
 * `FlorisImeSizing.keyboardUiHeight()` that read "whichever Characters keyboard was computed most
 * recently". Its intent was right — a layer switch should not resize the window under a thumb that
 * is already moving — but it expressed that intent as navigation history rather than as a property
 * of the mode, so it was wrong whenever history was empty or stale.
 *
 * `KeyboardFrameGroup` states the same intent as a declaration. `KeyboardManager` resolves the
 * text-entry group's reference surface, because it is the only component that can compute the
 * Characters keyboard for the current subtype and profile on demand. These tests cover the
 * declaration and the geometry that follows from it; the manager wiring needs an instrumented
 * device and is listed in the stage's device checkpoint.
 */
class KeyboardFrameGroupTest {

    private val width = 1080.0
    private val base = 65.0

    private fun prefs(
        utilityGapAbovePx: Double = 0.0,
        utilityGapWithinPx: Double = 0.0,
        utilityGapBelowPx: Double = 0.0,
    ) = GeometryPreferences(
        rowBaseHeightPx = base,
        utilityGapAbovePx = utilityGapAbovePx,
        utilityGapWithinPx = utilityGapWithinPx,
        utilityGapBelowPx = utilityGapBelowPx,
    )

    private fun frameHeight(keyboard: TextKeyboard, prefs: GeometryPreferences = prefs()) =
        TextKeyboardGeometryBridge.frameHeight(
            keyboard = keyboard,
            prefs = prefs,
            availableWidth = width,
        )

    private fun solveInto(keyboard: TextKeyboard, frame: Int, prefs: GeometryPreferences = prefs()) =
        TextKeyboardGeometryBridge.solve(
            keyboard = keyboard,
            prefs = prefs,
            availableWidth = width,
            framePolicy = FramePolicy.FitToHeight(frame.toDouble()),
        )

    // -- The declaration ---------------------------------------------------------------------

    /**
     * The four surfaces a single key press can reach from one another share a frame.
     *
     * Characters, Symbols, Symbols2 and Numeric-Advanced are the modes behind `?123`, `=\<` and the
     * shift key on the symbol layer. A user crossing between them is mid-gesture.
     */
    @COMPATIBILITY("the text-entry surfaces share one frame across a layer switch")
    @Test
    fun `the text entry surfaces are one frame group`() {
        val textEntry = listOf(
            KeyboardMode.CHARACTERS,
            KeyboardMode.SYMBOLS,
            KeyboardMode.SYMBOLS2,
            KeyboardMode.NUMERIC_ADVANCED,
        )
        for (mode in textEntry) {
            assertEquals(
                KeyboardFrameGroup.TEXT_ENTRY,
                mode.frameGroup(),
                "$mode is reachable by one key press from the letters and must share their frame",
            )
        }
    }

    /**
     * Numeric and the phone family size themselves.
     *
     * They are opened because the editor asked for digits, not because the user pressed a key on a
     * keyboard they were already looking at, so there is no switch to hold still.
     */
    @COMPATIBILITY("editor-entered digit surfaces size their own frame")
    @Test
    fun `numeric and phone size their own frame`() {
        val ownRows = listOf(KeyboardMode.NUMERIC, KeyboardMode.PHONE, KeyboardMode.PHONE2)
        for (mode in ownRows) {
            assertEquals(
                KeyboardFrameGroup.OWN_ROWS,
                mode.frameGroup(),
                "$mode is entered from the editor and has no layer switch to keep still",
            )
        }
    }

    /**
     * Every mode is classified, and the classification is a function of the mode alone.
     *
     * The old rule could return different answers for the same mode depending on where the user had
     * been. This asserts the property that replaced it.
     */
    @Test
    fun `every mode has exactly one stable frame group`() {
        for (mode in KeyboardMode.entries) {
            val group = mode.frameGroup()
            assertNotNull(group, "$mode was not classified")
            assertEquals(group, mode.frameGroup(), "$mode classified differently on a second call")
        }
    }

    // -- What the declaration costs the solver ------------------------------------------------

    /**
     * A Symbols surface fits inside the frame a Coding Characters surface establishes.
     *
     * Coding is six rows plus boundary gaps; Symbols is four. Sharing the frame therefore makes the
     * symbol rows taller than their canonical height rather than leaving a gap, which is the trade
     * the group is buying: a still window in exchange for rows that are not their natural size.
     */
    @COMPATIBILITY("Symbols fills the Characters frame instead of leaving the window half empty")
    @Test
    fun `symbols fills the frame that coding characters establishes`() {
        val gaps = prefs(utilityGapAbovePx = 16.0, utilityGapWithinPx = 8.0, utilityGapBelowPx = 16.0)
        val frame = assertNotNull(frameHeight(GeometryFixtures.defaultCoding(), gaps))

        val result = solveInto(GeometryFixtures.wideSymbols(), frame, gaps)
        assertTrue(
            result is TextKeyboardGeometryBridge.Result.Solved,
            "Symbols could not fit the Coding frame: ${result::class.simpleName}",
        )
        val geometry = (result as TextKeyboardGeometryBridge.Result.Solved).geometry
        assertEquals(frame, geometry.frame.height, "the symbol rows did not fill the frame they were given")
    }

    /**
     * Numeric-Advanced fits the same frame, and its rows are not the ones that decided it.
     *
     * This is the case the old code got wrong most often. Numeric-Advanced is reachable from the
     * editor as well as from the symbol layer, so on a cold start into a decimal field the
     * "last Characters keyboard" was still the four-row loading placeholder.
     */
    @COMPATIBILITY("Numeric-Advanced shares the text-entry frame rather than a placeholder's")
    @Test
    fun `numeric advanced fits the text entry frame`() {
        val frame = assertNotNull(frameHeight(GeometryFixtures.defaultCoding()))
        val own = assertNotNull(frameHeight(GeometryFixtures.numericAdvanced()))
        assertTrue(frame != own, "the fixture cannot show frame sharing if both shapes agree")

        val result = solveInto(GeometryFixtures.numericAdvanced(), frame)
        assertTrue(
            result is TextKeyboardGeometryBridge.Result.Solved,
            "Numeric-Advanced could not fit the text-entry frame: ${result::class.simpleName}",
        )
        assertEquals(
            frame,
            (result as TextKeyboardGeometryBridge.Result.Solved).geometry.frame.height,
            "Numeric-Advanced did not fill the frame it shares",
        )
    }

    /**
     * The frame the group shares tracks the *current* Characters shape, including its extension rows.
     *
     * Toggling the number row changes the reference, so every member of the group changes with it.
     * Under the old rule this only took effect once the user next visited Characters, because the
     * remembered evaluator was not updated by a preference change made from the symbol layer.
     */
    @COMPATIBILITY("the shared frame follows the current Characters shape, not a remembered one")
    @Test
    fun `toggling an extension row moves the whole group's frame`() {
        val plain = assertNotNull(frameHeight(GeometryFixtures.defaultCoding()))
        val withNumberRow = assertNotNull(frameHeight(GeometryFixtures.codingWithNumberExtension()))
        assertTrue(
            withNumberRow > plain,
            "adding the number row should make the reference taller ($withNumberRow vs $plain)",
        )

        for (member in listOf(GeometryFixtures.wideSymbols(), GeometryFixtures.numericAdvanced())) {
            val result = solveInto(member, withNumberRow)
            assertTrue(
                result is TextKeyboardGeometryBridge.Result.Solved,
                "${member.mode} could not follow the reference to $withNumberRow",
            )
            assertEquals(
                withNumberRow,
                (result as TextKeyboardGeometryBridge.Result.Solved).geometry.frame.height,
                "${member.mode} did not follow the reference",
            )
        }
    }

    /**
     * An `OWN_ROWS` surface measured intrinsically and then re-solved at that height is unchanged.
     *
     * The two solves are the same question for these modes, which is what makes it safe for
     * `FlorisImeSizing` to measure one keyboard and `TextKeyboardLayout` to place another.
     */
    @COMPATIBILITY("an own-rows mode's frame solve and row solve agree")
    @Test
    fun `an own rows surface reproduces its own frame`() {
        for (keyboard in listOf(GeometryFixtures.numeric(), GeometryFixtures.phone(), GeometryFixtures.phone2())) {
            val frame = assertNotNull(frameHeight(keyboard), "${keyboard.mode} had no intrinsic height")
            val result = solveInto(keyboard, frame)
            assertTrue(
                result is TextKeyboardGeometryBridge.Result.Solved,
                "${keyboard.mode} could not reproduce its own frame",
            )
            assertEquals(
                frame,
                (result as TextKeyboardGeometryBridge.Result.Solved).geometry.frame.height,
                "${keyboard.mode} disagreed with its own intrinsic frame",
            )
        }
    }
}
