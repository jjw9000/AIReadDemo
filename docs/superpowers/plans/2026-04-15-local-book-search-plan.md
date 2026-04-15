# Local Book Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement local-first book matching: ML Kit OCR → local FTS search → API fallback

**Architecture:** Android app uses Room FTS + Jaccard similarity for local book search. ML Kit OCR extracts text, searched against locally cached books. Falls back to API for unmatched books.

**Tech Stack:** Room, ML Kit Text Recognition, Kotlin Coroutines

---

## File Map

```
apps/PictureBookReading/app/src/main/java/com/picturebook/
├── data/local/
│   ├── entity/
│   │   ├── BookEntity.kt
│   │   ├── BookPageEntity.kt
│   │   └── BookTextFtsEntity.kt
│   ├── BookDao.kt
│   └── BookDatabase.kt
├── domain/model/
│   ├── BookMatchResult.kt
│   ├── BookDetails.kt
│   └── PageDetails.kt
└── infrastructure/ai/
    ├── MlKitOcrClient.kt
    ├── LocalBookSearchService.kt
    ├── BookRepository.kt
    └── BookMatchingClient.kt (modify)
```

---

### Task 1: Add Dependencies

**Files:**
- Modify: `apps/PictureBookReading/app/build.gradle.kts`

- [ ] **Step 1: Add Room and ML Kit dependencies**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.picturebook"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.picturebook"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Network
    implementation("com.google.code.gson:gson:2.10.1")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/PictureBookReading/app/build.gradle.kts
git commit -m "build: add Room and ML Kit dependencies"
```

---

### Task 2: Create Entity Classes

**Files:**
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/data/local/entity/BookEntity.kt`
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/data/local/entity/BookPageEntity.kt`
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/data/local/entity/BookTextFtsEntity.kt`

- [ ] **Step 1: Create BookEntity**

```kotlin
package com.picturebook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val bookId: String,
    val title: String,
    val isPlaceholder: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: Create BookPageEntity**

```kotlin
package com.picturebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_pages",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["bookId"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class BookPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val pageNumber: Int,
    val fullText: String
)
```

- [ ] **Step 3: Create BookTextFtsEntity**

```kotlin
package com.picturebook.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = BookPageEntity::class)
@Entity(tableName = "book_pages_fts")
data class BookTextFtsEntity(
    val fullText: String
)
```

- [ ] **Step 4: Commit**

```bash
git add apps/PictureBookReading/app/src/main/java/com/picturebook/data/local/entity/
git commit -m "feat: add Room entity classes for books and pages"
```

---

### Task 3: Create BookDao

**Files:**
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/data/local/BookDao.kt`

- [ ] **Step 1: Create BookDao**

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add apps/PictureBookReading/app/src/main/java/com/picturebook/data/local/BookDao.kt
git commit -m "feat: add BookDao with FTS search query"
```

---

### Task 4: Create BookDatabase

**Files:**
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/data/local/BookDatabase.kt`

- [ ] **Step 1: Create BookDatabase**

```kotlin
package com.picturebook.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.picturebook.data.local.entity.BookEntity
import com.picturebook.data.local.entity.BookPageEntity
import com.picturebook.data.local.entity.BookTextFtsEntity

@Database(
    entities = [BookEntity::class, BookPageEntity::class, BookTextFtsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: BookDatabase? = null

        fun getInstance(context: Context): BookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookDatabase::class.java,
                    "picturebook_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/PictureBookReading/app/src/main/java/com/picturebook/data/local/BookDatabase.kt
git commit -m "feat: add BookDatabase singleton"
```

---

### Task 5: Create Domain Models

**Files:**
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/domain/model/BookMatchResult.kt`
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/domain/model/BookDetails.kt`
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/domain/model/PageDetails.kt`

- [ ] **Step 1: Create BookMatchResult**

```kotlin
package com.picturebook.domain.model

data class BookMatchResult(
    val bookId: String,
    val title: String,
    val pageNumber: Int,
    val similarity: Float,
    val isLocal: Boolean
)
```

- [ ] **Step 2: Create PageDetails**

```kotlin
package com.picturebook.domain.model

data class PageDetails(
    val pageNumber: Int,
    val fullText: String
)
```

- [ ] **Step 3: Create BookDetails**

```kotlin
package com.picturebook.domain.model

