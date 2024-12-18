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

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AutoClickerService", "Service connected")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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

    private fun calculateClickPosition(answerNumber: Int): Pair<Float, Float> {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()

        // Start of the first button
        val startYPercentage = 0.6f // Y coordinate of the first button as 55% of the screen height
        val startY = screenHeight * startYPercentage

        // Space between buttons
        val yOffsetPercentage = 0.1f // Space between buttons as 5% of the screen height
        val yOffset = screenHeight * yOffsetPercentage

        // Click on the appropriate y-coordinate based on answerNumber
        val y = startY + (answerNumber - 1) * yOffset

        // X coordinate can be in the middle of the screen or where needed
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
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e("AutoClickerService", "Click cancelled")
            }
        }, null)
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Implement this method to handle accessibility events
        // For example, you can log the event type
        Log.d("AutoClickerService", "Accessibility event received: ${event?.eventType}")
    }

    override fun onInterrupt() {
        // Implement this method to handle service interruption
        Log.d("AutoClickerService", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::clickIndicatorView.isInitialized) {
            windowManager.removeView(clickIndicatorView)
        }
    }
}