package com.example.faul_uppu_yt

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.view.ContextThemeWrapper
import kotlin.math.max
import kotlin.math.roundToInt

class FloatingBrowserService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var browserView: View
    private lateinit var webView: WebView
    private lateinit var dragHandle: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var sharedPreferences: SharedPreferences

    private val touchToggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.faul_uppu_yt.TOGGLE_BROWSER_TOUCH") {
                val isLocked = intent.getBooleanExtra("lock_status", false)
                toggleTouch(isLocked)
            }
        }
    }

    private val visibilityToggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.faul_uppu_yt.TOGGLE_BROWSER_VISIBILITY") {
                val shouldBeVisible = intent.getBooleanExtra("is_visible", true)
                browserView.visibility = if (shouldBeVisible) View.VISIBLE else View.GONE
                isBrowserVisible = shouldBeVisible
            }
        }
    }

    companion object {
        var isServiceRunning = false
        var isBrowserVisible = true
        const val EXTRA_URL = "extra_url"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        isBrowserVisible = true
        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        val themedContext = ContextThemeWrapper(this, R.style.Theme_FauL_UppU_YT)
        browserView = LayoutInflater.from(themedContext).inflate(R.layout.floating_browser_layout, null)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        webView = browserView.findViewById(R.id.floating_webview)
        dragHandle = browserView.findViewById(R.id.drag_handle)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // --- THIS IS THE FIX ---
        // Load the last saved position and size, or use defaults.
        params = WindowManager.LayoutParams(
            sharedPreferences.getInt("BROWSER_WIDTH", 1500),
            sharedPreferences.getInt("BROWSER_HEIGHT", 1000),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = sharedPreferences.getInt("BROWSER_X", 100)
        params.y = sharedPreferences.getInt("BROWSER_Y", 100)

        windowManager.addView(browserView, params)
        setupWebView()
        setupTouchListeners()
        scaleGestureDetector = ScaleGestureDetector(themedContext, ScaleListener())

        val touchFilter = IntentFilter("com.example.faul_uppu_yt.TOGGLE_BROWSER_TOUCH")
        val visibilityFilter = IntentFilter("com.example.faul_uppu_yt.TOGGLE_BROWSER_VISIBILITY")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(touchToggleReceiver, touchFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(visibilityToggleReceiver, visibilityFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(touchToggleReceiver, touchFilter)
            registerReceiver(visibilityToggleReceiver, visibilityFilter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_URL)?.let {
            webView.loadUrl(it)
        }
        return START_NOT_STICKY
    }

    private fun setupWebView() {
        webView.webViewClient = WebViewClient()
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListeners() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        browserView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        windowManager.updateViewLayout(browserView, params)
                        webView.requestFocus()
                    }
                    if (event.y <= dragHandle.height) {
                        isDragging = true
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging && !scaleGestureDetector.isInProgress) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(browserView, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if(isDragging) {
                        saveBrowserState() // Save position after dragging
                    }
                    isDragging = false
                }
            }

            if (!isDragging && !scaleGestureDetector.isInProgress) {
                return@setOnTouchListener webView.onTouchEvent(event)
            }

            return@setOnTouchListener true
        }
    }

    private fun toggleTouch(isLocked: Boolean) {
        if (isLocked) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        windowManager.updateViewLayout(browserView, params)
    }

    // --- NEW FUNCTION ---
    // Saves the current position and size of the browser window.
    private fun saveBrowserState() {
        val editor = sharedPreferences.edit()
        editor.putInt("BROWSER_X", params.x)
        editor.putInt("BROWSER_Y", params.y)
        editor.putInt("BROWSER_WIDTH", params.width)
        editor.putInt("BROWSER_HEIGHT", params.height)
        editor.apply()
    }


    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var aspectRatio: Float = 0f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            aspectRatio = params.width.toFloat() / params.height.toFloat()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newWidth = max(300, (params.width * detector.scaleFactor).roundToInt())
            val newHeight = (newWidth / aspectRatio).roundToInt()
            params.width = newWidth
            params.height = newHeight
            windowManager.updateViewLayout(browserView, params)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            saveBrowserState() // Save size after resizing
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isBrowserVisible = true
        unregisterReceiver(touchToggleReceiver)
        unregisterReceiver(visibilityToggleReceiver)
        if (::browserView.isInitialized && browserView.isAttachedToWindow) {
            windowManager.removeView(browserView)
        }
    }
}

