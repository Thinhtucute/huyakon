package com.example.huyakon.platform.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import com.example.huyakon.domain.model.Capture
import com.example.huyakon.domain.port.ScreenCapturer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * owns one MediaProjection session
 * VirtualDisplay + ImageReader stay alive until [release],
 * frames are grabbed on demand through [capture].
 */
class ScreenCaptureController(
    private val context: Context,
    private val projection: MediaProjection,
    private val onProjectionStopped: () -> Unit,
) : ScreenCapturer {

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() = onProjectionStopped()
    }

    fun start() {
        projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        val metrics = context.resources.displayMetrics
        val reader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2
        )
        imageReader = reader
        virtualDisplay = projection.createVirtualDisplay(
            "HuyakonCapture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null
        )
    }

    // Bitmap work and file IO run off the main thread
    override suspend fun capture(): Result<Capture> = withContext(Dispatchers.Default) {
        runCatching {
            val reader = checkNotNull(imageReader) { "Capture session not started." }
            val image = reader.acquireLatestImage() ?: error("No frame received. Try again.")
            val bitmap = try {
                image.toBitmap()
            } finally {
                image.close()
            }
            bitmap.saveAsJpeg()
        }
    }

    fun release() {
        virtualDisplay?.release()
        imageReader?.close()

        projection.unregisterCallback(projectionCallback)
        projection.stop()
        virtualDisplay = null
        imageReader = null
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * width
        val padded = Bitmap.createBitmap(
            width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        if (cropped !== padded) padded.recycle()
        return cropped
    }

    private fun Bitmap.saveAsJpeg(): Capture {
        pruneOldCaptures()
        val frameWidth = width
        val frameHeight = height
        val file = File(context.cacheDir, "$FILE_PREFIX${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        recycle()
        return Capture(
            path = file.absolutePath,
            byteCount = file.length(),
            width = frameWidth,
            height = frameHeight,
        )
    }

    private fun pruneOldCaptures() {
        context.cacheDir.listFiles { f -> f.name.startsWith(FILE_PREFIX) }?.forEach(File::delete)
    }

    private companion object {
        const val FILE_PREFIX = "capture-"
        const val JPEG_QUALITY = 85
    }
}