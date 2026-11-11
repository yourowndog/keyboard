package dev.patrickgold.florisboard.ime.nlp.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordSegmentationTest {
    private val words = setOf(
        "in", "the", "int", "he", "every", "time", "termux",
        "what", "is", "this", "up", "keep", "car", "on", "ban", "it",
    )

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

    // --- space typed as a neighbouring key (spacebar mis-hit) ---

    @Test
    fun recoversSpaceMistypedAsAdjacentKey() {
        // 'n' sits directly above the spacebar: whatnis -> what is
        assertEquals("what is", find("whatnis", setOf("what" to "is")))
        // 'b' likewise
        assertEquals("this up", find("thisbup", setOf("this" to "up")))
    }

    @Test
    fun ignoresDroppedCharacterFarFromSpacebar() {
        // 'd' is nowhere near the spacebar, so 'car d on' is not a space mis-hit.
        assertNull(find("cardon", setOf("car" to "on")))
    }

    @Test
    fun mistypedSpaceStillRequiresBigramEvidence() {
        assertNull(find("whatnis", setOf("in" to "the")))
    }

    @Test
    fun mistypedSpaceStillRequiresBothPartsLongEnough() {
        // would split as "keep" + "o", and a one-character part is not a word
        assertNull(find("keepno", setOf("keep" to "o")))
    }

    @Test
    fun validJoinedWordIsImmuneToSpaceSubstitution() {
        val withJoined = words + "banit"
        assertNull(
            WordSegmentation.findUniqueHighConfidence(
                input = "banit",
                isWord = { it in withJoined },
                hasBigram = { _, _ -> true },
            )
        )
    }

    @Test
    fun ambiguityAcrossBothSplitKindsIsRejected() {
        // "whatnis" could read as omitted-space or mistyped-space; neither wins.
        val extra = words + "nis"
        assertNull(
            WordSegmentation.findUniqueHighConfidence(
                input = "whatnis",
                isWord = { it in extra },
                hasBigram = { _, _ -> true },
            )
        )
    }

    @Test
    fun appliesTypedAndSentenceCasing() {
        assertEquals("in the", WordSegmentation.applyCasing("inthe", "in the", false))
        assertEquals("In the", WordSegmentation.applyCasing("Inthe", "in the", false))
        assertEquals("In the", WordSegmentation.applyCasing("inthe", "in the", true))
        assertEquals("IN THE", WordSegmentation.applyCasing("INTHE", "in the", false))
    }
}
