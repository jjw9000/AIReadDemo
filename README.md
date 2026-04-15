# 绘本阅读 - 智能识别与本地优先架构

## 业务与技术背景

### 业务场景
绘本阅读 App 针对儿童教育场景设计，支持：
- **无文字绘本**：纯插图绘本无法用 OCR 识别文字，需要图像特征匹配
- **有文字绘本**：通过 OCR 提取文字后本地匹配已有绘本
- **离线优先**：减少对云端 API 的依赖，提升响应速度

### 核心痛点
1. **无文字绘本无法朗读** - 传统 OCR 方案依赖文字存在
2. **网络延迟影响体验** - 每次翻页都调用云端 API，响应慢
3. **API 成本** - CLIP 模型调用成本高，本地匹配可降低费用

---

## 架构决策

### 整体架构：本地优先 + 云端兜底

```
Android (本地)
    │
    ├─ ML Kit OCR (即时，文字绘本)
    │       ↓
    ├─ 文本 模糊匹配 (本地，已注册绘本)
    │       ↓
    └─ ORB 特征匹配 (本地，图片绘本)

云端兜底 (按需)
    │
    ├─ CLIP 封面匹配 (BookManagementService)
    │       ↓
    └─ PaddleOCR 文字识别 (OcrService)
```

### 关键决策与权衡

| 决策 | 选择 | 权衡 |
|------|------|------|
| **文字匹配** | 文本 模糊匹配 | 优点：本地查询快，缺点：不如向量相似度准确 |
| **图片匹配** | ORB 特征点匹配 | 优点：纯本地、存储小，缺点：依赖特征点明显程度 |
| **兜底方案** | CLIP API 封面匹配 | 优点：准确，缺点：需要网络调用 |
| **数据库** | SQLite/Room | 优点：Android 原生，缺点：不支持向量索引 |

### 为什么不只用 CLIP？
- CLIP 模型约 1.5GB，无法在移动端运行
- 每次 API 调用有延迟和成本
- 本地匹配可覆盖大部分常见绘本

## AI 协作说明

### AI 辅助部分
本项目在以下部分使用了 AI 辅助生成：

1. **OrbPageMatcher 实现** - AI 生成 ORB 特征匹配代码框架
2. **BookRepository 流程设计** - AI 建议多级降级匹配策略
3. **DTOs 和 API 响应结构** - AI 辅助设计 RESTful 接口
4. **CLIP 封面匹配** - AI 生成 CLIP 模型调用代码
5. **PaddleOCR 文字识别** - AI 生成 PaddleOCR 模型调用代码
6. **文本 模糊匹配** - AI 生成文本模糊匹配代码
7. **ORB 特征匹配** - AI 生成 ORB 特征匹配代码
8. **Android App 代码** - AI 生成 Android App 代码，包括相机、OCR、匹配逻辑

---

## 服务架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Android App                              │
│  ┌─────────┐  ┌─────────────┐  ┌──────────┐  ┌─────────┐  │
│  │CameraX  │→│ ML Kit OCR  │→│ Room FTS  │→│  ORB    │  │
│  └─────────┘  └─────────────┘  └──────────┘  └─────────┘  │
│       │                                    │                │
│       └────────── BookRepository ──────────┘                │
│                      (统一入口)                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP (内网)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│               BookManagementService (:5018)                 │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────────┐  │
│  │ CLIP 封面 │→│ OCR 兜底 │→│ PostgreSQL (pages 表)   │  │
│  │  匹配    │  │ 文字识别 │  │ full_text 列           │  │
│  └──────────┘  └──────────┘  └────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 OcrService (:5017)                          │
│  ┌──────────────────────┐  ┌────────────────────────────┐ │
│  │ PaddleOCR-VL-1.5 API │  │ 本地 OCR 兜底 (Tesseract) │ │
│  └──────────────────────┘  └────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 验证指南

### 环境要求
- Java 17+
- Android SDK 34
- .NET 10 SDK
- PostgreSQL 15+ (支持 vector 类型)

### 1. 启动后端服务

```bash
# 启动 BookManagementService
cd services/BookManagementService
dotnet run --urls "http://0.0.0.0:5018"

# 启动 OcrService (可选，已集成到 BookManagementService)
cd services/OcrService
dotnet run --urls "http://0.0.0.0:5017"
```

### 2. 初始化数据库

```bash
# 连接 PostgreSQL
psql -h localhost -U postgres -d picturebook

# 创建扩展和表
CREATE EXTENSION IF NOT EXISTS vector;

-- 执行 services/BookManagementService/Data/init.sql
\i services/BookManagementService/Data/init.sql
```

