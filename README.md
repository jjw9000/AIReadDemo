# 绘本阅读

一个精简的绘本阅读 Android App。

## 功能

- **绘本识别** - 对准绘本，点击"开始识别"，ML Kit OCR 识别书名
- **内容页朗读** - 翻页后点击"朗读本页"，识别文字并用 TTS 朗读

## 技术栈

- Kotlin + Jetpack Compose
- CameraX（摄像头）
- ML Kit Text Recognition（中文 OCR）
- Android TTS（文字转语音）
- Min SDK 26, Target SDK 34

## 项目结构

```
app/src/main/java/com/picturebook/
├── MainActivity.kt              # 入口
├── hardware/
│   └── AudioService.kt          # TTS 朗读服务
├── infrastructure/
│   └── ai/
│       └── MlKitOcrClient.kt    # ML Kit OCR 客户端
└── presentation/
    └── ui/
        ├── MainScreen.kt        # 主界面
        └── theme/               # 主题
```

## 构建

```bash
# 设置环境变量
export JAVA_HOME="d:/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="d:/Android"

# 构建 Debug APK
./gradlew assembleDebug
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`

## 使用

1. 首次打开会请求相机权限
2. 点击"开始识别"，对准绘本封面拍照，自动识别书名
3. 识别成功后书名显示在控制面板
4. 翻到任意一页，点击"朗读本页"，识别文字并朗读