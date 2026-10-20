package com.example.huyakon.domain.port

import com.example.huyakon.domain.model.Capture
import com.example.huyakon.domain.model.ScreenAnalysis
 
interface AnalysisRepository {
    suspend fun analyze(capture: Capture): Result<ScreenAnalysis>
}
 