data class BookDetails(
    val bookId: String,
    val title: String,
    val pages: List<PageDetails>
)
```

- [ ] **Step 4: Commit**

```bash
git add apps/PictureBookReading/app/src/main/java/com/picturebook/domain/model/
git commit -m "feat: add domain models for book matching"
```

---

### Task 6: Create MlKitOcrClient

**Files:**
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/MlKitOcrClient.kt`

- [ ] **Step 1: Create MlKitOcrClient**

```kotlin
package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MlKitOcrClient {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrResult? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text.trim()
                if (fullText.isNotEmpty()) {
                    val blocks = visionText.textBlocks.map { block ->
                        TextBlock(
                            text = block.text,
                            confidence = block.lines.firstOrNull()?.confidence ?: 0f,
                            boundingBox = block.boundingBox
                        )
                    }
                    cont.resume(OcrResult(blocks, fullText))
                } else {
                    cont.resume(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit OCR failed: ${e.message}")
                cont.resume(null)
            }
    }

    data class OcrResult(
        val textBlocks: List<TextBlock>,
        val fullText: String
    )

    data class TextBlock(
        val text: String,
        val confidence: Float,
        val boundingBox: android.graphics.Rect?
    )

    companion object {
        private const val TAG = "MlKitOcrClient"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/MlKitOcrClient.kt
git commit -m "feat: add ML Kit OCR client wrapper"
```

---

### Task 7: Create LocalBookSearchService

**Files:**
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/LocalBookSearchService.kt`

- [ ] **Step 1: Create LocalBookSearchService**

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/LocalBookSearchService.kt
git commit -m "feat: add LocalBookSearchService with FTS + Jaccard"
```

---

### Task 8: Create BookRepository

