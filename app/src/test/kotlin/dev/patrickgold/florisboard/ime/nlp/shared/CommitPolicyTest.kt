package dev.patrickgold.florisboard.ime.nlp.shared

import dev.patrickgold.florisboard.ime.nlp.shared.CommitPolicy.Blocker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommitPolicyTest {

    /** A plain typo correction that every clause should wave through. */
    private fun baseInput() = CommitPolicy.Input(
        typed = "teh",
        casedCandidate = "the",
        rawCandidate = "the",
        typedIsValidWord = false,
        isEditDistanceCandidate = true,
        isBlockedCorrection = false,
        typedIsProtectedVocab = false,
        neuralVerdict = null,
    )

    @Test
    fun plainTypoCorrectionCommits() {
        assertTrue(CommitPolicy.shouldCommit(baseInput()))
    }

    @Test
    fun identicalCandidateNeverCommits() {
        val input = baseInput().copy(typed = "the", casedCandidate = "the", rawCandidate = "the")
        assertEquals(listOf(Blocker.NO_CHANGE), CommitPolicy.blockers(input).take(1))
    }

    // "Valid Word Immunity": don't change "baby" -> "Babylon".
    @Test
    fun validTypedWordBlocksDifferentWordCommit() {
        val input = baseInput().copy(
            typed = "baby",
            casedCandidate = "Babylon",
            rawCandidate = "babylon",
            typedIsValidWord = true,
        )
        assertTrue(Blocker.VALID_WORD_IMMUNITY in CommitPolicy.blockers(input))
    }

    // Casing fix is exempt from valid-word immunity: "english" -> "English".
    @Test
    fun casingFixCommitsEvenWhenTypedWordIsValid() {
        val input = baseInput().copy(
            typed = "english",
            casedCandidate = "English",
            rawCandidate = "english",
            typedIsValidWord = true,
        )
        assertTrue(CommitPolicy.shouldCommit(input))
    }

    // ANTI_CORRECTIONS: may be shown, must never auto-commit.
    @Test
    fun blockedCorrectionNeverCommits() {
        val input = baseInput().copy(isBlockedCorrection = true)
        assertEquals(listOf(Blocker.ANTI_CORRECTION), CommitPolicy.blockers(input))
    }

    // PERSONAL_VOCAB: protected words are never corrected (e.g. Termux family).
    @Test
    fun protectedVocabNeverCommits() {
        val input = baseInput().copy(
            typed = "termux",
            casedCandidate = "Terms",
            rawCandidate = "terms",
            typedIsProtectedVocab = true,
        )
        assertTrue(Blocker.PROTECTED_VOCAB in CommitPolicy.blockers(input))
    }

    // Single letters committed with space are deliberate ("s" must not become "a").
    @Test
    fun singleCharIsNeverCorrectedToAnotherWord() {
        val input = baseInput().copy(typed = "s", casedCandidate = "a", rawCandidate = "a")
        assertTrue(Blocker.TOO_SHORT in CommitPolicy.blockers(input))
    }

    // ...but a single-char casing fix (i -> I) is allowed through the length clause.
    @Test
    fun singleCharCasingFixCommits() {
        val input = baseInput().copy(typed = "i", casedCandidate = "I", rawCandidate = "i")
        assertTrue(CommitPolicy.shouldCommit(input))
    }

    // A single number-row fat-finger with an adjacent-letter candidate is a
    // typo (5his -> this); the digit veto must not swallow it.
    @Test
    fun numberRowSlipCommits() {
        val input = baseInput().copy(typed = "5his", casedCandidate = "this", rawCandidate = "this")
        assertTrue(CommitPolicy.shouldCommit(input))
    }

    // The same word-shaped token is still blocked when the candidate letter
    // is nowhere near the digit on the keyboard (no fat-finger evidence).
    @Test
    fun digitWithoutAdjacencyEvidenceStaysBlocked() {
        val input = baseInput().copy(typed = "5his", casedCandidate = "whis", rawCandidate = "whis")
        assertTrue(Blocker.NUMERIC_TOKEN in CommitPolicy.blockers(input))
    }

    // A verified static ContractionRules license overrides valid-word immunity (dont -> don't)
    // and stands in for edit-distance provenance the contraction path lacks.
    @Test
    fun licensedContractionCommitsDespiteTypedBeingValidWord() {
        val input = baseInput().copy(
            typed = "dont",
            casedCandidate = "don't",
            rawCandidate = "don't",
            typedIsValidWord = true,
            isEditDistanceCandidate = false,
            isLicensedContraction = true,
        )
        assertTrue(CommitPolicy.shouldCommit(input))
    }

    // Without the license the same evidence stays blocked on both clauses.
    @Test
    fun unlicensedValidWordReplacementStaysBlocked() {
        val input = baseInput().copy(
            typed = "were",
            casedCandidate = "we're",
            rawCandidate = "we're",
            typedIsValidWord = true,
            isEditDistanceCandidate = false,
        )
        assertEquals(
            listOf(Blocker.VALID_WORD_IMMUNITY, Blocker.NOT_A_CORRECTION),
            CommitPolicy.blockers(input),
        )
    }

    // Prefix-only completions are predictions, not corrections (iOS/Gboard behavior).
    @Test
    fun prefixCompletionNeverCommits() {
        val input = baseInput().copy(
            typed = "dur",
            casedCandidate = "during",
            rawCandidate = "during",
            isEditDistanceCandidate = false,
        )
        assertEquals(listOf(Blocker.NOT_A_CORRECTION), CommitPolicy.blockers(input))
    }

    @Test
    fun neuralVetoBlocksWhenGateDeclinesToFire() {
        val input = baseInput().copy(
            neuralVerdict = CommitPolicy.NeuralVerdict(shouldFire = false, topTerm = "the"),
        )
        assertEquals(listOf(Blocker.NEURAL_VETO), CommitPolicy.blockers(input))
    }

    @Test
    fun neuralVetoBlocksWhenGateBacksDifferentCandidate() {
        val input = baseInput().copy(
            neuralVerdict = CommitPolicy.NeuralVerdict(shouldFire = true, topTerm = "then"),
        )
        assertEquals(listOf(Blocker.NEURAL_VETO), CommitPolicy.blockers(input))
    }

    @Test
    fun neuralAgreementCommits() {
        val input = baseInput().copy(
            neuralVerdict = CommitPolicy.NeuralVerdict(shouldFire = true, topTerm = "The"),
        )
        assertTrue(CommitPolicy.shouldCommit(input))
    }

    @Test
    fun invalidContractionLicenseEvidenceAlwaysBlocks() {
        val input = baseInput().copy(hasInvalidContractionLicense = true)
        assertEquals(
            listOf(Blocker.INVALID_CONTRACTION_LICENSE),
            CommitPolicy.blockers(input),
        )
    }

    @Test
    fun neuralComparisonUsesRawCandidateNotCasedText() {
        // The gate's agreement check must compare against the ranked (raw) term,
        // mirroring the original inline predicate.
        val input = baseInput().copy(
            casedCandidate = "The",
            rawCandidate = "the",
            neuralVerdict = CommitPolicy.NeuralVerdict(shouldFire = true, topTerm = "the"),
        )
        assertTrue(CommitPolicy.shouldCommit(input))
    }

    @Test
    fun multipleBlockersReportedInEvaluationOrder() {
        val input = baseInput().copy(
            typed = "baby",
            casedCandidate = "Babylon",
            rawCandidate = "babylon",
            typedIsValidWord = true,
            isBlockedCorrection = true,
            typedIsProtectedVocab = true,
        )
        assertEquals(
            listOf(Blocker.VALID_WORD_IMMUNITY, Blocker.ANTI_CORRECTION, Blocker.PROTECTED_VOCAB),
            CommitPolicy.blockers(input),
        )
        assertFalse(CommitPolicy.shouldCommit(input))
    }
}
