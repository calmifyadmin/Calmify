package com.lifo.mongo.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.model.ProfileSettings
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ProfileSettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock

/**
 * Firestore implementation of ProfileSettingsRepository
 */
class FirestoreProfileSettingsRepository constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ProfileSettingsRepository {

    companion object {
        private const val TAG = "ProfileSettingsRepo"
        private const val COLLECTION_PROFILE_SETTINGS = "profile_settings"
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    override fun getProfileSettings(): Flow<RequestState<ProfileSettings?>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(RequestState.Error(UserNotAuthenticatedException()))
            close()
            return@callbackFlow
        }

        trySend(RequestState.Loading)

        val listenerRegistration = firestore
            .collection(COLLECTION_PROFILE_SETTINGS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting profile settings", error)
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val settings = snapshot.toProfileSettings()
                        trySend(RequestState.Success(settings))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing profile settings", e)
                        trySend(RequestState.Error(e))
                    }
                } else {
                    // No profile settings yet - return null
                    trySend(RequestState.Success(null))
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override suspend fun saveProfileSettings(settings: ProfileSettings): RequestState<Boolean> {
        return try {
            val userId = currentUserId
            if (userId == null) {
                Log.w(TAG, "Cannot save profile settings: user not authenticated")
                return RequestState.Error(UserNotAuthenticatedException())
            }

            Log.d(TAG, "Saving profile settings for user: $userId")

            // Set document ID to userId
            val settingsToSave = settings.copy(
                id = userId,
                ownerId = userId,
                updatedAtMillis = Clock.System.now().toEpochMilliseconds()
            )

            firestore.collection(COLLECTION_PROFILE_SETTINGS)
                .document(userId)
                .set(settingsToSave.toFirestoreMap())
                .await()

            Log.d(TAG, "Profile settings saved successfully")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profile settings", e)
            RequestState.Error(e)
        }
    }

    override suspend fun hasCompletedOnboarding(): RequestState<Boolean> {
        return try {
            val userId = currentUserId
            if (userId == null) {
                return RequestState.Error(UserNotAuthenticatedException())
            }

            val doc = firestore.collection(COLLECTION_PROFILE_SETTINGS)
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                val settings = doc.toProfileSettings()
                RequestState.Success(settings?.isOnboardingCompleted ?: false)
            } else {
                RequestState.Success(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking onboarding status", e)
            RequestState.Error(e)
        }
    }

    override suspend fun getProfileSettings(userId: String): RequestState<ProfileSettings?> {
        return try {
            Log.d(TAG, "Getting profile settings for user: $userId")

            val doc = firestore.collection(COLLECTION_PROFILE_SETTINGS)
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                val settings = doc.toProfileSettings()
                Log.d(TAG, "Profile settings retrieved successfully")
                RequestState.Success(settings)
            } else {
                Log.d(TAG, "No profile settings found for user")
                RequestState.Success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting profile settings", e)
            RequestState.Error(e)
        }
    }

    override suspend fun deleteProfileSettings(userId: String): RequestState<Boolean> {
        return try {
            Log.d(TAG, "Deleting profile settings for user: $userId")

            firestore.collection(COLLECTION_PROFILE_SETTINGS)
                .document(userId)
                .delete()
                .await()

            Log.d(TAG, "Profile settings deleted successfully")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting profile settings", e)
            RequestState.Error(e)
        }
    }

    override suspend fun completeOnboarding(): RequestState<Boolean> {
        return try {
            val userId = currentUserId
            if (userId == null) {
                return RequestState.Error(UserNotAuthenticatedException())
            }

            firestore.collection(COLLECTION_PROFILE_SETTINGS)
                .document(userId)
                .update("isOnboardingCompleted", true)
                .await()

            Log.d(TAG, "Onboarding marked as completed")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error completing onboarding", e)
            RequestState.Error(e)
        }
    }
}
