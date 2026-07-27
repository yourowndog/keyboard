/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName

/**
 * Stage 01 normalized semantic row model.
 *
 * Composition knows what each row *is* at the moment it builds it, and until now threw that away:
 * downstream code was left to guess from row index, row count, `isAlpha`, or a literal Space code.
 * The types here let composition state row identity explicitly and carry it to consumers.
 *
 * This stage only records semantics. Nothing here drives geometry yet — `isAlpha` and
 * [dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard.bottomModRowCount] remain the
 * compatibility projection that sizing and layout consume. See
 * `omniboard-artifacts/implementation/keyboard-geometry/01-semantic-rows.md`.
 */

/**
 * What a row *is*, independent of where its asset came from or where it landed in the arrangement.
 *
 * Roles are not geometry aliases: a numeric row does not become [ALPHA] to obtain ordinary sizing,
 * and the merged Space/punctuation/action row does not become [CODING_UTILITY] because its asset
 * happens to live in a directory historically named `mod`.
 */
enum class SemanticRowRole(internal val idPrefix: String) {
    /** Letter-entry row. */
    ALPHA("alpha"),

    /** The main Space/punctuation/action row. Present in both Text and Coding. */
    PRIMARY_ACTION("primary_action"),

    /** A Coding utility row: navigation, arrows, Escape, modifiers and related controls. */
    CODING_UTILITY("coding_utility"),

    /** An explicitly inserted extension row, such as the number row or the developer row. */
    EXTENSION("extension"),

    /** Numeric-entry row (Numeric, Numeric-Advanced, Phone, Phone2). */
    NUMERIC("numeric"),

    /** Symbol-entry row. */
    SYMBOL("symbol"),

    /** Loading-only row. Never persisted and never customizable. */
    PLACEHOLDER("placeholder"),
}

/**
 * Where a row came from. Provenance is kept for diagnostics and migration; it must never control
 * behaviour implicitly.
 */
sealed interface RowProvenance {
    /** A row taken from a single bundled layout asset. */
    data class Bundled(
        val layoutType: LayoutType,
        val component: ExtensionComponentName?,
        val sourceRowIndex: Int,
    ) : RowProvenance

    /**
     * A row spliced together during merge, where a modifier row's code-0 placeholder was replaced
     * by the contents of a main row. [modifier] is null when the modifier asset had no rows.
     */
    data class Merged(
        val main: Bundled,
        val modifier: Bundled?,
    ) : RowProvenance

    /** A row decoded from a user layout pack. */
    data class Pack(
        val packId: String,
        val rowId: String,
        val sourceRowIndex: Int,
        val roleSource: PackRoleSource,
    ) : RowProvenance

    /** A row built in code with no source asset, such as the loading placeholder. */
    data object Synthetic : RowProvenance
}

/**
 * How a layout-pack row's [SemanticRowRole] was determined.
 *
 * Packs written before Stage 06 carry no role metadata, so their roles are inferred. Recording the
 * source keeps an inferred role from being mistaken for pack-declared metadata.
 */
enum class PackRoleSource {
    /** The pack's row id named a known role. */
    DECLARED_ROW_ID,

    /** The pack was ambiguous and the row fell back to the documented compatibility role. */
    COMPATIBILITY_FALLBACK,
}

/**
 * Reference to the geometry policy a row is laid out under.
 *
 * Stage 01 introduces the field but no policies: geometry still comes from the legacy compatibility
 * projection. Stage 02 replaces [Unassigned] with real policy references.
 */
sealed interface GeometryPolicyRef {
    data object Unassigned : GeometryPolicyRef
}

/**
 * A composed row's identity, as known at composition time.
 *
 * [stableId] is role-scoped, not positional: `alpha:0` stays `alpha:0` whether or not extension rows
 * are inserted above it.
 */
data class NormalizedRow(
    val stableId: String,
    val role: SemanticRowRole,
    val provenance: RowProvenance,
    val geometryPolicy: GeometryPolicyRef = GeometryPolicyRef.Unassigned,
)

/** Which empty-sentinel keyboard this is. Sentinels carry no rows and no layout semantics. */
enum class SentinelKind {
    /** The Editing keyboard, whose layout lives in a custom XML file rather than in an arrangement. */
    EDITING,

    /** The Smartbar quick-actions keyboard, whose actions are rendered by Compose, not by rows. */
    SMARTBAR_QUICK_ACTIONS,
}

/**
 * The semantic contract a keyboard declares. Every construction site states one explicitly, so no
 * keyboard can inherit semantics it never declared.
 */
