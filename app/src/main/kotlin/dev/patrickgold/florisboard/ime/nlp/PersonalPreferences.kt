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
        "min" to listOf("mine", "mini"),
        "Hurray" to listOf("Hurrah"),
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
