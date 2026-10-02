package dev.patrickgold.florisboard.ime.keyboard.pack

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.geometry.COMPATIBILITY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 06: the versioned schema, its vocabulary, and its round trip.
 *
 * Covers the stage's required tests *New pack round trip* and *Unknown semantic fields and
 * unsupported schema version*.
 */
class LayoutPackSchemaTest {

    // -- Vocabulary ------------------------------------------------------------------------------

    @Test
    fun `every role's serialized name is its stable-id prefix`() {
        // Roles are now persisted in packs *and* used to build stable row IDs. The two vocabularies
        // are the same strings by construction everywhere except the @SerialName annotations, which
        // cannot be derived from a property — so they are pinned here instead.
        for (role in SemanticRowRole.entries) {
            assertEquals(
                role.serialName,
                SemanticRowRole.fromSerialName(role.serialName)?.serialName,
                "role ${role.name} does not round-trip through its serial name",
            )
        }
        assertEquals(SemanticRowRole.ALPHA, SemanticRowRole.fromSerialName("alpha"))
        assertEquals(SemanticRowRole.PRIMARY_ACTION, SemanticRowRole.fromSerialName("primary_action"))
        assertNull(SemanticRowRole.fromSerialName("ALPHA"), "the vocabulary is lower case")
        assertNull(SemanticRowRole.fromSerialName(null))
    }

