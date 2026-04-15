# 本地优先绘本匹配方案

## 1. 概述

改变现有绘本识别流程：点"朗读"时先尝试本地 OCR 文字匹配，找不到再调 API。

**核心变化：**
- 朗读按钮触发：OCR → 本地 FTS 搜索 → API fallback
- API 一次返回完整绘本数据（含所有 page 的 text）
- 未识别绘本存 placeholder，避免重复调用 API

## 2. 识别流程

```
用户点"朗读"
    │
    ├── ML Kit OCR 提取文字
    │
    ├── 有文字
    │     │
    │     ├── 本地 Room FTS + Jaccard 搜索
    │     │     ├── 相似度 ≥ 0.85 → 返回本地数据 → 朗读
    │     │     └── 相似度 < 0.85 → 调用 API
    │     │
    │     └── 调用 API
    │           ├── 找到 → 存本地 → 朗读
    │           └── 找不到 → 存 placeholder → 提示"未识别到文字"
    │
    └── 无文字 → 直接调用 API
                  ├── 找到 → 存本地 → 朗读
                  └── 找不到 → 存 placeholder → 提示"未识别到绘本"
```

## 3. API 扩展

### POST /api/books/match

**输入：**
```json
{
  "imageBase64": "base64 encoded image"
}
```

**输出（扩展）：**
```json
{
  "success": true,
  "book": {
    "id": "uuid",
    "title": "书名",
    "pages": [
      {
        "pageNumber": 1,
        "fullText": "第一页文字内容"
      },
      {
        "pageNumber": 2,
        "fullText": "第二页文字内容"
      }
    ]
  },
  "similarity": 0.92,
  "isPlaceholder": false
}
```

**isPlaceholder = true** 时表示数据库中没有这本书，只有封面匹配结果。

## 4. 本地存储结构

### Room Entity

```
BookEntity
  - bookId: String (PK)
  - title: String
  - isPlaceholder: Boolean (default false)
  - updatedAt: Long

BookPageEntity
  - id: Long (PK, auto-generate)
  - bookId: String (FK)
  - pageNumber: Int
  - fullText: String

BookTextFtsEntity (FTS4)
  - rowid: Long (FK to BookPageEntity)
  - fullText: String
```

### 搜索策略

1. **FTS 粗筛**：用 OCR text 分词后做 FTS 查询，返回候选集
2. **Jaccard 精筛**：计算 OCR tokens 与候选 page tokens 的 Jaccard 相似度
3. **阈值**：相似度 ≥ 0.85 才算匹配

### Tokenization

- **中文**：bigram 分词（"你好世界" → "你好", "好世", "世界"）
- **英文**：word 分词（"hello world" → "hello", "world"）

## 5. 项目结构

```
apps/PictureBookReading/
├── app/
│   └── src/main/java/com/picturebook/
│       ├── data/local/
│       │   ├── entity/
│       │   │   ├── BookEntity.kt
│       │   │   ├── BookPageEntity.kt
│       │   │   └── BookTextFtsEntity.kt
│       │   ├── BookDao.kt
│       │   └── BookDatabase.kt
│       ├── domain/model/
│       │   ├── BookMatchResult.kt
│       │   ├── BookDetails.kt
│       │   └── PageDetails.kt
│       ├── infrastructure/ai/
│       │   ├── MlKitOcrClient.kt          # 新增
│       │   ├── LocalBookSearchService.kt   # 新增
│       │   ├── BookRepository.kt           # 新增
│       │   └── BookMatchingClient.kt       # 修改：支持返回完整数据
│       └── presentation/ui/
│           └── MainScreen.kt               # 修改：调用新流程
```

## 6. 组件设计

### MlKitOcrClient
- 封装 ML Kit Text Recognition
- `suspend fun recognize(bitmap: Bitmap): OcrResult?`
- `OcrResult` 包含 `fullText: String` 和 `blocks: List<TextBlock>`

### LocalBookSearchService
- `suspend fun search(ocrText: String): BookMatchResult?`
- FTS 粗筛 + Jaccard 精筛
- 返回匹配结果或 null

### BookRepository
- 协调 OCR、本地搜索、API 调用
- `suspend fun searchBook(bitmap: Bitmap): BookMatchResult?`
- `suspend fun getBookDetails(bookId: String): BookDetails?`
- `suspend fun saveBook(bookDetails: BookDetails)`

## 7. 关键阈值

| 相似度 | 操作 |
|--------|------|
| ≥ 0.85 | 使用本地结果 |
| < 0.85 | 调用 API |
| API 也找不到 | 存 placeholder |

## 8. Placeholder 处理

placeholder 存入 Room 后：
- `isPlaceholder = true`，`title = ""`，`pages = []`
- 下次 OCR 搜索时，如果 FTS 匹配到的是 placeholder，跳过并继续调 API
- 不朗读 placeholder 数据

## 9. 依赖

### build.gradle.kts 新增
```kotlin
// Room
implementation("androidx.room:room-runtime:$roomVersion")
implementation("androidx.room:room-ktx:$roomVersion")
ksp("androidx.room:room-compiler:$roomVersion")

// ML Kit Text Recognition
implementation("com.google.mlkit:text-recognition:16.0.0")
```

## 10. BookMatchingClient 修改

现有 `matchBook(bitmap)` 只返回 `{bookId, title, similarity}`。

新版本需要支持返回完整 `BookDetails`（含 pages），由 API 端扩展返回结构。

如果 API 尚未支持返回完整数据，先用现有 match 接口 + 额外 GET 请求获取完整数据。
