package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The Text/Code switch: which layouts each profile loads, and what the hint plates above the letters
 * end up showing. Plates are read off the symbol layer row for row and key for key, so the symbol
 * assets are checked against the mockup's plate sets here rather than on a device.
 */
class ProfileLayoutsTest {

    private companion object {
        const val EXT = "org.florisboard.layouts"
        val LAYOUT_ROOT = File("src/main/assets/ime/keyboard/org.florisboard.layouts/layouts")
        val EXTENSION_FILE = File(LAYOUT_ROOT.parentFile, "extension.json")
        val JSON = Json { ignoreUnknownKeys = true }

        fun ecn(id: String) = ExtensionComponentName(EXT, id)
        val QWERTY_WIDE = ecn("qwerty_wide")
        val WESTERN_WIDE = ecn("western_wide")
        val JCUKEN = ecn("jcuken_russian")
    }

    @Test
    fun `coding keeps the subtype's own layouts`() {
        for (mode in listOf(KeyboardMode.CHARACTERS, KeyboardMode.SYMBOLS, KeyboardMode.SYMBOLS2)) {
            assertEquals(
                WESTERN_WIDE,
                ProfileLayouts.resolve(KeyboardProfile.CODING, mode, QWERTY_WIDE, WESTERN_WIDE),
            )
        }
    }

    @Test
    fun `text swaps the coding family for the mockup board`() {
        assertEquals(
            ecn("qwerty_text"),
            ProfileLayouts.resolve(KeyboardProfile.TEXT, KeyboardMode.CHARACTERS, QWERTY_WIDE, QWERTY_WIDE),
        )
        for (mode in listOf(KeyboardMode.SYMBOLS, KeyboardMode.SYMBOLS2)) {
            assertEquals(
                ecn("western_text"),
                ProfileLayouts.resolve(KeyboardProfile.TEXT, mode, ecn("qwerty_wide_full"), WESTERN_WIDE),
            )
        }
        // Number pads and phone pads are not the profile's business.
        assertEquals(
            ecn("western_arabic"),
            ProfileLayouts.resolve(KeyboardProfile.TEXT, KeyboardMode.NUMERIC, QWERTY_WIDE, ecn("western_arabic")),
        )
    }

    @Test
    fun `text leaves other alphabets alone`() {
        assertEquals(
            JCUKEN,
            ProfileLayouts.resolve(KeyboardProfile.TEXT, KeyboardMode.CHARACTERS, JCUKEN, JCUKEN),
        )
    }

    @Test
    fun `every layout the profiles name is declared and on disk`() {
        val layouts = JSON.parseToJsonElement(EXTENSION_FILE.readText()).jsonObject["layouts"]!!.jsonObject
        fun declared(type: String, id: String): JsonObject? = layouts[type]!!.jsonArray
            .map { it.jsonObject }
            .singleOrNull { it["id"]!!.jsonPrimitive.content == id }

        val expected = listOf(
            "characters" to "qwerty_text", "charactersMod" to "qwerty_text_mod",
            "symbols" to "western_text", "symbolsMod" to "western_text_mod",
            "symbols2" to "western_text", "symbols2Mod" to "western_text_mod",
            "symbols" to "western_wide", "symbolsMod" to "western_wide_mod",
            "symbols2" to "western_wide", "symbols2Mod" to "western_wide_mod",
        )
        for ((type, id) in expected) {
            val entry = declared(type, id)
            assertTrue(entry != null, "$type/$id is not declared in extension.json")
            assertTrue(File(LAYOUT_ROOT, "$type/$id.json").isFile, "$type/$id.json is missing")
            entry["modifier"]?.jsonPrimitive?.content?.let { modifier ->
                val modType = "${type}Mod".takeIf { !type.endsWith("Mod") }
                assertTrue(modType != null && declared(modType, modifier.substringAfter(':')) != null,
                    "$type/$id names modifier $modifier, which is not declared")
            }
        }
    }

    @Test
    fun `text plates match the mockup`() {
        assertEquals(
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("@", "#", "$", "&", "*", "-", "+", "(", ")"),
                listOf("!", "\"", "'", ":", ";", "/", "?"),
            ),
            platesUnder("qwerty_text", "western_text"),
        )
    }

    @Test
    fun `code plates match the mockup`() {
        assertEquals(
            listOf(
                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                listOf("{", "}", "[", "]", "<", ">", "=", "_", "~"),
                listOf("=>", "&&", "||", "!=", "::", "->", "//"),
            ),
            platesUnder("qwerty_wide", "western_wide"),
        )
    }

    @Test
    fun `every symbol page can reach letters, the other page, delete and enter`() {
        for (name in listOf("western_text", "western_wide")) {
            for ((main, mod) in listOf("symbols" to "symbolsMod", "symbols2" to "symbols2Mod")) {
                val codes = rows(main, name).flatten().map { it.code() } +
                    rows(mod, "${name}_mod").flatten().map { it.code() }
                val otherPage = if (main == "symbols") -203 else -202
                for (needed in listOf(-201, otherPage, -7, 10, 32)) {
                    assertTrue(needed in codes, "$main/$name is missing key $needed")
                }
            }
        }
    }

    /**
     * What `LayoutManager.addRowHints` would put on each letter of the three letter rows: the symbol
     * key at the same index of the bottom-aligned symbol row, kept only for character keys.
     */
    private fun platesUnder(characters: String, symbols: String): List<List<String>> {
        val letterRows = rows("characters", characters).take(3)
        val symbolRows = rows("symbols", symbols).take(3)
        return letterRows.zip(symbolRows).map { (letters, syms) ->
            letters.withIndex().mapNotNull { (i, key) ->
                val isLetter = key.code() in 'a'.code..'z'.code
                val sym = syms.getOrNull(i) ?: return@mapNotNull null
                if (!isLetter || sym["type"] != null) null else sym.label()
            }
        }
    }

    private fun rows(type: String, name: String): List<List<JsonObject>> =
        JSON.parseToJsonElement(File(LAYOUT_ROOT, "$type/$name.json").readText()).jsonArray
            .map { row -> row.jsonArray.map { it.jsonObject } }

    private fun JsonObject.code(): Int = when {
        this["\$"]?.jsonPrimitive?.content == "multi_text_key" -> -902
        else -> this["code"]?.jsonPrimitive?.content?.toInt() ?: 0
    }

    private fun JsonObject.label(): String = this["label"]!!.jsonPrimitive.content
}
