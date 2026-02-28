package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.model.ProfileSettings
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ProfileSettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Firestore implementation of ProfileSettingsRepository
 */
class FirestoreProfileSettingsRepository @Inject constructor(
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
                    println("[" + TAG + "] ERROR: " + "Error getting profile settings")
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val settings = snapshot.toObject(ProfileSettings::class.java)
                        trySend(RequestState.Success(settings))
                    } catch (e: Exception) {
                        println("[" + TAG + "] ERROR: " + "Error parsing profile settings")
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
                println("[" + TAG + "] WARN: " + "Cannot save profile settings: user not authenticated")
                return RequestState.Error(UserNotAuthenticatedException())
            }

            println("[" + TAG + "] " + "Saving profile settings for user: $userId")

            // Set document ID to userId
            val settingsToSave = settings.copy(
                id = userId,
                ownerId = userId,
                updatedAt = com.google.firebase.Timestamp.now().toDate()
            )

            firestore.collection(COLLECTION_PROFILE_SETTINGS)
                .document(userId)
                .set(settingsToSave)
                .await()

            println("[" + TAG + "] " + "Profile settings saved successfully")
            RequestState.Success(true)
        } catch (e: Exception) {
            println("[" + TAG + "] ERROR: " + "Error saving profile settings")
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
                val settings = doc.toObject(ProfileSettings::class.java)
                RequestState.Success(settings?.isOnboardingCompleted ?: false)
            } else {
                RequestState.Success(false)
            }
        } catch (e: Exception) {
            println("[" + TAG + "] ERROR: " + "Error checking onboarding status")
            RequestState.Error(e)
        }
    }

    override suspend fun getProfileSettings(userId: String): RequestState<ProfileSettings?> {
        return try {
            println("[" + TAG + "] " + "Getting profile settings for user: $userId")

            val doc = firestore.collection(COLLECTION_PROFILE_SETTINGS)
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                val settings = doc.toObject(ProfileSettings::class.java)
                println("[" + TAG + "] " + "Profile settings retrieved successfully")
                RequestState.Success(settings)
            } else {
                println("[" + TAG + "] " + "No profile settings found for user")
                RequestState.Success(null)
            }
        } catch (e: Exception) {
            println("[" + TAG + "] ERROR: " + "Error getting profile settings")
            RequestState.Error(e)
        }
    }

    override suspend fun deleteProfileSettings(userId: String): RequestState<Boolean> {
        return try {
            println("[" + TAG + "] " + "Deleting profile settings for user: $userId")

            firestore.collection(COLLECTION_PROFILE_SETTINGS)
                .document(userId)
                .delete()
                .await()

            println("[" + TAG + "] " + "Profile settings deleted successfully")
            RequestState.Success(true)
        } catch (e: Exception) {
            println("[" + TAG + "] ERROR: " + "Error deleting profile settings")
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

            println("[" + TAG + "] " + "Onboarding marked as completed")
            RequestState.Success(true)
        } catch (e: Exception) {
            println("[" + TAG + "] ERROR: " + "Error completing onboarding")
            RequestState.Error(e)
        }
    }
}
