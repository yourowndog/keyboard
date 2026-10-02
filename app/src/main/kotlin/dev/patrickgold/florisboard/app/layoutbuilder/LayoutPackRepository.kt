package dev.patrickgold.florisboard.app.layoutbuilder

import android.content.Context
import android.net.Uri
import dev.patrickgold.florisboard.ime.keyboard.pack.ActiveLayoutPack
import dev.patrickgold.florisboard.ime.keyboard.pack.LayoutPackCodec
import dev.patrickgold.florisboard.ime.keyboard.pack.LayoutPackDocument
import dev.patrickgold.florisboard.ime.keyboard.pack.LayoutPackReadOutcome
import dev.patrickgold.florisboard.ime.keyboard.pack.LayoutPackStore
import java.io.File

/**
 * The Android side of layout-pack persistence.
 *
 * Everything that decides *what happens* lives in [LayoutPackStore], which is a plain file store
 * with no `Context`. This class supplies the three Android-shaped things the store cannot obtain for
 * itself — the app's private directory, the bundled asset, and content-provider URIs — and nothing
 * else. That split is what makes the stage's required persistence tests ordinary unit tests.
 */
class LayoutPackRepository(
    private val context: Context,
) {
    private val defaultAssetPath = "layouts/lcars_hacker_en_us.json"

    private val userDir: File by lazy {
        File(context.filesDir, "layouts/user").also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    /**
     * The one fixed filename the Layout Builder has always written.
     *
     * Read and preserved, never written again. See [LayoutPackStore.importLegacyUserFile].
     */
    private val legacyUserFile: File by lazy {
        File(userDir, "lcars_hacker_en_us.user.json")
    }

    private val packsDir: File by lazy {
        File(userDir, "packs").also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    val store: LayoutPackStore by lazy {
        LayoutPackStore(
            packsDir = packsDir,
            legacyUserFile = legacyUserFile,
            readBundledDefault = {
                runCatching {
                    context.assets.open(defaultAssetPath).bufferedReader().use { it.readText() }
                }.getOrNull()
            },
        )
    }

    /** The bundled pack at the current schema version, or null when it cannot be read. */
    fun loadBundled(): LayoutPackReadOutcome? = store.readBundled()

    /**
     * The pack that should be active for [selectedPackId], with a safe fallback.
     *
     * This replaces the old `loadInitialLayout()` sentinel: a selection is honoured, and every way
     * of failing to honour it is named rather than collapsing into an empty pack.
     */
    fun restore(selectedPackId: String?): LayoutPackStore.Restoration = store.restore(selectedPackId)

    /** Brings the legacy fixed user file forward, once. Idempotent; never destructive. */
    fun importLegacyUserFile(): LayoutPackStore.LegacyImport = store.importLegacyUserFile()

    /** Writes [document] at the current schema version, keeping the previous contents as a backup. */
    fun save(document: LayoutPackDocument): LayoutPackStore.SaveResult = store.save(document)

    /** Reads a pack the user picked with a document picker. */
    fun load(uri: Uri): LayoutPackReadOutcome {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return LayoutPackCodec.decode("")
        val raw = stream.use { it.bufferedReader(Charsets.UTF_8).readText() }
        return LayoutPackCodec.decode(raw)
    }

    /** Writes [document] to a location the user picked with a document picker. */
    fun export(document: LayoutPackDocument, uri: Uri): Result<Unit> = runCatching {
        val stream = requireNotNull(context.contentResolver.openOutputStream(uri)) {
            "No output stream"
        }
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(LayoutPackCodec.encode(document))
        }
    }

    /**
     * The pack the Layout Builder opens with: the user's own if they have one, otherwise the bundled
     * pack as an editable starting point.
     *
     * Note that this is an *editing* default, not a runtime one. Nothing here activates a pack; only
     * an explicit apply does, which is what keeps an install that has never chosen a pack composing
     * its keyboard from the bundled extension layouts.
     */
    fun loadForEditing(selectedPackId: String?): ActiveLayoutPack {
        val restored = restore(selectedPackId)
        if (restored is LayoutPackStore.Restoration.Restored) {
            return restored.pack
        }
        return loadBundled()?.asActivePack() ?: ActiveLayoutPack.None
    }
}
