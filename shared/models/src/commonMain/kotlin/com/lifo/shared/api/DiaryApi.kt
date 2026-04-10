package com.lifo.shared.api

import com.lifo.shared.model.DiaryInsightProto
import com.lifo.shared.model.DiaryProto
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class DiaryResponse(
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val data: DiaryProto = DiaryProto(),
    @ProtoNumber(3) val error: ApiError = ApiError(),
)

@Serializable
data class DiaryListResponse(
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val data: List<DiaryProto> = emptyList(),
    @ProtoNumber(3) val error: ApiError = ApiError(),
    @ProtoNumber(4) val meta: PaginationMeta = PaginationMeta(),
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
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val data: DiaryInsightProto = DiaryInsightProto(),
    @ProtoNumber(3) val error: ApiError = ApiError(),
)

@Serializable
data class DiaryInsightListResponse(
    @ProtoNumber(1) val success: Boolean = false,
    @ProtoNumber(2) val data: List<DiaryInsightProto> = emptyList(),
    @ProtoNumber(3) val error: ApiError = ApiError(),
    @ProtoNumber(4) val meta: PaginationMeta = PaginationMeta(),
)
