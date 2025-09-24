package com.example.faul_uppu_yt

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }

            // Save the chosen image URI so the menu can use it
            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            prefs.edit().putString("LAST_IMAGE_URI", it.toString()).apply()
            Toast.makeText(this, "Image saved for quick toggle!", Toast.LENGTH_SHORT).show()

            startOverlayWithUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTitle("FauL UppU YT")
        checkOverlayPermission()

        val urlEditText = findViewById<EditText>(R.id.et_url)
        val widthEditText = findViewById<EditText>(R.id.et_width)
        val heightEditText = findViewById<EditText>(R.id.et_height)
        val xEditText = findViewById<EditText>(R.id.et_x)
        val yEditText = findViewById<EditText>(R.id.et_y)

        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        urlEditText.setText(prefs.getString("URL", ""))
        widthEditText.setText(prefs.getInt("WIDTH", 400).toString())
        heightEditText.setText(prefs.getInt("HEIGHT", 300).toString())
        xEditText.setText(prefs.getInt("X_POS", 50).toString())
        yEditText.setText(prefs.getInt("Y_POS", 100).toString())

        findViewById<Button>(R.id.btn_launch_menu).setOnClickListener {
            if (!MenuService.isServiceRunning) {
                startService(Intent(this, MenuService::class.java))
                Toast.makeText(this, "Floating Menu Launched", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Menu is already running", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_select_start).setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*", "image/gif"))
        }

        findViewById<Button>(R.id.btn_stop_image_overlay).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Image overlay stopped", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_save_web_settings).setOnClickListener {
            val url = urlEditText.text.toString().trim()
            if (url.isNotEmpty()) {
                val editor = prefs.edit()

                val width = widthEditText.text.toString().toIntOrNull() ?: 400
                val height = heightEditText.text.toString().toIntOrNull() ?: 300
                val x = xEditText.text.toString().toIntOrNull() ?: 50
                val y = yEditText.text.toString().toIntOrNull() ?: 100

                editor.putString("URL", url)
                editor.putInt("WIDTH", width)
                editor.putInt("HEIGHT", height)
                editor.putInt("X_POS", x)
                editor.putInt("Y_POS", y)
                editor.apply()

                Toast.makeText(this, "Web Alert settings saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a URL first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startOverlayWithUri(uri: Uri) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("image_uri", uri.toString())
        }
        startService(intent)
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }
}