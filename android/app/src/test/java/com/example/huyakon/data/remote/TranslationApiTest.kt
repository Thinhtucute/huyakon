package com.example.huyakon.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslationApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: TranslationApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = TranslationApi(OkHttpClient(), server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun postsScriptAndReadsFlatTranslations() {
        server.enqueue(
            MockResponse().setBody(
                """{"flat":[{"text_id":3,"text":"こんにちは","translation":"Hello."}]}"""
            )
        )

        val response = runBlocking {
            api.translate(
                lines = listOf(
                    com.example.huyakon.data.remote.dto.TranslationRequestLineDto(
                        order = 0,
                        textId = 3,
                        text = "こんにちは",
                        role = "dialogue",
                        maybeSfx = false,
                    )
                )
            )
        }

        assertEquals("Hello.", response.flat.single().translation)
        val request = server.takeRequest()
        assertEquals("/v1/translate", request.path)
        assertTrue(request.body.readUtf8().contains("こんにちは"))
    }
}
