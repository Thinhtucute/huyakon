package com.example.huyakon.data.remote

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Retrofit's suspend functions throw [HttpException] on non-2xx and the converter throws
 * [SerializationException] on bad JSON. Map both to the same IOException messages the app
 * showed before, so the UI error text doesn't change.
 */
internal suspend fun <T> apiCall(what: String, block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    val body = e.response()?.errorBody()?.string().orEmpty().take(500)
    throw IOException("Backend returned HTTP ${e.code()}: $body", e)
} catch (e: SerializationException) {
    throw IOException("Backend returned an unexpected $what result", e)
}
