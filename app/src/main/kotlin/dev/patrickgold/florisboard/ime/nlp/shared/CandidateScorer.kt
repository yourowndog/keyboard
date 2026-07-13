/**
 * CandidateScorer - Unified scoring for autocorrect and suggestions.
 * 
 * ## Architecture Role:
 * Single source of truth for candidate ranking, used by NgramSuggestionEngine.rank()
 * (the Judge) and SymSpellManager.suggest() (fallback path).
 * 
 * ## Design for Neural Replacement:
 * - Flat primitives in (strings, doubles) → easy to serialize as features
 * - Single score out → easy to replace with model.predict()
 * - Feature extraction methods are public → can be used to build training data
 * 
 * ## Scoring Convention:
 * LOWER score = BETTER candidate (penalty-based, like edit distance)
 * This matches SymSpell's convention and makes penalties additive.
 */
package dev.patrickgold.florisboard.ime.nlp.shared

import dev.patrickgold.florisboard.ime.core.KeyboardLayout

object CandidateScorer {
    
    // ═══════════════════════════════════════════════════════════════════
    // TUNING CONSTANTS - All the hard-won magic numbers in one place
    // ═══════════════════════════════════════════════════════════════════
    
    /** Weight for bigram context. Higher = more influence from previous word. */
    private const val BIGRAM_WEIGHT = 5.0  // Increased from 0.5 to make bigrams actually matter
    
    /** Penalty when previous word exists but no bigram found. */
    private const val BIGRAM_NO_HIT_PENALTY = 0.2
    
    /** Bonus for exact apostrophe match (im → I'm). Negative = better. */
    private const val APOSTROPHE_EXACT_BONUS = -20.0
    
    /** Bonus for typo + apostrophe (wint → won't). Negative = better. */
    private const val APOSTROPHE_TYPO_BONUS = -10.0

    /**
     * Minimum ln-frequency for a candidate to earn APOSTROPHE_TYPO_BONUS (~4900 raw).
     * Real contractions clear it easily (she'll 7.8k, isn't 87k, it's 1.6M); junk
     * possessives don't (function's 3.2k, thinking's 254, going's 168) — without this
     * gate the bonus made "goin" correct to "going's" instead of "going".
     */
    private const val APOSTROPHE_TYPO_MIN_LOG_FREQ = 8.5
    
    /** Bonus for exact dictionary match (distance=0, spatial=0). Negative = better. */
    private const val EXACT_MATCH_BONUS = -100.0
    
    /** Bonus for user dictionary words. Negative = better. */
    private const val USER_WORD_BONUS = -1000.0
    
    /** Score for culled/filtered candidates. */
    const val CULLED_SCORE = Double.MAX_VALUE
    
    // Spatial cost constants (from existing spatialCost function)
    private const val SPATIAL_NEIGHBOR_COST = 0.5
    private const val SPATIAL_FAR_COST = 2.0
    private const val SPATIAL_TRANSPOSITION_COST = 0.3
    private const val SPATIAL_LENGTH_DIFF_COST = 0.5
    
