package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.picturebook.config.AppConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.coroutines.resume

class HttpOcrClient(
    private val apiBaseUrl: String = AppConfig.OCR_API_URL
) {

    private val gson = Gson()

    suspend fun recognize(bitmap: Bitmap): OcrResult? = suspendCancellableCoroutine { cont ->
        try {
            val base64 = bitmapToBase64(bitmap)
            val requestBody = mapOf(
                "imageBase64" to base64,
                "task" to "ocr"
            )
            val json = gson.toJson(requestBody)

            Log.d(TAG, "Calling OcrService API at $apiBaseUrl/ocr/recognize-simple")
            // Use URLConnection for simplicity - in production use OkHttp or Ktor
            val url = java.net.URL("$apiBaseUrl/ocr/recognize-simple")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = AppConfig.HTTP_CONNECT_TIMEOUT_MS
            connection.readTimeout = AppConfig.OCR_HTTP_READ_TIMEOUT_MS

            connection.outputStream.use { os ->
                os.write(json.toByteArray())
            }

            val responseCode = connection.responseCode
            Log.i(TAG, "OCR API response code: $responseCode")

            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                Log.i(TAG, "OCR API response: $response")

                val simpleResponse = gson.fromJson(response, SimpleOcrResponse::class.java)
                if (simpleResponse != null && simpleResponse.success && simpleResponse.text.isNotBlank()) {
                    val result = OcrResult(
                        textBlocks = listOf(
                            TextBlock(
                                text = simpleResponse.text,
                                confidence = 1.0f,
                                boundingBox = null
                            )
                        ),
                        fullText = simpleResponse.text
                    )
                    cont.resume(result)
                } else {
                    Log.d(TAG, "OcrService returned empty or failed: success=${simpleResponse?.success}, text=${simpleResponse?.text}")
                    cont.resume(null)
                }
            } else {
                Log.e(TAG, "OCR API error: $responseCode")
                cont.resume(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed: ${e.message}", e)
            cont.resume(null)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Resize to max dimension before compressing to reduce payload size
        val resized = resizeBitmap(bitmap, MAX_IMAGE_DIMENSION)
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        if (resized != bitmap) {
            resized.recycle()
        }
        val bytes = outputStream.toByteArray()
        Log.d(TAG, "OCR image resized from ${bitmap.width}x${bitmap.height} to ${resized.width}x${resized.height}, size: ${bytes.size} bytes")
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Resize bitmap to max dimension while preserving aspect ratio.
     * This significantly reduces payload size for OCR requests.
     */
    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val ratio = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    data class OcrResult(
        val textBlocks: List<TextBlock>,
        val fullText: String
    )

    data class TextBlock(
        val text: String,
        val confidence: Float,
        val boundingBox: android.graphics.Rect?
    )

    data class SimpleOcrResponse(
        val success: Boolean,
        val text: String,
        val error: String?,
        val blockCount: Int
    )

    companion object {
        private const val TAG = "HttpOcrClient"
        private const val MAX_IMAGE_DIMENSION = 1024  // Max width or height in pixels
        private const val JPEG_QUALITY = 80          // JPEG compression quality (0-100)
    }
}