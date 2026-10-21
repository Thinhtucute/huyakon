package com.example.huyakon.data.remote

import com.example.huyakon.data.remote.dto.AnalysisDto
import com.example.huyakon.data.remote.dto.AnalyzeRequestDto
import retrofit2.http.Body
import retrofit2.http.POST

/** Retrofit turns this interface into a working HTTP client at runtime. No body needed. */
interface SpaceApi {
    @POST("v1/analyze")
    suspend fun analyze(@Body request: AnalyzeRequestDto): AnalysisDto
}
