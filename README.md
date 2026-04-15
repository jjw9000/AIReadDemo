# 绘本阅读

一个精简的绘本阅读 Android App，配合 .NET OCR 后端服务。

## 项目结构

```
Demo/
├── apps/
│   └── PictureBookReading/     # Android App (Kotlin + Jetpack Compose)
│       └── app/src/main/java/com/picturebook/
│           ├── MainActivity.kt
│           ├── hardware/AudioService.kt      # TTS 朗读
│           ├── infrastructure/ai/HttpOcrClient.kt  # 调用后端 OCR API
│           └── presentation/ui/MainScreen.kt
│
└── services/
    └── OcrService/             # .NET 10 OCR 后端服务
        └── OcrService.csproj   # 调用 PaddleOCR-VL-1.5 API
```

## 功能

- **绘本识别** - 对准绘本，点击"开始识别"，调用后端 OCR API 识别书名
- **内容页朗读** - 翻页后点击"朗读本页"，识别文字并用 TTS 朗读

## 技术栈

### Android App
- Kotlin + Jetpack Compose
- CameraX（摄像头）
- Android TTS（文字转语音）
- HTTP 调用后端 OCR API
- Min SDK 26, Target SDK 34

### OCR 后端服务
- .NET 10 Web API
- 调用 PaddleOCR-VL-1.5 API

## 构建

### Android App
```bash
cd apps/PictureBookReading

# 设置环境变量
export JAVA_HOME="d:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="d:/Android"

# 构建 Debug APK
./gradlew assembleDebug
```

APK 输出位置：`apps/PictureBookReading/app/build/outputs/apk/debug/app-debug.apk`

### OCR 服务
```bash
cd services/OcrService
dotnet run
```

服务运行在 `http://localhost:5007`

## 配置

### Android App OCR API 地址
默认调用 `http://10.0.2.2:5007`（Android 模拟器访问宿主机的地址）

如需修改，编辑 `HttpOcrClient.kt` 中的 `apiBaseUrl`。

### OCR 服务配置
配置文件：`services/OcrService/appsettings.json`

```json
{
  "OcrService": {
    "ApiUrl": "http://localhost:5007/",
    "TimeoutSeconds": 120
  }
}
```

## 使用

1. 启动 OCR 后端服务
2. 安装并打开 Android App
3. 首次打开会请求相机权限
4. 点击"开始识别"，对准绘本封面拍照，自动识别书名
5. 识别成功后书名显示在控制面板
6. 翻到任意一页，点击"朗读本页"，识别文字并朗读