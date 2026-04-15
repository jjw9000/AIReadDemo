package com.picturebook.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.picturebook.data.local.entity.BookEntity
import com.picturebook.data.local.entity.BookPageEntity
import com.picturebook.data.local.entity.BookTextFtsEntity

@Database(
    entities = [BookEntity::class, BookPageEntity::class, BookTextFtsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: BookDatabase? = null

        fun getInstance(context: Context): BookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookDatabase::class.java,
                    "picturebook_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
