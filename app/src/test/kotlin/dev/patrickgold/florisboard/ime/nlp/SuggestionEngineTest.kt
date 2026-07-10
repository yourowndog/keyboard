package dev.patrickgold.florisboard.ime.nlp

import kotlin.test.Test
import kotlin.test.assertEquals

class SuggestionEngineTest {
    private val engine = NgramSuggestionEngine(
        unigramLogFreq = emptyMap()
    )

    @Test
    fun emptyCandidateListRanksEmpty() {
        assertEquals(emptyList<SuggestionCandidate>(), engine.rank(emptyList(), "hello", null))
    }
}
