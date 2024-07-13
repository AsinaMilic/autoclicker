// ScreenshotDetectorService.kt
package com.example.myapplication

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.FileObserver
import android.os.Environment
import java.io.File

class ScreenshotDetectorService : Service() {
    private var observer: FileObserver? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startScreenshotObserver()
    }

    private fun startScreenshotObserver() {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .absolutePath + File.separator + "Screenshots"

        observer = object : FileObserver(path, FileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (event == FileObserver.CREATE && path != null) {
                    val intent = Intent(applicationContext, AutoClickerAccessibilityService::class.java)
                    intent.action = "PERFORM_CLICK"
                    startService(intent)
                }
            }
        }
        observer?.startWatching()
    }

    override fun onDestroy() {
        observer?.stopWatching()
        super.onDestroy()
    }
}