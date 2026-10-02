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

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * Identifies which layer a customization parameter belongs to.
 *
 * Every customization field must declare exactly one layer:
 * - [STRUCTURAL]: changes solver input and reflows adjacent elements;
 * - [TOUCH]: changes derived pressable bounds under constraints;
 * - [VISUAL]: changes painted bounds/insets only without moving structural borders or stealing touches.
 */
@Serializable
enum class CustomizationLayer {
    @SerialName("structural")
    STRUCTURAL,

    @SerialName("touch")
    TOUCH,

    @SerialName("visual")
    VISUAL,
}

/**
 * Explicit, multi-level target for key customizations.
 *
 * Solves requirement 1 of Stage 07: replace global integer-key-code targeting for structural edits with stable:
 * - [profileId]: e.g. "coding", "text";
 * - [layoutId]: optional layout identifier (e.g. "org.florisboard.layouts:qwerty");
 * - [rowId]: semantic or pack row stable ID (e.g. "primary_action", "alpha:0");
 * - [itemInstanceId]: stable key/item instance identifier within the row (e.g. "alpha:0#4", "primary_action#2");
 * - [keyCode]: fallback / secondary key code filter.
 */
@Serializable
data class CustomizationTarget(
    val profileId: String = "",
    val layoutId: String? = null,
    val rowId: String? = null,
    val itemInstanceId: String? = null,
    val keyCode: Int? = null,
) {
    /** Formats a compact canonical string key for map indexing. */
    fun toCanonicalKey(): String = buildString {
        if (profileId.isNotEmpty()) append("$profileId/")
        if (layoutId != null) append("$layoutId/")
        if (rowId != null) append("$rowId/")
        if (itemInstanceId != null) append(itemInstanceId)
        else if (keyCode != null) append("code:$keyCode")
        else append("*")
    }

    fun matches(
        candidateProfileId: String,
        candidateLayoutId: String?,
        candidateRowId: String,
        candidateInstanceId: String,
        candidateKeyCode: Int,
    ): Boolean {
        if (profileId.isNotEmpty() && profileId != candidateProfileId) return false
        if (layoutId != null && candidateLayoutId != null && layoutId != candidateLayoutId) return false
        if (rowId != null && rowId != candidateRowId) return false
        if (itemInstanceId != null && itemInstanceId != candidateInstanceId) return false
        if (keyCode != null && keyCode != candidateKeyCode) return false
        return true
    }
}

/**
 * Structural customization: alters solver input before allocation and reflows neighboring elements.
 */
@Serializable
data class StructuralCustomization(
    val widthFactor: Float = 1.0f,
    val growWeightDelta: Float = 0.0f,
    val heightFactor: Float = 1.0f,
) {
    fun isDefault(): Boolean = widthFactor == 1.0f && growWeightDelta == 0.0f && heightFactor == 1.0f

    fun sanitized(): StructuralCustomization = StructuralCustomization(
        widthFactor = if (widthFactor.isFinite()) widthFactor.coerceIn(0.2f, 3.0f) else 1.0f,
        growWeightDelta = if (growWeightDelta.isFinite()) growWeightDelta.coerceIn(-1.0f, 10.0f) else 0.0f,
        heightFactor = if (heightFactor.isFinite()) heightFactor.coerceIn(0.2f, 3.0f) else 1.0f,
    )
}

/**
 * Touch customization: expands or contracts pressable hitboxes without altering visual rendering.
 */
@Serializable
data class TouchCustomization(
    val touchMarginHorizontalDp: Float = 0.0f,
    val touchMarginVerticalDp: Float = 0.0f,
) {
    fun isDefault(): Boolean = touchMarginHorizontalDp == 0.0f && touchMarginVerticalDp == 0.0f

    fun sanitized(): TouchCustomization = TouchCustomization(
        touchMarginHorizontalDp = if (touchMarginHorizontalDp.isFinite()) touchMarginHorizontalDp.coerceIn(-20f, 20f) else 0.0f,
        touchMarginVerticalDp = if (touchMarginVerticalDp.isFinite()) touchMarginVerticalDp.coerceIn(-20f, 20f) else 0.0f,
    )
}

/**
 * Visual customization: insets or expands painted keycaps and internal padding only.
 * Adjacent keys and structural boundaries are untouched, preventing touch stealing and dead strips.
 */
