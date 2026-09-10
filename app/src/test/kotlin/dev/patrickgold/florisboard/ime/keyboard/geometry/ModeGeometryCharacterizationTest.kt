/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.PlaceholderLoadingKeyboard
import dev.patrickgold.florisboard.ime.keyboard.SentinelKind
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stage 05 step 1: what every reachable mode's geometry does *before* the stage changes anything.
 *
 * These are characterization tests, not aspiration. Where a case below is marked
 * [KNOWN_DEFECT] it pins behaviour the stage intends to change, so the
 * change shows up as an edited assertion with a reason rather than as a silent movement of pixels.
 * Everything else is a compatibility target: Stage 05 must not move it.
 */
class ModeGeometryCharacterizationTest {

    private val width = SolverFixtures.PORTRAIT_WIDTH
    private val base = SolverFixtures.ROW_BASE_HEIGHT

    private fun prefs(
        alphaKeyWidthPercent: Int = 100,
        utilityKeyWidthPercent: Int = 100,
        alphaRowHeightPercent: Int = 100,
        utilityRowHeightPercent: Int = 75,
    ) = GeometryPreferences(
        rowBaseHeightPx = base,
        alphaKeyWidthPercent = alphaKeyWidthPercent,
        utilityKeyWidthPercent = utilityKeyWidthPercent,
        alphaRowHeightPercent = alphaRowHeightPercent,
        utilityRowHeightPercent = utilityRowHeightPercent,
    )

    private fun solveIntrinsic(
        keyboard: TextKeyboard,
        prefs: GeometryPreferences = prefs(),
    ) = TextKeyboardGeometryBridge.solve(
        keyboard = keyboard,
        prefs = prefs,
        availableWidth = width,
        framePolicy = FramePolicy.Intrinsic(prefs.sanitized().rowBaseHeightPx),
    )

    // -- Declared roles per mode ---------------------------------------------------------------

    /**
     * Every reachable mode's row roles, as composition actually produces them.
     *
     * This is the contract the rest of the stage builds on: if a mode's roles change, the frame
     * policy and the width policy both change with it, so the roles are pinned first.
     */
    @Test
    fun `each mode declares the roles its composition assigns`() {
        val expected = mapOf(
            "defaultCoding" to listOf(
                SemanticRowRole.ALPHA, SemanticRowRole.ALPHA, SemanticRowRole.ALPHA,
                SemanticRowRole.PRIMARY_ACTION,
                SemanticRowRole.CODING_UTILITY, SemanticRowRole.CODING_UTILITY,
            ),
            "codingUtilitiesHidden" to listOf(
                SemanticRowRole.ALPHA, SemanticRowRole.ALPHA, SemanticRowRole.ALPHA,
                SemanticRowRole.PRIMARY_ACTION,
            ),
            "characters" to listOf(
                SemanticRowRole.ALPHA, SemanticRowRole.ALPHA, SemanticRowRole.ALPHA,
                SemanticRowRole.PRIMARY_ACTION,
            ),
            "wideSymbols" to listOf(
                SemanticRowRole.SYMBOL, SemanticRowRole.SYMBOL, SemanticRowRole.SYMBOL,
                SemanticRowRole.PRIMARY_ACTION,
            ),
            "numeric" to List(4) { SemanticRowRole.NUMERIC },
            "numericAdvanced" to List(4) { SemanticRowRole.NUMERIC },
            "phone" to List(4) { SemanticRowRole.NUMERIC },
            "phone2" to List(4) { SemanticRowRole.NUMERIC },
        )
        val actual = mapOf(
            "defaultCoding" to GeometryFixtures.defaultCoding(),
            "codingUtilitiesHidden" to GeometryFixtures.codingUtilitiesHidden(),
            "characters" to GeometryFixtures.characters(),
            "wideSymbols" to GeometryFixtures.wideSymbols(),
            "numeric" to GeometryFixtures.numeric(),
            "numericAdvanced" to GeometryFixtures.numericAdvanced(),
            "phone" to GeometryFixtures.phone(),
            "phone2" to GeometryFixtures.phone2(),
        ).mapValues { (_, keyboard) ->
            TextKeyboardGeometryBridge.describeRows(keyboard).map { it.role }
        }
        assertEquals(expected, actual)
    }

