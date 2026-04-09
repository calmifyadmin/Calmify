package com.lifo.shared.api

import com.lifo.shared.model.ChatMessageProto
import com.lifo.shared.model.ChatSessionProto
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ChatSessionResponse(
    @ProtoNumber(1) val data: ChatSessionProto? = null,
    @ProtoNumber(2) val error: ApiError? = null,
)

@Serializable
data class ChatSessionListResponse(
    @ProtoNumber(1) val data: List<ChatSessionProto> = emptyList(),
    @ProtoNumber(2) val error: ApiError? = null,
    @ProtoNumber(3) val meta: PaginationMeta? = null,
)

@Serializable
data class ChatMessageListResponse(
    @ProtoNumber(1) val data: List<ChatMessageProto> = emptyList(),
    @ProtoNumber(2) val error: ApiError? = null,
    @ProtoNumber(3) val meta: PaginationMeta? = null,
)
