package com.example.huyakon.service

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.huyakon.R
import com.example.huyakon.domain.PipelineState
import com.example.huyakon.domain.PipelineStateHolder
import com.example.huyakon.domain.port.ScreenCapturer
import com.example.huyakon.domain.usecase.TranslateScreenUseCase
import com.example.huyakon.platform.capture.ScreenCaptureController
import com.example.huyakon.platform.overlay.OverlayFabController
import com.example.huyakon.platform.overlay.TranslationOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Wiring only: owns the lifecycle of the FAB and the capture session and forwards pipeline state. */
@AndroidEntryPoint
class OverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Injected by Hilt inside super.onCreate(); never touch these before that call.
    @Inject lateinit var pipeline: PipelineStateHolder
    @Inject lateinit var translateScreen: TranslateScreenUseCase

    private lateinit var fab: OverlayFabController
    private lateinit var translationOverlay: TranslationOverlayController
    private var capture: ScreenCaptureController? = null
    private var runJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // The type must be declared here (and in the manifest) before getMediaProjection() is called.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
        translationOverlay = TranslationOverlayController(this).also { it.hide() }
        fab = OverlayFabController(this, onTap = ::onFabTapped).also { it.show() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (capture == null && intent != null) {
            val data = intent.parcelable<Intent>(EXTRA_RESULT_DATA)
            if (data == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            val manager = getSystemService(MediaProjectionManager::class.java)
            val projection = manager.getMediaProjection(
                intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED), data
            )
            val controller = ScreenCaptureController(this, projection, onProjectionStopped = ::stopSelf)
            controller.start()
            capture = controller
        }
        return START_NOT_STICKY
    }

    private fun onFabTapped() {
        if (pipeline.state.value is PipelineState.Showing) {
            translationOverlay.remove()
            pipeline.update(PipelineState.Idle)
            fab.setStatus("訳")
            return
        }
        val capturer: ScreenCapturer = capture ?: return
        if (runJob?.isActive == true) return
        runJob = scope.launch {
            fab.setStatus("準備中")
            fab.setVisible(false)
            translationOverlay.hide()
            var showingTranslation = false
            try {
                delay(SETTLE_MS) // lets the hidden FAB leave the screen so it is not in the frame
                translateScreen(capturer).collect { state ->
                    // Bring the FAB back as soon as the frame is taken; the network wait is long.
                    if (state !is PipelineState.Capturing) fab.setVisible(true)
                    fab.setStatus(state.fabStatus())
                    when (state) {
                        is PipelineState.Showing -> {
                            showingTranslation = true
                            translationOverlay.show(state.analysis, state.translated)
                        }
                        else -> {
                            showingTranslation = false
                            translationOverlay.hide()
                        }
                    }
                    pipeline.update(state)
                    updateNotification(state)
                    if (state is PipelineState.Error) toast(state.message)
                }
            } finally {
                fab.setVisible(true)
                if (!showingTranslation) translationOverlay.hide()
            }
        }
    }

    private fun PipelineState.fabStatus(): String = when (this) {
        PipelineState.Idle -> "訳"
        PipelineState.Capturing -> "撮影中"
        is PipelineState.Detecting -> "解析中"
        is PipelineState.Translating -> "翻訳中"
        is PipelineState.Showing -> "完了"
        is PipelineState.Error -> "エラー: ${messageForFab(this.message)}"
    }

    private fun messageForFab(message: String): String =
        message
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_FAB_ERROR_LENGTH)
            .ifEmpty { "原因不明" }

    override fun onDestroy() {
        scope.cancel()
        translationOverlay.remove()
        fab.remove()
        capture?.release()
        pipeline.update(PipelineState.Idle)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen translator", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Huyakon overlay active")
        .setContentText("Tap 訳 to translate the current screen")
        .setSmallIcon(R.mipmap.ic_launcher)
        .build()

    private fun updateNotification(state: PipelineState) {
        val message = when (state) {
            PipelineState.Idle -> "Tap 訳 to translate the current screen"
            PipelineState.Capturing -> "Capturing the current screen…"
            is PipelineState.Detecting -> "Uploading image to analyzer Space…"
            is PipelineState.Translating -> "Translating ${state.analysis.lines.size} lines…"
            is PipelineState.Showing -> "Translation complete"
            is PipelineState.Error -> "Translation failed: ${state.message}"
        }
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification(message)
        )
    }

    private fun notification(message: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Huyakon screen translator")
        .setContentText(message)
        .setSmallIcon(R.mipmap.ic_launcher)
        .build()

    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(key)

    companion object {
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1001
        private const val SETTLE_MS = 250L
        private const val MAX_FAB_ERROR_LENGTH = 34

        /** Starts the foreground service with the MediaProjection consent result. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, OverlayService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}