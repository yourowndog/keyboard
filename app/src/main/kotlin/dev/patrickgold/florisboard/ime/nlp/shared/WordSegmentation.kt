package dev.patrickgold.florisboard.ime.nlp.shared

/** Conservative recovery for a single omitted space, such as `inthe` -> `in the`. */
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
            for (splitAt in MIN_PART_LENGTH..normalized.length - MIN_PART_LENGTH) {
                val left = normalized.substring(0, splitAt)
                val right = normalized.substring(splitAt)
                if (isWord(left) && isWord(right) && hasBigram(left, right)) {
                    add("$left $right")
                }
            }
        }.distinct()

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