@Serializable
data class VisualCustomization(
    val paddingTop: Float = 0f,
    val paddingBottom: Float = 0f,
    val paddingLeft: Float = 0f,
    val paddingRight: Float = 0f,
    val visualScaleX: Float = 1.0f,
    val visualScaleY: Float = 1.0f,
) {
    fun isDefault(): Boolean =
        paddingTop == 0f && paddingBottom == 0f && paddingLeft == 0f && paddingRight == 0f &&
        visualScaleX == 1.0f && visualScaleY == 1.0f

    fun sanitized(): VisualCustomization = VisualCustomization(
        paddingTop = if (paddingTop.isFinite()) paddingTop.coerceIn(0f, 40f) else 0f,
        paddingBottom = if (paddingBottom.isFinite()) paddingBottom.coerceIn(0f, 40f) else 0f,
        paddingLeft = if (paddingLeft.isFinite()) paddingLeft.coerceIn(0f, 40f) else 0f,
        paddingRight = if (paddingRight.isFinite()) paddingRight.coerceIn(0f, 40f) else 0f,
        visualScaleX = if (visualScaleX.isFinite()) visualScaleX.coerceIn(0.2f, 2.0f) else 1.0f,
        visualScaleY = if (visualScaleY.isFinite()) visualScaleY.coerceIn(0.2f, 2.0f) else 1.0f,
    )
}

/**
 * Represents per-key customization settings declaring structural, touch, and visual layers.
 */
@Serializable
data class KeyCustomization(
    val target: CustomizationTarget = CustomizationTarget(),
    val structural: StructuralCustomization = StructuralCustomization(),
    val touch: TouchCustomization = TouchCustomization(),
    val visual: VisualCustomization = VisualCustomization(),
    // Legacy compatibility fields:
    val paddingTop: Float = visual.paddingTop,
    val paddingBottom: Float = visual.paddingBottom,
    val paddingLeft: Float = visual.paddingLeft,
    val paddingRight: Float = visual.paddingRight,
    val heightFactor: Float = structural.heightFactor,
    val widthFactor: Float = structural.widthFactor,
) {
    constructor(
        widthFactor: Float = 1.0f,
        heightFactor: Float = 1.0f,
        paddingTop: Float = 0f,
        paddingBottom: Float = 0f,
        paddingLeft: Float = 0f,
        paddingRight: Float = 0f,
        target: CustomizationTarget = CustomizationTarget(),
    ) : this(
        target = target,
        structural = StructuralCustomization(heightFactor = heightFactor, widthFactor = widthFactor),
        touch = TouchCustomization(),
        visual = VisualCustomization(
            paddingTop = paddingTop,
            paddingBottom = paddingBottom,
            paddingLeft = paddingLeft,
            paddingRight = paddingRight,
        ),
        paddingTop = paddingTop,
        paddingBottom = paddingBottom,
        paddingLeft = paddingLeft,
        paddingRight = paddingRight,
        heightFactor = heightFactor,
        widthFactor = widthFactor,
    )
    fun isDefault(): Boolean = structural.isDefault() && touch.isDefault() && visual.isDefault()

    fun sanitized(): KeyCustomization = KeyCustomization(
        target = target,
        structural = structural.sanitized(),
        touch = touch.sanitized(),
        visual = visual.sanitized(),
        paddingTop = visual.sanitized().paddingTop,
        paddingBottom = visual.sanitized().paddingBottom,
        paddingLeft = visual.sanitized().paddingLeft,
        paddingRight = visual.sanitized().paddingRight,
        heightFactor = structural.sanitized().heightFactor,
        widthFactor = structural.sanitized().widthFactor,
    )
}

/**
 * An unresolvable or ambiguous entry migrated from legacy JSON format.
 */
@Serializable
data class LegacyUnresolvedEntry(
    val rawKey: String,
    val rawJson: String,
    val reason: String,
)

/**
 * Versioned document schema for key customizations (Version 2 introduced in Stage 07).
 */
@Serializable
data class KeyCustomizationDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val customizations: List<KeyCustomization> = emptyList(),
    val legacyUnresolved: List<LegacyUnresolvedEntry> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }

    fun isEmpty(): Boolean = customizations.isEmpty() && legacyUnresolved.isEmpty()
}

/**
 * Manager for per-key customizations stored as JSON in preferences.
 */
object KeyCustomizationManager {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    const val NO_CUSTOMIZATIONS: String = "{}"

    /**
     * Parses any stored JSON string (either legacy Map<Int, KeyCustomization> or versioned
     * [KeyCustomizationDocument]) into a normalized [KeyCustomizationDocument].
     */
    fun parseDocument(jsonString: String): KeyCustomizationDocument {
        if (jsonString.isBlank() || jsonString == NO_CUSTOMIZATIONS || jsonString == "[]") {
            return KeyCustomizationDocument()
        }
        return try {
            val element = json.parseToJsonElement(jsonString)
            if (element is JsonObject && element.containsKey("schemaVersion")) {
                json.decodeFromJsonElement<KeyCustomizationDocument>(element)
            } else if (element is JsonObject) {
                // Legacy format: "{\"32\": {\"widthFactor\": 1.4, ...}}"
                migrateLegacyJson(element)
            } else {
                KeyCustomizationDocument()
            }
        } catch (e: Exception) {
            KeyCustomizationDocument()
        }
    }

