package dev.patrickgold.florisboard.ime.nlp.shared

/** Where a candidate entered the correction pipeline. */
enum class CandidateProvenance {
    EDIT_DISTANCE,
    PREFIX_COMPLETION,
    CONTRACTION_RULE,
    SEGMENTATION,
    CASING_RULE,

    /**
     * The legacy SymSpell-only path currently loses per-candidate provenance.
     * Keeping that limitation explicit is safer than pretending fallback has
     * the same evidence as primary retrieval.
     */
    LEGACY_FALLBACK,
}

/** What the active lexical source established about the typed token. */
enum class TypedLexicalStatus {
    KNOWN_WORD,
    NOT_KNOWN_WORD,
    UNKNOWN,
}

enum class NeuralBypassReason {
    CASING_FAST_PATH,
    LICENSED_CONTRACTION_FAST_PATH,
    ENGINE_UNAVAILABLE,
}

/** Explicit neural state; no caller has to overload a null verdict. */
sealed interface NeuralEvidence {
    data object Disabled : NeuralEvidence
    data object UnsupportedCandidate : NeuralEvidence
    data class Bypassed(val reason: NeuralBypassReason) : NeuralEvidence
    data class Evaluated(val verdict: CommitPolicy.NeuralVerdict) : NeuralEvidence
}

/** Facts shared by every candidate produced for one typed token. */
data class CommitRequestEvidence(
    val typed: String,
    val typedLexicalStatus: TypedLexicalStatus,
    val typedIsProtectedVocab: Boolean,
    val neuralEvidence: NeuralEvidence,
)

/** Candidate-specific facts that must remain honest across retrieval paths. */
data class CommitCandidateEvidence(
    val raw: String,
    val cased: String,
    val provenance: CandidateProvenance,
    val isBlockedCorrection: Boolean,
    val contractionLicense: ContractionLicense? = null,
)

/**
 * Production translation from pipeline evidence into the low-level commit Gate.
 * Provider orchestration and assembled tests use this seam rather than
 * independently reconstructing [CommitPolicy.Input].
 */
object CorrectionDecision {
    data class Verdict(
        val blockers: List<CommitPolicy.Blocker>,
        val candidateProvenance: CandidateProvenance,
        val neuralEvidence: NeuralEvidence,
    ) {
        val shouldCommit: Boolean get() = blockers.isEmpty()
    }

    fun evaluate(
        request: CommitRequestEvidence,
        candidate: CommitCandidateEvidence,
    ): Verdict {
        val hasCorrectionProvenance = when (candidate.provenance) {
            CandidateProvenance.EDIT_DISTANCE,
            CandidateProvenance.SEGMENTATION,
            CandidateProvenance.LEGACY_FALLBACK -> true

            CandidateProvenance.PREFIX_COMPLETION,
            CandidateProvenance.CONTRACTION_RULE,
            CandidateProvenance.CASING_RULE -> false
        }
        val neuralVerdict = when (val neural = request.neuralEvidence) {
            NeuralEvidence.Disabled,
            is NeuralEvidence.Bypassed -> null

            NeuralEvidence.UnsupportedCandidate -> CommitPolicy.NeuralVerdict(
                shouldFire = false,
                topTerm = candidate.raw,
            )

            is NeuralEvidence.Evaluated -> neural.verdict
        }
        val isLicensedContraction = candidate.cased.equals(candidate.raw, ignoreCase = true) &&
            ContractionRules.isValidLicense(
                typed = request.typed,
                rawCandidate = candidate.raw,
                provenance = candidate.provenance,
                license = candidate.contractionLicense,
            )
        val hasInvalidContractionLicense = candidate.contractionLicense != null &&
            !isLicensedContraction
        val input = CommitPolicy.Input(
            typed = request.typed,
            casedCandidate = candidate.cased,
            rawCandidate = candidate.raw,
            typedIsValidWord = request.typedLexicalStatus == TypedLexicalStatus.KNOWN_WORD,
            isEditDistanceCandidate = hasCorrectionProvenance,
            isBlockedCorrection = candidate.isBlockedCorrection,
            typedIsProtectedVocab = request.typedIsProtectedVocab,
            neuralVerdict = neuralVerdict,
            isLicensedContraction = isLicensedContraction,
            hasInvalidContractionLicense = hasInvalidContractionLicense,
        )
        return Verdict(
            blockers = CommitPolicy.blockers(input),
            candidateProvenance = candidate.provenance,
            neuralEvidence = request.neuralEvidence,
        )
    }
}
