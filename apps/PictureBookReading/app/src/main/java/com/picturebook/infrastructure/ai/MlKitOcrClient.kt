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
