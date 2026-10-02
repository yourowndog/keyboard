/*
 * Copyright (C) 2026 The OmniBoard Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import kotlinx.serialization.Serializable

/**
 * Semantic classification of individual keys within the text profile,
 * defining standard role mappings and geometry behavior.
 */
@Serializable
enum class TextKeyRole {
    /** Primary alphabet character keys (A-Z, etc.). */
    ALPHA,

    /** Number key row or dedicated numeric input keys. */
    NUMBER,

    /** State toggles and functional modifiers (Shift, symbol toggles, mode switches). */
    FUNCTION,

    /** Text manipulation keys (Backspace/Delete, Enter, Space). */
    EDITING,

    /** Accessory helper keys (Emoji, Voice, Settings, Language Switch). */
    UTILITY;

    companion object {
        /**
         * Maps a key code and optional label/hint to a [TextKeyRole].
         */
        fun fromKeyCode(keyCode: Int): TextKeyRole = when (keyCode) {
            KeyCode.SPACE, KeyCode.CJK_SPACE -> EDITING
            KeyCode.ENTER -> EDITING
            KeyCode.DELETE, KeyCode.FORWARD_DELETE -> EDITING
            KeyCode.SHIFT, KeyCode.SHIFT_LOCK -> FUNCTION
            KeyCode.VIEW_CHARACTERS, KeyCode.VIEW_SYMBOLS, KeyCode.VIEW_SYMBOLS2, KeyCode.SWITCH_TO_TEXT_CONTEXT -> FUNCTION
            KeyCode.VIEW_NUMERIC, KeyCode.VIEW_NUMERIC_ADVANCED, KeyCode.VIEW_PHONE, KeyCode.VIEW_PHONE2 -> FUNCTION
            KeyCode.VOICE_INPUT -> UTILITY
            KeyCode.SETTINGS -> UTILITY
            KeyCode.IME_SUB_TYPE_SELECTOR, KeyCode.SYSTEM_INPUT_METHOD_PICKER, KeyCode.SYSTEM_PREV_INPUT_METHOD, KeyCode.SYSTEM_NEXT_INPUT_METHOD -> UTILITY
            KeyCode.CLIPBOARD -> UTILITY
            in '0'.code..'9'.code -> NUMBER
            else -> ALPHA
        }
    }
}

/**
 * Responsive screen form factor breakpoints for text profile geometry.
 */
@Serializable
enum class ResponsiveBreakpoint(val minWidthDp: Double, val maxWidthDp: Double) {
    /** Compact (Phone Portrait): strict alpha unit reference, minimal margins, spacebar absorbs width. */
    COMPACT(0.0, 599.0),

    /** Medium (Phone Landscape / Small Foldable Outer Screen): expanded horizontal touch targets, balanced padding. */
    MEDIUM(600.0, 839.0),

    /** Expanded (Tablet / Foldable Inner Screen): max key width caps, side margins or split clustering. */
    EXPANDED(840.0, Double.MAX_VALUE);

    companion object {
        fun forWidth(widthDp: Double): ResponsiveBreakpoint = when {
            widthDp < MEDIUM.minWidthDp -> COMPACT
            widthDp < EXPANDED.minWidthDp -> MEDIUM
            else -> EXPANDED
        }
    }
}

/**
 * Configuration parameters governing how the text profile is laid out across responsive breakpoints.
 */
@Serializable
data class TextProfileResponsiveConfig(
    val maxKeyWidthUnits: Double = 1.8,
    val maxKeyWidthDp: Double? = null,
    val spacebarGrowWeight: Double = 1.0,
    val alphaWidthUnits: Double = 1.0,
    val alphaGrowWeight: Double = 0.0,
    val shiftDeleteBalancedWidthUnits: Double = 1.5,
    val compactSidePaddingDp: Double = 0.0,
    val mediumSidePaddingDp: Double = 16.0,
    val expandedSidePaddingDp: Double = 64.0,
)

/**
 * Text profile definition establishing role mapping, default constraints, and responsive geometry builder.
 */
