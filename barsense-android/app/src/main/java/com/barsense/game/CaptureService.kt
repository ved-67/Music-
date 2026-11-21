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
    private val vision = GameVisionEngine()
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            BarSenseState.captureActive = false
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = "barsense_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "BarSense capture", NotificationManager.IMPORTANCE_LOW)
            )
            val notification = Notification.Builder(this, channel)
                .setContentTitle("BarSense vision active")
                .setContentText("Screen analysis is running • Auto mode is user controlled")
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
        BarSenseState.captureActive = true
        BarSenseState.status = "Capture active • waiting for game motion"
        return START_NOT_STICKY
    }

    private fun analyze(image: Image) {
        val plane = image.planes[0]
        val result = vision.analyze(image.width, image.height, plane.buffer, plane.rowStride, plane.pixelStride)
        BarSenseState.eggFound = result.eggFound
        BarSenseState.obstacleAhead = result.obstacleAhead
        BarSenseState.confidence = result.confidence
        BarSenseState.status = result.status
        BarSenseState.framesAnalyzed++
        if (result.shouldTap && BarSenseState.autoEnabled) {
            val x = image.width * BarSenseState.tapTargetX
            val y = image.height * BarSenseState.tapTargetY
            val sent = TapAccessibilityService.instance?.tap(x, y) ?: false
            if (sent) {
                BarSenseState.tapCount++
                BarSenseState.status = "AUTO jump sent • confidence ${result.confidence}%"
            } else {
                BarSenseState.status = "Auto ready • enable BarSense Accessibility"
            }
        }
    }

    override fun onDestroy() {
        BarSenseState.captureActive = false
        display?.release()
        reader?.close()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        super.onDestroy()
    }
}

object BarSenseState {
    @Volatile var captureActive = false
    @Volatile var autoEnabled = false
    @Volatile var eggFound = false
    @Volatile var obstacleAhead = false
    @Volatile var confidence = 0
    @Volatile var framesAnalyzed = 0
    @Volatile var tapCount = 0
    @Volatile var status = "Ready"
    @Volatile var tapTargetX = 0.89f
    @Volatile var tapTargetY = 0.50f
}
