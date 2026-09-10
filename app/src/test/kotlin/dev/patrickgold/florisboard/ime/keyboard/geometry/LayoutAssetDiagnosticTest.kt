package dev.patrickgold.florisboard.ime.keyboard.geometry

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.patrickgold.florisboard.ime.core.SubtypeLayoutMap
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
        val EXTENSION_FILE = File(LAYOUT_ROOT.parentFile, "extension.json")
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

    @Test
    fun `qwerty wide full resolves to the shipped coding layout`() {
        val root = JSON.parseToJsonElement(EXTENSION_FILE.readText()).jsonObject
        val characters = root["layouts"]!!.jsonObject["characters"]!!.jsonArray
        val qwertyWideFull = characters
            .map { it.jsonObject }
            .single { it["id"]?.jsonPrimitive?.content == "qwerty_wide_full" }

        val arrangementFile = qwertyWideFull["arrangementFile"]!!.jsonPrimitive.content
        assertEquals("layouts/characters/qwerty_wide.json", arrangementFile)
        assertTrue(File(LAYOUT_ROOT.parentFile, arrangementFile).isFile)
        assertEquals(
            "org.florisboard.layouts:qwerty_wide_mod",
            qwertyWideFull["modifier"]!!.jsonPrimitive.content,
        )
    }

    // -- Missing Symbols2 default -------------------------------------------------------------

    /**
     * Every layout default a subtype starts with resolves to a component that exists.
     *
     * This is the test that was missing. `SYMBOLS2_DEFAULT` was widened to `western_wide` in
     * 27323ebe alongside the Characters and Symbols defaults, but `symbols2` has no `western_wide`
     * component — and nothing failed. `loadLayoutAsync` throws, `mergeLayouts` logs a warning and
     * takes its modifier-only branch, and the result is a keyboard rather than an error: `=\<`
     * rendered the two rows of `symbols2Mod/default.json` and nothing else.
     *
     * A default is resolved through two independent things that have to agree — a component
     * declared in `extension.json` and an arrangement file on disk — so both are checked. The map is
     * read from the constructor rather than named here, which is what makes this catch the *next*
     * default someone widens.
     */
    @EXPECTED_FIX("every default in SubtypeLayoutMap resolves to a declared component and a file")
    @Test
    fun `every subtype layout default resolves to a component that exists`() {
        val declared = JSON.parseToJsonElement(EXTENSION_FILE.readText())
            .jsonObject["layouts"]!!.jsonObject
            .mapValues { (_, components) ->
                components.jsonArray.associate { component ->
                    val obj = component.jsonObject
                    obj["id"]!!.jsonPrimitive.content to obj["arrangementFile"]?.jsonPrimitive?.content
                }
            }

        val defaults = SubtypeLayoutMap()
        val layoutTypes = listOf(
            LayoutType.CHARACTERS,
            LayoutType.SYMBOLS,
            LayoutType.SYMBOLS2,
            LayoutType.NUMERIC,
            LayoutType.NUMERIC_ADVANCED,
            LayoutType.NUMERIC_ROW,
            LayoutType.PHONE,
            LayoutType.PHONE2,
        )

        for (layoutType in layoutTypes) {
            val component = defaults[layoutType]
            assertTrue(component != null, "$layoutType has no default")

            val family = declared[layoutType.id]
            assertTrue(family != null, "extension.json declares no `${layoutType.id}` family at all")
            assertTrue(
                family.containsKey(component.componentId),
                "the default $layoutType component `${component.componentId}` is not declared in " +
                    "extension.json — declared there: ${family.keys.sorted()}",
            )

            val path = family[component.componentId] ?: "layouts/${layoutType.id}/${component.componentId}.json"
            val file = File(LAYOUT_ROOT.parentFile, path)
            assertTrue(
                file.isFile,
                "the default $layoutType component `${component.componentId}` declares $path, which " +
                    "does not exist",
            )
        }
    }

    /**
     * There is still no wide Symbols2 arrangement, which is why the default points at `western`.
     *
     * The Coding profile's Characters and Symbols layers are both five columns wider than stock, and
     * Symbols2 is not, so `=\<` is a narrower keyboard stretched into the shared text-entry frame.
     * That is a content gap rather than a code defect — closing it means authoring an arrangement and
     * deciding what belongs on it — so it is pinned rather than papered over.
     */
    @KNOWN_DEFECT(
        "The Coding profile has no wide Symbols2 layer. `=\\<` falls back to the stock `western` " +
            "arrangement and is stretched to fill the frame the wide Characters layer establishes.",
    )
    @Test
    fun `symbols2 still has no wide component`() {
        assertTrue(
            !File(layoutDir("symbols2"), "western_wide.json").exists(),
            "symbols2/western_wide.json now exists — declare it in extension.json, point " +
                "SYMBOLS2_DEFAULT at it, and remove this diagnostic",
        )

        // The sibling families do have wide components, which is why the default was plausible.
        for (family in listOf("characters", "symbols")) {
            assertTrue(
                File(layoutDir(family), "western_wide.json").exists() ||
                    File(layoutDir(family), "qwerty_wide.json").exists(),
                "$family is expected to carry a wide component",
            )
        }
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

    // -- Hint source typing ------------------------------------------------------------------------

    /**
     * The wide symbol layer does not mark its digit row as numeric, so the number hint is inert.
     *
     * `LayoutManager.addRowHints` selects hints by the computed [KeyType] of the *source* key: the
     * number-hint pass keeps only `numeric` keys and the symbol-hint pass keeps only `character`
     * ones. `TextKeyData.type` defaults to `CHARACTER`, and `symbols/western_wide.json` sets no type
     * on its digit row — so for the Coding profile those digits reach the letters through the symbol
     * channel, and `hintedNumberRowEnabled` has never drawn anything at all.
     *
     * `numericRow/western_arabic.json` does type its keys, which is why the same feature works for
     * the stock profile and why this reads as an omission rather than a convention.
     *
     * Fixing it is a one-field asset edit, but it is a visible change to a shipped layout — the
     * digits would move from the symbol-hint style to the number-hint style and become subject to a
     * different preference — so it is pinned here rather than made silently alongside a pairing
     * change. See `HintRowPairing`.
     */
    @KNOWN_DEFECT(
        "symbols/western_wide.json does not type its digit row `numeric`, so hintedNumberRowEnabled " +
            "draws nothing for the Coding profile and the digits arrive as symbol hints instead."
    )
    @Test
    fun `the wide symbol layer leaves its digit row untyped`() {
        val reference = declaredTypes(rowsOf("numericRow", "western_arabic").first())
        assertEquals(
            setOf("numeric"),
            reference.toSet(),
            "the reference number row no longer types its keys, so this defect no longer has a contrast",
        )

        assertEquals(
            emptyList(),
            declaredTypes(rowsOf("symbols", "western_wide").first()),
            "the wide digit row now declares a type — if it is `numeric`, this defect is fixed and " +
                "the test should be replaced by a positive assertion",
        )
    }

    /** The rows of a layout asset, unflattened, so nested selector branches stay readable. */
    private fun rowsOf(type: String, name: String): List<JsonElement> {
        val file = File(layoutDir(type), "$name.json")
        assertTrue(file.isFile, "missing layout asset ${file.path}")
        return JSON.parseToJsonElement(file.readText()).jsonArray
    }

    /**
     * Every `type` declared anywhere inside [element], including inside a selector's branches.
     *
     * A key that declares none is a `character` key by default, which is the whole point of the
     * defect above — the omission is invisible in the asset and only shows up as a hint that never
     * gets drawn.
     */
    private fun declaredTypes(element: JsonElement): List<String> = buildList {
        runCatching { element.jsonObject }.getOrNull()?.forEach { (name, value) ->
            if (name == "type") {
                runCatching { value.jsonPrimitive.content }.getOrNull()?.let { add(it) }
            } else {
                addAll(declaredTypes(value))
            }
        }
        runCatching { element.jsonArray }.getOrNull()?.forEach { addAll(declaredTypes(it)) }
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