object TextProfile {
    /**
     * Standard role classification for text keyboard rows.
     */
    fun classifyRowRole(rowIndex: Int, totalRows: Int, hasNumberRow: Boolean): SemanticRowRole = when {
        hasNumberRow && rowIndex == 0 -> SemanticRowRole.NUMBER_ROW
        rowIndex == totalRows - 1 -> SemanticRowRole.PRIMARY_ACTION
        else -> SemanticRowRole.ALPHA
    }

    /**
     * Default width units for a key based on its semantic role and function in a standard QWERTY layout.
     */
    fun defaultWidthUnits(
        keyCode: Int,
        keyRole: TextKeyRole = TextKeyRole.fromKeyCode(keyCode),
        config: TextProfileResponsiveConfig = TextProfileResponsiveConfig(),
    ): Double = when (keyCode) {
        KeyCode.SPACE, KeyCode.CJK_SPACE -> 4.0
        KeyCode.SHIFT, KeyCode.DELETE, KeyCode.FORWARD_DELETE -> config.shiftDeleteBalancedWidthUnits
        KeyCode.ENTER -> 1.5
        KeyCode.VIEW_SYMBOLS, KeyCode.VIEW_CHARACTERS, KeyCode.SWITCH_TO_TEXT_CONTEXT -> 1.5
        KeyCode.VOICE_INPUT, KeyCode.SETTINGS, KeyCode.IME_SUB_TYPE_SELECTOR, KeyCode.CLIPBOARD -> 1.0
        else -> when (keyRole) {
            TextKeyRole.ALPHA -> config.alphaWidthUnits
            TextKeyRole.NUMBER -> 1.0
            TextKeyRole.FUNCTION -> 1.5
            TextKeyRole.EDITING -> 1.5
            TextKeyRole.UTILITY -> 1.0
        }
    }

    /**
     * Default grow weight for a key within the text profile.
     * Only the spacebar grows to absorb excess width in the primary action row by default.
     */
    fun defaultGrowWeight(
        keyCode: Int,
        config: TextProfileResponsiveConfig = TextProfileResponsiveConfig(),
    ): Double = when (keyCode) {
        KeyCode.SPACE, KeyCode.CJK_SPACE -> config.spacebarGrowWeight
        else -> 0.0
    }

    /**
     * Builds a [BoundaryGapPolicy] tuned for the current responsive breakpoint.
     */
    fun buildBoundaryGapPolicy(
        availableWidth: Double,
        config: TextProfileResponsiveConfig = TextProfileResponsiveConfig(),
    ): BoundaryGapPolicy {
        val breakpoint = ResponsiveBreakpoint.forWidth(availableWidth)
        val sidePadding = when (breakpoint) {
            ResponsiveBreakpoint.COMPACT -> config.compactSidePaddingDp
            ResponsiveBreakpoint.MEDIUM -> config.mediumSidePaddingDp
            ResponsiveBreakpoint.EXPANDED -> config.expandedSidePaddingDp
        }
        return BoundaryGapPolicy(keyboardSidePadding = sidePadding)
    }

    /**
     * Builds a standard [RowWidthPolicy] tying alphanumeric reference widths
     * to consumer rows like [SemanticRowRole.PRIMARY_ACTION].
     */
    fun buildRowWidthPolicy(
        alignment: RowAlignment = RowAlignment.CENTER,
    ): RowWidthPolicy = RowWidthPolicy(
        sharedReference = SharedWidthReference(
            measuredRoles = setOf(SemanticRowRole.ALPHA),
            consumerRoles = setOf(SemanticRowRole.ALPHA, SemanticRowRole.PRIMARY_ACTION),
        ),
        alignment = alignment,
    )

