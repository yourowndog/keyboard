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

/**
 * Stage 05: which row of the symbol layer supplies the hints for a row of letters.
 *
 * A key on the Characters keyboard can carry two hints: the digit it would produce on the symbol
 * layer, drawn as the number hint, and the punctuation it would produce there, drawn as the symbol
 * hint. Both are read off the *corresponding* row of the Symbols keyboard, so "corresponding" has to
 * be defined.
 *
 * It used to be defined by row index — row 0 paired with row 0 for the digits, and the remaining
 * rows were bottom-aligned by the difference in total row counts. Bottom-alignment is the right
 * instinct, but total row counts are the wrong thing to align on, because the rows that differ
 * between the two keyboards are not all above the letters:
 *
 *  - The Coding profile is the case that reaches Sam. Its modifier layout has three rows
 *    (`charactersMod/qwerty_wide_mod.json`) where the symbol layer's has two
 *    (`symbolsMod/western_wide_mod.json`), and those extra rows sit *below* the letters — so
 *    aligning on totals slid every pairing up by one. `qwertyuiop` was left unpaired,
 *    `asdfghjkl` was hinted from the symbol layer's digit row and showed `1 2 3 …`, and
 *    `z x c` showed `@ # $` — the punctuation belonging to the row above it, offset again by the
 *    shift key, which is a letter-row key as far as hinting is concerned. Pairing by role puts the
 *    digits back on `qwertyuiop` and gives `z x c` the `| _ =` that is actually under them.
 *  - The number row is an extension row on *both* keyboards, so row 0 paired the digits with
 *    themselves: the number hint was drawn as a "1" above a "1" and the letters got none at all.
 *  - The dev row, an extension row on Characters with no counterpart on Symbols, came out right —
 *    but only because it sits above everything, which is exactly the case a total-count offset
 *    absorbs. It was correct by luck, not by rule.
 *
 * Pairing by [SemanticRowRole] states the rule the index arithmetic was reaching for: letters take
 * their hints from symbol rows, and the primary action row takes its hints from the other keyboard's
 * primary action row, however many other rows either side is carrying. In the stock configuration —
 * no extension rows, both modifier layouts two rows deep — it produces exactly the pairing the index
 * arithmetic produced, which is what makes it a safe swap in.
 *
 * One caveat this cannot fix from here: `symbols/western_wide.json` does not mark its digit row
 * `"type": "numeric"`, so those digits reach the letters through the *symbol* hint channel and
 * `hintedNumberRowEnabled` has never drawn anything for the Coding profile. See
 * `LayoutAssetDiagnosticTest`; correcting it is an asset change, not a pairing change.
 *
 * Kept pure and separate from [LayoutManager] because the arithmetic is the part that was wrong and
 * the part worth testing; applying a pairing to two arrays of keys is not.
 */
internal object HintRowPairing {

    /** A hint source row [source] feeding a target row [target], both as arrangement indices. */
    data class RowPair(val target: Int, val source: Int)

    /**
     * The pairing between a Characters arrangement whose rows have [charactersRoles] and a Symbols
     * arrangement whose rows have [symbolsRoles].
     *
     * [numberHint] is the single pair the number-row hint uses, or null when either keyboard has no
     * row of the role it needs. Callers still decide whether the user asked for it.
     *
     * [symbolHints] are the pairs the punctuation hints use, in target order.
     */
    data class Pairing(
        val numberHint: RowPair?,
        val symbolHints: List<RowPair>,
    )

    fun of(
        charactersRoles: List<SemanticRowRole>,
        symbolsRoles: List<SemanticRowRole>,
    ): Pairing {
        val alphaRows = charactersRoles.indicesOf(SemanticRowRole.ALPHA)
        val symbolRows = symbolsRoles.indicesOf(SemanticRowRole.SYMBOL)

        // The digits live on the symbol layer's first symbol row, and they belong on the first row of
        // letters. Neither is "row 0" once an extension row is showing.
        val numberHint = if (alphaRows.isNotEmpty() && symbolRows.isNotEmpty()) {
            RowPair(target = alphaRows.first(), source = symbolRows.first())
        } else {
            null
        }

        // Bottom-aligned within the roles: the letter row nearest the bottom takes the symbol row
        // nearest the bottom. A layout with more symbol rows than letter rows drops its topmost
        // symbol rows rather than drawing them over the wrong letters.
        val offset = alphaRows.size - symbolRows.size
        val symbolHints = buildList {
            for ((i, target) in alphaRows.withIndex()) {
                val source = symbolRows.getOrNull(i - offset) ?: continue
                add(RowPair(target = target, source = source))
            }
            // The primary action row is spliced from a main row and the modifier's first row on both
            // keyboards, so it is exactly one row on each side no matter what else is showing. Its
            // letters take hints from the punctuation spliced into the same position; the modifier
            // keys around them are filtered out when the pairing is applied.
            val charactersPrimary = charactersRoles.indexOf(SemanticRowRole.PRIMARY_ACTION)
            val symbolsPrimary = symbolsRoles.indexOf(SemanticRowRole.PRIMARY_ACTION)
            if (charactersPrimary >= 0 && symbolsPrimary >= 0) {
                add(RowPair(target = charactersPrimary, source = symbolsPrimary))
            }
        }

        return Pairing(numberHint = numberHint, symbolHints = symbolHints)
    }

    private fun List<SemanticRowRole>.indicesOf(role: SemanticRowRole): List<Int> =
        withIndex().filter { it.value == role }.map { it.index }
}
