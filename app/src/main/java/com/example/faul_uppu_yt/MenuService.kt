package com.example.faul_uppu_yt // <-- THIS LINE IS NOW CORRECT

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat

class MenuService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var menuView: View
    private var isMenuVisible = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val parent = FrameLayout(this) // Create a temporary parent
        bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_layout, parent, false)
        menuView = LayoutInflater.from(this).inflate(R.layout.menu_layout, parent, false)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        bubbleParams.gravity = Gravity.TOP or Gravity.START
        bubbleParams.x = 0
        bubbleParams.y = 100

        windowManager.addView(bubbleView, bubbleParams)

        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleParams.x
                        initialY = bubbleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        bubbleParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                            toggleMenu()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun toggleMenu() {
        if (!isMenuVisible) {
            val menuParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            val bubbleParams = bubbleView.layoutParams as WindowManager.LayoutParams
            menuParams.gravity = Gravity.TOP or Gravity.START
            menuParams.x = bubbleParams.x + bubbleView.width
            menuParams.y = bubbleParams.y
            windowManager.addView(menuView, menuParams)

            // Setup menu item clicks
            menuView.findViewById<ImageView>(R.id.imageOverlayButton).setOnClickListener {
                // Handle image overlay button click
                toggleMenu() // Close menu after action
            }
            menuView.findViewById<ImageView>(R.id.subscribeAlertButton).setOnClickListener {
                // Handle subscribe alert button click
                toggleMenu()
            }
            menuView.findViewById<ImageView>(R.id.closeBubbleButton).setOnClickListener {
                stopSelf() // Stop the service to close everything
            }

        } else {
            windowManager.removeView(menuView)
        }
        isMenuVisible = !isMenuVisible
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(2, createNotification())
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "MenuServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Menu Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating Menu")
            .setContentText("Menu is running.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bubbleView.isInitialized) windowManager.removeView(bubbleView)
        if (::menuView.isInitialized && isMenuVisible) windowManager.removeView(menuView)
    }
}