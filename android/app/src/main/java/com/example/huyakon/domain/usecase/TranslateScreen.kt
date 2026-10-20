package com.example.huyakon.domain.usecase

import com.example.huyakon.domain.PipelineState
import com.example.huyakon.domain.port.AnalysisRepository
import com.example.huyakon.domain.port.ScreenCapturer
import com.example.huyakon.domain.port.Translator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TranslateScreenUseCase(
    private val capturer: ScreenCapturer,
    private val analysis: AnalysisRepository,
    private val translator: Translator,
) {
    operator fun invoke(): Flow<PipelineState> = flow {
        emit(PipelineState.Capturing)
        val capture = capturer.capture().getOrElse {
            emit(PipelineState.Error("Capture failed: ${it.message ?: it::class.simpleName}"))
            return@flow
        }

        emit(PipelineState.Detecting(capture))
        val result = analysis.analyze(capture).getOrElse {
            emit(PipelineState.Error("Analyzer failed: ${it.message ?: it::class.simpleName}"))
            return@flow
        }

        emit(PipelineState.Translating(capture, result))
        val translated = translator.translate(result.lines).getOrElse {
            emit(PipelineState.Error("Translator failed: ${it.message ?: it::class.simpleName}"))
            return@flow
        }

        emit(PipelineState.Showing(capture, result, translated))
    }
}