package com.example.huyakon.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** @Singleton: the Service writes to it and the ViewModel reads it, so both must get the SAME instance. */
@Singleton
class PipelineStateHolder @Inject constructor() {
    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    fun update(newState: PipelineState) {
        _state.value = newState
    }
}
