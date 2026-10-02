/*
 * Copyright (C) 2026 The OmniBoard Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextProfileTest {

    @Test
    fun `text profile correctly maps key codes to semantic roles`() {
        assertEquals(TextKeyRole.EDITING, TextKeyRole.fromKeyCode(KeyCode.SPACE))
        assertEquals(TextKeyRole.EDITING, TextKeyRole.fromKeyCode(KeyCode.ENTER))
        assertEquals(TextKeyRole.EDITING, TextKeyRole.fromKeyCode(KeyCode.DELETE))
        assertEquals(TextKeyRole.FUNCTION, TextKeyRole.fromKeyCode(KeyCode.SHIFT))
        assertEquals(TextKeyRole.FUNCTION, TextKeyRole.fromKeyCode(KeyCode.VIEW_SYMBOLS))
        assertEquals(TextKeyRole.UTILITY, TextKeyRole.fromKeyCode(KeyCode.VOICE_INPUT))
        assertEquals(TextKeyRole.UTILITY, TextKeyRole.fromKeyCode(KeyCode.SETTINGS))
        assertEquals(TextKeyRole.NUMBER, TextKeyRole.fromKeyCode('5'.code))
        assertEquals(TextKeyRole.ALPHA, TextKeyRole.fromKeyCode('a'.code))
    }

    @Test
    fun `text profile row classification distinguishes alpha, number, and action rows`() {
        assertEquals(SemanticRowRole.EXTENSION, TextProfile.classifyRowRole(0, 5, hasNumberRow = true))
        assertEquals(SemanticRowRole.ALPHA, TextProfile.classifyRowRole(1, 5, hasNumberRow = true))
        assertEquals(SemanticRowRole.ALPHA, TextProfile.classifyRowRole(2, 5, hasNumberRow = true))
        assertEquals(SemanticRowRole.ALPHA, TextProfile.classifyRowRole(3, 5, hasNumberRow = true))
        assertEquals(SemanticRowRole.PRIMARY_ACTION, TextProfile.classifyRowRole(4, 5, hasNumberRow = true))

        // Without number row
        assertEquals(SemanticRowRole.ALPHA, TextProfile.classifyRowRole(0, 4, hasNumberRow = false))
        assertEquals(SemanticRowRole.PRIMARY_ACTION, TextProfile.classifyRowRole(3, 4, hasNumberRow = false))
    }

    @Test
    fun `responsive breakpoints classify phone portrait, phone landscape, and tablet correctly`() {
        assertEquals(ResponsiveBreakpoint.COMPACT, ResponsiveBreakpoint.forWidth(360.0))
        assertEquals(ResponsiveBreakpoint.COMPACT, ResponsiveBreakpoint.forWidth(412.0))
        assertEquals(ResponsiveBreakpoint.MEDIUM, ResponsiveBreakpoint.forWidth(600.0))
        assertEquals(ResponsiveBreakpoint.MEDIUM, ResponsiveBreakpoint.forWidth(720.0))
        assertEquals(ResponsiveBreakpoint.EXPANDED, ResponsiveBreakpoint.forWidth(840.0))
        assertEquals(ResponsiveBreakpoint.EXPANDED, ResponsiveBreakpoint.forWidth(1024.0))
    }

    @Test
    fun `alpha reference unit calculation behaves consistently across phone portrait, phone landscape, and tablet widths`() {
        val widths = listOf(360.0, 412.0, 720.0, 1024.0)
        for (width in widths) {
            val input = TextProfile.createStandardQwertyInput(availableWidth = width)
            val solution = KeyboardGeometrySolver.solve(input) as GeometrySolution.Solved

            // Check row 0 (QWERTYUIOP: 10 keys)
            val row1 = solution.geometry.rows[0]
            assertEquals(10, row1.items.size)
            val keyQWidth = row1.items[0].bounds.width
            val keyPWidth = row1.items[9].bounds.width
            assertEquals(keyQWidth, keyPWidth)
            assertTrue(keyQWidth > 0)

            // Check row 1 (ASDFGHJKL: 9 keys) centered and having matching unit width with row 0
            val row2 = solution.geometry.rows[1]
            val keyAWidth = row2.items[0].bounds.width
            assertEquals(keyQWidth, keyAWidth)
        }
    }

    @Test
    fun `spacebar absorbs remaining width without distorting surrounding utility keys`() {
        val compactInput = TextProfile.createStandardQwertyInput(availableWidth = 360.0)
        val expandedInput = TextProfile.createStandardQwertyInput(availableWidth = 720.0)

        val compactSolution = KeyboardGeometrySolver.solve(compactInput) as GeometrySolution.Solved
        val expandedSolution = KeyboardGeometrySolver.solve(expandedInput) as GeometrySolution.Solved

        val compactPrimaryRow = compactSolution.geometry.rows.last()
        val expandedPrimaryRow = expandedSolution.geometry.rows.last()

        val compactSymWidth = compactPrimaryRow.items.first { it.stableId == "key_sym" }.bounds.width
        val expandedSymWidth = expandedPrimaryRow.items.first { it.stableId == "key_sym" }.bounds.width

        val compactSpaceWidth = compactPrimaryRow.items.first { it.stableId == "key_space" }.bounds.width
        val expandedSpaceWidth = expandedPrimaryRow.items.first { it.stableId == "key_space" }.bounds.width

        val compactAlphaUnit = compactSolution.geometry.rows[0].items[0].bounds.width
        val expandedAlphaUnit = expandedSolution.geometry.rows[0].items[0].bounds.width

        // Since sym key has widthUnits = 1.5 and growWeight = 0.0, its width strictly scales with alpha unit (within integer rounding tolerance)
        assertEquals((compactAlphaUnit * 1.5).toInt(), compactSymWidth)
        assertEquals((expandedAlphaUnit * 1.5).roundToInt(), expandedSymWidth)

        // Spacebar grows to absorb remaining width, so expanded space is significantly wider
        assertTrue(expandedSpaceWidth > compactSpaceWidth)
    }

    @Test
    fun `shift and delete balance is maintained under responsive scaling`() {
        val widths = listOf(360.0, 600.0, 900.0)
        for (width in widths) {
            val input = TextProfile.createStandardQwertyInput(availableWidth = width)
            val solution = KeyboardGeometrySolver.solve(input) as GeometrySolution.Solved

            val row3 = solution.geometry.rows[2]
            val shiftItem = row3.items.first { it.stableId == "key_shift" }
            val deleteItem = row3.items.first { it.stableId == "key_delete" }

            assertEquals(shiftItem.bounds.width, deleteItem.bounds.width)

            // The alpha keys in row 3 should match alpha unit width from row 1
            val alphaUnit = solution.geometry.rows[0].items[0].bounds.width
            val zKey = row3.items.first { it.stableId == "key_z" }
            assertEquals(alphaUnit, zKey.bounds.width)
        }
    }
}
