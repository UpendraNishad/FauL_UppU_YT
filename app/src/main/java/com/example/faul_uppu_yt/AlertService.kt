package com.example.faul_uppu_yt // <-- THIS LINE IS NOW CORRECT

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat

class AlertService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var webView: WebView

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // Inflate the floating view
        val parent = FrameLayout(this)
        floatingView = LayoutInflater.from(this).inflate(R.layout.alert_layout, parent, false)
        webView = floatingView.findViewById(R.id.webView)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        val width = intent?.getIntExtra("width", 1080) ?: 1080
        val height = intent?.getIntExtra("height", 1920) ?: 1920
        val x = intent?.getIntExtra("x", 0) ?: 0
        val y = intent?.getIntExtra("y", 0) ?: 0

        val params = floatingView.layoutParams as WindowManager.LayoutParams
        params.width = width
        params.height = height
        params.x = x
        params.y = y
        windowManager.updateViewLayout(floatingView, params)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.loadUrl(url ?: "https://www.google.com")

        startForeground(1, createNotification())
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "AlertServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alert Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Alert Service")
            .setContentText("Displaying web alert.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}