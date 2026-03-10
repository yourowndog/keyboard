package dev.patrickgold.florisboard.ime.nlp

import org.junit.Test
import org.junit.Assert.*
import dev.patrickgold.florisboard.ime.nlp.shared.CandidateScorer

class SymSpellTest {
    @Test
    fun testCandidateScorer() {
        val score = CandidateScorer.score("tbis", "this", 1.0, null)
        println("Score for tbis -> this: $score")
    }
}