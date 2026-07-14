package dev.patrickgold.florisboard.ime.nlp.shared

import dev.patrickgold.florisboard.ime.nlp.NgramSuggestionEngine
import dev.patrickgold.florisboard.ime.nlp.PersonalPreferences
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Asset-backed primary-component regressions: dictionary retrieval -> heuristic
 * ranking -> the production evidence adapter -> commit Gate. Provider context,
 * casing, prefix merging, neural inference, and fallback have separate tests;
 * this suite pins agreement with the real packaged dictionary rows.
 */
class AssembledCorrectionPipelineTest {

    private companion object {
        val engine: NgramSuggestionEngine by lazy {
            val dict = sequenceOf(
                File("src/main/assets/ime/dict/unified_dictionary.tsv"),
                File("app/src/main/assets/ime/dict/unified_dictionary.tsv"),
            ).firstOrNull { it.exists() }
                ?: error("packaged dictionary not found from ${File(".").absolutePath}")
            DictionaryRepository.loadFromReader(dict.bufferedReader())
            NgramSuggestionEngine(unigramLogFreq = DictionaryRepository.logFrequencies)
        }
    }

    private data class Outcome(val ranked: List<String>, val top: String?, val topCommits: Boolean)

    /** Mirrors the primary path's evidence assembly for a neutral context. */
    private fun runPipeline(typed: String): Outcome {
        val edits = DictionaryRepository.findWithinTwoEdits(typed.lowercase())
            .filterNot { PersonalPreferences.isAntiCorrection(typed, it.term) }
        val ranked = engine.rank(edits.map { it.term to it.distance }, typed, prevWord = null)
        val rankedTexts = ranked.map { it.text.toString() }
        val top = rankedTexts.firstOrNull()
        val requestEvidence = CommitRequestEvidence(
            typed = typed,
            typedLexicalStatus = if (rankedTexts.any { it.equals(typed, ignoreCase = true) }) {
                TypedLexicalStatus.KNOWN_WORD
            } else {
                TypedLexicalStatus.NOT_KNOWN_WORD
            },
            typedIsProtectedVocab = PersonalPreferences.isProtectedFromAutocorrect(typed),
            neuralEvidence = NeuralEvidence.Disabled,
        )
        val topCommits = top != null && CorrectionDecision.evaluate(
            request = requestEvidence,
            candidate = CommitCandidateEvidence(
                raw = top,
                cased = top,
                provenance = CandidateProvenance.EDIT_DISTANCE,
                isBlockedCorrection = PersonalPreferences.isAntiCorrection(typed, top),
            ),
        ).shouldCommit
        return Outcome(rankedTexts, top, topCommits)
    }

    @Test
    fun plainAndNumberRowTyposCorrectAgainstRealAssets() {
        val expectations = mapOf(
            "teh" to "the",
            "5his" to "this",
            "sugg3stions" to "suggestions",
            "correc5ed" to "corrected",
        )
        for ((typed, expected) in expectations) {
            val outcome = runPipeline(typed)
            assertEquals(expected, outcome.top?.lowercase(), "top candidate for '$typed'")
            assertTrue(outcome.topCommits, "'$typed' -> '$expected' must auto-commit")
        }
    }

    @Test
    fun lasRanksOrdinaryWordsAboveUnlicensedPossessives() {
        val outcome = runPipeline("las")
        assertEquals("las", outcome.top, "exact dictionary match stays on top")
        assertFalse(outcome.topCommits, "identical top candidate never commits")

        fun rankOf(word: String) = outcome.ranked.indexOfFirst { it.equals(word, ignoreCase = true) }
        assertTrue(rankOf("last") >= 0 && rankOf("la's") >= 0 && rankOf("law") >= 0,
            "expected candidates present: ${outcome.ranked.take(8)}")
        assertTrue(rankOf("last") < rankOf("la's"),
            "last must outrank La's: ${outcome.ranked.take(8)}")
        assertTrue(rankOf("law") < rankOf("la's"),
            "law must outrank La's: ${outcome.ranked.take(8)}")
    }

    @Test
    fun identifiersAndValidWordsNeverAutoReplace() {
        for (typed in listOf("742", "PS2", "v2", "3.14", "zsh")) {
            val outcome = runPipeline(typed)
            val changesText = outcome.top != null && !outcome.top.equals(typed, ignoreCase = true)
            if (changesText) {
                assertFalse(outcome.topCommits, "'$typed' must never be auto-replaced (top='${outcome.top}')")
            }
        }
        assertEquals("zsh", runPipeline("zsh").top, "zsh stays the exact top match")
    }
}
