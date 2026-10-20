package com.example.huyakon.data.remote

import com.example.huyakon.data.remote.dto.TranslationRequestLineDto
import com.example.huyakon.data.remote.dto.TranslationResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class TranslationApi(
    private val client: OkHttpClient,
    baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val endpoint = "${baseUrl.trimEnd('/')}/v1/translate"

    suspend fun translate(
        lines: List<TranslationRequestLineDto>,
        glossary: String = "",
    ): TranslationResponseDto {
        val body = buildJsonObject {
            put("script", json.parseToJsonElement(json.encodeToString(lines)))
            put("glossary", glossary)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(endpoint).post(body).build()

        return execute(client.newCall(request)) { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Backend returned HTTP ${response.code}: ${text.take(500)}")
            }
            try {
                json.decodeFromString(TranslationResponseDto.serializer(), text)
            } catch (error: Exception) {
                throw IOException("Backend returned an unexpected translation result", error)
            }
        }
    }

    private suspend fun <T> execute(call: Call, read: (Response) -> T): T =
        withContext(Dispatchers.IO) {
            val handle = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            try {
                call.execute().use(read)
            } finally {
                handle.dispose()
            }
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
