package dev.patrickgold.florisboard.app.layoutbuilder

import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData

object LayoutValidation {
    private val validTextKeyDataLabels = TextKeyData.InternalKeys.map { it.label }.toSet()

    // NEW: allow the same internal codes the built-in LCARS pack uses
    private val allowedSpecialCodes = setOf(
        "KEYCODE_TAB",
        "KEYCODE_DELETE",
        "KEYCODE_SHIFT",
        "KEYCODE_ENTER",
        "KEYCODE_SPACE",
        "MODE_SYMBOLS",
        "CTRL_MOD",
        "MENU_TOGGLE"   // already used in lcars_hacker_en_us.json
    )

    fun validatePack(pack: LayoutPack): List<String> {
        val errors = mutableListOf<String>()
        for ((index, row) in pack.rows.withIndex()) {
            val rowErrors = validateRow(row, pack.units)
            if (rowErrors.isNotEmpty()) {
                errors += rowErrors.map { error -> "Row ${row.id.ifEmpty { index.toString() }}: $error" }
            }
        }
        return errors
    }

    fun validateRow(row: LayoutRow, expectedUnits: Int = row.units): List<String> {
        val errors = mutableListOf<String>()
        val sumUnits = row.keys.sumOf { it.units }
        if (sumUnits != expectedUnits) {
            errors += "Σu $sumUnits/$expectedUnits"
        }
        for (key in row.keys) {
            if (!isValidCode(key.code)) {
                errors += "Invalid code '${key.code}'"
            }
        }
        return errors
    }

    private fun isValidCode(code: String): Boolean {
        if (code.isBlank()) return false
        val trimmed = code.trim()
        val codePointCount = trimmed.codePointCount(0, trimmed.length)

        // 1. Single character
        if (codePointCount == 1) return true

        // 2. One of the predefined TextKeyData “internal” labels
        if (validTextKeyDataLabels.contains(trimmed)) return true

        // 3. One of our known special codes (KEYCODE_*, MODE_*, *_MOD, *_TOGGLE)
        if (allowedSpecialCodes.contains(trimmed)) return true

        // 4. Raw KeyCode integer
        val intCode = trimmed.toIntOrNull()
        if (intCode != null) return true

        return false
    }
}