    /**
     * Versioned migration from legacy Map<Int, ...> JSON into KeyCustomizationDocument.
     *
     * Rules from Stage 07 spec:
     * - preserve genuinely visual intent as visual;
     * - map structural intent only when a unique instance can be identified safely;
     * - retain ambiguous entries in a recoverable legacy bucket;
     * - never silently apply one old code-based width to every matching key instance.
     */
    fun migrateLegacyJson(root: JsonObject): KeyCustomizationDocument {
        val resolvedList = mutableListOf<KeyCustomization>()
        val unresolvedList = mutableListOf<LegacyUnresolvedEntry>()

        for ((keyStr, valueElem) in root) {
            val code = keyStr.toIntOrNull()
            if (code == null) {
                unresolvedList.add(LegacyUnresolvedEntry(keyStr, valueElem.toString(), "Non-integer key code"))
                continue
            }
            val obj = valueElem as? JsonObject
            if (obj == null) {
                unresolvedList.add(LegacyUnresolvedEntry(keyStr, valueElem.toString(), "Malformed JSON value"))
                continue
            }

            val paddingTop = obj["paddingTop"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
            val paddingBottom = obj["paddingBottom"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
            val paddingLeft = obj["paddingLeft"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
            val paddingRight = obj["paddingRight"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
            val heightFactor = obj["heightFactor"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f
            val widthFactor = obj["widthFactor"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f

            val visual = VisualCustomization(
                paddingTop = paddingTop,
                paddingBottom = paddingBottom,
                paddingLeft = paddingLeft,
                paddingRight = paddingRight,
            )

            val hasStructural = widthFactor != 1.0f || heightFactor != 1.0f
            val hasVisual = !visual.isDefault()

            // Unique structural mapping: Space (32) and Enter (10) in standard primary action row
            // can be safely mapped to primary_action row. Ambiguous keys with multiple instances
            // (like Shift, Ctrl, or common letters) must not be broadcast structurally.
            if (hasStructural) {
                when (code) {
                    KeyCode.SPACE -> {
                        resolvedList.add(
                            KeyCustomization(
                                target = CustomizationTarget(
                                    rowId = "primary_action",
                                    keyCode = KeyCode.SPACE,
                                ),
                                structural = StructuralCustomization(
                                    widthFactor = widthFactor,
                                    heightFactor = heightFactor,
                                ),
                                visual = visual,
                            )
                        )
                    }
                    KeyCode.ENTER -> {
                        resolvedList.add(
                            KeyCustomization(
                                target = CustomizationTarget(
                                    rowId = "primary_action",
                                    keyCode = KeyCode.ENTER,
                                ),
                                structural = StructuralCustomization(
                                    widthFactor = widthFactor,
                                    heightFactor = heightFactor,
                                ),
                                visual = visual,
                            )
                        )
                    }
                    else -> {
                        // If it has visual intent, preserve visual intent safely; put structural in unresolved
                        if (hasVisual) {
                            resolvedList.add(
                                KeyCustomization(
                                    target = CustomizationTarget(keyCode = code),
                                    visual = visual,
                                )
                            )
                        }
                        unresolvedList.add(
                            LegacyUnresolvedEntry(
                                rawKey = keyStr,
                                rawJson = valueElem.toString(),
                                reason = "Ambiguous structural instance for key code $code across multiple rows",
                            )
                        )
                    }
                }
            } else if (hasVisual) {
                // Pure visual customization: safe to attach by keyCode
                resolvedList.add(
                    KeyCustomization(
                        target = CustomizationTarget(keyCode = code),
                        visual = visual,
                    )
                )
            }
        }

        return KeyCustomizationDocument(
            schemaVersion = KeyCustomizationDocument.CURRENT_SCHEMA_VERSION,
            customizations = resolvedList,
            legacyUnresolved = unresolvedList,
        )
    }

    /**
     * Parses a JSON string into a legacy map of KeyCode -> KeyCustomization for backward compatibility.
     */
    fun parseFromJson(jsonString: String): Map<Int, KeyCustomization> {
        val doc = parseDocument(jsonString)
        val map = mutableMapOf<Int, KeyCustomization>()
        for (custom in doc.customizations) {
            val code = custom.target.keyCode
            if (code != null) {
                map[code] = custom
            }
        }
        return map
    }

    /**
     * Serializes a [KeyCustomizationDocument] to JSON string.
     */
    fun toDocumentJson(doc: KeyCustomizationDocument): String {
        return if (doc.isEmpty()) {
            NO_CUSTOMIZATIONS
        } else {
            json.encodeToString(doc)
        }
    }

    /**
     * Serializes a map of KeyCode -> KeyCustomization to JSON string (producing versioned document).
     */
    fun toJson(customizations: Map<Int, KeyCustomization>): String {
        if (customizations.isEmpty()) return NO_CUSTOMIZATIONS
        val list = customizations.map { (code, custom) ->
            if (custom.target.keyCode == null) {
                custom.copy(target = custom.target.copy(keyCode = code))
            } else {
                custom
            }
        }
        val doc = KeyCustomizationDocument(customizations = list)
        return toDocumentJson(doc)
    }

    /**
     * Finds customization matching a specific key instance within a row.
     */
    fun findCustomization(
        doc: KeyCustomizationDocument,
        profileId: String,
        layoutId: String?,
        rowId: String,
        itemInstanceId: String,
        keyCode: Int,
    ): KeyCustomization? {
        // 1. Exact match on itemInstanceId
        doc.customizations.firstOrNull {
            it.target.itemInstanceId == itemInstanceId && (it.target.profileId.isEmpty() || it.target.profileId == profileId)
        }?.let { return it }

        // 2. Match on rowId + keyCode
        doc.customizations.firstOrNull {
            it.target.rowId == rowId && it.target.keyCode == keyCode && (it.target.profileId.isEmpty() || it.target.profileId == profileId)
        }?.let { return it }

        // 3. Fallback on keyCode only (only if structural is default or visual/touch)
        doc.customizations.firstOrNull {
            it.target.itemInstanceId == null && it.target.rowId == null && it.target.keyCode == keyCode
        }?.let { return it }

        return null
    }

    /**
     * Gets customization for a specific key code, or null if not set.
     */
    fun getForKey(jsonString: String, keyCode: Int): KeyCustomization? {
        val doc = parseDocument(jsonString)
        return doc.customizations.firstOrNull { it.target.keyCode == keyCode }
    }

    /**
     * Updates or sets customization for a specific target and returns updated JSON.
     */
    fun setCustomization(
        jsonString: String,
        customization: KeyCustomization,
    ): String {
        val doc = parseDocument(jsonString)
        val mutableList = doc.customizations.toMutableList()
        val target = customization.target

        val index = mutableList.indexOfFirst {
            if (target.itemInstanceId != null) it.target.itemInstanceId == target.itemInstanceId
            else if (target.rowId != null && target.keyCode != null) it.target.rowId == target.rowId && it.target.keyCode == target.keyCode
            else it.target.keyCode != null && it.target.keyCode == target.keyCode
        }

        if (customization.isDefault()) {
            if (index >= 0) mutableList.removeAt(index)
        } else {
            if (index >= 0) {
                mutableList[index] = customization.sanitized()
            } else {
                mutableList.add(customization.sanitized())
            }
        }

        val updatedDoc = doc.copy(customizations = mutableList)
        return toDocumentJson(updatedDoc)
    }

    /**
     * Legacy compatibility setter for integer key code.
     */
    fun setForKey(jsonString: String, keyCode: Int, customization: KeyCustomization): String {
        val customWithTarget = if (customization.target.keyCode != keyCode) {
            customization.copy(target = customization.target.copy(keyCode = keyCode))
        } else {
            customization
        }
        return setCustomization(jsonString, customWithTarget)
    }

    /**
     * List of keys that can be customized in the UI.
     */
    val customizableKeys = listOf(
        CustomizableKey(32, "Space", "primary_action"),
        CustomizableKey(10, "Enter", "primary_action"),
        CustomizableKey(-11, "Shift", "alpha:2"),
        CustomizableKey(-1, "Control", "coding_utility:0"),
        CustomizableKey(-7, "Backspace", "alpha:0"),
        CustomizableKey(-14, "Tab", "primary_action"),
        CustomizableKey(-23, "Arrow Up", "coding_utility:0"),
        CustomizableKey(-24, "Arrow Down", "coding_utility:0"),
        CustomizableKey(-21, "Arrow Left", "coding_utility:0"),
        CustomizableKey(-22, "Arrow Right", "coding_utility:0"),
    )

    data class CustomizableKey(
        val code: Int,
        val label: String,
        val defaultRowId: String? = null,
    )
}
