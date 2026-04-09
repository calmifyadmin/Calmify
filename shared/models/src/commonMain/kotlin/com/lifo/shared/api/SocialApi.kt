package com.lifo.shared.api

import com.lifo.shared.model.ThreadProto
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ThreadResponse(
    @ProtoNumber(1) val data: ThreadProto? = null,
    @ProtoNumber(2) val error: ApiError? = null,
)

@Serializable
data class ThreadListResponse(
    @ProtoNumber(1) val data: List<ThreadProto> = emptyList(),
    @ProtoNumber(2) val error: ApiError? = null,
    @ProtoNumber(3) val meta: PaginationMeta? = null,
)

@Serializable
data class ThreadDeltaResponse(
    @ProtoNumber(1) val created: List<ThreadProto> = emptyList(),
    @ProtoNumber(2) val updated: List<ThreadProto> = emptyList(),
    @ProtoNumber(3) val deletedIds: List<String> = emptyList(),
    @ProtoNumber(4) val serverTime: Long = 0L,
)
