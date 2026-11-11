package dev.patrickgold.florisboard.ime.nlp.shared

import dev.patrickgold.florisboard.ime.nlp.shared.CommitPolicy.Blocker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Proves the SymSpell-only fallback preserves truthful per-candidate evidence
 * through the shared Gate instead of fabricating coarse policy inputs.
 */
class FallbackCorrectionTest {
    private fun fallbackCandidate(
        raw: String,
        cased: String = raw,
        provenance: CandidateProvenance = CandidateProvenance.EDIT_DISTANCE,
        editDistance: Double? = 1.0,
        engineMode: FallbackEngineMode = FallbackEngineMode.SYMSPELL_ONLY,
        contractionLicense: ContractionLicense? = null,
    ) = FallbackCandidate(
        rawCandidate = raw,
        casedCandidate = cased,
        provenance = provenance,
        editDistance = editDistance,
        engineMode = engineMode,
        contractionLicense = contractionLicense,
    )

    private fun resolve(
        typed: String,
        candidate: FallbackCandidate,
        lexicalStatus: TypedLexicalStatus = TypedLexicalStatus.NOT_KNOWN_WORD,
        protected: Boolean = false,
        blocked: Boolean = false,
    ) = FallbackCorrection.resolve(
        typed = typed,
        candidate = candidate,
        typedLexicalStatus = lexicalStatus,
        typedIsProtectedVocab = protected,
        isBlockedCorrection = blocked,
    )

    // (1) A fallback edit correction carries edit-correction provenance and its real distance.
    @Test
    fun fallbackEditCorrectionRetainsProvenanceAndRealDistance() {
        val outcome = resolve(
            typed = "teh",
            candidate = fallbackCandidate(raw = "the", provenance = CandidateProvenance.EDIT_DISTANCE, editDistance = 1.0),
        )
        assertEquals(CandidateProvenance.EDIT_DISTANCE, outcome.candidate.provenance)
        assertEquals(1.0, outcome.candidate.editDistance)
        assertEquals(CandidateProvenance.EDIT_DISTANCE, outcome.decision.candidateProvenance)
        assertTrue(outcome.decision.shouldCommit)
    }

    // (2) A candidate without correction provenance cannot auto-commit merely by ranking first.
    @Test
    fun candidateWithoutCorrectionProvenanceCannotAutoCommitAsTopCandidate() {
        val outcome = resolve(
            typed = "teh",
            candidate = fallbackCandidate(raw = "the", provenance = CandidateProvenance.LEGACY_FALLBACK, editDistance = null),
        )
        assertFalse(outcome.decision.shouldCommit)
        assertTrue(Blocker.NOT_A_CORRECTION in outcome.decision.blockers)
    }

    // (3) Numeric and identifier protections remain intact in fallback.
    @Test
    fun numericAndIdentifierTokensStayProtected() {
        // Digit-bearing tokens that are NOT single number-row slips must never be rewritten,
        // even when the fallback offers an edit-distance candidate.
        val cases = listOf(
            "742" to "great",
            "PS2" to "play",
            "v2" to "very",
            "3.14" to "pie",
        )
        for ((typed, cand) in cases) {
            val outcome = resolve(
                typed = typed,
                candidate = fallbackCandidate(raw = cand, provenance = CandidateProvenance.EDIT_DISTANCE, editDistance = 1.0),
            )
            assertTrue(Blocker.NUMERIC_TOKEN in outcome.decision.blockers, "$typed must stay a protected numeric token")
            assertFalse(outcome.decision.shouldCommit, "$typed must not auto-commit")
        }
    }

    // (4) Number-row slips retain their intended behavior where fallback has sufficient evidence.
    @Test
    fun numberRowSlipsCommitWhenFallbackCarriesEditEvidence() {
        val slips = listOf(
            "5his" to "this",
            "sugg3stions" to "suggestions",
            "correc5ed" to "corrected",
        )
        for ((typed, cand) in slips) {
            val outcome = resolve(
                typed = typed,
                candidate = fallbackCandidate(raw = cand, provenance = CandidateProvenance.EDIT_DISTANCE, editDistance = 1.0),
            )
            assertFalse(Blocker.NUMERIC_TOKEN in outcome.decision.blockers, "$typed slip must not be vetoed as numeric")
            assertTrue(outcome.decision.shouldCommit, "$typed should commit with edit evidence")
        }
    }

    // (4b) The same slip WITHOUT correction evidence must not commit — the evidence is what authorizes it.
    @Test
    fun numberRowSlipWithoutEditEvidenceDoesNotCommit() {
        val outcome = resolve(
            typed = "5his",
            candidate = fallbackCandidate(raw = "this", provenance = CandidateProvenance.LEGACY_FALLBACK, editDistance = null),
        )
        assertFalse(outcome.decision.shouldCommit)
        assertTrue(Blocker.NOT_A_CORRECTION in outcome.decision.blockers)
    }

