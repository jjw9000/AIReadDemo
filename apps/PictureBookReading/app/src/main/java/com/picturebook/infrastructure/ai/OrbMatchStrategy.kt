package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log

/**
 * ORB image feature matching strategy.
 * Works best for picture books without text.
 */
class OrbMatchStrategy(
    private val bookDao: com.picturebook.data.local.BookDao,
    private val orbMatcher: OrbPageMatcher
) : PageMatchStrategy {

    override suspend fun match(bitmap: Bitmap, bookId: String): PageMatchResult? {
        val pagesWithOrb = bookDao.getPagesWithOrbDescriptors(bookId)
        if (pagesWithOrb.isEmpty()) {
            Log.d(TAG, "No pages with ORB descriptors for book $bookId")
            return null
        }

        val orbDescriptors = pagesWithOrb.map { it.pageNumber to (it.orbDescriptors ?: "") }
        val matchedPageNumber = orbMatcher.matchPage(bitmap, orbDescriptors)
            ?: return null

        val matchedPage = pagesWithOrb.find { it.pageNumber == matchedPageNumber }
            ?: return null

        if (matchedPage.fullText.isBlank()) {
            Log.d(TAG, "ORB matched page $matchedPageNumber but page has no text")
            return null
        }

        Log.d(TAG, "ORB matched page $matchedPageNumber")
        return PageMatchResult(
            pageNumber = matchedPageNumber,
            text = matchedPage.fullText,
            usedStoredText = true,
            strategy = "ORB"
        )
    }

    companion object {
        private const val TAG = "OrbMatchStrategy"
    }
}