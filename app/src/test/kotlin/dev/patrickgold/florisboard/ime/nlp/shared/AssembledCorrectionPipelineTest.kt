package dev.patrickgold.florisboard.ime.nlp.shared

import dev.patrickgold.florisboard.ime.nlp.NgramSuggestionEngine
import dev.patrickgold.florisboard.ime.nlp.PersonalPreferences
import dev.patrickgold.florisboard.ime.nlp.ProtectedVocabulary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Asset-backed primary-component regressions: dictionary/shortcut retrieval ->
 * ranking and evidence assembly -> commit Gate. Android provider integration,
 * prefix merging, neural inference, and fallback remain outside this suite; it
 * pins the pure production seams against the real packaged dictionary and
 * protected-vocabulary assets.
 */
class AssembledCorrectionPipelineTest {

    private companion object {
        fun packagedAsset(relativePath: String): File = sequenceOf(
            File("src/main/assets/$relativePath"),
            File("app/src/main/assets/$relativePath"),
        ).firstOrNull { it.exists() }
            ?: error("packaged asset '$relativePath' not found from ${File(".").absolutePath}")

        val engine: NgramSuggestionEngine by lazy {
            val dict = packagedAsset("ime/dict/unified_dictionary.tsv")
            DictionaryRepository.loadFromReader(dict.bufferedReader())
            NgramSuggestionEngine(unigramLogFreq = DictionaryRepository.logFrequencies)
        }

    }

    private data class Outcome(val ranked: List<String>, val top: String?, val topCommits: Boolean)

    /** Mirrors the primary path's evidence assembly for a neutral context. */
    private fun runPipeline(typed: String, previousWord: String? = null): Outcome {
        val edits = DictionaryRepository.findWithinTwoEdits(typed.lowercase())
            .filterNot { PersonalPreferences.isAntiCorrection(typed, it.term) }
        val ranked = engine.rank(edits.map { it.term to it.distance }, typed, prevWord = previousWord)
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

    private fun runShortcut(
        typed: String,
        isSentenceStart: Boolean = false,
        protected: Boolean = PersonalPreferences.isProtectedFromAutocorrect(typed),
        blocked: (String) -> Boolean = { candidate ->
            PersonalPreferences.isAntiCorrection(typed, candidate)
        },
    ): ShortcutCorrection.Outcome? {
        val lexicalStatus = if (engine.unigramLogFreq.containsKey(typed.lowercase())) {
            TypedLexicalStatus.KNOWN_WORD
        } else {
            TypedLexicalStatus.NOT_KNOWN_WORD
        }
        return ShortcutCorrection.resolve(
            typed = typed,
            isSentenceStart = isSentenceStart,
            typedLexicalStatus = lexicalStatus,
            typedIsProtectedVocab = protected,
            isBlockedCorrection = blocked,
        )
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

    @Test
    fun shortcutAssemblyUsesRealAssetsAndPersonalVetoes() {
        val packagedProtectedForms = packagedAsset("ime/dict/protected_forms.txt")
            .readLines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        assertFalse("i" in ProtectedVocabulary.BUILT_IN_FORMS + packagedProtectedForms)
        assertFalse(PersonalPreferences.isProtectedFromAutocorrect("i"))
        val loneI = requireNotNull(runShortcut("i"))
        assertEquals("I", loneI.casedCandidate)
        assertTrue(loneI.decision.shouldCommit, "lone i must auto-cap through the Gate")
        assertEquals(null, runShortcut("I"), "already-uppercase I continues through normal suggestions")

        val protectedI = requireNotNull(runShortcut("i", protected = true))
        assertEquals(listOf(CommitPolicy.Blocker.PROTECTED_VOCAB), protectedI.decision.blockers)
        val blockedI = requireNotNull(runShortcut("i", blocked = { true }))
        assertEquals(listOf(CommitPolicy.Blocker.ANTI_CORRECTION), blockedI.decision.blockers)

        val blockedId = requireNotNull(runShortcut("id"))
        assertTrue(blockedId.isBlockedCorrection, "real id -> I'd anti-correction must reach the seam")
        assertTrue(CommitPolicy.Blocker.ANTI_CORRECTION in blockedId.decision.blockers)
        assertTrue(CommitPolicy.Blocker.PROTECTED_VOCAB in blockedId.decision.blockers)
        val ordinaryId = runPipeline("id")
        assertTrue("it" in ordinaryId.ranked, "ordinary suggestions may remain tappable")
        assertFalse(ordinaryId.topCommits, "blocked shortcut fallback must leave id literal")

        val plainDont = requireNotNull(runShortcut("dont"))
        assertEquals("don't", plainDont.casedCandidate)
        assertTrue(plainDont.decision.shouldCommit)
        assertEquals(ContractionLicenseKind.STATIC_RULE, plainDont.contractionLicense?.kind)
        assertEquals("dont", plainDont.contractionLicense?.normalizedTyped)
        assertEquals("don't", plainDont.contractionLicense?.rawCandidate)

        val sentenceDont = requireNotNull(
            runShortcut("Dont", isSentenceStart = true),
        )
        assertEquals("Don't", sentenceDont.casedCandidate)
        assertTrue(sentenceDont.decision.shouldCommit, "static contraction control must still commit")

        val loudDont = requireNotNull(runShortcut("DONT"))
        assertEquals("DON'T", loudDont.casedCandidate)
        assertTrue(loudDont.decision.shouldCommit)
    }

    @Test
    fun ambiguousValidWordsStayInTheNormalSuggestionPath() {
        assertEquals(null, runShortcut("were"), "were has no shortcut authority")
        assertEquals(
            null,
            runShortcut("were", isSentenceStart = true),
            "sentence-start were has no shortcut authority",
        )
        assertEquals(null, runShortcut("its"), "its has no shortcut authority")
        assertEquals(null, runShortcut("its", isSentenceStart = true))

        val literalContexts = listOf(
            "we" to "were",
            "people" to "were",
            "so" to "were",
            null to "its",
            "so" to "its",
            "because" to "its",
        )
        for ((previousWord, typed) in literalContexts) {
            val outcome = runPipeline(typed, previousWord)
            assertFalse(
                outcome.topCommits,
                "'$previousWord $typed' must stay literal (top='${outcome.top}')",
            )
            val apostropheForm = if (typed == "were") "we're" else "it's"
            assertTrue(
                outcome.ranked.any { it.equals(apostropheForm, ignoreCase = true) },
                "$apostropheForm may remain visible for '$previousWord $typed'",
            )
        }
    }
}
