package com.barsense.game

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private var status: TextView? = null
    private var autoButton: Button? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            status?.text = "BarSense • ${BarSenseState.status}\nFrames: ${BarSenseState.framesAnalyzed}  Jumps: ${BarSenseState.tapCount}  Confidence: ${BarSenseState.confidence}%"
            autoButton?.text = if (BarSenseState.autoEnabled) "AUTO: ON  •  STOP" else "AUTO: OFF  •  START"
            handler.postDelayed(this, 350)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (panel == null) createPanel()
        return START_STICKY
    }

    private fun createPanel() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(0xEE17172B.toInt())
        }
        status = TextView(this).apply {
            text = "BarSense • READY"
            setTextColor(-1)
            textSize = 12f
        }
        box.addView(status)
        autoButton = Button(this).apply {
            text = "AUTO: OFF • START"
            setOnClickListener {
                BarSenseState.autoEnabled = !BarSenseState.autoEnabled
                text = if (BarSenseState.autoEnabled) "AUTO: ON • STOP" else "AUTO: OFF • START"
                status?.text = if (BarSenseState.autoEnabled) "Auto armed • watching screen" else "Auto stopped"
            }
        }
        box.addView(autoButton)
        box.addView(Button(this).apply {
            text = "TEST TAP"
            setOnClickListener {
                val d = resources.displayMetrics
                TapAccessibilityService.instance?.tap(d.widthPixels * BarSenseState.tapTargetX, d.heightPixels * BarSenseState.tapTargetY)
            }
        })
        box.addView(Button(this).apply {
            text = "CLOSE"
            setOnClickListener {
                BarSenseState.autoEnabled = false
                stopSelf()
            }
        })
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 12; y = 180 }
        wm.addView(box, params)
        panel = box
        handler.post(refresh)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        BarSenseState.autoEnabled = false
        panel?.let { wm.removeView(it) }
        panel = null
        super.onDestroy()
    }
}
