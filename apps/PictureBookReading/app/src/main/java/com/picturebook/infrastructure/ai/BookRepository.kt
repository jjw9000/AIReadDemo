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
     * 1. Try ORB image matching first (for picture books)
     * 2. Try ML OCR + Jaccard text matching
     * 3. If ML OCR finds nothing, fall back to OcrService API
     * 4. Return matched page text or OCR result
     */
    suspend fun readWithKnownBook(bitmap: Bitmap, bookId: String): ReadResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "readWithKnownBook called, bookId=$bookId")

        // Step 1: Try ORB image matching first (for picture books without text)
        val pagesWithOrb = bookDao.getPagesWithOrbDescriptors(bookId)
        if (pagesWithOrb.isNotEmpty()) {
            val orbDescriptors = pagesWithOrb.map { it.pageNumber to (it.orbDescriptors ?: "") }
            val matchedPageNumber = orbMatcher.matchPage(bitmap, orbDescriptors)
            if (matchedPageNumber != null) {
                val matchedPage = pagesWithOrb.find { it.pageNumber == matchedPageNumber }
                if (matchedPage != null && matchedPage.fullText.isNotBlank()) {
                    Log.d(TAG, "ORB matched page $matchedPageNumber, using stored text")
                    return@withContext ReadResult(
                        text = matchedPage.fullText,
                        matchedPageNumber = matchedPageNumber,
                        usedStoredText = true
                    )
                }
            }
            Log.d(TAG, "ORB match failed or page has no text, trying OCR...")
        }

        // Step 2: Try ML OCR + Jaccard text matching
        var ocrText = ocrClient.recognize(bitmap)?.fullText
        if (ocrText == null) {
            Log.d(TAG, "ML Kit OCR returned null, falling back to OcrService API")
            ocrText = httpOcrClient.recognize(bitmap)?.fullText
        }

        if (ocrText.isNullOrBlank()) {
            Log.d(TAG, "All OCR methods failed for known book")
            return@withContext null
        }
        Log.d(TAG, "readWithKnownBook OCR text: $ocrText")

        // Get pages for this book
        val pages = bookDao.getPagesByBookId(bookId)
        if (pages.isEmpty()) {
            Log.d(TAG, "No pages found for book $bookId, using OCR text directly")
            return@withContext ReadResult(text = ocrText, matchedPageNumber = 0, usedStoredText = false)
        }

        // Match OCR text to pages using Jaccard
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
        return@withContext if (bestMatch != null && bestMatch.second >= 0.3f) {
            val (matchedPage, similarity) = bestMatch
            Log.d(TAG, "Jaccard matched page ${matchedPage.pageNumber} with similarity $similarity, stored text: ${matchedPage.fullText.take(50)}")

            // Save ORB descriptors for future matching (if not already saved)
            if (matchedPage.orbDescriptors.isNullOrBlank()) {
                Log.d(TAG, "Saving ORB descriptors for page ${matchedPage.pageNumber} for future matching")
                savePageOrbDescriptors(bookId, matchedPage.pageNumber, bitmap)
            }

            ReadResult(
                text = matchedPage.fullText,
                matchedPageNumber = matchedPage.pageNumber,
                usedStoredText = true
            )
        } else {
            Log.d(TAG, "No page match or low similarity, using OCR text directly")
            ReadResult(text = ocrText, matchedPageNumber = 0, usedStoredText = false)
        }
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
