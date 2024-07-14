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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

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
                        val formattedText = ("Kviz se sastoji od jednog pitanja i 4 odgovora, ignorisi sve ostalo sto mislis da nije deo kviza. Pitanje: " +
                                "$question ${formattedAnswers.joinToString("\n")}     Odgovori samo brojkom tačnog odgovora!")
                            .replace("\n", " ")

                        Log.d("OCR", "Formatted Text: $formattedText")

                        CoroutineScope(Dispatchers.IO).launch {
                            val apiKey = "sk-proj-nL79lVT8traX5QJ4pF6yT3BlbkFJrIJvNyeMB43INwliMwBo"
                            val openAiApiClient = OpenAiApiClient(apiKey)
                            openAiApiClient.sendPrompt(formattedText) { result ->
                                result?.let {
                                    Log.d("OpenAI API", "Response: $it")
                                    processOpenAiResponse(it)
                                } ?: Log.e("OpenAI API", "Failed to get response from OpenAI API")
                            }
                        }
                    } else {
                        Log.e("OCR", "Unexpected text format")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("OCR", "Error: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("ScreenshotDetectorService", "Error processing screenshot: ${e.message}")
        }
    }

    private fun processOpenAiResponse(response: String) {
        Log.d("OpenAI API", "Processing response: $response")

        // Use regular expression to find the first digit between 1 and 4
        val regex = Regex("[1-4]")
        val match = regex.find(response.trim())

        // If a digit is found, use it as answerNumber
        val answerNumber = match?.value?.toIntOrNull()

        if (answerNumber != null) {
            val intent = Intent(this, AutoClickerAccessibilityService::class.java).apply {
                action = "PERFORM_CLICK"
                putExtra("ANSWER_NUMBER", answerNumber)
            }
            startService(intent)
        } else {
            Log.e("OpenAI API", "Invalid response: $response")
        }
    }

    override fun onDestroy() {
        observer?.stopWatching()
        executor.shutdown()
        super.onDestroy()
    }
}

class OpenAiApiClient(private val apiKey: String) {
    private val client = OkHttpClient()

    fun sendPrompt(prompt: String, callback: (String?) -> Unit) {
        val json = JSONObject()
        json.put("model", "gpt-3.5-turbo")
        val messages = JSONArray()
        val message = JSONObject()
        message.put("role", "user")
        message.put("content", prompt)
        messages.put(message)
        json.put("messages", messages)

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        Log.d("OpenAiApiClient", "Sending request to OpenAI API")
        Log.d("OpenAiApiClient", "Request body: $json")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OpenAiApiClient", "Request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("OpenAiApiClient", "Received response. Status: ${response.code}")
                Log.d("OpenAiApiClient", "Response body: $responseBody")

                if (response.isSuccessful && responseBody != null) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val content = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        Log.d("OpenAiApiClient", "Extracted content: $content")
                        callback(content)
                    } catch (e: Exception) {
                        Log.e("OpenAiApiClient", "Error parsing JSON response", e)
                        callback(null)
                    }
                } else {
                    Log.e("OpenAiApiClient", "Unsuccessful response: ${response.code}")
                    callback(null)
                }
            }
        })
    }
}
