package com.lifo.util.repository

import com.lifo.util.model.ProfileSettings
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * Profile Settings Repository Interface
 *
 * Manages user profile settings in Firestore
 */
interface ProfileSettingsRepository {
    fun getProfileSettings(): Flow<RequestState<ProfileSettings?>>
    suspend fun getProfileSettings(userId: String): RequestState<ProfileSettings?>
    suspend fun saveProfileSettings(settings: ProfileSettings): RequestState<Boolean>
    suspend fun deleteProfileSettings(userId: String): RequestState<Boolean>
    suspend fun hasCompletedOnboarding(): RequestState<Boolean>
    suspend fun completeOnboarding(): RequestState<Boolean>
}
