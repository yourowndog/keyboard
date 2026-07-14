package dev.patrickgold.florisboard.ime.nlp.shared

import dev.patrickgold.florisboard.ime.nlp.shared.CommitPolicy.Blocker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorrectionDecisionTest {
    private fun request(
        typed: String = "teh",
        lexicalStatus: TypedLexicalStatus = TypedLexicalStatus.NOT_KNOWN_WORD,
        protected: Boolean = false,
        neural: NeuralEvidence = NeuralEvidence.Disabled,
    ) = CommitRequestEvidence(
        typed = typed,
        typedLexicalStatus = lexicalStatus,
        typedIsProtectedVocab = protected,
        neuralEvidence = neural,
    )

    private fun candidate(
        raw: String = "the",
        cased: String = raw,
        provenance: CandidateProvenance = CandidateProvenance.EDIT_DISTANCE,
        blocked: Boolean = false,
        contractionLicense: ContractionLicense? = null,
    ) = CommitCandidateEvidence(
        raw = raw,
        cased = cased,
        provenance = provenance,
        isBlockedCorrection = blocked,
        contractionLicense = contractionLicense,
    )

    @Test
    fun editCandidatesCommitButPrefixCompletionsDoNot() {
        assertTrue(CorrectionDecision.evaluate(request(), candidate()).shouldCommit)

        val completion = CorrectionDecision.evaluate(
            request = request(typed = "dur"),
            candidate = candidate(
                raw = "during",
                provenance = CandidateProvenance.PREFIX_COMPLETION,
            ),
        )
        assertEquals(listOf(Blocker.NOT_A_CORRECTION), completion.blockers)
    }

    @Test
    fun protectedIdCannotFallThroughToAnOrdinaryEditCommit() {
        val verdict = CorrectionDecision.evaluate(
            request = request(typed = "id", protected = true),
            candidate = candidate(raw = "it"),
        )

        assertEquals(listOf(Blocker.PROTECTED_VOCAB), verdict.blockers)
        assertFalse(verdict.shouldCommit)
    }

    @Test
    fun casingRuleUsesRealPersonalVetoEvidence() {
        val casing = candidate(
            raw = "I",
            cased = "I",
            provenance = CandidateProvenance.CASING_RULE,
        )
        val bypass = NeuralEvidence.Bypassed(NeuralBypassReason.CASING_FAST_PATH)

        assertTrue(CorrectionDecision.evaluate(request(typed = "i", neural = bypass), casing).shouldCommit)
        assertEquals(
            listOf(Blocker.ANTI_CORRECTION),
            CorrectionDecision.evaluate(
                request = request(typed = "i", neural = bypass),
                candidate = casing.copy(isBlockedCorrection = true),
            ).blockers,
        )
        assertEquals(
            listOf(Blocker.PROTECTED_VOCAB),
            CorrectionDecision.evaluate(
                request = request(typed = "i", protected = true, neural = bypass),
                candidate = casing,
            ).blockers,
        )
    }

    @Test
    fun explicitContractionLicenseAuthorizesOnlyTheEvidencedRulePath() {
        val resolution = requireNotNull(ContractionRules.resolveStatic("dont"))
        val typed = request(
            typed = "dont",
            lexicalStatus = TypedLexicalStatus.KNOWN_WORD,
            neural = NeuralEvidence.Bypassed(NeuralBypassReason.LICENSED_CONTRACTION_FAST_PATH),
        )
        val contraction = candidate(
            raw = resolution.candidate,
            provenance = CandidateProvenance.CONTRACTION_RULE,
            contractionLicense = resolution.license,
        )
        assertTrue(CorrectionDecision.evaluate(typed, contraction).shouldCommit)

        val unlicensed = CorrectionDecision.evaluate(
            request = typed,
            candidate = contraction.copy(contractionLicense = null),
        )
        assertEquals(
            listOf(Blocker.VALID_WORD_IMMUNITY, Blocker.NOT_A_CORRECTION),
            unlicensed.blockers,
        )
    }

    @Test
    fun contractionLicenseCannotAuthorizeAnotherPairOrProvenance() {
        val dont = requireNotNull(ContractionRules.resolveStatic("dont"))
        val cant = requireNotNull(ContractionRules.resolveStatic("cant"))
        val typedDont = request(
            typed = "dont",
            lexicalStatus = TypedLexicalStatus.KNOWN_WORD,
            neural = NeuralEvidence.Bypassed(NeuralBypassReason.LICENSED_CONTRACTION_FAST_PATH),
        )
        val licensedDont = candidate(
            raw = dont.candidate,
            provenance = CandidateProvenance.CONTRACTION_RULE,
            contractionLicense = dont.license,
        )
        val expected = listOf(
            Blocker.INVALID_CONTRACTION_LICENSE,
            Blocker.VALID_WORD_IMMUNITY,
            Blocker.NOT_A_CORRECTION,
        )

        assertEquals(
            expected,
            CorrectionDecision.evaluate(
                typedDont,
                licensedDont.copy(provenance = CandidateProvenance.PREFIX_COMPLETION),
            ).blockers,
        )
        assertEquals(
            expected,
            CorrectionDecision.evaluate(
                typedDont,
                licensedDont.copy(raw = "can't", cased = "can't"),
            ).blockers,
        )
        assertEquals(
            expected,
            CorrectionDecision.evaluate(
                request = typedDont.copy(typed = "cant"),
                candidate = licensedDont,
            ).blockers,
        )
        assertEquals(
            expected,
            CorrectionDecision.evaluate(
                typedDont,
                licensedDont.copy(contractionLicense = cant.license),
            ).blockers,
        )
        assertEquals(
            expected,
            CorrectionDecision.evaluate(
                typedDont,
                licensedDont.copy(cased = "do not"),
            ).blockers,
        )
        assertEquals(
            listOf(Blocker.INVALID_CONTRACTION_LICENSE),
            CorrectionDecision.evaluate(
                request = request(),
                candidate = candidate(contractionLicense = dont.license),
            ).blockers,
            "incoherent license evidence rejects an otherwise valid edit candidate",
        )
    }

    @Test
    fun neuralEvaluationStillVetoesLicensedContractions() {
        val resolution = requireNotNull(ContractionRules.resolveStatic("dont"))
        val result = CorrectionDecision.evaluate(
            request = request(
                typed = "dont",
                lexicalStatus = TypedLexicalStatus.KNOWN_WORD,
                neural = NeuralEvidence.Evaluated(
                    CommitPolicy.NeuralVerdict(shouldFire = false, topTerm = resolution.candidate),
                ),
            ),
            candidate = candidate(
                raw = resolution.candidate,
                provenance = CandidateProvenance.CONTRACTION_RULE,
                contractionLicense = resolution.license,
            ),
        )
        assertEquals(listOf(Blocker.NEURAL_VETO), result.blockers)
    }

    @Test
    fun unsupportedSegmentationIsSuggestionOnlyWhenNeuralGateIsLive() {
        val segmented = candidate(
            raw = "in the",
            provenance = CandidateProvenance.SEGMENTATION,
        )
        assertTrue(CorrectionDecision.evaluate(request(typed = "inthe"), segmented).shouldCommit)

        val liveGate = CorrectionDecision.evaluate(
            request = request(typed = "inthe", neural = NeuralEvidence.UnsupportedCandidate),
            candidate = segmented,
        )
        assertEquals(listOf(Blocker.NEURAL_VETO), liveGate.blockers)
    }

    @Test
    fun fallbackLimitationIsExplicitButPreservesCurrentPlainTypoDecision() {
        val fallback = candidate(provenance = CandidateProvenance.LEGACY_FALLBACK)
        val result = CorrectionDecision.evaluate(
            request = request(
                neural = NeuralEvidence.Bypassed(NeuralBypassReason.ENGINE_UNAVAILABLE),
            ),
            candidate = fallback,
        )
        assertTrue(result.shouldCommit)
        assertFalse(Blocker.NOT_A_CORRECTION in result.blockers)
        assertEquals(CandidateProvenance.LEGACY_FALLBACK, result.candidateProvenance)
        assertEquals(
            NeuralEvidence.Bypassed(NeuralBypassReason.ENGINE_UNAVAILABLE),
            result.neuralEvidence,
        )
    }

    @Test
    fun primaryAndFallbackPlainTyposHavePolicyParity() {
        val primary = CorrectionDecision.evaluate(
            request = request(),
            candidate = candidate(provenance = CandidateProvenance.EDIT_DISTANCE),
        )
        val fallback = CorrectionDecision.evaluate(
            request = request(
                neural = NeuralEvidence.Bypassed(NeuralBypassReason.ENGINE_UNAVAILABLE),
            ),
            candidate = candidate(provenance = CandidateProvenance.LEGACY_FALLBACK),
        )

        assertEquals(primary.blockers, fallback.blockers)
        assertEquals(primary.shouldCommit, fallback.shouldCommit)
    }
}
