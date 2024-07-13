package com.example.myapplication

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScreenshotDetectorService : Service() {
    private var observer: FileObserver? = null
    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startScreenshotObserver()
    }

    private fun startScreenshotObserver() {
        val screenshotsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots")

        observer = @RequiresApi(Build.VERSION_CODES.Q)
        object : FileObserver(screenshotsDir, FileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (event == FileObserver.CREATE && path != null) {
                    // Uklanjanje `.pending-` prefiksa ako postoji
                    val cleanedPath = path.substringAfter("-").substringAfter("-")
                    Log.d("ScreenshotDetectorService", "Detected file: $cleanedPath")

                    if (cleanedPath.startsWith("Screenshot_")) {
                        println(screenshotsDir)
                        println(cleanedPath)
                        val filePath = File(screenshotsDir, cleanedPath)
                        executor.schedule({
                            processScreenshotWithRetry(filePath, 0)
                        }, 1, TimeUnit.SECONDS)
                    }
                }
            }
        }
        observer?.startWatching()
    }

    private fun processScreenshotWithRetry(file: File, retryCount: Int) {
        if (retryCount >= 5) {
            Log.e("ScreenshotDetectorService", "File not found or not readable after retries: ${file.absolutePath}")
            return
        }

        if (file.exists() && file.canRead()) {
            processScreenshot(file)
        } else {
            executor.schedule({
                processScreenshotWithRetry(file, retryCount + 1)
            }, 1, TimeUnit.SECONDS)
        }
    }

    private fun processScreenshot(file: File) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        try {
            val image = InputImage.fromFilePath(applicationContext, Uri.fromFile(file))
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedText = visionText.text
                    Log.d("OCR", "Extracted Text: $extractedText")
                    // Handle the extracted text here
                    // For instance, you can start the AutoClickerAccessibilityService based on certain conditions
                }
                .addOnFailureListener { e ->
                    Log.e("OCR", "Error: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("ScreenshotDetectorService", "Error processing screenshot: ${e.message}")
        }
    }

    override fun onDestroy() {
        observer?.stopWatching()
        executor.shutdown()
        super.onDestroy()
    }
}
