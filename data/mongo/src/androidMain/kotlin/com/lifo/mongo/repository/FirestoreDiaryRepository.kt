package com.lifo.mongo.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.lifo.util.repository.MongoRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.LocalDate

/**
 * Firestore Diary Repository - 2025 Stack
 *
 * Sostituisce MongoDB/Realm con Firebase Firestore
 * Offline-first con cache persistente
 * Implements MongoRepository for backward compatibility
 */
class FirestoreDiaryRepository constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : MongoRepository {
    companion object {
        private const val TAG = "FirestoreDiaryRepo"
        private const val COLLECTION_DIARIES = "diaries"
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Ottiene tutti i diary dell'utente corrente, ordinati per data
     */
    override fun getAllDiaries(): Flow<RequestState<Map<LocalDate, List<Diary>>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(RequestState.Error(UserNotAuthenticatedException()))
            close()
            return@callbackFlow
        }

        val listenerRegistration = firestore
            .collection(COLLECTION_DIARIES)
            .whereEqualTo("ownerId", userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting diaries", error)
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    try {
                        val diaries = snapshot.documents.mapNotNull { doc ->
                            doc.toDiary()
                        }

                        // Group by dayKey (timezone-safe) and convert to LocalDate for Map key
                        val grouped = diaries
                            .filter { it.dayKey.isNotBlank() } // Skip diaries without dayKey (old data)
                            .groupBy {
                                // Convert dayKey "YYYY-MM-DD" to LocalDate
                                LocalDate.parse(it.dayKey)
                            }

                        trySend(RequestState.Success(grouped))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing diaries", e)
                        trySend(RequestState.Error(e))
                    }
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    /**
     * Ottiene diary filtrati per una specifica data
     * Usa dayKey per filtraggio timezone-safe
     */
    override fun getFilteredDiaries(dayKey: String): Flow<RequestState<Map<LocalDate, List<Diary>>>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(RequestState.Error(UserNotAuthenticatedException()))
            close()
            return@callbackFlow
        }

