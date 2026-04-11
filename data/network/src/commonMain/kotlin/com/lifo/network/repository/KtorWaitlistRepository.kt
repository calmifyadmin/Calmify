package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.RequestState
import com.lifo.util.repository.WaitlistRepository
import kotlinx.serialization.Serializable

class KtorWaitlistRepository(
    private val api: KtorApiClient,
) : WaitlistRepository {

    override suspend fun saveWaitlistEmail(
        email: String,
        userId: String?,
        source: String,
    ): RequestState<Boolean> {
        val body = WaitlistRequest(email = email, userId = userId ?: "", source = source)
        val result = api.post<WaitlistResponse>("/api/v1/waitlist", body)
        return result.map { true }
    }
}

@Serializable
data class WaitlistRequest(
    val email: String,
    val userId: String = "",
    val source: String = "",
)

@Serializable
data class WaitlistResponse(
    val success: Boolean = true,
)