    // -- Intrinsic frame height per mode --------------------------------------------------------

    /**
     * The intrinsic frame height each mode solves to at shipped defaults.
     *
     * `FlorisImeSizing.keyboardUiHeight()` asks exactly this question. The numbers matter less than
     * the fact that they are a function of declared roles: 60px per full row, 45px per utility row.
     */
    @Test
    fun `each mode solves a frame height from its declared roles`() {
        val heights = listOf(
            "defaultCoding" to GeometryFixtures.defaultCoding(),
            "codingUtilitiesHidden" to GeometryFixtures.codingUtilitiesHidden(),
            "characters" to GeometryFixtures.characters(),
            "wideSymbols" to GeometryFixtures.wideSymbols(),
            "numeric" to GeometryFixtures.numeric(),
            "numericAdvanced" to GeometryFixtures.numericAdvanced(),
            "phone" to GeometryFixtures.phone(),
        ).associate { (name, keyboard) ->
            name to TextKeyboardGeometryBridge.frameHeight(keyboard, prefs(), width)
        }
        assertEquals(
            mapOf(
                // 4 full rows at 60 + 2 utility rows at 45
                "defaultCoding" to 330,
                "codingUtilitiesHidden" to 240,
                "characters" to 240,
                "wideSymbols" to 240,
                "numeric" to 240,
                "numericAdvanced" to 240,
                "phone" to 240,
            ),
            heights,
        )
    }

    /**
     * Symbols and Characters solve the same intrinsic height when they have the same row count.
     *
     * This is why borrowing the Characters frame has been survivable so far: at the shipped wide
     * layouts the two surfaces agree by coincidence of shape, not by policy. Stage 05 replaces the
     * coincidence with a declared group.
     */
    @Test
    fun `characters and wide symbols agree on height only because their shapes agree`() {
        val characters = TextKeyboardGeometryBridge.frameHeight(GeometryFixtures.characters(), prefs(), width)
        val symbols = TextKeyboardGeometryBridge.frameHeight(GeometryFixtures.wideSymbols(), prefs(), width)
        assertEquals(characters, symbols)
        // And the coincidence is exactly that: equal row counts with equal roles per position.
        assertEquals(
            TextKeyboardGeometryBridge.describeRows(GeometryFixtures.characters()).size,
            TextKeyboardGeometryBridge.describeRows(GeometryFixtures.wideSymbols()).size,
        )
    }

    // -- Width policy per role ------------------------------------------------------------------

    /**
     * Alpha rows are the shared grid; numeric and symbol rows fit their own units to the content.
     *
     * A nine-key alpha row is centred against a ten-unit reference rather than stretched, while a
     * numeric row — which consumes no shared reference — spans the whole content area.
     */
    @Test
    fun `alpha rows share one grid while numeric rows fit the content width`() {
        val coding = (solveIntrinsic(GeometryFixtures.defaultCoding()) as TextKeyboardGeometryBridge.Result.Solved)
            .geometry
        val tenKeyRow = coding.rows[0]
        val nineKeyRow = coding.rows[1]
        val tenKeyUnit = tenKeyRow.items[0].bounds.width
        val nineKeyUnit = nineKeyRow.items[0].bounds.width
        assertEquals(tenKeyUnit, nineKeyUnit, "nine-key alpha rows keep the ten-key grid")
        assertTrue(nineKeyRow.items.first().bounds.left > 0, "a nine-key row is centred, not stretched")

        val numeric = (solveIntrinsic(GeometryFixtures.numeric()) as TextKeyboardGeometryBridge.Result.Solved)
            .geometry
        assertEquals(0, numeric.rows[0].items.first().bounds.left, "a numeric row starts at the content edge")
        assertEquals(
            width.toInt(),
            numeric.rows[0].items.last().bounds.right,
            "a numeric row spans the content width",
        )
    }

