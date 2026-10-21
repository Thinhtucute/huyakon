package com.example.huyakon.data.remote

import com.example.huyakon.data.remote.dto.TranslationRequestDto
import com.example.huyakon.data.remote.dto.TranslationRequestLineDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TranslationApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: TranslationApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(TranslationApi::class.java)
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
                TranslationRequestDto(
                    script = listOf(
                        TranslationRequestLineDto(
                            order = 0,
                            textId = 3,
                            text = "こんにちは",
                            role = "dialogue",
                            maybeSfx = false,
                        )
                    ),
                    glossary = "",
                )
            )
        }

        assertEquals("Hello.", response.flat.single().translation)
        val request = server.takeRequest()
        assertEquals("/v1/translate", request.path)
        assertTrue(request.body.readUtf8().contains("こんにちは"))
    }
}
