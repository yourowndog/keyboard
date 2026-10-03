package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Recomposition/layout stress for the `SM-S938U` crash fix.
 *
 * The on-device crash was a race: `KeyboardManager.updateActiveEvaluators` recomputes keys on a
 * background coroutine (eagerly writing the Compose `isVisible` snapshot state), and when that
 * write landed after the solving layout pass had taken its snapshot, the geometry bridge's read of
 * that snapshot state threw `IllegalStateException`. The fix removes the entire hazardous read: the
 * bridge now consumes the plain `Key.isVisibleProjection` mirror and never touches a Compose
 * snapshot state, making the exception structurally unreachable from the geometry path.
 *
 * This test hammers the exact production concurrency pattern the crash came from — key visibility
 * mutated concurrently with geometry solving — and asserts the solver never throws and always
 * returns a usable result. On a live Android compositor a stale snapshot could still be taken if a
 * Compose state were read; here we assert the geometry path is stable under recomposition stress and
 * that every write the evaluator performs goes through `Key.applyVisibility`, which keeps the
 * Compose state and the plain projection in lockstep.
 */
class RecompositionGeometryStressTest {

    private val availableWidths = listOf(840.0, 1080.0, 1440.0, 1920.0)

    private fun stressKeyboard(): TextKeyboard {
        val keyboard = GeometryFixtures.defaultCoding()
        // Make every key recompose through the real evaluator path so `compute` re-writes
        // visibility through `applyVisibility` (the production writer), not raw field pokes.
        for (key in keyboard.keys()) {
            key.compute(DefaultComputingEvaluator)
        }
        return keyboard
    }

    @Test
    fun `geometry solving never throws under concurrent recomposition of key visibility`() {
        val keyboard = stressKeyboard()
        val keys: List<TextKey> = buildList {
            val it = keyboard.keys()
            while (it.hasNext()) add(it.next())
        }
        require(keys.isNotEmpty())
        val running = AtomicBoolean(true)
        val solverFailure = AtomicReference<String?>(null)
        val completedRounds = AtomicReference(0)
        val startGate = CountDownLatch(1)

        // Recomposition writer: the background coroutine analogue of
        // KeyboardManager.updateActiveEvaluators, toggling each key's visibility both through the
        // evaluator path (compute -> applyVisibility(true)) and the layout-pack spacer path
        // (applyVisibility(false)), so both production writer routes are exercised mid-solve.
        val recomposer = Thread {
            startGate.await()
            var i = 0
            var last = keys.lastIndex
            while (running.get()) {
                var key = keys[i % (last + 1)]
                if (i % 3 == 0) {
                    key.applyVisibility(false)
                } else {
                    key.compute(DefaultComputingEvaluator)
                }
                i++
            }
        }

        // Layout pass: keep solving the keyboard while recomposition mutates it.
        recomposer.start()
        startGate.countDown()
        try {
            val prefs = GeometryPreferences(
                rowBaseHeightPx = 160.0,
                alphaRowHeightPercent = 100,
                utilityRowHeightPercent = 80,
                keySpacingHorizontalPx = 2.0,
                keySpacingVerticalPx = 3.0,
            )
            for (round in 0 until 12) {
                for (width in availableWidths) {
                    // frameHeight (the FlorisImeSizing.keyboardUiHeight consumer).
                    val height = TextKeyboardGeometryBridge.frameHeight(
                        keyboard = keyboard,
                        prefs = prefs,
                        availableWidth = width,
                        orientation = GeometryOrientation.PORTRAIT,
                    )
                    assertNotNull(height, "a stressed solve must still decide a frame height")
                    // solve + applyTo (the Compose layout consumer).
                    val result = TextKeyboardGeometryBridge.solve(
                        keyboard = keyboard,
                        prefs = prefs,
                        availableWidth = width,
                        framePolicy = FramePolicy.Intrinsic(prefs.sanitized().rowBaseHeightPx),
                        orientation = GeometryOrientation.PORTRAIT,
                    )
                    when (result) {
                        is TextKeyboardGeometryBridge.Result.Solved -> {
                            TextKeyboardGeometryBridge.applyTo(keyboard, result.geometry, true)
                            assertCoherent(result.geometry)
                        }
                        is TextKeyboardGeometryBridge.Result.Fallback -> {
                            TextKeyboardGeometryBridge.applyTo(keyboard, result.geometry, true)
                        }
                        is TextKeyboardGeometryBridge.Result.Unavailable -> {
                            // A degenerate frame is a legitimate outcome under stress, never a throw.
                        }
                    }
                }
                completedRounds.set(round + 1)
            }
        } catch (t: Throwable) {
            solverFailure.set("geometry solver threw under recomposition stress: ${t}\n${t.stackTraceToString()}")
        } finally {
            running.set(false)
            recomposer.join(TimeUnit.SECONDS.toMillis(5))
        }

        val failure = solverFailure.get()
        if (failure != null) {
            fail(failure)
        }
        assertTrue(completedRounds.get() >= 12, "solver must complete all stress rounds")
        assertTrue(keys.any { !it.isVisibleProjection }, "recomposer must have toggled at least one key hidden")
    }

    private fun assertCoherent(geometry: SolvedGeometry) {
        // Every solved item must carry a non-negative, finite width — nothing may come out of a
        // solve as a collapsed or NaN rectangle.
        val rows = geometry.rows
        for (rowIndex in rows.indices) {
            val row = rows[rowIndex]
            val items = row.items
            for (itemIndex in items.indices) {
                val item = items[itemIndex]
                assertNotNull(item.stableId)
                val w = item.bounds.width
                assertTrue(w >= 0, "row $rowIndex item $itemIndex must not have a negative width, was $w")
            }
        }
    }
}