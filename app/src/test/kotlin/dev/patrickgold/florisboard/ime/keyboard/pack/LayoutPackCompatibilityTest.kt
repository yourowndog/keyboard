package dev.patrickgold.florisboard.ime.keyboard.pack

import dev.patrickgold.florisboard.app.layoutbuilder.LayoutKey
import dev.patrickgold.florisboard.app.layoutbuilder.LayoutPack
import dev.patrickgold.florisboard.app.layoutbuilder.LayoutRow
import dev.patrickgold.florisboard.ime.keyboard.LayoutPackRowSemantics
import dev.patrickgold.florisboard.ime.keyboard.PackRoleSource
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.geometry.COMPATIBILITY
import dev.patrickgold.florisboard.ime.keyboard.geometry.EXPECTED_FIX
import dev.patrickgold.florisboard.ime.keyboard.geometry.MIGRATION_FIXTURE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stage 06: reading schema version 1 packs and bringing them forward.
 *
 * Covers the stage's required tests *Old pack decode* and *Old-to-new migration and repeated
 * migration*.
 */
class LayoutPackCompatibilityTest {

    // -- Old pack decode --------------------------------------------------------------------------

    @MIGRATION_FIXTURE(
        "The bundled pack is the only schema version 1 pack that ships, and the one every user's " +
            "saved file is derived from. It must decode from the asset tree exactly as written.",
    )
    @Test
    fun `the bundled pack decodes through the compatibility path`() {
        assertTrue(
            LayoutPackFixtures.BUNDLED_ASSET.isFile,
            "expected the bundled pack at ${LayoutPackFixtures.BUNDLED_ASSET.path}",
        )

        val outcome = LayoutPackCodec.decode(LayoutPackFixtures.BUNDLED_ASSET.readText())
        val document = assertNotNull(outcome.document, "the bundled pack must decode")

        assertEquals(LayoutPackSchema.LEGACY_VERSION, outcome.sourceVersion)
        assertTrue(outcome.wasMigrated)
        assertEquals(emptyList(), outcome.problems, "the bundled pack must also be valid")
        assertEquals("lcars_hacker_en_us", document.id)
        assertEquals(LayoutPackSchema.CURRENT_VERSION, document.schemaVersion)
        assertEquals(12f, document.units)
        assertEquals(4, document.rows.size)
    }

    @EXPECTED_FIX(
        "Before Stage 06 every row of the bundled pack resolved to ALPHA, so its space/enter row " +
            "was laid out on the letter grid. Its historical row ids now map to real roles.",
    )
    @Test
    fun `the bundled pack's historical row ids map to real roles`() {
        val document = assertNotNull(
            LayoutPackCodec.decode(LayoutPackFixtures.BUNDLED_ASSET.readText()).document,
        )

        assertEquals(
            listOf(
                SemanticRowRole.ALPHA,
                SemanticRowRole.ALPHA,
                SemanticRowRole.ALPHA,
                SemanticRowRole.PRIMARY_ACTION,
            ),
            document.rows.map { it.role },
        )
        assertEquals(
            listOf("row_letters_top", "row_letters_home", "row_letters_bottom", "row_space"),
            document.rows.map { it.id },
            "stable row ids are preserved; only the role is added",
        )
    }

    @Test
    fun `a mapped legacy role is never reported as pack-declared`() {
        val outcome = LayoutPackCodec.decode(LayoutPackFixtures.BUNDLED_ASSET.readText())

        for (rowId in listOf("row_letters_top", "row_space")) {
            assertEquals(
                PackRoleSource.COMPATIBILITY_MAPPING,
                outcome.roleSources[rowId],
                "row '$rowId' was mapped, not declared",
            )
        }
        val pack = assertNotNull(outcome.asActivePack())
        assertTrue(
            pack.roleSources.values.none { it.isPackDeclared },
            "nothing migrated from version 1 may claim to be pack-declared metadata",
        )
    }

    @Test
    fun `every mapped or ambiguous row produces a diagnostic`() {
        val outcome = LayoutPackCodec.decode(LayoutPackFixtures.LEGACY_PACK_JSON)

        val mapped = outcome.diagnostics.filter {
            it.kind == LayoutPackDiagnosticKind.LEGACY_ROLE_MAPPED
        }
        assertEquals(
            listOf("rows[0].role", "rows[1].role", "rows[2].role"),
            mapped.map { it.path },
            "row_letters_top, row_space and row_mod are all in the explicit mapping",
        )

        val ambiguous = outcome.diagnostics.single {
            it.kind == LayoutPackDiagnosticKind.LEGACY_ROLE_AMBIGUOUS
        }
        assertEquals("rows[3].role", ambiguous.path)
        assertTrue(
            ambiguous.detail.contains("mystery_row"),
            "the diagnostic must name the row, got: ${ambiguous.detail}",
        )
    }

