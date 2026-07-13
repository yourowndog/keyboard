package dev.patrickgold.florisboard.ime.nlp.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class CommitPolicyRegressionTest {
    private data class Fixture(
        val name: String,
        val input: CommitPolicy.Input,
        val expectedBlockers: List<CommitPolicy.Blocker>,
    )

    private fun input(
        typed: String,
        candidate: String,
        typedIsValidWord: Boolean = false,
        isEditDistanceCandidate: Boolean = true,
        isBlockedCorrection: Boolean = false,
        typedIsProtectedVocab: Boolean = false,
    ) = CommitPolicy.Input(
        typed = typed,
        casedCandidate = candidate,
        rawCandidate = candidate,
        typedIsValidWord = typedIsValidWord,
        isEditDistanceCandidate = isEditDistanceCandidate,
        isBlockedCorrection = isBlockedCorrection,
        typedIsProtectedVocab = typedIsProtectedVocab,
        neuralVerdict = null,
    )

    @Test
    fun knownCommitRegressions() {
        val fixtures = listOf(
            Fixture(
                name = "captured numeric revert: 742 must not become PS2",
                input = input("742", "PS2"),
                expectedBlockers = listOf(CommitPolicy.Blocker.NUMERIC_TOKEN),
            ),
            Fixture(
                name = "mixed identifier is not rewritten",
                input = input("PS2", "PS"),
                expectedBlockers = listOf(CommitPolicy.Blocker.NUMERIC_TOKEN),
            ),
            Fixture(
                name = "version string is not rewritten",
                input = input("v2", "vs"),
                expectedBlockers = listOf(CommitPolicy.Blocker.NUMERIC_TOKEN),
            ),
            Fixture(
                name = "dotted number is not rewritten",
                input = input("3.14", "314"),
                expectedBlockers = listOf(CommitPolicy.Blocker.NUMERIC_TOKEN),
            ),
            Fixture(
                name = "number-row slip at word start commits: 5his -> this",
                input = input("5his", "this"),
                expectedBlockers = emptyList(),
            ),
            Fixture(
                name = "number-row slip inside a word commits: sugg3stions -> suggestions",
                input = input("sugg3stions", "suggestions"),
                expectedBlockers = emptyList(),
            ),
            Fixture(
                name = "number-row slip inside a word commits: correc5ed -> corrected",
                input = input("correc5ed", "corrected"),
                expectedBlockers = emptyList(),
            ),
            Fixture(
                name = "digit far from replacement letter stays data: PS2 -> psa",
                input = input("PS2", "psa"),
                expectedBlockers = listOf(CommitPolicy.Blocker.NUMERIC_TOKEN),
            ),
            Fixture(
                name = "chemical-style identifier stays data: H2O -> hao",
                input = input("H2O", "hao"),
                expectedBlockers = listOf(CommitPolicy.Blocker.NUMERIC_TOKEN),
            ),
            Fixture(
                name = "protected shell vocabulary: zsh must not become ssh",
                input = input("zsh", "ssh", typedIsProtectedVocab = true),
                expectedBlockers = listOf(CommitPolicy.Blocker.PROTECTED_VOCAB),
            ),
            Fixture(
                name = "valid word immunity",
                input = input("baby", "Babylon", typedIsValidWord = true),
                expectedBlockers = listOf(CommitPolicy.Blocker.VALID_WORD_IMMUNITY),
            ),
            Fixture(
                name = "prefix completion may be shown but never committed",
                input = input("dur", "during", isEditDistanceCandidate = false),
                expectedBlockers = listOf(CommitPolicy.Blocker.NOT_A_CORRECTION),
            ),
            Fixture(
                name = "explicit anti-correction remains authoritative",
                input = input("typed", "candidate", isBlockedCorrection = true),
                expectedBlockers = listOf(CommitPolicy.Blocker.ANTI_CORRECTION),
            ),
            Fixture(
                name = "ordinary typo still commits",
                input = input("teh", "the"),
                expectedBlockers = emptyList(),
            ),
            Fixture(
                name = "high-confidence missing-space correction still commits",
                input = input("inthe", "in the"),
                expectedBlockers = emptyList(),
            ),
        )

        fixtures.forEach { fixture ->
            assertEquals(
                fixture.expectedBlockers,
                CommitPolicy.blockers(fixture.input),
                fixture.name,
            )
        }
    }
}
