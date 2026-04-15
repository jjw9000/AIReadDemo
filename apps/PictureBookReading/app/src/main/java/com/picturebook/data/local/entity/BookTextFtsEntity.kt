package com.picturebook.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = BookPageEntity::class)
@Entity(tableName = "book_pages_fts")
data class BookTextFtsEntity(
    val fullText: String
)
