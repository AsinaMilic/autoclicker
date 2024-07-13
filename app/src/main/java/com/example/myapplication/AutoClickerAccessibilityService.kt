package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
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
                performClickAt(x, y)
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
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        // Definišite y-offset
        val yOffset = screenHeight * (0f) // Pomeranje y-koordinate za 5% visine ekrana

        // Pozicije odgovora bazirane na slici
        val topMargin = screenHeight * 0.55f // Početak prvog odgovora
        val bottomMargin = screenHeight * 0.85f // Kraj poslednjeg odgovora
        val height = bottomMargin - topMargin

        val x = screenWidth * 0.5f // Klik u sredini po širini
        val y = when(answerNumber) {
            1 -> topMargin + height * 0.125f + yOffset
            2 -> topMargin + height * 0.375f + yOffset
            3 -> topMargin + height * 0.625f + yOffset
            4 -> topMargin + height * 0.875f + yOffset
            else -> topMargin + height * 0.5f + yOffset // Sredina ako je neispravan broj
        }

        Log.d("AutoClickerService", "Calculated click position: x=$x, y=$y")
        return Pair(x, y)
    }


    private fun performClickAt(x: Float, y: Float) {
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
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e("AutoClickerService", "Click cancelled")
            }
        }, null)
    }

    private fun showClickIndicator(x: Float, y: Float) {
        Log.d("AutoClickerService", "Showing click indicator at x=$x, y=$y")
        // Prikaz indikatora klika
        clickIndicatorView.visibility = View.VISIBLE

        clickIndicatorView.post {
            val params = clickIndicatorView.layoutParams as WindowManager.LayoutParams

            // Log trenutnih parametara pre ažuriranja
            Log.d("AutoClickerService", "Current clickIndicatorView position: x=${params.x}, y=${params.y}")

            // Centriraj indikator klika na tačku (x, y)
            params.x = x.toInt() - clickIndicatorView.width / 2
            params.y = y.toInt() - clickIndicatorView.height / 2

            // Log novih parametara nakon ažuriranja
            Log.d("AutoClickerService", "Updated clickIndicatorView position: x=${params.x}, y=${params.y}")

            windowManager.updateViewLayout(clickIndicatorView, params)

            // Sakrij indikator nakon kratkog vremena
            handler.postDelayed({
                clickIndicatorView.visibility = View.GONE
                Log.d("AutoClickerService", "Click indicator hidden")
            }, 300) // 300ms = 0.3 seconds
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
