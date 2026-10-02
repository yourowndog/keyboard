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

package dev.patrickgold.florisboard.ime.keyboard.pack

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stage 06 versioned layout-pack schema.
 *
 * A layout pack is a serialized, user-authored layout definition. Until this stage it was a bag of
 * rows with free-form ids and no version, which meant the runtime had to *guess* what each row was
 * (see `LayoutPackRowSemantics`) and a pack written by a newer build was read by an older one with
 * its new fields silently dropped. Both of those are now stated rather than inferred:
 *
 * - [LayoutPackDocument.schemaVersion] says which schema the bytes on disk follow;
 * - [LayoutPackRow.role] says what the row *is*, so pack composition and normal composition produce
 *   the same semantic contract rather than two different ones.
 *
 * The schema is deliberately structural. It describes rows, roles, ordered keys, spacers and width
 * units — the inputs `KeyboardGeometrySolver` consumes. It cannot express colours, shapes, corner
 * radii or any other rendering concern: those belong to Snygg, and a layout pack must not become a
 * second theme format.
 *
 * See `omniboard-artifacts/implementation/keyboard-geometry/06-layout-pack-schema.md` and the
 * layout-pack contract in `omniboard-artifacts/docs/architecture/keyboard-row-geometry.md`.
 */
object LayoutPackSchema {

    /**
     * The newest schema this build can read and the only one it writes.
     *
     * Version 1 is the unversioned legacy shape in
     * `dev.patrickgold.florisboard.app.layoutbuilder.LayoutPack`; it has no `schemaVersion` field at
     * all, which is how it is recognised. Version 2 is everything in this file.
     */
    const val CURRENT_VERSION: Int = 2

    /** The version assumed for a document that declares none. See [CURRENT_VERSION]. */
    const val LEGACY_VERSION: Int = 1

    /** The structural width of a pack's grid when the pack does not state one. */
    const val DEFAULT_UNITS: Float = 12f

    /**
     * Largest row/key width a pack may declare.
     *
     * Not an aesthetic limit: a pack is user-supplied data, and an absurd unit count would make the
     * solver's shared-width reference meaningless rather than merely ugly. Validation rejects it
     * instead of letting it through to geometry.
     */
    const val MAX_UNITS: Float = 64f

    /** Fields a version 2 document may declare, by JSON name. */
    val DOCUMENT_FIELDS: Set<String> = setOf("schemaVersion", "id", "label", "units", "rows")

    /** Fields a version 2 row may declare, by JSON name. */
    val ROW_FIELDS: Set<String> =
        setOf("id", "role", "units", "enabled", "visibleWhen", "policy", "keys")

    /** Fields a version 2 key or spacer may declare, by JSON name. */
    val ELEMENT_FIELDS: Set<String> = setOf("kind", "id", "code", "label", "widthUnits", "style")

    /** Fields a version 2 row policy may declare, by JSON name. */
    val POLICY_FIELDS: Set<String> = setOf("heightFactorPercent", "insetHorizontalPercent")

    /**
     * Roles a pack may name.
     *
     * [SemanticRowRole.PLACEHOLDER] is excluded on purpose: it marks the loading-only row that
     * composition builds in code, is never persisted and is never customizable. A pack that could
     * claim it would be able to describe a keyboard the user can never use.
     */
    val ALLOWED_ROLES: Set<SemanticRowRole> =
        SemanticRowRole.entries.toSet() - SemanticRowRole.PLACEHOLDER
}

/**
 * A layout pack as stored on disk, schema version 2 or later.
 *
 * @param schemaVersion which schema these fields follow. Written as [LayoutPackSchema.CURRENT_VERSION];
 *   a higher value read back means the pack came from a newer build and is refused rather than
 *   partially understood.
 * @param id the pack's stable identity. This is what the selected-pack preference persists, so it
 *   must not change when the pack's contents or [label] do.
 * @param label the pack's display name. Free to change at any time; nothing reads it back.
 * @param units the structural width of the pack's grid, in units. Rows that do not state their own
 *   width are this wide.
 * @param rows the pack's rows, in arrangement order.
 */
@Serializable
data class LayoutPackDocument(
    val schemaVersion: Int = LayoutPackSchema.CURRENT_VERSION,
    val id: String,
    val label: String,
    val units: Float = LayoutPackSchema.DEFAULT_UNITS,
    val rows: List<LayoutPackRow> = emptyList(),
) {
    /** True when this document carries no rows, i.e. describes no keyboard at all. */
    val isEmpty: Boolean
        get() = rows.isEmpty()

    companion object {
        /**
         * The document that means "no layout pack is selected".
         *
         * Composition treats an empty arrangement as "fall through to the bundled extension
         * layouts", which is the behaviour every install has today. Naming that state is what lets
         * startup restoration distinguish *nothing was selected* from *the selection could not be
         * loaded* — the first is normal, the second is a diagnostic.
         */
        val None: LayoutPackDocument = LayoutPackDocument(
            id = "dev.florisboard.layoutpacks.none",
            label = "None",
            rows = emptyList(),
        )
    }
}

/**
 * One row of a layout pack.
 *
 * @param id the row's stable identity within the pack. Customization and diagnostics address rows by
 *   this, so it survives reordering and must be unique inside the pack.
 * @param role what the row is. Declared, never inferred — this is the field whose absence in schema
 *   version 1 forced `LayoutPackRowSemantics` to guess.
 * @param units the row's structural width in units, or null to inherit the pack's.
 * @param enabled whether the row exists at all. A disabled row is dropped during composition and
 *   cannot be revealed by a preference.
 * @param visibleWhen the preference that reveals this row, for rows that are conditional rather than
 *   permanently present. The set of conditions is closed because the runtime has to be able to
 *   evaluate every one of them; see [LayoutPackVisibility].
 * @param policy optional structural overrides. Validated here, applied in Stage 07 — see
 *   [LayoutPackRowPolicy].
 * @param keys the row's keys and spacers, in order.
 */
