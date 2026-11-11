package dev.patrickgold.florisboard.ime.keyboard.pack

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import java.io.File

/**
 * Pack fixtures for the Stage 06 tests.
 *
 * The version 1 fixtures are written as *JSON text*, not as [LayoutPackDocument] builders, because
 * the thing under test is the ability to read bytes that already exist on a device. A fixture built
 * from the current data classes could not catch a schema change that breaks those bytes.
 */
object LayoutPackFixtures {

    /** The bundled pack as it actually ships, read from the asset tree. */
    val BUNDLED_ASSET = File("src/main/assets/layouts/lcars_hacker_en_us.json")

    /**
     * A schema version 1 pack exercising every version 1 capability: historical row ids, a spacer, a
     * non-unit width, a disabled row, and an inert `showIfSetting`.
     */
    val LEGACY_PACK_JSON = """
        {
          "id": "legacy_pack",
          "label": "Legacy Pack",
          "units": 12,
          "rows": [
            {
              "id": "row_letters_top",
              "units": 12,
              "keys": [
                { "label": "Q", "code": "q", "units": 2, "style": "default", "spacer": false },
                { "label": "", "code": "", "units": 2, "spacer": true },
                { "label": "W", "code": "w", "units": 8, "style": "aux", "spacer": false }
              ]
            },
            {
              "id": "row_space",
              "units": 12,
              "keys": [
                { "label": "123", "code": "MODE_SYMBOLS", "units": 2, "style": "aux" },
                { "label": "Space", "code": "KEYCODE_SPACE", "units": 9, "style": "space" },
                { "label": "Enter", "code": "KEYCODE_ENTER", "units": 1, "style": "special_right" }
              ]
            },
            {
              "id": "row_mod",
              "units": 12,
              "showIfSetting": "mod_rows_visible",
              "keys": [
                { "label": "Ctrl", "code": "CTRL_MOD", "units": 12, "style": "aux" }
              ]
            },
            {
              "id": "mystery_row",
              "units": 12,
              "enabled": false,
              "showIfSetting": "some_pref_nobody_knows",
              "keys": [
                { "label": "Z", "code": "z", "units": 12 }
              ]
            }
          ]
        }
    """.trimIndent()

    /** A minimal, valid schema version 2 pack. */
    fun currentPack(
        id: String = "current_pack",
        rows: List<LayoutPackRow> = listOf(alphaRow(), primaryActionRow()),
        units: Float = 12f,
    ) = LayoutPackDocument(
        schemaVersion = LayoutPackSchema.CURRENT_VERSION,
        id = id,
        label = "Current Pack",
        units = units,
        rows = rows,
    )

    /** Three one-unit keys and a nine-unit key, summing to the default twelve-unit grid. */
    fun alphaRow(id: String = "alpha_top") = LayoutPackRow(
        id = id,
        role = SemanticRowRole.ALPHA,
        units = 12f,
        keys = listOf(
            key("q", widthUnits = 1f),
            key("w", widthUnits = 1f),
            key("e", widthUnits = 1f),
            key("r", widthUnits = 9f),
        ),
    )

    fun primaryActionRow(id: String = "primary") = LayoutPackRow(
        id = id,
        role = SemanticRowRole.PRIMARY_ACTION,
        units = 12f,
        keys = listOf(
            key("MODE_SYMBOLS", label = "123", widthUnits = 2f, style = LayoutPackKeyStyle.AUX),
            key("KEYCODE_SPACE", label = "Space", widthUnits = 9f, style = LayoutPackKeyStyle.SPACE),
            key("KEYCODE_ENTER", label = "Enter", widthUnits = 1f, style = LayoutPackKeyStyle.SPECIAL_RIGHT),
        ),
    )

    /** A row with a spacer in the middle, so structural filler can be asserted on. */
    fun rowWithSpacer(id: String = "utility") = LayoutPackRow(
        id = id,
        role = SemanticRowRole.CODING_UTILITY,
        units = 12f,
        keys = listOf(
            // "esc" is the internal key's own label; the resolver matches it case-insensitively.
            key("ESC", label = "Esc", widthUnits = 2f),
            spacer(widthUnits = 8f),
            key("CTRL", label = "Ctrl", widthUnits = 2f),
        ),
    )

    fun key(
        code: String,
        label: String = "",
        widthUnits: Float = 1f,
        style: LayoutPackKeyStyle = LayoutPackKeyStyle.DEFAULT,
        id: String? = null,
    ) = LayoutPackElement(
        kind = LayoutPackElementKind.KEY,
        id = id,
        code = code,
        label = label,
        widthUnits = widthUnits,
        style = style,
    )

    fun spacer(widthUnits: Float = 1f, id: String? = null) = LayoutPackElement(
        kind = LayoutPackElementKind.SPACER,
        id = id,
        code = "",
        label = "",
        widthUnits = widthUnits,
    )
}
