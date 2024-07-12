package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent

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
        startForeground(NOTIFICATION_ID, createNotification())
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startClicking()
            "STOP" -> stopClicking()
        }
        return START_STICKY
    }

    private fun createClickerView() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = screenWidth / 2
        params.y = screenHeight / 2

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
        //stopForeground(true)
        //stopSelf()
    }

    private suspend fun performClick() {
        withContext(Dispatchers.Main) {
            // Visual effect of the click
            clickerView.animate().scaleX(1.5f).scaleY(1.5f).setDuration(100).withEndAction {
                clickerView.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()

            // Trigger the actual click
            val intent = Intent("PERFORM_CLICK")
            sendBroadcast(intent)
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