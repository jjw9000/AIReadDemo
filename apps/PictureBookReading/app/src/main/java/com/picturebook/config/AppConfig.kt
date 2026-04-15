package com.picturebook.config

/**
 * Application configuration - centralized settings
 */
object AppConfig {
    // API endpoints
    const val BOOK_API_HOST = "192.168.3.18"
    const val BOOK_API_PORT = 5018
    const val OCR_API_HOST = "192.168.3.18"
    const val OCR_API_PORT = 5017

    val BOOK_API_URL = "http://$BOOK_API_HOST:$BOOK_API_PORT"
    val OCR_API_URL = "http://$OCR_API_HOST:$OCR_API_PORT"

    // Match thresholds
    const val JACCARD_SIMILARITY_THRESHOLD = 0.3f
    const val ORB_MATCH_THRESHOLD = 15
    const val ORB_HAMMING_THRESHOLD = 80f
    const val MAX_KEYPOINTS = 500

    // Database
    const val DATABASE_NAME = "picturebook_database"
    const val DATABASE_VERSION = 2

    // Camera
    const val CAMERA_FOCUS_DELAY_MS = 1000L

    // Network
    const val HTTP_CONNECT_TIMEOUT_MS = 30000
    const val HTTP_READ_TIMEOUT_MS = 60000
    const val OCR_HTTP_READ_TIMEOUT_MS = 120000
}