### 3. 构建 Android App

```bash
cd apps/PictureBookReading

# 设置环境变量
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="d:/Android"

# 清理并构建
./gradlew clean assembleDebug
```

APK 输出：`apps/PictureBookReading/app/build/outputs/apk/debug/app-debug.apk`

### 4. 功能验证

#### 场景一：有文字绘本
1. 打开 App，授权相机权限
2. 点击"开始识别"，对准绘本封面
3. 等待 CLIP 封面匹配
4. 匹配成功后，点击"朗读本页"
5. 应该显示匹配的绘本名称

**验证点：**
- 服务端日志应显示 CLIP 封面匹配
- 匹配到的 book 应包含 pages

#### 场景二：本地已有绘本，朗读内容页
1. 识别成功后，翻到其他页
2. 点击"朗读本页"
3. App 使用 ORB 或 Jaccard 匹配本地存储的 page

**验证点：**
- 日志显示 "ORB matched page X" 或 "Jaccard matched page X"
- 朗读正确的页面文字

#### 场景三：无文字绘本
1. 对准绘本页面，点击"朗读本页"
2. ML Kit OCR 返回 null
3. 降级到 OcrService API
4. API 返回 OCR 文字后，用文字搜索数据库 pages
5. 返回匹配的 book

**验证点：**
- 日志显示 "ML Kit OCR returned null"
- 后续日志显示 "falling back to OcrService API"
- 或 "CLIP match failed, trying OcrService..."

#### 场景四：ORB 特征学习
1. 首次朗读某页时，用文字匹配成功
2. 成功后自动保存 ORB descriptors 到本地数据库
3. 下次朗读同一页时，直接用 ORB 匹配

**验证点：**
- 日志显示 "Saved ORB descriptors for page X"
- 再次朗读时显示 "ORB matched page X"

### 5. 调试日志

```bash
# 过滤 App 日志
adb logcat -s BookRepository LocalBookSearchService OrbPageMatcher MainScreen

# 过滤服务端日志
curl http://localhost:5018/health
```

---

## 项目结构

```
Demo/
├── apps/
│   └── PictureBookReading/           # Android App
│       └── app/src/main/java/com/picturebook/
│           ├── data/local/           # Room 数据库
│           │   ├── BookDao.kt
│           │   ├── BookDatabase.kt
│           │   └── entity/
│           │       ├── BookEntity.kt
│           │       └── BookPageEntity.kt
│           ├── domain/model/         # 领域模型
│           ├── hardware/            # TTS 服务
│           │   └── AudioService.kt
│           └── infrastructure/ai/   # AI 服务
│               ├── BookRepository.kt    # 统一入口
│               ├── BookMatchingClient.kt # CLIP API
│               ├── HttpOcrClient.kt      # OcrService API
│               ├── LocalBookSearchService.kt # FTS + Jaccard
│               ├── MlKitOcrClient.kt    # ML Kit OCR
│               └── OrbPageMatcher.kt     # ORB 匹配
│
└── services/
    ├── BookManagementService/        # .NET 10 封面匹配服务
    │   ├── Controllers/BooksController.cs
    │   ├── DTOs/BookDtos.cs
    │   ├── Repositories/
    │   │   ├── BookRepository.cs
    │   │   └── IBookRepository.cs
    │   └── Services/
    │       └── ClipService.cs
    │
    └── OcrService/                  # .NET 10 OCR 服务
        ├── Controllers/OcrController.cs
        ├── Services/
        │   ├── LocalOcrService.cs
        │   └── PaddleOcrService.cs
        └── Data/init.sql
```

---

## 配置

### Android App
文件：`apps/PictureBookReading/app/src/main/java/com/picturebook/infrastructure/ai/`

| 文件 | 默认地址 | 说明 |
|------|---------|------|
| BookMatchingClient | 192.168.3.18:5018 | CLIP 封面匹配 |
| HttpOcrClient | 192.168.3.18:5017 | OCR 文字识别 |

### BookManagementService
文件：`services/BookManagementService/appsettings.json`

```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Database=picturebook;Username=postgres;Password=postgres"
  }
}
```

### OcrService
文件：`services/OcrService/appsettings.json`

```json
{
  "OcrService": {
    "PaddleLayoutUrl": "https://...",
    "PaddleLayoutToken": "..."
  },
  "LocalOcrService": {
    "Url": "http://localhost:5007"
  }
}
```
