package dev.patrickgold.florisboard.ime.nlp.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalLexiconStateTest {

    @Test
    fun repeatedNovelWordBecomesVocabulary() {
        val lex = PersonalLexiconState()
        repeat(2) { lex.recordCommit("herdr") }
        assertFalse(lex.isKnown("herdr"), "two uses are not enough")
        lex.recordCommit("herdr")
        assertTrue(lex.isKnown("herdr"))
        assertTrue(lex.isKnown("Herdr"), "lookup ignores case")
    }

    @Test
    fun repeatedTypoNextToAFrequentWordStaysCorrectable() {
        val lex = PersonalLexiconState()
        repeat(33) { lex.recordCommit("thst") }
        repeat(400) { lex.recordCommit("that") }
        assertFalse(lex.isKnown("thst"))
    }

    @Test
    fun apostropheFreeFormNextToItsContractionStaysCorrectable() {
        val lex = PersonalLexiconState()
        repeat(20) { lex.recordCommit("dont") }
        repeat(300) { lex.recordCommit("don't") }
        assertFalse(lex.isKnown("dont"))
    }

    @Test
    fun abbreviationWithoutAFrequentNeighbourIsLearned() {
        val lex = PersonalLexiconState()
        repeat(60) { lex.recordCommit("idk") }
        repeat(30) { lex.recordCommit("id") }
        assertTrue(lex.isKnown("idk"))
    }

    @Test
    fun undoTeachesTheWordAndRefusesThePair() {
        val lex = PersonalLexiconState()
        lex.recordRevert("litellm", "Linell")
        assertTrue(lex.isKnown("litellm"))
        assertTrue(lex.isRejected("litellm", "linell"))
        assertTrue(lex.isRejected("LiteLLM", "Linell"))
        assertFalse(lex.isRejected("litellm", "literal"))
    }

    @Test
    fun insistingOnTheTypedWordTeachesIt() {
        val lex = PersonalLexiconState()
        lex.recordInsist("chezmoi")
        assertTrue(lex.isKnown("chezmoi"))
    }

    @Test
    fun nonWordsAreIgnored() {
        val lex = PersonalLexiconState()
        repeat(5) { lex.recordCommit("adbw2") }
        repeat(5) { lex.recordCommit("x") }
        repeat(5) { lex.recordCommit("--") }
        assertEquals(0, lex.size())
    }

    @Test
    fun serializationRoundTrips() {
        val lex = PersonalLexiconState()
        repeat(4) { lex.recordCommit("tailscale.") }
        lex.recordRevert("karellen", "Karelian")
        val copy = PersonalLexiconState()
        copy.merge(lex.serialize().lineSequence())
        assertEquals(4, copy.count("tailscale"))
        assertTrue(copy.isKnown("karellen"))
        assertTrue(copy.isRejected("karellen", "karelian"))
    }

    @Test
    fun seedCountsAddToLiveCounts() {
        val lex = PersonalLexiconState()
        lex.recordCommit("zepp")
        lex.merge(sequenceOf("c\tzepp\t66", "c\tbad line", "l\tgatik", "r\tsdk\tidk", "garbage"))
        assertEquals(67, lex.count("zepp"))
        assertTrue(lex.isKnown("gatik"))
        assertTrue(lex.isRejected("sdk", "idk"))
    }

    @Test
    fun pruningKeepsTheMostUsedWords() {
        val lex = PersonalLexiconState(maxEntries = 10)
        repeat(50) { lex.recordCommit("keeper") }
        for (i in 0 until 30) lex.recordCommit("word" + ('a' + i % 26) + ('a' + i / 26))
        assertEquals(50, lex.count("keeper"))
        assertTrue(lex.size() <= 10)
    }

    // Harvest 2026-11: hundreds of "undos" restored real typos while the person was
    // backspacing to fix something else.
    @Test
    fun undoOfATypoNextToACommonWordTeachesNothing() {
        val lex = PersonalLexiconState()
        repeat(500) { lex.recordCommit("the") }
        lex.recordRevert("tbe", "the")
        assertFalse(lex.isKnown("tbe"))
        assertFalse(lex.isRejected("tbe", "the"))
    }
}
