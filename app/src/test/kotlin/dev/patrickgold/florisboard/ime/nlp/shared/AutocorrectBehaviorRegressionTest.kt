package dev.patrickgold.florisboard.ime.nlp.shared

import dev.patrickgold.florisboard.ime.nlp.PersonalPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Locks the ranking and shortcut behavior that the scorer cleanup must preserve. */
class AutocorrectBehaviorRegressionTest {
    @Test
    fun protectedVocabularyAndAntiCorrectionsRemainDistinct() {
        assertTrue(PersonalPreferences.isProtectedFromAutocorrect("bc"))
        assertFalse(PersonalPreferences.isAntiCorrection("bc", "be"))

        assertTrue(PersonalPreferences.isAntiCorrection("sams", "samson"))
        assertFalse(PersonalPreferences.isAntiCorrection("sams", "sample"))
        assertFalse(PersonalPreferences.isProtectedFromAutocorrect("sams"))
    }

    @Test
    fun contractionsKeepTheirCurrentShortcutAndContextBehavior() {
        assertEquals("I'm", CasingUtils.CONTRACTION_SHORTCUTS["im"])
        assertEquals("I'd", CasingUtils.CONTRACTION_SHORTCUTS["id"])

        assertEquals("we're", CasingUtils.resolveContextualContraction("were", "hello"))
        assertNull(CasingUtils.resolveContextualContraction("were", "they"))
        assertEquals("it's", CasingUtils.resolveContextualContraction("its", "think"))
        assertNull(CasingUtils.resolveContextualContraction("its", "on"))
    }

    @Test
    fun idToIsKeepsItsCurrentSoftContextPreference() {
        val neutral = CandidateScorer.score(
            typed = "id",
            candidate = "is",
            editDistance = 1.0,
            prevWord = "maybe",
        )
        val preferred = CandidateScorer.score(
            typed = "id",
            candidate = "is",
            editDistance = 1.0,
            prevWord = "this",
        )

        assertEquals(neutral - 50.0, preferred, absoluteTolerance = 0.0001)
    }

    @Test
    fun idToIdContractionRemainsAnExplicitPairExclusion() {
        assertTrue(PersonalPreferences.isAntiCorrection("id", "I'd"))
        assertFalse(PersonalPreferences.isProtectedFromAutocorrect("id"))
    }

    @Test
    fun contractionAfterDeterminerKeepsItsSoftGrammarPenalty() {
        val neutral = CandidateScorer.score(
            typed = "im",
            candidate = "i'm",
            editDistance = 0.0,
            prevWord = "and",
            frequency = 10.0,
        )
        val penalized = CandidateScorer.score(
            typed = "im",
            candidate = "i'm",
            editDistance = 0.0,
            prevWord = "my",
            frequency = 10.0,
        )

        assertEquals(neutral + 50.0, penalized, absoluteTolerance = 0.0001)
    }
}
