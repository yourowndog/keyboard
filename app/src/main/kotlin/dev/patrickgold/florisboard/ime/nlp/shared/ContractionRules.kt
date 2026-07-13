package dev.patrickgold.florisboard.ime.nlp.shared

/** Canonical contraction data; consumers retain distinct behavior scopes. */
object ContractionRules {
    /** General shortcuts used by the primary suggestion path and casing. */
    val SHORTCUTS = mapOf(
        "im" to "I'm",
        "i'm" to "I'm",
        "ive" to "I've",
        "id" to "I'd",
        "ill" to "I'll",
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "wint" to "won't",
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
        // Real words "were" and "its" are handled only by resolveContextual().
        "hes" to "he's",
        "shes" to "she's",
        "thats" to "that's",
        "whats" to "what's",
        "whos" to "who's",
        "lets" to "let's",
        "ac" to "AC",
        "itd" to "it'd",
        "hows" to "how's",
        "km" to "I'm",
        "moms" to "Mom's",
    )

    /** Contraction category used only for the Judge's soft grammar penalty. */
    val SOFT_GRAMMAR_CANDIDATES = setOf(
        "i'm", "i'd", "i'll", "i've", "we're", "we'll", "they're", "you're",
        "he's", "she's", "it's", "that's", "what's", "who's", "here's", "there's",
    )

    private val LEGACY_FALLBACK_KEYS = setOf(
        "i'm", "ive", "ill", "dont", "cant", "wont", "isnt", "arent", "doesnt",
        "didnt", "wasnt", "werent", "youre", "theyre", "lets", "thats", "whos",
        "whats", "ac", "itd",
    )

    /** Exact narrower shortcut set historically used when the main engine is unavailable. */
    val LEGACY_FALLBACK_SHORTCUTS = SHORTCUTS.filterKeys { it in LEGACY_FALLBACK_KEYS } + mapOf(
        "wheres" to "where's",
        "theres" to "there's",
        "hell" to "he'll",
        "shell" to "she'll",
    )

    /**
     * Every contraction form the ranker may treat as an intended contraction.
     * CandidateScorer's apostrophe bonuses require membership here; a random
     * dictionary possessive (La's, function's) earns no contraction evidence
     * from merely containing an apostrophe.
     */
    val LICENSED_FORMS: Set<String> = buildSet {
        SHORTCUTS.values.forEach { add(it.lowercase()) }
        LEGACY_FALLBACK_SHORTCUTS.values.forEach { add(it.lowercase()) }
        addAll(SOFT_GRAMMAR_CANDIDATES)
        addAll(
            setOf(
                "i'm", "i've", "i'd", "i'll",
                "you're", "you've", "you'd", "you'll",
                "we're", "we've", "we'd", "we'll",
                "they're", "they've", "they'd", "they'll",
                "he's", "he'd", "he'll", "she's", "she'd", "she'll",
                "it's", "it'd", "it'll",
                "don't", "can't", "won't", "isn't", "aren't", "wasn't", "weren't",
                "hasn't", "haven't", "hadn't", "couldn't", "wouldn't", "shouldn't",
                "didn't", "doesn't", "ain't", "mustn't", "mightn't", "needn't", "shan't",
                "that's", "that'd", "that'll", "there's", "there'd", "there'll",
                "here's", "what's", "what're", "what'd", "who's", "who're", "who'd", "who'll",
                "where's", "when's", "why's", "how's", "this'll", "let's",
                "y'all", "o'clock", "ma'am",
            )
        )
    }

    private val PREV_WORDS_FOR_WERE = setOf(
        "we", "they", "you", "there", "here", "who", "which", "what", "that", "these", "those",
    )
    private val PREV_WORDS_FOR_ITS_POSSESSIVE = setOf(
        "lost", "on", "at", "in", "of", "with", "by", "for", "from",
        "the", "a", "an", "this", "that", "these", "those",
        "my", "your", "his", "her", "their", "our",
    )

    /** Resolves contractions whose unpunctuated spelling is also a valid word. */
    fun resolveContextual(typed: String, prevWord: String?): String? {
        val prev = prevWord?.lowercase() ?: ""
        return when (typed.lowercase()) {
            "were" -> if (prev.isEmpty() || prev !in PREV_WORDS_FOR_WERE) "we're" else null
            "its" -> if (prev.isNotEmpty() && prev in PREV_WORDS_FOR_ITS_POSSESSIVE) null else "it's"
            else -> null
        }
    }
}
