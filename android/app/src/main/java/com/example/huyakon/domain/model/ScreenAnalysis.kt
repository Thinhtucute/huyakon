package com.example.huyakon.domain.model

/** Pixel box in the coordinate space of the analysed image. */
data class Box(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun scaled(scaleX: Float, scaleY: Float) =
        Box(left * scaleX, top * scaleY, right * scaleX, bottom * scaleY)
}

enum class TextRole { Dialogue, FreeText, Watermark, Unknown }

enum class SpeakerConfidence { High, Medium, Low }

/** clusterId is per page */
data class Speaker(val clusterId: Int, val confidence: SpeakerConfidence)

data class TextLine(
    val id: Int,
    val text: String,
    val box: Box,
    val role: TextRole,
    val maybeSfx: Boolean,
    val speaker: Speaker?,
)

/** lines in reading order */
data class ScreenAnalysis(
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<TextLine>,
    val bubbles: List<Box> = emptyList()
)