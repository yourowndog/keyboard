package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression guard for the on-device Compose snapshot crash (`SM-S938U`, 2026-10-03 01:38:41):
 *
 *     IllegalStateException: Reading a state that was created after the snapshot was taken or in a
 *     snapshot that has not yet been applied
 *       at Key.isVisible(Key.kt:122)
 *       at TextKeyboardGeometryBridge.describeRows(TextKeyboardGeometryBridge.kt:97)
 *
 * `describeRows` used to read `key.isVisible`, a Compose `SnapshotMutableState`. Key recomposition
 * runs on a background coroutine (`KeyboardManager.updateActiveEvaluators`) that writes `isVisible`
 * eagerly, so when a recomposition lands after the solving layout pass took its snapshot, reading
 * that state from the geometry bridge let the runtime reject the read. The fix: the evaluator
 * records visibility into a plain, non-Compose projection (`Key.isVisibleProjection`) and the
 * bridge consumes that instead.
 *
 * These tests fail on the unsafe pattern: they reference `isVisibleProjection`, which did not
 * exist while `describeRows` read the Compose state directly. They also pin the geometry contract
 * that a hidden key collapses to zero units while its neighbours keep their canonical width.
 */
class KeyVisibilityProjectionTest {

    /** A single alpha row of [count] ordinary keys. */
    private fun alphaRow(count: Int): Array<TextKey> = GeometryFixtures.alphaRow(count)

    private fun keyboardOf(rows: List<Array<TextKey>>, roles: List<SemanticRowRole>): TextKeyboard =
        GeometryFixtures.keyboard(rows, roles)

    @Test
    fun `describeRows reads the plain visibility projection, never the Compose state`() {
        // The whole point of the fix: Compose `isVisible` stays true (as far as the renderer is
        // concerned) while the projection says hidden. If the bridge ever regressed to reading the
        // Compose snapshot state, the collapsed cell would come back and this test would fail.
        val keys = alphaRow(3)
        keys[1].isVisible = true                       // snapshot state the renderer sees
        keys[1].isVisibleProjection = false            // the projection the geometry reads
        val keyboard = keyboardOf(listOf(keys), listOf(SemanticRowRole.ALPHA))

        val described = TextKeyboardGeometryBridge.describeRows(keyboard)
        assertEquals(1, described.size)
        val row = described.single()
        assertEquals(3, row.items.size)
        assertEquals(0.0, row.items[1].widthUnits, "the hidden middle key must collapse to zero")
        assertEquals(1.0, row.items[0].widthUnits, "a visible neighbour keeps its canonical width")
        assertEquals(1.0, row.items[2].widthUnits, "a visible neighbour keeps its canonical width")
    }

    @Test
    fun `a hidden key collapses and the row still solves without holes`() {
        val keys = alphaRow(4)
        keys[2].applyVisibility(false)
        val keyboard = keyboardOf(listOf(keys), listOf(SemanticRowRole.ALPHA))
        val prefs = GeometryPreferences(rowBaseHeightPx = 160.0)

        val described = TextKeyboardGeometryBridge.describeRows(keyboard)
        assertEquals(keys.size, described.single().items.size)
        assertEquals(0.0, described.single().items[2].widthUnits)

        // A solve over the collapsed row must still produce a frame and placed rows — nothing may
        // become unavailable just because a modifier/substitution key was hidden mid-recomposition.
        val result = TextKeyboardGeometryBridge.solve(
            keyboard = keyboard,
            prefs = prefs,
            availableWidth = 1080.0,
            framePolicy = FramePolicy.FitToHeight(frameHeight = 400.0),
        )
        assertTrue(result is TextKeyboardGeometryBridge.Result.Solved, "a hidden key must not make geometry unavailable")
        val solved = (result as TextKeyboardGeometryBridge.Result.Solved).geometry
        assertEquals(1, solved.rows.size)
        assertEquals(4, solved.rows.single().items.size)
    }

    @Test
    fun `applyVisibility keeps the Compose state and the projection in lockstep`() {
        val key = GeometryFixtures.alphaRow(1)[0]
        assertTrue(key.isVisible)
        assertTrue(key.isVisibleProjection)

        key.applyVisibility(false)
        assertFalse(key.isVisible)
        assertFalse(key.isVisibleProjection)

        key.applyVisibility(true)
        assertTrue(key.isVisible)
        assertTrue(key.isVisibleProjection)
    }
}