    @Test
    fun `an ambiguous row takes the documented fallback and says so`() {
        val outcome = LayoutPackCodec.decode(LayoutPackFixtures.LEGACY_PACK_JSON)
        val document = assertNotNull(outcome.document)

        val mystery = document.rows.single { it.id == "mystery_row" }
        assertEquals(LayoutPackRowSemantics.COMPATIBILITY_FALLBACK_ROLE, mystery.role)
        assertEquals(PackRoleSource.COMPATIBILITY_FALLBACK, outcome.roleSources["mystery_row"])
    }

    @Test
    fun `the migration does not invent two bottom modifier rows`() {
        // Reading a pack as "all alpha plus two mod rows" is how version 1 made every pack look like
        // the bundled Coding composition. The migration adds no row the pack did not declare.
        val document = assertNotNull(
            LayoutPackCodec.decode(LayoutPackFixtures.BUNDLED_ASSET.readText()).document,
        )

        assertEquals(4, document.rows.size)
        assertEquals(
            0,
            document.rows.count { it.role == SemanticRowRole.CODING_UTILITY },
            "the bundled pack declares no utility rows, so the migration must not add any",
        )
    }

    // -- Version 1 structure ----------------------------------------------------------------------

    @Test
    fun `a version 1 spacer becomes an explicit spacer`() {
        val document = assertNotNull(LayoutPackCodec.decode(LayoutPackFixtures.LEGACY_PACK_JSON).document)

        val row = document.rows.first()
        assertEquals(
            listOf(
                LayoutPackElementKind.KEY,
                LayoutPackElementKind.SPACER,
                LayoutPackElementKind.KEY,
            ),
            row.keys.map { it.kind },
        )
        assertEquals("", row.keys[1].code, "a spacer carries no code")
        assertEquals(2f, row.keys[1].widthUnits, "a spacer keeps the width it occupied")
    }

    @MIGRATION_FIXTURE(
        "Version 1 silently turned a key whose code did not resolve into a spacer. Migrating it to " +
            "an explicit spacer keeps the pack rendering as it always did instead of newly " +
            "failing validation on a hole the user already had.",
    )
    @Test
    fun `a version 1 key with an unresolvable code becomes an explicit spacer`() {
        val legacy = """
            {
              "id": "typo_pack", "label": "Typo", "units": 2,
              "rows": [{ "id": "row_letters_top", "units": 2, "keys": [
                { "label": "Q", "code": "q", "units": 1 },
                { "label": "?", "code": "KEYCODE_WHAT", "units": 1 }
              ]}]
            }
        """.trimIndent()

        val outcome = LayoutPackCodec.decode(legacy)
        val document = assertNotNull(outcome.document)

        assertEquals(emptyList(), outcome.problems, "a migrated hole must not fail validation")
        assertEquals(LayoutPackElementKind.SPACER, document.rows.single().keys[1].kind)
    }

    @Test
    fun `a non-positive version 1 width is stated as one unit`() {
        // `LayoutManager` has always coerced this at layout time. Doing it in the migration means the
        // stored pack states the width it is actually laid out with.
        val legacy = """
            {
              "id": "zero_pack", "label": "Zero", "units": 2,
              "rows": [{ "id": "row_letters_top", "units": 2, "keys": [
                { "label": "Q", "code": "q", "units": 0 },
                { "label": "W", "code": "w", "units": 1 }
              ]}]
            }
        """.trimIndent()

        val document = assertNotNull(LayoutPackCodec.decode(legacy).document)

        assertEquals(listOf(1f, 1f), document.rows.single().keys.map { it.widthUnits })
    }

    @Test
    fun `a disabled version 1 row stays disabled`() {
        val document = assertNotNull(LayoutPackCodec.decode(LayoutPackFixtures.LEGACY_PACK_JSON).document)

        assertTrue(!document.rows.single { it.id == "mystery_row" }.enabled)
        assertTrue(document.rows.filter { it.id != "mystery_row" }.all { it.enabled })
    }

    // -- showIfSetting ----------------------------------------------------------------------------

    @Test
    fun `a recognisable showIfSetting stays inert during compatibility import`() {
        val outcome = LayoutPackCodec.decode(LayoutPackFixtures.LEGACY_PACK_JSON)
        val document = assertNotNull(outcome.document)

        assertEquals(
            LayoutPackVisibility.ALWAYS,
            document.rows.single { it.id == "row_mod" }.visibleWhen,
        )
        assertTrue(
            outcome.diagnostics.any {
                it.kind == LayoutPackDiagnosticKind.LEGACY_FIELD_DROPPED &&
                    it.path == "rows[2].visibleWhen"
            },
            "dropping an inert field must be reported",
        )
    }

