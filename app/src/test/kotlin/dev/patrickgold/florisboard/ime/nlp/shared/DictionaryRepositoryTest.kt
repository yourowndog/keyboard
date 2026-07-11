package dev.patrickgold.florisboard.ime.nlp.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DictionaryRepositoryTest {
    @Test
    fun `distance handles exact matches`() {
        assertEquals(0.0, DictionaryRepository.distance("cat", "cat"))
        assertEquals(0.0, DictionaryRepository.distance("Cat", "cat")) // case-insensitive
    }

    @Test
    fun `distance counts single edits`() {
        assertEquals(1.0, DictionaryRepository.distance("teh", "the"))       // transposition
        assertEquals(1.0, DictionaryRepository.distance("recieve", "receive")) // transposition
        assertEquals(1.0, DictionaryRepository.distance("dont", "don't"))    // insertion
        assertEquals(1.0, DictionaryRepository.distance("cst", "cat"))       // substitution
        assertEquals(1.0, DictionaryRepository.distance("catt", "cat"))      // deletion
        assertEquals(1.0, DictionaryRepository.distance("definately", "definitely")) // substitution
    }

    @Test
    fun `distance counts double edits`() {
        assertEquals(2.0, DictionaryRepository.distance("ksnt", "isn't"))    // sub + insertion
        assertEquals(2.0, DictionaryRepository.distance("ct", "cast"))       // two insertions
        assertEquals(2.0, DictionaryRepository.distance("hte", "then"))      // transposition + insertion
    }

    @Test
    fun `distance abandons beyond two edits`() {
        assertEquals(99.0, DictionaryRepository.distance("xyz", "cat"))
        assertEquals(99.0, DictionaryRepository.distance("abcdef", "abcxyzdef")) // 3 insertions
        assertEquals(99.0, DictionaryRepository.distance("keyboard", "kb"))
    }

    @Test
    fun `distance handles short and empty-ish inputs`() {
        assertEquals(1.0, DictionaryRepository.distance("a", "ab"))
        assertEquals(2.0, DictionaryRepository.distance("a", "abc"))
        assertEquals(1.0, DictionaryRepository.distance("ab", "ba"))
    }
}
