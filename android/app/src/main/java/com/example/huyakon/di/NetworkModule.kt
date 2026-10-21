package com.example.huyakon.di

import com.example.huyakon.data.remote.SpaceApi
import com.example.huyakon.data.remote.SpaceConfig
import com.example.huyakon.data.remote.TranslationApi
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Recipes for objects we don't own (OkHttp, Retrofit, Json) and so can't put @Inject on.
 * Each @Provides function is one recipe; its parameters are resolved by Hilt automatically.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // ZeroGPU cold starts are slow
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Retrofit requires the base URL to end with "/"
        .baseUrl(SpaceConfig.BACKEND_BASE_URL.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideSpaceApi(retrofit: Retrofit): SpaceApi = retrofit.create(SpaceApi::class.java)

    @Provides
    @Singleton
    fun provideTranslationApi(retrofit: Retrofit): TranslationApi =
        retrofit.create(TranslationApi::class.java)
}
