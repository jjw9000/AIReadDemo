package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log

/**
 * OcrService API strategy for when local OCR fails.
 * Used as fallback when ML Kit OCR returns nothing.
 */
class OcrServiceStrategy(
    private val httpOcrClient: HttpOcrClient
) : PageMatchStrategy {

    override suspend fun match(bitmap: Bitmap, bookId: String): PageMatchResult? {
        Log.d(TAG, "OcrServiceStrategy: calling OcrService API")
        val ocrResult = httpOcrClient.recognize(bitmap)
            ?: return null

        if (ocrResult.fullText.isBlank()) {
            Log.d(TAG, "OcrService returned blank text")
            return null
        }

        Log.d(TAG, "OcrServiceStrategy returned: ${ocrResult.fullText.take(50)}")
        return PageMatchResult(
            pageNumber = 0,
            text = ocrResult.fullText,
            usedStoredText = false,
            strategy = "OcrService"
        )
    }

    companion object {
        private const val TAG = "OcrServiceStrategy"
    }
}