package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.PlaceholderLoadingKeyboard
import dev.patrickgold.florisboard.ime.keyboard.SmartbarQuickActionsKeyboard
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Characterizes the semantics each of the five `TextKeyboard` construction sites produces today.
 *
 * The recurring theme is that `bottomModRowCount` defaults to 2 and `isAlpha` defaults to true, so
 * every site that does not set them explicitly inherits geometry semantics it never declared —
 * including sites with no rows at all.
 */
class ConstructionSiteContractTest {

    // -- Site 1: normal bundled composition (mergeLayouts) ---------------------------------

    @COMPATIBILITY("Default Coding composes 6 rows and declares 2 bottom modifier rows.")
    @Test
    fun `bundled composition reports six rows and two bottom mod rows`() {
        val keyboard = GeometryFixtures.defaultCoding()

        assertEquals(6, keyboard.rowCount)
        assertEquals(2, keyboard.bottomModRowCount)
    }

    @KNOWN_DEFECT(
        "bottomModRowCount is not a count. With utilities hidden the primary action row survives " +
            "composition but the keyboard reports zero bottom modifier rows.",
    )
    @Test
    fun `hidden coding utilities keep the primary row but report zero bottom mod rows`() {
        val keyboard = GeometryFixtures.codingUtilitiesHidden()

        assertEquals(4, keyboard.rowCount)
        assertEquals(0, keyboard.bottomModRowCount)
        // The primary action row is still present and still carries Space.
        val lastRow = keyboard.arrangement.last()
        assertTrue(lastRow.any { it.computedData.code == 32 })
    }

    // -- Site 2: layout-pack composition ---------------------------------------------------

    @KNOWN_DEFECT(
        "Layout-pack rows lose row identity and every key defaults to alpha, so a pack row is " +
            "indistinguishable from an alpha row downstream.",
    )
    @Test
    fun `layout pack keys all default to alpha`() {
        val keyboard = GeometryFixtures.layoutPackWithSpacersAndUnits()

        val firstRow = keyboard.arrangement.first()
        assertTrue(firstRow.all { it.isAlpha }, "expected every layout-pack key to read as alpha")
    }

    // -- Site 3: Editing empty sentinel ----------------------------------------------------

    @KNOWN_DEFECT(
        "The Editing sentinel has no rows, yet inherits bottomModRowCount = 2 from the constructor " +
            "default, so it claims modifier rows it does not have.",
    )
    @Test
    fun `editing empty sentinel inherits constructor defaults despite having no rows`() {
        val sentinel = TextKeyboard(
            arrangement = emptyArray(),
            mode = KeyboardMode.EDITING,
            extendedPopupMapping = null,
            extendedPopupMappingDefault = null,
        )

        assertEquals(0, sentinel.rowCount)
        assertEquals(2, sentinel.bottomModRowCount)
    }

    // -- Site 4: loading placeholder -------------------------------------------------------

    @COMPATIBILITY("The loading placeholder is a 4-row CHARACTERS keyboard.")
    @Test
    fun `placeholder loading keyboard has four rows in characters mode`() {
        assertEquals(4, PlaceholderLoadingKeyboard.rowCount)
        assertEquals(KeyboardMode.CHARACTERS, PlaceholderLoadingKeyboard.mode)
    }

    @KNOWN_DEFECT(
        "The placeholder declares no row semantics: it inherits bottomModRowCount = 2 and its " +
            "code-0 filler keys inherit isAlpha = true.",
    )
    @Test
    fun `placeholder rows carry no explicit semantics`() {
        assertEquals(2, PlaceholderLoadingKeyboard.bottomModRowCount)
        val fillerKeys = PlaceholderLoadingKeyboard.arrangement.first()
        assertTrue(fillerKeys.all { it.isAlpha }, "expected placeholder filler keys to read as alpha")
    }

    // -- Site 5: smartbar quick-actions empty sentinel --------------------------------------

    @KNOWN_DEFECT(
        "The smartbar sentinel has no rows but also inherits bottomModRowCount = 2.",
    )
    @Test
    fun `smartbar quick actions sentinel inherits constructor defaults despite having no rows`() {
        assertEquals(0, SmartbarQuickActionsKeyboard.rowCount)
        assertEquals(2, SmartbarQuickActionsKeyboard.bottomModRowCount)
        assertEquals(KeyboardMode.SMARTBAR_QUICK_ACTIONS, SmartbarQuickActionsKeyboard.mode)
    }

    // -- Cross-site invariant --------------------------------------------------------------

    @KNOWN_DEFECT(
        "There is no TextKeyboard copy/clone path, so every construction site must set semantics " +
            "itself; three of the five currently do not.",
    )
    @Test
    fun `every empty-arrangement site claims two bottom mod rows`() {
        val emptySites = listOf(
            TextKeyboard(emptyArray(), KeyboardMode.EDITING, null, null),
            SmartbarQuickActionsKeyboard,
        )

        for (site in emptySites) {
            assertEquals(0, site.rowCount, "${site.mode} should have no rows")
            assertEquals(2, site.bottomModRowCount, "${site.mode} inherits the default mod row count")
        }
    }
}
