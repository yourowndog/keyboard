package dev.patrickgold.florisboard.ime.nlp.shared

import dev.patrickgold.florisboard.ime.core.KeyboardLayout

/**
 * Conservative recovery for a single missing space, covering both ways one goes
 * missing: omitted entirely (`inthe` -> `in the`) and *replaced* by a letter
 * (`whatnis` -> `what is`, where the spacebar was missed and a neighbouring key
 * registered instead).
 *
 * The substituted case is not a special rule for particular letters. It is
 * licensed by the same spatial model that governs every other fat-finger: the
 * dropped character must be a plausible mis-hit of the spacebar per
 * [KeyboardLayout]. Missing space and mistyped space are one error class.
 */
object WordSegmentation {
    private const val MIN_JOINED_LENGTH = 5
    private const val MAX_JOINED_LENGTH = 24
    private const val MIN_PART_LENGTH = 2

    /**
     * Returns a correction only when exactly one split is supported by both the dictionary
     * and observed bigram data. Ambiguous or merely possible splits are intentionally ignored.
     */
    fun findUniqueHighConfidence(
        input: String,
        isWord: (String) -> Boolean,
        hasBigram: (String, String) -> Boolean,
    ): String? {
        if (input.length !in MIN_JOINED_LENGTH..MAX_JOINED_LENGTH || input.any { !it.isLetter() }) {
            return null
        }

        val normalized = input.lowercase()
        if (isWord(normalized)) return null

        val supported = buildList {
            // The space was omitted: every typed character is kept.
            for (splitAt in MIN_PART_LENGTH..normalized.length - MIN_PART_LENGTH) {
                val left = normalized.substring(0, splitAt)
                val right = normalized.substring(splitAt)
                if (isWord(left) && isWord(right) && hasBigram(left, right)) {
                    add("$left $right")
                }
            }
            // The space was mistyped: one character is dropped, but only where
            // that character is a credible mis-hit of the spacebar.
            for (glueAt in MIN_PART_LENGTH..normalized.length - MIN_PART_LENGTH - 1) {
                if (!KeyboardLayout.isAdjacent(normalized[glueAt], ' ')) continue
                val left = normalized.substring(0, glueAt)
                val right = normalized.substring(glueAt + 1)
                if (right.length < MIN_PART_LENGTH) continue
                if (isWord(left) && isWord(right) && hasBigram(left, right)) {
                    add("$left $right")
                }
            }
        }.distinct()

        // Still exactly one reading, or nothing. Adding a second way for a space
        // to go missing must not become a second way to guess wrong.
        return supported.singleOrNull()
    }

    fun applyCasing(typed: String, segmented: String, isSentenceStart: Boolean): String {
        return when {
            typed.all { !it.isLetter() || it.isUpperCase() } -> segmented.uppercase()
            isSentenceStart || typed.firstOrNull()?.isUpperCase() == true ->
                segmented.replaceFirstChar { it.titlecase() }
            else -> segmented
        }
    }
}