    @COMPATIBILITY(
        "A version 1 row carrying an unreadable showIfSetting was always shown, because nothing " +
            "evaluated the field. It stays always-shown.",
    )
    @Test
    fun `an unrecognisable showIfSetting is dropped rather than hiding a row`() {
        // Version 1 never evaluated this field, so the row was always shown. Promoting an unknown
        // preference name to a condition would hide a row the user has always been able to see.
        val outcome = LayoutPackCodec.decode(LayoutPackFixtures.LEGACY_PACK_JSON)
        val document = assertNotNull(outcome.document)

        assertEquals(
            LayoutPackVisibility.ALWAYS,
            document.rows.single { it.id == "mystery_row" }.visibleWhen,
        )
        assertTrue(
            outcome.diagnostics.any {
                it.kind == LayoutPackDiagnosticKind.LEGACY_FIELD_DROPPED &&
                    it.detail.contains("some_pref_nobody_knows")
            },
            "dropping a field must name the value that was dropped",
        )
    }

    @Test
    fun `legacy visibility text never changes whether the row is shown`() {
        val spellings = listOf(
            "mod_rows_visible",
            "keyboard__mod_rows_visible",
            "number_row",
            "keyboard__number_row",
            "dev_row",
            "keyboard__dev_row",
            "some_pref_nobody_knows",
        )
        for (raw in spellings) {
            val legacy = LayoutPack(
                id = "p",
                label = "P",
                units = 1,
                rows = listOf(
                    LayoutRow(
                        id = "row_letters_top",
                        units = 1,
                        showIfSetting = raw,
                        keys = listOf(LayoutKey(label = "Q", code = "q", units = 1)),
                    )
                ),
            )

            val migrated = LayoutPackCompatibility.migrate(legacy)

            assertEquals(LayoutPackVisibility.ALWAYS, migrated.document.rows.single().visibleWhen)
            assertEquals(
                1,
                LayoutPackComposition.resolve(
                    ActiveLayoutPack(migrated.document, migrated.roleSources),
                    LayoutPackConditions(modRowsVisible = false, numberRowEnabled = false, devRowEnabled = false),
                ).size,
                "schema 1 always showed a row carrying '$raw'",
            )
        }
    }

    @MIGRATION_FIXTURE(
        "The shipped bottom letter row declares 12 units but its key widths total 13. Version 1 " +
            "rendered those widths directly, so migration states 13 rather than moving a key.",
    )
    @Test
    fun `legacy row width is normalized to the element total without changing key widths`() {
        val outcome = LayoutPackCodec.decode(LayoutPackFixtures.BUNDLED_ASSET.readText())
        val bottom = assertNotNull(outcome.document).rows.single { it.id == "row_letters_bottom" }

        assertEquals(13f, bottom.units)
        assertEquals(13f, bottom.declaredElementUnits)
        assertTrue(
            outcome.diagnostics.any {
                it.path == "rows[2].units" && it.kind == LayoutPackDiagnosticKind.LEGACY_VALUE_REWRITTEN
            },
        )
    }

    // -- Idempotency ------------------------------------------------------------------------------

    @Test
    fun `migration is deterministic`() {
        val first = LayoutPackCodec.decode(LayoutPackFixtures.LEGACY_PACK_JSON)
        val second = LayoutPackCodec.decode(LayoutPackFixtures.LEGACY_PACK_JSON)

        assertEquals(first, second, "the same bytes must always produce the same outcome")
    }

    @Test
    fun `re-reading a migrated pack is a no-op`() {
        // Idempotency is structural, not counted: a migrated pack declares the current schema, so
        // re-reading it takes the native path and the compatibility code never runs again. There is
        // no version flag that can get out of step with the files.
        val once = assertNotNull(LayoutPackCodec.decode(LayoutPackFixtures.LEGACY_PACK_JSON).document)

        val twice = LayoutPackCodec.decode(LayoutPackCodec.encode(once))

        assertEquals(once, twice.document)
        assertEquals(LayoutPackSchema.CURRENT_VERSION, twice.sourceVersion)
        assertTrue(!twice.wasMigrated, "the second read must not go through the migration")
        assertEquals(
            emptyList(),
            twice.diagnostics,
            "a migrated pack declares its roles, so there is nothing left to infer",
        )
    }

    @Test
    fun `a migrated pack's roles become declared once it is written`() {
        val migrated = LayoutPackCodec.decode(LayoutPackFixtures.BUNDLED_ASSET.readText())
        val rewritten = LayoutPackCodec.decode(LayoutPackCodec.encode(assertNotNull(migrated.document)))

        val pack = assertNotNull(rewritten.asActivePack())
        for (row in pack.document.rows) {
            assertEquals(
                PackRoleSource.DECLARED_ROLE,
                pack.roleSourceOf(row.id),
                "once the file states the role, it really is declared",
            )
        }
    }

    @Test
    fun `an explicitly versioned schema 1 pack takes the same path as an unversioned one`() {
        val unversioned = LayoutPackCodec.decode(LayoutPackFixtures.LEGACY_PACK_JSON)
        val versioned = LayoutPackCodec.decode(
            LayoutPackFixtures.LEGACY_PACK_JSON.replaceFirst("{", """{"schemaVersion": 1,"""),
        )

        assertEquals(unversioned.document, versioned.document)
        assertEquals(LayoutPackSchema.LEGACY_VERSION, versioned.sourceVersion)
    }
}
