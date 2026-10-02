/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.CustomizationLayer
import dev.patrickgold.florisboard.ime.keyboard.CustomizationTarget
import dev.patrickgold.florisboard.ime.keyboard.KeyCustomization
import dev.patrickgold.florisboard.ime.keyboard.KeyCustomizationDocument
import dev.patrickgold.florisboard.ime.keyboard.KeyCustomizationManager
import dev.patrickgold.florisboard.ime.keyboard.SemanticItemKind
import dev.patrickgold.florisboard.ime.keyboard.SemanticRow
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowItem
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.StructuralCustomization
import dev.patrickgold.florisboard.ime.keyboard.TouchCustomization
import dev.patrickgold.florisboard.ime.keyboard.VisualCustomization
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveCustomizationTest {

    @Test
    fun `schema version 2 roundtrips all three layers`() {
        val original = KeyCustomizationDocument(
            schemaVersion = 2,
            customizations = listOf(
                KeyCustomization(
                    target = CustomizationTarget(
                        profileId = "coding",
                        layoutId = "org.florisboard.layouts:qwerty",
                        rowId = "primary_action",
                        itemInstanceId = "space_1",
                        keyCode = KeyCode.SPACE,
                    ),
                    structural = StructuralCustomization(widthFactor = 1.4f, growWeightDelta = 0.5f, heightFactor = 1.1f),
                    touch = TouchCustomization(touchMarginHorizontalDp = 4f, touchMarginVerticalDp = 2f),
                    visual = VisualCustomization(paddingTop = 2f, paddingBottom = 2f, paddingLeft = 4f, paddingRight = 4f),
                ),
            ),
        )

        val jsonStr = KeyCustomizationManager.toDocumentJson(original)
        val parsed = KeyCustomizationManager.parseDocument(jsonStr)

        assertEquals(2, parsed.schemaVersion)
        assertEquals(1, parsed.customizations.size)
        val custom = parsed.customizations.first()
        assertEquals("coding", custom.target.profileId)
        assertEquals("space_1", custom.target.itemInstanceId)
        assertEquals(1.4f, custom.structural.widthFactor, 0.001f)
        assertEquals(0.5f, custom.structural.growWeightDelta, 0.001f)
        assertEquals(4f, custom.touch.touchMarginHorizontalDp, 0.001f)
        assertEquals(2f, custom.visual.paddingTop, 0.001f)
    }

    @Test
    fun `legacy json migration preserves visual intent and segregates ambiguous structural entries`() {
        // Legacy map format: Space (32) and Enter (10) can be mapped safely;
        // an ambiguous key like 65 ('A') with structural width should not be broadcast blindly
        val legacyJson = """{
            "32": {"widthFactor": 1.5, "paddingTop": 4.0},
            "65": {"widthFactor": 1.2, "paddingLeft": 2.0}
        }""".trimIndent()

        val doc = KeyCustomizationManager.parseDocument(legacyJson)
        assertEquals(KeyCustomizationDocument.CURRENT_SCHEMA_VERSION, doc.schemaVersion)

        // Space (32) mapped structural and visual safely to primary_action
        val spaceCustom = doc.customizations.firstOrNull { it.target.keyCode == 32 }
        assertNotNull(spaceCustom)
        assertEquals(1.5f, spaceCustom!!.structural.widthFactor, 0.001f)
        assertEquals(4.0f, spaceCustom.visual.paddingTop, 0.001f)
        assertEquals("primary_action", spaceCustom.target.rowId)

        // Key 65 ('A'): visual is preserved, but structural is isolated in legacyUnresolved
        val aCustom = doc.customizations.firstOrNull { it.target.keyCode == 65 }
        assertNotNull(aCustom)
        assertEquals(2.0f, aCustom!!.visual.paddingLeft, 0.001f)
        assertEquals(1.0f, aCustom.structural.widthFactor, 0.001f) // structural not broadcast

        assertEquals(1, doc.legacyUnresolved.size)
        assertEquals("65", doc.legacyUnresolved.first().rawKey)
        assertTrue(doc.legacyUnresolved.first().reason.contains("Ambiguous"))
    }

    @Test
    fun `customization target matching works hierarchically`() {
        val doc = KeyCustomizationDocument(
            customizations = listOf(
                KeyCustomization(
                    target = CustomizationTarget(itemInstanceId = "alpha:0#1"),
                    structural = StructuralCustomization(widthFactor = 1.2f),
                ),
                KeyCustomization(
                    target = CustomizationTarget(rowId = "primary_action", keyCode = KeyCode.SPACE),
                    structural = StructuralCustomization(widthFactor = 1.8f),
                ),
            ),
        )

        // Exact match by itemInstanceId
        val match1 = KeyCustomizationManager.findCustomization(
            doc = doc,
            profileId = "default",
            layoutId = null,
            rowId = "alpha:0",
            itemInstanceId = "alpha:0#1",
            keyCode = 113,
        )
        assertNotNull(match1)
        assertEquals(1.2f, match1!!.structural.widthFactor, 0.001f)

        // Match by rowId + keyCode
        val match2 = KeyCustomizationManager.findCustomization(
            doc = doc,
            profileId = "default",
            layoutId = null,
            rowId = "primary_action",
            itemInstanceId = "primary_action#3",
            keyCode = KeyCode.SPACE,
        )
        assertNotNull(match2)
        assertEquals(1.8f, match2!!.structural.widthFactor, 0.001f)

        // Unmatched key
        val matchNone = KeyCustomizationManager.findCustomization(
            doc = doc,
            profileId = "default",
            layoutId = null,
            rowId = "alpha:1",
            itemInstanceId = "alpha:1#2",
            keyCode = 97,
        )
        assertNull(matchNone)
    }

    @Test
    fun `solver item width and grow weight overrides take effect responsive to geometry`() {
        val row = GeometryRow(
            stableId = "row_test",
            role = SemanticRowRole.PRIMARY_ACTION,
            items = listOf(
                GeometryItem(stableId = "item1", kind = ItemKind.KEY, widthUnits = 1.0, growWeight = 0.0),
                GeometryItem(stableId = "item2_space", kind = ItemKind.KEY, widthUnits = 4.0, growWeight = 1.0),
                GeometryItem(stableId = "item3", kind = ItemKind.KEY, widthUnits = 1.0, growWeight = 0.0),
            ),
        )

        val defaultInput = GeometrySolverInput(
            availableWidth = 600.0,
            rows = listOf(row),
            framePolicy = FramePolicy.Intrinsic(100),
            rowHeightPolicy = RowHeightPolicy(),
            gapPolicy = BoundaryGapPolicy(),
            widthPolicy = RowWidthPolicy(
                sharedReference = SharedWidthReference(measuredRoles = setOf(SemanticRowRole.PRIMARY_ACTION)),
                alignment = RowAlignment.CENTER,
            ),
            overrides = GeometryOverrides(),
        )

        val defaultSolution = KeyboardGeometrySolver.solve(defaultInput) as GeometrySolution.Solved
        val defaultItem1Width = defaultSolution.geometry.rows[0].items[0].bounds.width
        val defaultSpaceWidth = defaultSolution.geometry.rows[0].items[1].bounds.width

        // Now apply structural override: expand item1 by 1.5x width
        val overrideInput = defaultInput.copy(
            overrides = GeometryOverrides(
                itemWidthMultiplierByStableId = mapOf("item1" to 1.5),
            ),
        )
        val overriddenSolution = KeyboardGeometrySolver.solve(overrideInput) as GeometrySolution.Solved
        val overriddenItem1Width = overriddenSolution.geometry.rows[0].items[0].bounds.width
        val overriddenSpaceWidth = overriddenSolution.geometry.rows[0].items[1].bounds.width

        assertTrue(overriddenItem1Width > defaultItem1Width)
        // In a fixed container with flexible space, space absorbs the remainder without breaking row bounds
        assertEquals(600, overriddenSolution.geometry.frame.width)
        assertTrue(overriddenSpaceWidth < defaultSpaceWidth)
    }

    @Test
    fun `structural touch and visual layers have distinct roles and properties`() {
        assertEquals(3, CustomizationLayer.entries.size)
        assertTrue(CustomizationLayer.entries.contains(CustomizationLayer.STRUCTURAL))
        assertTrue(CustomizationLayer.entries.contains(CustomizationLayer.TOUCH))
        assertTrue(CustomizationLayer.entries.contains(CustomizationLayer.VISUAL))
    }
}
