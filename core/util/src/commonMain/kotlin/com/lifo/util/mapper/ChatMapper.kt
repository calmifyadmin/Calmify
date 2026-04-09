package com.lifo.util.mapper

import com.lifo.shared.model.ChatMessageProto
import com.lifo.shared.model.ChatSessionProto
import com.lifo.shared.model.MessageStatusProto
import com.lifo.util.model.ChatMessage
import com.lifo.util.model.ChatSession
import com.lifo.util.model.MessageStatus
import kotlinx.datetime.Instant

// --- ChatSession ---

fun ChatSession.toProto(): ChatSessionProto = ChatSessionProto(
    id = id,
    ownerId = ownerId,
    title = title,
    createdAtMillis = createdAt.toEpochMilliseconds(),
    lastMessageAtMillis = lastMessageAt.toEpochMilliseconds(),
    aiModel = aiModel,
    messageCount = messageCount,
)

fun ChatSessionProto.toDomain(): ChatSession = ChatSession(
    id = id,
    ownerId = ownerId,
    title = title,
    createdAt = Instant.fromEpochMilliseconds(createdAtMillis),
    lastMessageAt = Instant.fromEpochMilliseconds(lastMessageAtMillis),
    aiModel = aiModel,
    messageCount = messageCount,
)

// --- ChatMessage ---

fun ChatMessage.toProto(): ChatMessageProto = ChatMessageProto(
    id = id,
    sessionId = sessionId,
    content = content,
    isUser = isUser,
    timestampMillis = timestamp.toEpochMilliseconds(),
    status = status.toProto(),
    error = error ?: "",
)

fun ChatMessageProto.toDomain(): ChatMessage = ChatMessage(
    id = id,
    sessionId = sessionId,
    content = content,
    isUser = isUser,
    timestamp = Instant.fromEpochMilliseconds(timestampMillis),
    status = status.toDomain(),
    error = error.takeIf { it.isNotEmpty() },
)

// --- MessageStatus ---

fun MessageStatus.toProto(): MessageStatusProto = when (this) {
    MessageStatus.SENDING -> MessageStatusProto.SENDING
    MessageStatus.SENT -> MessageStatusProto.SENT
    MessageStatus.FAILED -> MessageStatusProto.FAILED
    MessageStatus.STREAMING -> MessageStatusProto.STREAMING
}

fun MessageStatusProto.toDomain(): MessageStatus = when (this) {
    MessageStatusProto.SENDING -> MessageStatus.SENDING
    MessageStatusProto.SENT -> MessageStatus.SENT
    MessageStatusProto.FAILED -> MessageStatus.FAILED
    MessageStatusProto.STREAMING -> MessageStatus.STREAMING
}
