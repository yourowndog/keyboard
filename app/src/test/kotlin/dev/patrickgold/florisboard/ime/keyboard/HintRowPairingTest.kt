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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.keyboard.geometry.COMPATIBILITY
import dev.patrickgold.florisboard.ime.keyboard.geometry.EXPECTED_FIX
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/**
 * Stage 05: symbol and number hint pairing, before and after it stopped counting rows.
 *
 * The compatibility bar is that the *stock* configuration keeps the hints it has. These tests hold
 * that bar by reimplementing the row-index arithmetic that shipped ([legacyPairing]) and asserting
 * the two agree wherever a hint can actually land — rows whose role is [SemanticRowRole.ALPHA] or
 * [SemanticRowRole.PRIMARY_ACTION], since `LayoutManager.addRowHints` skips every key with
 * `isAlpha == false` and the modifier rows are entirely such keys.
 *
 * The row counts come from the shipped assets rather than from numbers typed in here, so a layout
 * that gains or loses a row fails these tests instead of quietly invalidating them. What the fixtures
 * still hardcode is the role assignment `LayoutManager.mergeLayouts` performs, which is stated once
 * in [mergedRoles].
 */
class HintRowPairingTest {

    private companion object {
        val LAYOUT_ROOT = File("src/main/assets/ime/keyboard/org.florisboard.layouts/layouts")
        val JSON = Json { ignoreUnknownKeys = true }
    }

    // -- Fixtures, read off the shipped layouts --------------------------------------------------

    private fun rowsOf(type: String, name: String): Int {
        val file = File(LAYOUT_ROOT, "$type/$name.json")
        assertTrue(file.isFile, "missing layout asset ${file.path}")
        return JSON.parseToJsonElement(file.readText()).jsonArray.size
    }

    /**
     * The role sequence `LayoutManager.mergeLayouts` produces for a main layout and its modifier.
     *
     * Extension rows come first, then the main layout's rows minus its last, then the primary action
     * row the last main row is spliced into, then the modifier's rows minus the one consumed by that
     * splice. Utility-row visibility is not modelled: `modRowsHidden` drops modifier rows that carry
     * no space key, which changes how many trailing utility rows there are but never touches the
     * letters or the primary action row, so it cannot move a hint.
     */
    private fun mergedRoles(
        mainType: String,
        main: String,
        modifierType: String,
        modifier: String,
        mainRole: SemanticRowRole,
        extensionRows: Int = 0,
    ): List<SemanticRowRole> = buildList {
        repeat(extensionRows) { add(SemanticRowRole.EXTENSION) }
        repeat(rowsOf(mainType, main) - 1) { add(mainRole) }
        add(SemanticRowRole.PRIMARY_ACTION)
        repeat(rowsOf(modifierType, modifier) - 1) { add(SemanticRowRole.CODING_UTILITY) }
    }

    /** The stock text profile: `characters/qwerty` over `charactersMod/default`. */
    private fun stockCharacters(extensionRows: Int = 0) =
        mergedRoles("characters", "qwerty", "charactersMod", "default", SemanticRowRole.ALPHA, extensionRows)

    /** Its symbol layer: `symbols/western` over `symbolsMod/default`. */
    private fun stockSymbols(extensionRows: Int = 0) =
        mergedRoles("symbols", "western", "symbolsMod", "default", SemanticRowRole.SYMBOL, extensionRows)

    /** The Coding profile, which is what OmniBoard ships as the default subtype. */
    private fun codingCharacters(extensionRows: Int = 0) =
        mergedRoles("characters", "qwerty_wide", "charactersMod", "qwerty_wide_mod", SemanticRowRole.ALPHA, extensionRows)

    /** Its symbol layer: `symbols/western_wide` over `symbolsMod/western_wide_mod`. */
    private fun codingSymbols(extensionRows: Int = 0) =
        mergedRoles("symbols", "western_wide", "symbolsMod", "western_wide_mod", SemanticRowRole.SYMBOL, extensionRows)

    // -- The arithmetic that shipped, kept so the new rule can be compared against it -------------

