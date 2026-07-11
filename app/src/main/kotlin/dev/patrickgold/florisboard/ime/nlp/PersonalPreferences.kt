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
    /**
     * Personal vocabulary: words Sam types intentionally that should NEVER be corrected.
     * Checked case-insensitively — one lowercase entry covers all capitalizations.
     * Add abbreviations, slang, and shorthand here instead of anti-corrections.
     */
    val PERSONAL_VOCAB = setOf(
        // Abbreviations
        "bc", "rn", "tf", "lmk", "ppl", "msg", "thx", "sry", "btw",
        "imo", "idk", "omg", "wtf", "smh", "ngl", "tbh", "fr", "wdym",
        "bday", "pls", "llms", "cs", "gen", "config", "ai",
        // Slang / informal
        // NOTE: "im" must NOT go here — it suppresses the im -> I'm contraction
        // fast-path, and the word then auto-corrects to "Important" downstream.
        "ya", "def", "tho", "nah", "ugh", "oof", "bruh", "cuz",
        "g", "i",
    )

    fun isPersonalVocab(typed: String): Boolean {
        return PERSONAL_VOCAB.contains(typed.lowercase())
    }

    /**
     * Anti-corrections: block specific wrong suggestions for genuine typos and real words.
     * Use this for: typos with bad suggestions, real words being mutated, proper nouns.
     * Use PERSONAL_VOCAB instead for: abbreviations and slang that should never be corrected at all.
     */
    val ANTI_CORRECTIONS = mapOf(
        // Real words with bad suggestions
        "sams" to listOf("samson", "samoa"),
        "min" to listOf("mine", "mini", "mind"),
        "Hurray" to listOf("Hurrah"),
        "uh" to listOf("uhuru", "u"),
        "id" to listOf("i'd", "I'd"),
        "s" to listOf("so", "see"),
        "Hows" to listOf("How"),
        "minecraft" to listOf("mineshaft"),
        "hesd" to listOf("he'd"),
        "d" to listOf("don't"),
        "snd" to listOf("and"),
        "t" to listOf("to"),
        "gor" to listOf("gore"),
        "snf" to listOf("sncf"),
        "ir" to listOf("iron"),
        "Congrats" to listOf("Contrast"),
        "domething" to listOf("something"),
        // "were" -> we're and "its" -> it's removed: handled context-aware by
        // CasingUtils.resolveContextualContraction (blanket blocks were duct tape
        // against the old blind shortcut map).
        "Kaylyn" to listOf("Kayla"),
        "ton" to listOf("top", "to"),
        "Quora" to listOf("Quota"),
        "Uh" to listOf("U"),
        "un" to listOf("under"),
        "facetime" to listOf("peacetime"),
        "constipated" to listOf("constituted"),
        "gramps" to listOf("tramps"),
        "peeps" to listOf("peep"),
        "caps" to listOf("capsule"),
        "CO" to listOf("COURT", "COULD"),
        // Fat-finger artifacts
        "7o" to listOf("to"),
        // Real words losing to bad suggestions
        "duh" to listOf("oh"),
        "Duh" to listOf("Dug"),
        "dopesick" to listOf("homesick"),
        "Nugs" to listOf("Bugs"),
        "tomcat" to listOf("tosca"),
        "deadhead" to listOf("deadbeat"),
        "junkie" to listOf("junk"),
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
