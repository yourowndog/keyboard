package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.KeyboardSemantics
import dev.patrickgold.florisboard.ime.keyboard.LayoutPackRowSemantics
import dev.patrickgold.florisboard.ime.keyboard.NormalizedRow
import dev.patrickgold.florisboard.ime.keyboard.NormalizedRowsBuilder
import dev.patrickgold.florisboard.ime.keyboard.PackRoleSource
import dev.patrickgold.florisboard.ime.keyboard.PlaceholderLoadingKeyboard
import dev.patrickgold.florisboard.ime.keyboard.RowProvenance
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.SentinelKind
import dev.patrickgold.florisboard.ime.keyboard.SmartbarQuickActionsKeyboard
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Characterizes the semantics each of the five `TextKeyboard` construction sites produces today.
 *
 * Stage 00 recorded that `bottomModRowCount` defaults to 2 and `isAlpha` defaults to true, so every
 * site that did not set them inherited geometry semantics it never declared — including sites with
 * no rows at all. Stage 01 does not remove those projections; it adds an explicit semantic contract
 * beside them, so each site now states what its rows *are* even where the legacy numbers stay put.
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

    @Test
    fun `bundled composition declares an explicit role for every row`() {
        val keyboard = GeometryFixtures.defaultCoding()

        assertEquals(
            listOf(
                SemanticRowRole.ALPHA,
                SemanticRowRole.ALPHA,
                SemanticRowRole.ALPHA,
                SemanticRowRole.PRIMARY_ACTION,
                SemanticRowRole.CODING_UTILITY,
                SemanticRowRole.CODING_UTILITY,
            ),
            keyboard.semanticRows.map { it.role },
        )
        assertEquals(
            listOf("alpha:0", "alpha:1", "alpha:2", "primary_action", "coding_utility:0", "coding_utility:1"),
            keyboard.semanticRows.map { it.stableId },
        )
    }

    @Test
    fun `the primary action row is neither an alpha row nor a coding utility row`() {
        val keyboard = GeometryFixtures.defaultCoding()

        val primary = keyboard.semanticRows.single { it.role == SemanticRowRole.PRIMARY_ACTION }
        assertEquals("primary_action", primary.stableId)
        assertEquals(3, keyboard.semanticRows.indexOf(primary))
        assertEquals(3, keyboard.rowsWithRole(SemanticRowRole.ALPHA).size)
        assertEquals(2, keyboard.rowsWithRole(SemanticRowRole.CODING_UTILITY).size)
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

    @Test
    fun `hiding coding utilities removes only the utility rows`() {
        val full = GeometryFixtures.defaultCoding()
        val hidden = GeometryFixtures.codingUtilitiesHidden()

        // The utility rows are gone.
        assertEquals(2, full.rowsWithRole(SemanticRowRole.CODING_UTILITY).size)
        assertTrue(hidden.rowsWithRole(SemanticRowRole.CODING_UTILITY).isEmpty())

        // Nothing else was removed, reclassified, or renumbered. In particular the primary action
        // row is still the primary action row and no alpha row was mistaken for a utility row.
        assertEquals(
            full.semanticRows.filter { it.role != SemanticRowRole.CODING_UTILITY },
            hidden.semanticRows,
        )
    }

    @Test
    fun `compact coding keeps its primary action row despite matching Text's shape`() {
        val hidden = GeometryFixtures.codingUtilitiesHidden()
        val text = GeometryFixtures.characters()

        // Compact Coding and Text have the same row count and the same bottom row shape. Any code
        // that inferred identity from either would conflate them, so roles are carried, not derived.
        assertEquals(text.rowCount, hidden.rowCount)
        assertNotNull(hidden.semanticRows.single { it.role == SemanticRowRole.PRIMARY_ACTION })
        assertNotNull(text.semanticRows.single { it.role == SemanticRowRole.PRIMARY_ACTION })
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

    @Test
    fun `layout pack rows carry pack provenance and flag inferred roles`() {
        val keyboard = GeometryFixtures.layoutPackWithSpacersAndUnits()

        assertEquals(3, keyboard.semanticRows.size)
        for (row in keyboard.semanticRows) {
            val provenance = row.provenance
            assertTrue(provenance is RowProvenance.Pack, "expected pack provenance, got $provenance")
            // The role was inferred, and the row says so rather than passing it off as pack metadata.
            assertEquals(PackRoleSource.COMPATIBILITY_FALLBACK, provenance.roleSource)
            assertEquals(LayoutPackRowSemantics.COMPATIBILITY_FALLBACK_ROLE, row.role)
        }
    }

    @Test
    fun `layout pack rows that declare a role are taken at their word`() {
        val keyboard = GeometryFixtures.layoutPackWithDeclaredRoles()

        assertEquals(
            listOf(SemanticRowRole.ALPHA, SemanticRowRole.ALPHA, SemanticRowRole.PRIMARY_ACTION),
            keyboard.semanticRows.map { it.role },
        )
        for (row in keyboard.semanticRows) {
            assertEquals(PackRoleSource.DECLARED_ROW_ID, (row.provenance as RowProvenance.Pack).roleSource)
        }
    }

    @Test
    fun `pack role resolution is deterministic for the same row id`() {
        assertEquals(LayoutPackRowSemantics.resolve("row-1"), LayoutPackRowSemantics.resolve("row-1"))
        assertEquals(
            SemanticRowRole.CODING_UTILITY to PackRoleSource.DECLARED_ROW_ID,
            LayoutPackRowSemantics.resolve("  Coding_Utility "),
        )
        assertEquals(
            LayoutPackRowSemantics.COMPATIBILITY_FALLBACK_ROLE to PackRoleSource.COMPATIBILITY_FALLBACK,
            LayoutPackRowSemantics.resolve(""),
        )
    }

    // -- Site 3: Editing empty sentinel ----------------------------------------------------

    @KNOWN_DEFECT(
        "The Editing sentinel has no rows, yet still reports bottomModRowCount = 2. Stage 01 made " +
            "the value explicit rather than inherited, but could not correct it: " +
            "FlorisImeSizing.keyboardUiHeight() coerces a zero row count up to 4 and partitions it " +
            "with this number, so changing it to 0 moves real pixels.",
    )
    @Test
    fun `editing empty sentinel still reports two bottom mod rows`() {
        val sentinel = GeometryFixtures.sentinel(KeyboardMode.EDITING, SentinelKind.EDITING)

        assertEquals(0, sentinel.rowCount)
        assertEquals(2, sentinel.bottomModRowCount)
    }

    @Test
    fun `editing empty sentinel identifies itself as a sentinel`() {
        val sentinel = GeometryFixtures.sentinel(KeyboardMode.EDITING, SentinelKind.EDITING)

        assertEquals(SentinelKind.EDITING, sentinel.sentinelKind)
        assertTrue(sentinel.semanticRows.isEmpty(), "a sentinel has no semantic rows")
    }

    // -- Site 4: loading placeholder -------------------------------------------------------

    @COMPATIBILITY("The loading placeholder is a 4-row CHARACTERS keyboard.")
    @Test
    fun `placeholder loading keyboard has four rows in characters mode`() {
        assertEquals(4, PlaceholderLoadingKeyboard.rowCount)
        assertEquals(KeyboardMode.CHARACTERS, PlaceholderLoadingKeyboard.mode)
    }

    @KNOWN_DEFECT(
        "The placeholder's compatibility projection is still legacy: bottomModRowCount = 2 and its " +
            "code-0 filler keys still read as isAlpha = true. Stage 01 declares both explicitly " +
            "instead of inheriting them, but does not change either value.",
    )
    @Test
    fun `placeholder keeps its legacy compatibility projection`() {
        assertEquals(2, PlaceholderLoadingKeyboard.bottomModRowCount)
        val fillerKeys = PlaceholderLoadingKeyboard.arrangement.first()
        assertTrue(fillerKeys.all { it.isAlpha }, "expected placeholder filler keys to read as alpha")
    }

    @Test
    fun `placeholder rows are all placeholder rows`() {
        assertEquals(
            List(4) { SemanticRowRole.PLACEHOLDER },
            PlaceholderLoadingKeyboard.semanticRows.map { it.role },
        )
        assertTrue(
            PlaceholderLoadingKeyboard.semanticRows.all { it.provenance == RowProvenance.Synthetic },
            "loading rows come from no asset",
        )
        // Loading chrome is not an alpha row, an action row, or a utility row.
        assertNull(PlaceholderLoadingKeyboard.semanticRows.firstOrNull { it.role != SemanticRowRole.PLACEHOLDER })
    }

    // -- Site 5: smartbar quick-actions empty sentinel --------------------------------------

    @KNOWN_DEFECT(
        "The smartbar sentinel has no rows but still reports bottomModRowCount = 2, for the same " +
            "pixel-coupling reason as the Editing sentinel.",
    )
    @Test
    fun `smartbar quick actions sentinel still reports two bottom mod rows`() {
        assertEquals(0, SmartbarQuickActionsKeyboard.rowCount)
        assertEquals(2, SmartbarQuickActionsKeyboard.bottomModRowCount)
        assertEquals(KeyboardMode.SMARTBAR_QUICK_ACTIONS, SmartbarQuickActionsKeyboard.mode)
    }

    @Test
    fun `smartbar quick actions sentinel identifies itself as a sentinel`() {
        assertEquals(SentinelKind.SMARTBAR_QUICK_ACTIONS, SmartbarQuickActionsKeyboard.sentinelKind)
        assertTrue(SmartbarQuickActionsKeyboard.semanticRows.isEmpty())
    }

    // -- Cross-site invariant --------------------------------------------------------------

    @KNOWN_DEFECT(
        "Both empty-arrangement sites still report two bottom mod rows. The number is now stated " +
            "at each site rather than inherited, but it is still wrong and still load-bearing.",
    )
    @Test
    fun `every empty-arrangement site claims two bottom mod rows`() {
        val emptySites = listOf(
            GeometryFixtures.sentinel(KeyboardMode.EDITING, SentinelKind.EDITING),
            SmartbarQuickActionsKeyboard,
        )

        for (site in emptySites) {
            assertEquals(0, site.rowCount, "${site.mode} should have no rows")
            assertEquals(2, site.bottomModRowCount, "${site.mode} still reports the legacy mod row count")
        }
    }

    @Test
    fun `every empty-arrangement site declares itself a sentinel`() {
        val emptySites = listOf(
            GeometryFixtures.sentinel(KeyboardMode.EDITING, SentinelKind.EDITING),
            SmartbarQuickActionsKeyboard,
        )

        for (site in emptySites) {
            assertTrue(
                site.semantics is KeyboardSemantics.Sentinel,
                "${site.mode} must identify itself as a sentinel rather than inherit row defaults",
            )
        }
    }

    // -- Model validation -------------------------------------------------------------------

    @Test
    fun `duplicate stable row IDs fail validation`() {
        val duplicated = KeyboardSemantics.Rows(
            listOf(
                NormalizedRow("alpha:0", SemanticRowRole.ALPHA, RowProvenance.Synthetic),
                NormalizedRow("alpha:0", SemanticRowRole.ALPHA, RowProvenance.Synthetic),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            TextKeyboard(
                arrangement = arrayOf(GeometryFixtures.alphaRow(3), GeometryFixtures.alphaRow(3)),
                mode = KeyboardMode.CHARACTERS,
                extendedPopupMapping = null,
                extendedPopupMappingDefault = null,
                semantics = duplicated,
            )
        }
        assertTrue(error.message!!.contains("duplicate stable row IDs"), error.message!!)
    }

    @Test
    fun `blank stable row IDs fail validation`() {
        assertFailsWith<IllegalArgumentException> {
            TextKeyboard(
                arrangement = arrayOf(GeometryFixtures.alphaRow(3)),
                mode = KeyboardMode.CHARACTERS,
                extendedPopupMapping = null,
                extendedPopupMappingDefault = null,
                semantics = KeyboardSemantics.Rows(
                    listOf(NormalizedRow("  ", SemanticRowRole.ALPHA, RowProvenance.Synthetic)),
                ),
            )
        }
    }

    @Test
    fun `missing row semantics fail validation`() {
        assertFailsWith<IllegalArgumentException> {
            TextKeyboard(
                arrangement = arrayOf(GeometryFixtures.alphaRow(3), GeometryFixtures.alphaRow(3)),
                mode = KeyboardMode.CHARACTERS,
                extendedPopupMapping = null,
                extendedPopupMappingDefault = null,
                semantics = NormalizedRowsBuilder()
                    .apply { add(SemanticRowRole.ALPHA, RowProvenance.Synthetic) }
                    .build(),
            )
        }
    }

    @Test
    fun `a sentinel with rows fails validation`() {
        assertFailsWith<IllegalArgumentException> {
            TextKeyboard(
                arrangement = arrayOf(GeometryFixtures.alphaRow(3)),
                mode = KeyboardMode.EDITING,
                extendedPopupMapping = null,
                extendedPopupMappingDefault = null,
                semantics = KeyboardSemantics.Sentinel(SentinelKind.EDITING),
            )
        }
    }

    @Test
    fun `stable IDs are role-scoped, not positional`() {
        val withoutExtensions = GeometryFixtures.defaultCoding()
        val withExtensions = GeometryFixtures.codingWithBothExtensions()

        // Two extension rows are inserted above the alpha rows. The alpha rows keep their IDs.
        val alphaIds = { kb: TextKeyboard ->
            kb.rowsWithRole(SemanticRowRole.ALPHA).map { it.stableId }
        }
        assertEquals(alphaIds(withoutExtensions), alphaIds(withExtensions))
        assertEquals(listOf("alpha:0", "alpha:1", "alpha:2"), alphaIds(withExtensions))
    }
}