    /**
     * `@KNOWN_DEFECT` — the *Alpha Key Width* preference governs numeric and symbol rows.
     *
     * Numeric and symbol rows consume no shared width reference, so they fit their own units to the
     * content area — and are then multiplied by the alpha scale anyway, because
     * `KeyboardGeometryPolicy` names no width scale for their roles and they fall through to the
     * alpha branch. A slider labelled "Alpha Key Width" therefore narrows a keyboard that contains
     * no letters at all.
     *
     * Stage 05 required scope 1 and the compatibility requirement that specialized rows must not
     * inherit alpha width policy both land on this assertion.
     */
    @KNOWN_DEFECT("the Alpha Key Width slider governs numeric and symbol rows, which contain no letters")
    @Test
    fun `alpha key width silently scales numeric and symbol rows`() {
        val narrow = prefs(alphaKeyWidthPercent = 80)

        val numeric = (solveIntrinsic(GeometryFixtures.numeric(), narrow)
            as TextKeyboardGeometryBridge.Result.Solved).geometry
        val numericSpan = numeric.rows[0].items.last().bounds.right - numeric.rows[0].items.first().bounds.left
        assertEquals((width * 0.8).toInt(), numericSpan, "numeric rows shrink with the alpha slider")

        val symbols = (solveIntrinsic(GeometryFixtures.wideSymbols(), narrow)
            as TextKeyboardGeometryBridge.Result.Solved).geometry
        val symbolRow = symbols.rows[0]
        val symbolSpan = symbolRow.items.last().bounds.right - symbolRow.items.first().bounds.left
        assertEquals((width * 0.8).toInt(), symbolSpan, "symbol rows shrink with the alpha slider")
    }

    /**
     * `@KNOWN_DEFECT` — *Alpha Key Width* above 100% is unsatisfiable, and takes every other
     * geometry preference down with it.
     *
     * The shared reference gives the widest alpha row exactly the content width, so any scale above
     * 1.0 overflows it. The bridge then re-solves with [GeometryPreferences] stripped back to the
     * shipped defaults, which silently discards the user's spacing, gaps and heights for that
     * render. The settings slider offers 80–140%, so two fifths of its range does this.
     */
    @KNOWN_DEFECT("any alpha scale above 1.0 overflows the shared reference and discards every other geometry preference")
    @Test
    fun `alpha key width above one hundred percent falls the whole keyboard back to canonical`() {
        val wide = prefs(alphaKeyWidthPercent = 105)
        val result = solveIntrinsic(GeometryFixtures.defaultCoding(), wide)
        assertTrue(result is TextKeyboardGeometryBridge.Result.Fallback, "expected a canonical fallback, got ${result::class.simpleName}")
        val diagnostics = (result as TextKeyboardGeometryBridge.Result.Fallback).diagnostics
        assertTrue(diagnostics.any { it.contains("unsatisfiable") }, "the fallback should say why: $diagnostics")
    }

    /**
     * `@KNOWN_DEFECT` — *Mod Key Width* above 100% does the same through the utility rows.
     */
    @KNOWN_DEFECT("any utility scale above 1.0 overflows the utility rows and discards every other geometry preference")
    @Test
    fun `mod key width above one hundred percent falls the whole keyboard back to canonical`() {
        val wide = prefs(utilityKeyWidthPercent = 105)
        val result = solveIntrinsic(GeometryFixtures.defaultCoding(), wide)
        assertTrue(result is TextKeyboardGeometryBridge.Result.Fallback, "expected a canonical fallback, got ${result::class.simpleName}")
    }

    /**
     * The primary action row is a width-reference *consumer* but is not width-*scaled*.
     *
     * So narrowing the alpha keys narrows the letter block and leaves the space row spanning the
     * full grid. Pinned deliberately: it is the closest thing the current policy has to giving the
     * space row independent width authority, and Stage 05 must not disturb it while it is naming
     * the specialized roles around it.
     */
    @Test
    fun `narrowing alpha keys does not narrow the primary action row`() {
        val narrow = prefs(alphaKeyWidthPercent = 80)
        val coding = (solveIntrinsic(GeometryFixtures.defaultCoding(), narrow)
            as TextKeyboardGeometryBridge.Result.Solved).geometry
        val alphaRow = coding.rows[0]
        val primaryRow = coding.rows[3]
        val alphaSpan = alphaRow.items.last().bounds.right - alphaRow.items.first().bounds.left
        val primarySpan = primaryRow.items.last().bounds.right - primaryRow.items.first().bounds.left
        assertEquals((width * 0.8).toInt(), alphaSpan, "the letter block narrows")
        assertEquals(width.toInt(), primarySpan, "the primary action row keeps the full grid")
    }

    // -- Height policy per role -----------------------------------------------------------------

