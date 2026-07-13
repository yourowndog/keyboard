package dev.patrickgold.florisboard.ime.nlp

/** Bootstrap protection available before harvested asset forms finish loading. */
object ProtectedVocabulary {
    val BUILT_IN_FORMS = setOf(
        "bc", "rn", "tf", "lmk", "ppl", "msg", "thx", "sry", "btw",
        "imo", "idk", "omg", "wtf", "smh", "ngl", "tbh", "fr", "wdym",
        "bday", "pls", "llms", "cs", "gen", "config", "ai",
        // "im" is intentionally absent so the im -> I'm shortcut can fire.
        "ya", "def", "tho", "nah", "ugh", "oof", "bruh", "cuz",
        "g", "i",
    )
}
