package com.example.huyakon.data.translator

import com.example.huyakon.domain.model.TextLine
import com.example.huyakon.domain.model.TranslatedLine
import com.example.huyakon.domain.port.Translator

/** PLACEHOLDER until Qwen is wired in */
class StubTranslator : Translator {
    override suspend fun translate(lines: List<TextLine>): Result<List<TranslatedLine>> =
        Result.success(lines.map { TranslatedLine(it, "[EN] ${it.text}") })
}