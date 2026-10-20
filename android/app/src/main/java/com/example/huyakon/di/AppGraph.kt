package com.example.huyakon.di

import com.example.huyakon.data.remote.SpaceApi
import com.example.huyakon.data.remote.SpaceConfig
import com.example.huyakon.data.remote.TranslationApi
import com.example.huyakon.data.repository.SpaceAnalysisRepository
import com.example.huyakon.data.translator.SpaceTranslator
import com.example.huyakon.domain.PipelineStateHolder
import com.example.huyakon.domain.port.AnalysisRepository
import com.example.huyakon.domain.port.ScreenCapturer
import com.example.huyakon.domain.port.Translator
import com.example.huyakon.domain.usecase.TranslateScreenUseCase
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Temporary hand-rolled DI. Replace with Hilt modules next (the interfaces are already in place). */
object AppGraph {
    val pipelineState: PipelineStateHolder by lazy { PipelineStateHolder() }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // ZeroGPU cold starts are slow
            .build()
    }

    private val spaceApi: SpaceApi by lazy {
        SpaceApi(httpClient, SpaceConfig.BACKEND_BASE_URL)
    }
    private val translationApi: TranslationApi by lazy {
        TranslationApi(httpClient, SpaceConfig.BACKEND_BASE_URL)
    }

    val analysisRepository: AnalysisRepository by lazy { SpaceAnalysisRepository(spaceApi) }

    val translator: Translator by lazy { SpaceTranslator(translationApi) }

    /** The capturer belongs to the service (it owns the MediaProjection), so it is passed in. */
    fun translateScreen(capturer: ScreenCapturer) =
        TranslateScreenUseCase(capturer, analysisRepository, translator)
}