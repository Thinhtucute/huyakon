package com.example.huyakon.domain
 
import com.example.huyakon.domain.model.Capture
import com.example.huyakon.domain.model.ScreenAnalysis
import com.example.huyakon.domain.model.TranslatedLine
 
sealed interface PipelineState {
    data object Idle : PipelineState
    data object Capturing : PipelineState
    data class Detecting(val capture: Capture) : PipelineState
    data class Translating(
        val capture: Capture,
        val analysis: ScreenAnalysis,
    ) : PipelineState
    data class Showing(
        val capture: Capture,
        val analysis: ScreenAnalysis,
        val translated: List<TranslatedLine>,
    ) : PipelineState
    data class Error(val message: String) : PipelineState
}