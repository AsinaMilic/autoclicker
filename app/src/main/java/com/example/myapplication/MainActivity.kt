// MainActivity.kt
package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isRunning = false
    private val REQUEST_MEDIA_PROJECTION = 1
    private val OVERLAY_PERMISSION_REQUEST_CODE = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startButton = findViewById<Button>(R.id.start_button)
        startButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
            } else if (!isAccessibilityServiceEnabled()) {
                requestAccessibilityPermission()
            } else {
                toggleAutoClicker()
            }
        }

        isRunning = isServiceRunning(AutoClickerService::class.java)
        updateButtonText()
        requestStoragePermission()
    }

    private fun toggleAutoClicker() {
        if (isRunning) {
            stopAutoClicker()
        } else {
            startAutoClicker()
        }
    }

    private fun startAutoClicker() {
        val intent = Intent(this, AutoClickerService::class.java).apply {
            action = "START"
        }
        startForegroundService(intent)
        isRunning = true
        updateButtonText()
    }

    private fun stopAutoClicker() {
        val intent = Intent(this, AutoClickerService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
        isRunning = false
        updateButtonText()
    }

    private fun updateButtonText() {
        findViewById<Button>(R.id.start_button).text = if (isRunning) "Stop Auto-clicker" else "Start Auto-clicker"
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }

    private fun requestAccessibilityPermission() {
        Toast.makeText(this, "Please enable Accessibility Service for Auto-clicker", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityServiceName = packageName + "/" + AutoClickerAccessibilityService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(accessibilityServiceName) == true
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_MEDIA_PROJECTION -> if (resultCode == Activity.RESULT_OK && data != null) {
                val projectionIntent = data
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mediaProjection = projectionManager.getMediaProjection(resultCode, projectionIntent)
            }
            OVERLAY_PERMISSION_REQUEST_CODE -> if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
