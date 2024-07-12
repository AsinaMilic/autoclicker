package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*

class AutoClickerAccessibilityService : AccessibilityService() {
    private var isClicking = false
    private var clickJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        startClicking()
    }

    private fun startClicking() {
        if (!isClicking) {
            isClicking = true
            clickJob = CoroutineScope(Dispatchers.Default).launch {
                while (isActive) {
                    performClick()
                    delay(1000) // Klik svake sekunde
                }
            }
        }
    }

    private fun performClick() {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f

        val path = Path().apply {
            moveTo(centerX, centerY)
        }

        val gestureBuilder = GestureDescription.Builder().apply {
            addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        }

        val gesture = gestureBuilder.build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                // Klik je završen
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                // Klik je otkazan
            }
        }, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopClicking()
    }

    private fun stopClicking() {
        isClicking = false
        clickJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
    }
}