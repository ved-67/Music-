package com.barsense.game
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = "barsense_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "BarSense capture", NotificationManager.IMPORTANCE_LOW)
            )
            val notification = Notification.Builder(this, channel)
                .setContentTitle("BarSense screen capture")
                .setContentText("User-approved capture active")
                .setSmallIcon(android.R.drawable.ic_menu_view).build()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else startForeground(7, notification)
        }
        val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)
        projection?.registerCallback(projectionCallback, null)
        val dm = resources.displayMetrics
        reader = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
        display = projection?.createVirtualDisplay(
            "BarSense", dm.widthPixels, dm.heightPixels, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null
        )
        reader?.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try { analyze(image) } finally { image.close() }
        }, null)
        return START_NOT_STICKY
    }
    private fun analyze(image: Image) {
        // Diagnostic red-pixel sampling only; no automatic tap decisions are made.
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        var count = 0
        var y = height / 4
        while (y < height * 3 / 4) {
            var x = 0
            while (x < width) {
                val index = y * rowStride + x * pixelStride
                if (index + 2 < buffer.limit()) {
                    val red = buffer.get(index).toInt() and 255
                    val green = buffer.get(index + 1).toInt() and 255
                    val blue = buffer.get(index + 2).toInt() and 255
                    if (red > 130 && red > green * 1.35 && red > blue * 1.25) count++
                }
                x += 8
            }
            y += 8
        }
        BarSenseState.redSamples = count
    }
    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        super.onDestroy()
    }
}
object BarSenseState { @Volatile var redSamples = 0 }
