package dev.patrickgold.florisboard.ime.nlp.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordSegmentationTest {
    private val words = setOf("in", "the", "int", "he", "every", "time", "termux")

    private fun find(
        input: String,
        bigrams: Set<Pair<String, String>> = setOf("in" to "the"),
    ): String? = WordSegmentation.findUniqueHighConfidence(
        input = input,
        isWord = { it in words },
        hasBigram = { left, right -> left to right in bigrams },
    )

    @Test
    fun recoversUniqueObservedSplit() {
        assertEquals("in the", find("inthe"))
    }

    @Test
    fun ignoresSplitWithoutBigramEvidence() {
        assertNull(find("everytime"))
    }

    @Test
    fun ignoresAmbiguousObservedSplits() {
        assertNull(find("inthe", setOf("in" to "the", "int" to "he")))
    }

    @Test
    fun validJoinedWordIsImmune() {
        val withJoinedWord = words + "inthe"
        assertNull(
            WordSegmentation.findUniqueHighConfidence(
                input = "inthe",
                isWord = { it in withJoinedWord },
                hasBigram = { _, _ -> true },
            )
        )
    }

    @Test
    fun ignoresNumbersAndPunctuation() {
        assertNull(find("in2the"))
        assertNull(find("in-the"))
    }

    @Test
    fun appliesTypedAndSentenceCasing() {
        assertEquals("in the", WordSegmentation.applyCasing("inthe", "in the", false))
        assertEquals("In the", WordSegmentation.applyCasing("Inthe", "in the", false))
        assertEquals("In the", WordSegmentation.applyCasing("inthe", "in the", true))
        assertEquals("IN THE", WordSegmentation.applyCasing("INTHE", "in the", false))
    }
}
