package com.example.huyakon.data.remote

import com.example.huyakon.data.remote.dto.AnalysisDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class SpaceApi(
    private val client: OkHttpClient,
    baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val endpoint = "${baseUrl.trimEnd('/')}/v1/analyze"

    suspend fun analyze(
        jpegBase64: String,
        lang: String = "ja",
        proximitySpeakers: Boolean = true,
    ): AnalysisDto {
        val body = buildJsonObject {
            put("image_base64", jpegBase64)
            put("lang", lang)
            put("proximity_speakers", proximitySpeakers)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(endpoint).post(body).build()

        return execute(client.newCall(request)) { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Backend returned HTTP ${response.code}: ${text.take(500)}")
            }
            try {
                val result = json.decodeFromString(AnalysisDto.serializer(), text)
                result.error?.let { error ->
                    throw IOException(
                        "Analyzer error: ${error.message ?: error.type ?: "unknown error"}"
                    )
                }
                result
            } catch (error: Exception) {
                if (error is IOException && error.message?.startsWith("Analyzer error:") == true) {
                    throw error
                }
                throw IOException("Backend returned an unexpected analysis result", error)
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
