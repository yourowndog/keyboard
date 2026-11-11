package dev.patrickgold.florisboard.ime.keyboard.pack

import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stage 06: the validation model.
 *
 * Covers the stage's required test *Duplicate IDs and invalid roles/units*, plus the rest of the
 * validation matrix the stage asks for: unique stable IDs, allowed roles, finite positive units,
 * satisfiable row constraints, and key/spacer structure.
 */
class LayoutPackValidationTest {

    private fun kindsOf(document: LayoutPackDocument): List<LayoutPackProblemKind> =
        LayoutPackValidation.validate(document).map { it.kind }

    // -- The happy path ---------------------------------------------------------------------------

    @Test
    fun `a well-formed pack has no problems`() {
        assertEquals(emptyList(), LayoutPackValidation.validate(LayoutPackFixtures.currentPack()))
    }

    @Test
    fun `a row inherits the pack width when it declares none`() {
        val pack = LayoutPackFixtures.currentPack(
            units = 3f,
            rows = listOf(
                LayoutPackRow(
                    id = "r",
                    role = SemanticRowRole.ALPHA,
                    units = null,
                    keys = List(3) { LayoutPackFixtures.key("q") },
                )
            ),
        )

        assertEquals(emptyList(), LayoutPackValidation.validate(pack))
        assertEquals(3f, pack.rows.single().widthUnits(pack.units))
    }

    // -- Unique stable IDs ------------------------------------------------------------------------

