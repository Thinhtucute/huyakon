package com.example.huyakon.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranslationRequestLineDto(
    val order: Int,
    @SerialName("text_id") val textId: Int,
    val text: String,
    val role: String,
    @SerialName("maybe_sfx") val maybeSfx: Boolean,
    val speaker: String? = null,
    @SerialName("speaker_confidence") val speakerConfidence: String? = null,
)

@Serializable
data class TranslationRequestDto(
    val script: List<TranslationRequestLineDto>,
    val glossary: String,
)

@Serializable
data class TranslationResponseDto(
    val flat: List<TranslatedEntryDto> = emptyList(),
    val error: SpaceErrorDto? = null,
)

@Serializable
data class TranslatedEntryDto(
    @SerialName("text_id") val textId: Int? = null,
    val text: String? = null,
    val translation: String = "",
)
