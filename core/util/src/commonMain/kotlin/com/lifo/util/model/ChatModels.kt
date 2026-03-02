package com.lifo.util.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ChatSession(
    val id: String = Uuid.random().toString(),
    val title: String = "New Chat",
    val createdAt: Instant = Clock.System.now(),
    val lastMessageAt: Instant = Clock.System.now(),
    val aiModel: String = "gemini-2.0-flash",
    val messageCount: Int = 0,
    val ownerId: String = ""
)

@OptIn(ExperimentalUuidApi::class)
data class ChatMessage(
    val id: String = Uuid.random().toString(),
    val sessionId: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Instant = Clock.System.now(),
    val status: MessageStatus = MessageStatus.SENT,
    val error: String? = null
)

enum class MessageStatus {
    SENDING,
    SENT,
    FAILED,
    STREAMING
}
