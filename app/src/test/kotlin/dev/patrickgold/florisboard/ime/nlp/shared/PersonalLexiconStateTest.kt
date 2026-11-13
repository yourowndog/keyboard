package dev.patrickgold.florisboard.ime.nlp.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalLexiconStateTest {

    private fun lexicon(vararg lines: String, maxEntries: Int = PersonalLexiconState.DEFAULT_MAX_ENTRIES) =
        PersonalLexiconState(maxEntries).apply { merge(lines.asSequence()) }

    @Test
    fun repeatedNovelWordBecomesVocabulary() {
        assertFalse(lexicon("c\therdr\t2").isKnown("herdr"), "two uses are not enough")
        val lex = lexicon("c\therdr\t3")
        assertTrue(lex.isKnown("herdr"))
        assertTrue(lex.isKnown("Herdr"), "lookup ignores case")
    }

    @Test
    fun repeatedTypoNextToAFrequentWordStaysCorrectable() {
        assertFalse(lexicon("c\tthst\t33", "c\tthat\t400").isKnown("thst"))
    }

    @Test
    fun apostropheFreeFormNextToItsContractionStaysCorrectable() {
        assertFalse(lexicon("c\tdont\t20", "c\tdon't\t300").isKnown("dont"))
    }

    @Test
    fun abbreviationWithoutAFrequentNeighbourIsLearned() {
        assertTrue(lexicon("c\tidk\t60", "c\tid\t30").isKnown("idk"))
    }

    @Test
    fun undoneCorrectionMarksTheWordAndRefusesThePair() {
        val lex = lexicon("l\tlitellm", "r\tlitellm\tlinell")
        assertTrue(lex.isKnown("litellm"))
        assertTrue(lex.isRejected("litellm", "linell"))
        assertTrue(lex.isRejected("LiteLLM", "Linell"))
        assertFalse(lex.isRejected("litellm", "literal"))
    }

    @Test
    fun nonWordsAreIgnored() {
        assertEquals(0, lexicon("c\tadbw2\t5", "c\tx\t5", "c\t--\t5").size())
    }

    @Test
    fun serializationRoundTrips() {
        val lex = lexicon("c\ttailscale.\t4", "l\tkarellen", "r\tkarellen\tKarelian")
        val copy = lexicon(*lex.serialize().lines().toTypedArray())
        assertEquals(4, copy.count("tailscale"))
        assertTrue(copy.isKnown("karellen"))
        assertTrue(copy.isRejected("karellen", "karelian"))
    }

    @Test
    fun malformedLinesAreSkippedAndCountsAdd() {
        val lex = lexicon("c\tzepp\t1", "c\tzepp\t66", "c\tbad line", "l\tgatik", "r\tsdk\tidk", "garbage")
        assertEquals(67, lex.count("zepp"))
        assertTrue(lex.isKnown("gatik"))
        assertTrue(lex.isRejected("sdk", "idk"))
    }

    @Test
    fun pruningKeepsTheMostUsedWords() {
        val lines = listOf("c\tkeeper\t50") +
            (0 until 30).map { i -> "c\tword" + ('a' + i % 26) + ('a' + i / 26) + "\t1" }
        val lex = lexicon(*lines.toTypedArray(), maxEntries = 10)
        assertEquals(50, lex.count("keeper"))
        assertTrue(lex.size() <= 10)
    }

    // Harvest 2026-11: hundreds of "undos" restored real typos while the person was
    // backspacing to fix something else.
    @Test
    fun undoOfATypoNextToACommonWordTeachesNothing() {
        val lex = lexicon("c\tthe\t500", "l\ttbe", "r\ttbe\tthe")
        assertFalse(lex.isKnown("tbe"))
        assertFalse(lex.isRejected("tbe", "the"))
    }
}
