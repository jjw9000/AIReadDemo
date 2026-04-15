package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.coroutines.resume

class HttpOcrClient(private val apiBaseUrl: String = "http://192.168.3.18:5017") {

    private val gson = Gson()

    suspend fun recognize(bitmap: Bitmap): OcrResult? = suspendCancellableCoroutine { cont ->
        try {
            val base64 = bitmapToBase64(bitmap)
            val requestBody = mapOf(
                "imageBase64" to base64,
                "task" to "ocr"
            )
            val json = gson.toJson(requestBody)

            // Use URLConnection for simplicity - in production use OkHttp or Ktor
            val url = java.net.URL("$apiBaseUrl/ocr/recognize-simple")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 120000

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
                    cont.resume(null)
                }
            } else {
                Log.e(TAG, "OCR API error: $responseCode")
                cont.resume(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed: ${e.message}")
            cont.resume(null)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
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
    }
}