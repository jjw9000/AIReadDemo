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

    suspend fun searchBook(bitmap: Bitmap): BookMatchResult? = withContext(Dispatchers.IO) {
        val ocrResult = ocrClient.recognize(bitmap)
        val ocrText = ocrResult?.fullText ?: return@withContext null

        Log.d(TAG, "OCR text: $ocrText")

        // Try local search first
        val localResult = localSearchService.search(ocrText)
        if (localResult != null) {
            Log.d(TAG, "Local match found: ${localResult.bookId}, similarity: ${localResult.similarity}")
            val book = bookDao.getBookById(localResult.bookId)
            return@withContext localResult.copy(title = book?.title ?: "")
        }

        // Fallback to API
        Log.d(TAG, "No local match, calling server API")
        val serverResult = bookApi.matchBook(bitmap)
        if (serverResult != null) {
            Log.d(TAG, "Server match: ${serverResult.id}, similarity: ${serverResult.similarity}")
            val result = BookMatchResult(
                bookId = serverResult.id,
                title = serverResult.title,
                similarity = serverResult.similarity,
                isLocal = false
            )
            // Cache book from server
            if (serverResult.pages.isNotEmpty()) {
                cacheBookFromServer(serverResult)
            } else {
                // Save placeholder for unmatched books
                savePlaceholder(serverResult.id)
            }
            return@withContext result
        }

        return@withContext null
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

    private suspend fun savePlaceholder(bookId: String) {
        try {
            val placeholder = BookEntity(
                bookId = bookId,
                title = "",
                isPlaceholder = true
            )
            bookDao.insertBook(placeholder)
            Log.d(TAG, "Saved placeholder for: $bookId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save placeholder: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BookRepository"
    }
}
