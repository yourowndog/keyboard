/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads and caches precomputed ideal swipe gestures.
 * Gestures are stored in normalized 0-1 coordinates and scaled to keyboard size at runtime.
 */
class PrecomputedGestureCache(private val context: Context) {
    private val gestureCache = HashMap<String, List<FloatArray>>()
    private var isLoaded = false
    
    companion object {
        private const val TAG = "PrecomputedGestures"
        private const val ASSET_PATH = "ime/swipe/futo_swipes.bin"
        private const val PRECOMPUTED_SAMPLE_POINTS = 50
    }
    
    /**
     * Load precomputed gestures from binary asset.
     * Binary format loads 50x+ faster than JSON.
     */
    fun load() {
        if (isLoaded) return
        
        try {
            Log.d(TAG, "Loading precomputed gestures from $ASSET_PATH...")
            val startTime = System.currentTimeMillis()
            
            val inputStream = try {
                context.assets.open(ASSET_PATH)
            } catch (e: java.io.FileNotFoundException) {
                // No precomputed swipe data bundled yet (swipe model not trained) — not an error
                Log.i(TAG, "No precomputed swipe data at $ASSET_PATH; skipping")
                isLoaded = true
                return
            }

            // Read header
            val headerBuf = ByteArray(8)
            inputStream.read(headerBuf)
            val headerBB = java.nio.ByteBuffer.wrap(headerBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val numWords = headerBB.getInt(0)
            val numSamplePoints = headerBB.getInt(4)
            
            Log.d(TAG, "Header: $numWords words, $numSamplePoints points per gesture")
            
            // Read each word
            for (i in 0 until numWords) {
                try {
                    // Read word length (2 bytes)
                    val wordLenBuf = ByteArray(2)
                    if (inputStream.read(wordLenBuf) != 2) {
                        Log.e(TAG, "Failed to read word length at word $i")
                        break
                    }
                    val wordLen = ((wordLenBuf[1].toInt() and 0xFF) shl 8) or (wordLenBuf[0].toInt() and 0xFF)
                    
                    // Read word
                    val wordBytes = ByteArray(wordLen)
                    if (inputStream.read(wordBytes) != wordLen) {
                        Log.e(TAG, "Failed to read word bytes at word $i")
                        break
                    }
                    val word = String(wordBytes, Charsets.UTF_8)
                    
                    // Read number of gestures (1 byte)
                    val numGesturesArray = ByteArray(1)
                    if (inputStream.read(numGesturesArray) != 1) {
                        Log.e(TAG, "Failed to read num gestures at word $i ($word)")
                        break
                    }
                    val numGestures = numGesturesArray[0].toInt() and 0xFF
                    
                    // Read each gesture
                    val gestures = mutableListOf<FloatArray>()
                    val pointsPerGesture = numSamplePoints * 2 // x,y pairs
                    val bytesPerGesture = pointsPerGesture * 4 // 4 bytes per float
                    
                    for (g in 0 until numGestures) {
                        val gestureBytes = ByteArray(bytesPerGesture)
                        val bytesRead = inputStream.read(gestureBytes)
                        if (bytesRead != bytesPerGesture) {
                            Log.e(TAG, "Failed to read gesture $g for word $i ($word): expected $bytesPerGesture, got $bytesRead")
                            break
                        }
                        
                        // Convert bytes to floats
                        val points = FloatArray(pointsPerGesture)
                        val bb = java.nio.ByteBuffer.wrap(gestureBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        for (p in 0 until pointsPerGesture) {
                            points[p] = bb.getFloat(p * 4)
                        }
                        gestures.add(points)
                    }
                    
                    if (gestures.isNotEmpty()) {
                        gestureCache[word] = gestures
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading word $i", e)
                    break
                }
            }
            
            inputStream.close()
            
            val loadTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Loaded ${gestureCache.size} precomputed gestures in ${loadTime}ms")
            isLoaded = true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load precomputed gestures", e)
            // Fallback: cache will be empty, generateIdealGestures will be used
        }
    }
    
    /**
     * Get precomputed gestures for a word, scaled to keyboard dimensions.
     * Returns null if word not found in precomputed cache.
     * 
     * @param word The word to get gestures for
     * @param keyboardWidth Width to scale gestures to
     * @param keyboardHeight Height to scale gestures to
     * @return List of scaled gestures, or null if not precomputed
     */
    fun getScaledGestures(
        word: String,
        keyboardWidth: Float,
        keyboardHeight: Float
    ): List<StatisticalGlideTypingClassifier.Gesture>? {
        val normalizedGestures = gestureCache[word] ?: return null
        
        return normalizedGestures.map { normalized ->
            val gesture = StatisticalGlideTypingClassifier.Gesture()
            
            // Normalized data is [x1, y1, x2, y2, ...]
            // Scale each point to keyboard dimensions
            var i = 0
            while (i < normalized.size) {
                val normalizedX = normalized[i]
                val normalizedY = normalized[i + 1]
                
                val scaledX = normalizedX * keyboardWidth
                val scaledY = normalizedY * keyboardHeight
                
                gesture.addPoint(scaledX, scaledY)
                i += 2
            }
            
            gesture
        }
    }
    
    /**
     * Check if a word has precomputed gestures.
     */
    fun has(word: String): Boolean = gestureCache.containsKey(word)
    
    /**
     * Get the number of precomputed sample points per gesture.
     */
    fun getSamplePoints(): Int = PRECOMPUTED_SAMPLE_POINTS
}
