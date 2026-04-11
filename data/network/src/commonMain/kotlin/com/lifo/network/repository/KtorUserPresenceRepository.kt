package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.RequestState
import com.lifo.util.repository.UserPresenceRepository
import com.lifo.util.repository.UserPresenceRepository.PresenceStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

class KtorUserPresenceRepository(
    private val api: KtorApiClient,
) : UserPresenceRepository {

    override suspend fun setOnline(userId: String) {
        api.postNoBody("/api/v1/presence/online")
    }

    override suspend fun setOffline(userId: String) {
        api.postNoBody("/api/v1/presence/offline")
    }

    override fun observePresence(userId: String): Flow<PresenceStatus> = flow {
        // MVP: single fetch, not real-time.
        // Future: WebSocket /ws/presence for < 100ms updates.
        val result = api.get<PresenceDto>("/api/v1/presence/$userId")
        when (result) {
            is RequestState.Success -> emit(result.data.toPresenceStatus())
            else -> emit(PresenceStatus(userId = userId))
        }
    }

    override fun observeMultiplePresence(userIds: List<String>): Flow<Map<String, PresenceStatus>> = flow {
        if (userIds.isEmpty()) {
            emit(emptyMap())
            return@flow
        }
        val ids = userIds.take(50).joinToString(",")
        val result = api.get<PresenceListDto>("/api/v1/presence?userIds=$ids")
        when (result) {
            is RequestState.Success -> emit(
                result.data.data.associate { it.userId to it.toPresenceStatus() }
            )
            else -> emit(userIds.associateWith { PresenceStatus(userId = it) })
        }
    }
}

@Serializable
data class PresenceDto(
    val userId: String = "",
    val isOnline: Boolean = false,
    val lastSeenAt: Long = 0,
) {
    fun toPresenceStatus() = PresenceStatus(
        userId = userId,
        isOnline = isOnline,
        lastSeenAt = lastSeenAt,
    )
}

@Serializable
data class PresenceListDto(
    val data: List<PresenceDto> = emptyList(),
)
