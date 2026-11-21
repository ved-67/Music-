package com.barsense.game
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val captureRequest = 41
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,40,28,24) }
        root.addView(TextView(this).apply { text = "BarSense\nGame-specific overlay prototype\n\n1. Grant overlay permission.\n2. Enable BarSense in Accessibility settings.\n3. Start screen capture.\n4. Open the game and use the floating panel.\n\nAutomatic play is not enabled yet: egg appearance changes need robust tracking and calibration."; textSize = 17f })
        root.addView(Button(this).apply { text = "Grant overlay permission"; setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } })
        root.addView(Button(this).apply { text = "Enable accessibility service"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        root.addView(Button(this).apply { text = "Show floating panel"; setOnClickListener { if (Settings.canDrawOverlays(this@MainActivity)) startService(Intent(this@MainActivity, OverlayService::class.java)) else startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } })
        root.addView(Button(this).apply { text = "Start screen capture"; setOnClickListener { val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager; startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest) } })
        setContentView(root)
    }
    @Deprecated("Compatibility callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) startForegroundService(Intent(this, CaptureService::class.java).putExtra("resultCode", resultCode).putExtra("data", data))
    }
}
