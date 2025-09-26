package com.example.faul_uppu_yt

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.security.MessageDigest
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var loadingView: ProgressBar
    private lateinit var mainContent: LinearLayout
    private lateinit var activationContent: LinearLayout
    private val db = Firebase.firestore

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }

            val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
            prefs.edit().putString("LAST_IMAGE_URI", it.toString()).apply()
            Toast.makeText(this, "Image saved for quick toggle!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadingView = findViewById(R.id.loading_view)
        mainContent = findViewById(R.id.main_content)
        activationContent = findViewById(R.id.activation_content)

        checkLicense()
    }

    private fun checkLicense() {
        showLoading()
        val deviceId = getUniqueDeviceId()
        val licenseDocRef = db.collection("licenses").document(deviceId)

        licenseDocRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val status = document.getString("status")
                    val expiryDate = document.getDate("expiryDate")

                    if (status == "ACTIVE") {
                        if (expiryDate == null || expiryDate.after(Date())) {
                            showMainContent()
                        } else {
                            Toast.makeText(this, "Your license has expired.", Toast.LENGTH_LONG).show()
                            showActivationScreen(deviceId)
                        }
                    } else {
                        Toast.makeText(this, "Your license has been revoked.", Toast.LENGTH_LONG).show()
                        showActivationScreen(deviceId)
                    }
                } else {
                    showActivationScreen(deviceId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: Could not verify license. Check internet.", Toast.LENGTH_LONG).show()
                showActivationScreen(deviceId)
            }
    }

    private fun activateLicense(key: String) {
        val deviceId = getUniqueDeviceId()
        showLoading()

        if (verifyAndDecodeKey(key, deviceId)) {
            val isPermanent = key.startsWith("PERM-")

            val expiryDate: Date? = if (isPermanent) {
                null
            } else {
                val days = key.substringBefore("D-").toIntOrNull()
                if (days != null) {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, days)
                    calendar.time
                } else {
                    Toast.makeText(this, "Invalid license key format.", Toast.LENGTH_SHORT).show()
                    showActivationScreen(deviceId)
                    return
                }
            }

            val licenseData = hashMapOf(
                "status" to "ACTIVE",
                "expiryDate" to expiryDate,
                "activatedOn" to FieldValue.serverTimestamp()
            )

            db.collection("licenses").document(deviceId)
                .set(licenseData)
                .addOnSuccessListener {
                    Toast.makeText(this, "License Activated Successfully!", Toast.LENGTH_SHORT).show()
                    showMainContent()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Activation failed. Please try again.", Toast.LENGTH_SHORT).show()
                    showActivationScreen(deviceId)
                }
        } else {
            Toast.makeText(this, "Invalid License Key.", Toast.LENGTH_SHORT).show()
            showActivationScreen(deviceId)
        }
    }

    private fun setupMainContent() {
        setTitle("  FauL UppU YT")
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        supportActionBar?.setLogo(getScaledLogo(R.mipmap.ic_launcher_round))

        checkOverlayPermission()
        checkWriteSettingsPermission()

        val urlEditText = findViewById<EditText>(R.id.et_url)
        val widthEditText = findViewById<EditText>(R.id.et_width)
        val heightEditText = findViewById<EditText>(R.id.et_height)
        val xEditText = findViewById<EditText>(R.id.et_x) // Re-added
        val yEditText = findViewById<EditText>(R.id.et_y) // Re-added

        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        urlEditText.setText(prefs.getString("URL", ""))
        widthEditText.setText(prefs.getInt("WIDTH", 400).toString())
        heightEditText.setText(prefs.getInt("HEIGHT", 300).toString())
        xEditText.setText(prefs.getInt("X_POS", 50).toString()) // Re-added
        yEditText.setText(prefs.getInt("Y_POS", 100).toString()) // Re-added

        findViewById<Button>(R.id.btn_launch_menu).setOnClickListener {
            if (!MenuService.isServiceRunning) {
                startService(Intent(this, MenuService::class.java))
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
                val x = xEditText.text.toString().toIntOrNull() ?: 50 // Re-added
                val y = yEditText.text.toString().toIntOrNull() ?: 100 // Re-added

                editor.putString("URL", url)
                editor.putInt("WIDTH", width)
                editor.putInt("HEIGHT", height)
                editor.putInt("X_POS", x) // Re-added
                editor.putInt("Y_POS", y) // Re-added
                editor.apply()
                Toast.makeText(this, "Subscriber Alert Settings Saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a URL first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading() {
        loadingView.visibility = View.VISIBLE
        mainContent.visibility = View.GONE
        activationContent.visibility = View.GONE
    }

    private fun showMainContent() {
        loadingView.visibility = View.GONE
        mainContent.visibility = View.VISIBLE
        activationContent.visibility = View.GONE
        setupMainContent()
    }

    private fun showActivationScreen(deviceId: String) {
        loadingView.visibility = View.GONE
        mainContent.visibility = View.GONE
        activationContent.visibility = View.VISIBLE

        val deviceIdTextView = findViewById<TextView>(R.id.tv_device_id)
        val activationKeyEditText = findViewById<EditText>(R.id.et_activation_key)
        val activateButton = findViewById<Button>(R.id.btn_activate)
        val copyButton = findViewById<Button>(R.id.btn_copy_id)

        deviceIdTextView.text = deviceId

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Device ID", deviceId)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Device ID Copied!", Toast.LENGTH_SHORT).show()
        }

        activateButton.setOnClickListener {
            val key = activationKeyEditText.text.toString().trim()
            if (key.isNotEmpty()) {
                activateLicense(key)
            } else {
                Toast.makeText(this, "Please enter an activation key.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("HardwareIds")
    private fun getUniqueDeviceId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun verifyAndDecodeKey(key: String, deviceId: String): Boolean {
        val parts = key.split("-")
        if (parts.size != 2) return false

        val durationPrefix = parts[0]
        val receivedHash = parts[1]

        val secretKey = "MyFauLAppIsAwesome2025!" // Example: Replace with your own key
        val salt = "SomeRandomSaltValueForSecurity" // Example: Replace with your own salt

        val dataToHash = "$deviceId-$durationPrefix-$salt"

        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(dataToHash.toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }
            .substring(0, 12)

        return receivedHash == expectedHash
    }

    private fun getScaledLogo(drawableId: Int): Drawable {
        val drawable = ContextCompat.getDrawable(this, drawableId)
        val desiredHeightDp = 40
        val desiredWidthDp = 40
        val density = resources.displayMetrics.density
        val desiredWidth = (desiredWidthDp * density).toInt()
        val desiredHeight = (desiredHeightDp * density).toInt()

        val bitmap = Bitmap.createBitmap(desiredWidth, desiredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable?.setBounds(0, 0, canvas.width, canvas.height)
        drawable?.draw(canvas)
        return BitmapDrawable(resources, bitmap)
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun checkWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}