        val listenerRegistration = firestore
            .collection(COLLECTION_DIARIES)
            .whereEqualTo("ownerId", userId)
            .whereEqualTo("dayKey", dayKey) // Use dayKey for filtering (timezone-safe)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting filtered diaries", error)
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    try {
                        val diaries = snapshot.documents.mapNotNull { doc ->
                            doc.toDiary()
                        }

                        // Group by dayKey (timezone-safe) and convert to LocalDate for Map key
                        val grouped = diaries
                            .filter { it.dayKey.isNotBlank() } // Skip diaries without dayKey (old data)
                            .groupBy {
                                // Convert dayKey "YYYY-MM-DD" to LocalDate
                                LocalDate.parse(it.dayKey)
                            }

                        trySend(RequestState.Success(grouped))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing filtered diaries", e)
                        trySend(RequestState.Error(e))
                    }
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    /**
     * Ottiene un singolo diary per ID
     */
    override fun getSelectedDiary(diaryId: String): Flow<RequestState<Diary>> = callbackFlow {
        val userId = currentUserId
        if (userId == null) {
            trySend(RequestState.Error(UserNotAuthenticatedException()))
            close()
            return@callbackFlow
        }

        val listenerRegistration = firestore
            .collection(COLLECTION_DIARIES)
            .document(diaryId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting diary", error)
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val diary = snapshot.toDiary()
                        if (diary != null && diary.ownerId == userId) {
                            trySend(RequestState.Success(diary))
                        } else {
                            trySend(RequestState.Error(Exception("Diary not found or access denied")))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing diary", e)
                        trySend(RequestState.Error(e))
                    }
                } else {
                    trySend(RequestState.Error(Exception("Diary not found")))
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    /**
     * Inserisce un nuovo diary
     */
    override suspend fun insertDiary(diary: Diary): RequestState<Diary> {
        val userId = currentUserId
            ?: return RequestState.Error(UserNotAuthenticatedException())

        return try {
            Log.d(TAG, "Inserting diary: ${diary.title}")

            // Imposta owner
            diary.ownerId = userId

            // Crea documento con ID auto-generato
            val docRef = firestore.collection(COLLECTION_DIARIES).document()
            diary._id = docRef.id

            // Salva
            docRef.set(diary.toFirestoreMap()).await()

            Log.d(TAG, "Diary inserted successfully: ${diary._id}")
            RequestState.Success(diary)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting diary", e)
            RequestState.Error(e)
        }
    }

    /**
     * Aggiorna un diary esistente
     */
    override suspend fun updateDiary(diary: Diary): RequestState<Diary> {
        val userId = currentUserId
            ?: return RequestState.Error(UserNotAuthenticatedException())

        return try {
            Log.d(TAG, "Updating diary: ${diary._id}")

            // Verifica ownership
            if (diary.ownerId != userId) {
                return RequestState.Error(Exception("Access denied"))
            }

            // Aggiorna
            firestore.collection(COLLECTION_DIARIES)
                .document(diary._id)
                .set(diary.toFirestoreMap())
                .await()

            Log.d(TAG, "Diary updated successfully")
            RequestState.Success(diary)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating diary", e)
            RequestState.Error(e)
        }
    }

    /**
     * Elimina un diary
     */
    override suspend fun deleteDiary(id: String): RequestState<Boolean> {
        val userId = currentUserId
            ?: return RequestState.Error(UserNotAuthenticatedException())

        return try {
            Log.d(TAG, "Deleting diary: $id")

            // Verifica ownership prima di eliminare
            val doc = firestore.collection(COLLECTION_DIARIES).document(id).get().await()
            val diary = doc.toDiary()

            if (diary == null || diary.ownerId != userId) {
                return RequestState.Error(Exception("Diary not found or access denied"))
            }

            // Elimina
            firestore.collection(COLLECTION_DIARIES).document(id).delete().await()

            Log.d(TAG, "Diary deleted successfully")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting diary", e)
            RequestState.Error(e)
        }
    }

    /**
     * Elimina tutti i diary dell'utente
     */
    override suspend fun deleteAllDiaries(): RequestState<Boolean> {
        val userId = currentUserId
            ?: return RequestState.Error(UserNotAuthenticatedException())

        return try {
            Log.d(TAG, "Deleting all diaries for user: $userId")

            // Ottieni tutti i documenti
            val snapshot = firestore.collection(COLLECTION_DIARIES)
                .whereEqualTo("ownerId", userId)
                .get()
                .await()

            Log.d(TAG, "Found ${snapshot.size()} diaries to delete")

            // Elimina in batch (max 500 per batch)
            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()

            Log.d(TAG, "All diaries deleted successfully")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all diaries", e)
            RequestState.Error(e)
        }
    }

    /**
     * Delete ALL user data from Firestore
     * Collections: diaries, diary_insights, psychological_profiles, wellbeing_snapshots,
     *              chat_sessions, chat_messages, profile_settings
     */
    override suspend fun deleteAllUserData(): RequestState<Boolean> {
        val userId = currentUserId
            ?: return RequestState.Error(UserNotAuthenticatedException())

        return try {
            Log.d(TAG, "Deleting ALL data for user: $userId")

            // Collections to delete (with ownerId field)
            val collections = listOf(
                "diaries",
                "diary_insights",
                "psychological_profiles",
                "wellbeing_snapshots",
                "chat_sessions",
                "chat_messages"
            )

            var totalDeleted = 0

            // Delete collections that use ownerId field
            collections.forEach { collectionName ->
                try {
                    val snapshot = firestore.collection(collectionName)
                        .whereEqualTo("ownerId", userId)
                        .get()
                        .await()

                    Log.d(TAG, "Found ${snapshot.size()} documents in $collectionName")

                    if (snapshot.size() > 0) {
                        val batch = firestore.batch()
                        snapshot.documents.forEach { doc ->
                            batch.delete(doc.reference)
                        }
                        batch.commit().await()
                        totalDeleted += snapshot.size()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error deleting from $collectionName: ${e.message}")
                    // Continue with other collections even if one fails
                }
            }

            // Delete profile_settings (uses document ID = userId, not ownerId field)
            try {
                val profileSettingsDoc = firestore.collection("profile_settings")
                    .document(userId)
                    .get()
                    .await()

                if (profileSettingsDoc.exists()) {
                    profileSettingsDoc.reference.delete().await()
                    totalDeleted++
                    Log.d(TAG, "Deleted profile_settings document")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting profile_settings: ${e.message}")
                // Continue even if this fails
            }

            // Delete users document (FCM token storage - uses document ID = userId)
            try {
                val usersDoc = firestore.collection("users")
                    .document(userId)
                    .get()
                    .await()

                if (usersDoc.exists()) {
                    usersDoc.reference.delete().await()
                    totalDeleted++
                    Log.d(TAG, "Deleted users document (FCM token)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting users document: ${e.message}")
                // Continue even if this fails
            }

            Log.d(TAG, "All user data deleted successfully. Total: $totalDeleted documents")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all user data", e)
            RequestState.Error(e)
        }
    }

    /**
     * Salva FCM token in Firestore (Week 8)
     */
    override suspend fun saveFCMToken(token: String): RequestState<Boolean> {
        return try {
            val userId = currentUserId
            if (userId == null) {
                Log.w(TAG, "Cannot save FCM token: user not authenticated")
                return RequestState.Error(UserNotAuthenticatedException())
            }

            Log.d(TAG, "Saving FCM token for user: $userId")

            // Salva in Firestore users collection
            firestore.collection("users")
                .document(userId)
                .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                .await()

            Log.d(TAG, "FCM token saved successfully")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving FCM token", e)
            RequestState.Error(e)
        }
    }
}

class UserNotAuthenticatedException : Exception("User is not logged in")
