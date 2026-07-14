package dev.patrickgold.florisboard.ime.nlp

/** Bootstrap protection available before harvested asset forms finish loading. */
object ProtectedVocabulary {
    val BUILT_IN_FORMS = setOf(
        "bc", "rn", "tf", "lmk", "ppl", "msg", "thx", "sry", "btw",
        "imo", "idk", "omg", "wtf", "smh", "ngl", "tbh", "fr", "wdym",
        "bday", "pls", "llms", "cs", "gen", "config", "ai", "id",
        // "im" is intentionally absent so the im -> I'm shortcut can fire.
        "ya", "def", "tho", "nah", "ugh", "oof", "bruh", "cuz",
        // CommitPolicy already protects single letters from word replacement. Keep
        // lowercase "i" out of personal vocab so its one legitimate casing-only
        // correction (i -> I) can still pass through the Gate.
        "g",
    )
}
