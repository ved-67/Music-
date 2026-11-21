package com.barsense.game
import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import java.nio.ByteBuffer

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val engine = GameVisionEngine()
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel="barsense_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"BarSense capture",NotificationManager.IMPORTANCE_LOW))
            startForeground(7,Notification.Builder(this,channel).setContentTitle("BarSense screen analysis").setContentText("User-approved capture active").setSmallIcon(android.R.drawable.ic_menu_view).build())
        }
        val code=intent?.getIntExtra("resultCode",Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        @Suppress("DEPRECATION") val data=intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        projection=(getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code,data)
        val dm=resources.displayMetrics
        reader=ImageReader.newInstance(dm.widthPixels,dm.heightPixels,PixelFormat.RGBA_8888,2)
        display=projection?.createVirtualDisplay("BarSense",dm.widthPixels,dm.heightPixels,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,null)
        reader?.setOnImageAvailableListener({ r -> val image=r.acquireLatestImage() ?: return@setOnImageAvailableListener; try { analyze(image) } finally { image.close() } },null)
        return START_NOT_STICKY
    }
    private fun analyze(image: Image) {
        val plane=image.planes[0]
        val buffer=plane.buffer
        val bytes=ByteArray(buffer.remaining())
        buffer.get(bytes)
        val result=engine.analyze(image.width,image.height,bytes,plane.pixelStride,plane.rowStride)
        BarSenseState.update(result)
    }
    override fun onDestroy() { display?.release(); reader?.close(); projection?.stop(); engine.reset(); super.onDestroy() }
}
object BarSenseState {
    @Volatile var redSamples=0
    @Volatile var status="Waiting for screen"
    @Volatile var eggX=-1
    @Volatile var eggY=-1
    @Volatile var gapTop=-1
    @Volatile var gapBottom=-1
    @Volatile var obstacleColumns=""
    fun update(r: GameVisionEngine.Result) {
        eggX=r.motionX; eggY=r.motionY; gapTop=r.gapTop ?: -1; gapBottom=r.gapBottom ?: -1
        obstacleColumns=r.barColumns.joinToString(","); status=r.status
    }
}
