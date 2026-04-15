package com.picturebook.infrastructure.ai

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Result sealed class.
 */
class ResultTest {

    @Test
    fun `Success contains data`() {
        val result: Result<Int> = Result.Success(42)

        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `Error contains message`() {
        val result: Result<Int> = Result.Error("Something went wrong")

        assertFalse(result.isSuccess)
        assertTrue(result.isError)
        assertNull(result.getOrNull())
        assertEquals("Something went wrong", result.message)
    }

    @Test
    fun `Error with cause`() {
        val cause = RuntimeException("Original error")
        val result: Result<Int> = Result.Error("Failed", cause)

        assertEquals("Failed", result.message)
        assertEquals(cause, result.cause)
    }

    @Test
    fun `map transforms Success`() {
        val result: Result<Int> = Result.Success(21)
        val mapped = result.map { it * 2 }

        assertTrue(mapped is Result.Success)
        assertEquals(42, (mapped as Result.Success).data)
    }

    @Test
    fun `map does not transform Error`() {
        val result: Result<Int> = Result.Error("Failed")
        val mapped = result.map { it * 2 }

        assertTrue(mapped is Result.Error)
    }

    @Test
    fun `onSuccess executes only for Success`() {
        var called = false
        val result: Result<Int> = Result.Success(42)
        result.onSuccess { called = true }

        assertTrue(called)
    }

    @Test
    fun `onSuccess does not execute for Error`() {
        var called = false
        val result: Result<Int> = Result.Error("Failed")
        result.onSuccess { called = true }

        assertFalse(called)
    }

    @Test
    fun `onError executes only for Error`() {
        var message: String? = null
        val result: Result<Int> = Result.Error("Failed")
        result.onError { message = it }

        assertEquals("Failed", message)
    }

    @Test
    fun `onError does not execute for Success`() {
        var called = false
        val result: Result<Int> = Result.Success(42)
        result.onError { called = true }

        assertFalse(called)
    }

    @Test
    fun `getOrThrow returns data for Success`() {
        val result: Result<Int> = Result.Success(42)

        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun `getOrThrow throws for Error`() {
        val result: Result<Int> = Result.Error("Failed")

        try {
            result.getOrThrow()
            fail("Expected exception")
        } catch (e: RuntimeException) {
            assertEquals("Failed", e.message)
        }
    }
}