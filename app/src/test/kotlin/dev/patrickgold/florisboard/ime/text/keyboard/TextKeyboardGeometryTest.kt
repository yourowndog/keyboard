package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.LayoutPackKeyData
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryFixtures
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryItemKind
import dev.patrickgold.florisboard.ime.keyboard.geometry.GeometryOrientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TextKeyboardGeometryTest {
    private val portrait = TextKeyboardGeometryConfig(
        rowBaseHeight = 60.0,
        bottomRowHeightFactor = 0.75,
        alphaRowHeightFactor = 1.0,
        alphaKeyWidthFactor = 1.0,
        modKeyWidthFactor = 1.0,
        alphaSpacingHorizontal = 2.0,
        alphaSpacingVertical = 2.0,
        modSpacingHorizontal = 3.0,
        modSpacingVertical = 2.0,
        modRowUpperGap = 8.0,
        modRowInnerGap = 4.0,
        modRowLowerGap = 8.0,
        orientation = GeometryOrientation.PORTRAIT,
    )

    private fun solve(
        keyboard: TextKeyboard,
        config: TextKeyboardGeometryConfig = portrait,
        width: Double = 1080.0,
        fitToHeight: Double? = null,
    ): TextKeyboardGeometry {
        return when (
            val solution = TextKeyboardGeometryResolver.solve(
                keyboard = keyboard,
                availableWidth = width,
                config = config,
                fitToHeight = fitToHeight,
            )
        ) {
            is TextKeyboardGeometrySolution.Solved -> solution.geometry
            is TextKeyboardGeometrySolution.Unsatisfiable -> {
                throw AssertionError("expected solvable geometry: ${solution.reasons}")
            }
        }
    }

    @Test
    fun `default Coding conserves frame and structural rows never overlap`() {
        val geometry = solve(GeometryFixtures.defaultCoding())

        assertEquals(350, geometry.structural.frame.height)
        val allocatedHeight = geometry.structural.rows.sumOf { it.bounds.height } +
            geometry.structural.gaps.sumOf { it.bounds.height }
        assertEquals(geometry.structural.content.height, allocatedHeight)
        geometry.structural.rows.zipWithNext().forEach { (above, below) ->
            assertTrue(above.bounds.bottom <= below.bounds.top)
            assertFalse(above.bounds.overlaps(below.bounds))
        }
        geometry.structural.rows.forEach { row ->
            row.items.zipWithNext().forEach { (left, right) ->
                assertEquals(left.bounds.right, right.bounds.left)
                assertFalse(left.bounds.overlaps(right.bounds))
            }
        }
    }

    @Test
    fun `semantic Coding boundaries own all three gaps`() {
        val geometry = solve(GeometryFixtures.defaultCoding()).structural

        assertEquals(
            listOf(240 to 248, 293 to 297, 342 to 350),
            geometry.gaps.map { it.bounds.top to it.bounds.bottom },
        )
        assertEquals(
            listOf(0 to 60, 60 to 120, 120 to 180, 180 to 240, 248 to 293, 297 to 342),
            geometry.rows.map { it.bounds.top to it.bounds.bottom },
        )
    }

    @Test
    fun `hidden Coding utilities and specialized modes have no positional gaps`() {
        val hidden = solve(GeometryFixtures.codingUtilitiesHidden()).structural
        val numeric = solve(GeometryFixtures.numeric()).structural
        val symbols = solve(GeometryFixtures.wideSymbols()).structural

        assertEquals(240, hidden.frame.height)
        assertTrue(hidden.gaps.isEmpty())
        assertEquals(240, numeric.frame.height)
        assertTrue(numeric.gaps.isEmpty())
        assertEquals(240, symbols.frame.height)
        assertTrue(symbols.gaps.isEmpty())
    }

    @Test
    fun `fit frame uses one solved result for specialized row placement`() {
        val geometry = solve(
            keyboard = GeometryFixtures.wideSymbols(),
            fitToHeight = 350.0,
        ).structural

        assertEquals(350, geometry.frame.height)
        assertEquals(geometry.frame.bottom, geometry.rows.last().bounds.bottom)
        assertEquals(350, geometry.rows.sumOf { it.bounds.height })
    }

    @Test
    fun `touch and visual bounds treat inner and bottom gaps differently`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val upperUtilityKey = geometry.keys[4].first()
        val finalUtilityKey = geometry.keys[5].first()

        assertEquals(293f, upperUtilityKey.touchBounds.bottom)
        assertEquals(297f, finalUtilityKey.touchBounds.top)
        assertEquals(350f, finalUtilityKey.touchBounds.bottom)
        assertTrue(finalUtilityKey.visibleBounds.bottom < geometry.structural.frame.bottom)
        assertEquals(342, finalUtilityKey.structuralBounds.bottom)
    }

    @Test
    fun `layout pack three-row classification intentionally solves to 180 pixels`() {
        val keyboard = GeometryFixtures.layoutPackWithSpacersAndUnits()
        val geometry = solve(keyboard)

        assertEquals(3, keyboard.semanticRows.size)
        assertTrue(keyboard.semanticRows.all { it.role == SemanticRowRole.ALPHA })
        assertEquals(3, geometry.structural.rows.size)
        assertEquals(180, geometry.structural.frame.height)
    }

    @Test
    fun `explicit layout pack spacer receives allocation but no touch or visual bounds`() {
        val spacer = TextKey(
            LayoutPackKeyData(
                delegate = TextKeyData.UNSPECIFIED,
                widthUnits = 2f,
                isSpacer = true,
            ),
        ).also {
            it.compute(DefaultComputingEvaluator)
            it.flayWidthFactor = 2f
        }
        val keyboard = GeometryFixtures.keyboard(
            rows = listOf(
                arrayOf(
                    GeometryFixtures.key('a'.code, isAlpha = true),
                    spacer,
                    GeometryFixtures.key('b'.code, isAlpha = true),
                ),
            ),
            roles = listOf(SemanticRowRole.ALPHA),
        )

        val geometry = solve(keyboard)
        val solvedSpacer = geometry.structural.rows.single().items[1]
        val derivedSpacer = geometry.keys.single()[1]

        assertEquals(GeometryItemKind.SPACER, solvedSpacer.kind)
        assertTrue(solvedSpacer.bounds.width > 0)
        assertEquals(GeometryFloatRect.Empty, derivedSpacer.touchBounds)
        assertEquals(GeometryFloatRect.Empty, derivedSpacer.visibleBounds)
    }

    @Test
    fun `popup geometry anchors center and clamps portrait screen edges`() {
        val geometry = solve(GeometryFixtures.defaultCoding())
        val row = geometry.keys.first()
        val left = geometry.popupBoundsFor(row.first().visibleBounds.toFlorisRect())
        val centerKey = row[row.size / 2].visibleBounds
        val center = geometry.popupBoundsFor(centerKey.toFlorisRect())
        val right = geometry.popupBoundsFor(row.last().visibleBounds.toFlorisRect())

        assertEquals(0f, left.left)
        assertEquals(1080f, right.right)
        assertEquals(
            centerKey.left + centerKey.width / 2f,
            center.left + center.width / 2f,
            absoluteTolerance = 0.001f,
        )
        assertEquals(
            geometry.popupReferenceBounds.height * 2.5f,
            center.height,
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun `landscape popup and geometry use landscape policy`() {
        val landscapeConfig = portrait.copy(orientation = GeometryOrientation.LANDSCAPE)
        val geometry = solve(
            keyboard = GeometryFixtures.defaultCoding(),
            config = landscapeConfig,
            width = 2160.0,
        )
        val popup = geometry.popupBoundsFor(geometry.keys.first()[4].visibleBounds.toFlorisRect())

        assertEquals(2160, geometry.structural.frame.width)
        assertEquals(
            geometry.popupReferenceBounds.height * 3.0f,
            popup.height,
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun `runtime geometry cache key changes only for declared geometry inputs`() {
        val base = TextKeyboardGeometryCacheKey(
            evaluatorVersion = 10,
            frameSourceVersion = -1,
            availableWidthPx = 1080,
            config = portrait,
        )

        assertNotEquals(base, base.copy(evaluatorVersion = 11))
        assertNotEquals(base, base.copy(frameSourceVersion = 9))
        assertNotEquals(base, base.copy(availableWidthPx = 2160))
        assertNotEquals(base, base.copy(config = portrait.copy(modRowInnerGap = 7.0)))
        assertNotEquals(base, base.copy(config = portrait.copy(orientation = GeometryOrientation.LANDSCAPE)))
        assertNotEquals(base, base.copy(solverRevision = TEXT_KEYBOARD_GEOMETRY_REVISION + 1))
        assertEquals(base, base.copy())
    }

    @Test
    fun `width preferences above containment limit are validated before solving`() {
        val input = TextKeyboardGeometryResolver.solverInput(
            keyboard = GeometryFixtures.defaultCoding(),
            availableWidth = 1080.0,
            config = portrait.copy(alphaKeyWidthFactor = 1.4, modKeyWidthFactor = 1.4),
        )

        assertEquals(1.0, input.overrides.itemWidthScale(SemanticRowRole.ALPHA))
        assertEquals(1.0, input.overrides.itemWidthScale(SemanticRowRole.CODING_UTILITY))
    }
}
