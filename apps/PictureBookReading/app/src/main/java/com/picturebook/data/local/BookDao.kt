package com.picturebook.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.picturebook.data.local.entity.BookEntity
import com.picturebook.data.local.entity.BookPageEntity

@Dao
interface BookDao {
    // Book operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Query("SELECT * FROM books WHERE bookId = :bookId")
    suspend fun getBookById(bookId: String): BookEntity?

    @Query("SELECT * FROM books WHERE isPlaceholder = 0")
    suspend fun getAllRealBooks(): List<BookEntity>

    @Query("SELECT COUNT(*) FROM books WHERE isPlaceholder = 0")
    suspend fun getBookCount(): Int

    // Page operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<BookPageEntity>)

    @Query("SELECT * FROM book_pages WHERE bookId = :bookId ORDER BY pageNumber")
    suspend fun getPagesByBookId(bookId: String): List<BookPageEntity>

    @Query("DELETE FROM book_pages WHERE bookId = :bookId")
    suspend fun deletePagesByBookId(bookId: String)

    // FTS search - returns page matches with fullText for Jaccard scoring
    @Query("""
        SELECT bp.bookId, bp.pageNumber, bp.fullText
        FROM book_pages bp
        JOIN book_pages_fts fts ON bp.rowid = fts.rowid
        WHERE book_pages_fts MATCH :query
        AND bp.bookId NOT IN (SELECT bookId FROM books WHERE isPlaceholder = 1)
        LIMIT 50
    """)
    suspend fun searchCandidates(query: String): List<BookPageMatch>
}

data class BookPageMatch(
    val bookId: String,
    val pageNumber: Int,
    val fullText: String
)
