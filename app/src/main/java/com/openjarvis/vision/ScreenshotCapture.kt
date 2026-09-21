package com.openjarvis.vision

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Optional MediaProjection fallback for callers that need a persistent capture surface. */
class ScreenshotCapture(private val context: Context) {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var width = 0
    private var height = 0
    private var density = 0

    fun createCaptureIntent(): Intent {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    fun startCapture(): Boolean = true

    fun initialize(projection: MediaProjection): Bitmap? {
        release()
        val metrics = context.resources.displayMetrics
        width = metrics.widthPixels
        height = metrics.heightPixels
        density = metrics.densityDpi
        handlerThread = HandlerThread("JarvisScreenshot").apply { start() }
        handler = Handler(handlerThread!!.looper)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        mediaProjection = projection
        virtualDisplay = projection.createVirtualDisplay(
            "OpenJarvisCapture", width, height, density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, handler
        )
        return null
    }

    fun capture(): Bitmap? {
        val reader = imageReader ?: return null
        repeat(3) {
            val image = reader.acquireLatestImage()
            if (image != null) {
                return image.use { img ->
                    val plane = img.planes.firstOrNull() ?: return@use null
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * img.width
                    val bitmap = Bitmap.createBitmap(
                        img.width + rowPadding / pixelStride,
                        img.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(plane.buffer)
                    if (rowPadding > 0) Bitmap.createBitmap(bitmap, 0, 0, img.width, img.height).also { bitmap.recycle() }
                    else bitmap
                }
            }
            runCatching { Thread.sleep(40) }
        }
        return null
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    companion object {
        @Volatile private var instance: ScreenshotCapture? = null
        fun getInstance(context: Context): ScreenshotCapture = instance ?: synchronized(this) {
            instance ?: ScreenshotCapture(context.applicationContext).also { instance = it }
        }
    }
}
