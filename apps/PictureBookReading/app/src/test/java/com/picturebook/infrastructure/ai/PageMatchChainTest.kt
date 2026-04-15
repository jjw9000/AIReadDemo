package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PageMatchChain and strategy pattern.
 */
class PageMatchChainTest {

    /**
     * Test that chain returns first successful result.
     */
    @Test
    fun `chain returns first successful strategy result`() {
        // Given a chain with strategies that succeed/fail
        val chain = PageMatchChain(listOf(
            AlwaysFailStrategy(),
            AlwaysSucceedStrategy("page 5", "Hello World")
        ))

        // When matching
        val result = chain.match(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), "book1")

        // Then we get the first successful result
        assertNotNull(result)
        assertEquals("page 5", result!!.pageNumber.toString())
        assertEquals("Hello World", result.text)
        assertEquals("AlwaysSucceed", result.strategy)
    }

    /**
     * Test that chain returns null when all strategies fail.
     */
    @Test
    fun `chain returns null when all strategies fail`() {
        // Given a chain with only failing strategies
        val chain = PageMatchChain(listOf(
            AlwaysFailStrategy(),
            AlwaysFailStrategy()
        ))

        // When matching
        val result = chain.match(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), "book1")

        // Then result is null
        assertNull(result)
    }

    /**
     * Test that chain tries strategies in order.
     */
    @Test
    fun `chain tries strategies in order`() {
        // Given a tracking strategy
        val trackingStrategy = TrackingStrategy()

        // Given a chain
        val chain = PageMatchChain(listOf(
            trackingStrategy,
            AlwaysFailStrategy()
        ))

        // When matching (and strategy doesn't match)
        chain.match(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888), "book1")

        // Then the strategy was tried
        assertTrue(trackingStrategy.wasCalled)
    }

    // Test strategies
    private class AlwaysFailStrategy : PageMatchStrategy {
        override suspend fun match(bitmap: Bitmap, bookId: String): PageMatchResult? {
            return null
        }
    }

    private class AlwaysSucceedStrategy(
        private val pageNumber: String,
        private val text: String
    ) : PageMatchStrategy {
        override suspend fun match(bitmap: Bitmap, bookId: String): PageMatchResult {
            return PageMatchResult(
                pageNumber = pageNumber.toInt(),
                text = text,
                usedStoredText = true,
                strategy = "AlwaysSucceed"
            )
        }
    }

    private class TrackingStrategy : PageMatchStrategy {
        var wasCalled = false

        override suspend fun match(bitmap: Bitmap, bookId: String): PageMatchResult? {
            wasCalled = true
            return null
        }
    }
}

/**
 * Unit tests for PageMatchResult data class.
 */
class PageMatchResultTest {

    @Test
    fun `PageMatchResult stores all fields correctly`() {
        // Given a result
        val result = PageMatchResult(
            pageNumber = 5,
            text = "Hello World",
            usedStoredText = true,
            strategy = "ORB"
        )

        // Then fields are accessible
        assertEquals(5, result.pageNumber)
        assertEquals("Hello World", result.text)
        assertTrue(result.usedStoredText)
        assertEquals("ORB", result.strategy)
    }

    @Test
    fun `PageMatchResult usedStoredText is false for OCR strategy`() {
        // Given a result from OCR strategy
        val result = PageMatchResult(
            pageNumber = 0,
            text = "OCR Text",
            usedStoredText = false,
            strategy = "OcrService"
        )

        // Then usedStoredText is false
        assertFalse(result.usedStoredText)
    }
}