package com.example.huyakon.data.remote

import com.example.huyakon.data.remote.dto.TranslationRequestDto
import com.example.huyakon.data.remote.dto.TranslationResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface TranslationApi {
    @POST("v1/translate")
    suspend fun translate(@Body request: TranslationRequestDto): TranslationResponseDto
}
