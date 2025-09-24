package com.example.faul_uppu_yt

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        var isServiceRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: FrameLayout
    private lateinit var imageView: ImageView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null) as FrameLayout
        imageView = overlayView.findViewById(R.id.overlayImage)

        setupWindowParameters()

        windowManager.addView(overlayView, params)
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
        setupTouchListener()

        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("image_uri")?.let {
            val imageUri = Uri.parse(it)
            loadImageIntoOverlay(imageUri)
        }
        return START_NOT_STICKY
    }

    private fun setupWindowParameters() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val lastX = prefs.getInt("OVERLAY_X", 0)
        val lastY = prefs.getInt("OVERLAY_Y", 100)
        val lastWidth = prefs.getInt("OVERLAY_WIDTH", WindowManager.LayoutParams.WRAP_CONTENT)
        val lastHeight = prefs.getInt("OVERLAY_HEIGHT", WindowManager.LayoutParams.WRAP_CONTENT)

        params = WindowManager.LayoutParams(
            lastWidth,
            lastHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = lastX
            this.y = lastY
        }

        if (lastWidth != WindowManager.LayoutParams.WRAP_CONTENT) {
            val imageLayout = imageView.layoutParams
            imageLayout.width = lastWidth
            imageLayout.height = lastHeight
            imageView.layoutParams = imageLayout
        }
    }

    private fun loadImageIntoOverlay(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean { return false }
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    if (params.width == WindowManager.LayoutParams.WRAP_CONTENT) {
                        val aspectRatio = resource.intrinsicWidth.toFloat() / resource.intrinsicHeight.toFloat()
                        val initialWidth = (resources.displayMetrics.density * 200).toInt()
                        val initialHeight = (initialWidth / aspectRatio).toInt()
                        updateOverlaySize(initialWidth, initialHeight)
                        saveOverlayState()
                    }
                    return false
                }
            })
            .into(imageView)
    }

    private fun saveOverlayState() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.edit().apply {
            putInt("OVERLAY_X", params.x)
            putInt("OVERLAY_Y", params.y)
            putInt("OVERLAY_WIDTH", params.width)
            putInt("OVERLAY_HEIGHT", params.height)
            apply()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        overlayView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (!scaleGestureDetector.isInProgress) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                    }
                    MotionEvent.ACTION_UP -> {
                        saveOverlayState()
                    }
                }
            }
            true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newWidth = (params.width * scaleFactor).roundToInt()
            val newHeight = (params.height * scaleFactor).roundToInt()

            if (newWidth > 50 && newHeight > 50) {
                updateOverlaySize(newWidth, newHeight)
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            saveOverlayState()
        }
    }

    private fun updateOverlaySize(width: Int, height: Int) {
        params.width = width
        params.height = height
        windowManager.updateViewLayout(overlayView, params)
        imageView.layoutParams.width = width
        imageView.layoutParams.height = height
        imageView.requestLayout()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "overlay_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Floating Overlay Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating Overlay Active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(101, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }
    }
}