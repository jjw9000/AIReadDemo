package com.picturebook.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
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
import com.picturebook.infrastructure.ai.BookMatchingClient
import com.picturebook.infrastructure.ai.BookRepository
import com.picturebook.infrastructure.ai.BookSearchResult
import com.picturebook.infrastructure.ai.ReadResult
import com.picturebook.domain.model.BookMatchResult
import com.picturebook.presentation.ui.theme.ReadingColor
import kotlinx.coroutines.*

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
    var currentBook by remember { mutableStateOf<BookMatchResult?>(null) }

    val audioService = remember { AudioService(context) }
    val bookRepository = remember { BookRepository(context) }

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

                // Book name in control panel
                if (bookName != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "书名",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = bookName ?: "",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Speak button
                OutlinedButton(
                    onClick = {
                        if (!isReading) {
                            isReading = true
                            val book = currentBook
                            if (book != null) {
                                // Book already recognized - use readWithKnownBook
                                statusText = "正在朗读..."
                                captureAndReadWithKnownBook(
                                    context, lifecycleOwner, previewView,
                                    bookRepository, audioService, scope, book.bookId,
                                    onReadReady = { text ->
                                        isReading = false
                                        statusText = "朗读中..."
                                        audioService.speak(text) {
                                            isReading = false
                                            statusText = "朗读完成"
                                        }
                                    },
                                    onError = {
                                        isReading = false
                                        statusText = "朗读失败"
                                    }
                                )
                            } else {
                                // No book recognized - do full search flow
                                statusText = "正在识别页面文字..."
                                captureAndRead(
                                    context, lifecycleOwner, previewView,
                                    bookRepository, audioService, scope,
                                    onTextRecognized = { text ->
                                        isReading = false
                                        statusText = "朗读中..."
                                        audioService.speak(text) {
                                            isReading = false
                                            statusText = "朗读完成"
                                        }
                                    },
                                    onBookFound = { name ->
                                        isReading = false
                                        bookName = name
                                        statusText = "识别成功"
                                    },
                                    onError = {
                                        isReading = false
                                        statusText = "未识别到文字"
                                    }
                                )
                            }
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
                                bookRepository, scope,
                                onBookRecognized = { bookMatch ->
                                    isReading = false
                                    currentBook = bookMatch
                                    bookName = bookMatch.title
                                    statusText = "识别成功"
                                },
                                onImageMatchSuccess = { bookMatch ->
                                    isReading = false
                                    currentBook = bookMatch
                                    bookName = bookMatch.title
                                    statusText = "图像识别成功"
                                },
                                onImageMatchFailed = {
                                    isReading = false
                                    currentBook = null
                                    bookName = null
                                    statusText = "未识别到绘本，请尝试调整角度"
                                },
                                onError = {
                                    isReading = false
                                    currentBook = null
                                    bookName = null
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
    bookRepository: BookRepository,
    audioService: AudioService,
    scope: CoroutineScope,
    onTextRecognized: (String) -> Unit,
    onBookFound: (String) -> Unit,
    onError: () -> Unit
) {
    Log.d("MainScreen", "captureAndRead: previewView=${previewView != null}")
    var imageCapture: ImageCapture? = null

    if (imageCapture == null) {
        bindCamera(context, lifecycleOwner, previewView) { cap ->
            Log.d("MainScreen", "Camera bound, calling doCaptureAndRead")
            imageCapture = cap
            doCaptureAndRead(context, cap, bookRepository, audioService, scope, onTextRecognized, onBookFound, onError)
        }
    } else {
        Log.d("MainScreen", "Using existing imageCapture")
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
                Log.d("MainScreen", "onCaptureSuccess: image=${image.width}x${image.height}")
                val bitmap = image.toBitmap()
                Log.d("MainScreen", "onCaptureSuccess: bitmap=${bitmap?.width}x${bitmap?.height}")
                image.close()
                scope.launch(Dispatchers.IO) {
                    Log.d("MainScreen", "Calling searchBook...")
                    try {
                        val result = bookRepository.searchBook(bitmap)
                        Log.d("MainScreen", "searchBook result: $result")
                        withContext(Dispatchers.Main) {
                            when (result) {
                                is BookSearchResult.Found -> {
                                    if (result.text.isNotBlank()) {
                                        onTextRecognized(result.text)
                                    } else {
                                        onBookFound(result.match.title)
                                    }
                                }
                                is BookSearchResult.NoOcrText, is BookSearchResult.NotFound -> {
                                    Log.d("MainScreen", "No match found")
                                    onError()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainScreen", "searchBook failed: ${e.message}", e)
                        withContext(Dispatchers.Main) { onError() }
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
    bookRepository: BookRepository,
    scope: CoroutineScope,
    onBookRecognized: (BookMatchResult) -> Unit,
    onImageMatchSuccess: (BookMatchResult) -> Unit,
    onImageMatchFailed: () -> Unit,
    onError: () -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build()
            previewView?.let { pv ->
                preview.setSurfaceProvider(pv.surfaceProvider)
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

            // Give camera time to focus, then capture
            scope.launch(Dispatchers.Main) {
                delay(1500)
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap()
                            image.close()
                            scope.launch(Dispatchers.IO) {
                                val result = bookRepository.searchBook(bitmap)
                                withContext(Dispatchers.Main) {
                                    when (result) {
                                        is BookSearchResult.Found -> onImageMatchSuccess(result.match)
                                        else -> onImageMatchFailed()
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
        } catch (e: Exception) {
            Log.e("MainScreen", "Camera bind failed: ${e.message}")
            scope.launch(Dispatchers.Main) { onError() }
        }
    }, ContextCompat.getMainExecutor(context))
}

// Capture and read using a known book (no cover match needed)
private fun captureAndReadWithKnownBook(
    context: android.content.Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView?,
    bookRepository: BookRepository,
    audioService: AudioService,
    scope: CoroutineScope,
    bookId: String,
    onReadReady: (String) -> Unit,
    onError: () -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build()
            previewView?.let { pv ->
                preview.setSurfaceProvider(pv.surfaceProvider)
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

            // Give camera time to focus, then capture
            scope.launch(Dispatchers.Main) {
                delay(1000)
                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap()
                            image.close()
                            scope.launch(Dispatchers.IO) {
                                Log.d("MainScreen", "Calling readWithKnownBook for bookId=$bookId")
                                val result = bookRepository.readWithKnownBook(bitmap, bookId)
                                withContext(Dispatchers.Main) {
                                    if (result != null && result.text.isNotBlank()) {
                                        Log.d("MainScreen", "readWithKnownBook result: page=${result.matchedPageNumber}, usedStoredText=${result.usedStoredText}")
                                        onReadReady(result.text)
                                    } else {
                                        Log.d("MainScreen", "readWithKnownBook returned null or empty")
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
        } catch (e: Exception) {
            Log.e("MainScreen", "Camera bind failed: ${e.message}")
            scope.launch(Dispatchers.Main) { onError() }
        }
    }, ContextCompat.getMainExecutor(context))
}
