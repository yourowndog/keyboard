/*
 * PersonalPreferences - Sam-specific keyboard customization
 * 
 * Contains anti-corrections, personal frequency boosts, and app-specific scoring.
 * These are hard-coded preferences that override generic dictionary/scoring logic.
 */
package dev.patrickgold.florisboard.ime.nlp

object PersonalPreferences {
    /**
     * Anti-corrections: Never suggest these correction pairs.
     * 
     * Format: Map<typed_word, list_of_corrections_to_never_suggest>
     * 
     * Example: "sams" -> listOf("samson") means if user types "sams",
     * never suggest "samson" as a correction.
     * 
     * Added when a correction is rejected 5+ times in harvest analysis.
     */
    val ANTI_CORRECTIONS = mapOf(
        "sams" to listOf("samson", "samoa"),
        "min" to listOf("mine", "mini", "mind"),
        "Hurray" to listOf("Hurrah"),
        "uh" to listOf("uhuru"),        // 2x rejected - common hesitation
        "bc" to listOf("by", "bye"),    // 4x rejected - abbreviation for "because"
        "pls" to listOf("Plays", "plus"),  // Common abbrev for "please"
        "oof" to listOf("Ok", "of"),    // Exclamation
        "id" to listOf("i'd", "I'd"),
        "s" to listOf("so", "see"),
        "im" to listOf("I'm"),
        "i" to listOf("I"),
        "Ya" to listOf("Yale"),
        "Hows" to listOf("How"),
        "minecraft" to listOf("mineshaft"),
        "hesd" to listOf("he'd"),
        "d" to listOf("don't"),
        "ai" to listOf("Ain't"),
        "snd" to listOf("and"),
        "t" to listOf("to"),
        "gor" to listOf("gore"),
        "snf" to listOf("sncf"),
        "ir" to listOf("iron"),
        "Congrats" to listOf("Contrast"),
        "domething" to listOf("something"),
    )
    
    /**
     * Check if a given correction is forbidden.
     * 
     * @param typed The word the user typed
     * @param candidate The suggested correction
     * @return True if this correction should be blocked, false otherwise
     */
    fun isAntiCorrection(typed: String, candidate: String): Boolean {
        val lowercaseTyped = typed.lowercase()
        val lowercaseCandidate = candidate.lowercase()
        
        return ANTI_CORRECTIONS[typed]?.contains(candidate) == true ||
                ANTI_CORRECTIONS[lowercaseTyped]?.contains(lowercaseCandidate) == true
    }
}
