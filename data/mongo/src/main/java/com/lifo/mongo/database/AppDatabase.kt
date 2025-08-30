package com.lifo.mongo.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lifo.mongo.database.dao.*
import com.lifo.mongo.database.entity.*

@Database(
    entities = [
        ImageToUpload::class,
        ImageToDelete::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 4, // Updated for unified feed fields in ChatSessionEntity
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase() {
    abstract fun imageToUploadDao(): ImageToUploadDao
    abstract fun imageToDeleteDao(): ImageToDeleteDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
}