package dev.patrickgold.florisboard.ime.keyboard.pack

import dev.patrickgold.florisboard.ime.keyboard.KeyboardProfile
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryItemKind
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Stage 06 row composition and process-restart contract. */
class LayoutPackCompositionTest {

    private fun conditionalPack(id: String = "conditional") = LayoutPackFixtures.currentPack(
        id = id,
        rows = listOf(
            LayoutPackFixtures.alphaRow(),
            LayoutPackFixtures.rowWithSpacer().copy(
                visibleWhen = LayoutPackVisibility.MOD_ROWS_VISIBLE,
            ),
            LayoutPackRow(
                id = "number",
                role = SemanticRowRole.EXTENSION,
                units = 2f,
                visibleWhen = LayoutPackVisibility.NUMBER_ROW_ENABLED,
                keys = listOf(
                    LayoutPackFixtures.key("1"),
                    LayoutPackFixtures.key("2"),
                ),
            ),
            LayoutPackRow(
                id = "developer",
                role = SemanticRowRole.EXTENSION,
                units = 1f,
                enabled = false,
                visibleWhen = LayoutPackVisibility.DEV_ROW_ENABLED,
                keys = listOf(LayoutPackFixtures.key("ESC")),
            ),
            LayoutPackFixtures.primaryActionRow(),
        ),
    )

    @Test
    fun `disabled rows and false conditions contribute no rows`() {
        val pack = ActiveLayoutPack(conditionalPack())

        val resolved = LayoutPackComposition.resolve(
            pack,
            LayoutPackConditions(
                modRowsVisible = false,
                numberRowEnabled = false,
                devRowEnabled = true,
            ),
        )

        assertEquals(listOf("alpha_top", "primary"), resolved.map { it.rowId })
        assertEquals(
            listOf(SemanticRowRole.ALPHA, SemanticRowRole.PRIMARY_ACTION),
            resolved.map { it.role },
        )
    }

    @Test
    fun `supported visibility conditions reveal only their own rows`() {
        val pack = ActiveLayoutPack(conditionalPack())

        val utility = LayoutPackComposition.resolve(pack, LayoutPackConditions(modRowsVisible = true))
        assertEquals(listOf("alpha_top", "utility", "primary"), utility.map { it.rowId })

        val number = LayoutPackComposition.resolve(
            pack,
            LayoutPackConditions(modRowsVisible = false, numberRowEnabled = true),
        )
        assertEquals(listOf("alpha_top", "number", "primary"), number.map { it.rowId })

        val all = LayoutPackComposition.resolve(
            pack,
            LayoutPackConditions(modRowsVisible = true, numberRowEnabled = true, devRowEnabled = true),
        )
        assertEquals(
            listOf("alpha_top", "utility", "number", "primary"),
            all.map { it.rowId },
            "the disabled developer row stays absent even when its condition is true",
        )
    }

    @Test
    fun `spacers and authored units reach the single geometry input unchanged`() {
        val row = LayoutPackComposition.resolve(
            ActiveLayoutPack(LayoutPackFixtures.currentPack(rows = listOf(LayoutPackFixtures.rowWithSpacer()))),
        ).single()

        val geometry = LayoutPackComposition.geometryRows(listOf(row)).single()

        assertEquals("utility", geometry.stableId)
        assertEquals(SemanticRowRole.CODING_UTILITY, geometry.role)
        assertEquals(listOf(2.0, 8.0, 2.0), geometry.items.map { it.widthUnits })
        assertEquals(
            listOf(GeometryItemKind.KEY, GeometryItemKind.SPACER, GeometryItemKind.KEY),
            geometry.items.map { it.kind },
        )
    }

    @Test
    fun `process restart restores profile selection semantic rows and geometry`() {
        val root = createTempDirectory("layout-pack-restart").toFile()
        try {
            val packs = File(root, "packs")
            val legacy = File(root, "legacy.json")
            val beforeStore = LayoutPackStore(packs, legacy) { null }
            val document = conditionalPack(id = "restart_pack")
            assertIs<LayoutPackStore.SaveResult.Written>(beforeStore.save(document))

            // These are persisted separately in production: profile id in AppPrefs and pack id in
            // selectedLayoutPackId. Reconstructing them independently models a fresh process rather
            // than carrying an ActiveLayoutPack object across the boundary.
            val persistedProfileId = KeyboardProfile.CODING.id
            val persistedSelectedPackId = document.id
            val profileConditions = LayoutPackConditions(
                modRowsVisible = true,
                numberRowEnabled = true,
                devRowEnabled = false,
            )
            val beforeRows = LayoutPackComposition.resolve(ActiveLayoutPack(document), profileConditions)
            val beforeGeometry = LayoutPackComposition.geometryRows(beforeRows)

            val afterStore = LayoutPackStore(packs, legacy) { null }
            val restoration = assertIs<LayoutPackStore.Restoration.Restored>(
                afterStore.restore(persistedSelectedPackId),
            )
            val restoredProfile = KeyboardProfile.fromId(persistedProfileId)
            val afterRows = LayoutPackComposition.resolve(restoration.pack, profileConditions)
            val afterGeometry = LayoutPackComposition.geometryRows(afterRows)

            assertEquals(KeyboardProfile.CODING, restoredProfile)
            assertEquals(document, restoration.pack.document)
            assertEquals(beforeRows.map { it.rowId }, afterRows.map { it.rowId })
            assertEquals(beforeRows.map { it.role }, afterRows.map { it.role })
            assertEquals(beforeGeometry, afterGeometry)
        } finally {
            root.deleteRecursively()
        }
    }
}
