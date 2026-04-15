# 绘本阅读 App 设计方案

## 1. 概述

一个精简的绘本阅读 App，仅包含两个核心功能：
- 绘本封面识别：通过 ML Kit OCR 识别绘本，显示书名
- 内容页朗读：通过 ML Kit OCR 识别页面文字，使用 TTS 朗读

## 2. 核心功能

### 2.1 绘本识别

- 使用后置摄像头对准绘本
- ML Kit OCR（支持中文）识别封面文字
- 识别成功后显示书名在界面上
- 数据存储在内存中（无 Room）

### 2.2 内容页朗读

- 用户翻页后，点击朗读按钮
- ML Kit OCR 识别当前页面文字
- Android TTS 引擎朗读识别出的文字

## 3. 技术架构

```
app/
├── MainActivity.kt           # 入口
├── data/
│   └── BookRepository.kt     # 绘本数据仓库（内存）
├── domain/
│   └── model/
│       └── Book.kt           # 绘本实体
├── infrastructure/
│   └── ai/
│       └── MlKitOcrClient.kt # ML Kit OCR 客户端
├── hardware/
│   └── AudioService.kt      # TTS 朗读服务
└── presentation/
    └── ui/
        ├── MainScreen.kt     # 主界面
        └── theme/            # 主题配置
```

## 4. 技术栈

- Kotlin + Jetpack Compose
- CameraX（摄像头）
- ML Kit Text Recognition + Chinese OCR (16.0.0)
- Android TTS（文字转语音）
- Min SDK 26, Target SDK 34

## 5. 界面设计

```
┌─────────────────────────────────────────────────────────┐
│  [摄像头预览区域]              │    [控制面板]            │
│                              │                         │
│   状态: xxx                  │   书名: xxx              │
│                              │                         │
│                              │   [朗读本页]             │
│                              │                         │
│                              │   [开始识别]             │
└─────────────────────────────────────────────────────────┘
```

## 6. 数据流

```
摄像头拍照 → ML Kit OCR → 显示书名
     │
     └─→ 用户翻页 → 拍照 → ML Kit OCR → 提取文字 → TTS 朗读
```

## 7. 简化点

- 无人脸识别
- 无翻页检测（手动按钮翻页）
- 无网络API（纯本地，离线可用）
- 无数据库（内存存储）
- 控制面板只显示书名