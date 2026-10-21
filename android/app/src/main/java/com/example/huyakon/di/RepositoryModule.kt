package com.example.huyakon.di

import com.example.huyakon.data.repository.SpaceAnalysisRepository
import com.example.huyakon.data.translator.SpaceTranslator
import com.example.huyakon.domain.port.AnalysisRepository
import com.example.huyakon.domain.port.Translator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * "When someone asks for the interface, hand them this implementation."
 * @Binds is for interface -> implementation; the implementation needs an @Inject constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAnalysisRepository(impl: SpaceAnalysisRepository): AnalysisRepository

    @Binds
    @Singleton
    abstract fun bindTranslator(impl: SpaceTranslator): Translator
}
