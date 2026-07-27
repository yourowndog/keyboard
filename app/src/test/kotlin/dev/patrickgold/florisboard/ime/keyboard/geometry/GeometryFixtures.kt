package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.popup.PopupMapping
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard

/**
 * Composition fixtures for the Stage 00 characterization tests.
 *
 * These build [TextKeyboard] instances whose row/key *shape* mirrors what the corresponding real
 * composition produces, so the geometry authorities can be exercised without a `Context`, the
 * Compose runtime, or a device.
 *
 * They are shape fixtures, not asset snapshots: they reproduce row counts, per-row key counts,
 * width units, `isAlpha` flags, and `bottomModRowCount`, because those are the only inputs the
 * geometry code actually consumes. They deliberately do not reproduce labels or popups.
 */
object GeometryFixtures {

    /** Builds a computed [TextKey]. [isAlpha] is applied after `compute` because `compute` resets it. */
    fun key(
        code: Int,
        isAlpha: Boolean,
        widthUnits: Float = 1f,
        type: KeyType = KeyType.CHARACTER,
    ): TextKey {
        return TextKey(TextKeyData(type = type, code = code, label = code.toLabel())).also {
            it.compute(DefaultComputingEvaluator)
            it.isAlpha = isAlpha
            it.flayWidthFactor = widthUnits
        }
    }

    private fun Int.toLabel(): String = when (this) {
        in 32..126 -> this.toChar().toString()
        else -> "k$this"
    }

    /** A row of [count] alpha keys, each one unit wide. */
    fun alphaRow(count: Int): Array<TextKey> =
        Array(count) { key(code = 'a'.code + (it % 26), isAlpha = true) }

    /** A row of [count] non-alpha keys, each one unit wide. */
    fun modRow(count: Int): Array<TextKey> =
        Array(count) { key(code = KeyCode.UNSPECIFIED - it, isAlpha = false, type = KeyType.MODIFIER) }

    /**
     * The primary action row: shift/symbols, space, enter. Space carries literal code 32, which is
     * what the layout code keys its "space row" branch off.
     */
    fun primaryActionRow(spaceUnits: Float = 4f): Array<TextKey> = arrayOf(
        key(KeyCode.VIEW_SYMBOLS, isAlpha = false, type = KeyType.SYSTEM_GUI),
        key(KeyCode.SPACE, isAlpha = false, widthUnits = spaceUnits),
        key(KeyCode.ENTER, isAlpha = false, type = KeyType.ENTER_EDITING),
    )

    fun keyboard(
        rows: List<Array<TextKey>>,
        mode: KeyboardMode = KeyboardMode.CHARACTERS,
        bottomModRowCount: Int = 2,
        extendedPopupMapping: PopupMapping? = null,
    ): TextKeyboard = TextKeyboard(
        arrangement = rows.toTypedArray(),
        mode = mode,
        extendedPopupMapping = extendedPopupMapping,
        extendedPopupMappingDefault = null,
        bottomModRowCount = bottomModRowCount,
    )

    // ---------------------------------------------------------------------------------------
    // Coding profile (today's default experience)
    // ---------------------------------------------------------------------------------------

    /** Default Coding: 3 alpha rows + primary action + 2 coding utility rows. */
    fun defaultCoding(): TextKeyboard = keyboard(
        rows = listOf(
            alphaRow(10),
            alphaRow(9),
            alphaRow(9),
            primaryActionRow(),
            modRow(7),
            modRow(7),
        ),
        bottomModRowCount = 2,
    )

    /**
     * Coding with utilities hidden. The primary row survives merge, but `bottomModRowCount` drops
     * to 0 — the merged primary row is not represented in the count.
     */
    fun codingUtilitiesHidden(): TextKeyboard = keyboard(
        rows = listOf(
            alphaRow(10),
            alphaRow(9),
            alphaRow(9),
            primaryActionRow(),
        ),
        bottomModRowCount = 0,
    )

