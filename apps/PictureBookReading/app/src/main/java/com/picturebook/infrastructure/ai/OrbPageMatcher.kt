package com.picturebook.infrastructure.ai

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs
import java.util.Base64

/**
 * ORB-based page matching service.
 * Extracts ORB features from bitmaps and matches against stored descriptors.
 */
class OrbPageMatcher {
    companion object {
        private const val TAG = "OrbPageMatcher"
        private const val GOOD_MATCH_THRESHOLD = 15  // Minimum good matches to consider a match
        private const val MAX_KPTS = 500  // Maximum keypoints to extract for performance

        init {
            // Initialize OpenCV native libraries
            if (!OpenCVLoader.initDebug()) {
                Log.e(TAG, "OpenCV initialization failed!")
            } else {
                Log.d(TAG, "OpenCV initialized successfully")
            }
        }
    }

    private val orb = ORB.create(MAX_KPTS)
    private val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

    /**
     * Extract ORB descriptors from a bitmap.
     * Returns Base64 encoded descriptors string, or null if extraction fails.
     */
    fun extractDescriptors(bitmap: Bitmap): String? {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()

            orb.detectAndCompute(mat, Mat(), keypoints, descriptors)

            if (descriptors.empty()) {
                Log.d(TAG, "No descriptors extracted")
                mat.release()
                return null
            }

            // Convert Mat to PNG bytes, then to Base64
            val byteBuffer = MatOfByte()
            if (!Imgcodecs.imencode(".png", descriptors, byteBuffer)) {
                Log.e(TAG, "Failed to encode descriptors to PNG")
                mat.release()
                byteBuffer.release()
                return null
            }
            val bytes = byteBuffer.toArray()
            val base64 = Base64.getEncoder().encodeToString(bytes)

            Log.d(TAG, "Extracted ${keypoints.rows()} keypoints, encoded size: ${bytes.size} bytes")

            mat.release()
            byteBuffer.release()
            return base64
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ORB descriptors: ${e.message}")
            null
        }
    }

    /**
     * Match current bitmap against stored descriptors.
     * Returns the best matching page number (1-based), or null if no good match.
     */
    fun matchPage(bitmap: Bitmap, storedDescriptors: List<Pair<Int, String>>): Int? {
        if (storedDescriptors.isEmpty()) return null

        val currentDescriptors = extractDescriptors(bitmap) ?: return null
        val currentMat = decodeDescriptors(currentDescriptors) ?: return null

        var bestPageNumber = -1
        var bestMatchCount = 0

        for ((pageNumber, storedBase64) in storedDescriptors) {
            val storedMat = decodeDescriptors(storedBase64) ?: continue

            try {
                val matches = MatOfDMatch()
                matcher.match(currentMat, storedMat, matches)

                // Filter good matches (low distance = better match)
                val goodMatches = matches.toArray()
                    .filter { it.distance < 80f }  // Lower distance = better match

                val matchCount = goodMatches.size
                Log.d(TAG, "Page $pageNumber: $matchCount good matches (total: ${matches.rows()})")

                if (matchCount > bestMatchCount) {
                    bestMatchCount = matchCount
                    bestPageNumber = pageNumber
                }

                storedMat.release()
            } catch (e: Exception) {
                Log.e(TAG, "Match failed for page $pageNumber: ${e.message}")
            }
        }

        currentMat.release()

        return if (bestMatchCount >= GOOD_MATCH_THRESHOLD) {
            Log.d(TAG, "Best match: page $bestPageNumber with $bestMatchCount good matches")
            bestPageNumber
        } else {
            Log.d(TAG, "No good match found (best: $bestMatchCount, need $GOOD_MATCH_THRESHOLD)")
            null
        }
    }

    /**
     * Decode Base64 encoded descriptors back to Mat.
     */
    private fun decodeDescriptors(base64: String): Mat? {
        return try {
            val bytes = Base64.getDecoder().decode(base64)
            val mat = Imgcodecs.imdecode(MatOfByte(*bytes), -1)  // IMREAD_UNCHANGED = -1
            if (mat.empty()) {
                Log.e(TAG, "Decoded descriptors are empty")
                return null
            }
            mat
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode descriptors: ${e.message}")
            null
        }
    }
}