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

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel="barsense_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"BarSense capture",NotificationManager.IMPORTANCE_LOW))
            startForeground(7,Notification.Builder(this,channel).setContentTitle("BarSense screen capture").setContentText("User-approved capture active").setSmallIcon(android.R.drawable.ic_menu_view).build())
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
        // Diagnostic red-pixel sampling only. No autonomous tap decisions are made.
        val p=image.planes[0]; val b=p.buffer; val w=image.width; val h=image.height
        val pixel=p.pixelStride; val row=p.rowStride; var count=0
        var y=h/4
        while(y<h*3/4) { var x=0; while(x<w) { val i=y*row+x*pixel
            if(i+2<b.limit()) { val r=b.get(i).toInt() and 255; val g=b.get(i+1).toInt() and 255; val bl=b.get(i+2).toInt() and 255
                if(r>130 && r>g*1.35 && r>bl*1.25) count++
            }; x+=8
        }; y+=8 }
        BarSenseState.redSamples=count
    }
    override fun onDestroy() { display?.release(); reader?.close(); projection?.stop(); super.onDestroy() }
}
object BarSenseState { @Volatile var redSamples=0 }
