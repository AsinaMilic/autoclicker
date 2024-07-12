package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "PERFORM_CLICK") {
            val x = intent.getFloatExtra("x", 0f)
            val y = intent.getFloatExtra("y", 0f)
            val serviceIntent = Intent(context, AutoClickerAccessibilityService::class.java).apply {
                putExtra("x", x)
                putExtra("y", y)
            }
            context?.startService(serviceIntent)
        }
    }
}