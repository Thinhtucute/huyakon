package com.example.huyakon.presentation.main

import androidx.lifecycle.ViewModel
import com.example.huyakon.di.AppGraph
import com.example.huyakon.domain.PipelineState
import com.example.huyakon.domain.PipelineStateHolder
import kotlinx.coroutines.flow.StateFlow

// The default argument lets `by viewModels()` work without a factory until Hilt arrives.
class MainViewModel(
    pipeline: PipelineStateHolder = AppGraph.pipelineState,
) : ViewModel() {
    val state: StateFlow<PipelineState> = pipeline.state
}
