package com.example.huyakon.domain.usecase

import com.example.huyakon.domain.PipelineState
import com.example.huyakon.domain.port.AnalysisRepository
import com.example.huyakon.domain.port.ScreenCapturer
import com.example.huyakon.domain.port.Translator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Hilt supplies the two long-lived dependencies. The capturer is NOT injected: it owns a
 * MediaProjection that only exists after the user grants permission, so it is passed per call.
 */
class TranslateScreenUseCase @Inject constructor(
    private val analysis: AnalysisRepository,
    private val translator: Translator,
) {
    operator fun invoke(capturer: ScreenCapturer): Flow<PipelineState> = flow {
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
