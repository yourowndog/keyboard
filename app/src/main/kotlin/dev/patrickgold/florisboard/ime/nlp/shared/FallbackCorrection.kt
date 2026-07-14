package dev.patrickgold.florisboard.ime.nlp.shared

/**
 * Which degraded retrieval engine produced a fallback candidate. Kept explicit
 * (rather than a bare boolean) so diagnostics can distinguish fallback modes if
 * more are ever added, and so callers can never silently confuse a fallback
 * candidate with a primary-engine one.
 */
enum class FallbackEngineMode {
    /** SymSpell dictionary lookup only; the neural/ngram engine was unavailable. */
    SYMSPELL_ONLY,
}

/**
 * Retrieval-time evidence for a single candidate produced by the SymSpell-only
 * fallback path. Replaces the previous string-only boundary that discarded the
 * source of each candidate. Carries only truthful facts established during
 * retrieval; the Gate decision is made later by [FallbackCorrection.resolve].
 */
data class FallbackCandidate(
    /** Candidate text as retrieved, before request-side casing adjustments. */
    val rawCandidate: String,
    /** Candidate text after the fallback path applied its casing pattern. */
    val casedCandidate: String,
    /** Truthful provenance: EDIT_DISTANCE, CONTRACTION_RULE, or LEGACY_FALLBACK (verbatim). */
    val provenance: CandidateProvenance,
    /** Real edit distance for EDIT_DISTANCE candidates; null when not an edit correction. */
    val editDistance: Double?,
    /** Which fallback engine produced this candidate. */
    val engineMode: FallbackEngineMode,
    /** Exact static contraction license, when the candidate is a licensed contraction. */
    val contractionLicense: ContractionLicense? = null,
)

/**
 * Assembles fallback evidence and routes it through the shared [CorrectionDecision]
 * Gate — the same boundary the primary path and [ShortcutCorrection] use. Keeping
 * this pure lets tests exercise the exact production seam without Android singletons.
 *
 * Fallback never fabricates policy inputs: neural scoring is recorded as
 * [NeuralEvidence.Bypassed] with [NeuralBypassReason.ENGINE_UNAVAILABLE] (bypassed
 * because the engine is unavailable — not evaluated or approved), and a candidate
 * with no correction provenance cannot auto-commit on ranking alone.
 */
object FallbackCorrection {
    data class Outcome(
        val candidate: FallbackCandidate,
        val isBlockedCorrection: Boolean,
        val decision: CorrectionDecision.Verdict,
    )

    fun resolve(
        typed: String,
        candidate: FallbackCandidate,
        typedLexicalStatus: TypedLexicalStatus,
        typedIsProtectedVocab: Boolean,
        isBlockedCorrection: Boolean,
    ): Outcome {
        val decision = CorrectionDecision.evaluate(
            request = CommitRequestEvidence(
                typed = typed,
                typedLexicalStatus = typedLexicalStatus,
                typedIsProtectedVocab = typedIsProtectedVocab,
                // The engine is genuinely unavailable; record the bypass truthfully
                // rather than inventing an evaluated verdict.
                neuralEvidence = NeuralEvidence.Bypassed(NeuralBypassReason.ENGINE_UNAVAILABLE),
            ),
            candidate = CommitCandidateEvidence(
                raw = candidate.rawCandidate,
                cased = candidate.casedCandidate,
                provenance = candidate.provenance,
                isBlockedCorrection = isBlockedCorrection,
                contractionLicense = candidate.contractionLicense,
            ),
        )
        return Outcome(
            candidate = candidate,
            isBlockedCorrection = isBlockedCorrection,
            decision = decision,
        )
    }
}
