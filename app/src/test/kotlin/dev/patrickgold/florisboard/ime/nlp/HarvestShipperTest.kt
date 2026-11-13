package dev.patrickgold.florisboard.ime.nlp

import kotlin.test.Test
import kotlin.test.assertEquals

class HarvestShipperTest {
    @Test
    fun `stream id matches the Titan receiver`() {
        // Same bytes as keylog_store.stream_id_for_head in tools/keylog_archive:
        // python3 -c 'import hashlib;print(hashlib.sha256(b"{\"v\":3,\"id\":1,\"type\":\"X\"}\n").hexdigest()[:16])'
        val head = "{\"v\":3,\"id\":1,\"type\":\"X\"}\n".toByteArray(Charsets.UTF_8)
        assertEquals("3129f1053a38b23b", HarvestShipper.streamIdFor(head))
    }

    @Test
    fun `chunks are cut back to the last complete line`() {
        val bytes = "{\"a\":1}\n{\"b\":2}\n{\"c\":".toByteArray(Charsets.UTF_8)
        assertEquals(16, HarvestShipper.completeLinesLength(bytes))
        assertEquals(0, HarvestShipper.completeLinesLength("{\"partial\":".toByteArray()))
        assertEquals(8, HarvestShipper.completeLinesLength("{\"a\":1}\n".toByteArray()))
    }
}
