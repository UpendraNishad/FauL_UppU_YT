package com.example.faul_uppu_yt

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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

    companion object {
        var isServiceRunning = false
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
        windowManager.addView(menuView, menuParams)
        windowManager.addView(bubbleView, bubbleParams)

        setupBubbleTouchListener()
        setupMenuControls()
        startForegroundServiceNotification()
    }

    private fun setupWindowParameters() {
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val lastBubbleX = prefs.getInt("BUBBLE_X", 0)
        val lastBubbleY = prefs.getInt("BUBBLE_Y", 100)

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastBubbleX
            y = lastBubbleY
        }

        // --- THIS IS THE FIX ---
        // Convert your desired dp values to pixels for the WindowManager
        val widthInDp = 240
        val heightInDp = 280
        val metrics = resources.displayMetrics
        val widthInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthInDp.toFloat(), metrics).toInt()
        val heightInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightInDp.toFloat(), metrics).toInt()

        menuParams = WindowManager.LayoutParams(
            widthInPixels,  // Use the fixed pixel width
            heightInPixels, // Use the fixed pixel height
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
            // Sync all controls before showing
            menuView.findViewById<SwitchCompat>(R.id.switch_web_alert).isChecked = AlertService.isServiceRunning
            menuView.findViewById<SwitchCompat>(R.id.switch_image_overlay).isChecked = OverlayService.isServiceRunning
            menuView.findViewById<SwitchCompat>(R.id.switch_mic_mute).isChecked = audioManager.isMicrophoneMute

            val volumeSeekBar = menuView.findViewById<SeekBar>(R.id.seekBar_volume)
            volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            val brightnessSeekBar = menuView.findViewById<SeekBar>(R.id.seekBar_brightness)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                brightnessSeekBar.progress = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            }

            menuView.visibility = View.VISIBLE
        }
    }

    private fun setupMenuControls() {
        val imageOverlaySwitch = menuView.findViewById<SwitchCompat>(R.id.switch_image_overlay)
        val webAlertSwitch = menuView.findViewById<SwitchCompat>(R.id.switch_web_alert)
        val micMuteSwitch = menuView.findViewById<SwitchCompat>(R.id.switch_mic_mute)
        val closeBubbleButton = menuView.findViewById<Button>(R.id.btn_close_bubble)
        val brightnessSeekBar = menuView.findViewById<SeekBar>(R.id.seekBar_brightness)
        val volumeSeekBar = menuView.findViewById<SeekBar>(R.id.seekBar_volume)

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
                    Toast.makeText(this, "Set a URL in the main app first.", Toast.LENGTH_SHORT).show()
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

        micMuteSwitch.setOnCheckedChangeListener { _, isChecked ->
            try {
                audioManager.isMicrophoneMute = isChecked
                if (isChecked) {
                    Toast.makeText(this, "Microphone MUTED", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Microphone UNMUTED", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Toast.makeText(this, "Error: Permission to modify audio settings denied.", Toast.LENGTH_LONG).show()
                Log.e("MenuService", "Mic Mute SecurityException", e)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
            brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } else {
            brightnessSeekBar.isEnabled = false
        }

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.max = maxVolume

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
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
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
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

        try {
            if(audioManager.isMicrophoneMute) {
                audioManager.isMicrophoneMute = false
                Toast.makeText(this, "Microphone UNMUTED automatically.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Log.e("MenuService", "Could not unmute mic on destroy", e)
        }

        stopService(Intent(this, OverlayService::class.java))
        stopService(Intent(this, AlertService::class.java))

        if (::bubbleView.isInitialized && bubbleView.isAttachedToWindow) windowManager.removeView(bubbleView)
        if (::menuView.isInitialized && menuView.isAttachedToWindow) windowManager.removeView(menuView)
    }
}