    /** Coding + number extension row on top. */
    fun codingWithNumberExtension(): TextKeyboard = keyboard(
        rows = listOf(
            numberExtensionRow(),
            alphaRow(10),
            alphaRow(9),
            alphaRow(9),
            primaryActionRow(),
            modRow(7),
            modRow(7),
        ),
        bottomModRowCount = 2,
    )

    /** Coding + developer extension row on top. */
    fun codingWithDeveloperExtension(): TextKeyboard = keyboard(
        rows = listOf(
            developerExtensionRow(),
            alphaRow(10),
            alphaRow(9),
            alphaRow(9),
            primaryActionRow(),
            modRow(7),
            modRow(7),
        ),
        bottomModRowCount = 2,
    )

    /** Coding + both extension rows on top. */
    fun codingWithBothExtensions(): TextKeyboard = keyboard(
        rows = listOf(
            numberExtensionRow(),
            developerExtensionRow(),
            alphaRow(10),
            alphaRow(9),
            alphaRow(9),
            primaryActionRow(),
            modRow(7),
            modRow(7),
        ),
        bottomModRowCount = 2,
    )

    /**
     * Extension rows inherit `isAlpha = true` from the constructor default rather than declaring
     * their own semantics. The fixtures reproduce that inheritance on purpose.
     */
    fun numberExtensionRow(): Array<TextKey> =
        Array(10) { key(code = '0'.code + it, isAlpha = true) }

    fun developerExtensionRow(): Array<TextKey> =
        Array(10) { key(code = '!'.code + it, isAlpha = true) }

    // ---------------------------------------------------------------------------------------
    // Symbols
    // ---------------------------------------------------------------------------------------

    fun characters(): TextKeyboard = keyboard(
        rows = listOf(alphaRow(10), alphaRow(9), alphaRow(9), primaryActionRow()),
        mode = KeyboardMode.CHARACTERS,
        bottomModRowCount = 2,
    )

    fun wideSymbols(): TextKeyboard = keyboard(
        rows = listOf(alphaRow(10), alphaRow(10), alphaRow(9), primaryActionRow()),
        mode = KeyboardMode.SYMBOLS,
        bottomModRowCount = 2,
    )

    // ---------------------------------------------------------------------------------------
    // Numeric / phone families — four main-only rows whose keys all read as alpha
    // ---------------------------------------------------------------------------------------

    private fun fourUniformRows(perRow: Int): List<Array<TextKey>> =
        List(4) { alphaRow(perRow) }

    fun numeric(): TextKeyboard =
        keyboard(fourUniformRows(3), mode = KeyboardMode.NUMERIC, bottomModRowCount = 2)

    fun numericAdvanced(): TextKeyboard =
        keyboard(fourUniformRows(4), mode = KeyboardMode.NUMERIC_ADVANCED, bottomModRowCount = 2)

    fun phone(): TextKeyboard =
        keyboard(fourUniformRows(3), mode = KeyboardMode.PHONE, bottomModRowCount = 2)

    fun phone2(): TextKeyboard =
        keyboard(fourUniformRows(3), mode = KeyboardMode.PHONE2, bottomModRowCount = 2)

    // ---------------------------------------------------------------------------------------
    // Layout pack
    // ---------------------------------------------------------------------------------------

    /**
     * A representative layout pack keyboard: fractional width units, a spacer, and a disabled row
     * that composition has already dropped. Layout-pack rows lose their row IDs at runtime and all
     * keys default to alpha.
     */
    fun layoutPackWithSpacersAndUnits(): TextKeyboard = keyboard(
        rows = listOf(
            arrayOf(
                key('q'.code, isAlpha = true, widthUnits = 1.5f),
                key('w'.code, isAlpha = true, widthUnits = 0.5f),
                key(KeyCode.UNSPECIFIED, isAlpha = true, widthUnits = 2f), // spacer
                key('e'.code, isAlpha = true, widthUnits = 1f),
            ),
            alphaRow(9),
            primaryActionRow(),
        ),
        bottomModRowCount = 2,
    )
}
