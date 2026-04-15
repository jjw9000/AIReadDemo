package com.picturebook.infrastructure.ai

import android.graphics.Bitmap

/**
 * Strategy interface for matching a page within a known book.
 * Used in readWithKnownBook flow.
 */
interface PageMatchStrategy {
    /**
     * Try to match the current bitmap to a page in the known book.
     * @param bitmap The captured page image
     * @param bookId The known book ID
     * @return MatchResult if matched, null if this strategy couldn't match
     */
    suspend fun match(bitmap: Bitmap, bookId: String): PageMatchResult?
}

/**
 * Result of page matching by a strategy.
 */
data class PageMatchResult(
    val pageNumber: Int,
    val text: String,
    val usedStoredText: Boolean,
    val strategy: String
)