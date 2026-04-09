package com.lifo.shared.api

import com.lifo.shared.model.DiaryInsightProto
import com.lifo.shared.model.DiaryProto
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class DiaryResponse(
    @ProtoNumber(1) val data: DiaryProto? = null,
    @ProtoNumber(2) val error: ApiError? = null,
)

@Serializable
data class DiaryListResponse(
    @ProtoNumber(1) val data: List<DiaryProto> = emptyList(),
    @ProtoNumber(2) val error: ApiError? = null,
    @ProtoNumber(3) val meta: PaginationMeta? = null,
)

@Serializable
data class DiaryDeltaResponse(
    @ProtoNumber(1) val created: List<DiaryProto> = emptyList(),
    @ProtoNumber(2) val updated: List<DiaryProto> = emptyList(),
    @ProtoNumber(3) val deletedIds: List<String> = emptyList(),
    @ProtoNumber(4) val serverTime: Long = 0L,
)

@Serializable
data class DiaryInsightResponse(
    @ProtoNumber(1) val data: DiaryInsightProto? = null,
    @ProtoNumber(2) val error: ApiError? = null,
)

@Serializable
data class DiaryInsightListResponse(
    @ProtoNumber(1) val data: List<DiaryInsightProto> = emptyList(),
    @ProtoNumber(2) val error: ApiError? = null,
    @ProtoNumber(3) val meta: PaginationMeta? = null,
)
