package com.picturebook.domain.model

data class BookDetails(
    val bookId: String,
    val title: String,
    val pages: List<PageDetails>
)
