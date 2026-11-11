package dev.patrickgold.florisboard.ime.keyboard.geometry

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.SemanticRowRole
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboard
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpatialCoordinateFrameTest {
    private val width = 1080.0
    private val frameHeight = 900.0

    private data class Configuration(
        val keyboard: TextKeyboard,
        val preferences: GeometryPreferences,
        val frame: SpatialCoordinateFrame,
        val fingerprint: LayoutFingerprint,
    )

    private fun qwertyRows(): List<Array<TextKey>> = listOf(
        "qwertyuiop".map { GeometryFixtures.key(it.code, isAlpha = true) }.toTypedArray(),
        "asdfghjkl".map { GeometryFixtures.key(it.code, isAlpha = true) }.toTypedArray(),
        "zxcvbnm".map { GeometryFixtures.key(it.code, isAlpha = true) }.toTypedArray(),
    )

    private fun configuration(
        alphaHeightPercent: Int,
        numberRow: Boolean,
        utilityRows: Int,
    ): Configuration {
        val rows = buildList {
            if (numberRow) {
                add(Array(10) { index -> GeometryFixtures.key('0'.code + index, isAlpha = false) })
            }
            addAll(qwertyRows())
            add(GeometryFixtures.primaryActionRow())
            repeat(utilityRows) { add(GeometryFixtures.utilityRow()) }
        }
        val roles = buildList {
            if (numberRow) add(SemanticRowRole.NUMBER_ROW)
            repeat(3) { add(SemanticRowRole.ALPHA) }
            add(SemanticRowRole.PRIMARY_ACTION)
            repeat(utilityRows) { add(SemanticRowRole.CODING_UTILITY) }
        }
        val keyboard = GeometryFixtures.keyboard(rows, roles, mode = KeyboardMode.CHARACTERS)
        val preferences = GeometryPreferences(
            rowBaseHeightPx = 160.0,
            alphaRowHeightPercent = alphaHeightPercent,
            utilityRowHeightPercent = 75,
            numberRowHeightPercent = 75,
            keySpacingHorizontalPx = 2.0,
            keySpacingVerticalPx = 2.0,
        )
        val result = TextKeyboardGeometryBridge.solve(
            keyboard = keyboard,
            prefs = preferences,
            availableWidth = width,
            framePolicy = FramePolicy.FitToHeight(frameHeight),
            orientation = GeometryOrientation.PORTRAIT,
        )
        assertTrue(result is TextKeyboardGeometryBridge.Result.Solved, "expected clean solve: $result")
        TextKeyboardGeometryBridge.applyTo(
            keyboard = keyboard,
            geometry = result.geometry,
            extendTouchBoundariesDownwards = true,
        )
        val frame = assertNotNull(SpatialCoordinateFrame.from(keyboard.keys().asSequence().asIterable()))
        val fingerprint = LayoutFingerprint.create(
            keyboard = keyboard,
            preferences = preferences,
            frame = frame,
            densityDpi = 420,
            orientation = GeometryOrientation.PORTRAIT,
        )
        return Configuration(keyboard, preferences, frame, fingerprint)
    }

    @Test
    fun `alpha coordinates stay stable while layout fingerprints change`() {
        val mediumCollapsed = configuration(alphaHeightPercent = 100, numberRow = false, utilityRows = 0)
        val tallWithNumber = configuration(alphaHeightPercent = 150, numberRow = true, utilityRows = 0)
        val mediumExpanded = configuration(alphaHeightPercent = 100, numberRow = false, utilityRows = 3)

        val points = listOf(mediumCollapsed, tallWithNumber, mediumExpanded).map {
            assertNotNull(it.frame.normalizedCenter('g'))
        }
        val baseline = points.first()
        for (point in points.drop(1)) {
            assertTrue(abs(point.x - baseline.x) <= 0.02f, "x moved: $baseline -> $point")
            assertTrue(abs(point.y - baseline.y) <= 0.02f, "y moved: $baseline -> $point")
        }

        val fingerprints = setOf(
            mediumCollapsed.fingerprint.fp,
            tallWithNumber.fingerprint.fp,
            mediumExpanded.fingerprint.fp,
        )
        assertEquals(3, fingerprints.size)
        assertTrue(tallWithNumber.fingerprint.numberRowPresent)
        assertEquals(3, mediumExpanded.fingerprint.modRowsBottom)
    }

    @Test
    fun `touch resolution returns alpha and key-local coordinates`() {
        val configuration = configuration(alphaHeightPercent = 100, numberRow = false, utilityRows = 0)
        val key = configuration.keyboard.keys().asSequence().first {
            (it.data as? dev.patrickgold.florisboard.ime.keyboard.KeyData)?.code == 'g'.code
        }
        val touch = assertNotNull(
            configuration.frame.resolveTouch(
                touchX = key.visibleBounds.center.x,
                touchY = key.visibleBounds.center.y,
                resolveKey = configuration.keyboard::getKeyForPos,
            ),
        )
        val normalizedCenter = assertNotNull(configuration.frame.normalizedCenter('g'))

        assertSame(key, touch.resolvedKey)
        assertEquals(0f, touch.dx, absoluteTolerance = 0.0001f)
        assertEquals(0f, touch.dy, absoluteTolerance = 0.0001f)
        assertEquals(normalizedCenter.x, touch.xn, absoluteTolerance = 0.0001f)
        assertEquals(normalizedCenter.y, touch.yn, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `isotropic projection gives equal pixel distances equal coordinate distances`() {
        val frame = configuration(alphaHeightPercent = 150, numberRow = true, utilityRows = 0).frame
        val center = assertNotNull(frame.normalizedCenter('g'))
        val rawX = frame.boardLeft + center.x * frame.alphaW
        val rawY = frame.boardTop + center.y * frame.alphaH
        val origin = frame.toIsotropic(rawX, rawY)
        val xShift = frame.toIsotropic(rawX + 20f, rawY)
        val yShift = frame.toIsotropic(rawX, rawY + 20f)

        assertEquals(xShift.x - origin.x, yShift.y - origin.y, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `sampling threshold uses both representative key dimensions`() {
        val frame = configuration(alphaHeightPercent = 150, numberRow = false, utilityRows = 0).frame
        val characteristicLength = sqrt(frame.representativeKeyWidth * frame.representativeKeyHeight)
        val expected = characteristicLength * 0.25f

        assertEquals(expected * expected, frame.samplingDistanceThresholdSquared(), absoluteTolerance = 0.001f)
    }

    @Test
    fun `fingerprint is deterministic and includes one-handed geometry`() {
        val configuration = configuration(alphaHeightPercent = 100, numberRow = false, utilityRows = 0)
        val baseline = configuration.fingerprint
        val repeated = LayoutFingerprint.create(
            keyboard = configuration.keyboard,
            preferences = configuration.preferences,
            frame = configuration.frame,
            densityDpi = 420,
            orientation = GeometryOrientation.PORTRAIT,
        )
        val oneHanded = LayoutFingerprint.create(
            keyboard = configuration.keyboard,
            preferences = configuration.preferences,
            frame = configuration.frame,
            densityDpi = 420,
            orientation = GeometryOrientation.PORTRAIT,
            oneHanded = OneHandedGeometry(side = "end", scaleFactor = 0.8f, offsetPx = 216f),
        )

        assertEquals(baseline.fp, repeated.fp)
        assertNotEquals(baseline.fp, oneHanded.fp)
        assertEquals("end", oneHanded.oneHanded)
    }
}
