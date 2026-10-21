package com.example.huyakon.data.translator

import com.example.huyakon.data.remote.TranslationApi
import com.example.huyakon.data.remote.apiCall
import com.example.huyakon.data.remote.dto.TranslationRequestDto
import com.example.huyakon.data.remote.dto.TranslationRequestLineDto
import com.example.huyakon.domain.model.TextLine
import com.example.huyakon.domain.model.TextRole
import com.example.huyakon.domain.model.TranslatedLine
import com.example.huyakon.domain.port.Translator
import com.example.huyakon.domain.util.suspendRunCatching
import javax.inject.Inject

// Hilt can't inject a bare String, so the old `glossary: String = ""` constructor
// parameter is gone. When glossary becomes real, provide it via a @Qualifier or a GlossaryProvider class.
class SpaceTranslator @Inject constructor(
    private val api: TranslationApi,
) : Translator {
    private val glossary = ""

    override suspend fun translate(lines: List<TextLine>): Result<List<TranslatedLine>> = suspendRunCatching {
        val request = TranslationRequestDto(
            script = lines.mapIndexed { index, line ->
                TranslationRequestLineDto(
                    order = index,
                    textId = line.id,
                    text = line.text,
                    role = line.role.toApiValue(),
                    maybeSfx = line.maybeSfx,
                    speaker = line.speaker?.let { "character_${it.clusterId}" },
                    speakerConfidence = line.speaker?.confidence?.name?.lowercase(),
                )
            },
            glossary = glossary,
        )
        val response = apiCall("translation") { api.translate(request) }

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
