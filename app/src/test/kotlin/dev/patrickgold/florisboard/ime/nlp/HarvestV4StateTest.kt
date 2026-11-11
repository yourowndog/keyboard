package dev.patrickgold.florisboard.ime.nlp

import dev.patrickgold.florisboard.ime.keyboard.geometry.LayoutFingerprint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HarvestV4StateTest {
    private fun context(blocked: Boolean = false) = AppContext(
        packageName = "dev.test.editor",
        fieldId = 42,
        inputType = "text",
        inputVariation = "normal",
        flags = "autoCorrect",
        isHarvestBlocked = blocked,
        hint = "Message",
        extrasKeys = listOf("capability"),
    )

    private fun layout() = LayoutFingerprint(
        fp = "abc12345",
        alphaW = 1080,
        alphaH = 420,
        aspect = 2.571f,
        alphaRows = 3,
        modRowsTop = 1,
        modRowsBottom = 2,
        baseKeyH = 140,
        dpi = 420,
        orientation = "portrait",
        oneHanded = null,
        numberRowPresent = true,
    )

    @Test
    fun `backspace and retype remains one struggle slot`() {
        var now = 1_000L
        val rows = mutableListOf<Map<String, Any?>>()
        val state = HarvestV4State(
            emit = { _, _, _, fields -> rows += fields.toMap() },
            clockMs = { now },
            timestamp = { "t$it" },
        )
        state.attach(context(), restarting = false, sessionId = "sess")
        state.updateLayout(layout())
        "failed".forEach { char ->
            state.touch(HarvestTouch(char.toString(), char.toString(), .5f, .5f, 0f, 0f, 10, 20, 40, "TAP"))
            state.typed(char.toString())
            now += 100
        }
        state.commit("failed", HarvestRoute.TYPED_THROUGH, prevWord = "it")
        repeat(6) {
            state.edit("BKSP", left = "failed")
            now += 50
        }
        state.typed("fixed")
        state.commit("fixed", HarvestRoute.TYPED_THROUGH, prevWord = "it")
        state.finish()

        assertEquals(1, rows.size)
        @Suppress("UNCHECKED_CAST")
        val outcome = rows.single().getValue("outcome") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val signals = rows.single().getValue("signals") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val edits = rows.single().getValue("edits") as List<Map<String, Any?>>
        assertEquals("fixed", outcome["final"])
        assertEquals(6, edits.size)
        assertEquals(6, signals["totalBksp"])
        assertEquals(2, signals["attempts"])
        assertEquals(true, signals["struggle"])
    }

    @Test
    fun `bar pick records rank and reachability`() {
        val rows = mutableListOf<Map<String, Any?>>()
        val state = HarvestV4State(
            emit = { _, _, _, fields -> rows += fields.toMap() },
            clockMs = { 5_000L },
            timestamp = { "t$it" },
        )
        state.attach(context(), restarting = false, sessionId = "sess")
        state.typed("recieve")
        state.offer("recieve", listOf("receive" to 0.9, "recipe" to 0.2), shown = 2)
        state.commit("receive", HarvestRoute.BAR_PICK, barIndex = 0, autoFrom = "recieve")
        state.finish()

        @Suppress("UNCHECKED_CAST")
        val outcome = rows.single().getValue("outcome") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val shadow = rows.single().getValue("shadow") as Map<String, Any?>
        assertEquals(HarvestRoute.BAR_PICK, outcome["route"])
        assertEquals(0, outcome["barIndex"])
        assertEquals(true, outcome["reachable"])
        assertEquals(0, outcome["reachableAt"])
        assertEquals(true, shadow["reachable"])
        assertEquals(0, shadow["reachableAt"])
    }

    @Test
    fun `autocorrect revert closes before the next word`() {
        val rows = mutableListOf<Map<String, Any?>>()
        val state = HarvestV4State(
            emit = { _, _, _, fields -> rows += fields.toMap() },
            clockMs = { 7_000L },
            timestamp = { "t$it" },
        )
        state.attach(context(), restarting = false, sessionId = "sess")
        state.typed("teh")
        state.commit("the", HarvestRoute.AUTO_APPLIED, autoFrom = "teh")
        state.edit("BKSP")
        state.revert("teh", "the")
        state.typed("next")
        state.commit("next", HarvestRoute.TYPED_THROUGH)
        state.finish()

        assertEquals(2, rows.size)
        @Suppress("UNCHECKED_CAST")
        val reverted = rows.first().getValue("outcome") as Map<String, Any?>
        assertEquals("teh", reverted["final"])
        assertEquals(true, reverted["reverted"])
        assertEquals("the", reverted["revertedFrom"])
        @Suppress("UNCHECKED_CAST")
        val next = rows.last().getValue("outcome") as Map<String, Any?>
        assertEquals("next", next["final"])
    }

    @Test
    fun `accepting a recently rejected target records capitulation`() {
        var now = 8_000L
        val rows = mutableListOf<Map<String, Any?>>()
        val state = HarvestV4State(
            emit = { _, _, _, fields -> rows += fields.toMap() },
            clockMs = { now },
            timestamp = { "t$it" },
        )
        state.attach(context(), restarting = false, sessionId = "sess")
        state.typed("fso")
        state.commit("fos", HarvestRoute.AUTO_APPLIED, autoFrom = "fso")
        state.edit("BKSP")
        state.revert("fso", "fos")
        now += 5_000L
        state.typed("fso")
        state.commit("fos", HarvestRoute.BAR_PICK, autoFrom = "fso", barIndex = 1)
        state.finish()

        assertEquals(2, rows.size)
        @Suppress("UNCHECKED_CAST")
        val signals = rows.last().getValue("signals") as Map<String, Any?>
        assertEquals(true, signals["capitulation"])
    }

    @Test
    fun `sensitive attachment emits nothing`() {
        val rows = mutableListOf<Map<String, Any?>>()
        val state = HarvestV4State(
            emit = { _, _, _, fields -> rows += fields.toMap() },
            clockMs = { 9_000L },
            timestamp = { "t$it" },
        )
        state.attach(context(blocked = true), restarting = false, sessionId = "sess")
        state.touch(HarvestTouch("x", "x", .5f, .5f, 0f, 0f, 1, 2, 30, "TAP"))
        state.typed("x")
        state.offer("x", listOf("x" to 1.0), shown = 1)
        state.commit("x", HarvestRoute.TYPED_THROUGH)
        state.finish()
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `layout and editor metadata are stamped into every emitted slot`() {
        val rows = mutableListOf<Map<String, Any?>>()
        val state = HarvestV4State(
            emit = { _, _, _, fields -> rows += fields.toMap() },
            clockMs = { 11_000L },
            timestamp = { "t$it" },
        )
        state.attach(context(), restarting = false, sessionId = "sess")
        state.updateLayout(layout())
        state.typed("hello")
        state.commit("hello", HarvestRoute.TYPED_THROUGH)
        state.finish()

        @Suppress("UNCHECKED_CAST")
        val ctx = rows.single().getValue("ctx") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val stampedLayout = rows.single().getValue("layout") as Map<String, Any?>
        assertEquals("Message", ctx["hint"])
        assertEquals(listOf("capability"), ctx["extrasKeys"])
        assertEquals(1L, ctx["inputSession"])
        assertEquals("abc12345", stampedLayout["fp"])
        assertFalse((rows.single()["slot"] as String).isBlank())
    }

    @Test
    fun `voice slots carry opaque segment metadata without invented confidence`() {
        val rows = mutableListOf<Map<String, Any?>>()
        val state = HarvestV4State(
            emit = { _, _, _, fields -> rows += fields.toMap() },
            clockMs = { 13_000L },
            timestamp = { "t$it" },
        )
        state.attach(context(), restarting = false, sessionId = "sess")
        state.beginVoice(audioRef = "segment-opaque-id", transcriptKind = "APPEND")
        state.externalText("hello world", HarvestRoute.VOICE)
        state.endVoice()
        state.typed("typed")
        state.commit("typed", HarvestRoute.TYPED_THROUGH)
        state.finish()

        assertEquals(3, rows.size)
        @Suppress("UNCHECKED_CAST")
        val firstVoice = rows[0].getValue("voice") as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val secondVoice = rows[1].getValue("voice") as Map<String, Any?>
        assertEquals("segment-opaque-id", firstVoice["audioRef"])
        assertEquals("APPEND", firstVoice["transcriptKind"])
        assertEquals(0, firstVoice["segIndex"])
        assertNull(firstVoice["asrConf"])
        assertEquals(1, secondVoice["segIndex"])
        assertNull(rows[2]["voice"])
    }
}
