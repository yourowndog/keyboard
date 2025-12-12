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
        private const val ASSET_PATH = "ime/swipe/precomputed_gestures.json"
        private const val PRECOMPUTED_SAMPLE_POINTS = 50
    }
    
    /**
     * Load precomputed gestures from assets.
     * This is called once on initialization and runs in background.
     */
    fun load() {
        if (isLoaded) return
        
        try {
            Log.d(TAG, "Loading precomputed gestures from $ASSET_PATH...")
            val startTime = System.currentTimeMillis()
            
            val inputStream = context.assets.open(ASSET_PATH)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonText = reader.readText()
            reader.close()
            
            val jsonObject = JSONObject(jsonText)
            val words = jsonObject.keys()
            
            while (words.hasNext()) {
                val word = words.next()
                val gesturesArray = jsonObject.getJSONArray(word)
                val gestures = mutableListOf<FloatArray>()
                
                for (i in 0 until gesturesArray.length()) {
                    val gestureArray = gesturesArray.getJSONArray(i)
                    val points = FloatArray(gestureArray.length())
                    for (j in 0 until gestureArray.length()) {
                        points[j] = gestureArray.getDouble(j).toFloat()
                    }
                    gestures.add(points)
                }
                
                gestureCache[word] = gestures
            }
            
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
