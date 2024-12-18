package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScreenshotDetectorService : Service() {
    private var observer: FileObserver? = null
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)  // Add this line
        .build()
    private val API_URL = "http://192.168.100.75:8000/process_screenshot"

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
            sendScreenshotToApi(file)
        } else {
            executor.schedule({
                processScreenshotWithRetry(file, retryCount + 1)
            }, 1, TimeUnit.SECONDS)
        }
    }

    private fun sendScreenshotToApi(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("API", "Attempting to send screenshot to $API_URL")
                Log.d("API", "File size: ${file.length()} bytes")
                Log.d("API", "Device IP: ${getLocalIpAddress()}")  // Add this line

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        file.name,
                        file.asRequestBody("image/*".toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url(API_URL)
                    .post(requestBody)
                    .build()

                Log.d("API", "Sending request...")
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val answer = response.body?.string()
                        Log.d("API Response", "Success! Response: $answer")
                        answer?.let { processApiResponse(it) }
                    } else {
                        Log.e("API Error", "Response not successful: ${response.code}")
                        Log.e("API Error", "Response body: ${response.body?.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("API Error", "Error sending screenshot", e)
                e.printStackTrace()
            }
        }
    }

    // Add this helper function to get the device's IP address
    private fun getLocalIpAddress(): String {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress.toString()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("API", "Error getting IP address", e)
        }
        return "Unknown"
    }


    private fun processApiResponse(response: String) {
        // Extract the answer number from the API response
        val answerNumber = response.trim().toIntOrNull()

        if (answerNumber != null && answerNumber in 1..4) {
            val intent = Intent(this, AutoClickerAccessibilityService::class.java).apply {
                action = "PERFORM_CLICK"
                putExtra("ANSWER_NUMBER", answerNumber)
            }
            startService(intent)
        } else {
            Log.e("API Response", "Invalid answer number: $response")
        }
    }

    override fun onDestroy() {
        observer?.stopWatching()
        executor.shutdown()
        super.onDestroy()
    }
}