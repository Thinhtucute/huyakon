package com.example.huyakon.presentation.main

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.huyakon.domain.PipelineState
import com.example.huyakon.domain.model.Capture
import com.example.huyakon.domain.model.ScreenAnalysis
import com.example.huyakon.domain.model.Speaker
import com.example.huyakon.domain.model.SpeakerConfidence
import com.example.huyakon.domain.model.TextRole
import com.example.huyakon.domain.model.TranslatedLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    state: PipelineState,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Huyakon")
        Text("Capture → analyzer Space → translation Space")
        Button(onClick = onStart) { Text("Enable translator overlay") }
        when (state) {
            PipelineState.Idle -> Unit
            PipelineState.Capturing -> {
                PipelineStatus(
                    title = "Capturing screen",
                    detail = "Preparing the image before uploading it…",
                    progress = 0.15f
                )
            }
            is PipelineState.Detecting -> {
                PipelineStatus(
                    title = "Uploading image to analyzer Space",
                    detail = "The Space is detecting Japanese text and reading order…",
                    progress = 0.45f
                )
                CapturePreview(state.capture)
            }
            is PipelineState.Translating -> {
                PipelineStatus(
                    title = "Translating ${state.analysis.lines.size} lines",
                    detail = "The translation Space is processing the detected script…",
                    progress = 0.75f
                )
                CapturePreview(state.capture, state.analysis)
            }
            is PipelineState.Showing -> {
                PipelineStatus(
                    title = "Translation complete",
                    detail = "${state.translated.count { it.english.isNotBlank() }} translations received from the Spaces.",
                    progress = 1f
                )
                CapturePreview(state.capture, state.analysis)
                TranslatedLines(state.translated)
            }
            is PipelineState.Error -> {
                Card {
                    Text(
                        "Translation failed\n${state.message}",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineStatus(
    title: String,
    detail: String,
    progress: Float,
) {
    Card {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title)
            Text(detail)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CapturePreview(capture: Capture, analysis: ScreenAnalysis? = null) {
    // Decoded off the main thread
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = capture.path) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(capture.path)?.asImageBitmap()
        }
    }
    val ratio = capture.width.toFloat() / capture.height
    Box(Modifier.height(520.dp).aspectRatio(ratio, matchHeightConstraintsFirst = true)) {
        bitmap?.let {
            Image(
                it, "Last screen capture",
                Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
        if (analysis != null) {
            Canvas(Modifier.fillMaxSize()) {
                val scaleX = size.width / analysis.imageWidth
                val scaleY = size.height / analysis.imageHeight
                analysis.lines.forEach { line ->
                    val box = line.box.scaled(scaleX, scaleY)
                    drawRect(
                        color = line.role.color(),
                        topLeft = Offset(box.left, box.top),
                        size = Size(box.width, box.height),
                        style = Stroke(width = 3f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslatedLines(lines: List<TranslatedLine>) {
    lines.forEachIndexed { index, item ->
        Column {
            Text("${index + 1}. ${item.line.text}${item.line.speaker.tag()}")
            Text("→ ${item.english}", color = Color(0xFF174A2C))
        }
    }
}

private fun TextRole.color(): Color = when (this) {
    TextRole.Dialogue -> Color.Red
    TextRole.FreeText -> Color(0xFFFF9800)
    TextRole.Watermark, TextRole.Unknown -> Color.Gray
}

/** "[S1]" for a confident speaker, "[S1?]" for a guess, nothing when unknown. */
private fun Speaker?.tag(): String = when {
    this == null -> ""
    confidence == SpeakerConfidence.High -> "  [S$clusterId]"
    else -> "  [S$clusterId?]"
}