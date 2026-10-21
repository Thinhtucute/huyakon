package com.example.huyakon.presentation.main

import androidx.lifecycle.ViewModel
import com.example.huyakon.domain.PipelineState
import com.example.huyakon.domain.PipelineStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    pipeline: PipelineStateHolder,
) : ViewModel() {
    val state: StateFlow<PipelineState> = pipeline.state
}
