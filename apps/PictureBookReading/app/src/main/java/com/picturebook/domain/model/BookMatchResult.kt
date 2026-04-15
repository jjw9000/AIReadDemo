package com.picturebook.domain.model

data class BookMatchResult(
    val bookId: String,
    val title: String,
    val pageNumber: Int,
    val similarity: Float,
    val isLocal: Boolean
)
