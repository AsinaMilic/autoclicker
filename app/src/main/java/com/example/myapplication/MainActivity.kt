package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MainActivity : AppCompatActivity() {

    private var running = false
    private lateinit var screenshotJob: Job
    private var mediaProjection: MediaProjection? = null
    private val okHttpClient = OkHttpClient()

    private val PERMISSION_REQUEST_CODE = 1001
    private val SCREEN_CAPTURE_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.start_button).setOnClickListener { toggleAutoClicker() }

        checkAndRequestPermissions()
    }

    private fun requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.RECORD_AUDIO
        )

        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGrantedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }

        requestManageExternalStoragePermission()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = permissions.zip(grantResults.toTypedArray())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }

            if (deniedPermissions.isEmpty()) {
                println("All permissions granted")
            } else {
                println("Permissions denied: $deniedPermissions")
                // Optionally, show a dialog explaining why these permissions are needed and ask the user to grant them
            }
        }
    }



    private fun toggleAutoClicker() {
        running = !running
        findViewById<Button>(R.id.start_button).text = if (running) "Zaustavi Autoclicker" else "Pokreni Autoclicker"

        if (running) {
            startScreenCapture()
        } else {
            stopAutoClicker()
        }
    }

    private fun startScreenCapture() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Start the foreground service
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            running = false
            findViewById<Button>(R.id.start_button).text = "Pokreni Autoclicker"
        }
    }


    private fun startAutoClicker() {
        screenshotJob = lifecycleScope.launch(Dispatchers.Default) {
            val imageReader = createImageReader()
            val virtualDisplay = createVirtualDisplay(imageReader)

            while (running) {
                try {
                    val screenshot = takeScreenshot(imageReader)
                    val (question, answers) = imageToText(screenshot)

                    if (question.isNotBlank() && answers.isNotEmpty()) {
                        val correctAnswerIndex = getLLMAnswer(question, answers)
                        clickAnswer(correctAnswerIndex, answers)
                    }

                    delay(5000) // Wait for 5 seconds before the next iteration
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        // Show error to user
                    }
                }
            }

            virtualDisplay.release()
            imageReader.close()
        }
    }

    private fun stopAutoClicker() {
        if (::screenshotJob.isInitialized && screenshotJob.isActive) {
            screenshotJob.cancel()
        }
    }

    private fun createImageReader(): ImageReader {
        val metrics = resources.displayMetrics
        return ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
    }

    private fun createVirtualDisplay(imageReader: ImageReader): VirtualDisplay {
        val metrics = resources.displayMetrics
        return mediaProjection!!.createVirtualDisplay(
            "AutoClicker",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )
    }

    private fun takeScreenshot(imageReader: ImageReader): Bitmap {
        val image = imageReader.acquireLatestImage()
        val planes = image.planes
        val buffer = planes[0].buffer
        val width = image.width
        val height = image.height
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        return bitmap
    }

    private suspend fun imageToText(image: Bitmap): Pair<String, List<String>> {
        val metrics = resources.displayMetrics
        val questionBox = Rect(
            (0.05 * metrics.widthPixels).toInt(),
            (0.2 * metrics.heightPixels).toInt(),
            (0.95 * metrics.widthPixels).toInt(),
            (0.3 * metrics.heightPixels).toInt()
        )
        val answersBox = Rect(
            (0.05 * metrics.widthPixels).toInt(),
            (0.35 * metrics.heightPixels).toInt(),
            (0.95 * metrics.widthPixels).toInt(),
            (0.8 * metrics.heightPixels).toInt()
        )

        val questionBitmap = Bitmap.createBitmap(image, questionBox.left, questionBox.top, questionBox.width(), questionBox.height())
        val questionText = extractTextFromImage(questionBitmap)

        val answersBitmap = Bitmap.createBitmap(image, answersBox.left, answersBox.top, answersBox.width(), answersBox.height())
        val answersText = extractTextFromImage(answersBitmap)

        val answers = answersText.lines().filter { it.isNotBlank() }

        return questionText to answers
    }

    private suspend fun extractTextFromImage(bitmap: Bitmap): String {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        return suspendCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    private suspend fun getLLMAnswer(question: String, answers: List<String>): Int = withContext(Dispatchers.IO) {
        val prompt = "Pitanje: $question\nOdgovori:\n" + answers.mapIndexed { index, answer -> "${index + 1}. $answer" }.joinToString("\n") + "\nOdgovori samo brojem tačnog odgovora."

        val jsonBody = JSONObject().apply {
            put("messages", listOf(
                JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
            ))
            put("model", "llama3-8b-8192")
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer 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")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            val jsonResponse = JSONObject(responseBody)
            jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim().toInt()
        }
    }

    private fun clickAnswer(answerIndex: Int, answers: List<String>) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val yStart = metrics.heightPixels * 0.4f
        val yStep = metrics.heightPixels * 0.1f
        val y = yStart + yStep * answerIndex

        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()

        val touchDown = MotionEvent.obtain(
            downTime,
            eventTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0
        )

        val touchUp = MotionEvent.obtain(
            downTime,
            eventTime + 100,
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        )

        lifecycleScope.launch(Dispatchers.Main) {
            dispatchTouchEvent(touchDown)
            dispatchTouchEvent(touchUp)
        }

        touchDown.recycle()
        touchUp.recycle()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::screenshotJob.isInitialized && screenshotJob.isActive) {
            screenshotJob.cancel()
        }
        mediaProjection?.stop()
    }
}
