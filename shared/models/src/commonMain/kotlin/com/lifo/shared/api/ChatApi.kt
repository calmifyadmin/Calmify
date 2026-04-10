package com.lifo.shared.api

import com.lifo.shared.model.ChatMessageProto
import com.lifo.shared.model.ChatSessionProto
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ChatSessionResponse(
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val data: ChatSessionProto = ChatSessionProto(),
    @ProtoNumber(3) val error: ApiError = ApiError(),
)

@Serializable
data class ChatSessionListResponse(
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val data: List<ChatSessionProto> = emptyList(),
    @ProtoNumber(3) val error: ApiError = ApiError(),
    @ProtoNumber(4) val meta: PaginationMeta = PaginationMeta(),
)

@Serializable
data class ChatMessageListResponse(
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val data: List<ChatMessageProto> = emptyList(),
    @ProtoNumber(3) val error: ApiError = ApiError(),
    @ProtoNumber(4) val meta: PaginationMeta = PaginationMeta(),
)
