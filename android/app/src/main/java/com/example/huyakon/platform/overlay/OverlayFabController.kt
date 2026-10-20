package com.example.huyakon.platform.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayFabController(
    private val context: Context,
    private val onTap: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var fab: Button? = null

    fun show() {
        if (fab != null) return
        val button = Button(context).apply {
            text = "訳"
        }
        val params = WindowManager.LayoutParams(
            192.dp(), 64.dp(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (context.resources.displayMetrics.widthPixels - 112.dp()).coerceAtLeast(0)
            y = (context.resources.displayMetrics.heightPixels / 2).coerceAtLeast(0)
        }
        installDragAndTap(button, params)
        windowManager.addView(button, params)
        fab = button
    }

    /** Hidden while capturing */
    fun setVisible(visible: Boolean) {
        fab?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun setStatus(status: String) {
        fab?.text = status
    }

    fun remove() {
        fab?.let(windowManager::removeView)
        fab = null
    }

    private fun installDragAndTap(
        button: Button,
        layoutParams: WindowManager.LayoutParams,
    ) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val touchSlop = 8.dp()

        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        layoutParams.x = (startX + dx).roundToInt()
                            .coerceIn(0, context.resources.displayMetrics.widthPixels - view.width)
                        layoutParams.y = (startY + dy).roundToInt()
                            .coerceIn(0, context.resources.displayMetrics.heightPixels - view.height)
                        windowManager.updateViewLayout(view, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        view.performClick()
                        onTap()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun Int.dp() = (this * context.resources.displayMetrics.density).toInt()
}
