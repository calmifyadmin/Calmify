package com.lifo.mongo.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lifo.util.Constants.CHAT_SESSION_TABLE

@Entity(tableName = CHAT_SESSION_TABLE)
data class ChatSessionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long,
    val lastMessageAt: Long,
    val aiModel: String,
    val messageCount: Int,
    val ownerId: String
)