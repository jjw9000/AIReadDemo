# 无文字绘本识别设计方案

## 1. 概述

扩展现有绘本阅读 App，支持无文字绘本的识别。当 OCR 无法识别到文字时，通过图像特征向量匹配来识别绘本。

## 2. 系统架构

```
Android App ──(封面图片)──> BookManagementService (.NET 10)
                                    │
                                    ├── CLIP (CPU) ──> 提取特征向量 (512维)
                                    │
                                    └── PostgreSQL/pgvector ──> 余弦相似度搜索
                                              │
                                              └── 返回最相似绘本列表
```

## 3. 服务设计

### 3.1 BookManagementService (.NET 10)

**技术栈**：
- .NET 10 Web API
- PostgreSQL + pgvector（向量存储和搜索）
- CLIP 模型（图像特征提取，CPU 可运行）

**端口**：5018（避免与 OCR Service 5017 冲突）

### 3.2 核心接口

#### POST /api/books/match
识别绘本封面
- **输入**：
  ```json
  {
    "imageBase64": "base64 encoded image"
  }
  ```
- **输出**：
  ```json
  {
    "success": true,
    "books": [
      {
        "id": "uuid",
        "title": "书名",
        "similarity": 0.95,
        "metadata": {}
      }
    ]
  }
  ```

#### POST /api/books/register
注册新绘本
- **输入**：
  ```json
  {
    "imageBase64": "base64 encoded image",
    "title": "书名",
    "metadata": {}
  }
  ```
- **输出**：
  ```json
  {
    "success": true,
    "id": "uuid"
  }
  ```

#### GET /api/books/{id}
获取绘本详情
- **输出**：
  ```json
  {
    "id": "uuid",
    "title": "书名",
    "metadata": {},
    "createdAt": "2024-01-01T00:00:00Z"
  }
  ```

#### GET /health
健康检查

## 4. 数据模型

### PostgreSQL Schema

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE books (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    cover_embedding vector(512),  -- CLIP 图像特征向量
    metadata jsonb,                -- 额外信息（作者、出版社、年龄推荐等）
    created_at timestamp DEFAULT NOW(),
    updated_at timestamp DEFAULT NOW()
);

-- IVFFlat 索引加速余弦相似度搜索
CREATE INDEX ON books USING ivfflat (cover_embedding vector_cosine_ops) WITH (lists = 100);
```

## 5. CLIP 特征提取

### 模型选择
- **模型**：clip-vit-base-patch32 或 openai/clip-vit-base-patch32
- **维度**：512 维向量
- **运行方式**：CPU 推理，无需 GPU

### 预处理
1. 接收 Base64 图像
2. 解码为 PIL Image
3. 调整大小为 224x224
4. 标准化处理
5. CLIP 图像编码器提取特征

## 6. Android App 集成

### 6.1 识别流程

```
开始识别
    │
    ├── OCR 识别文字
    │
    ├── 有文字 (> 5 字符) ──> 显示书名
    │
    └── 无文字 ──> 调用 /api/books/match
                    │
                    └── 有匹配 (similarity > 0.85) ──> 显示书名
                    └── 无匹配 ──> 提示"未识别到绘本，可尝试调整角度"
```

### 6.2 Android 端修改

**新增模块**：
```
infrastructure/
└── ai/
    └── BookMatchingClient.kt  # 调用 BookManagementService
```

**修改 MainScreen.kt**：
- OCR 无结果时，调用 BookMatchingClient 匹配绘本

### 6.3 服务地址
- 开发环境：`http://192.168.3.18:5018`
- 生产环境：配置化

## 7. 项目结构

```
services/
├── OcrService/              # 现有 OCR 服务 (.NET)
├── OcrServicePython/        # 现有 Python OCR 服务
└── BookManagementService/   # 新建 - 绘本管理服务 (.NET 10)

apps/PictureBookReading/
├── infrastructure/ai/
│   ├── HttpOcrClient.kt     # 现有 OCR 客户端
│   └── BookMatchingClient.kt # 新增 - 绘本匹配客户端
```

## 8. 相似度阈值

- **> 0.90**：高置信度，直接使用
- **0.85 - 0.90**：中等置信度，显示提示
- **< 0.85**：低置信度，提示未识别

## 9. 依赖项

### .NET (BookManagementService)
- Npgsql.EntityFrameworkCore.PostgreSQL
- pgvector.EntityFrameworkCore.PostgreSQL
- torchsharp（CLIP 推理）或调用 Python CLIP 服务

### Android
- 无新增依赖（使用现有 HttpURLConnection）

## 10. 部署

- PostgreSQL + pgvector 插件
- BookManagementService 作为独立服务部署
- Android App 配置服务地址
