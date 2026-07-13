package dev.patrickgold.florisboard.ime.nlp.shared

/** Soft context signals which adjust ranking without granting commit authority. */
object ContextualEvidence {
    private const val GRAMMAR_PENALTY = 50.0
    private const val BIGRAM_CONFLICT_PENALTY = 20.0
    private const val ID_CONTEXT_BONUS = -50.0

    private val POSSESSIVE_CONTEXTS = setOf("my", "your", "his", "her", "their", "our", "its")
    private val DETERMINERS = setOf(
        "the", "this", "that", "these", "those", "a", "an", "some", "any", "each", "every",
    )
    private val CONTRACTIONS = setOf(
        "i'm", "i'd", "i'll", "i've", "we're", "we'll", "they're", "you're",
        "he's", "she's", "it's", "that's", "what's", "who's", "here's", "there's",
    )
    private val PREFER_IS_CONTEXTS = setOf(
        "this", "that", "it", "he", "she", "what", "which", "who", "there", "here",
    )
    private val PREFER_ID_CONTEXTS = setOf(
        "and", "but", "so", "or", "because", "if", "when", "well", "yeah", "yes", "no",
    )

    /** Returns an additive penalty. Negative values are ranking bonuses. */
    fun rankingPenalty(typed: String, candidate: String, prevWord: String?): Double {
        if (prevWord == null) return 0.0

        val typedLower = typed.lowercase()
        val candidateLower = candidate.lowercase()
        val prevLower = prevWord.lowercase()
        var penalty = 0.0

        if ((prevLower in POSSESSIVE_CONTEXTS || prevLower in DETERMINERS) &&
            candidateLower in CONTRACTIONS
        ) {
            penalty += GRAMMAR_PENALTY
        }

        BigramTable.get()?.let { table ->
            val typedBigramFreq = table.getFrequency(prevWord, typed)
            val candidateBigramFreq = table.getFrequency(prevWord, candidate)
            if (typedBigramFreq > 0 && typedBigramFreq >= candidateBigramFreq * 2) {
                penalty += BIGRAM_CONFLICT_PENALTY
            }
        }

        if (typedLower == "id") {
            if (candidateLower == "is" && prevLower in PREFER_IS_CONTEXTS) {
                penalty += ID_CONTEXT_BONUS
            } else if (candidateLower == "i'd" && prevLower in PREFER_ID_CONTEXTS) {
                penalty += ID_CONTEXT_BONUS
            }
        }

        return penalty
    }
}
