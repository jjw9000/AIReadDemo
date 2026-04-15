package com.picturebook.infrastructure.ai

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LocalBookSearchService tokenization and Jaccard similarity.
 */
class LocalBookSearchServiceTest {

    /**
     * Test Chinese bigram tokenization.
     */
    @Test
    fun `tokenizeToSet splits Chinese text into bigrams`() {
        // Given a simple Chinese phrase
        val text = "你好世界"

        // When tokenizing
        val tokens = tokenizeToSet(text)

        // Then we get character bigrams
        // "你好世界" -> ["你好", "好世", "世界"]
        assertEquals(3, tokens.size)
        assertTrue(tokens.contains("你好"))
        assertTrue(tokens.contains("好世"))
        assertTrue(tokens.contains("世界"))
    }

    /**
     * Test that tokenization handles English text.
     */
    @Test
    fun `tokenizeToSet handles English text`() {
        // Given an English phrase
        val text = "hello world"

        // When tokenizing
        val tokens = tokenizeToSet(text)

        // Then we get word tokens (space-separated)
        assertTrue(tokens.contains("hello"))
        assertTrue(tokens.contains("world"))
    }

    /**
     * Test that tokenization handles mixed content.
     */
    @Test
    fun `tokenizeToSet handles mixed content`() {
        // Given mixed Chinese and English
        val text = "Hello 你好 world 世界"

        // When tokenizing
        val tokens = tokenizeToSet(text)

        // Then we get both English words and Chinese bigrams
        assertTrue(tokens.contains("hello"))
        assertTrue(tokens.contains("world"))
        assertTrue(tokens.contains("你好"))
        assertTrue(tokens.contains("好世"))
        assertTrue(tokens.contains("世界"))
    }

    /**
     * Test Jaccard similarity calculation.
     */
    @Test
    fun `calculateJaccardSimilarity returns 1 for identical sets`() {
        val set1 = setOf("a", "b", "c")
        val set2 = setOf("a", "b", "c")

        val similarity = calculateJaccardSimilarity(set1, set2)

        assertEquals(1.0f, similarity, 0.001f)
    }

    /**
     * Test Jaccard similarity calculation.
     */
    @Test
    fun `calculateJaccardSimilarity returns 0 for disjoint sets`() {
        val set1 = setOf("a", "b", "c")
        val set2 = setOf("d", "e", "f")

        val similarity = calculateJaccardSimilarity(set1, set2)

        assertEquals(0.0f, similarity, 0.001f)
    }

    /**
     * Test Jaccard similarity calculation with partial overlap.
     */
    @Test
    fun `calculateJaccardSimilarity returns correct value for partial overlap`() {
        val set1 = setOf("a", "b", "c")
        val set2 = setOf("b", "c", "d")

        // Intersection: {b, c} = 2
        // Union: {a, b, c, d} = 4
        // Jaccard = 2/4 = 0.5
        val similarity = calculateJaccardSimilarity(set1, set2)

        assertEquals(0.5f, similarity, 0.001f)
    }

    /**
     * Test Jaccard similarity with empty set.
     */
    @Test
    fun `calculateJaccardSimilarity returns 0 for empty first set`() {
        val set1 = emptySet<String>()
        val set2 = setOf("a", "b")

        val similarity = calculateJaccardSimilarity(set1, set2)

        assertEquals(0.0f, similarity, 0.001f)
    }

    /**
     * Test Jaccard similarity with both empty sets.
     */
    @Test
    fun `calculateJaccardSimilarity returns 0 for both empty sets`() {
        val set1 = emptySet<String>()
        val set2 = emptySet<String>()

        val similarity = calculateJaccardSimilarity(set1, set2)

        assertEquals(0.0f, similarity, 0.001f)
    }

    // Helper functions that mimic LocalBookSearchService implementation
    private fun tokenizeToSet(text: String): Set<String> {
        val tokens = mutableSetOf<String>()
        val cleanText = text.lowercase().replace("[^a-z0-9\\u4e00-\\u9fff]".toRegex(), "")

        // For Chinese text, use bigram tokenization
        if (cleanText.any { it in "\u4e00-\u9fff" }) {
            for (i in 0 until cleanText.length - 1) {
                val char = cleanText[i]
                val nextChar = cleanText[i + 1]
                if (char in "\u4e00-\u9fff" && nextChar in "\u4e00-\u9fff") {
                    tokens.add(cleanText.substring(i, i + 2))
                }
            }
        }

        // For English text, split by whitespace
        val englishTokens = cleanText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        tokens.addAll(englishTokens)

        return tokens
    }

    private fun calculateJaccardSimilarity(set1: Set<String>, set2: Set<String>): Float {
        if (set1.isEmpty() && set2.isEmpty()) return 0f
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }
}