package dev.patrickgold.florisboard.ime.nlp

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GemmaClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start(8080) // GemmaClient uses 8080 by default
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testComplete() {
        // Mock successful response from llama.cpp
        val jsonResponse = """
            {
                "content": "Hello world",
                "generation_settings": {},
                "model": "gemma-2b",
                "prompt": "Hello",
                "stopped_eos": true,
                "stopped_limit": false,
                "stopped_word": false,
                "stopping_word": "",
                "timings": {
                    "predicted_ms": 100.0,
                    "predicted_n": 2,
                    "predicted_per_second": 20.0,
                    "prompt_ms": 50.0,
                    "prompt_n": 1,
                    "prompt_per_second": 20.0
                },
                "tokens_cached": 0,
                "tokens_evaluated": 1,
                "tokens_predicted": 2,
                "truncated": false
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(jsonResponse))

        val result = GemmaClient.complete("Hello")
        assertEquals("Hello world", result)
        
        val request = server.takeRequest()
        assertEquals("/completion", request.path)
        assertEquals("POST", request.method)
    }
}
