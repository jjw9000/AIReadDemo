package com.picturebook.infrastructure.ai

import android.util.Log
import com.picturebook.data.local.BookDao
import com.picturebook.data.local.BookPageMatch
import com.picturebook.domain.model.BookMatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalBookSearchService(
    private val bookDao: BookDao
) {
    companion object {
        private const val TAG = "LocalBookSearchService"
        private const val SIMILARITY_THRESHOLD = 0.85f
        private const val MIN_TEXT_LENGTH = 5
    }

    suspend fun search(ocrText: String): LocalSearchResult? = withContext(Dispatchers.IO) {
        if (ocrText.length < MIN_TEXT_LENGTH) {
            Log.d(TAG, "OCR text too short: ${ocrText.length} chars")
            return@withContext null
        }

        val bookCount = bookDao.getBookCount()
        if (bookCount == 0) {
            Log.d(TAG, "No books in local database")
            return@withContext null
        }

        val tokens = tokenize(ocrText)
        if (tokens.isEmpty()) {
            return@withContext null
        }

        val ftsQuery = tokens.joinToString(" OR ") { "$it*" }

        // Search real books by page text
        val pageCandidates = try {
            bookDao.searchCandidates(ftsQuery)
        } catch (e: Exception) {
            Log.e(TAG, "FTS search failed: ${e.message}")
            emptyList()
        }

        if (pageCandidates.isNotEmpty()) {
            val ocrTokens = tokenizeToSet(ocrText)
            val scored = pageCandidates.mapNotNull { candidate ->
                val candidateTokens = tokenizeToSet(candidate.fullText)
                val similarity = calculateJaccardSimilarity(ocrTokens, candidateTokens)
                if (similarity > 0) {
                    candidate to similarity
                } else {
                    null
                }
            }.sortedByDescending { it.second }

            scored.firstOrNull()
                ?.takeIf { it.second >= SIMILARITY_THRESHOLD }
                ?.let { (candidate, similarity) ->
                    Log.d(TAG, "Real book match: ${candidate.bookId}, page ${candidate.pageNumber}, similarity $similarity")
                    return@withContext LocalSearchResult(
                        bookId = candidate.bookId,
                        title = "",
                        pageNumber = candidate.pageNumber,
                        similarity = similarity,
                        isPlaceholder = false
                    )
                }
        }

        // Search placeholders by title (title might be empty or partially filled)
        val placeholderCandidates = try {
            bookDao.getAllPlaceholders()
        } catch (e: Exception) {
            Log.e(TAG, "Placeholder search failed: ${e.message}")
            emptyList()
        }

        if (placeholderCandidates.isNotEmpty()) {
            val ocrTokens = tokenizeToSet(ocrText)
            val scored = placeholderCandidates.mapNotNull { placeholder ->
                if (placeholder.title.isBlank()) {
                    null
                } else {
                    val placeholderTokens = tokenizeToSet(placeholder.title)
                    val similarity = calculateJaccardSimilarity(ocrTokens, placeholderTokens)
                    if (similarity >= SIMILARITY_THRESHOLD) {
                        placeholder to similarity
                    } else {
                        null
                    }
                }
            }.sortedByDescending { it.second }

            scored.firstOrNull()?.let { (placeholder, similarity) ->
                Log.d(TAG, "Placeholder match: ${placeholder.bookId}, title '${placeholder.title}', similarity $similarity")
                return@withContext LocalSearchResult(
                    bookId = placeholder.bookId,
                    title = placeholder.title,
                    pageNumber = 0,
                    similarity = similarity,
                    isPlaceholder = true
                )
            }
        }

        return@withContext null
    }

    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))
            .flatMap { word ->
                when {
                    word.isEmpty() -> emptyList()
                    word.codePoints().allMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN } -> {
                        word.windowed(2).filter { it.length == 2 }
                    }
                    word.matches(Regex("^[a-z0-9]+$")) -> listOf(word)
                    else -> listOf(word)
                }
            }
            .filter { it.length >= 2 }
            .distinct()
    }

    fun tokenizeToSet(text: String): Set<String> = tokenize(text).toSet()

    fun calculateJaccardSimilarity(set1: Set<String>, set2: Set<String>): Float {
        if (set1.isEmpty() && set2.isEmpty()) return 0f
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        return if (union > 0) intersection.toFloat() / union else 0f
    }
}

data class LocalSearchResult(
    val bookId: String,
    val title: String,
    val pageNumber: Int,
    val similarity: Float,
    val isPlaceholder: Boolean
)
