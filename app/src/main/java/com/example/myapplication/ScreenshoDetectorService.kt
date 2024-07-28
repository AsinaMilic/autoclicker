package com.example.myapplication

import GroqApiClient
import OverlayView
import android.app.Service
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScreenshotDetectorService : Service() {
    private var observer: FileObserver? = null
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private lateinit var overlayView: OverlayView
    private val client = OkHttpClient()

    // Add your SerpApi key here
    private val serpApiKey = "669e533fae938a28cad10d6e7ba452aa21ea985689e4fc864fea670d16e15bce"

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
                    val cleanedPath = path.substringAfter("-").substringAfter("-")
                    Log.d("ScreenshotDetectorService", "Detected file: $cleanedPath")

                    if (cleanedPath.startsWith("Screenshot_")) {
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

                        CoroutineScope(Dispatchers.IO).launch {
                            val searchResults = performInternetSearch(question + answers)
                            val formattedText = ("Kviz ima jedno pitanje i 4 odgovora. Pitanje: " +
                                    "$question ${formattedAnswers.joinToString("\n")}     Odgovori samo brojkom tačnog odgovora! Ako nemaju ponudjeni ogovori, odgovori na pitanje svojim kratkim odgovorom! " +
                                    "Dodatne informacije iz internet pretrage: $searchResults")
                                .replace("\n", " ").replace("mts", "").replace("do kraja", "")
                                .replace("Ne odgovaraj odmah, sačekajte da se odgovori uokvire belom bojom odnosno postanu aktivni.", "")
                                .replace("stotine", "")
                                .replace("hiljade","")

                            Log.d("OCR", "Formatted Text with Search Results: $formattedText")

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

    private fun performInternetSearch(query: String): String {
        val encodedQuery = Uri.encode(query)
        val url = "https://serpapi.com/search.json?engine=bing&q=$encodedQuery&api_key=$serpApiKey"

        val request = Request.Builder()
            .url(url)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val organicResults = jsonResponse.getJSONArray("organic_results")
                val snippets = mutableListOf<String>()
                for (i in 0 until minOf(3, organicResults.length())) {
                    val result = organicResults.getJSONObject(i)
                    snippets.add(result.getString("snippet"))
                }
                snippets.joinToString(" ")
            } else {
                "No relevant information found."
            }
        } catch (e: Exception) {
            Log.e("InternetSearch", "Error performing search: ${e.message}")
            "Error performing internet search."
        }
    }

    private fun processGroqResponse(response: String) {
        Log.d("Groq API", "Processing response: $response")
        overlayView.show(response)

        val regex = Regex("[1-4]")
        val match = regex.find(response.trim())

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