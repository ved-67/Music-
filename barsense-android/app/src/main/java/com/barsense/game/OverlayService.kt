package com.barsense.game
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if (panel == null) createPanel(); return START_STICKY }
    private fun createPanel() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12,8,12,8); setBackgroundColor(0xEE17172B.toInt()) }
        val status = TextView(this).apply { text = "BarSense • READY"; setTextColor(-1) }; box.addView(status)
        box.addView(Button(this).apply { text = "PAUSE / READY"; setOnClickListener { status.text = "PAUSED • detector prototype" } })
        box.addView(Button(this).apply { text = "TEST TAP"; setOnClickListener { val d=resources.displayMetrics; TapAccessibilityService.instance?.tap(d.widthPixels*0.89f,d.heightPixels*0.50f) } })
        box.addView(Button(this).apply { text = "CLOSE"; setOnClickListener { stopSelf() } })
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.END; x=12; y=180 }
        wm.addView(box,params); panel=box
    }
    override fun onDestroy() { panel?.let { wm.removeView(it) }; panel=null; super.onDestroy() }
}
