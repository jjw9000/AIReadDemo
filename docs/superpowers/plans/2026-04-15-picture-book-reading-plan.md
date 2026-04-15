# Picture Book Reading App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal picture book reading app with two features: (1) recognize book cover via ML Kit OCR and display book name, (2) recognize page text via ML Kit OCR and read aloud with TTS.

**Architecture:** Single-screen Android app using Jetpack Compose. CameraX for camera preview and capture. ML Kit Chinese OCR for text recognition. Android TTS for text-to-speech. All data held in memory (no Room/database).

**Tech Stack:** Kotlin, Jetpack Compose, CameraX 1.3.1, ML Kit Text Recognition Chinese 16.0.0, Android TTS, Min SDK 26, Target SDK 34.

---

## File Structure

```
app/
├── build.gradle.kts                    # App dependencies (CameraX, ML Kit, Compose)
├── src/main/
│   ├── AndroidManifest.xml             # Camera, audio permissions
│   ├── java/com/picturebook/
│   │   ├── MainActivity.kt             # Single activity, sets up Compose
│   │   ├── hardware/
│   │   │   └── AudioService.kt         # TTS wrapper
│   │   ├── infrastructure/
│   │   │   └── ai/
│   │   │       └── MlKitOcrClient.kt   # ML Kit OCR client (from aireading, adapted)
│   │   └── presentation/
│   │       └── ui/
│   │           ├── MainScreen.kt       # Single screen: camera + controls
│   │           └── theme/
│   │               ├── Color.kt
│   │               ├── Theme.kt
│   │               └── Type.kt
├── settings.gradle.kts                # Project settings
└── build.gradle.kts                  # Root build file
```

---

## Task 1: Project Setup

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "PictureBookReading"
include(":app")
```

- [ ] **Step 2: Create root build.gradle.kts**

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

- [ ] **Step 3: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Create gradle/wrapper/gradle-wrapper.properties**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 5: Create app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [ ] **Step 6: Create app/src/main/AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <uses-feature android:name="android.hardware.camera" android:required="true" />
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.PictureBook">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="landscape"
            android:theme="@style/Theme.PictureBook">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 7: Create app/src/main/res/values/strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">绘本阅读</string>
</resources>
```

- [ ] **Step 8: Create app/src/main/res/values/themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PictureBook" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

---

## Task 2: Theme Setup

**Files:**
- Create: `app/src/main/java/com/picturebook/presentation/ui/theme/Color.kt`
- Create: `app/src/main/java/com/picturebook/presentation/ui/theme/Type.kt`
- Create: `app/src/main/java/com/picturebook/presentation/ui/theme/Theme.kt`

- [ ] **Step 1: Create Color.kt**

```kotlin
package com.picturebook.presentation.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
val Primary = Color(0xFF6200EE)
val ReadingColor = Color(0xFF4CAF50)
val Surface = Color(0xFFFFFBFE)
val OnSurface = Color(0xFF1C1B1F)
val SurfaceVariant = Color(0xFFE7E0EC)
```

- [ ] **Step 2: Create Type.kt**

```kotlin
package com.picturebook.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
)
```

- [ ] **Step 3: Create Theme.kt**

