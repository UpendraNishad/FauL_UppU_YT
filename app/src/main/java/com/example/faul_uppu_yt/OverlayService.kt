package com.example.faul_uppu_yt

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
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
    private var hasSavedSize: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true

        // Ensure overlay permission granted
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_layout, null) as FrameLayout
        imageView = overlayView.findViewById(R.id.overlayImage)

        setupWindowParameters()
        windowManager.addView(overlayView, params)

        // Self-heal: if a position saved from a previous session (or a previous
        // buggy build) ends up off-screen, snap it back into view once the
        // overlay has actually been laid out and we know its real size.
        overlayView.post { clampToScreen() }

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastX
            y = lastY

            // Without this, the system clamps the window's vertical position to
            // avoid the status bar / cutout / nav bar — even with
            // FLAG_LAYOUT_NO_LIMITS — which is why dragging could reach the true
            // left/right edges but stopped short of the true top/bottom edges.
            // There's no equivalent horizontal system bar on a phone, so X was
            // never affected the same way.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        @Suppress("DEPRECATION")
        overlayView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )

        if (lastWidth != WindowManager.LayoutParams.WRAP_CONTENT) {
            hasSavedSize = true
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
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
                ): Boolean = false

                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    // Only apply the default size the first time an image/gif is loaded.
                    // If a custom size was already saved (user resized it before), keep it
                    // instead of resetting back to the default on every toggle on/off.
                    if (!hasSavedSize) {
                        val aspectRatio = resource.intrinsicWidth.toFloat() / resource.intrinsicHeight.toFloat()
                        val newWidth = (resources.displayMetrics.density * 200).toInt()
                        val newHeight = (newWidth / aspectRatio).toInt()
                        updateOverlaySize(newWidth, newHeight)
                        saveOverlayState()
                        hasSavedSize = true
                    }
                    return false
                }
            })
            .into(imageView)
    }

    /**
     * The window's x/y for a TYPE_APPLICATION_OVERLAY with FLAG_LAYOUT_NO_LIMITS is
     * expressed in *real* raw display pixels (the full physical screen, including
     * the area under the status bar / nav bar / cutout) — not the possibly-smaller
     * "current app window" size that resources.displayMetrics can report. Using the
     * wrong size here is what made dragging feel like it hit invisible walls before
     * actually reaching the screen edges.
     */
    private fun getRealScreenSize(): Pair<Int, Int> {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * Minimum strip of the overlay (in px) that must always stay on-screen so it
     * can be grabbed and dragged back. Roughly a comfortable touch-target size.
     */
    private fun minVisiblePx(): Int = (48 * resources.displayMetrics.density).toInt()

    /**
     * Height of the on-screen navigation bar (back/home/recents), in px. The nav
     * bar is drawn by a system layer that intercepts touches for its own buttons —
     * so if the overlay's touchable strip sits inside that zone, taps hit the nav
     * buttons instead of the overlay, making the image effectively untouchable.
     * Returns 0 on gesture-nav devices where there's no dedicated button bar.
     */
    private fun navigationBarHeightPx(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    /**
     * Requiring the WHOLE image to stay on-screen (as a first pass did) means the
     * usable drag range shrinks as the image grows — a big image can't get its
     * corner near an edge and still fit, which feels like invisible walls. Instead,
     * only a small fixed strip of the view is required to stay visible; the rest is
     * free to hang off any edge. This keeps dragging free across the whole screen
     * at any image size, while still guaranteeing it can never be dragged fully off
     * and lost. The bottom bound is additionally pulled up above the navigation
     * bar so the visible strip never lands in the nav buttons' dead zone.
     */
    private fun coerceToScreen(x: Int, y: Int): Pair<Int, Int> {
        val (screenWidth, screenHeight) = getRealScreenSize()
        val viewWidth = overlayView.width
        val viewHeight = overlayView.height
        val navBarHeight = navigationBarHeightPx()

        val minVisibleX = minOf(minVisiblePx(), viewWidth)
        val minVisibleY = minOf(minVisiblePx(), viewHeight)

        val minX = -(viewWidth - minVisibleX)
        val maxX = screenWidth - minVisibleX
        val minY = -(viewHeight - minVisibleY)
        val maxY = (screenHeight - minVisibleY - navBarHeight).coerceAtLeast(minY)

        return x.coerceIn(minX, maxX) to y.coerceIn(minY, maxY)
    }

    private fun clampToScreen() {
        if (overlayView.width == 0 || overlayView.height == 0) return

        val (clampedX, clampedY) = coerceToScreen(params.x, params.y)
        if (clampedX != params.x || clampedY != params.y) {
            params.x = clampedX
            params.y = clampedY
            windowManager.updateViewLayout(overlayView, params)
            saveOverlayState()
        }
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
                        val newX = initialX + (event.rawX - initialTouchX).toInt()
                        val newY = initialY + (event.rawY - initialTouchY).toInt()

                        val (clampedX, clampedY) = coerceToScreen(newX, newY)
                        params.x = clampedX
                        params.y = clampedY
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
            clampToScreen()
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
        val channelName = "Overlay Service"

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }
        }

        // Build the notification before using it!
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Faul Uppu YT")
            .setContentText("Overlay Service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        // Android 14+ requires explicit foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                101,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(101, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }
    }
}