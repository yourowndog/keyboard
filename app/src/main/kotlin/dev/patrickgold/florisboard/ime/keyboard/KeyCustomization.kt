/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.keyboard

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Represents per-key customization settings for padding, height, and width.
 */
@Serializable
data class KeyCustomization(
    val paddingTop: Float = 0f,
    val paddingBottom: Float = 0f,
    val paddingLeft: Float = 0f,
    val paddingRight: Float = 0f,
    val heightFactor: Float = 1.0f,
    val widthFactor: Float = 1.0f,
)

/**
 * Manager for per-key customizations stored as JSON in preferences.
 */
object KeyCustomizationManager {
    const val NEUTRAL_JSON = "{}"

    data class NeutralReset(
        val activeJson: String,
        val backupJson: String,
    )

    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Parses a JSON string into a map of KeyCode -> KeyCustomization.
     */
    fun parseFromJson(jsonString: String): Map<Int, KeyCustomization> {
        if (jsonString.isBlank() || jsonString == "{}") {
            return emptyMap()
        }
        return try {
            json.decodeFromString<Map<Int, KeyCustomization>>(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Serializes a map of KeyCode -> KeyCustomization to JSON string.
     */
    fun toJson(customizations: Map<Int, KeyCustomization>): String {
        return json.encodeToString(customizations)
    }

    /**
     * Preserves the exact legacy payload while activating a genuinely neutral override layer.
     */
    fun neutralReset(currentJson: String): NeutralReset {
        return NeutralReset(
            activeJson = NEUTRAL_JSON,
            backupJson = currentJson,
        )
    }
    
    /**
     * Gets customization for a specific key code, or null if not set.
     */
    fun getForKey(jsonString: String, keyCode: Int): KeyCustomization? {
        return parseFromJson(jsonString)[keyCode]
    }
    
    /**
     * Updates customization for a specific key code and returns new JSON string.
     */
    fun setForKey(jsonString: String, keyCode: Int, customization: KeyCustomization): String {
        val map = parseFromJson(jsonString).toMutableMap()
        // Remove if it's the default values to keep JSON clean
        if (customization == KeyCustomization()) {
            map.remove(keyCode)
        } else {
            map[keyCode] = customization
        }
        return toJson(map)
    }
    
    /**
     * List of keys that can be customized in the UI.
     */
    val customizableKeys = listOf(
        CustomizableKey(32, "Space"),        // KeyCode.SPACE
        CustomizableKey(10, "Enter"),        // KeyCode.ENTER
        CustomizableKey(-11, "Shift"),       // KeyCode.SHIFT
        CustomizableKey(-1, "Control"),      // KeyCode.CTRL
        CustomizableKey(-7, "Backspace"),    // KeyCode.DELETE
        CustomizableKey(-14, "Tab"),         // KeyCode.TAB
        CustomizableKey(-23, "Arrow Up"),    // KeyCode.ARROW_UP
        CustomizableKey(-24, "Arrow Down"),  // KeyCode.ARROW_DOWN
        CustomizableKey(-21, "Arrow Left"),  // KeyCode.ARROW_LEFT
        CustomizableKey(-22, "Arrow Right"), // KeyCode.ARROW_RIGHT
    )
    
    data class CustomizableKey(val code: Int, val label: String)
}
