# 无文字绘本识别实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现无文字绘本识别功能 - 当 OCR 无结果时，通过图像特征向量匹配识别绘本

**Architecture:**
- BookManagementService (.NET 10) 提供 REST API，处理向量存储和搜索
- PostgreSQL + pgvector 存储 CLIP 图像特征向量 (512维)
- CLIP 模型提取图像特征（通过 ONNX Runtime .NET 版本，CPU 可运行）
- Android App 在 OCR 无结果时调用匹配接口

**Tech Stack:**
- .NET 10, PostgreSQL 16 + pgvector, ONNX Runtime (CLIP), Kotlin + Jetpack Compose

---

## 文件结构

```
services/
├── BookManagementService/           # 新建 - .NET 10 服务
│   ├── BookManagementService.csproj
│   ├── Program.cs
│   ├── appsettings.json
│   ├── Controllers/
│   │   └── BooksController.cs
│   ├── DTOs/
│   │   └── BookDtos.cs
│   ├── Services/
│   │   ├── IClipService.cs
│   │   └── ClipService.cs          # CLIP 特征提取 (ONNX)
│   └── Data/
│       └── BookDbContext.cs
│
├── OcrServicePython/                # 现有 - CLIP 可复用
│   └── (可选: 如果 ONNX CLIP 不可用，调用此服务)
│
apps/PictureBookReading/
├── infrastructure/ai/
│   └── BookMatchingClient.kt        # 新增
```

---

## Task 1: 创建 BookManagementService 项目结构

**Files:**
- Create: `services/BookManagementService/BookManagementService.csproj`
- Create: `services/BookManagementService/Program.cs`
- Create: `services/BookManagementService/appsettings.json`
- Create: `services/BookManagementService/Properties/launchSettings.json`

- [ ] **Step 1: 创建 csproj 文件**

```xml
<Project Sdk="Microsoft.NET.Sdk.Web">

  <PropertyGroup>
    <TargetFramework>net10.0</TargetFramework>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
    <RootNamespace>BookManagementService</RootNamespace>
  </PropertyGroup>

  <ItemGroup>
    <PackageReference Include="Microsoft.AspNetCore.OpenApi" Version="10.0.1" />
    <PackageReference Include="Npgsql.EntityFrameworkCore.PostgreSQL" Version="10.0.2" />
    <PackageReference Include="pgvector.EntityFrameworkCore.PostgreSQL" Version="10.1.1" />
    <PackageReference Include="Microsoft.ML.OnnxRuntime" Version="1.19.2" />
    <PackageReference Include="Microsoft.Extensions.Http" Version="8.0.1" />
  </ItemGroup>

</Project>
```

- [ ] **Step 2: 创建 Program.cs**

```csharp
using BookManagementService.Services;
using BookManagementService.Data;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

// Add services
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddOpenApi();

// Database
var connectionString = builder.Configuration.GetConnectionString("DefaultConnection")
    ?? "Host=localhost;Database=bookdb;Username=postgres;Password=postgres";
builder.Services.AddDbContext<BookDbContext>(options =>
    options.UseNpgsql(connectionString));

// CLIP Service (ONNX)
builder.Services.AddSingleton<IClipService, ClipService>();

// CORS
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader();
    });
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseCors();
app.MapControllers();

// Health check
app.MapGet("/", () => Results.Ok(new { service = "BookManagementService", version = "1.0.0", status = "running" }));

app.Run();
```

- [ ] **Step 3: 创建 appsettings.json**

```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning"
    }
  },
  "AllowedHosts": "*",
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Database=bookdb;Username=postgres;Password=postgres"
  },
  "ClipService": {
    "ModelPath": "models/clip-vit-base-patch32.onnx",
    "Dimension": 512
  }
}
```

- [ ] **Step 4: 创建 launchSettings.json**

