package dev.patrickgold.florisboard.ime.nlp.shared

/**
 * Single source of truth for text casing logic in the NLP pipeline.
 *
 * ## Responsibilities:
 * - Detect casing patterns (all-caps, title case, lowercase)
 * - Apply casing to suggestions based on user input pattern
 * - Handle special cases: standalone "i", contractions, proper nouns
 * - Detect sentence-start context for auto-capitalization
 *
 * ## Usage:
 * - LatinLanguageProvider calls [applyPredictedCasing] for Smartbar suggestions
 * - SymSpellManager calls [applyPredictedCasing] for autocorrect
 *
 * ## Architecture Role:
 * This is the "Caser" in the Brain Transplant pattern:
 * - SymSpellManager retrieves candidates (the "Retriever")
 * - NgramSuggestionEngine ranks them (the "Judge")
 * - CasingUtils applies final casing (the "Caser")
 */
object CasingUtils {

    /**
     * Contractions that require specific casing (e.g., "im" -> "I'm").
     * Keys should be lowercase without apostrophes.
     */
    val CONTRACTION_SHORTCUTS = mapOf(
        "im" to "I'm",
        "i'm" to "I'm",
        "ive" to "I've",
        "id" to "I'd",
        "ill" to "I'll",
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "wint" to "won't",  // Common typo for "won't"
        "didnt" to "didn't",
        "doesnt" to "doesn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "hasnt" to "hasn't",
        "havent" to "haven't",
        "hadnt" to "hadn't",
        "couldnt" to "couldn't",
        "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't",
        "youre" to "you're",
        "theyre" to "they're",
        "were" to "we're",
        "hes" to "he's",
        "shes" to "she's",
        "its" to "it's",
        "thats" to "that's",
        "whats" to "what's",
        "whos" to "who's",
        "lets" to "let's",
    )

    /**
     * Proper nouns that should always be capitalized.
     * Store in lowercase for lookup, output in title case.
     */
    val PROPER_NOUNS = setOf(
        "sam", "sam's",
        "elijah", "elijah's",
        "kiry", "kiry's",
        "john", "john's",
        "mary", "mary's",
        "david", "david's",
        "sarah", "sarah's",
        "michael", "michael's",
        "emily", "emily's",
        "james", "james's",
        "jennifer", "jennifer's",
        "tony", "tony's",
        "ellie", "ellie's",
        "otis", "otis's",
        "rupert", "rupert's",
        "dan", "dan's",
        "tim", "tim's",
    )

    /**
     * Apply context-aware casing to a suggestion.
     *
     * This is the main entry point for casing logic. It checks:
     * 1. Lone "i" -> "I"
     * 2. Contraction shortcuts (im -> I'm)
     * 3. Sentence start (capitalize first letter)
     * 4. User's casing pattern (ALLCAPS, TitleCase, etc.)
     * 5. Proper nouns
     *
     * @param typed What the user actually typed
     * @param suggestion The raw suggestion (usually lowercase)
     * @param textBeforeSelection Text before the cursor (for context detection)
     * @return The suggestion with appropriate casing applied
     */
    fun applyPredictedCasing(typed: String, suggestion: String, textBeforeSelection: String): String {
        // Special case: lone "i" should always become "I"
        if (suggestion.equals("i", ignoreCase = true)) {
            return "I"
        }

        // Apply contraction shortcuts (im -> I'm, etc.)
        val contractionResult = CONTRACTION_SHORTCUTS[typed.lowercase()]
        if (contractionResult != null && suggestion.replace("'", "").equals(typed, ignoreCase = true)) {
            return contractionResult
        }

        // Check if we're at sentence start
        if (isAtSentenceStart(textBeforeSelection) && typed.firstOrNull()?.isLowerCase() == true) {
            // At sentence start, force capitalize first letter
            val cased = matchCasingPattern(typed, suggestion)
            return if (cased.firstOrNull()?.isLowerCase() == true) {
                cased.replaceFirstChar { it.titlecase() }
            } else {
                cased
            }
        }

        // Otherwise use normal casing rules
        return matchCasingPattern(typed, suggestion)
    }

    /**
     * Match the casing pattern of the original input to the suggestion.
     *
     * Handles:
     * - Lone "i" -> "I"
     * - Proper nouns -> Title Case
     * - ALL CAPS -> ALL CAPS
     * - Title Case -> Title Case
     * - Leading capital -> Preserve it
     *
     * @param original What the user typed
     * @param suggestion The raw suggestion
     * @return Suggestion with casing matching the original
     */
    fun matchCasingPattern(original: String, suggestion: String): String {
        if (original.isEmpty()) return suggestion

        // Handle lone "i"
        if (original.length == 1 && original.equals("i", ignoreCase = true) && suggestion.equals("i", ignoreCase = true)) {
            return "I"
        }

        // Handle proper nouns
        if (suggestion.lowercase() in PROPER_NOUNS) {
            return suggestion.replaceFirstChar { it.titlecase() }
        }

        // Handle ALL CAPS
        if (original.all { it.isUpperCase() }) {
            return suggestion.uppercase()
        }

        // Handle Title Case
        if (original.first().isUpperCase() && original.drop(1).all { it.isLowerCase() }) {
            return suggestion.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        // Preserve leading capital if user started with one
        if (original.first().isUpperCase()) {
            return suggestion.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        return suggestion
    }

    /**
     * Detect if we're at the start of a sentence.
     *
     * A sentence start is detected when:
     * - The text is empty
     * - The text ends with '.', '!', '?', or newline
     *
     * @param text The text before the current word
     * @return true if at sentence start
     */
    fun isAtSentenceStart(text: String): Boolean {
        val trimmed = text.trimEnd()
        return trimmed.isEmpty() ||
               trimmed.endsWith('.') ||
               trimmed.endsWith('!') ||
               trimmed.endsWith('?') ||
               trimmed.endsWith('\n')
    }
}