    /**
     * Creates a standard QWERTY text profile input specification for testing and runtime solving.
     */
    fun createStandardQwertyInput(
        availableWidth: Double,
        hasNumberRow: Boolean = false,
        config: TextProfileResponsiveConfig = TextProfileResponsiveConfig(),
        overrides: GeometryOverrides = GeometryOverrides.EMPTY,
    ): GeometrySolverInput {
        val rows = mutableListOf<GeometryRow>()

        if (hasNumberRow) {
            val numberItems = ("1234567890").map { ch ->
                GeometryItem(
                    stableId = "num_$ch",
                    kind = GeometryItemKind.KEY,
                    widthUnits = 1.0,
                    growWeight = 0.0,
                )
            }
            rows.add(
                GeometryRow(
                    stableId = "row_number",
                    role = SemanticRowRole.NUMBER_ROW,
                    items = numberItems,
                )
            )
        }

        // Row 1: QWERTYUIOP
        val row1Items = "qwertyuiop".map { ch ->
            GeometryItem(
                stableId = "key_$ch",
                kind = GeometryItemKind.KEY,
                widthUnits = config.alphaWidthUnits,
                growWeight = config.alphaGrowWeight,
            )
        }
        rows.add(GeometryRow(stableId = "row_alpha_1", role = SemanticRowRole.ALPHA, items = row1Items))

        // Row 2: ASDFGHJKL
        val row2Items = "asdfghjkl".map { ch ->
            GeometryItem(
                stableId = "key_$ch",
                kind = GeometryItemKind.KEY,
                widthUnits = config.alphaWidthUnits,
                growWeight = config.alphaGrowWeight,
            )
        }
        rows.add(GeometryRow(stableId = "row_alpha_2", role = SemanticRowRole.ALPHA, items = row2Items))

        // Row 3: SHIFT + ZXCVBNM + BACKSPACE (Symmetrical Shift/Delete balancing)
        val row3Items = mutableListOf<GeometryItem>()
        row3Items.add(
            GeometryItem(
                stableId = "key_shift",
                kind = GeometryItemKind.KEY,
                widthUnits = config.shiftDeleteBalancedWidthUnits,
                growWeight = 0.0,
            )
        )
        for (ch in "zxcvbnm") {
            row3Items.add(
                GeometryItem(
                    stableId = "key_$ch",
                    kind = GeometryItemKind.KEY,
                    widthUnits = config.alphaWidthUnits,
                    growWeight = config.alphaGrowWeight,
                )
            )
        }
        row3Items.add(
            GeometryItem(
                stableId = "key_delete",
                kind = GeometryItemKind.KEY,
                widthUnits = config.shiftDeleteBalancedWidthUnits,
                growWeight = 0.0,
            )
        )
        rows.add(GeometryRow(stableId = "row_alpha_3", role = SemanticRowRole.ALPHA, items = row3Items))

        // Row 4: Primary Action Row (?123, comma, spacebar [grow=1.0], period, enter)
        val row4Items = listOf(
            GeometryItem(stableId = "key_sym", kind = GeometryItemKind.KEY, widthUnits = 1.5, growWeight = 0.0),
            GeometryItem(stableId = "key_comma", kind = GeometryItemKind.KEY, widthUnits = 1.0, growWeight = 0.0),
            GeometryItem(stableId = "key_space", kind = GeometryItemKind.KEY, widthUnits = 4.0, growWeight = config.spacebarGrowWeight),
            GeometryItem(stableId = "key_period", kind = GeometryItemKind.KEY, widthUnits = 1.0, growWeight = 0.0),
            GeometryItem(stableId = "key_enter", kind = GeometryItemKind.KEY, widthUnits = 1.5, growWeight = 0.0),
        )
        rows.add(GeometryRow(stableId = "row_primary", role = SemanticRowRole.PRIMARY_ACTION, items = row4Items))

        return GeometrySolverInput(
            availableWidth = availableWidth,
            rows = rows,
            framePolicy = FramePolicy.WrapContent,
            rowHeightPolicy = RowHeightPolicy(),
            gapPolicy = buildBoundaryGapPolicy(availableWidth, config),
            widthPolicy = buildRowWidthPolicy(),
            overrides = overrides,
        )
    }
}
