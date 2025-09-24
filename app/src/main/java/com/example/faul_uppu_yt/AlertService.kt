package com.example.faul_uppu_yt

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

class AlertService : Service() {

    companion object {
        var isServiceRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var webOverlayView: View
    private lateinit var webView: WebView
    private var isReceiverRegistered = false

    private val alertTriggerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.faul_uppu_yt.SHOW_ALERT") {
                webView.reload()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        webOverlayView = LayoutInflater.from(this).inflate(R.layout.web_overlay_layout, null)
        webView = webOverlayView.findViewById(R.id.webView)

        webView.apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            setBackgroundColor(0x00000000)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            visibility = View.VISIBLE
        }

        if (!isReceiverRegistered) {
            val filter = IntentFilter("com.example.faul_uppu_yt.SHOW_ALERT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(alertTriggerReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(alertTriggerReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        url?.let { webView.loadUrl(it) }

        val width = intent?.getIntExtra("width", 400) ?: 400
        val height = intent?.getIntExtra("height", 300) ?: 300
        val x = intent?.getIntExtra("x", 50) ?: 50
        val y = intent?.getIntExtra("y", 100) ?: 100

        val params = WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        if (!webOverlayView.isAttachedToWindow) {
            windowManager.addView(webOverlayView, params)
        } else {
            windowManager.updateViewLayout(webOverlayView, params)
        }

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "alert_channel_faul")
            .setContentTitle("Alert Service Active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(102, notification)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (isReceiverRegistered) {
            try { unregisterReceiver(alertTriggerReceiver) } catch (e: Exception) { }
            isReceiverRegistered = false
        }
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }
        if (::webOverlayView.isInitialized && webOverlayView.isAttachedToWindow) {
            windowManager.removeView(webOverlayView)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("alert_channel_faul", "Alert Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}