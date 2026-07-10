package dev.patrickgold.florisboard.ime.nlp

import kotlin.test.Test
import kotlin.test.assertEquals

class NeuralScorerTest {
    @Test
    fun fnvBucketMatchesTrainingVectors() {
        assertEquals(22679L, NeuralScorer.fnvBucket("the"))
        assertEquals(2220L, NeuralScorer.fnvBucket("i'm"))
        assertEquals(22284L, NeuralScorer.fnvBucket("wife's"))
        assertEquals(26795L, NeuralScorer.fnvBucket("dont"))
        assertEquals(20236L, NeuralScorer.fnvBucket("know"))
        assertEquals(0L, NeuralScorer.fnvBucket(null))
        assertEquals(0L, NeuralScorer.fnvBucket(""))
    }

    @Test
    fun encodeWordUsesTrainingVocabularyAndPadding() {
        val ids = NeuralScorer.encodeWordPadded("a'z!")
        assertEquals(NeuralScorer.MAX_WORD_IDS, ids.size)
        assertEquals(28L, ids[0])
        assertEquals(1L, ids[1])
        assertEquals(27L, ids[2])
        assertEquals(26L, ids[3])
        assertEquals(30L, ids[4])
        assertEquals(29L, ids[5])
        assertEquals(0L, ids[6])
    }

    @Test
    fun scalarRowMatchesTrainingContract() {
        val actual = NeuralScorer.scalarRow(
            typed = "dont",
            term = "dont",
            editDistance = 1.0,
            lnFreq = 8.0,
            bigramCount = 0,
        )
        val expected = floatArrayOf(0.5f, 0.5f, 0.0f, 0.2f, 1.0f)
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals(expected[index], actual[index], absoluteTolerance = 0.0001f)
        }
    }
}
