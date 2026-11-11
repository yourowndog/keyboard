package dev.patrickgold.florisboard.ime.nlp.shared

import java.io.BufferedReader
import java.io.File
import java.io.Reader
import java.io.StringReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DictionaryRepositoryTest {
    private class CloseTrackingReader(delegate: Reader) : BufferedReader(delegate) {
        var wasClosed = false
            private set

        override fun close() {
            try {
                super.close()
            } finally {
                wasClosed = true
            }
        }
    }

    private fun packagedDictionary(): File = sequenceOf(
        File("src/main/assets/ime/dict/unified_dictionary.tsv"),
        File("app/src/main/assets/ime/dict/unified_dictionary.tsv"),
    ).firstOrNull { it.exists() }
        ?: error("packaged dictionary not found from ${File(".").absolutePath}")

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

    @Test
    fun `load owns supplied readers and lookup diagnostics are JVM safe`() {
        val loadingReader = CloseTrackingReader(packagedDictionary().reader())
        DictionaryRepository.loadFromReader(loadingReader)
        assertTrue(loadingReader.wasClosed)

        val alreadyLoadedReader = CloseTrackingReader(StringReader("ignored\t1\n"))
        DictionaryRepository.loadFromReader(alreadyLoadedReader)
        assertTrue(alreadyLoadedReader.wasClosed)

        // Every 32-call window hits the sampled diagnostic branch once.
        repeat(32) {
            DictionaryRepository.findWithinTwoEdits("teh")
        }
    }
}
