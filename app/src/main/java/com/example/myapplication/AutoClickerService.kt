package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class AutoClickerService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var clickerView: ImageView
    private var isClicking = false
    private var clickJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AutoClickerChannel"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createClickerView()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
                startClicking()
            }
            "STOP" -> stopClicking()
        }
        return START_NOT_STICKY
    }

    private fun createClickerView() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0

        clickerView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_mylocation)
            alpha = 0.5f
        }

        windowManager.addView(clickerView, params)
    }

    private fun startClicking() {
        if (!isClicking) {
            isClicking = true
            clickJob = CoroutineScope(Dispatchers.Default).launch {
                while (isActive) {
                    performClick()
                    delay(1000) // Click every second
                }
            }
        }
    }

    private fun stopClicking() {
        isClicking = false
        clickJob?.cancel()
        stopForeground(true)
        stopSelf()
    }

    private suspend fun performClick() {
        withContext(Dispatchers.Main) {
            val location = IntArray(2)
            clickerView.getLocationOnScreen(location)
            val x = location[0].toFloat()
            val y = location[1].toFloat()

            // Send click coordinates to AccessibilityService
            val intent = Intent("PERFORM_CLICK").apply {
                putExtra("x", x)
                putExtra("y", y)
            }
            sendBroadcast(intent)

            // Visual effect of the click
            clickerView.animate().scaleX(1.5f).scaleY(1.5f).setDuration(100).withEndAction {
                clickerView.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
        }
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Clicker")
            .setContentText("Auto Clicker is active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Clicker Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(clickerView)
        clickJob?.cancel()
    }
}