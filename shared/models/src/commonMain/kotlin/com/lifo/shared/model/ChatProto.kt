package com.lifo.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ChatSessionProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val createdAtMillis: Long = 0L,
    @ProtoNumber(5) val lastMessageAtMillis: Long = 0L,
    @ProtoNumber(6) val aiModel: String = "gemini-2.0-flash",
    @ProtoNumber(7) val messageCount: Int = 0,
)

@Serializable
data class ChatMessageProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val sessionId: String = "",
    @ProtoNumber(3) val content: String = "",
    @ProtoNumber(4) val isUser: Boolean = false,
    @ProtoNumber(5) val timestampMillis: Long = 0L,
    @ProtoNumber(6) val status: MessageStatusProto = MessageStatusProto.SENT,
    @ProtoNumber(7) val error: String = "",
)

@Serializable
enum class MessageStatusProto {
    @ProtoNumber(0) SENDING,
    @ProtoNumber(1) SENT,
    @ProtoNumber(2) FAILED,
    @ProtoNumber(3) STREAMING,
}
