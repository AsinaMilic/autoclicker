package com.example.myapplication

import GroqApiClient
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                        val formattedText = "$question ${formattedAnswers.joinToString("\n")} .Odgovori samo brojkom tačnog odgovora!".replace("\n", " ")
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
        // Ovde dodajte logiku za obradu odgovora od Groq API-ja
        // Na primer, možete parsirati odgovor i pokrenuti odgovarajuću akciju
        // baziranu na sadržaju odgovora
        Log.d("Groq API", "Processing response: $response")
        // Ovde možete dodati dodatnu logiku za obradu odgovora
    }

    override fun onDestroy() {
        observer?.stopWatching()
        executor.shutdown()
        super.onDestroy()
    }
}
