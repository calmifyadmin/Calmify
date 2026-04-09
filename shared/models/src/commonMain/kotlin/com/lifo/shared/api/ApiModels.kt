package com.lifo.shared.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Generic API response wrapper used by all server endpoints.
 * Supports both Protobuf (mobile) and JSON (debug/browser) serialization.
 *
 * NOTE: kotlinx.serialization.protobuf does NOT support generic @Serializable
 * data classes with type parameter T. We use concrete typed responses instead.
 */
@Serializable
data class ApiError(
    @ProtoNumber(1) val code: String = "",
    @ProtoNumber(2) val message: String = "",
)

@Serializable
data class PaginationMeta(
    @ProtoNumber(1) val cursor: String = "",
    @ProtoNumber(2) val hasMore: Boolean = false,
    @ProtoNumber(3) val totalCount: Int = 0,
)