    // ═══════════════════════════════════════════════════════════════════
    // MAIN SCORING FUNCTION
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Score a candidate word. Lower score = better candidate.
     * 
     * @param typed What the user typed (lowercase)
     * @param candidate The candidate word (lowercase)
     * @param editDistance Basic edit distance from SymSpell lookup
     * @param prevWord Previous word for bigram context (nullable, lowercase)
     * @param isInUserDict Whether candidate is in user's personal dictionary
     * @param frequency Log-frequency of candidate (0.0 if not using frequency)
     * @return Score where lower is better. CULLED_SCORE if candidate should be filtered.
     */
    fun score(
        typed: String,
        candidate: String,
        editDistance: Double,
        prevWord: String?,
        isInUserDict: Boolean = false,
        frequency: Double = 0.0,
    ): Double {
        // PERSONAL VOCAB: Never correct words Sam types intentionally as-is
        if (dev.patrickgold.florisboard.ime.nlp.PersonalPreferences.isPersonalVocab(typed)) {
            return CULLED_SCORE
        }

        // ANTI-CORRECTIONS: Filter out forbidden typed→candidate pairs
        if (dev.patrickgold.florisboard.ime.nlp.PersonalPreferences.isAntiCorrection(typed, candidate)) {
            android.util.Log.d("CandidateScorer", "ANTI-CORRECTION: $typed → $candidate")
            return CULLED_SCORE  // Reject this candidate completely
        }

        // HYBRID CONTEXT-AWARE PENALTY: Grammar rules + bigram validation
        var grammarPenalty = 0.0
        var bigramBlockPenalty = 0.0

        if (prevWord != null) {
            val prevLower = prevWord.lowercase()
            val candLower = candidate.lowercase()

            // Grammatical penalty: Possessives/determiners rarely precede contractions
            val POSSESSIVE_CONTEXTS = setOf("my", "your", "his", "her", "their", "our", "its")
            val DETERMINERS = setOf("the", "this", "that", "these", "those", "a", "an", "some", "any", "each", "every")
            val CONTRACTIONS = setOf("i'm", "i'd", "i'll", "i've", "we're", "we'll", "they're", "you're",
                                      "he's", "she's", "it's", "that's", "what's", "who's", "here's", "there's")

            if ((POSSESSIVE_CONTEXTS.contains(prevLower) || DETERMINERS.contains(prevLower))
                && CONTRACTIONS.contains(candLower)) {
                grammarPenalty = 50.0  // Heavy penalty instead of hard block
            }

            // Bigram validation: If typed word forms stronger bigram than candidate, penalize candidate
            val bigramTable = BigramTable.get()
            if (bigramTable != null) {
                val typedBigramFreq = bigramTable.getFrequency(prevWord, typed)
                val candidateBigramFreq = bigramTable.getFrequency(prevWord, candidate)

                if (typedBigramFreq > 0 && typedBigramFreq >= candidateBigramFreq * 2) {
                    bigramBlockPenalty = 20.0 // Push it down the list, but don't delete it
                }
            }
        }

        val typedNoApos = typed.replace("'", "")
        val candidateNoApos = candidate.replace("'", "")
        
        // Start with edit distance as base penalty
        var score = editDistance + grammarPenalty + bigramBlockPenalty
        
        // Spatial cost: penalize far keys, reward transpositions
        score += spatialCost(typed, candidate)
        
        // Bigram context
        val bigramResult = bigramScore(prevWord, candidate)
        score -= BIGRAM_WEIGHT * bigramResult.bonus
        if (prevWord != null && !bigramResult.hasHit) {
            score += BIGRAM_NO_HIT_PENALTY
        }
        
        // Apostrophe handling
        if (candidate.contains('\'')) {
            if (candidateNoApos == typedNoApos) {
                // Exact letter match (im → I'm)
                score += APOSTROPHE_EXACT_BONUS
            } else {
                // Check if it's a close typo (wint → won't)
                val spatial = spatialCost(typedNoApos, candidateNoApos)
                if (spatial < 2.0 && frequency >= APOSTROPHE_TYPO_MIN_LOG_FREQ) {
                    score += APOSTROPHE_TYPO_BONUS
                }
            }
        }
        
        // Exact match bonus (perfect input that's in dictionary)
        if (editDistance == 0.0 && spatialCost(typed, candidate) == 0.0) {
            score += EXACT_MATCH_BONUS
        }
        
        // User dictionary bonus
        if (isInUserDict) {
            score += USER_WORD_BONUS
        }
        
        // Frequency bonus (convert from log-freq where higher=better to penalty where lower=better)
        // Scale factor keeps frequency influence reasonable relative to other factors
        score -= frequency * 0.1
        
        // CONTEXT-AWARE: "id" → "is" vs "I'd" depending on previous word
        // After pronouns/determiners → prefer "is" (this is, that is, it is)
        // At sentence start or after conjunctions → prefer "I'd" (I'd like, And I'd)
        if (typed.lowercase() == "id" && prevWord != null) {
            val prevLower = prevWord.lowercase()
            val preferIsContext = setOf("this", "that", "it", "he", "she", "what", "which", "who", "there", "here")
            val preferIdContext = setOf("and", "but", "so", "or", "because", "if", "when", "well", "yeah", "yes", "no")
            
            if (candidate.lowercase() == "is" && preferIsContext.contains(prevLower)) {
                score -= 50.0  // Strong bonus for "is" in this context
            } else if (candidate.lowercase() == "i'd" && preferIdContext.contains(prevLower)) {
                score -= 50.0  // Strong bonus for "I'd" in this context
            }
        }
        
        return score
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // FEATURE EXTRACTION (public for training data generation)
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Calculate spatial keyboard cost between typed and candidate.
     * 0.0 = perfect match, higher = worse.
     * 
     * Handles:
     * - Adjacent key misses (0.5)
     * - Far key misses (2.0)
     * - Transpositions like ie→ei (0.3)
     * - Length differences (0.5 per char)
     */
    fun spatialCost(typed: String, candidate: String): Double {
        var cost = 0.0
        val len = kotlin.math.min(typed.length, candidate.length)

        var i = 0
        while (i < len) {
            val t = typed[i]
            val c = candidate[i]
            if (t == c) {
                i++
                continue
            }

            // Check for transposition (adjacent swap like ie → ei)
            if (i + 1 < len && i + 1 < typed.length && i + 1 < candidate.length) {
                val t1 = typed[i + 1]
                val c1 = candidate[i + 1]
                if (t == c1 && t1 == c) {
                    // This is a transposition - penalize lightly
                    cost += SPATIAL_TRANSPOSITION_COST
                    i += 2  // Skip both characters
                    continue
                }
            }

            // Use continuous Euclidean distance (0.0 - 2.0 range)
            // Adjacent keys ≈ 0.5-1.0, same-row-skip ≈ 1.4, cross-keyboard ≈ 2.0
            cost += KeyboardLayout.keyDistance(t, c)
            i++
        }

        // Add penalty for length difference (insertions/deletions)
        val diff = kotlin.math.abs(typed.length - candidate.length)
        cost += diff * SPATIAL_LENGTH_DIFF_COST

        return cost
    }
    
    /**
     * Get bigram score for context-aware ranking.
     * @return BigramResult with bonus (higher=better) and whether a hit was found
     */
    fun bigramScore(prevWord: String?, candidate: String): BigramResult {
        val table = BigramTable.get() ?: return BigramResult(0.0, false)
        val bonus = table.bonus(prevWord, candidate)
        val hasHit = table.hasHit(prevWord, candidate)
        return BigramResult(bonus, hasHit)
    }
    
    data class BigramResult(val bonus: Double, val hasHit: Boolean)
    
    /**
     * Check if candidate is an apostrophe variant of typed word.
     * @return 0 = no match, 1 = exact letters match, 2 = close typo match
     */
    fun apostropheMatchLevel(typed: String, candidate: String): Int {
        if (!candidate.contains('\'')) return 0
        val typedNoApos = typed.replace("'", "")
        val candidateNoApos = candidate.replace("'", "")
        return when {
            candidateNoApos == typedNoApos -> 1 // Exact
            spatialCost(typedNoApos, candidateNoApos) < 2.0 -> 2 // Close typo
            else -> 0
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // UTILITY: Convert score conventions
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * Convert from penalty-based (lower=better) to confidence-based (higher=better).
     * Useful when interfacing with UI that expects confidence scores.
     */
    fun toConfidence(penaltyScore: Double): Double {
        // Invert and shift so typical good scores (around -100 to 0) become positive
        return -penaltyScore
    }
}
