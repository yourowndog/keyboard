package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.ime.nlp.shared.CandidateScorer
import kotlin.test.Test

class SymSpellTest {
    @Test
    fun testCandidateScorer() {
        val score = CandidateScorer.score("tbis", "this", 1.0, null)
        println("Score for tbis -> this: $score")
    }
}
