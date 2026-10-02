package dev.patrickgold.florisboard.ime.keyboard.pack

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Stage 06 persistence, migration, fallback, and restart contract. */
class LayoutPackStoreTest {

    private data class Fixture(
        val root: File,
        val packs: File,
        val legacy: File,
        val store: LayoutPackStore,
    )

    private fun fixture(): Fixture {
        val root = createTempDirectory("layout-pack-store").toFile()
        val packs = File(root, "packs")
        val legacy = File(root, "lcars_hacker_en_us.user.json")
        return Fixture(
            root = root,
            packs = packs,
            legacy = legacy,
            store = LayoutPackStore(packs, legacy) { LayoutPackFixtures.LEGACY_PACK_JSON },
        )
    }

    @Test
    fun `legacy import preserves the original and is idempotent`() {
        val f = fixture()
        try {
            val original = LayoutPackFixtures.LEGACY_PACK_JSON.toByteArray()
            f.legacy.writeBytes(original)

            val first = assertIs<LayoutPackStore.LegacyImport.Imported>(f.store.importLegacyUserFile())
            assertEquals("legacy_pack", first.packId)
            assertContentEquals(original, f.legacy.readBytes(), "migration must not rewrite the only legacy copy")

            val migrated = assertIs<LayoutPackStore.Restoration.Restored>(f.store.restore("legacy_pack"))
            assertEquals(LayoutPackSchema.CURRENT_VERSION, migrated.pack.document.schemaVersion)
            assertEquals(
                LayoutPackFixtures.LEGACY_PACK_JSON.let(LayoutPackCodec::decode).document,
                migrated.pack.document,
            )

            val bytesAfterFirstImport = f.store.fileFor("legacy_pack").readBytes()
            val second = assertIs<LayoutPackStore.LegacyImport.AlreadyImported>(f.store.importLegacyUserFile())
            assertEquals("legacy_pack", second.packId)
            assertContentEquals(bytesAfterFirstImport, f.store.fileFor("legacy_pack").readBytes())
            assertContentEquals(original, f.legacy.readBytes())
        } finally {
            f.root.deleteRecursively()
        }
    }

    @Test
    fun `saving twice keeps the previous valid pack as a backup`() {
        val f = fixture()
        try {
            val first = LayoutPackFixtures.currentPack(id = "saved")
            assertIs<LayoutPackStore.SaveResult.Written>(f.store.save(first))
            val firstBytes = f.store.fileFor("saved").readBytes()

            val second = first.copy(label = "Renamed")
            assertIs<LayoutPackStore.SaveResult.Written>(f.store.save(second))

            val backup = File(f.packs, "saved.json.bak")
            assertTrue(backup.isFile)
            assertContentEquals(firstBytes, backup.readBytes())
            assertEquals(second, assertIs<LayoutPackStore.Restoration.Restored>(f.store.restore("saved")).pack.document)
        } finally {
            f.root.deleteRecursively()
        }
    }

    @Test
    fun `a rejected replacement leaves the saved pack untouched`() {
        val f = fixture()
        try {
            val original = LayoutPackFixtures.currentPack(id = "saved")
            assertIs<LayoutPackStore.SaveResult.Written>(f.store.save(original))
            val bytes = f.store.fileFor("saved").readBytes()
            val invalid = original.copy(rows = listOf(LayoutPackFixtures.alphaRow(id = "same"), LayoutPackFixtures.alphaRow(id = "same")))

            assertIs<LayoutPackStore.SaveResult.Rejected>(f.store.save(invalid))
            assertContentEquals(bytes, f.store.fileFor("saved").readBytes())
            assertEquals(original, assertIs<LayoutPackStore.Restoration.Restored>(f.store.restore("saved")).pack.document)
        } finally {
            f.root.deleteRecursively()
        }
    }

    @Test
    fun `missing corrupt invalid and newer selected packs fall back safely`() {
        val f = fixture()
        try {
            assertEquals(
                LayoutPackStore.FallbackReason.MISSING,
                assertIs<LayoutPackStore.Restoration.FellBack>(f.store.restore("missing")).reason,
            )

            f.packs.mkdirs()
            f.store.fileFor("corrupt").writeText("not json")
            assertEquals(
                LayoutPackStore.FallbackReason.UNREADABLE,
                assertIs<LayoutPackStore.Restoration.FellBack>(f.store.restore("corrupt")).reason,
            )

            f.store.fileFor("invalid").writeText(
                """{"schemaVersion":2,"id":"invalid","label":"Invalid","units":12,"rows":[{"id":"r","role":"alpha","units":12,"keys":[]}]}""",
            )
            assertEquals(
                LayoutPackStore.FallbackReason.INVALID,
                assertIs<LayoutPackStore.Restoration.FellBack>(f.store.restore("invalid")).reason,
            )

            f.store.fileFor("future").writeText(
                """{"schemaVersion":99,"id":"future","label":"Future","units":12,"rows":[]}""",
            )
            assertEquals(
                LayoutPackStore.FallbackReason.UNSUPPORTED_NEWER,
                assertIs<LayoutPackStore.Restoration.FellBack>(f.store.restore("future")).reason,
            )

            assertEquals(ActiveLayoutPack.None, f.store.restore("missing").activePack())
            assertIs<LayoutPackStore.Restoration.NoSelection>(f.store.restore(""))
            assertIs<LayoutPackStore.Restoration.NoSelection>(f.store.restore(null))
        } finally {
            f.root.deleteRecursively()
        }
    }

    @Test
    fun `deleting the selected pack after save produces the missing fallback`() {
        val f = fixture()
        try {
            val pack = LayoutPackFixtures.currentPack(id = "deleted")
            assertIs<LayoutPackStore.SaveResult.Written>(f.store.save(pack))
            assertTrue(f.store.fileFor("deleted").delete())

            val restoration = assertIs<LayoutPackStore.Restoration.FellBack>(f.store.restore("deleted"))
            assertEquals(LayoutPackStore.FallbackReason.MISSING, restoration.reason)
            assertEquals(ActiveLayoutPack.None, restoration.activePack())
        } finally {
            f.root.deleteRecursively()
        }
    }
}
