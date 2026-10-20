package com.example.huyakon.platform.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.huyakon.domain.model.ScreenAnalysis
import com.example.huyakon.domain.model.TranslatedLine
import com.example.huyakon.domain.model.Box
import kotlin.math.max
import kotlin.math.min

class TranslationOverlayController(
    context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val view = TranslationOverlayView(context)
    private var added = false

    fun show(analysis: ScreenAnalysis, translated: List<TranslatedLine>) {
        view.setLines(analysis, translated)
        if (!added) {
            windowManager.addView(view, layoutParams())
            added = true
        } else {
            view.visibility = View.VISIBLE
        }
    }

    fun hide() {
        if (added) view.visibility = View.GONE
    }

    fun remove() {
        if (added) {
            windowManager.removeView(view)
            added = false
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private class TranslationOverlayView(context: Context) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create("Comic Sans MS", Typeface.BOLD)
        }
        private val lines = mutableListOf<RenderedLine>()
        private val hiddenLineIds = mutableSetOf<Int>()
        private var analysis: ScreenAnalysis? = null
        private var translated: List<TranslatedLine> = emptyList()

        fun setLines(analysis: ScreenAnalysis, translated: List<TranslatedLine>) {
            if (this.translated != translated) {
                hiddenLineIds.clear()
            }
            this.analysis = analysis
            this.translated = translated
            rebuildLines()
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            rebuildLines()
        }

        private fun rebuildLines() {
            lines.clear()
            val currentAnalysis = analysis ?: return
            if (width <= 0 || height <= 0 ||
                currentAnalysis.imageWidth <= 0 || currentAnalysis.imageHeight <= 0
            ) {
                visibility = GONE
                return
            }

            val scaleX = width.toFloat() / currentAnalysis.imageWidth
            val scaleY = height.toFloat() / currentAnalysis.imageHeight
            translated
                .filter { it.english.isNotBlank() && it.line.box.width > 0f && it.line.box.height > 0f }
                .forEach { translatedLine ->
                    val box = currentAnalysis.bubbleFor(translatedLine.line.box)
                    lines += RenderedLine(
                        translatedLine.line.id,
                        RectF(
                            box.left * scaleX,
                            box.top * scaleY,
                            box.right * scaleX,
                            box.bottom * scaleY
                        ),
                        translatedLine.english.trim()
                    )
                }
            visibility = if (lines.isEmpty()) GONE else VISIBLE
            invalidate()
        }

        private fun ScreenAnalysis.bubbleFor(textBox: Box): Box {
            bubbles
                .filter { bubble ->
                    bubble.left <= textBox.left &&
                        bubble.top <= textBox.top &&
                        bubble.right >= textBox.right &&
                        bubble.bottom >= textBox.bottom
                }
                .minByOrNull { it.width * it.height }
                ?.let { bubble ->
                    val paddingX = min(12f, bubble.width * 0.025f)
                    val paddingY = min(12f, bubble.height * 0.025f)
                    return Box(
                        bubble.left + paddingX,
                        bubble.top + paddingY,
                        bubble.right - paddingX,
                        bubble.bottom - paddingY
                    )
                }
            return textBox
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            lines.forEach { line ->
                if (line.id in hiddenLineIds) return@forEach
                val box = line.box
                val padding = max(4f, min(box.width(), box.height()) * 0.08f)
                canvas.drawRoundRect(box, 6f, 6f, backgroundPaint)
                drawWrappedText(
                    canvas,
                    line.text,
                    box.left + padding,
                    box.top + padding,
                    box.width() - padding * 2,
                    box.height() - padding * 2,
                    max(8f, min(box.width(), box.height()) * 0.28f),
                )
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                lines.asReversed()
                    .firstOrNull { it.box.contains(event.x, event.y) }
                    ?.let { line ->
                        if (!hiddenLineIds.add(line.id)) {
                            hiddenLineIds.remove(line.id)
                        }
                        invalidate()
                    }
            }
            return true
        }

        private fun drawWrappedText(
            canvas: Canvas,
            text: String,
            left: Float,
            top: Float,
            maxWidth: Float,
            maxHeight: Float,
            initialTextSize: Float,
        ) {
            val availableWidth = max(1f, maxWidth)
            val availableHeight = max(1f, maxHeight)
            var textSize = initialTextSize
            var rows: List<String>
            do {
                textPaint.textSize = textSize
                rows = wrapText(text, availableWidth)
                if (rows.size * textPaint.fontSpacing <= availableHeight || textSize <= 8f) break
                textSize -= 0.5f
            } while (textSize > 8f)

            val lineHeight = textPaint.fontSpacing
            val maxRows = (availableHeight / lineHeight).toInt().coerceAtLeast(1)
            val visibleRows = if (rows.size <= maxRows) {
                rows
            } else {
                rows.take(maxRows).toMutableList().also { visible ->
                    visible[visible.lastIndex] = ellipsize(
                        visible.last(),
                        availableWidth
                    )
                }
            }

            canvas.save()
            canvas.clipRect(left, top, left + availableWidth, top + availableHeight)
            visibleRows.forEachIndexed { index, value ->
                canvas.drawText(
                    value,
                    left,
                    top - textPaint.ascent() + lineHeight * index,
                    textPaint
                )
            }
            canvas.restore()
        }

        private fun wrapText(text: String, maxWidth: Float): List<String> {
            val rows = mutableListOf<String>()
            var row = ""
            text.split(Regex("\\s+"))
                .filter(String::isNotEmpty)
                .flatMap { word ->
                    if (textPaint.measureText(word) <= maxWidth) {
                        listOf(word)
                    } else {
                        word.chunkedByWidth(maxWidth)
                    }
                }
                .forEach { word ->
                    val candidate = if (row.isEmpty()) word else "$row $word"
                    if (row.isNotEmpty() && textPaint.measureText(candidate) > maxWidth) {
                        rows += row
                        row = word
                    } else {
                        row = candidate
                    }
                }
            if (row.isNotEmpty()) rows += row
            return rows.ifEmpty { listOf("") }
        }

        private fun String.chunkedByWidth(maxWidth: Float): List<String> {
            val chunks = mutableListOf<String>()
            var chunk = ""
            for (character in this) {
                val candidate = chunk + character
                if (chunk.isNotEmpty() && textPaint.measureText(candidate) > maxWidth) {
                    chunks += chunk
                    chunk = character.toString()
                } else {
                    chunk = candidate
                }
            }
            if (chunk.isNotEmpty()) chunks += chunk
            return chunks
        }

        private fun ellipsize(value: String, maxWidth: Float): String {
            var result = value
            while (result.isNotEmpty() &&
                textPaint.measureText("$result…") > maxWidth
            ) {
                result = result.dropLast(1)
            }
            return if (result.isEmpty()) "…" else "$result…"
        }

        private data class RenderedLine(
            val id: Int,
            val box: RectF,
            val text: String,
        )
    }
}
