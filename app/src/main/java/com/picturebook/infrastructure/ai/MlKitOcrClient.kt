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