package com.example.myapplication

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import android.Manifest

class MainActivity : AppCompatActivity() {

    private var isRunning = false
    private val OVERLAY_PERMISSION_REQUEST_CODE = 2
    private val REQUEST_FOREGROUND_SERVICE_PERMISSION = 123
    private var clickIndicatorView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerReceiver(clickIndicatorReceiver, IntentFilter("SHOW_CLICK_INDICATOR"), Context.RECEIVER_NOT_EXPORTED)

        val startButton = findViewById<Button>(R.id.start_button)
        startButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE), REQUEST_FOREGROUND_SERVICE_PERMISSION)
            } else {
                startAutoClickerService()
                toggleAutoClicker()
            }
        }

        requestOverlayPermission()
        requestAccessibilityPermission()
        isAccessibilityServiceEnabled()
        updateButtonText()
        requestStoragePermission()
        requestManageExternalStoragePermission()
        isRunning = isServiceRunning(AutoClickerService::class.java)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_FOREGROUND_SERVICE_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                startAutoClickerService()
            } else {
                Toast.makeText(this, "Permission required to start the service", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startAutoClickerService() {
        val intent = Intent(this, AutoClickerService::class.java).apply {
            action = "START"
        }
        startForegroundService(intent)
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

    private val clickIndicatorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SHOW_CLICK_INDICATOR") {
                val x = intent.getFloatExtra("CLICK_X", 0f)
                val y = intent.getFloatExtra("CLICK_Y", 0f)
                showClickIndicatorOnScreen(x, y)
            }
        }
    }

    private fun showClickIndicatorOnScreen(x: Float, y: Float) {
        if (clickIndicatorView == null) {
            clickIndicatorView = View(this).apply {
                setBackgroundResource(R.drawable.click_indicator)
            }
        }

        val size = 48 // Size of the indicator in pixels
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.x = (x - size / 2).toInt()
        params.y = (y - size / 2).toInt()

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(clickIndicatorView, params)

        // Remove the indicator after a short time
        Handler(Looper.getMainLooper()).postDelayed({
            windowManager.removeView(clickIndicatorView)
        }, 300) // 300ms = 0.3 seconds
    }

    private fun requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
        }
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
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(clickIndicatorReceiver)
    }
}