package dev.patrickgold.florisboard.ime.nlp

object FeatureFlags {
    // Default to the new n-gram engine; keep switchable for safety.
    @Volatile var useNgramEngine: Boolean = true
}
