package com.lifo.mongo.repository

import com.lifo.util.model.ProfileSettings
import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * Profile Settings Repository Interface
 *
 * Manages user profile settings in Firestore
 */
interface ProfileSettingsRepository {
    /**
     * Get user's profile settings (real-time)
     */
    fun getProfileSettings(): Flow<RequestState<ProfileSettings?>>

    /**
     * Get specific user's profile settings (one-time)
     */
    suspend fun getProfileSettings(userId: String): RequestState<ProfileSettings?>

    /**
     * Save or update profile settings
     */
    suspend fun saveProfileSettings(settings: ProfileSettings): RequestState<Boolean>

    /**
     * Delete user's profile settings
     */
    suspend fun deleteProfileSettings(userId: String): RequestState<Boolean>

    /**
     * Check if user has completed onboarding
     */
    suspend fun hasCompletedOnboarding(): RequestState<Boolean>

    /**
     * Mark onboarding as completed
     */
    suspend fun completeOnboarding(): RequestState<Boolean>
}
