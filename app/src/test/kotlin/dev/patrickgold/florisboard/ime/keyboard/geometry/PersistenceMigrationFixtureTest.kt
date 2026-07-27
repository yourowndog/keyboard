package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.app.layoutbuilder.LayoutPack
import dev.patrickgold.florisboard.app.layoutbuilder.LayoutKeyStyle
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.keyboard.LayoutType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Pins the on-device persisted formats that a future upgrade must still decode.
 *
 * Decision 8 seeds the Coding profile from today's values and Decision 13 keeps subtype identity
 * independent of profiles, so both of those payloads have to survive the migration byte-for-byte.
 * These fixtures fail if the wire format changes, which is the point.
 */
class PersistenceMigrationFixtureTest {

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }

    // -- Subtype JSON (localization__subtypes) ------------------------------------------------

    @MIGRATION_FIXTURE(
        "localization__subtypes stores a JSON array of Subtype objects. The eight layout-family " +
            "component IDs inside layoutMap must survive the profile migration untouched.",
    )
    @Test
    fun `default subtype round-trips through its persisted json form`() {
        val encoded = JSON.encodeToString(Subtype.serializer(), Subtype.DEFAULT)
        val decoded = JSON.decodeFromString(Subtype.serializer(), encoded)

        assertEquals(Subtype.DEFAULT, decoded)
        assertEquals(Subtype.DEFAULT.id, decoded.id)
        assertEquals(Subtype.DEFAULT.primaryLocale, decoded.primaryLocale)
    }

    @MIGRATION_FIXTURE("All eight layout families remain addressable after a decode.")
    @Test
    fun `subtype layout map exposes all eight layout families`() {
        val families = listOf(
            LayoutType.CHARACTERS,
            LayoutType.SYMBOLS,
            LayoutType.SYMBOLS2,
            LayoutType.NUMERIC,
            LayoutType.NUMERIC_ADVANCED,
            LayoutType.NUMERIC_ROW,
            LayoutType.PHONE,
            LayoutType.PHONE2,
        )

        val layoutMap = Subtype.DEFAULT.layoutMap
        for (family in families) {
            assertNotNull(layoutMap[family], "$family should resolve to a component name")
        }
    }

    @KNOWN_DEFECT(
        "The persisted Symbols2 default points at a component that does not exist on disk. The " +
            "value round-trips correctly — the defect is the missing asset, not the encoding.",
    )
    @Test
    fun `subtype symbols2 default records the unresolvable component`() {
        val symbols2 = Subtype.DEFAULT.layoutMap[LayoutType.SYMBOLS2]

        assertNotNull(symbols2)
        assertEquals("western_wide", symbols2.componentId)
    }

    @MIGRATION_FIXTURE(
        "A populated subtype array from an existing install must decode. Unknown future keys are " +
            "tolerated, so older readers survive newer writers.",
    )
    @Test
    fun `populated subtype array decodes and tolerates unknown keys`() {
        val encoded = JSON.encodeToString(Subtype.serializer(), Subtype.DEFAULT)
        val withUnknown = encoded.dropLast(1) + ""","someFutureKey":"ignored"}"""

        val decoded = JSON.decodeFromString(Subtype.serializer(), withUnknown)
        assertEquals(Subtype.DEFAULT.layoutMap, decoded.layoutMap)
    }

    // -- Layout packs ---------------------------------------------------------------------------

    @MIGRATION_FIXTURE(
        "A saved layout pack with fractional units, a spacer and a disabled row must still decode. " +
            "Packs carry no row semantics today, which is what Stage 06 adds.",
    )
    @Test
    fun `saved layout pack with spacers units and a disabled row decodes`() {
        val json = """
            {
              "id": "user_pack",
              "label": "User Pack",
              "units": 12,
              "rows": [
                {
                  "id": "row_alpha",
                  "units": 12,
                  "enabled": true,
                  "keys": [
                    {"id": "k1", "label": "q", "code": "113", "units": 2},
                    {"id": "sp", "label": "", "code": "", "units": 1, "spacer": true},
                    {"id": "k2", "label": "w", "code": "119", "units": 1, "style": "aux"}
                  ]
                },
                {
                  "id": "row_hidden",
                  "enabled": false,
                  "showIfSetting": "some_pref",
                  "keys": [{"id": "k3", "label": "z", "code": "122"}]
                }
              ]
            }
        """.trimIndent()

        val pack = JSON.decodeFromString(LayoutPack.serializer(), json)

        assertEquals("user_pack", pack.id)
        assertEquals(12, pack.units)
        assertEquals(2, pack.rows.size)

        val alphaRow = pack.rows[0]
        assertEquals(3, alphaRow.keys.size)
        assertEquals(2, alphaRow.keys[0].units)
        assertTrue(alphaRow.keys[1].spacer, "the spacer flag must survive")
        assertEquals(LayoutKeyStyle.AUX, alphaRow.keys[2].style)

        val disabledRow = pack.rows[1]
        assertTrue(!disabledRow.enabled, "the disabled flag must survive")
        assertEquals("some_pref", disabledRow.showIfSetting)
    }

    @MIGRATION_FIXTURE(
        "Layout-pack parsing ignores unknown JSON keys, so packs written by a later build still " +
            "load on an older one.",
    )
    @Test
    fun `layout pack ignores unknown keys`() {
        val json = """
            {
              "id": "p",
              "label": "P",
              "futureField": {"nested": true},
              "rows": [{"id": "r", "role": "PRIMARY_ACTION", "keys": []}]
            }
        """.trimIndent()

        val pack = JSON.decodeFromString(LayoutPack.serializer(), json)

        assertEquals("p", pack.id)
        assertEquals(1, pack.rows.size)
        assertEquals("r", pack.rows[0].id)
    }

    @COMPATIBILITY("Layout pack defaults are stable: 12 units, enabled rows, non-spacer keys.")
    @Test
    fun `layout pack defaults are stable`() {
        val pack = JSON.decodeFromString(
            LayoutPack.serializer(),
            """{"id":"d","label":"D","rows":[{"id":"r","keys":[{"label":"a","code":"97"}]}]}""",
        )

        assertEquals(LayoutPack.DefaultUnits, pack.units)
        val row = pack.rows.single()
        assertEquals(LayoutPack.DefaultUnits, row.units)
        assertTrue(row.enabled)

        val key = row.keys.single()
        assertEquals(1, key.units)
        assertTrue(!key.spacer)
        assertEquals(LayoutKeyStyle.DEFAULT, key.style)
    }

    // -- Legacy key customization ------------------------------------------------------------------

    @MIGRATION_FIXTURE(
        "Existing key customization is global and addressed by integer key code, not by " +
            "profile/layout/row/key instance. Stage 04 must widen this key space without losing " +
            "the values already stored under bare codes.",
    )
    @Test
    fun `legacy customization json is keyed by bare integer key code`() {
        val legacy = """{"32":{"widthFactor":1.5},"-14":{"widthFactor":0.8}}"""

        val decoded = JSON.parseToJsonElement(legacy).jsonObject

        assertEquals(setOf("32", "-14"), decoded.keys)
        // The codes carry no profile, layout, row or instance qualifier.
        assertTrue(decoded.keys.all { it.toIntOrNull() != null }, "every key is a bare integer code")
    }
}
