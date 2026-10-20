package com.example.huyakon.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SpaceApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SpaceApi

    private val resultJson = """
        {"image": {"width": 100, "height": 200},
         "texts": [{"id": 0, "bbox": [1, 2, 3, 4]}],
         "script": [{"order": 0, "text_id": 0, "text": "こんにちは", "role": "dialogue"}]}
    """.trimIndent().replace("\n", "")

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = SpaceApi(OkHttpClient(), server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun postsTheImageToTheBackend() {
        server.enqueue(MockResponse().setBody(resultJson))

        val dto = runBlocking { api.analyze("QkFTRTY0") }

        assertEquals(100, dto.image.width)
        assertEquals("こんにちは", dto.script.single().text)

        val post = server.takeRequest()
        assertEquals("/v1/analyze", post.path)
        assertTrue(post.body.readUtf8().contains("QkFTRTY0"))
    }

    @Test
    fun acceptsTheBackendObjectPayload() {
        server.enqueue(MockResponse().setBody(resultJson))

        val dto = runBlocking { api.analyze("QkFTRTY0") }

        assertEquals(200, dto.image.height)
        assertEquals("こんにちは", dto.script.single().text)
    }

    @Test
    fun aBackendErrorBecomesAnIOException() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("Space unavailable"))

        assertThrows(IOException::class.java) { runBlocking { api.analyze("QQ==") } }
    }

    @Test
    fun anHttpErrorBecomesAnIOException() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        assertThrows(IOException::class.java) { runBlocking { api.analyze("QQ==") } }
    }
}