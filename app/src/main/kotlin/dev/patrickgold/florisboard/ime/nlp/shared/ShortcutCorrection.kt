package dev.patrickgold.florisboard.ime.nlp.shared

/**
 * Assembles production evidence for deterministic casing and contraction
 * shortcuts. Keeping this pure lets tests exercise the same seam as the
 * provider instead of recreating CommitPolicy booleans.
 */
object ShortcutCorrection {
    data class Outcome(
        val rawCandidate: String,
        val casedCandidate: String,
        val provenance: CandidateProvenance,
        val isBlockedCorrection: Boolean,
        val contractionLicense: ContractionLicense?,
        val decision: CorrectionDecision.Verdict,
    )

    fun resolve(
        typed: String,
        isSentenceStart: Boolean,
        typedLexicalStatus: TypedLexicalStatus,
        typedIsProtectedVocab: Boolean,
        isBlockedCorrection: (String) -> Boolean,
    ): Outcome? {
        val isLoneI = typed == "i"
        val staticResolution = if (isLoneI) null else ContractionRules.resolveStatic(typed)
        val rawCandidate = if (isLoneI) {
            "I"
        } else {
            staticResolution?.candidate ?: return null
        }
        val contractionLicense = staticResolution?.license
        val provenance = if (isLoneI) {
            CandidateProvenance.CASING_RULE
        } else {
            CandidateProvenance.CONTRACTION_RULE
        }
        val casedCandidate = if (isLoneI) {
            rawCandidate
        } else {
            val matched = CasingUtils.matchCasingPattern(typed, rawCandidate)
            if (isSentenceStart) {
                matched.replaceFirstChar { it.titlecase() }
            } else {
                matched
            }
        }
        val blocked = isBlockedCorrection(rawCandidate)
        val neuralBypass = if (isLoneI) {
            NeuralBypassReason.CASING_FAST_PATH
        } else {
            NeuralBypassReason.LICENSED_CONTRACTION_FAST_PATH
        }
        val decision = CorrectionDecision.evaluate(
            request = CommitRequestEvidence(
                typed = typed,
                typedLexicalStatus = typedLexicalStatus,
                typedIsProtectedVocab = typedIsProtectedVocab,
                neuralEvidence = NeuralEvidence.Bypassed(neuralBypass),
            ),
            candidate = CommitCandidateEvidence(
                raw = rawCandidate,
                cased = casedCandidate,
                provenance = provenance,
                isBlockedCorrection = blocked,
                contractionLicense = contractionLicense,
            ),
        )
        return Outcome(
            rawCandidate = rawCandidate,
            casedCandidate = casedCandidate,
            provenance = provenance,
            isBlockedCorrection = blocked,
            contractionLicense = contractionLicense,
            decision = decision,
        )
    }
}
