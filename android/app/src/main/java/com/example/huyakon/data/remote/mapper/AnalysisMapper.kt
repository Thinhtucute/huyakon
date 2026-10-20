package com.example.huyakon.data.remote.mapper

import com.example.huyakon.data.remote.dto.AnalysisDto
import com.example.huyakon.data.remote.dto.ScriptLineDto
import com.example.huyakon.domain.model.Box
import com.example.huyakon.domain.model.ScreenAnalysis
import com.example.huyakon.domain.model.Speaker
import com.example.huyakon.domain.model.SpeakerConfidence
import com.example.huyakon.domain.model.TextLine
import com.example.huyakon.domain.model.TextRole

fun AnalysisDto.toDomain(): ScreenAnalysis {
    val boxes = texts.associate { it.id to it.bbox }
    val bubbles = bubbles.mapNotNull { bubble ->
        bubble.bbox.takeIf { it.size >= 4 }?.let { Box(it[0], it[1], it[2], it[3]) }
    }
    val lines = script.sortedBy { it.order }.mapNotNull { line ->
        val b = boxes[line.textId]?.takeIf { it.size >= 4 } ?: return@mapNotNull null
        TextLine(
            id = line.textId,
            text = line.text,
            box = Box(b[0], b[1], b[2], b[3]),
            role = line.role.toRole(),
            maybeSfx = line.maybeSfx,
            speaker = line.toSpeaker(),
        )
    }
    return ScreenAnalysis(image.width, image.height, lines, bubbles)
}

private fun String.toRole() = when (this) {
    "dialogue" -> TextRole.Dialogue
    "free_text" -> TextRole.FreeText
    "watermark" -> TextRole.Watermark
    else -> TextRole.Unknown
}

private fun ScriptLineDto.toSpeaker(): Speaker? {
    val cluster = speaker?.removePrefix("character_")?.toIntOrNull() ?: return null
    val confidence = when (speakerConfidence) {
        "high" -> SpeakerConfidence.High
        "medium" -> SpeakerConfidence.Medium
        "low" -> SpeakerConfidence.Low
        else -> return null
    }
    return Speaker(cluster, confidence)
}