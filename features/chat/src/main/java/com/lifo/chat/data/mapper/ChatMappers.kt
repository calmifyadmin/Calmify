package com.lifo.chat.data.mapper

import com.lifo.chat.data.local.entities.ChatMessageEntity
import com.lifo.chat.data.local.entities.ChatSessionEntity
import com.lifo.chat.domain.model.ChatMessage
import com.lifo.chat.domain.model.ChatSession
import com.lifo.chat.domain.model.MessageStatus

// Session mappers
fun ChatSession.toEntity(): ChatSessionEntity {
    return ChatSessionEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        lastMessageAt = lastMessageAt,
        aiModel = aiModel,
        messageCount = messageCount
    )
}

fun ChatSessionEntity.toDomain(): ChatSession {
    return ChatSession(
        id = id,
        title = title,
        createdAt = createdAt,
        lastMessageAt = lastMessageAt,
        aiModel = aiModel,
        messageCount = messageCount
    )
}

// Message mappers
fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        sessionId = sessionId,
        content = content,
        isUser = isUser,
        timestamp = timestamp,
        status = status.name,
        error = error
    )
}

fun ChatMessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = id,
        sessionId = sessionId,
        content = content,
        isUser = isUser,
        timestamp = timestamp,
        status = MessageStatus.valueOf(status),
        error = error
    )
}