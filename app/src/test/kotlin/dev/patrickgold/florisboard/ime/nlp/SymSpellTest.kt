package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.ime.nlp.shared.CandidateScorer
import dev.patrickgold.florisboard.ime.nlp.shared.DictionaryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SymSpellTest {
    @Test
    fun testCandidateScorer() {
        val score = CandidateScorer.score("tbis", "this", 1.0, null)
        println("Score for tbis -> this: $score")
    }

    @Test
    fun testProtectedOnlyForm() {
        val protectedWord = "hahaha"
        val originalVocab = PersonalPreferences.PERSONAL_VOCAB
        
        try {
            // 1. Manually add to PERSONAL_VOCAB to simulate runtime load
            PersonalPreferences.PERSONAL_VOCAB = originalVocab + protectedWord
            
            // 2. Verify it is recognized by the live commit-policy helper:
            val isProtected = PersonalPreferences.isProtectedFromAutocorrect(protectedWord)
            assertTrue(isProtected, "Protected form must be recognized as protected from autocorrect")
            
            // 3. Verify it is absent from DictionaryRepository candidate retrieval
            assertFalse(DictionaryRepository.contains(protectedWord))
            
            val candidates = DictionaryRepository.findWithinTwoEdits("hahah")
            val containsProtected = candidates.any { it.term.equals(protectedWord, ignoreCase = true) }
            assertFalse(containsProtected, "Protected-only form should not be returned as an edit-distance candidate")
        } finally {
            // Restore original state to prevent contamination
            PersonalPreferences.PERSONAL_VOCAB = originalVocab
        }
    }
}
