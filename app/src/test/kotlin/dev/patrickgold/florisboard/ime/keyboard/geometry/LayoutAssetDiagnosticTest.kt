package dev.patrickgold.florisboard.ime.keyboard.geometry

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Asset-level diagnostics for the layout files the composition paths read.
 *
 * These read the bundled JSON directly rather than going through `LayoutManager`, which requires a
 * `Context`. That keeps them runnable as plain unit tests while still pinning the asset facts the
 * migration depends on.
 */
class LayoutAssetDiagnosticTest {

    private companion object {
        val LAYOUT_ROOT = File("src/main/assets/ime/keyboard/org.florisboard.layouts/layouts")
        val JSON = Json { ignoreUnknownKeys = true }
    }

    private fun layoutDir(name: String) = File(LAYOUT_ROOT, name)

    /**
     * Layout assets are a top-level JSON array of rows, each row an array of key objects.
     * Only the fields the geometry code reads are projected here.
     */
    private fun readRows(path: File): List<List<Map<String, String?>>> {
        val root = JSON.parseToJsonElement(path.readText())
        val rows = runCatching { root.jsonArray }.getOrNull() ?: return emptyList()
        return rows.mapNotNull { row ->
            runCatching {
                row.jsonArray.mapNotNull { key ->
                    runCatching {
                        key.jsonObject.mapValues { (_, v) ->
                            runCatching { v.jsonPrimitive.content }.getOrNull()
                        }
                    }.getOrNull()
                }
            }.getOrNull()
        }
    }

    @Test
    fun `layout asset root is present`() {
        assertTrue(LAYOUT_ROOT.isDirectory, "expected layout assets at ${LAYOUT_ROOT.absolutePath}")
    }

    // -- Missing Symbols2 default -------------------------------------------------------------

    @KNOWN_DEFECT(
        "Subtype.kt declares SYMBOLS2_DEFAULT = extCoreLayout(\"western_wide\"), but no " +
            "symbols2/western_wide.json exists. The default Symbols2 component cannot resolve. " +
            "Filed as a narrow diagnostic only — Symbols2 is not redesigned in Stage 00.",
    )
    @Test
    fun `symbols2 has no western_wide component despite it being the declared default`() {
        val symbols2 = layoutDir("symbols2")
        assertTrue(symbols2.isDirectory, "symbols2 layout directory should exist")

        val westernWide = File(symbols2, "western_wide.json")
        assertTrue(
            !westernWide.exists(),
            "This test documents a missing asset. If symbols2/western_wide.json now exists, the " +
                "defect is fixed and this diagnostic should be removed.",
        )

        // The sibling family does have the component, which is why the default was plausible.
        assertTrue(
            File(layoutDir("symbols"), "western_wide.json").exists(),
            "symbols/western_wide.json is expected to exist",
        )
    }

    @COMPATIBILITY("The Symbols2 components that do exist remain available.")
    @Test
    fun `symbols2 provides its existing components`() {
        val names = layoutDir("symbols2").listFiles()?.map { it.name }?.toSet().orEmpty()

        assertTrue(names.containsAll(setOf("western.json", "western_samsung.json")), "found: $names")
    }

    // -- Dead QWERTY row ------------------------------------------------------------------------

    @KNOWN_DEFECT(
        "characters/qwerty_wide.json row 3 is unreachable in normal operation because " +
            "charactersMod/qwerty_wide_mod.json row 0 carries no code-0 placeholder and therefore " +
            "replaces the row wholesale. Removed in b4b8645. Decision 11 keeps the row only long " +
            "enough to test the fallback path.",
    )
    @Test
    fun `qwerty wide modifier row has no placeholder so it replaces rather than merges`() {
        val modFile = File(layoutDir("charactersMod"), "qwerty_wide_mod.json")
        assertTrue(modFile.exists(), "expected ${modFile.path}")

        val modRows = readRows(modFile)
        assertTrue(modRows.isNotEmpty(), "modifier layout should declare rows")

        val firstRowCodes = modRows.first().mapNotNull { it["code"]?.toString() }
        assertTrue(
            firstRowCodes.none { it == "0" },
            "row 0 carries no code-0 placeholder, so the main row is replaced wholesale",
        )
    }

    @MIGRATION_FIXTURE(
        "Placeholder-present vs placeholder-absent merge behaviour. A code-0 placeholder is the " +
            "signal that the main row's contents are spliced in rather than discarded.",
    )
    @Test
    fun `placeholder presence distinguishes merge from replace across modifier assets`() {
        val modDir = layoutDir("charactersMod")
        val modFiles = modDir.listFiles { f: File -> f.extension == "json" }.orEmpty()
        assertTrue(modFiles.isNotEmpty(), "expected modifier layouts in ${modDir.path}")

        val withPlaceholder = mutableListOf<String>()
        val withoutPlaceholder = mutableListOf<String>()

        for (file in modFiles) {
            val rows = runCatching { readRows(file) }.getOrDefault(emptyList())
            if (rows.isEmpty()) continue
            val hasPlaceholder = rows.first().any { it["code"]?.toString() == "0" }
            if (hasPlaceholder) withPlaceholder += file.name else withoutPlaceholder += file.name
        }

        // Both behaviours are present in the bundled assets; the merge path must keep handling each.
        assertTrue(
            withoutPlaceholder.contains("qwerty_wide_mod.json"),
            "qwerty_wide_mod should be in the replace group, found merge=$withPlaceholder replace=$withoutPlaceholder",
        )
        assertTrue(
            withPlaceholder.isNotEmpty(),
            "expected at least one modifier layout that merges via a code-0 placeholder",
        )
    }

    // -- Numeric / phone family shapes ------------------------------------------------------------

    @COMPATIBILITY("Numeric and phone layout families declare four rows each.")
    @Test
    fun `numeric and phone families declare four rows`() {
        for (family in listOf("numeric", "numericAdvanced", "phone", "phone2")) {
            val dir = layoutDir(family)
            if (!dir.isDirectory) continue
            val files = dir.listFiles { f: File -> f.extension == "json" }.orEmpty()
            assertTrue(files.isNotEmpty(), "$family should bundle at least one layout")

            for (file in files) {
                val rows = runCatching { readRows(file) }.getOrDefault(emptyList())
                if (rows.isEmpty()) continue
                assertEquals(4, rows.size, "${file.path} should declare four rows")
            }
        }
    }
}
