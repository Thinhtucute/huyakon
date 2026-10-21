package com.example.huyakon.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// No default values here: kotlinx.serialization omits properties equal to their default
// (encodeDefaults = false), which would silently drop "lang" / "proximity_speakers" from the request.
@Serializable
data class AnalyzeRequestDto(
    @SerialName("image_base64") val imageBase64: String,
    val lang: String,
    @SerialName("proximity_speakers") val proximitySpeakers: Boolean,
)

@Serializable
data class AnalysisDto(
    val image: ImageDto = ImageDto(),
    val bubbles: List<BubbleDto> = emptyList(),
    val texts: List<TextDto> = emptyList(),
    val script: List<ScriptLineDto> = emptyList(),
    val error: SpaceErrorDto? = null,
)

@Serializable
data class ImageDto(val width: Int = 0, val height: Int = 0)

@Serializable
data class BubbleDto(
    val id: Int? = null,
    @SerialName("panel_id") val panelId: Int? = null,
    val bbox: List<Float> = emptyList(),
)

@Serializable
data class TextDto(val id: Int, val bbox: List<Float>)

@Serializable
data class ScriptLineDto(
    val order: Int,
    @SerialName("text_id") val textId: Int,
    val text: String,
    val role: String,
    @SerialName("maybe_sfx") val maybeSfx: Boolean = false,
    val speaker: String? = null,
    @SerialName("speaker_confidence") val speakerConfidence: String? = null,
)