    /**
     * The pre-Stage-05 pairing: row 0 to row 0 for the digits, everything else bottom-aligned on the
     * difference in total row counts.
     */
    private fun legacyPairing(
        charactersRoles: List<SemanticRowRole>,
        symbolsRoles: List<SemanticRowRole>,
    ): HintRowPairing.Pairing {
        val numberHint = if (symbolsRoles.isNotEmpty()) HintRowPairing.RowPair(0, 0) else null
        val rOffset = charactersRoles.size - symbolsRoles.size
        val symbolHints = buildList {
            for (r in charactersRoles.indices) {
                if (r < rOffset) continue
                if (r - rOffset !in symbolsRoles.indices) continue
                add(HintRowPairing.RowPair(target = r, source = r - rOffset))
            }
        }
        return HintRowPairing.Pairing(numberHint = numberHint, symbolHints = symbolHints)
    }

    /** The rows a hint can actually land on. Everything else is modifier keys, which are skipped. */
    private fun List<HintRowPairing.RowPair>.onHintableRows(roles: List<SemanticRowRole>) = filter {
        roles[it.target] == SemanticRowRole.ALPHA || roles[it.target] == SemanticRowRole.PRIMARY_ACTION
    }

    private fun assertSameSymbolHints(
        charactersRoles: List<SemanticRowRole>,
        symbolsRoles: List<SemanticRowRole>,
        message: String,
    ) {
        assertEquals(
            legacyPairing(charactersRoles, symbolsRoles).symbolHints.onHintableRows(charactersRoles),
            HintRowPairing.of(charactersRoles, symbolsRoles).symbolHints.onHintableRows(charactersRoles),
            message,
        )
    }

    // -- The shapes everything below depends on --------------------------------------------------

    /**
     * The four merged shapes, spelled out.
     *
     * If a layout asset changes row count this test names the change, rather than letting the
     * compatibility assertions below silently start describing a different keyboard.
     */
    @Test
    fun `the shipped layouts have the shapes these tests assume`() {
        assertEquals(
            listOf(
                SemanticRowRole.ALPHA,
                SemanticRowRole.ALPHA,
                SemanticRowRole.PRIMARY_ACTION,
                SemanticRowRole.CODING_UTILITY,
            ),
            stockCharacters(),
            "characters/qwerty over charactersMod/default",
        )
        assertEquals(
            listOf(
                SemanticRowRole.SYMBOL,
                SemanticRowRole.PRIMARY_ACTION,
                SemanticRowRole.CODING_UTILITY,
            ),
            stockSymbols(),
            "symbols/western over symbolsMod/default — one symbol row, no digit row",
        )
        assertEquals(
            listOf(
                SemanticRowRole.ALPHA,
                SemanticRowRole.ALPHA,
                SemanticRowRole.ALPHA,
                SemanticRowRole.PRIMARY_ACTION,
                SemanticRowRole.CODING_UTILITY,
                SemanticRowRole.CODING_UTILITY,
            ),
            codingCharacters(),
            "characters/qwerty_wide over charactersMod/qwerty_wide_mod",
        )
        assertEquals(
            listOf(
                SemanticRowRole.SYMBOL,
                SemanticRowRole.SYMBOL,
                SemanticRowRole.SYMBOL,
                SemanticRowRole.PRIMARY_ACTION,
                SemanticRowRole.CODING_UTILITY,
            ),
            codingSymbols(),
            "symbols/western_wide over symbolsMod/western_wide_mod",
        )
    }

    // -- Compatibility ---------------------------------------------------------------------------

    /**
     * The stock configuration pairs exactly as it did, hint for hint.
     *
     * Two letter rows over one symbol row: bottom-alignment gave the lower letter row the symbol row
     * and left the top one alone, and pairing by role reaches the same answer.
     */
    @COMPATIBILITY("the stock text profile keeps every hint it had")
    @Test
    fun `the stock profile pairs exactly as the row indices did`() {
        val characters = stockCharacters()
        val symbols = stockSymbols()
        assertSameSymbolHints(characters, symbols, "the stock profile's punctuation hints moved")
        assertEquals(
            legacyPairing(characters, symbols).numberHint,
            HintRowPairing.of(characters, symbols).numberHint,
            "the stock profile's number hint moved",
        )
        assertEquals(
            listOf(
                HintRowPairing.RowPair(target = 1, source = 0),
                HintRowPairing.RowPair(target = 2, source = 1),
            ),
            HintRowPairing.of(characters, symbols).symbolHints,
            "the lower letter row takes the symbol row; the primary action rows pair with each other",
        )
    }

