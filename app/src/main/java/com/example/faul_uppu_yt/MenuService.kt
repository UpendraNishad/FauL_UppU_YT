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
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
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
    private lateinit var audioManager: AudioManager

    private var overlaysLikelyCovered = false

    private val bubbleVisibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.faul_uppu_yt.TOGGLE_BUBBLE_VISIBILITY") {
                val shouldBeVisible = intent.getBooleanExtra("is_visible", true)
                setBubbleVisibility(shouldBeVisible)
            }
        }
    }

    companion object {
        var isServiceRunning = false
        const val PREF_BUBBLE_VISIBLE = "PREF_BUBBLE_VISIBLE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val themedContext = ContextThemeWrapper(this, R.style.Theme_FauL_UppU_YT)
        val inflater = LayoutInflater.from(themedContext)
        bubbleView = inflater.inflate(R.layout.bubble_layout, null)
        menuView = inflater.inflate(R.layout.menu_layout, null)
        setupWindowParameters()
        menuView.visibility = View.GONE

        // Add menu and then bubble (bubble always on top)
        if (!menuView.isAttachedToWindow) windowManager.addView(menuView, menuParams)
        if (!bubbleView.isAttachedToWindow) windowManager.addView(bubbleView, bubbleParams)

        setupBubbleTouchListener()
        setupMenuControls()
        registerBubbleVisibilityReceiver()
        startForegroundServiceNotification()

        // Restore last saved bubble visibility state
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        setBubbleVisibility(prefs.getBoolean(PREF_BUBBLE_VISIBLE, true))
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerBubbleVisibilityReceiver() {
        val filter = IntentFilter("com.example.faul_uppu_yt.TOGGLE_BUBBLE_VISIBILITY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bubbleVisibilityReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bubbleVisibilityReceiver, filter)
        }
    }

    private fun setBubbleVisibility(visible: Boolean) {
        bubbleView.visibility = if (visible) View.VISIBLE else View.GONE
        // No bubble means no way to tap open the menu, so hide it too.
        if (!visible) menuView.visibility = View.GONE
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
            .putBoolean(PREF_BUBBLE_VISIBLE, visible)
            .apply()
    }

    // <-- THIS restores menu z-order above overlays after being hidden and re-shown.
    private fun resetMenuBubbleZOrder() {
        try { if (bubbleView.isAttachedToWindow) windowManager.removeView(bubbleView) } catch (_: Exception) {}
        try { if (menuView.isAttachedToWindow) windowManager.removeView(menuView) } catch (_: Exception) {}
        windowManager.addView(menuView, menuParams)
        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun setupWindowParameters() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val lastBubbleX = prefs.getInt("BUBBLE_X", 0)
        val lastBubbleY = prefs.getInt("BUBBLE_Y", 100)
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastBubbleX
            y = lastBubbleY
        }

        val widthInDp = 240
        val heightInDp = 320
        val metrics = resources.displayMetrics
        val widthInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthInDp.toFloat(), metrics).toInt()
        val heightInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightInDp.toFloat(), metrics).toInt()

        menuParams = WindowManager.LayoutParams(
            widthInPixels,
            heightInPixels,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun toggleMenuVisibility() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        if (menuView.visibility == View.VISIBLE) {
            menuView.visibility = View.GONE
        } else {
            menuView.findViewById<SwitchCompat>(R.id.switch_web_alert).isChecked = AlertService.isServiceRunning
            menuView.findViewById<SwitchCompat>(R.id.switch_image_overlay).isChecked = OverlayService.isServiceRunning
            menuView.findViewById<SwitchCompat>(R.id.switch_mic_mute).isChecked = audioManager.isMicrophoneMute

            menuView.findViewById<SwitchCompat>(R.id.switch_floating_browser).isChecked =
                FloatingBrowserService.isServiceRunning &&
                        prefs.getBoolean(FloatingBrowserService.PREF_BROWSER_VISIBLE, true)

            val volumeSeekBar = menuView.findViewById<SeekBar>(R.id.seekBar_volume)
            volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            val brightnessSeekBar = menuView.findViewById<SeekBar>(R.id.seekBar_brightness)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                brightnessSeekBar.progress = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            }

            menuView.visibility = View.VISIBLE

            // <-- Ensure menu is on top after overlays:
            if (overlaysLikelyCovered) {
                resetMenuBubbleZOrder()
                overlaysLikelyCovered = false
            }
        }
    }

    private fun setupMenuControls() {
        val imageOverlaySwitch = menuView.findViewById<SwitchCompat>(R.id.switch_image_overlay)
        val subscriberAlertSwitch = menuView.findViewById<SwitchCompat>(R.id.switch_web_alert)
        val liveSubscriberCountSwitch = menuView.findViewById<SwitchCompat>(R.id.switch_floating_browser)
        val micMuteSwitch = menuView.findViewById<SwitchCompat>(R.id.switch_mic_mute)
        val closeBubbleButton = menuView.findViewById<Button>(R.id.btn_close_bubble)
        val brightnessSeekBar = menuView.findViewById<SeekBar>(R.id.seekBar_brightness)
        val volumeSeekBar = menuView.findViewById<SeekBar>(R.id.seekBar_volume)

        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        liveSubscriberCountSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FloatingBrowserService.PREF_BROWSER_VISIBLE, isChecked).apply()

            val visibilityIntent = Intent("com.example.faul_uppu_yt.TOGGLE_BROWSER_VISIBILITY").apply {
                putExtra("is_visible", isChecked)
                setPackage(packageName)
            }
            sendBroadcast(visibilityIntent)
            overlaysLikelyCovered = isChecked

            if (isChecked && !FloatingBrowserService.isServiceRunning) {
                val url = prefs.getString("SUB_COUNT_URL", null)
                if (url != null) {
                    val browserIntent = Intent(this, FloatingBrowserService::class.java)
                    browserIntent.putExtra(FloatingBrowserService.EXTRA_URL, url)
                    startService(browserIntent)
                    Toast.makeText(this, "Live Subscriber Count Launched", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Set a subscriber URL first!", Toast.LENGTH_SHORT).show()
                    liveSubscriberCountSwitch.isChecked = false
                    prefs.edit().putBoolean(FloatingBrowserService.PREF_BROWSER_VISIBLE, false).apply()
                }
            }
        }

        imageOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val uriString = prefs.getString("LAST_IMAGE_URI", null)
                if (uriString != null) {
                    val intent = Intent(this, OverlayService::class.java).apply { putExtra("image_uri", uriString) }
                    startService(intent)
                    overlaysLikelyCovered = true
                } else {
                    Toast.makeText(this, "Select an image first!", Toast.LENGTH_LONG).show()
                    imageOverlaySwitch.isChecked = false
                }
            } else stopService(Intent(this, OverlayService::class.java))
        }

        subscriberAlertSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val url = prefs.getString("URL", "")
                if (url.isNullOrEmpty()) {
                    Toast.makeText(this, "Set a URL first.", Toast.LENGTH_SHORT).show()
                    subscriberAlertSwitch.isChecked = false
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
                overlaysLikelyCovered = true
            } else stopService(Intent(this, AlertService::class.java))
        }

        closeBubbleButton.setOnClickListener { stopSelf() }

        micMuteSwitch.setOnCheckedChangeListener { _, isChecked ->
            try {
                audioManager.isMicrophoneMute = isChecked
                Toast.makeText(this, if (isChecked) "Microphone MUTED" else "Microphone UNMUTED", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(this, "Permission denied for mic control.", Toast.LENGTH_LONG).show()
                Log.e("MenuService", "Mic Mute SecurityException", e)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
            brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } else brightnessSeekBar.isEnabled = false

        volumeSeekBar.max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubbleTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                }
                MotionEvent.ACTION_UP -> {
                    val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                    prefs.edit().apply {
                        putInt("BUBBLE_X", bubbleParams.x)
                        putInt("BUBBLE_Y", bubbleParams.y)
                        apply()
                    }
                    val isClick = kotlin.math.abs(event.rawX - initialTouchX) < 10 && kotlin.math.abs(event.rawY - initialTouchY) < 10
                    if (isClick) toggleMenuVisibility()
                }
                MotionEvent.ACTION_MOVE -> {
                    bubbleParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                }
            }
            true
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
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(103, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(103, notification)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        try {
            unregisterReceiver(bubbleVisibilityReceiver)
        } catch (_: Exception) {}
        try {
            if (audioManager.isMicrophoneMute) audioManager.isMicrophoneMute = false
        } catch (_: SecurityException) {}
        stopService(Intent(this, OverlayService::class.java))
        stopService(Intent(this, AlertService::class.java))
        if (::bubbleView.isInitialized && bubbleView.isAttachedToWindow) windowManager.removeView(bubbleView)
        if (::menuView.isInitialized && menuView.isAttachedToWindow) windowManager.removeView(menuView)
    }
}
