/*
 * Copyright (C) 2026 The OmniBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.theme.backdrop

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackdropHueTest {
    private val gif = BackdropId.PrivateAsset("warp-core.gif")
    private val png = BackdropId.Imported("bridge.png")

    /** Applies the 4x5 matrix to an RGBA colour with channels in 0..1, clamped like Skia. */
    private fun apply(m: FloatArray, r: Float, g: Float, b: Float, a: Float = 1f): FloatArray =
        FloatArray(4) { row ->
            (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * a + m[row * 5 + 4] / 255f)
                .coerceIn(0f, 1f)
        }

    private fun assertClose(expected: FloatArray, actual: FloatArray, tolerance: Float = 0.01f) {
        assertTrue(
            expected.indices.all { abs(expected[it] - actual[it]) <= tolerance },
            "expected ${expected.toList()} but was ${actual.toList()}",
        )
    }

    @Test
    fun `zero and a full turn leave colours unchanged`() {
        for (degrees in listOf(0f, 360f, -360f)) {
            assertClose(floatArrayOf(0.9f, 0.4f, 0.3f, 1f), apply(BackdropHue.matrix(degrees), 0.9f, 0.4f, 0.3f))
        }
    }

    @Test
    fun `greys stay grey and alpha passes through`() {
        for (degrees in listOf(-180f, -90f, 45f, 120f, 180f)) {
            val m = BackdropHue.matrix(degrees)
            assertClose(floatArrayOf(0.5f, 0.5f, 0.5f, 0.3f), apply(m, 0.5f, 0.5f, 0.5f, 0.3f))
            assertClose(floatArrayOf(0f, 0f, 0f, 0f), apply(m, 0f, 0f, 0f, 0f))
            // The alpha row ignores colour entirely.
            assertEquals(listOf(0f, 0f, 0f, 1f, 0f), m.slice(15..19))
        }
    }

    @Test
    fun `a shift actually moves the hue and opposite shifts undo each other`() {
        val coral = floatArrayOf(0.98f, 0.45f, 0.38f)
        val shifted = apply(BackdropHue.matrix(180f), coral[0], coral[1], coral[2])
        // Coral turned half way round the wheel is a blue-green: red drops, blue rises.
        assertTrue(shifted[0] < coral[0] - 0.3f && shifted[2] > coral[2] + 0.2f, "got ${shifted.toList()}")
        val back = apply(BackdropHue.matrix(-60f), *apply(BackdropHue.matrix(60f), 0.3f, 0.6f, 0.8f).take(3).toFloatArray())
        assertClose(floatArrayOf(0.3f, 0.6f, 0.8f, 1f), back, tolerance = 0.02f)
    }

    @Test
    fun `slider values round clamp and snap to zero`() {
        assertEquals(0, BackdropHue.fromSlider(2.6f))
        assertEquals(0, BackdropHue.fromSlider(-3.4f))
        assertEquals(4, BackdropHue.fromSlider(4.2f))
        assertEquals(180, BackdropHue.fromSlider(999f))
        assertEquals(-180, BackdropHue.fromSlider(-999f))
    }

    @Test
    fun `shifts are stored per backdrop and zero removes the entry`() {
        var raw = BackdropHue.withShift("", gif, 40)
        raw = BackdropHue.withShift(raw, png, -90)
        assertEquals(40, BackdropHue.shiftFor(raw, gif))
        assertEquals(-90, BackdropHue.shiftFor(raw, png))
        assertEquals(0, BackdropHue.shiftFor(raw, BackdropId.Imported("other.gif")))
        raw = BackdropHue.withShift(raw, gif, 0)
        assertEquals(0, BackdropHue.shiftFor(raw, gif))
        assertEquals("file:bridge.png=-90", raw)
        assertEquals("", BackdropHue.withShift(raw, png, 0))
    }

    @Test
    fun `stored values never throw and are clamped`() {
        val raw = "asset:warp-core.gif=900;garbage;=12;file:bridge.png=abc;asset:x.png=0"
        assertEquals(mapOf("asset:warp-core.gif" to 180), BackdropHue.parse(raw))
        assertEquals(emptyMap(), BackdropHue.parse(null))
    }

    @Test
    fun `code-drawn backdrops and none are never shifted`() {
        val raw = "builtin:grid=90"
        assertEquals(0, BackdropHue.shiftFor(raw, BackdropId.Builtin(BuiltinBackdrop.GRID)))
        assertEquals(0, BackdropHue.shiftFor(raw, BackdropId.None))
        assertTrue(BackdropHue.supports(gif) && BackdropHue.supports(png))
    }
}
