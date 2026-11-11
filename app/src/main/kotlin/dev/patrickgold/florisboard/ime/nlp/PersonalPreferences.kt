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
    /** Built-in protection plus additive forms loaded from protected_forms.txt. */
    @Volatile
    var PERSONAL_VOCAB = ProtectedVocabulary.BUILT_IN_FORMS

    fun init(context: android.content.Context) {
        try {
            val newVocab = PERSONAL_VOCAB.toMutableSet()
            context.assets.open("ime/dict/protected_forms.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val word = line.trim().lowercase()
                    if (word.isNotEmpty()) {
                        newVocab.add(word)
                    }
                }
            }
            PERSONAL_VOCAB = newVocab
        } catch (e: Exception) {
            android.util.Log.e("PersonalPreferences", "Failed to load protected_forms.txt: ${e.message}")
        }
    }

    fun isPersonalVocab(typed: String): Boolean {
        return PERSONAL_VOCAB.contains(typed.lowercase())
    }

    fun isProtectedFromAutocorrect(word: String): Boolean {
        return isPersonalVocab(word)
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
        // "were" -> we're and "its" -> it's are intentionally absent. Left
        // context cannot safely disambiguate them, so valid-word immunity keeps
        // the literal text while the normal suggestion path remains available.
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
