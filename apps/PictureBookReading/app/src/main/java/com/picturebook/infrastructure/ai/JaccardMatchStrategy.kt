package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log
import com.picturebook.config.AppConfig

/**
 * Jaccard similarity based text matching strategy.
 * Requires OCR text from the bitmap.
 */
class JaccardMatchStrategy(
    private val bookDao: com.picturebook.data.local.BookDao,
    private val localSearchService: LocalBookSearchService
) : PageMatchStrategy {

    override suspend fun match(bitmap: Bitmap, bookId: String): PageMatchResult? {
        val ocrText = MlKitOcrClient().recognize(bitmap)?.fullText
            ?: return null

        if (ocrText.isBlank()) {
            Log.d(TAG, "OCR returned blank text")
            return null
        }

        Log.d(TAG, "JaccardMatchStrategy OCR text: ${ocrText.take(50)}")
        val pages = bookDao.getPagesByBookId(bookId)
        if (pages.isEmpty()) {
            Log.d(TAG, "No pages found for book $bookId")
            return null
        }

        val ocrTokens = localSearchService.tokenizeToSet(ocrText)
        val scored = pages.mapNotNull { page ->
            if (page.fullText.isBlank()) {
                null
            } else {
                val pageTokens = localSearchService.tokenizeToSet(page.fullText)
                val similarity = localSearchService.calculateJaccardSimilarity(ocrTokens, pageTokens)
                if (similarity > 0) {
                    page to similarity
                } else {
                    null
                }
            }
        }.sortedByDescending { it.second }

        val bestMatch = scored.firstOrNull()
        return if (bestMatch != null && bestMatch.second >= AppConfig.JACCARD_SIMILARITY_THRESHOLD) {
            val (matchedPage, similarity) = bestMatch
            Log.d(TAG, "Jaccard matched page ${matchedPage.pageNumber} with similarity $similarity")
            PageMatchResult(
                pageNumber = matchedPage.pageNumber,
                text = matchedPage.fullText,
                usedStoredText = true,
                strategy = "Jaccard"
            )
        } else {
            Log.d(TAG, "Jaccard match below threshold or no match")
            null
        }
    }

    companion object {
        private const val TAG = "JaccardMatchStrategy"
    }
}