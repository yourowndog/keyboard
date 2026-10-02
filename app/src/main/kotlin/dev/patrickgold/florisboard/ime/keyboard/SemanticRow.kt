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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
@Serializable
enum class SemanticRowRole(internal val idPrefix: String) {
    /** Letter-entry row. */
    @SerialName("alpha")
    ALPHA("alpha"),

    /** The main Space/punctuation/action row. Present in both Text and Coding. */
    @SerialName("primary_action")
    PRIMARY_ACTION("primary_action"),

    /** A Coding utility row: navigation, arrows, Escape, modifiers and related controls. */
    @SerialName("coding_utility")
    CODING_UTILITY("coding_utility"),

    /** An explicitly inserted extension row, such as the number row or the developer row. */
    @SerialName("extension")
    EXTENSION("extension"),

    /** Numeric-entry row (Numeric, Numeric-Advanced, Phone, Phone2). */
    @SerialName("numeric")
    NUMERIC("numeric"),

    /** Symbol-entry row. */
    @SerialName("symbol")
    SYMBOL("symbol"),

    /** Loading-only row. Never persisted and never customizable. */
    @SerialName("placeholder")
    PLACEHOLDER("placeholder"),
    ;

    companion object {
        /**
         * The role whose serialized name is [name], or null when nothing matches.
         *
         * Stage 06 writes roles into layout packs, so this vocabulary is now persisted user data.
         * The serial names above are deliberately the same strings as [idPrefix] — one vocabulary
         * for stable row IDs and for pack metadata, rather than two that can drift. An annotation
         * cannot be derived from a property, so `LayoutPackSchemaTest` pins the equality instead.
         */
        fun fromSerialName(name: String?): SemanticRowRole? = entries.find { it.idPrefix == name }
    }
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
 * Schema version 1 packs carry no role metadata, so their roles are inferred. Recording the source
 * keeps an inferred role from being mistaken for pack-declared metadata. The four sources are
 * ordered by how much the pack actually said, most to least.
 */
enum class PackRoleSource {
    /**
     * The pack declared a `role` field. Only a schema version 2 or later pack can reach this, and
     * it is the only source that is pack *metadata* rather than an inference.
     */
    DECLARED_ROLE,

    /** The pack's row id named a known role. */
    DECLARED_ROW_ID,

    /**
     * The row id is a known historical id — `row_letters_top`, `row_space` and friends — and was
     * resolved through [LayoutPackRowSemantics.LEGACY_ROW_IDS].
     *
     * This is deliberately distinct from [DECLARED_ROW_ID]: a legacy id is not authoritative
     * semantics, it is a documented guess about what the pack author meant. Stage 06 requires the
     * guess to be explicit and named rather than implied by the id's spelling.
     */
    COMPATIBILITY_MAPPING,

    /** The pack was ambiguous and the row fell back to the documented compatibility role. */
    COMPATIBILITY_FALLBACK,
    ;

    /** Whether the pack itself stated the role, as opposed to the role having been inferred. */
    val isPackDeclared: Boolean
        get() = this == DECLARED_ROLE
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

    /**
     * The role of every row appended so far, in append order.
     *
     * Row `i` here is row `i` of the arrangement being built alongside it, so a caller mid-merge
     * can ask what a row *is* instead of counting how many rows precede it.
     */
    val roles: List<SemanticRowRole>
        get() = rows.map { it.role }

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
 * Maps a layout-pack row with no declared role onto a semantic role.
 *
 * A schema version 2 pack states its roles and never comes here. This is the compatibility decoder
 * for schema version 1 packs, which carry no role metadata at all, and it resolves a row id in
 * three documented steps:
 *
 * 1. the id *is* a role name (`alpha`, `primary_action`, …) — taken at its word,
 *    [PackRoleSource.DECLARED_ROW_ID];
 * 2. the id is a known historical id listed in [LEGACY_ROW_IDS] — mapped explicitly,
 *    [PackRoleSource.COMPATIBILITY_MAPPING];
 * 3. anything else is genuinely ambiguous and falls back to [COMPATIBILITY_FALLBACK_ROLE] — the
 *    role whose geometry pack rows already receive — flagged
 *    [PackRoleSource.COMPATIBILITY_FALLBACK].
 *
 * Step 2 exists because Stage 06 forbids treating ids such as `row_letters_*` or `row_space` as
 * authoritative semantics *without an explicit compatibility mapping*. The table below is that
 * mapping, and a row resolved through it is never reported as pack-declared.
 */
object LayoutPackRowSemantics {

    /** The role assigned to a pack row that declares nothing recognisable. */
    val COMPATIBILITY_FALLBACK_ROLE = SemanticRowRole.ALPHA

    /**
     * Historical schema version 1 row ids and what they are taken to mean.
     *
     * Every entry is a decision about an existing on-disk pack, not a naming convention for new
     * ones: version 2 packs declare `role` and must not rely on any of these spellings. The bundled
     * `layouts/lcars_hacker_en_us.json` supplies the first four; the rest are the ids the Layout
     * Builder's own row-insertion labels produce.
     */
    val LEGACY_ROW_IDS: Map<String, SemanticRowRole> = mapOf(
        "row_letters_top" to SemanticRowRole.ALPHA,
        "row_letters_home" to SemanticRowRole.ALPHA,
        "row_letters_bottom" to SemanticRowRole.ALPHA,
        "row_space" to SemanticRowRole.PRIMARY_ACTION,
        "row_alpha" to SemanticRowRole.ALPHA,
        "row_letters" to SemanticRowRole.ALPHA,
        "row_primary" to SemanticRowRole.PRIMARY_ACTION,
        "row_primary_action" to SemanticRowRole.PRIMARY_ACTION,
        "row_number" to SemanticRowRole.EXTENSION,
        "row_numbers" to SemanticRowRole.EXTENSION,
        "row_dev" to SemanticRowRole.EXTENSION,
        "row_mod" to SemanticRowRole.CODING_UTILITY,
        "row_utility" to SemanticRowRole.CODING_UTILITY,
        "row_ctrl" to SemanticRowRole.CODING_UTILITY,
        "row_symbols" to SemanticRowRole.SYMBOL,
        "row_numeric" to SemanticRowRole.NUMERIC,
    )

    private val declaredRoles: Map<String, SemanticRowRole> =
        SemanticRowRole.entries.associateBy { it.idPrefix }

    /** Resolves [rowId] to a role and records how that role was determined. */
    fun resolve(rowId: String): Pair<SemanticRowRole, PackRoleSource> {
        val normalized = rowId.trim().lowercase()
        declaredRoles[normalized]?.let { return it to PackRoleSource.DECLARED_ROW_ID }
        LEGACY_ROW_IDS[normalized]?.let { return it to PackRoleSource.COMPATIBILITY_MAPPING }
        return COMPATIBILITY_FALLBACK_ROLE to PackRoleSource.COMPATIBILITY_FALLBACK
    }
}