    /**
     * A top extension row leaves the stock pairing alone.
     *
     * The dev row sits above everything, which is the one shape a total-count offset handles
     * correctly, so this configuration was never broken and must not start moving now.
     *
     * Its number hint does move, from the extension row itself to the first letter row — but
     * `LayoutManager` suppresses the number hint outright while the dev row is showing, so nothing is
     * drawn either way. The assertion is about what would happen if that gate were opened.
     */
    @COMPATIBILITY("a top extension row does not move the punctuation hints")
    @Test
    fun `a top extension row pairs as it did before`() {
        val characters = stockCharacters(extensionRows = 1)
        val symbols = stockSymbols()
        assertSameSymbolHints(characters, symbols, "an extension row shifted hints it used to leave alone")

        val numberHint = assertNotNull(HintRowPairing.of(characters, symbols).numberHint)
        assertEquals(
            SemanticRowRole.ALPHA,
            characters[numberHint.target],
            "the number hint would land on the extension row rather than on the letters",
        )
    }

    /**
     * With the number row showing on both keyboards, every letter-to-symbol pairing is unchanged.
     *
     * The number row is added to Characters and to Symbols alike, so the totals stay level. The one
     * pairing that disappears is the top letter row's, which the old rule pointed at the *symbol
     * layer's number row* — a row of `type: numeric` keys, which `addRowHints` discards when it is
     * asked for character hints. Nothing was ever drawn from it.
     */
    @COMPATIBILITY("the number row does not move any hint that was actually drawn")
    @Test
    fun `an extension row on both keyboards leaves the symbol pairings alone`() {
        val characters = stockCharacters(extensionRows = 1)
        val symbols = stockSymbols(extensionRows = 1)

        val legacy = legacyPairing(characters, symbols).symbolHints.onHintableRows(characters)
        val current = HintRowPairing.of(characters, symbols).symbolHints.onHintableRows(characters)
        assertEquals(
            legacy.filterNot { symbols[it.source] == SemanticRowRole.EXTENSION },
            current,
            "a hint that was actually drawn moved",
        )
        assertEquals(
            listOf(SemanticRowRole.EXTENSION),
            legacy.filterNot { it in current }.map { symbols[it.source] },
            "the only dropped pairing should be the one that read from the symbol layer's number row",
        )
    }

    // -- Fixes -----------------------------------------------------------------------------------

    /**
     * The Coding profile's letters take the symbol rows that belong to them again.
     *
     * `charactersMod/qwerty_wide_mod.json` has three rows and `symbolsMod/western_wide_mod.json` has
     * two, and the extra row hangs below the letters — so aligning on totals slid the whole pairing
     * up by one. The top letter row was never paired at all, and each letter row below it was given
     * the symbol row belonging to the row above: `a s d f` had no hints and `z x c v` showed
     * `@ # $ %`, which are the hints `a s d f` should have been showing.
     */
    @EXPECTED_FIX("the Coding profile's letters pair with the symbol rows above them")
    @Test
    fun `an extra modifier row below the letters no longer displaces the hints`() {
        val characters = codingCharacters()
        val symbols = codingSymbols()

        val legacy = legacyPairing(characters, symbols).symbolHints.onHintableRows(characters)
        assertEquals(
            listOf(
                HintRowPairing.RowPair(target = 1, source = 0),
                HintRowPairing.RowPair(target = 2, source = 1),
                HintRowPairing.RowPair(target = 3, source = 2),
            ),
            legacy,
            "the fixture no longer reproduces the defect this test exists for",
        )
        assertTrue(legacy.none { it.target == 0 }, "the top letter row used to be skipped entirely")
        assertTrue(
            legacy.filter { characters[it.target] == SemanticRowRole.ALPHA }.all { it.source == it.target - 1 },
            "every letter row used to take the symbol row belonging to the row above it",
        )

        assertEquals(
            listOf(
                HintRowPairing.RowPair(target = 0, source = 0),
                HintRowPairing.RowPair(target = 1, source = 1),
                HintRowPairing.RowPair(target = 2, source = 2),
                HintRowPairing.RowPair(target = 3, source = 3),
            ),
            HintRowPairing.of(characters, symbols).symbolHints,
            "each letter row should take the symbol row at the same depth",
        )
    }

