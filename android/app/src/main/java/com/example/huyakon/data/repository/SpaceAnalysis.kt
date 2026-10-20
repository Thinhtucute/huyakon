package com.example.huyakon.data.repository

import com.example.huyakon.data.remote.SpaceApi
import com.example.huyakon.data.remote.mapper.toDomain
import com.example.huyakon.domain.model.Capture
import com.example.huyakon.domain.model.ScreenAnalysis
import com.example.huyakon.domain.port.AnalysisRepository
import com.example.huyakon.domain.util.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

class SpaceAnalysisRepository(private val api: SpaceApi) : AnalysisRepository {

    override suspend fun analyze(capture: Capture): Result<ScreenAnalysis> = suspendRunCatching {
        val jpegBase64 = withContext(Dispatchers.IO) {
            Base64.getEncoder().encodeToString(File(capture.path).readBytes())
        }
        api.analyze(jpegBase64).toDomain()
    }
}