@Serializable
data class LayoutPackRow(
    val id: String,
    val role: SemanticRowRole,
    val units: Float? = null,
    val enabled: Boolean = true,
    val visibleWhen: LayoutPackVisibility = LayoutPackVisibility.ALWAYS,
    val policy: LayoutPackRowPolicy? = null,
    val keys: List<LayoutPackElement> = emptyList(),
) {
    /** The row's declared width, falling back to [packUnits]. */
    fun widthUnits(packUnits: Float): Float = units ?: packUnits

    /** Total width the row's elements declare. */
    val declaredElementUnits: Float
        get() = keys.fold(0f) { sum, element -> sum + element.widthUnits }
}

/** Whether an element occupies width for its own sake or on behalf of a key. */
@Serializable
enum class LayoutPackElementKind {
    /** A real key. Must name a resolvable [LayoutPackElement.code]. */
    @SerialName("key")
    KEY,

    /**
     * Structural filler: occupies width, receives no touch handling.
     *
     * Spacers are explicit in this schema. Version 1 expressed them two ways — a `spacer: true`
     * flag *and*, accidentally, any key whose code failed to resolve — so an authoring typo became
     * an invisible hole in the keyboard. Here an unresolvable code on a [KEY] is a validation
     * problem, and a hole has to be asked for.
     */
    @SerialName("spacer")
    SPACER,
}

/**
 * One key or spacer in a row.
 *
 * @param kind whether this is a key or structural filler.
 * @param id optional stable identity within the row, for customization.
 * @param code what the key does: a single character, a `TextKeyData` internal key name, one of the
 *   documented aliases, or a raw integer key code. Must be empty for a [LayoutPackElementKind.SPACER].
 * @param label what the key shows. Defaults to [code] at composition time when left empty.
 * @param widthUnits structural width in units. The authored value is preserved exactly; the geometry
 *   policy supplies a canonical width only where a pack author stated none.
 * @param style the key's structural grouping — left/right special, auxiliary, space. Not a theme:
 *   it maps onto `KeyData.GROUP_*`, which decides popup grouping and nothing visual.
 */
@Serializable
data class LayoutPackElement(
    val kind: LayoutPackElementKind = LayoutPackElementKind.KEY,
    val id: String? = null,
    val code: String = "",
    val label: String = "",
    val widthUnits: Float = 1f,
    val style: LayoutPackKeyStyle = LayoutPackKeyStyle.DEFAULT,
) {
    val isSpacer: Boolean
        get() = kind == LayoutPackElementKind.SPACER
}

/**
 * A key's structural grouping.
 *
 * The serialized names are identical to the schema version 1 vocabulary, because they *are* the
 * version 1 vocabulary: `dev.patrickgold.florisboard.app.layoutbuilder.LayoutKeyStyle` is an alias
 * for this enum. Renaming an entry here would silently stop decoding packs already on disk.
 */
@Serializable
enum class LayoutPackKeyStyle {
    @SerialName("default")
    DEFAULT,

    @SerialName("aux")
    AUX,

    @SerialName("special_left")
    SPECIAL_LEFT,

    @SerialName("special_right")
    SPECIAL_RIGHT,

    @SerialName("space")
    SPACE,
}

/**
 * The conditions under which a pack row is shown.
 *
 * Closed on purpose. Schema version 1 had a free-form `showIfSetting: String?` that no runtime code
 * ever read, so every value in it was inert; promoting that to a real condition means the runtime
 * must be able to evaluate it, and it can only do that for preferences it knows. An unrecognised
 * condition is a validation problem rather than a row that silently never appears.
 */
@Serializable
enum class LayoutPackVisibility {
    /** Always present. */
    @SerialName("always")
    ALWAYS,

    /** Present while the active profile shows its modifier/utility rows. */
    @SerialName("mod_rows_visible")
    MOD_ROWS_VISIBLE,

    /** Present while the active profile has the digit row switched on. */
    @SerialName("number_row_enabled")
    NUMBER_ROW_ENABLED,

    /** Present while the active profile has the developer row switched on. */
    @SerialName("dev_row_enabled")
    DEV_ROW_ENABLED,
}

/**
 * Optional structural overrides a pack row may declare.
 *
 * Stage 06 serializes and validates these; it deliberately does not apply them. Solver-backed
 * structural editing is Stage 07, and wiring half of it here would mean two authorities for row
 * height while the second one is still being designed. What this stage guarantees is that a pack
 * carrying them round-trips without loss and that an out-of-range value is rejected at the door
 * rather than reaching the solver.
 *
 * `LayoutPackPolicyInertnessTest` pins the fact that these do not move geometry yet, so Stage 07
 * has to delete that test on purpose rather than discover the field by accident.
 *
 * @param heightFactorPercent the row's height relative to its role's policy, as a percentage.
 * @param insetHorizontalPercent how far the row is inset from the content width, as a percentage.
 */
@Serializable
data class LayoutPackRowPolicy(
    val heightFactorPercent: Int? = null,
    val insetHorizontalPercent: Int? = null,
) {
    companion object {
        /** Accepted range for [heightFactorPercent]. */
        val HEIGHT_FACTOR_RANGE: IntRange = 10..400

        /** Accepted range for [insetHorizontalPercent]. Half the width per side is already absurd. */
        val INSET_RANGE: IntRange = 0..50
    }
}