    // (5) Valid words and protected vocabulary remain safe.
    @Test
    fun validWordsAndProtectedVocabStaySafe() {
        val validWord = resolve(
            typed = "cat",
            candidate = fallbackCandidate(raw = "car"),
            lexicalStatus = TypedLexicalStatus.KNOWN_WORD,
        )
        assertTrue(Blocker.VALID_WORD_IMMUNITY in validWord.decision.blockers)
        assertFalse(validWord.decision.shouldCommit)

        val protectedVocab = resolve(
            typed = "teh",
            candidate = fallbackCandidate(raw = "the"),
            protected = true,
        )
        assertTrue(Blocker.PROTECTED_VOCAB in protectedVocab.decision.blockers)
        assertFalse(protectedVocab.decision.shouldCommit)
    }

    // (6) Anti-correction pairs remain authoritative.
    @Test
    fun antiCorrectionPairsRemainAuthoritative() {
        val outcome = resolve(
            typed = "teh",
            candidate = fallbackCandidate(raw = "the"),
            blocked = true,
        )
        assertTrue(outcome.isBlockedCorrection)
        assertTrue(Blocker.ANTI_CORRECTION in outcome.decision.blockers)
        assertFalse(outcome.decision.shouldCommit)
    }

    // (7) Neural state is recorded as bypassed because the engine is unavailable — not evaluated/approved.
    @Test
    fun neuralStateRecordedAsBypassedNotEvaluated() {
        val outcome = resolve(typed = "teh", candidate = fallbackCandidate(raw = "the"))
        assertEquals(
            NeuralEvidence.Bypassed(NeuralBypassReason.ENGINE_UNAVAILABLE),
            outcome.decision.neuralEvidence,
        )
        assertFalse(outcome.decision.neuralEvidence is NeuralEvidence.Evaluated)
        assertFalse(Blocker.NEURAL_VETO in outcome.decision.blockers)
    }

    // (8) Primary and fallback consume the same decision adapter when supplied equivalent evidence.
    @Test
    fun fallbackAndPrimaryShareTheDecisionAdapterForEquivalentEvidence() {
        val fallback = resolve(
            typed = "teh",
            candidate = fallbackCandidate(raw = "the", provenance = CandidateProvenance.EDIT_DISTANCE, editDistance = 1.0),
        )
        val primary = CorrectionDecision.evaluate(
            request = CommitRequestEvidence(
                typed = "teh",
                typedLexicalStatus = TypedLexicalStatus.NOT_KNOWN_WORD,
                typedIsProtectedVocab = false,
                neuralEvidence = NeuralEvidence.Disabled,
            ),
            candidate = CommitCandidateEvidence(
                raw = "the",
                cased = "the",
                provenance = CandidateProvenance.EDIT_DISTANCE,
                isBlockedCorrection = false,
            ),
        )
        assertEquals(primary.blockers, fallback.decision.blockers)
        assertEquals(primary.shouldCommit, fallback.decision.shouldCommit)
        assertTrue(fallback.decision.shouldCommit)
    }

    // (9) Fallback mode remains explicitly distinguishable in returned diagnostics.
    @Test
    fun fallbackModeRemainsDistinguishableInDiagnostics() {
        val outcome = resolve(typed = "teh", candidate = fallbackCandidate(raw = "the"))
        assertEquals(FallbackEngineMode.SYMSPELL_ONLY, outcome.candidate.engineMode)
    }

    // Contraction-license safeguard survives fallback: a licensed static contraction commits,
    // the same shape without a valid license does not.
    @Test
    fun licensedContractionCommitsInFallbackUnlicensedDoesNot() {
        val dont = requireNotNull(ContractionRules.resolveStatic("dont"))
        val licensed = resolve(
            typed = "dont",
            candidate = fallbackCandidate(
                raw = dont.candidate,
                provenance = CandidateProvenance.CONTRACTION_RULE,
                editDistance = null,
                contractionLicense = dont.license,
            ),
            lexicalStatus = TypedLexicalStatus.KNOWN_WORD,
        )
        assertTrue(licensed.decision.shouldCommit)

        val unlicensed = resolve(
            typed = "dont",
            candidate = fallbackCandidate(
                raw = dont.candidate,
                provenance = CandidateProvenance.CONTRACTION_RULE,
                editDistance = null,
                contractionLicense = null,
            ),
            lexicalStatus = TypedLexicalStatus.KNOWN_WORD,
        )
        assertFalse(unlicensed.decision.shouldCommit)
        assertTrue(Blocker.NOT_A_CORRECTION in unlicensed.decision.blockers)
    }
}
