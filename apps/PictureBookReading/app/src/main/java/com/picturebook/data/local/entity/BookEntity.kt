package com.picturebook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val bookId: String,
    val title: String,
    val isPlaceholder: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