```kotlin
package com.picturebook.presentation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant
)

@Composable
fun PictureBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

---

## Task 3: AudioService (TTS)

**Files:**
- Create: `app/src/main/java/com/picturebook/hardware/AudioService.kt`

- [ ] **Step 1: Create AudioService.kt**

```kotlin
package com.picturebook.hardware

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class AudioService(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                                 result != TextToSpeech.LANG_NOT_SUPPORTED
                Log.i(TAG, "TTS initialized: $isInitialized")
            } else {
                Log.e(TAG, "TTS init failed")
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            onDone?.invoke()
            return
        }
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onDone?.invoke()
            }
            override fun onError(utteranceId: String?) {
                onDone?.invoke()
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "AudioService"
    }
}
```

---

## Task 4: MLKitOcrClient

**Files:**
- Create: `app/src/main/java/com/picturebook/infrastructure/ai/MlKitOcrClient.kt`

- [ ] **Step 1: Create MlKitOcrClient.kt**

```kotlin
package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MlKitOcrClient {

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    suspend fun recognize(bitmap: Bitmap): OcrResult? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val textBlocks = visionText.textBlocks.map { block ->
                    TextBlock(
                        text = block.text,
                        confidence = 0.9f,
                        boundingBox = block.boundingBox
                    )
                }
                val fullText = textBlocks.joinToString(" ") { it.text }
                Log.i(TAG, "ML Kit OCR success: $fullText")
                cont.resume(OcrResult(textBlocks, fullText))
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit OCR failed: ${e.message}")
                cont.resume(null)
            }
    }

    fun close() {
        recognizer.close()
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

---

## Task 5: MainActivity

**Files:**
- Create: `app/src/main/java/com/picturebook/MainActivity.kt`

- [ ] **Step 1: Create MainActivity.kt**

```kotlin
package com.picturebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.picturebook.presentation.ui.MainScreen
import com.picturebook.presentation.ui.theme.PictureBookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PictureBookTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}
```

---

## Task 6: MainScreen

**Files:**
- Create: `app/src/main/java/com/picturebook/presentation/ui/MainScreen.kt`

- [ ] **Step 1: Create MainScreen.kt**

```kotlin
package com.picturebook.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.picturebook.hardware.AudioService
import com.picturebook.infrastructure.ai.MlKitOcrClient
import com.picturebook.presentation.ui.theme.ReadingColor
import kotlinx.coroutines.*
import java.util.concurrent.Executors

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var statusText by remember { mutableStateOf("点击\"开始识别\"对准绘本") }
    var bookName by remember { mutableStateOf<String?>(null) }
    var isReading by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val audioService = remember { AudioService(context) }
    val mlKitOcrClient = remember { MlKitOcrClient() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            statusText = "点击\"开始识别\"对准绘本"
        } else {
            statusText = "需要相机权限"
        }
    }

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.CAMERA
        hasPermission = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            permissionLauncher.launch(permission)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioService.release()
            mlKitOcrClient.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "绘本阅读",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ReadingColor
                )
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            if (bookName != null) {
                Text(
                    text = "《${bookName}》",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main content
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Camera preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also {
                            it.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            previewView = it
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!hasPermission) {
                        Text(
                            text = "请授予相机权限",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    } else if (bookName == null) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "准备就绪",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Control panel
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "控制面板",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                // Speak button
                OutlinedButton(
                    onClick = {
                        if (!isReading) {
                            isReading = true
                            statusText = "正在识别页面文字..."
                            captureAndRead(
                                context, lifecycleOwner, previewView, imageCapture,
                                mlKitOcrClient, audioService, scope,
                                onTextRecognized = { text ->
                                    isReading = false
                                    statusText = "朗读中..."
                                    audioService.speak(text) {
                                        isReading = false
                                        statusText = "朗读完成"
                                    }
                                },
                                onError = {
                                    isReading = false
                                    statusText = "未识别到文字"
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isReading
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("朗读本页")
                }

                // Recognize button
                Button(
                    onClick = {
                        if (!isReading) {
                            isReading = true
                            statusText = "正在识别绘本..."
                            captureAndRecognize(
                                context, lifecycleOwner, previewView,
                                mlKitOcrClient, scope,
                                onBookRecognized = { name ->
                                    isReading = false
                                    bookName = name
                                    statusText = "识别成功"
                                },
                                onError = {
                                    isReading = false
                                    statusText = "识别失败，请重试"
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ReadingColor),
                    enabled = !isReading && hasPermission
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始识别")
                }
            }
        }
    }
}

private fun bindCamera(
    context: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView?,
    onCameraBound: (ImageCapture) -> Unit
) {
    if (previewView == null) return

    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )

            onCameraBound(imageCapture)
        } catch (e: Exception) {
            Log.e("MainScreen", "Camera bind failed: ${e.message}")
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun captureAndRead(
    context: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView?,
    imageCaptureRef: ImageCapture?,
    mlKitOcrClient: MlKitOcrClient,
    audioService: AudioService,
    scope: CoroutineScope,
    onTextRecognized: (String) -> Unit,
    onError: () -> Unit
) {
    var imageCapture by remember { mutableStateOf(imageCaptureRef) }

    if (imageCapture == null) {
        bindCamera(context, lifecycleOwner, previewView) { cap ->
            imageCapture = cap
            doCaptureAndRead(context, cap, mlKitOcrClient, audioService, scope, onTextRecognized, onError)
        }
    } else {
        doCaptureAndRead(context, imageCapture, mlKitOcrClient, audioService, scope, onTextRecognized, onError)
    }
}

private fun doCaptureAndRead(
    context: android.content.Context,
    imageCapture: ImageCapture,
    mlKitOcrClient: MlKitOcrClient,
    audioService: AudioService,
    scope: CoroutineScope,
    onTextRecognized: (String) -> Unit,
    onError: () -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.toBitmap()
                image.close()
                scope.launch(Dispatchers.IO) {
                    val result = mlKitOcrClient.recognize(bitmap)
                    withContext(Dispatchers.Main) {
                        if (result != null && result.fullText.isNotBlank()) {
                            onTextRecognized(result.fullText)
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

private fun captureAndRecognize(
    context: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView?,
    mlKitOcrClient: MlKitOcrClient,
    scope: CoroutineScope,
    onBookRecognized: (String) -> Unit,
    onError: () -> Unit
) {
    var imageCapture: ImageCapture? = null

    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                previewView?.let { pv -> it.setSurfaceProvider(pv.surfaceProvider) }
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture!!
            )

            // Give camera time to focus, then capture
            scope.launch(Dispatchers.Main) {
                delay(1500)
                imageCapture?.let { cap ->
                    cap.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = image.toBitmap()
                                image.close()
                                scope.launch(Dispatchers.IO) {
                                    val result = mlKitOcrClient.recognize(bitmap)
                                    withContext(Dispatchers.Main) {
                                        if (result != null && result.fullText.isNotBlank()) {
                                            onBookRecognized(result.fullText)
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
            }
        } catch (e: Exception) {
            Log.e("MainScreen", "Camera bind failed: ${e.message}")
            scope.launch(Dispatchers.Main) { onError() }
        }
    }, ContextCompat.getMainExecutor(context))
}
```

---

## Task 7: Verify Build

**Files:**
- Verify all files compile

- [ ] **Step 1: Run Gradle sync**

Run: `./gradlew assembleDebug --no-daemon` (or `gradlew.bat` on Windows)
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify APK exists**

Run: `ls app/build/outputs/apk/debug/`
Expected: `app-debug.apk`

---

## Self-Review Checklist

- [ ] Spec coverage: Both features (book recognition + page reading) mapped to tasks
- [ ] No placeholders: All code blocks filled, no "TODO" or "TBD"
- [ ] Type consistency: `AudioService`, `MlKitOcrClient`, `MainScreen` all use consistent naming
- [ ] Task boundaries: Each task produces a runnable state