    /**
     * Utility and extension rows take the utility height; every other role takes the alpha height.
     *
     * Numeric and symbol rows are full-height entry rows, which is correct and stays that way. What
     * the stage changes is that they will say so by name rather than by falling through an `else`.
     */
    @Test
    fun `numeric and symbol rows take the full row height`() {
        val numeric = (solveIntrinsic(GeometryFixtures.numeric()) as TextKeyboardGeometryBridge.Result.Solved)
            .geometry
        assertTrue(numeric.rows.all { it.bounds.height == 60 }, "every numeric row is a full row")

        val symbols = (solveIntrinsic(GeometryFixtures.wideSymbols()) as TextKeyboardGeometryBridge.Result.Solved)
            .geometry
        assertTrue(symbols.rows.take(3).all { it.bounds.height == 60 }, "every symbol row is a full row")
    }

    /**
     * The utility height control moves utility rows and nothing else.
     *
     * Pinned because Stage 05 adds roles to the height map, and adding a role to the wrong set
     * would show up here first.
     */
    @Test
    fun `the utility height control moves only utility rows`() {
        val tall = prefs(utilityRowHeightPercent = 100)
        val coding = (solveIntrinsic(GeometryFixtures.defaultCoding(), tall)
            as TextKeyboardGeometryBridge.Result.Solved).geometry
        assertTrue(coding.rows.all { it.bounds.height == 60 }, "at 100% a utility row equals an alpha row")

        val numeric = TextKeyboardGeometryBridge.frameHeight(GeometryFixtures.numeric(), tall, width)
        assertEquals(240, numeric, "numeric has no utility rows to move")
    }

    // -- Coding boundary gaps -------------------------------------------------------------------

    /**
     * Coding boundary gaps are declared for the utility block, so no numeric or symbol surface can
     * receive one. Pinned because the gap policy is keyed by role and Stage 05 edits role sets.
     */
    @Test
    fun `no numeric or symbol surface receives coding boundary gaps`() {
        val gapped = GeometryPreferences(
            rowBaseHeightPx = base,
            utilityGapAbovePx = 16.0,
            utilityGapWithinPx = 8.0,
            utilityGapBelowPx = 16.0,
        )
        // Four full rows and nothing else: 240px, with no gap budget charged anywhere.
        assertEquals(240, TextKeyboardGeometryBridge.frameHeight(GeometryFixtures.numeric(), gapped, width))
        assertEquals(240, TextKeyboardGeometryBridge.frameHeight(GeometryFixtures.wideSymbols(), gapped, width))
        assertEquals(240, TextKeyboardGeometryBridge.frameHeight(GeometryFixtures.characters(), gapped, width))
        // Coding does charge them: 330 + 16 + 8 + 16.
        assertEquals(370, TextKeyboardGeometryBridge.frameHeight(GeometryFixtures.defaultCoding(), gapped, width))
    }

    // -- Sentinels ------------------------------------------------------------------------------

    /**
     * A sentinel keyboard declares no rows, so it has no solvable geometry and no frame height.
     * `FlorisImeSizing` substitutes four canonical rows; that substitution is its own, not the
     * solver's, and this pins the solver half of it.
     */
    @Test
    fun `sentinel keyboards have no solvable frame height`() {
        val smartbar = GeometryFixtures.sentinel(
            KeyboardMode.SMARTBAR_QUICK_ACTIONS,
            SentinelKind.SMARTBAR_QUICK_ACTIONS,
        )
        assertEquals(null, TextKeyboardGeometryBridge.frameHeight(smartbar, prefs(), width))
    }

    /**
     * The loading placeholder does have rows, all of them `PLACEHOLDER`, and solves to the same
     * four-row height the window reserves for it. This is what keeps the first few frames from
     * resizing under the user's thumb.
     */
    @Test
    fun `the loading placeholder solves to four full rows`() {
        val placeholder = PlaceholderLoadingKeyboard
        assertEquals(
            List(4) { SemanticRowRole.PLACEHOLDER },
            TextKeyboardGeometryBridge.describeRows(placeholder).map { it.role },
        )
        assertNotNull(TextKeyboardGeometryBridge.frameHeight(placeholder, prefs(), width))
        assertEquals(240, TextKeyboardGeometryBridge.frameHeight(placeholder, prefs(), width))
    }
}
