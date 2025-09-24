package com.example.faul_uppu_yt

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationCompat

class MenuService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var menuView: View

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var menuParams: WindowManager.LayoutParams

    companion object {
        var isServiceRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val themedContext = ContextThemeWrapper(this, R.style.Theme_FauL_UppU_YT)
        val inflater = LayoutInflater.from(themedContext)

        bubbleView = inflater.inflate(R.layout.bubble_layout, null)
        menuView = inflater.inflate(R.layout.menu_layout, null)

        setupWindowParameters()

        windowManager.addView(bubbleView, bubbleParams)
        menuView.visibility = View.GONE
        windowManager.addView(menuView, menuParams)

        setupBubbleTouchListener()
        setupMenuControls()
        startForegroundServiceNotification()
    }

    private fun setupWindowParameters() {
        // Read saved positions for the bubble
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val lastBubbleX = prefs.getInt("BUBBLE_X", 0)       // Default to 0 if not found
        val lastBubbleY = prefs.getInt("BUBBLE_Y", 100)     // Default to 100 if not found

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastBubbleX  // Use the saved X position
            y = lastBubbleY  // Use the saved Y position
        }

        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun toggleMenuVisibility() {
        if (menuView.visibility == View.VISIBLE) {
            menuView.visibility = View.GONE
        } else {
            // Sync switch states before showing
            menuView.findViewById<SwitchCompat>(R.id.switch_web_alert).isChecked = AlertService.isServiceRunning
            menuView.findViewById<SwitchCompat>(R.id.switch_image_overlay).isChecked = OverlayService.isServiceRunning
            menuView.visibility = View.VISIBLE
        }
    }

    private fun setupMenuControls() {
        val imageOverlaySwitch = menuView.findViewById<SwitchCompat>(R.id.switch_image_overlay)
        val webAlertSwitch = menuView.findViewById<SwitchCompat>(R.id.switch_web_alert)
        val closeBubbleButton = menuView.findViewById<Button>(R.id.btn_close_bubble)

        imageOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                val uriString = prefs.getString("LAST_IMAGE_URI", null)

                if (uriString != null) {
                    val intent = Intent(this, OverlayService::class.java).apply {
                        putExtra("image_uri", uriString)
                    }
                    startService(intent)
                } else {
                    Toast.makeText(this, "Select an image from the main app first!", Toast.LENGTH_LONG).show()
                    imageOverlaySwitch.isChecked = false
                }
            } else {
                stopService(Intent(this, OverlayService::class.java))
            }
        }

        webAlertSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                val url = prefs.getString("URL", "")
                if (url.isNullOrEmpty()) {
                    webAlertSwitch.isChecked = false
                    return@setOnCheckedChangeListener
                }
                val intent = Intent(this, AlertService::class.java).apply {
                    putExtra("url", url)
                    putExtra("width", prefs.getInt("WIDTH", 400))
                    putExtra("height", prefs.getInt("HEIGHT", 300))
                    putExtra("x", prefs.getInt("X_POS", 50))
                    putExtra("y", prefs.getInt("Y_POS", 100))
                }
                startService(intent)
            } else {
                stopService(Intent(this, AlertService::class.java))
            }
        }

        closeBubbleButton.setOnClickListener {
            stopSelf()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubbleTouchListener() {
        var initialX: Int = 0
        var initialY: Int = 0
        var initialTouchX: Float = 0f
        var initialTouchY: Float = 0f

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    // Save the position when the user lets go
                    val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                    prefs.edit().apply {
                        putInt("BUBBLE_X", bubbleParams.x)
                        putInt("BUBBLE_Y", bubbleParams.y)
                        apply()
                    }

                    val isClick = kotlin.math.abs(event.rawX - initialTouchX) < 10 && kotlin.math.abs(event.rawY - initialTouchY) < 10
                    if (isClick) {
                        toggleMenuVisibility()
                    }
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_MOVE -> {
                    bubbleParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "menu_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channelId, "Floating Menu Service", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("FauL UppU Controls Active")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .build()
        startForeground(103, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        stopService(Intent(this, OverlayService::class.java))
        stopService(Intent(this, AlertService::class.java))

        if (::bubbleView.isInitialized && bubbleView.isAttachedToWindow) windowManager.removeView(bubbleView)
        if (::menuView.isInitialized && menuView.isAttachedToWindow) windowManager.removeView(menuView)
    }
}