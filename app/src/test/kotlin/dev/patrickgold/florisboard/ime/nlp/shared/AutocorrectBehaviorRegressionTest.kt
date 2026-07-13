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
        assertTrue(PersonalPreferences.isProtectedFromAutocorrect("rn"))
        assertFalse(PersonalPreferences.isAntiCorrection("bc", "be"))

        assertTrue(PersonalPreferences.isAntiCorrection("sams", "samson"))
        assertFalse(PersonalPreferences.isAntiCorrection("sams", "sample"))
        assertFalse(PersonalPreferences.isProtectedFromAutocorrect("sams"))
    }

    @Test
    fun policyInputsStillReceiveOrdinaryNumericalScores() {
        val protectedScore = CandidateScorer.score("bc", "be", 1.0, null)
        val excludedPairScore = CandidateScorer.score("sams", "samson", 2.0, null)

        assertTrue(protectedScore < Double.MAX_VALUE)
        assertTrue(excludedPairScore < Double.MAX_VALUE)
    }

    @Test
    fun contractionsKeepTheirCurrentShortcutAndContextBehavior() {
        assertEquals("I'm", ContractionRules.SHORTCUTS["im"])
        assertEquals("I'd", ContractionRules.SHORTCUTS["id"])
        assertEquals("where's", ContractionRules.LEGACY_FALLBACK_SHORTCUTS["wheres"])
        assertNull(ContractionRules.LEGACY_FALLBACK_SHORTCUTS["id"])

        assertEquals("we're", ContractionRules.resolveContextual("were", "hello"))
        assertNull(ContractionRules.resolveContextual("were", "they"))
        assertEquals("it's", ContractionRules.resolveContextual("its", "think"))
        assertNull(ContractionRules.resolveContextual("its", "on"))
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
        assertEquals(
            -50.0,
            ContextualEvidence.rankingPenalty("id", "i'd", "and"),
            absoluteTolerance = 0.0001,
        )
        assertTrue(PersonalPreferences.isAntiCorrection("id", "I'd"))
        assertFalse(PersonalPreferences.isProtectedFromAutocorrect("id"))
    }

    // Ranking evidence for the real dictionary rows behind the observed
    // "las -> La's, L's ahead of last" defect: an unlicensed apostrophe
    // candidate must not outrank an ordinary edit candidate.
    @Test
    fun unlicensedApostropheCandidateEarnsNoContractionEvidence() {
        // ln-frequencies from the packaged unified_dictionary.tsv rows.
        val laS = CandidateScorer.score("las", "la's", 1.0, null, frequency = kotlin.math.ln(1483.0))
        val lS = CandidateScorer.score("las", "l's", 1.0, null, frequency = kotlin.math.ln(8211.0))
        val last = CandidateScorer.score("las", "last", 1.0, null, frequency = kotlin.math.ln(361875.0))
        val law = CandidateScorer.score("las", "law", 1.0, null, frequency = kotlin.math.ln(294082.0))

        assertTrue(last < laS, "last must outrank La's (last=$last laS=$laS)")
        assertTrue(last < lS, "last must outrank L's (last=$last lS=$lS)")
        assertTrue(law < laS, "law must outrank La's (law=$law laS=$laS)")
    }

    @Test
    fun licensedContractionKeepsItsApostropheEvidence() {
        val licensed = CandidateScorer.score("dont", "don't", 1.0, null, frequency = 10.0)
        val plain = CandidateScorer.score("dont", "dot", 1.0, null, frequency = 10.0)
        assertTrue(licensed < plain, "don't must keep its contraction bonus over dot")
        assertTrue("la's" !in ContractionRules.LICENSED_FORMS)
        assertTrue("don't" in ContractionRules.LICENSED_FORMS)
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
