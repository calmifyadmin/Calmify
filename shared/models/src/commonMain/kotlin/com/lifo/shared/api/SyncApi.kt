package com.lifo.shared.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Generic delta sync response for any entity type.
 *
 * Uses List<String> for payload where each string is a JSON-encoded entity.
 * The client deserializes payload items based on the entity type.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GenericDeltaResponse(
    @ProtoNumber(1) val entityType: String = "",
    @ProtoNumber(2) val created: List<String> = emptyList(),
    @ProtoNumber(3) val updated: List<String> = emptyList(),
    @ProtoNumber(4) val deletedIds: List<String> = emptyList(),
    @ProtoNumber(5) val serverTime: Long = 0L,
)

/**
 * Batch sync request -- push multiple operations at once.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BatchSyncRequest(
    @ProtoNumber(1) val operations: List<SyncOperationDto> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SyncOperationDto(
    @ProtoNumber(1) val entityType: String = "",
    @ProtoNumber(2) val entityId: String = "",
    @ProtoNumber(3) val operation: String = "",  // CREATE, UPDATE, DELETE
    @ProtoNumber(4) val payload: String = "",     // JSON of the entity
)

/**
 * Batch sync response -- results for each operation.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BatchSyncResponse(
    @ProtoNumber(1) val results: List<SyncResultDto> = emptyList(),
    @ProtoNumber(2) val serverTime: Long = 0L,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SyncResultDto(
    @ProtoNumber(1) val entityId: String = "",
    @ProtoNumber(2) val success: Boolean = true,
    @ProtoNumber(3) val error: String = "",
    @ProtoNumber(4) val serverVersion: Long = 0L,  // updatedAt from server (for LWW)
)
