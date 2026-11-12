package dev.patrickgold.florisboard.ime.nlp.shared

import dev.patrickgold.florisboard.ime.core.KeyboardLayout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TouchAwareScoringTest {

    @AfterTest
    fun clearTrace() = ComposingTouchTrace.reset()

    @Test
    fun centredTapCostsTheKeyDistance() {
        val cost = CandidateScorer.touchSubstitutionCost(TouchSample('u', 0f, 0f), 'u', 'y')
        assertEquals(KeyboardLayout.keyDistance('u', 'y'), cost, 1e-6)
    }

    @Test
    fun borderTapTowardTheIntendedKeyIsCheap() {
        // "lazu" -> "lazy": the U landed on its left edge, against Y.
        val border = CandidateScorer.touchSubstitutionCost(TouchSample('u', -0.5f, 0f), 'u', 'y')
        val away = CandidateScorer.touchSubstitutionCost(TouchSample('u', 0.4f, 0f), 'u', 'y')
        assertTrue(border < 0.2, "border tap $border")
        assertTrue(away >= 0.99, "tap leaning away $away")
    }

    @Test
    fun touchesDecideBetweenTwoEquallyCloseWords() {
        // T sits between R and Y, so "fot" is one substitution from both "for" and "foy".
        // The side of the T the finger landed on should decide.
        val leaningLeft = listOf(null, null, TouchSample('t', -0.45f, 0f))
        val leaningRight = listOf(null, null, TouchSample('t', 0.45f, 0f))
        val forLeft = CandidateScorer.spatialCost("fot", "for", leaningLeft)
        val foyLeft = CandidateScorer.spatialCost("fot", "foy", leaningLeft)
        val forRight = CandidateScorer.spatialCost("fot", "for", leaningRight)
        val foyRight = CandidateScorer.spatialCost("fot", "foy", leaningRight)
        assertTrue(forLeft < foyLeft)
        assertTrue(foyRight < forRight)
    }

    @Test
    fun withoutTouchesTheScoreIsUnchanged() {
        assertEquals(CandidateScorer.spatialCost("thst", "that"), CandidateScorer.spatialCost("thst", "that", null))
    }

    @Test
    fun traceFollowsTypingAndBackspace() {
        ComposingTouchTrace.onLetter('l', 0.1f, 0f)
        ComposingTouchTrace.onLetter('a', 0f, 0f)
        ComposingTouchTrace.onLetter('z', 0f, 0f)
        ComposingTouchTrace.onLetter('x', 0f, 0f)
        ComposingTouchTrace.onBackspace()
        ComposingTouchTrace.onLetter('u', -0.5f, 0.1f)
        val snap = ComposingTouchTrace.snapshotFor("Lazu")
        assertEquals(4, snap?.size)
        assertEquals(-0.5f, snap!![3]!!.dx)
    }

    @Test
    fun traceIsIgnoredWhenItDoesNotDescribeTheWord() {
        ComposingTouchTrace.onLetter('a', 0f, 0f)
        ComposingTouchTrace.onLetter('b', 0f, 0f)
        assertNull(ComposingTouchTrace.snapshotFor("abc"))
        assertNull(ComposingTouchTrace.snapshotFor("xb"))
        ComposingTouchTrace.reset()
        assertNull(ComposingTouchTrace.snapshotFor("ab"))
    }

    @Test
    fun staleSamplesBeforeTheWordAreSkipped() {
        // A held backspace deletes several letters but reports one release.
        for (c in "hello") ComposingTouchTrace.onLetter(c, 0f, 0f)
        ComposingTouchTrace.onBackspace()
        ComposingTouchTrace.onLetter('p', 0f, 0f)
        assertEquals(2, ComposingTouchTrace.snapshotFor("lp")?.size)
    }

    @Test
    fun samplesWithoutCoordinatesFallBackToKeyDistance() {
        ComposingTouchTrace.onLetter('o', null, null)
        ComposingTouchTrace.onLetter('d', 0f, 0f)
        val snap = ComposingTouchTrace.snapshotFor("od")
        assertNull(snap!![0])
        assertEquals(
            CandidateScorer.spatialCost("od", "of"),
            CandidateScorer.spatialCost("od", "of", listOf(null, TouchSample('d', 0f, 0f))),
            1e-6,
        )
    }
}
