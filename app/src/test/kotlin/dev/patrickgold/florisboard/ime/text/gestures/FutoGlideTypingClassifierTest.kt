/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.gestures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for native-swipe crash guards (b/1790392060).
 *
 * The guards are extracted as [internal] companion-object members so they can be exercised
 * without a real Android context, SwipeDecoder JNI bindings, or device. The guards handle two
 * failure modes that caused `std::length_error: vector` / `bionic_allocator.cpp:116` in
 * `libswipe_jni.so`'s beam search:
 *
 * 1. **Point cap** — the gesture trace is capped to [FutoGlideTypingClassifier.MAX_RECOGNITION_POINTS]
 *    before entering the native decoder, preventing heap exhaustion on extremely long traces.
 * 2. **Non-finite guard** — NaN or Infinity coordinates are rejected early, preventing
 *    unbounded hypothesis-vector growth in the beam search.
 */
class FutoGlideTypingClassifierTest {

    // ---------------------------------------------------------------- constant

    @Test
    fun pointCapConstant_isSane() {
        assertEquals(2048, FutoGlideTypingClassifier.MAX_RECOGNITION_POINTS)
    }

    // ------------------------------------------------------------- valid data

    @Test
    fun isValidGestureData_allFinite_returnsTrue() {
        val x = floatArrayOf(0.1f, 0.5f, 0.9f)
        val y = floatArrayOf(0.2f, 0.5f, 0.8f)
        val t = floatArrayOf(0f, 50f, 120f)
        assertTrue(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    @Test
    fun isValidGestureData_singlePoint_returnsTrue() {
        val x = floatArrayOf(0.5f)
        val y = floatArrayOf(0.5f)
        val t = floatArrayOf(0f)
        assertTrue(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    // ----------------------------------------------------------- NaN rejection

    @Test
    fun isValidGestureData_nanInX_returnsFalse() {
        val x = floatArrayOf(0.1f, Float.NaN, 0.9f)
        val y = floatArrayOf(0.2f, 0.5f, 0.8f)
        val t = floatArrayOf(0f, 50f, 120f)
        assertFalse(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    @Test
    fun isValidGestureData_nanInY_returnsFalse() {
        val x = floatArrayOf(0.1f, 0.5f, 0.9f)
        val y = floatArrayOf(0.2f, Float.NaN, 0.8f)
        val t = floatArrayOf(0f, 50f, 120f)
        assertFalse(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    @Test
    fun isValidGestureData_nanInT_returnsFalse() {
        val x = floatArrayOf(0.1f, 0.5f, 0.9f)
        val y = floatArrayOf(0.2f, 0.5f, 0.8f)
        val t = floatArrayOf(0f, Float.NaN, 120f)
        assertFalse(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    @Test
    fun isValidGestureData_infinityInX_returnsFalse() {
        val x = floatArrayOf(Float.POSITIVE_INFINITY, 0.5f, 0.9f)
        val y = floatArrayOf(0.2f, 0.5f, 0.8f)
        val t = floatArrayOf(0f, 50f, 120f)
        assertFalse(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    @Test
    fun isValidGestureData_negativeInfinityInY_returnsFalse() {
        val x = floatArrayOf(0.1f, 0.5f, 0.9f)
        val y = floatArrayOf(0.2f, Float.NEGATIVE_INFINITY, 0.8f)
        val t = floatArrayOf(0f, 50f, 120f)
        assertFalse(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    @Test
    fun isValidGestureData_edgeValues_boundarySafe() {
        // Float.MIN_VALUE and MAX_VALUE are finite (not Infinity or NaN)
        val x = floatArrayOf(Float.MIN_VALUE, 0.5f, Float.MAX_VALUE)
        val y = floatArrayOf(0.2f, -Float.MAX_VALUE, 0.8f)
        val t = floatArrayOf(0f, 0f, Float.MAX_VALUE * 0.5f)
        assertTrue(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    @Test
    fun isValidGestureData_allNan_returnsFalse() {
        val x = FloatArray(16) { Float.NaN }
        val y = FloatArray(16) { Float.NaN }
        val t = FloatArray(16) { Float.NaN }
        assertFalse(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    @Test
    fun isValidGestureData_allInfinity_returnsFalse() {
        val x = FloatArray(16) { Float.POSITIVE_INFINITY }
        val y = FloatArray(16) { Float.POSITIVE_INFINITY }
        val t = FloatArray(16) { Float.POSITIVE_INFINITY }
        assertFalse(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    // ------------------------------------------------------- length mismatch
    // The function rejects arrays of different sizes.

    @Test
    fun isValidGestureData_yShorterThanX_returnsFalse() {
        val x = floatArrayOf(0.1f, 0.5f, 0.9f)
        val y = floatArrayOf(0.2f, 0.5f)
        val t = floatArrayOf(0f, 50f, 120f)
        assertFalse(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    @Test
    fun isValidGestureData_tShorterThanX_returnsFalse() {
        val x = floatArrayOf(0.1f, 0.5f, 0.9f)
        val y = floatArrayOf(0.2f, 0.5f, 0.8f)
        val t = floatArrayOf(0f, 50f)
        assertFalse(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }

    @Test
    fun isValidGestureData_allEmpty_returnsTrue() {
        val x = FloatArray(0)
        val y = FloatArray(0)
        val t = FloatArray(0)
        assertTrue(FutoGlideTypingClassifier.isValidGestureData(x, y, t))
    }
}