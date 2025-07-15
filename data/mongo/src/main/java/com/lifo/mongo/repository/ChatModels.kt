package com.lifo.mongo.repository

import java.time.Instant
import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val createdAt: Instant = Instant.now(),
    val lastMessageAt: Instant = Instant.now(),
    val aiModel: String = "gemini-2.0-flash",
    val messageCount: Int = 0,
    val ownerId: String = ""
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Instant = Instant.now(),
    val status: MessageStatus = MessageStatus.SENT,
    val error: String? = null
)

enum class MessageStatus {
    SENDING,
    SENT,
    FAILED,
    STREAMING
}