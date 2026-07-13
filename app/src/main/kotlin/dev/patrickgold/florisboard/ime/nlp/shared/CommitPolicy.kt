package dev.patrickgold.florisboard.ime.nlp.shared

/**
 * The Gate of the correction pipeline: the single authority on whether the
 * top-ranked candidate may alter the user's typed text.
 *
 * The Retriever produces candidates, the Judge (NgramSuggestionEngine.rank)
 * only orders them, the Caser formats — and this policy alone decides commits.
 * Pure and dependency-free: derived clauses (change, casing fix, length) are
 * computed here; external facts (dictionary validity, personal-vocab
 * protection, blocked corrections, neural verdict) arrive as inputs so every
 * clause is unit-testable without Android or singletons.
 */
object CommitPolicy {

    /** Neural gate verdict, decoupled from NeuralScorer's types. */
    data class NeuralVerdict(
        val shouldFire: Boolean,
        /** The candidate the neural gate considers the intended correction. */
        val topTerm: String,
    )

    data class Input(
        /** Raw composing text as typed (trimmed). */
        val typed: String,
        /** Candidate text after the Caser applied casing. */
        val casedCandidate: String,
        /** Candidate text as ranked, before casing. */
        val rawCandidate: String,
        /** Typed word already exists in the dictionary (Valid Word Immunity). */
        val typedIsValidWord: Boolean,
        /** Candidate came from edit-distance retrieval; prefix-only completions never commit. */
        val isEditDistanceCandidate: Boolean,
        /** ANTI_CORRECTIONS: user has explicitly blocked typed→candidate. */
        val isBlockedCorrection: Boolean,
        /** PERSONAL_VOCAB: typed word must never be autocorrected. */
        val typedIsProtectedVocab: Boolean,
        /** Null when the neural scorer is disabled — the gate then defers to heuristics. */
        val neuralVerdict: NeuralVerdict?,
    )

    /** Everything that can veto a commit, in evaluation order. */
    enum class Blocker {
        /** Candidate is byte-identical to the typed text — nothing to commit. */
        NO_CHANGE,
        /** Typed word is valid, and the candidate is a different word (not a casing fix). */
        VALID_WORD_IMMUNITY,
        /** Neural gate is live and either declined to fire or backs a different candidate. */
        NEURAL_VETO,
        /** User explicitly blocked this correction (ANTI_CORRECTIONS). */
        ANTI_CORRECTION,
        /** Typed word is protected personal vocabulary. */
        PROTECTED_VOCAB,
        /** Tokens containing digits are identifiers/data, not prose to rewrite. */
        NUMERIC_TOKEN,
        /** Single letters are deliberate; only casing fixes may touch them. */
        TOO_SHORT,
        /** Prefix-only completion — a prediction, not a correction. */
        NOT_A_CORRECTION,
    }

    fun shouldCommit(input: Input): Boolean = blockers(input).isEmpty()

    /**
     * Full verdict: empty list means commit. Order matches clause evaluation,
     * so the first entry is the primary reason a commit was withheld
     * ("right order but no auto-commit = Gate bug" starts here).
     */
    fun blockers(input: Input): List<Blocker> {
        val isChange = input.casedCandidate != input.typed
        val isCasingFix = input.casedCandidate.equals(input.typed, ignoreCase = true)
        val neuralAllows = input.neuralVerdict == null ||
            (input.neuralVerdict.shouldFire &&
                input.rawCandidate.equals(input.neuralVerdict.topTerm, ignoreCase = true))

        return buildList {
            if (!isChange) add(Blocker.NO_CHANGE)
            if (input.typedIsValidWord && !isCasingFix) add(Blocker.VALID_WORD_IMMUNITY)
            if (!neuralAllows) add(Blocker.NEURAL_VETO)
            if (input.isBlockedCorrection) add(Blocker.ANTI_CORRECTION)
            if (input.typedIsProtectedVocab) add(Blocker.PROTECTED_VOCAB)
            if (isChange && input.typed.any { it.isDigit() }) add(Blocker.NUMERIC_TOKEN)
            if (input.typed.length < 2 && !isCasingFix) add(Blocker.TOO_SHORT)
            if (!input.isEditDistanceCandidate && !isCasingFix) add(Blocker.NOT_A_CORRECTION)
        }
    }
}
