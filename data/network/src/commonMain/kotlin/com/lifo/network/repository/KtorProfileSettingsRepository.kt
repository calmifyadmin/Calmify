package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.ProfileSettingsProto
import com.lifo.util.mapper.toDomain
import com.lifo.util.mapper.toProto
import com.lifo.util.model.ProfileSettings
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ProfileSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorProfileSettingsRepository(
    private val api: KtorApiClient,
) : ProfileSettingsRepository {

    override fun getProfileSettings(): Flow<RequestState<ProfileSettings?>> = flow {
        emit(RequestState.Loading)
        val result = api.get<ProfileSettingsProto>("/api/v1/profile")
        emit(result.map { it.toDomain() })
    }

    override suspend fun getProfileSettings(userId: String): RequestState<ProfileSettings?> {
        return api.get<ProfileSettingsProto>("/api/v1/profile").map { it.toDomain() }
    }

    override suspend fun saveProfileSettings(settings: ProfileSettings): RequestState<Boolean> {
        val result = api.put<ProfileSettingsProto>("/api/v1/profile", settings.toProto())
        return result.map { true }
    }

    override suspend fun deleteProfileSettings(userId: String): RequestState<Boolean> {
        // Profile deletion is handled by GDPR endpoint, not a direct delete
        return api.deleteNoBody("/api/v1/profile")
    }

    override suspend fun hasCompletedOnboarding(): RequestState<Boolean> {
        val result = api.get<ProfileSettingsProto>("/api/v1/profile")
        return result.map { it.isOnboardingCompleted }
    }

    override suspend fun completeOnboarding(): RequestState<Boolean> {
        val result = api.get<ProfileSettingsProto>("/api/v1/profile")
        return when (result) {
            is RequestState.Success -> {
                val updated = result.data.copy(isOnboardingCompleted = true)
                val saveResult = api.put<ProfileSettingsProto>("/api/v1/profile", updated)
                saveResult.map { true }
            }
            is RequestState.Error -> result
            else -> result.map { true }
        }
    }
}