sealed interface KeyboardSemantics {
    /** A keyboard with rows. [rows] is parallel to the arrangement, one entry per row. */
    data class Rows(val rows: List<NormalizedRow>) : KeyboardSemantics

    /** A keyboard with no rows, which exists for a purpose other than laying keys out. */
    data class Sentinel(val kind: SentinelKind) : KeyboardSemantics
}

/**
 * Validates that this contract can describe an arrangement of [rowCount] rows.
 *
 * @throws IllegalArgumentException if a sentinel has rows, if the row descriptions do not match the
 *   arrangement one-to-one, or if a stable ID is blank or duplicated.
 */
internal fun KeyboardSemantics.validateAgainst(rowCount: Int) {
    when (this) {
        is KeyboardSemantics.Sentinel -> {
            require(rowCount == 0) {
                "sentinel keyboard ($kind) must have no rows, but the arrangement has $rowCount"
            }
        }
        is KeyboardSemantics.Rows -> {
            require(rows.size == rowCount) {
                "semantic rows (${rows.size}) must match the arrangement ($rowCount) one-to-one"
            }
            val ids = rows.map { it.stableId }
            require(ids.none { it.isBlank() }) { "every semantic row needs a non-blank stable ID" }
            val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            require(duplicates.isEmpty()) { "duplicate stable row IDs: ${duplicates.sorted()}" }
        }
    }
}

/**
 * Accumulates [NormalizedRow]s in arrangement order, assigning each a unique role-scoped stable ID.
 *
 * Rows must be added in the same order they are appended to the arrangement, so that the two stay
 * parallel.
 */
class NormalizedRowsBuilder {
    private val rows = mutableListOf<NormalizedRow>()
    private val ordinals = mutableMapOf<SemanticRowRole, Int>()

    val size: Int
        get() = rows.size

    fun add(role: SemanticRowRole, provenance: RowProvenance): NormalizedRow {
        val ordinal = ordinals.getOrElse(role) { 0 }
        ordinals[role] = ordinal + 1
        // The primary action row is unique per keyboard, so it gets an unsuffixed ID. A suffix is
        // still applied if a layout somehow produces a second one, to keep IDs unique.
        val stableId = if (role == SemanticRowRole.PRIMARY_ACTION && ordinal == 0) {
            role.idPrefix
        } else {
            "${role.idPrefix}:$ordinal"
        }
        val row = NormalizedRow(stableId = stableId, role = role, provenance = provenance)
        rows.add(row)
        return row
    }

    fun build(): KeyboardSemantics.Rows = KeyboardSemantics.Rows(rows.toList())
}

/**
 * The role a main layout's non-merged rows carry in this mode.
 *
 * Numeric and symbol rows keep their own identity rather than claiming to be alpha rows in order to
 * receive ordinary sizing.
 */
internal fun KeyboardMode.mainRowRole(): SemanticRowRole = when (this) {
    KeyboardMode.NUMERIC,
    KeyboardMode.NUMERIC_ADVANCED,
    KeyboardMode.PHONE,
    KeyboardMode.PHONE2 -> SemanticRowRole.NUMERIC
    KeyboardMode.SYMBOLS,
    KeyboardMode.SYMBOLS2 -> SemanticRowRole.SYMBOL
    else -> SemanticRowRole.ALPHA
}

/**
 * Maps a layout-pack row onto a semantic role.
 *
 * Packs have no role metadata yet (Stage 06 adds a versioned schema), so this is an explicit
 * compatibility decoder, not a native reader. A row whose id names a known role is taken at its
 * word; anything else is genuinely ambiguous and falls back to [SemanticRowRole.ALPHA] — the role
 * whose geometry pack rows already receive — flagged as
 * [PackRoleSource.COMPATIBILITY_FALLBACK] so callers can tell an inferred role from a declared one.
 */
object LayoutPackRowSemantics {

    /** The role assigned to a pack row that declares nothing recognisable. */
    val COMPATIBILITY_FALLBACK_ROLE = SemanticRowRole.ALPHA

    private val declaredRoles: Map<String, SemanticRowRole> =
        SemanticRowRole.entries.associateBy { it.idPrefix }

    /** Resolves [rowId] to a role and records how that role was determined. */
    fun resolve(rowId: String): Pair<SemanticRowRole, PackRoleSource> {
        val declared = declaredRoles[rowId.trim().lowercase()]
        return if (declared != null) {
            declared to PackRoleSource.DECLARED_ROW_ID
        } else {
            COMPATIBILITY_FALLBACK_ROLE to PackRoleSource.COMPATIBILITY_FALLBACK
        }
    }
}
