package dev.patrickgold.florisboard.ime.nlp

import org.junit.Test
import org.junit.Assert.assertEquals

class SuggestionEngineTest {
    private val engine = NgramSuggestionEngine(
        unigramLogFreq = emptyMap(),
        bigramTable = emptyMap(),
        bigramMaxByPrev = emptyMap()
    )

    @Test
    fun testApplyCasing() {
        // Lowercase input -> Lowercase output
        assertEquals("hello", engine.applyCasing("hello", "hello"))
        
        // Uppercase input -> Uppercase output
        assertEquals("HELLO", engine.applyCasing("hello", "HELLO"))
        
        // Titlecase input -> Titlecase output
        assertEquals("Hello", engine.applyCasing("hello", "Hello"))
        
        // Mixed/Other -> Original word (lowercase usually)
        assertEquals("hello", engine.applyCasing("hello", "hElLo"))
        
        // Empty input -> Original word
        assertEquals("hello", engine.applyCasing("hello", ""))
    }
}