**Files:**
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/BookRepository.kt`

- [ ] **Step 1: Create BookRepository**

```kotlin
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
            Log.d(TAG, "Server match: ${serverResult.bookId}, similarity: ${serverResult.similarity}")
            val result = BookMatchResult(
                bookId = serverResult.bookId,
                title = serverResult.title,
                similarity = serverResult.similarity,
                isLocal = false
            )
            // Cache book from server
            if (serverResult.pages.isNotEmpty()) {
                cacheBookFromServer(serverResult)
            } else {
                // Save placeholder for unmatched books
                savePlaceholder(serverResult.bookId)
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
```

- [ ] **Step 2: Commit**

```bash
git add apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/BookRepository.kt
git commit -m "feat: add BookRepository orchestrating OCR, local search, API"
```

---

### Task 9: Update BookMatchingClient

**Files:**
- Modify: `apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/BookMatchingClient.kt`

- [ ] **Step 1: Add BookDto and page support to BookMatchingClient**

```kotlin
package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.coroutines.resume

class BookMatchingClient(
    private val apiBaseUrl: String = "http://192.168.0.100:5018"
) {
    private val gson = Gson()

    suspend fun matchBook(bitmap: Bitmap): BookDto? = suspendCancellableCoroutine { cont ->
        try {
            val base64 = bitmapToBase64(bitmap)
            val requestBody = mapOf("imageBase64" to base64)
            val json = gson.toJson(requestBody)

            val url = URL("$apiBaseUrl/api/books/match")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            connection.outputStream.use { os ->
                os.write(json.toByteArray())
            }

            val responseCode = connection.responseCode
            Log.i(TAG, "Book match API response code: $responseCode")

            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                Log.i(TAG, "Book match API response: $response")

                val matchResponse = gson.fromJson(response, MatchResponse::class.java)
                if (matchResponse != null && matchResponse.success && matchResponse.book != null) {
                    cont.resume(matchResponse.book)
                } else {
                    cont.resume(null)
                }
            } else {
                Log.e(TAG, "Book match API error: $responseCode")
                cont.resume(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Book match failed: ${e.message}")
            cont.resume(null)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }

    data class BookDto(
        val id: String,
        val title: String,
        val pages: List<PageDto>,
        val similarity: Float = 0f,
        val isPlaceholder: Boolean = false
    )

    data class PageDto(
        val pageNumber: Int,
        val fullText: String
    )

    data class MatchResponse(
        val success: Boolean,
        val book: BookDto?,
        val error: String?
    )

    companion object {
        private const val TAG = "BookMatchingClient"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/BookMatchingClient.kt
git commit -m "feat: extend BookMatchingClient to return BookDto with pages"
```

---

### Task 10: Update MainScreen

**Files:**
- Modify: `apps/PictureBookReading/app/src/main/java/com/picturebook/presentation/ui/MainScreen.kt`

- [ ] **Step 1: Add BookRepository field and update captureAndRead**

Find the section with:
```kotlin
val audioService = remember { AudioService(context) }
val httpOcrClient = remember { HttpOcrClient() }
val bookMatchingClient = remember { BookMatchingClient() }
```

Replace with:
```kotlin
val audioService = remember { AudioService(context) }
val bookRepository = remember { BookRepository(context) }
```

- [ ] **Step 2: Update captureAndRead to use new flow**

Find the `captureAndRead` function parameters and update the body:

```kotlin
private fun captureAndRead(
    context: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView?,
    bookRepository: BookRepository,
    audioService: AudioService,
    scope: CoroutineScope,
    onTextRecognized: (String) -> Unit,
    onBookFound: (String) -> Unit,
    onError: () -> Unit
) {
    var imageCapture: ImageCapture? = null

    if (imageCapture == null) {
        bindCamera(context, lifecycleOwner, previewView) { cap ->
            imageCapture = cap
            doCaptureAndRead(context, cap, bookRepository, audioService, scope, onTextRecognized, onBookFound, onError)
        }
    } else {
        imageCapture?.let {
            doCaptureAndRead(context, it, bookRepository, audioService, scope, onTextRecognized, onBookFound, onError)
        }
    }
}

private fun doCaptureAndRead(
    context: android.content.Context,
    imageCapture: ImageCapture,
    bookRepository: BookRepository,
    audioService: AudioService,
    scope: CoroutineScope,
    onTextRecognized: (String) -> Unit,
    onBookFound: (String) -> Unit,
    onError: () -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.toBitmap()
                image.close()
                scope.launch(Dispatchers.IO) {
                    val matchResult = bookRepository.searchBook(bitmap)
                    withContext(Dispatchers.Main) {
                        if (matchResult != null) {
                            val bookDetails = bookRepository.getBookDetails(matchResult.bookId)
                            if (bookDetails != null && bookDetails.pages.isNotEmpty()) {
                                val currentPage = bookDetails.pages.find { it.pageNumber == matchResult.pageNumber }
                                    ?: bookDetails.pages.firstOrNull()
                                if (currentPage != null && currentPage.fullText.isNotBlank()) {
                                    onTextRecognized(currentPage.fullText)
                                } else {
                                    onBookFound(matchResult.title)
                                }
                            } else {
                                onBookFound(matchResult.title)
                            }
                        } else {
                            onError()
                        }
                    }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("MainScreen", "Capture failed: ${exception.message}")
                scope.launch(Dispatchers.Main) { onError() }
            }
        }
    )
}
```

- [ ] **Step 3: Update button call site**

Find the "朗读本页" button:
```kotlin
captureAndRead(
    context, lifecycleOwner, previewView,
    bookRepository, audioService, scope,
    onTextRecognized = { text -> ... },
    onBookFound = { name -> ... },
    onError = { ... }
)
```

Replace with:
```kotlin
captureAndRead(
    context, lifecycleOwner, previewView,
    bookRepository, audioService, scope,
    onTextRecognized = { text -> ... },
    onBookFound = { name -> ... },  // Add callback for book recognized without text
    onError = { ... }
)
```

- [ ] **Step 4: Commit**

```bash
git add apps/PictureBookReading/app/src/main/java/com/picturebook/presentation/ui/MainScreen.kt
git commit -m "feat: integrate BookRepository into MainScreen read flow"
```

---

## Verification

After all tasks, build the project:
```bash
cd apps/PictureBookReading
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

## Spec Coverage Check

| Spec Requirement | Task |
|------------------|------|
| ML Kit OCR | Task 6 |
| Room FTS search | Tasks 2, 3, 7 |
| Jaccard similarity | Task 7 |
| API fallback | Task 8 |
| Placeholder storage | Task 8 |
| BookRepository orchestration | Task 8 |
| MainScreen integration | Task 10 |
| API returns pages | Task 9 |

All spec requirements covered.
