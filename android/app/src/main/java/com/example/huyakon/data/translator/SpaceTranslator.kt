package com.example.huyakon.data.translator

import com.example.huyakon.data.remote.TranslationApi
import com.example.huyakon.data.remote.dto.TranslationRequestLineDto
import com.example.huyakon.domain.model.TextLine
import com.example.huyakon.domain.model.TextRole
import com.example.huyakon.domain.model.TranslatedLine
import com.example.huyakon.domain.port.Translator

class SpaceTranslator(
    private val api: TranslationApi,
    private val glossary: String = "",
) : Translator {
    override suspend fun translate(lines: List<TextLine>): Result<List<TranslatedLine>> = runCatching {
        val response = api.translate(lines.mapIndexed { index, line ->
            TranslationRequestLineDto(
                order = index,
                textId = line.id,
                text = line.text,
                role = line.role.toApiValue(),
                maybeSfx = line.maybeSfx,
                speaker = line.speaker?.let { "character_${it.clusterId}" },
                speakerConfidence = line.speaker?.confidence?.name?.lowercase(),
            )
        }, glossary)

        response.error?.let { error ->
            error("Translator error: ${error.message ?: error.type ?: "unknown error"}")
        }
        val translationsById = response.flat.associateBy { it.textId }
        lines.map { line ->
            val translation = translationsById[line.id]?.translation
                ?: error("Translator returned no translation for text_id ${line.id}")
            TranslatedLine(line, translation)
        }
    }

    private fun TextRole.toApiValue() = when (this) {
        TextRole.Dialogue -> "dialogue"
        TextRole.FreeText -> "free_text"
        TextRole.Watermark -> "watermark"
        TextRole.Unknown -> "unknown"
    }
}