    @Test
    fun `duplicate row ids are rejected`() {
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackFixtures.alphaRow(id = "same"),
                LayoutPackFixtures.alphaRow(id = "same"),
            ),
        )

        val problems = LayoutPackValidation.validate(pack)

        assertEquals(listOf(LayoutPackProblemKind.DUPLICATE_ID), problems.map { it.kind })
        assertEquals("rows[1].id", problems.single().path, "the second occurrence is the problem")
    }

    @Test
    fun `duplicate key ids within a row are rejected`() {
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackRow(
                    id = "r",
                    role = SemanticRowRole.ALPHA,
                    units = 2f,
                    keys = listOf(
                        LayoutPackFixtures.key("q", id = "dup"),
                        LayoutPackFixtures.key("w", id = "dup"),
                    ),
                )
            ),
        )

        assertEquals(listOf(LayoutPackProblemKind.DUPLICATE_ID), kindsOf(pack))
    }

    @Test
    fun `the same key id in two different rows is fine`() {
        // Key ids are row-scoped, which is what lets two rows each have a "left" key.
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackRow(
                    id = "a",
                    role = SemanticRowRole.ALPHA,
                    units = 1f,
                    keys = listOf(LayoutPackFixtures.key("q", id = "left")),
                ),
                LayoutPackRow(
                    id = "b",
                    role = SemanticRowRole.ALPHA,
                    units = 1f,
                    keys = listOf(LayoutPackFixtures.key("w", id = "left")),
                ),
            ),
        )

        assertEquals(emptyList(), LayoutPackValidation.validate(pack))
    }

    @Test
    fun `blank ids are rejected everywhere they appear`() {
        val pack = LayoutPackDocument(
            id = "   ",
            label = "P",
            units = 1f,
            rows = listOf(
                LayoutPackRow(
                    id = "",
                    role = SemanticRowRole.ALPHA,
                    units = 1f,
                    keys = listOf(LayoutPackFixtures.key("q", id = " ")),
                )
            ),
        )

        assertEquals(
            listOf("id", "rows[0].id", "rows[0].keys[0].id"),
            LayoutPackValidation.validate(pack).map { it.path },
        )
    }

    // -- Pack id safety ---------------------------------------------------------------------------

    @Test
    fun `a pack id that could escape the packs directory is rejected`() {
        // The id is a filename. A pack arriving from a document picker must not choose where it is
        // written.
        val unsafe = listOf(
            "../../../theme",
            "a/b",
            "a\\b",
            "..",
            "...",
            "pack name with spaces",
            "pack\u0000name",
            "x".repeat(LayoutPackValidation.MAX_PACK_ID_LENGTH + 1),
        )

        for (id in unsafe) {
            assertTrue(!LayoutPackValidation.isSafePackId(id), "'$id' must be refused")
            assertTrue(
                LayoutPackProblemKind.UNSAFE_ID in kindsOf(LayoutPackFixtures.currentPack(id = id)),
                "'$id' must be reported as unsafe",
            )
        }
    }

    @Test
    fun `the ids packs actually use are accepted`() {
        val safe = listOf(
            "lcars_hacker_en_us",
            "dev.florisboard.layoutpacks.none",
            "my-pack-2",
            "A",
        )

        for (id in safe) {
            assertTrue(LayoutPackValidation.isSafePackId(id), "'$id' must be accepted")
        }
    }

    // -- Units ------------------------------------------------------------------------------------

    @Test
    fun `non-finite widths are rejected`() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val pack = LayoutPackFixtures.currentPack(
                rows = listOf(
                    LayoutPackRow(
                        id = "r",
                        role = SemanticRowRole.ALPHA,
                        units = 1f,
                        keys = listOf(LayoutPackFixtures.key("q", widthUnits = bad)),
                    )
                ),
            )

            assertTrue(
                LayoutPackProblemKind.NON_FINITE_UNITS in kindsOf(pack),
                "$bad must be refused as a key width",
            )
        }
    }

    @Test
    fun `zero, negative and absurd widths are rejected`() {
        for (bad in listOf(0f, -1f, LayoutPackSchema.MAX_UNITS + 1f)) {
            val pack = LayoutPackFixtures.currentPack(
                rows = listOf(
                    LayoutPackRow(
                        id = "r",
                        role = SemanticRowRole.ALPHA,
                        units = 1f,
                        keys = listOf(LayoutPackFixtures.key("q", widthUnits = bad)),
                    )
                ),
            )

            assertTrue(
                LayoutPackProblemKind.UNITS_OUT_OF_RANGE in kindsOf(pack),
                "$bad must be refused as a key width",
            )
        }
    }

    @Test
    fun `a non-finite pack width is rejected`() {
        assertTrue(
            LayoutPackProblemKind.NON_FINITE_UNITS in
                kindsOf(LayoutPackFixtures.currentPack().copy(units = Float.NaN)),
        )
    }

    // -- Satisfiability ---------------------------------------------------------------------------

    @Test
    fun `a row whose keys do not fill it is unsatisfiable`() {
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackRow(
                    id = "r",
                    role = SemanticRowRole.ALPHA,
                    units = 12f,
                    keys = List(11) { LayoutPackFixtures.key("q") },
                )
            ),
        )

        val problem = LayoutPackValidation.validate(pack).single()
        assertEquals(LayoutPackProblemKind.UNSATISFIABLE_ROW, problem.kind)
        assertTrue(
            problem.detail.contains("11") && problem.detail.contains("12"),
            "the message must say what was declared and what was expected: ${problem.detail}",
        )
    }

    @Test
    fun `a row whose keys overflow it is unsatisfiable`() {
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackRow(
                    id = "r",
                    role = SemanticRowRole.ALPHA,
                    units = 2f,
                    keys = List(3) { LayoutPackFixtures.key("q") },
                )
            ),
        )

        assertEquals(listOf(LayoutPackProblemKind.UNSATISFIABLE_ROW), kindsOf(pack))
    }

    @Test
    fun `fractional widths that add up are accepted`() {
        // Float arithmetic must not make an author's valid row fail: 1.1 three times is 3.3000002.
        val pack = LayoutPackFixtures.currentPack(
            units = 3.3f,
            rows = listOf(
                LayoutPackRow(
                    id = "r",
                    role = SemanticRowRole.ALPHA,
                    units = 3.3f,
                    keys = List(3) { LayoutPackFixtures.key("q", widthUnits = 1.1f) },
                )
            ),
        )

        assertEquals(emptyList(), LayoutPackValidation.validate(pack))
    }

    @Test
    fun `a spacer counts toward the row width`() {
        val row = LayoutPackFixtures.rowWithSpacer()

        assertEquals(12f, row.declaredElementUnits)
        assertEquals(
            emptyList(),
            LayoutPackValidation.validate(LayoutPackFixtures.currentPack(rows = listOf(row))),
        )
    }

    // -- Key and spacer structure -----------------------------------------------------------------

    @Test
    fun `a row with no keys is rejected`() {
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(LayoutPackRow(id = "r", role = SemanticRowRole.ALPHA, keys = emptyList())),
        )

        assertEquals(listOf(LayoutPackProblemKind.EMPTY_ROW), kindsOf(pack))
    }

    @Test
    fun `a disabled row must still be well-formed`() {
        // A pack that only validates in one visibility state breaks when a switch is flipped.
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackFixtures.alphaRow(),
                LayoutPackFixtures.primaryActionRow(),
                LayoutPackRow(
                    id = "hidden",
                    role = SemanticRowRole.CODING_UTILITY,
                    units = 12f,
                    enabled = false,
                    keys = listOf(LayoutPackFixtures.key("q", widthUnits = 1f)),
                ),
            ),
        )

        assertEquals(listOf(LayoutPackProblemKind.UNSATISFIABLE_ROW), kindsOf(pack))
    }

    @Test
    fun `a spacer may not also claim to do something`() {
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackRow(
                    id = "r",
                    role = SemanticRowRole.ALPHA,
                    units = 1f,
                    keys = listOf(LayoutPackFixtures.spacer().copy(code = "q")),
                )
            ),
        )

        assertEquals(listOf(LayoutPackProblemKind.MALFORMED_ELEMENT), kindsOf(pack))
    }

    @Test
    fun `a key must declare a code`() {
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackRow(
                    id = "r",
                    role = SemanticRowRole.ALPHA,
                    units = 1f,
                    keys = listOf(LayoutPackFixtures.key("")),
                )
            ),
        )

        val problem = LayoutPackValidation.validate(pack).single()
        assertEquals(LayoutPackProblemKind.MALFORMED_ELEMENT, problem.kind)
        assertTrue(
            problem.detail.contains("spacer"),
            "the message must point at the right construct: ${problem.detail}",
        )
    }

    @Test
    fun `a key whose code resolves to nothing is rejected rather than becoming a hole`() {
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackRow(
                    id = "r",
                    role = SemanticRowRole.ALPHA,
                    units = 1f,
                    keys = listOf(LayoutPackFixtures.key("KEYCODE_NOT_A_THING")),
                )
            ),
        )

        val problem = LayoutPackValidation.validate(pack).single()
        assertEquals(LayoutPackProblemKind.MALFORMED_ELEMENT, problem.kind)
        assertEquals("rows[0].keys[0].code", problem.path)
    }

    @Test
    fun `every code form a pack may use resolves`() {
        val forms = listOf(
            "q" to "a single character",
            "€" to "a non-ASCII character",
            "KEYCODE_SPACE" to "a documented alias",
            "MODE_SYMBOLS" to "a mode alias",
            "space" to "an internal key's own label",
            "SHIFT" to "an internal key label in upper case",
            "97" to "a raw key code",
        )

        for ((code, description) in forms) {
            assertTrue(
                LayoutPackKeyResolver.isResolvable(code),
                "'$code' ($description) must resolve",
            )
        }
    }

    // -- Policy -----------------------------------------------------------------------------------

    @Test
    fun `an out-of-range policy override is rejected`() {
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackFixtures.alphaRow().copy(
                    policy = LayoutPackRowPolicy(
                        heightFactorPercent = 10_000,
                        insetHorizontalPercent = 90,
                    ),
                ),
                LayoutPackFixtures.primaryActionRow(),
            ),
        )

        val problems = LayoutPackValidation.validate(pack)
        assertEquals(
            listOf(
                "rows[0].policy.heightFactorPercent",
                "rows[0].policy.insetHorizontalPercent",
            ),
            problems.map { it.path },
        )
        assertTrue(problems.all { it.kind == LayoutPackProblemKind.POLICY_OUT_OF_RANGE })
    }

    @Test
    fun `an in-range policy override is accepted`() {
        val pack = LayoutPackFixtures.currentPack(
            rows = listOf(
                LayoutPackFixtures.alphaRow().copy(
                    policy = LayoutPackRowPolicy(heightFactorPercent = 150, insetHorizontalPercent = 10),
                ),
                LayoutPackFixtures.primaryActionRow(),
            ),
        )

        assertEquals(emptyList(), LayoutPackValidation.validate(pack))
    }

    // -- Schema version ---------------------------------------------------------------------------

    @Test
    fun `a document claiming a newer schema is rejected by validation too`() {
        // The codec refuses these before deserializing; validating it again means a document built in
        // code cannot bypass the check.
        val pack = LayoutPackFixtures.currentPack()
            .copy(schemaVersion = LayoutPackSchema.CURRENT_VERSION + 1)

        assertTrue(LayoutPackProblemKind.UNSUPPORTED_SCHEMA_VERSION in kindsOf(pack))
    }

    // -- Totality ---------------------------------------------------------------------------------

    @Test
    fun `validation never throws on hostile input`() {
        val hostile = listOf(
            LayoutPackDocument(id = "", label = "", units = Float.NaN, rows = emptyList()),
            LayoutPackDocument(
                id = "p",
                label = "P",
                units = 0f,
                rows = List(3) {
                    LayoutPackRow(
                        id = "",
                        role = SemanticRowRole.PLACEHOLDER,
                        units = Float.NEGATIVE_INFINITY,
                        keys = listOf(LayoutPackFixtures.key("", widthUnits = Float.NaN)),
                    )
                },
            ),
            LayoutPackDocument.None,
        )

        for (document in hostile) {
            // The assertion is that this returns at all.
            LayoutPackValidation.validate(document)
        }
    }
}