    /**
     * The number hint lands on the letters rather than on the number row.
     *
     * With the number row showing, row 0 of both keyboards *is* the number row, so the old rule read
     * the digits off it and wrote them straight back onto themselves — a "1" hinted with a "1" —
     * while the letters, which is where the user asked for digit hints, got none.
     */
    @EXPECTED_FIX("the number hint targets the first letter row instead of the number row")
    @Test
    fun `the number hint skips an extension row on both sides`() {
        val characters = stockCharacters(extensionRows = 1)
        val symbols = stockSymbols(extensionRows = 1)

        val legacyTarget = assertNotNull(legacyPairing(characters, symbols).numberHint).target
        assertEquals(
            SemanticRowRole.EXTENSION,
            characters[legacyTarget],
            "the fixture no longer reproduces the number-row-onto-itself pairing",
        )

        val numberHint = assertNotNull(HintRowPairing.of(characters, symbols).numberHint)
        assertEquals(SemanticRowRole.ALPHA, characters[numberHint.target], "digits must be hinted onto letters")
        assertEquals(SemanticRowRole.SYMBOL, symbols[numberHint.source], "digits must be read off a symbol row")
    }

    // -- Properties ------------------------------------------------------------------------------

    private fun configurations() = listOf(
        stockCharacters() to stockSymbols(),
        codingCharacters() to codingSymbols(),
        stockCharacters(extensionRows = 1) to stockSymbols(),
        stockCharacters(extensionRows = 1) to stockSymbols(extensionRows = 1),
        codingCharacters(extensionRows = 2) to codingSymbols(extensionRows = 1),
    )

    /**
     * A hint is never written onto a row that is not letters or the primary action row, and never
     * read off a row that is not symbols or the primary action row.
     *
     * The per-key `isAlpha` filter in `addRowHints` made the first half true by accident before;
     * stating it here means a future role cannot quietly start collecting hints.
     */
    @Test
    fun `hints only ever pair letters with symbols`() {
        for ((characters, symbols) in configurations()) {
            val pairing = HintRowPairing.of(characters, symbols)
            val targets = (pairing.symbolHints + listOfNotNull(pairing.numberHint)).map { characters[it.target] }
            assertTrue(
                targets.all { it == SemanticRowRole.ALPHA || it == SemanticRowRole.PRIMARY_ACTION },
                "a hint was aimed at $targets in $characters",
            )
            val sources = pairing.symbolHints.map { symbols[it.source] }
            assertTrue(
                sources.all { it == SemanticRowRole.SYMBOL || it == SemanticRowRole.PRIMARY_ACTION },
                "a hint was read off $sources in $symbols",
            )
        }
    }

    /**
     * Every pair indexes both arrangements.
     *
     * `LayoutManager` indexes both arrangements with these numbers directly, so an out-of-range pair
     * is a crash rather than a wrong hint. Degenerate shapes are included because a failed layout
     * load produces them.
     */
    @Test
    fun `every pair indexes both arrangements`() {
        val degenerate = listOf(
            emptyList(),
            listOf(SemanticRowRole.ALPHA),
            listOf(SemanticRowRole.SYMBOL),
            listOf(SemanticRowRole.CODING_UTILITY, SemanticRowRole.CODING_UTILITY),
        )
        val shapes = configurations().flatMap { listOf(it.first, it.second) } + degenerate
        for (characters in shapes) {
            for (symbols in shapes) {
                val pairing = HintRowPairing.of(characters, symbols)
                for (pair in pairing.symbolHints + listOfNotNull(pairing.numberHint)) {
                    assertTrue(pair.target in characters.indices, "target ${pair.target} out of range for $characters")
                    assertTrue(pair.source in symbols.indices, "source ${pair.source} out of range for $symbols")
                }
            }
        }
    }

    /**
     * A keyboard with no symbol rows produces no hints rather than an arbitrary pairing.
     *
     * This is the state a failed Symbols computation leaves behind, and it used to be guarded by an
     * `isNotEmpty()` check at the call site that only covered the number hint.
     */
    @Test
    fun `a keyboard with nothing to read from produces no hints`() {
        val pairing = HintRowPairing.of(stockCharacters(), emptyList())
        assertNull(pairing.numberHint, "there was no symbol row to read digits from")
        assertTrue(pairing.symbolHints.isEmpty(), "there was no symbol row to read punctuation from")
    }
}