```json
{
  "$schema": "http://json.schemastore.org/launchsettings.json",
  "profiles": {
    "BookManagementService": {
      "commandName": "Project",
      "dotnetRunMessages": true,
      "launchBrowser": false,
      "applicationUrl": "http://localhost:5018",
      "environmentVariables": {
        "ASPNETCORE_ENVIRONMENT": "Development"
      }
    }
  }
}
```

- [ ] **Step 5: 提交**

```bash
git add services/BookManagementService/
git commit -m "feat: create BookManagementService project structure

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 2: 创建 DTOs 和数据模型

**Files:**
- Create: `services/BookManagementService/DTOs/BookDtos.cs`
- Create: `services/BookManagementService/Entities/Book.cs`

- [ ] **Step 1: 创建 Book.cs 实体**

```csharp
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace BookManagementService.Entities;

[Table("books")]
public class Book
{
    [Key]
    [Column("id")]
    public Guid Id { get; set; } = Guid.NewGuid();

    [Required]
    [Column("title")]
    [MaxLength(255)]
    public string Title { get; set; } = string.Empty;

    [Column("cover_embedding")]
    public float[]? CoverEmbedding { get; set; }

    [Column("metadata", TypeName = "jsonb")]
    public string? Metadata { get; set; }

    [Column("created_at")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [Column("updated_at")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
```

- [ ] **Step 2: 创建 BookDtos.cs**

```csharp
using System.ComponentModel.DataAnnotations;

namespace BookManagementService.DTOs;

// Request DTOs
public class MatchRequest
{
    [Required]
    public string ImageBase64 { get; set; } = string.Empty;
}

public class RegisterRequest
{
    [Required]
    public string ImageBase64 { get; set; } = string.Empty;

    [Required]
    [MaxLength(255)]
    public string Title { get; set; } = string.Empty;

    public Dictionary<string, object>? Metadata { get; set; }
}

// Response DTOs
public class BookMatchResult
{
    public Guid Id { get; set; }
    public string Title { get; set; } = string.Empty;
    public float Similarity { get; set; }
    public Dictionary<string, object>? Metadata { get; set; }
}

public class MatchResponse
{
    public bool Success { get; set; }
    public List<BookMatchResult> Books { get; set; } = new();
    public string? Error { get; set; }
}

public class RegisterResponse
{
    public bool Success { get; set; }
    public Guid? Id { get; set; }
    public string? Error { get; set; }
}

public class BookDetailResponse
{
    public Guid Id { get; set; }
    public string Title { get; set; } = string.Empty;
    public Dictionary<string, object>? Metadata { get; set; }
    public DateTime CreatedAt { get; set; }
}

public class HealthResponse
{
    public string Status { get; set; } = "healthy";
    public string Service { get; set; } = "BookManagementService";
    public string Version { get; set; } = "1.0.0";
}
```

- [ ] **Step 3: 提交**

```bash
git add services/BookManagementService/DTOs/BookDtos.cs services/BookManagementService/Entities/Book.cs
git commit -m "feat: add Book entity and DTOs

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 3: 创建 DbContext

**Files:**
- Create: `services/BookManagementService/Data/BookDbContext.cs`

- [ ] **Step 1: 创建 BookDbContext.cs**

```csharp
using Microsoft.EntityFrameworkCore;
using BookManagementService.Entities;

namespace BookManagementService.Data;

public class BookDbContext : DbContext
{
    public BookDbContext(DbContextOptions<BookDbContext> options) : base(options)
    {
    }

    public DbSet<Book> Books { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.HasPostgresExtension("vector");

        modelBuilder.Entity<Book>(entity =>
        {
            entity.HasKey(e => e.Id);

            entity.Property(e => e.Id)
                .HasDefaultValueSql("gen_random_uuid()");

            entity.Property(e => e.Title)
                .IsRequired()
                .HasMaxLength(255);

            entity.Property(e => e.CreatedAt)
                .HasDefaultValueSql("NOW()");

            entity.Property(e => e.UpdatedAt)
                .HasDefaultValueSql("NOW()");

            // Vector indexing for similarity search
            // Note: IVFFlat index creation requires raw SQL migration
        });
    }
}
```

- [ ] **Step 2: 创建数据库迁移脚本**

```sql
-- Run this manually or via EF migrations
CREATE EXTENSION IF NOT EXISTS vector;

-- Books table
CREATE TABLE IF NOT EXISTS books (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    cover_embedding vector(512),
    metadata jsonb,
    created_at timestamp DEFAULT NOW(),
    updated_at timestamp DEFAULT NOW()
);

-- IVFFlat index for cosine similarity search
CREATE INDEX IF NOT EXISTS idx_books_cover_embedding ON books USING ivfflat (cover_embedding vector_cosine_ops) WITH (lists = 100);
```

- [ ] **Step 3: 提交**

```bash
git add services/BookManagementService/Data/BookDbContext.cs
git commit -m "feat: add BookDbContext with pgvector support

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 4: 实现 CLIP 特征提取服务

**Files:**
- Create: `services/BookManagementService/Services/IClipService.cs`
- Create: `services/BookManagementService/Services/ClipService.cs`

- [ ] **Step 1: 创建 IClipService.cs**

```csharp
namespace BookManagementService.Services;

public interface IClipService
{
    Task<float[]> ExtractFeaturesAsync(string imageBase64);
}
```

- [ ] **Step 2: 创建 ClipService.cs (ONNX 实现)**

```csharp
using Microsoft.ML.OnnxRuntime;
using Microsoft.ML.OnnxRuntime.Tensors;
using System.Drawing;
using System.Drawing.Imaging;

namespace BookManagementService.Services;

public class ClipService : IClipService, IDisposable
{
    private readonly InferenceSession _session;
    private readonly ILogger<ClipService> _logger;
    private readonly int _imageSize = 224;

    public ClipService(ILogger<ClipService> logger, IConfiguration configuration)
    {
        _logger = logger;
        var modelPath = configuration["ClipService:ModelPath"] ?? "models/clip-vit-base-patch32.onnx";
        
        var sessionOptions = new SessionOptions();
        sessionOptions.GraphOptimizationLevel = GraphOptimizationLevel.ORT_ENABLE_ALL;
        _session = new InferenceSession(modelPath, sessionOptions);
        
        _logger.LogInformation("CLIP ONNX model loaded from {ModelPath}", modelPath);
    }

    public async Task<float[]> ExtractFeaturesAsync(string imageBase64)
    {
        return await Task.Run(() =>
        {
            try
            {
                // Handle data URI format
                if (imageBase64.Contains(','))
                {
                    imageBase64 = imageBase64.Split(',')[1];
                }

                var imageBytes = Convert.FromBase64String(imageBase64);
                using var ms = new MemoryStream(imageBytes);
                using var bitmap = new Bitmap(ms);

                // Preprocess: resize to 224x224 and normalize
                var tensor = PreprocessImage(bitmap);

                // Run inference
                var inputs = new[]
                {
                    NamedOnnxValue.CreateFromTensor("input", tensor)
                };

                using var results = _session.Run(inputs);
                var output = results.FirstOrDefault();

                if (output == null)
                {
                    throw new InvalidOperationException("CLIP inference returned no output");
                }

                var embedding = output.AsEnumerable<float>().ToArray();
                
                // L2 normalize
                var norm = (float)Math.Sqrt(embedding.Sum(x => x * x));
                for (int i = 0; i < embedding.Length; i++)
                {
                    embedding[i] /= norm;
                }

                _logger.LogDebug("Extracted CLIP features: dimension={Dimension}", embedding.Length);
                return embedding;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to extract CLIP features");
                throw;
            }
        });
    }

    private Tensor<float> PreprocessImage(Bitmap bitmap)
    {
        using var resized = new Bitmap(bitmap, new Size(_imageSize, _imageSize));
        var tensor = new DenseTensor<float>(new[] { 3, _imageSize, _imageSize });

        // ImageNet normalization
        float[] mean = { 0.48145466f, 0.4578275f, 0.40821073f };
        float[] std = { 0.26862954f, 0.26130258f, 0.27577711f };

        for (int y = 0; y < _imageSize; y++)
        {
            for (int x = 0; x < _imageSize; x++)
            {
                var pixel = resized.GetPixel(x, y);
                tensor[0, y, x] = (pixel.R / 255f - mean[0]) / std[0];
                tensor[1, y, x] = (pixel.G / 255f - mean[1]) / std[1];
                tensor[2, y, x] = (pixel.B / 255f - mean[2]) / std[2];
            }
        }

        return tensor;
    }

    public void Dispose()
    {
        _session?.Dispose();
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add services/BookManagementService/Services/IClipService.cs services/BookManagementService/Services/ClipService.cs
git commit -m "feat: implement CLIP feature extraction service using ONNX Runtime

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 5: 实现 BooksController

**Files:**
- Create: `services/BookManagementService/Controllers/BooksController.cs`

- [ ] **Step 1: 创建 BooksController.cs**

```csharp
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using BookManagementService.Data;
using BookManagementService.DTOs;
using BookManagementService.Entities;
using BookManagementService.Services;

namespace BookManagementService.Controllers;

[ApiController]
[Route("api/[controller]")]
public class BooksController : ControllerBase
{
    private readonly BookDbContext _context;
    private readonly IClipService _clipService;
    private readonly ILogger<BooksController> _logger;

    public BooksController(
        BookDbContext context,
        IClipService clipService,
        ILogger<BooksController> logger)
    {
        _context = context;
        _clipService = clipService;
        _logger = logger;
    }

    [HttpGet("/health")]
    public async Task<ActionResult<HealthResponse>> HealthCheck()
    {
        return Ok(new HealthResponse
        {
            Status = "healthy",
            Service = "BookManagementService",
            Version = "1.0.0"
        });
    }

    [HttpPost("match")]
    public async Task<ActionResult<MatchResponse>> Match([FromBody] MatchRequest request)
    {
        try
        {
            _logger.LogInformation("Received match request, image length: {Length}", request.ImageBase64.Length);

            // Extract features from image
            var queryEmbedding = await _clipService.ExtractFeaturesAsync(request.ImageBase64);

            // Search in database using cosine similarity
            // pgvector cosine distance: 1 - cosine_similarity
            var books = await _context.Books
                .Where(b => b.CoverEmbedding != null)
                .ToListAsync();

            var results = books
                .Select(b => new
                {
                    Book = b,
                    Similarity = CosineSimilarity(queryEmbedding, b.CoverEmbedding!)
                })
                .Where(x => x.Similarity > 0.7f)  // Minimum threshold
                .OrderByDescending(x => x.Similarity)
                .Take(5)
                .Select(x => new BookMatchResult
                {
                    Id = x.Book.Id,
                    Title = x.Book.Title,
                    Similarity = x.Similarity,
                    Metadata = string.IsNullOrEmpty(x.Book.Metadata)
                        ? null
                        : System.Text.Json.JsonSerializer.Deserialize<Dictionary<string, object>>(x.Book.Metadata)
                })
                .ToList();

            _logger.LogInformation("Found {Count} matching books", results.Count);

            return Ok(new MatchResponse
            {
                Success = true,
                Books = results
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Match failed");
            return StatusCode(500, new MatchResponse
            {
                Success = false,
                Error = ex.Message
            });
        }
    }

    [HttpPost("register")]
    public async Task<ActionResult<RegisterResponse>> Register([FromBody] RegisterRequest request)
    {
        try
        {
            _logger.LogInformation("Registering new book: {Title}", request.Title);

            // Extract features from image
            var embedding = await _clipService.ExtractFeaturesAsync(request.ImageBase64);

            var book = new Book
            {
                Title = request.Title,
                CoverEmbedding = embedding,
                Metadata = request.Metadata != null
                    ? System.Text.Json.JsonSerializer.Serialize(request.Metadata)
                    : null,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };

            _context.Books.Add(book);
            await _context.SaveChangesAsync();

            _logger.LogInformation("Book registered with ID: {Id}", book.Id);

            return Ok(new RegisterResponse
            {
                Success = true,
                Id = book.Id
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Register failed");
            return StatusCode(500, new RegisterResponse
            {
                Success = false,
                Error = ex.Message
            });
        }
    }

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<BookDetailResponse>> GetById(Guid id)
    {
        var book = await _context.Books.FindAsync(id);

        if (book == null)
        {
            return NotFound(new { error = "Book not found" });
        }

        return Ok(new BookDetailResponse
        {
            Id = book.Id,
            Title = book.Title,
            Metadata = string.IsNullOrEmpty(book.Metadata)
                ? null
                : System.Text.Json.JsonSerializer.Deserialize<Dictionary<string, object>>(book.Metadata),
            CreatedAt = book.CreatedAt
        });
    }

    private static float CosineSimilarity(float[] a, float[] b)
    {
        if (a.Length != b.Length)
            return 0;

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < a.Length; i++)
        {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        return dotProduct / ((float)Math.Sqrt(normA) * (float)Math.Sqrt(normB));
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add services/BookManagementService/Controllers/BooksController.cs
git commit -m "feat: implement BooksController with match and register endpoints

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 6: 下载 CLIP ONNX 模型

**Files:**
- Create: `services/BookManagementService/models/` (directory placeholder)

- [ ] **Step 1: 下载 CLIP ONNX 模型**

需要将 CLIP ViT-B/32 转换为 ONNX 格式。使用 Python 脚本：

```python
# convert_clip_to_onnx.py
from transformers import CLIPProcessor, CLIPModel
import torch

model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32")
processor = CLIPProcessor.from_pretrained("openai/clip-vit-base-patch32")

# Export image encoder
image_features = model.get_image_features
torch.onnx.export(
    image_features,
    (torch.randn(1, 3, 224, 224),),
    "clip-vit-base-patch32.onnx",
    input_names=["input"],
    output_names=["output"],
    dynamic_axes={"input": {0: "batch"}, "output": {0: "batch"}}
)
print("ONNX model exported successfully")
```

运行：
```bash
cd services/BookManagementService
python convert_clip_to_onnx.py  # 需要先安装 transformers, torch, onnx
mkdir -p models
mv clip-vit-base-patch32.onnx models/
```

- [ ] **Step 2: 添加 .gitignore 忽略大模型文件**

```bash
echo "*.onnx" >> services/BookManagementService/.gitignore
echo "models/" >> services/BookManagementService/.gitignore
```

- [ ] **Step 3: 提交**

```bash
git add services/BookManagementService/.gitignore
git commit -m "chore: add .gitignore for ONNX models

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 7: Android BookMatchingClient

**Files:**
- Create: `apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/BookMatchingClient.kt`
- Modify: `apps/PictureBookReading/app/src/main/java/com/picturebook/presentation/ui/MainScreen.kt`

- [ ] **Step 1: 创建 BookMatchingClient.kt**

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
    private val apiBaseUrl: String = "http://192.168.3.18:5018"
) {
    private val gson = Gson()

    suspend fun matchBook(bitmap: Bitmap): MatchResult? = suspendCancellableCoroutine { cont ->
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
                if (matchResponse != null && matchResponse.success && matchResponse.books.isNotEmpty()) {
                    val bestMatch = matchResponse.books.first()
                    cont.resume(MatchResult(
                        bookId = bestMatch.id,
                        title = bestMatch.title,
                        similarity = bestMatch.similarity
                    ))
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

    data class MatchResult(
        val bookId: String,
        val title: String,
        val similarity: Float
    )

    data class MatchResponse(
        val success: Boolean,
        val books: List<BookMatch>,
        val error: String?
    )

    data class BookMatch(
        val id: String,
        val title: String,
        val similarity: Float,
        val metadata: Map<String, Any>?
    )

    companion object {
        private const val TAG = "BookMatchingClient"
    }
}
```

- [ ] **Step 2: 修改 MainScreen.kt 集成 BookMatchingClient**

在 MainScreen.kt 中修改 `captureAndRecognize` 函数，当 OCR 无结果时调用 BookMatchingClient：

```kotlin
// 在 MainScreen.kt 添加:
val bookMatchingClient = remember { BookMatchingClient() }

// 修改 captureAndRecognize 函数的 onError 回调
// 或者在调用 OCR 后检查结果，如果无文字则调用 bookMatchingClient

// 示例逻辑:
private fun captureAndRecognize(
    ...
    onBookRecognized: (String) -> Unit,
    onNoTextBookRecognized: (String) -> Unit,  // 新增
    onImageMatchFailed: () -> Unit,             // 新增
    onError: () -> Unit
) {
    // ... 现有 OCR 逻辑
    val result = httpOcrClient.recognize(bitmap)
    if (result != null && result.fullText.isNotBlank()) {
        onBookRecognized(result.fullText)
    } else {
        // OCR 无结果，尝试图像匹配
        scope.launch(Dispatchers.IO) {
            val matchResult = bookMatchingClient.matchBook(bitmap)
            withContext(Dispatchers.Main) {
                if (matchResult != null && matchResult.similarity > 0.85f) {
                    onNoTextBookRecognized(matchResult.title)
                } else {
                    onImageMatchFailed()
                }
            }
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/BookMatchingClient.kt
git commit -m "feat: add BookMatchingClient for no-text book recognition

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 8: 数据库设置

- [ ] **Step 1: 确保 PostgreSQL 安装了 pgvector 扩展**

```bash
psql -U postgres -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -c "CREATE DATABASE bookdb;"
```

- [ ] **Step 2: 运行数据库迁移**

```bash
cd services/BookManagementService
dotnet ef database update
# 或者手动执行 SQL
```

---

## Task 9: 构建和测试

- [ ] **Step 1: 构建 BookManagementService**

```bash
cd services/BookManagementService
dotnet build
```

- [ ] **Step 2: 运行 BookManagementService**

```bash
cd services/BookManagementService
dotnet run
```

- [ ] **Step 3: 测试健康检查**

```bash
curl http://localhost:5018/health
```

- [ ] **Step 4: 测试注册绘本**

```bash
# 先准备一个 base64 编码的图片
curl -X POST http://localhost:5018/api/books/register \
  -H "Content-Type: application/json" \
  -d '{"imageBase64":"BASE64_IMAGE_DATA","title":"我的第一本书"}'
```

- [ ] **Step 5: 测试匹配绘本**

```bash
curl -X POST http://localhost:5018/api/books/match \
  -H "Content-Type: application/json" \
  -d '{"imageBase64":"BASE64_IMAGE_DATA"}'
```

---

## Task 10: 提交所有更改

- [ ] **Step 1: 查看所有更改**

```bash
git status
git log --oneline -10
```

- [ ] **Step 2: 确保所有文件已提交**

```bash
git diff --stat HEAD~5
```

---

## 依赖清单

### .NET (BookManagementService)
- Microsoft.AspNetCore.OpenApi 10.0.1
- Npgsql.EntityFrameworkCore.PostgreSQL 10.0.2
- pgvector.EntityFrameworkCore.PostgreSQL 10.1.1
- Microsoft.ML.OnnxRuntime 1.19.2
- Microsoft.Extensions.Http 8.0.1

### Python (模型转换)
- transformers
- torch
- onnx

### Android
- 无新增依赖（使用现有 HttpURLConnection）
