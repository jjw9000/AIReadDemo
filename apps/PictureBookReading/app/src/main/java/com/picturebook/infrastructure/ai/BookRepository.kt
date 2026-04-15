package com.picturebook.infrastructure.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.picturebook.data.local.BookDatabase
import com.picturebook.data.local.entity.BookEntity
import com.picturebook.data.local.entity.BookPageEntity
import com.picturebook.domain.model.BookDetails
import com.picturebook.domain.model.BookMatchResult
import com.picturebook.domain.model.PageDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookRepository(context: Context) {
    private val database = BookDatabase.getInstance(context)
    private val bookDao = database.bookDao()
    private val ocrClient = MlKitOcrClient()
    private val localSearchService = LocalBookSearchService(bookDao)
    private val bookApi = BookMatchingClient()
    private val httpOcrClient = HttpOcrClient()
    private val orbMatcher = OrbPageMatcher()

    // Page matching strategies - tried in order until one succeeds
    private val pageMatchChain = PageMatchChain(
        listOf(
            OrbMatchStrategy(bookDao, orbMatcher),
            JaccardMatchStrategy(bookDao, localSearchService),
            OcrServiceStrategy(httpOcrClient)
        )
    )

    suspend fun searchBook(bitmap: Bitmap, extractedText: String? = null): BookSearchResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "searchBook called, bitmap=${bitmap.width}x${bitmap.height}")

        // Try local search with ML OCR first
        var ocrText = extractedText
        if (ocrText == null) {
            Log.d(TAG, "No extractedText, calling ML Kit OCR...")
            ocrText = ocrClient.recognize(bitmap)?.fullText
        }

        // Try local search with OCR text
        if (!ocrText.isNullOrBlank()) {
            Log.d(TAG, "OCR text: $ocrText")
            val localResult = localSearchService.search(ocrText)
            if (localResult != null) {
                Log.d(TAG, "Local match found: ${localResult.bookId}, isPlaceholder=${localResult.isPlaceholder}, similarity: ${localResult.similarity}")

                if (localResult.isPlaceholder) {
                    // Fill in placeholder with OCR text
                    val title = if (localResult.title.isNotBlank()) localResult.title else ocrText.take(20)
                    val pageText = ocrText
                    fillPlaceholder(localResult.bookId, title, pageText)
                    return@withContext BookSearchResult.Found(
                        BookMatchResult(
                            bookId = localResult.bookId,
                            title = title,
                            pageNumber = 1,
                            similarity = localResult.similarity,
                            isLocal = true
                        ),
                        text = pageText
                    )
                } else {
                    // Real book found - get page text
                    val pages = bookDao.getPagesByBookId(localResult.bookId)
                    val matchedPage = pages.find { it.pageNumber == localResult.pageNumber } ?: pages.firstOrNull()
                    val pageText = matchedPage?.fullText ?: ""
                    return@withContext BookSearchResult.Found(
                        BookMatchResult(
                            bookId = localResult.bookId,
                            title = localResult.title,
                            pageNumber = matchedPage?.pageNumber ?: 0,
                            similarity = localResult.similarity,
                            isLocal = true
                        ),
                        text = pageText
                    )
                }
            }
        }

        // Fallback to BookManagementService (CLIP) - for cover/image matching
        Log.d(TAG, "No local match or no OCR text, calling CLIP API")
        val serverResult = bookApi.matchBook(bitmap)
        if (serverResult != null && serverResult.pages.isNotEmpty()) {
            Log.d(TAG, "CLIP match: ${serverResult.id}, ${serverResult.title}, ${serverResult.pages.size} pages")
            cacheBookFromServer(serverResult)
            val pageText = serverResult.pages.firstOrNull()?.fullText ?: ""
            return@withContext BookSearchResult.Found(
                BookMatchResult(
                    bookId = serverResult.id,
                    title = serverResult.title,
                    pageNumber = 0,
                    similarity = serverResult.similarity,
                    isLocal = false
                ),
                text = pageText
            )
        }

        // CLIP found nothing - try OcrService API as last resort
        if (ocrText.isNullOrBlank()) {
            Log.d(TAG, "CLIP found nothing and no local OCR, calling OcrService API")
            val ocrServiceResult = httpOcrClient.recognize(bitmap)
            if (ocrServiceResult != null && ocrServiceResult.fullText.isNotBlank()) {
                Log.d(TAG, "OcrService returned: ${ocrServiceResult.fullText}")
                // Save as new book
                val bookId = "ocr_${System.currentTimeMillis()}"
                val title = ocrServiceResult.fullText.take(20)
                cacheOcrResult(bookId, title, ocrServiceResult.fullText)
                return@withContext BookSearchResult.Found(
                    BookMatchResult(
                        bookId = bookId,
                        title = title,
                        pageNumber = 1,
                        similarity = 1.0f,
                        isLocal = false
                    ),
                    text = ocrServiceResult.fullText
                )
            }
        }

        return@withContext BookSearchResult.NotFound
    }

    suspend fun getBookDetails(bookId: String): BookDetails? = withContext(Dispatchers.IO) {
        val book = bookDao.getBookById(bookId) ?: return@withContext null
        if (book.isPlaceholder) return@withContext null

        val pages = bookDao.getPagesByBookId(bookId)
        BookDetails(
            bookId = book.bookId,
            title = book.title,
            pages = pages.map { PageDetails(pageNumber = it.pageNumber, fullText = it.fullText) }
        )
    }

    /**
     * Read a page from a known book without doing cover match.
     * Uses strategy chain: ORB -> Jaccard -> OcrService
     */
    suspend fun readWithKnownBook(bitmap: Bitmap, bookId: String): ReadResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "readWithKnownBook called, bookId=$bookId")

        val matchResult = pageMatchChain.match(bitmap, bookId)
        if (matchResult == null) {
            Log.d(TAG, "All strategies failed to match page")
            return@withContext null
        }

        Log.d(TAG, "Matched page ${matchResult.pageNumber} using ${matchResult.strategy} strategy")

        // Save ORB descriptors for future matching if Jaccard matched
        if (matchResult.strategy == "Jaccard" && matchResult.pageNumber > 0) {
            val pages = bookDao.getPagesByBookId(bookId)
            val matchedPage = pages.find { it.pageNumber == matchResult.pageNumber }
            if (matchedPage != null && matchedPage.orbDescriptors.isNullOrBlank()) {
                Log.d(TAG, "Saving ORB descriptors for page ${matchResult.pageNumber} for future matching")
                savePageOrbDescriptors(bookId, matchResult.pageNumber, bitmap)
            }
        }

        ReadResult(
            text = matchResult.text,
            matchedPageNumber = matchResult.pageNumber,
            usedStoredText = matchResult.usedStoredText
        )
    }

    /**
     * Save ORB descriptors for a page.
     * Called when user explicitly captures a page to enable future ORB matching.
     */
    private suspend fun savePageOrbDescriptors(bookId: String, pageNumber: Int, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        val descriptors = orbMatcher.extractDescriptors(bitmap)
        if (descriptors != null) {
            val pages = bookDao.getPagesByBookId(bookId)
            val existingPage = pages.find { it.pageNumber == pageNumber }
            if (existingPage != null) {
                val updatedPage = existingPage.copy(orbDescriptors = descriptors)
                bookDao.updatePage(updatedPage)
                Log.d(TAG, "Saved ORB descriptors for page $pageNumber of book $bookId")
            }
        }
    }

    private suspend fun fillPlaceholder(bookId: String, title: String, pageText: String) {
        try {
            // Update placeholder to real book
            val updatedBook = BookEntity(
                bookId = bookId,
                title = title,
                isPlaceholder = false,
                updatedAt = System.currentTimeMillis()
            )
            bookDao.insertBook(updatedBook)

            // Add page with OCR text
            val page = BookPageEntity(
                bookId = bookId,
                pageNumber = 1,
                fullText = pageText
            )
            bookDao.insertPages(listOf(page))
            Log.d(TAG, "Filled placeholder: $bookId with title='$title'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fill placeholder: ${e.message}")
        }
    }

    private suspend fun cacheBookFromServer(book: BookMatchingClient.BookDto) {
        try {
            val bookEntity = BookEntity(
                bookId = book.id,
                title = book.title,
                isPlaceholder = false
            )
            bookDao.insertBook(bookEntity)

            val pageEntities = book.pages.map { page ->
                BookPageEntity(
                    bookId = book.id,
                    pageNumber = page.pageNumber,
                    fullText = page.fullText
                )
            }
            if (pageEntities.isNotEmpty()) {
                bookDao.insertPages(pageEntities)
            }
            Log.d(TAG, "Cached book: ${book.id} with ${pageEntities.size} pages")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache book: ${e.message}")
        }
    }

    private suspend fun cacheOcrResult(bookId: String, title: String, text: String) {
        try {
            val bookEntity = BookEntity(
                bookId = bookId,
                title = title,
                isPlaceholder = false
            )
            bookDao.insertBook(bookEntity)

            val pageEntity = BookPageEntity(
                bookId = bookId,
                pageNumber = 1,
                fullText = text
            )
            bookDao.insertPages(listOf(pageEntity))
            Log.d(TAG, "Cached OCR result: $bookId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache OCR result: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BookRepository"
    }
}

sealed class BookSearchResult {
    data class Found(val match: BookMatchResult, val text: String) : BookSearchResult()
    data object NoOcrText : BookSearchResult()
    data object NotFound : BookSearchResult()
}

// For reading with a known book (no cover match needed)
data class ReadResult(
    val text: String,
    val matchedPageNumber: Int,
    val usedStoredText: Boolean
)
