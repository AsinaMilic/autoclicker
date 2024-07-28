package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "PERFORM_CLICK") {
            val serviceIntent = Intent(context, AutoClickerAccessibilityService::class.java)
            context?.startService(serviceIntent)
        }
    }
}