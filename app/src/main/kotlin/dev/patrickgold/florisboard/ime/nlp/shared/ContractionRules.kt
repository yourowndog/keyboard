package dev.patrickgold.florisboard.ime.nlp.shared

enum class ContractionLicenseKind {
    STATIC_RULE,
}

/** Opaque commit authority issued only with an exact ContractionRules resolution. */
sealed interface ContractionLicense {
    val kind: ContractionLicenseKind
    val normalizedTyped: String
    val rawCandidate: String
    val provenance: CandidateProvenance
}

/** Canonical contraction data; consumers retain distinct behavior scopes. */
object ContractionRules {
    private class StaticRuleLicense(
        override val normalizedTyped: String,
        override val rawCandidate: String,
    ) : ContractionLicense {
        override val kind = ContractionLicenseKind.STATIC_RULE
        override val provenance = CandidateProvenance.CONTRACTION_RULE
    }

    data class Resolution(
        val candidate: String,
        val license: ContractionLicense,
    )

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
        // Ambiguous valid words "were" and "its" deliberately have no shortcut.
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

    /** Issues an exact license only for an explicit static shortcut. */
    fun resolveStatic(typed: String): Resolution? {
        val normalizedTyped = typed.lowercase()
        val candidate = SHORTCUTS[normalizedTyped] ?: return null
        return Resolution(
            candidate = candidate,
            license = StaticRuleLicense(
                normalizedTyped = normalizedTyped,
                rawCandidate = candidate,
            ),
        )
    }

    /** Revalidates the opaque license at the final evidence boundary. */
    fun isValidLicense(
        typed: String,
        rawCandidate: String,
        provenance: CandidateProvenance,
        license: ContractionLicense?,
    ): Boolean {
        val staticLicense = license as? StaticRuleLicense ?: return false
        if (provenance != CandidateProvenance.CONTRACTION_RULE) return false
        if (staticLicense.provenance != provenance) return false
        if (staticLicense.normalizedTyped != typed.lowercase()) return false
        if (staticLicense.rawCandidate != rawCandidate) return false
        return SHORTCUTS[staticLicense.normalizedTyped] == staticLicense.rawCandidate
    }
}
