package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log

/**
 * Orchestrates page matching strategies in sequence.
 * Tries each strategy until one succeeds.
 */
class PageMatchChain(
    private val strategies: List<PageMatchStrategy>
) {
    /**
     * Try each strategy in order until one matches.
     * @return MatchResult from first successful strategy, or null if none succeed
     */
    suspend fun match(bitmap: Bitmap, bookId: String): PageMatchResult? {
        for (strategy in strategies) {
            val result = strategy.match(bitmap, bookId)
            if (result != null) {
                Log.d(TAG, "PageMatchChain: ${result.strategy} succeeded")
                return result
            }
        }
        Log.d(TAG, "PageMatchChain: no strategy matched")
        return null
    }

    companion object {
        private const val TAG = "PageMatchChain"
    }
}