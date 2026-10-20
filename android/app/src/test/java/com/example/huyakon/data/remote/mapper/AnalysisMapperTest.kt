package com.example.huyakon.data.remote.mapper

import com.example.huyakon.data.remote.dto.AnalysisDto
import com.example.huyakon.domain.model.Box
import com.example.huyakon.domain.model.SpeakerConfidence
import com.example.huyakon.domain.model.TextRole
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Trimmed copy of what the Space returns (extra fields are ignored on purpose).
    private val sample = """
        {
          "image": {"width": 1336, "height": 1920},
          "bubbles": [
            {"id": 0, "bbox": [20, 30, 900, 900]}
          ],
          "texts": [
            {"id": 0, "bbox": [637, 66, 806, 446], "text": "モデル", "kind": "bubble_text"},
            {"id": 3, "bbox": [1117, 42, 1320, 335], "text": "亜美", "kind": "bubble_text"}
          ],
          "script": [
            {"order": 1, "text_id": 0, "text": "モデル", "role": "dialogue", "maybe_sfx": false,
             "speaker": "character_0", "speaker_confidence": "high", "speaker_source": "tail"},
            {"order": 0, "text_id": 3, "text": "亜美", "role": "dialogue", "maybe_sfx": false,
             "speaker": "character_2", "speaker_confidence": "medium", "speaker_source": "nearest"},
            {"order": 2, "text_id": 9, "text": "ざわ", "role": "free_text", "maybe_sfx": true,
             "speaker": null, "speaker_confidence": null, "speaker_source": null}
          ]
        }
    """.trimIndent()

    private fun parse() = json.decodeFromString(AnalysisDto.serializer(), sample).toDomain()

    @Test
    fun linesComeBackInScriptOrderWithBoxesJoinedFromTexts() {
        val analysis = parse()
        assertEquals(1336, analysis.imageWidth)
        assertEquals(1920, analysis.imageHeight)
        assertEquals(listOf(3, 0), analysis.lines.map { it.id })
        assertEquals(Box(1117f, 42f, 1320f, 335f), analysis.lines[0].box)
        assertEquals(listOf(Box(20f, 30f, 900f, 900f)), analysis.bubbles)
    }

    @Test
    fun scriptLinesWithoutABoxAreDropped() {
        // text_id 9 has no entry in `texts`.
        assertEquals(2, parse().lines.size)
    }

    @Test
    fun speakerAndConfidenceAreParsed() {
        val speaker = parse().lines[1].speaker
        assertEquals(0, speaker?.clusterId)
        assertEquals(SpeakerConfidence.High, speaker?.confidence)
        assertEquals(SpeakerConfidence.Medium, parse().lines[0].speaker?.confidence)
    }

    @Test
    fun roleIsMappedAndMissingSpeakerBecomesNull() {
        val noSpeaker = """
            {"image": {"width": 10, "height": 10},
             "texts": [{"id": 1, "bbox": [0, 0, 5, 5]}],
             "script": [{"order": 0, "text_id": 1, "text": "x", "role": "free_text"}]}
        """.trimIndent()
        val line = json.decodeFromString(AnalysisDto.serializer(), noSpeaker).toDomain().lines.single()
        assertEquals(TextRole.FreeText, line.role)
        assertNull(line.speaker)
    }
}