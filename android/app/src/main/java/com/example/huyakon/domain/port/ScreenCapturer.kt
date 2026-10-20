package com.example.huyakon.domain.port

import com.example.huyakon.domain.model.Capture

interface ScreenCapturer {
    suspend fun capture(): Result<Capture>
}
