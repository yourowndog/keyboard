package dev.patrickgold.florisboard.app.layoutbuilder

import dev.patrickgold.florisboard.ime.keyboard.pack.LayoutPackKeyStyle
import kotlinx.serialization.Serializable

/**
 * Schema version 1 layout packs: the on-disk shape, read-only as of Stage 06.
 *
 * These types describe bytes that already exist on users' devices, so they are frozen rather than
 * maintained. Nothing writes them any more — `LayoutPackCodec` writes
 * `dev.patrickgold.florisboard.ime.keyboard.pack.LayoutPackDocument` — and
 * `LayoutPackCompatibility` is the only reader, turning a version 1 pack into a current one.
 *
 * Two properties of this shape are load-bearing and pinned by `PersistenceMigrationFixtureTest`:
 * there is no `schemaVersion` field, which is how a version 1 pack is recognised; and it is decoded
 * with `ignoreUnknownKeys`, because the builds that wrote these files did.
 *
 * The one thing version 1 cannot express is what a row *means*. That is the gap Stage 06 closes, and
 * it is why migrating a pack necessarily involves inference — see `LayoutPackRowSemantics`.
 */
@Serializable
data class LayoutPack(
    val id: String,
    val label: String,
    val units: Int = DefaultUnits,
    val rows: List<LayoutRow> = emptyList(),
) {
    companion object {
        const val DefaultUnits: Int = 12
    }
}

@Serializable
data class LayoutRow(
    val id: String,
    val units: Int = LayoutPack.DefaultUnits,
    /**
     * A preference name that was never read by any runtime code, so a row carrying one was always
     * shown. Compatibility import preserves that behaviour by dropping the inert field with a
     * diagnostic; schema version 2 authors can declare a real `visibleWhen` condition explicitly.
     */
    val showIfSetting: String? = null,
    val enabled: Boolean = true,
    val keys: List<LayoutKey> = emptyList(),
)

@Serializable
data class LayoutKey(
    val id: String? = null,
    val label: String = "",
    val code: String = "",
    val units: Int = 1,
    val style: LayoutKeyStyle = LayoutKeyStyle.DEFAULT,
    val spacer: Boolean = false,
)

/**
 * The key style vocabulary, shared with the current schema.
 *
 * Version 1 and version 2 spell these identically because they are the same strings on disk; an
 * alias states that rather than letting a second enum drift away from the first.
 */
typealias LayoutKeyStyle = LayoutPackKeyStyle
