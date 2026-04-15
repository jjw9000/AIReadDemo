package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.coroutines.resume

class BookMatchingClient(
    private val apiBaseUrl: String = "http://192.168.3.18:5018"
) {
    private val gson = Gson()

    suspend fun matchBook(bitmap: Bitmap): MatchResult? = suspendCancellableCoroutine { cont ->
        try {
            val base64 = bitmapToBase64(bitmap)
            val requestBody = mapOf("imageBase64" to base64)
            val json = gson.toJson(requestBody)

            val url = URL("$apiBaseUrl/api/books/match")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            connection.outputStream.use { os ->
                os.write(json.toByteArray())
            }

            val responseCode = connection.responseCode
            Log.i(TAG, "Book match API response code: $responseCode")

            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                Log.i(TAG, "Book match API response: $response")

                val matchResponse = gson.fromJson(response, MatchResponse::class.java)
                if (matchResponse != null && matchResponse.success && matchResponse.books.isNotEmpty()) {
                    val bestMatch = matchResponse.books.first()
                    cont.resume(MatchResult(
                        bookId = bestMatch.id,
                        title = bestMatch.title,
                        similarity = bestMatch.similarity
                    ))
                } else {
                    cont.resume(null)
                }
            } else {
                Log.e(TAG, "Book match API error: $responseCode")
                cont.resume(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Book match failed: ${e.message}")
            cont.resume(null)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    data class MatchResult(
        val bookId: String,
        val title: String,
        val similarity: Float
    )

    data class MatchResponse(
        val success: Boolean,
        val books: List<BookMatch>,
        val error: String?
    )

    data class BookMatch(
        val id: String,
        val title: String,
        val similarity: Float,
        val metadata: Map<String, Any>?
    )

    companion object {
        private const val TAG = "BookMatchingClient"
    }
}