    @Test
    fun `a pack may not claim the placeholder role`() {
        // PLACEHOLDER marks the loading-only row composition builds in code. A pack that could claim
        // it would describe a keyboard the user can never reach.
        assertTrue(SemanticRowRole.PLACEHOLDER !in LayoutPackSchema.ALLOWED_ROLES)
        assertEquals(
            SemanticRowRole.entries.size - 1,
            LayoutPackSchema.ALLOWED_ROLES.size,
            "every role except PLACEHOLDER must be claimable",
        )

        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(LayoutPackFixtures.alphaRow().copy(role = SemanticRowRole.PLACEHOLDER)),
        )
        val problems = LayoutPackValidation.validate(pack)
        assertEquals(
            listOf(LayoutPackProblemKind.DISALLOWED_ROLE),
            problems.map { it.kind },
        )
    }

    // -- Round trip ------------------------------------------------------------------------------

    @Test
    fun `a new pack round trips without semantic loss`() {
        val original = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackFixtures.alphaRow(),
                LayoutPackFixtures.rowWithSpacer(),
                LayoutPackFixtures.primaryActionRow().copy(
                    visibleWhen = LayoutPackVisibility.MOD_ROWS_VISIBLE,
                    policy = LayoutPackRowPolicy(heightFactorPercent = 120, insetHorizontalPercent = 4),
                ),
            ),
        )

        val encoded = LayoutPackCodec.encode(original)
        val decoded = LayoutPackCodec.decode(encoded)

        assertEquals(emptyList(), decoded.problems, "a fixture pack must validate")
        assertEquals(original, decoded.document)
        assertEquals(LayoutPackSchema.CURRENT_VERSION, decoded.sourceVersion)
        assertTrue(!decoded.wasMigrated, "a current-schema pack needs no compatibility path")
    }

    @Test
    fun `encoding always writes the schema version`() {
        // The default on `schemaVersion` means a serializer that omits defaults would write a
        // version 2 pack that reads back as unversioned version 1 — silently, and only noticed once
        // roles started being guessed again.
        val encoded = LayoutPackCodec.encode(LayoutPackFixtures.currentPack())

        assertTrue(
            encoded.contains("\"schemaVersion\": ${LayoutPackSchema.CURRENT_VERSION}"),
            "the encoded pack must state its schema version, got:\n$encoded",
        )
    }

    @Test
    fun `encoding stamps the current version onto a document that claims another`() {
        val stale = LayoutPackFixtures.currentPack().copy(schemaVersion = 1)

        val decoded = LayoutPackCodec.decode(LayoutPackCodec.encode(stale))

        assertEquals(LayoutPackSchema.CURRENT_VERSION, decoded.document?.schemaVersion)
    }

    @Test
    fun `a round trip is stable under repetition`() {
        val once = LayoutPackCodec.encode(LayoutPackFixtures.currentPack())
        val twice = LayoutPackCodec.encode(assertNotNull(LayoutPackCodec.decode(once).document))

        assertEquals(once, twice, "encoding a decoded pack must reproduce the same bytes")
    }

    // -- Unsupported newer schema ----------------------------------------------------------------

    @Test
    fun `a newer schema is refused rather than partly understood`() {
        val future = """
            {
              "schemaVersion": ${LayoutPackSchema.CURRENT_VERSION + 1},
              "id": "future_pack",
              "label": "From the future",
              "units": 12,
              "rows": [
                {
                  "id": "alpha_top",
                  "role": "alpha",
                  "units": 12,
                  "hiddenUnlessRaining": true,
                  "keys": [{ "kind": "key", "code": "q", "widthUnits": 12 }]
                }
              ]
            }
        """.trimIndent()

        val outcome = LayoutPackCodec.decode(future)

        assertNull(outcome.document, "a newer pack must not be decoded at all")
        assertEquals(
            listOf(LayoutPackProblemKind.UNSUPPORTED_SCHEMA_VERSION),
            outcome.problems.map { it.kind },
        )
        assertEquals(LayoutPackSchema.CURRENT_VERSION + 1, outcome.sourceVersion)
        assertTrue(!outcome.isUsable)
    }

    @Test
    fun `an unsupported newer pack is never written back in reduced form`() {
        // The failure this guards is the whole reason the codec exists: decode-with-ignore followed
        // by a save rewrites a newer pack as an older one, destroying whatever the new schema added.
        val future = """{"schemaVersion": 99, "id": "p", "label": "P", "rows": []}"""

        assertNull(LayoutPackCodec.decode(future).asActivePack())
    }

    // -- Unknown fields ---------------------------------------------------------------------------

    @Test
    fun `an unknown document field is reported with its path`() {
        val raw = """
            {
              "schemaVersion": 2,
              "id": "p",
              "label": "P",
              "units": 12,
              "tintColor": "#ff0000",
              "rows": []
            }
        """.trimIndent()

        val outcome = LayoutPackCodec.decode(raw)

        assertNull(outcome.document)
        val problem = outcome.problems.single()
        assertEquals(LayoutPackProblemKind.UNKNOWN_FIELD, problem.kind)
        assertEquals("tintColor", problem.path)
    }

    @Test
    fun `unknown row and key fields are all reported, not just the first`() {
        val raw = """
            {
              "schemaVersion": 2,
              "id": "p",
              "label": "P",
              "units": 12,
              "rows": [
                {
                  "id": "r",
                  "role": "alpha",
                  "units": 12,
                  "cornerRadius": 8,
                  "keys": [
                    { "kind": "key", "code": "q", "widthUnits": 12, "glow": true }
                  ]
                }
              ]
            }
        """.trimIndent()

        val outcome = LayoutPackCodec.decode(raw)

        assertEquals(
            listOf("rows[0].cornerRadius", "rows[0].keys[0].glow"),
            outcome.problems.map { it.path },
            "a strict deserializer would abort on the first; the author needs both",
        )
    }

    @Test
    fun `a behaviour-bearing unknown field is not silently dropped`() {
        // `visibleUnless` is a plausible near-miss for `visibleWhen`. Ignoring it would leave a row
        // that the author expected to be conditional permanently visible.
        val raw = """
            {
              "schemaVersion": 2, "id": "p", "label": "P", "units": 12,
              "rows": [{
                "id": "r", "role": "alpha", "units": 12,
                "visibleUnless": "mod_rows_visible",
                "keys": [{ "kind": "key", "code": "q", "widthUnits": 12 }]
              }]
            }
        """.trimIndent()

        val outcome = LayoutPackCodec.decode(raw)

        assertNull(outcome.document, "the pack must not load with the field quietly discarded")
        assertEquals(LayoutPackProblemKind.UNKNOWN_FIELD, outcome.problems.single().kind)
    }

    @Test
    fun `an out-of-vocabulary role, kind or style is named with the allowed set`() {
        val raw = """
            {
              "schemaVersion": 2, "id": "p", "label": "P", "units": 12,
              "rows": [{
                "id": "r", "role": "letters", "units": 12,
                "keys": [{ "kind": "button", "code": "q", "widthUnits": 12, "style": "neon" }]
              }]
            }
        """.trimIndent()

        val outcome = LayoutPackCodec.decode(raw)

        assertNull(outcome.document)
        assertEquals(
            listOf("rows[0].role", "rows[0].keys[0].kind", "rows[0].keys[0].style"),
            outcome.problems.map { it.path },
        )
        assertTrue(outcome.problems.all { it.kind == LayoutPackProblemKind.UNKNOWN_VOCABULARY })
        assertTrue(
            outcome.problems.first().detail.contains("alpha"),
            "the message must name what is allowed, got: ${outcome.problems.first().detail}",
        )
    }

    @Test
    fun `a row with no declared role is refused rather than defaulted`() {
        // Defaulting a missing role to alpha is precisely the version 1 behaviour this schema
        // exists to end.
        val raw = """
            {
              "schemaVersion": 2, "id": "p", "label": "P", "units": 12,
              "rows": [{ "id": "r", "units": 12, "keys": [{ "kind": "key", "code": "q", "widthUnits": 12 }] }]
            }
        """.trimIndent()

        val outcome = LayoutPackCodec.decode(raw)

        assertNull(outcome.document)
        assertEquals("rows[0].role", outcome.problems.single().path)
    }

    // -- Malformed input ---------------------------------------------------------------------------

    @Test
    fun `malformed input never throws`() {
        val inputs = listOf(
            "",
            "   ",
            "not json at all",
            "[]",
            "null",
            "42",
            """{"schemaVersion": "two", "id": "p", "label": "P"}""",
            """{"schemaVersion": 0, "id": "p", "label": "P"}""",
            """{"schemaVersion": -1, "id": "p", "label": "P"}""",
            """{"schemaVersion": 2, "id": "p", "label": "P", "rows": "lots"}""",
            """{"schemaVersion": 2, "id": "p", "label": "P", "rows": [3]}""",
            """{"schemaVersion": 2, "id": "p", "label": "P", "units": "wide", "rows": []}""",
        )

        for (input in inputs) {
            val outcome = LayoutPackCodec.decode(input)
            assertTrue(
                !outcome.isUsable,
                "'$input' must not produce a usable pack",
            )
            assertTrue(
                outcome.problems.isNotEmpty(),
                "'$input' must come back with a reason, not an empty failure",
            )
        }
    }

    @COMPATIBILITY("Packs with no rows decode; they simply contribute nothing to composition.")
    @Test
    fun `an empty pack is readable and contributes nothing`() {
        val outcome = LayoutPackCodec.decode(
            """{"schemaVersion": 2, "id": "empty_pack", "label": "Empty", "units": 12, "rows": []}""",
        )

        val document = assertNotNull(outcome.document)
        assertTrue(document.isEmpty)
        assertEquals(emptyList(), outcome.problems)
        assertTrue(LayoutPackDocument.None.isEmpty)
    }
}
