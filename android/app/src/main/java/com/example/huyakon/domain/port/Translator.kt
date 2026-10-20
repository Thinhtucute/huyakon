package com.example.huyakon.domain.port
 
import com.example.huyakon.domain.model.TextLine
import com.example.huyakon.domain.model.TranslatedLine
 
interface Translator {
    suspend fun translate(lines: List<TextLine>): Result<List<TranslatedLine>>
}