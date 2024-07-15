package com.example.myapplication

import GroqApiClient
import OverlayView
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScreenshotDetectorService : Service() {
    private var observer: FileObserver? = null
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private lateinit var overlayView: OverlayView

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startScreenshotObserver()
        overlayView = OverlayView(applicationContext)
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
                    var extractedText = visionText.text
                    Log.d("OCR", "Extracted Text: $extractedText")
                    extractedText = extractedText.replace(Regex("^\\d+\\.\\d{2}\\s*"), "")
                    val parts = extractedText.split(Regex("[?:]"), 2)
                    if (parts.size == 2) {
                        val question = parts[0].trim() + "?"
                        val answers = parts[1].trim().split("\n")
                        val formattedAnswers = answers.mapIndexed { index, answer ->
                            "${index + 1}) ${answer.trim()}"
                        }
                        val formattedText = ("Kviz ima jedno pitanje i 4 odgovora, ignorisi sve ostalo sto mislis da nije pitanje i moguci odgovor. Pitanje: " +
                                "$question ${formattedAnswers.joinToString("\n")}     Odgovori samo brojkom tačnog odgovora! Ako nemaju ponudjeni ogovori, odgovori na pitanje svojim kratkim odgovorom!")
                            .replace("\n", " ").replace("mts", "").replace("do kraja", "")
                            .replace("Ne odgovaraj odmah, sačekajte da se odgovori uokvire belom bojom odnosno postanu aktivni.", "")

                        Log.d("OCR", "Formatted Text: $formattedText")


                        CoroutineScope(Dispatchers.IO).launch {
                            val apiKey = "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"
                            val groqApiClient = GroqApiClient(apiKey)
                            groqApiClient.sendPrompt(formattedText) { result ->
                                result?.let {
                                    Log.d("Groq API", "Response: $it")
                                    processGroqResponse(it)
                                } ?: Log.e("Groq API", "Neuspjelo dobivanje odgovora od Groq API-ja")
                            }
                        }
                    } else {
                        Log.e("OCR", "Neočekivani format teksta")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("OCR", "Error: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("ScreenshotDetectorService", "Error processing screenshot: ${e.message}")
        }
    }

    private fun processGroqResponse(response: String) {
        Log.d("Groq API", "Processing response: $response")
        overlayView.show(response)

        // Koristi regularni izraz za pronalaženje prve cifre između 1 i 4
        val regex = Regex("[1-4]")
        val match = regex.find(response.trim())

        // Ako je pronađena cifra, koristi je kao answerNumber
        val answerNumber = match?.value?.toIntOrNull()

        if (answerNumber != null) {
            val intent = Intent(this, AutoClickerAccessibilityService::class.java).apply {
                action = "PERFORM_CLICK"
                putExtra("ANSWER_NUMBER", answerNumber)
            }
            startService(intent)
        } else {
            Log.e("Groq API", "Invalid response: $response")
        }
    }


    override fun onDestroy() {
        observer?.stopWatching()
        executor.shutdown()
        super.onDestroy()
    }
}