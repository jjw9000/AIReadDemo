package com.picturebook.infrastructure.ai

import android.util.Log
import com.picturebook.data.local.BookDao
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

    suspend fun search(ocrText: String): BookMatchResult? = withContext(Dispatchers.IO) {
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

        val candidates = try {
            bookDao.searchCandidates(ftsQuery)
        } catch (e: Exception) {
            Log.e(TAG, "FTS search failed: ${e.message}")
            return@withContext null
        }

        if (candidates.isEmpty()) {
            Log.d(TAG, "No FTS candidates found")
            return@withContext null
        }

        val ocrTokens = tokenizeToSet(ocrText)
        val scored = candidates.mapNotNull { candidate ->
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
                BookMatchResult(
                    bookId = candidate.bookId,
                    title = "",
                    pageNumber = candidate.pageNumber,
                    similarity = similarity,
                    isLocal = true
                )
            }
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

    private fun tokenizeToSet(text: String): Set<String> = tokenize(text).toSet()

    private fun calculateJaccardSimilarity(set1: Set<String>, set2: Set<String>): Float {
        if (set1.isEmpty() && set2.isEmpty()) return 0f
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        return if (union > 0) intersection.toFloat() / union else 0f
    }
}
