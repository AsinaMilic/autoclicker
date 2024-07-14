package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class AutoClickerAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var clickIndicatorView: View
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AutoClickerService", "Service connected")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupClickIndicator()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AutoClickerService", "onStartCommand called")
        if (intent?.action == "PERFORM_CLICK") {
            val answerNumber = intent.getIntExtra("ANSWER_NUMBER", -1)
            Log.d("AutoClickerService", "Received click request for answer: $answerNumber")
            if (answerNumber in 1..4) {
                val (x, y) = calculateClickPosition(answerNumber)
                performClickAt(x, y, answerNumber)
            }
        }
        return START_NOT_STICKY
    }

    private fun setupClickIndicator() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        clickIndicatorView = inflater.inflate(R.layout.click_indicator, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        windowManager.addView(clickIndicatorView, params)
        clickIndicatorView.visibility = View.GONE
    }

    private fun calculateClickPosition(answerNumber: Int): Pair<Float, Float> {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()

        // Početak prvog dugmeta
        val startYPercentage = 0.6f // Y koordinata prvog dugmeta kao 55% visine ekrana
        val startY = screenHeight * startYPercentage

        // Razmak između dugmadi
        val yOffsetPercentage = 0.1f // Razmak između dugmadi kao 5% visine ekrana
        val yOffset = screenHeight * yOffsetPercentage

        // Klik na odgovarajuću y-koordinatu na osnovu answerNumber
        val y = startY + (answerNumber - 1) * yOffset

        // X koordinata može biti u sredini ekrana ili gde je potrebno
        val x = displayMetrics.widthPixels.toFloat() / 2

        Log.d("AutoClickerService", "Calculated click position: x=$x, y=$y")
        return Pair(x, y)
    }


    private fun performClickAt(x: Float, y: Float, answerNumber: Int) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gestureBuilder = GestureDescription.Builder().apply {
            addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        }

        val gesture = gestureBuilder.build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d("AutoClickerService", "Click performed at x=$x, y=$y")
                showClickIndicator(x, y)
                vibrateForAnswer(answerNumber)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e("AutoClickerService", "Click cancelled")
            }
        }, null)
    }

    private fun vibrateForAnswer(answerNumber: Int) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            val vibrationDuration = 200L // Duration of each vibration in milliseconds
            val pauseDuration = 200L // Duration of pause between vibrations

            val pattern = LongArray(answerNumber * 2) { index ->
                if (index % 2 == 0) vibrationDuration else pauseDuration
            }

            val vibrationEffect = VibrationEffect.createWaveform(pattern, -1) // -1 means don't repeat
            vibrator.vibrate(vibrationEffect)
        }
    }

    private fun showClickIndicator(x: Float, y: Float) {
        Log.d("AutoClickerService", "Showing click indicator at x=$x, y=$y")
        // Prikaz indikatora klika
        clickIndicatorView.visibility = View.VISIBLE

        clickIndicatorView.post {
            val params = clickIndicatorView.layoutParams as WindowManager.LayoutParams

            // Postavi indikator tačno na tačke (x, y)
            params.x = x.toInt()
            params.y = y.toInt()

            Log.d("AutoClickerService", "Updated clickIndicatorView position: x=${params.x}, y=${params.y}")

            windowManager.updateViewLayout(clickIndicatorView, params)

            // Sakrij indikator nakon kratkog vremena
            handler.postDelayed({
                clickIndicatorView.visibility = View.GONE
                Log.d("AutoClickerService", "Click indicator hidden")
            }, 3000) // 300ms = 0.3 seconds
        }
    }



    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used in this implementation
    }

    override fun onInterrupt() {
        // Not used in this implementation
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::clickIndicatorView.isInitialized) {
            windowManager.removeView(clickIndicatorView)
        }
    }
}
