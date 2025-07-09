package com.lifo.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lifo.chat.data.local.entities.ChatMessageEntity
import com.lifo.chat.data.local.entities.ChatSessionEntity
import com.lifo.chat.data.local.entities.Converters

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        const val DATABASE_NAME = "chat_database"
    }
}