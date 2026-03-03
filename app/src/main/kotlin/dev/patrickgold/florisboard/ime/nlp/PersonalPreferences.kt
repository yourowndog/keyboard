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
        "bc" to listOf("by", "bye", "be"), // abbreviation for "because"
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
        // 2026-03-02 harvest
        "rn" to listOf("rnase"),        // 5x rejected - "right now" abbrev
        "Bc" to listOf("Be"),           // 4x rejected - capitalized "because"
        "Ugh" to listOf("Up"),          // 3x rejected - exclamation
        "were" to listOf("we're"),      // 3x rejected - past tense is valid
        "Kaylyn" to listOf("Kayla"),    // 3x rejected - proper name
        "ton" to listOf("top", "to"),   // 2x rejected - real word, not in dict
        "def" to listOf("be"),          // 2x rejected - "definitely" abbrev
        "Quora" to listOf("Quota"),     // 2x rejected - proper noun
        "tf" to listOf("to"),           // 3x rejected - slang
        "ppl" to listOf("pop"),         // 2x rejected - "people" abbrev
        "Lmk" to listOf("Ok"),          // 2x rejected - "let me know"
        "Uh" to listOf("U"),            // 2x rejected - hesitation word
        "un" to listOf("under"),        // 3x rejected - prefix/abbrev
        "Thx" to listOf("The"),         // "thanks" abbreviation
        "bday" to listOf("by"),         // "birthday" abbreviation
        "Wdym" to listOf("Way"),        // "what do you mean"
        "tho" to listOf("those"),       // slang for "though"
        "Nah" to listOf("Naha"),        // common slang
        "facetime" to listOf("peacetime"), // product name
        "constipated" to listOf("constituted"), // real word
        "gramps" to listOf("tramps"),   // real word
        "peeps" to listOf("peep"),      // plural form
        "msg" to listOf("is"),          // "message" abbreviation
        "sry" to listOf("or"),          // "sorry" abbreviation
        "llms" to listOf("alms"),       // tech term (LLMs)
        "Ppl" to listOf("Pop"),         // capitalized "people"
        "caps" to listOf("capsule"),    // real word
        "CO" to listOf("COURT"),        // state abbreviation
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
