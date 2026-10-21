package com.example.huyakon.domain.usecase

import com.example.huyakon.domain.PipelineState
import com.example.huyakon.domain.model.Box
import com.example.huyakon.domain.model.Capture
import com.example.huyakon.domain.model.ScreenAnalysis
import com.example.huyakon.domain.model.TextLine
import com.example.huyakon.domain.model.TextRole
import com.example.huyakon.domain.model.TranslatedLine
import com.example.huyakon.domain.port.AnalysisRepository
import com.example.huyakon.domain.port.ScreenCapturer
import com.example.huyakon.domain.port.Translator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateScreenUseCaseTest {
    private val capture = Capture("/tmp/c.jpg", byteCount = 10, width = 100, height = 200)
    private val line = TextLine(1, "こんにちは", Box(0f, 0f, 10f, 10f), TextRole.Dialogue, false, null)
    private val analysis = ScreenAnalysis(100, 200, listOf(line))

    private class FakeCapturer(private val result: Result<Capture>) : ScreenCapturer {
        override suspend fun capture() = result
    }

    private class FakeAnalysis(private val result: Result<ScreenAnalysis>) : AnalysisRepository {
        override suspend fun analyze(capture: Capture) = result
    }

    private class FakeTranslator : Translator {
        var called = false
        override suspend fun translate(lines: List<TextLine>): Result<List<TranslatedLine>> {
            called = true
            return Result.success(lines.map { TranslatedLine(it, "hello") })
        }
    }

    @Test
    fun emitsEveryStageInOrder() = runTest {
        val useCase = TranslateScreenUseCase(
            FakeAnalysis(Result.success(analysis)),
            FakeTranslator(),
        )

        val states = useCase(FakeCapturer(Result.success(capture))).toList()

        assertEquals(
            listOf(
                PipelineState.Capturing,
                PipelineState.Detecting(capture),
                PipelineState.Translating(capture, analysis),
                PipelineState.Showing(capture, analysis, listOf(TranslatedLine(line, "hello"))),
            ),
            states,
        )
    }

    @Test
    fun analysisFailureEmitsErrorAndSkipsTheTranslator() = runTest {
        val translator = FakeTranslator()
        val useCase = TranslateScreenUseCase(
            FakeAnalysis(Result.failure(IllegalStateException("space down"))),
            translator,
        )

        val states = useCase(FakeCapturer(Result.success(capture))).toList()

        assertEquals(
            listOf(
                PipelineState.Capturing,
                PipelineState.Detecting(capture),
                PipelineState.Error("Analyzer failed: space down"),
            ),
            states,
        )
        assertFalse(translator.called)
    }

    @Test
    fun captureFailureStopsImmediately() = runTest {
        val useCase = TranslateScreenUseCase(
            FakeAnalysis(Result.success(analysis)),
            FakeTranslator(),
        )

        val states = useCase(FakeCapturer(Result.failure(IllegalStateException("no frame")))).toList()

        assertEquals(
            listOf(PipelineState.Capturing, PipelineState.Error("Capture failed: no frame")),
            states
        )
        assertTrue(states.last() is PipelineState.Error)
    }
}