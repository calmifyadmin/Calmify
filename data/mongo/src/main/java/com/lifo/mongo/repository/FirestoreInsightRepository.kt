package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.RequestState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * FirestoreInsightRepository Implementation
 *
 * Firestore-backed implementation of InsightRepository
 * Collection: diary_insights
 */
class FirestoreInsightRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : InsightRepository {

    private val collection = firestore.collection("diary_insights")

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    override fun getInsightByDiaryId(diaryId: String): Flow<RequestState<DiaryInsight?>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = collection
            .whereEqualTo("ownerId", currentUserId)
            .whereEqualTo("diaryId", diaryId)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val insight = snapshot.documents.first().toObject(DiaryInsight::class.java)
                    trySend(RequestState.Success(insight))
                } else {
                    trySend(RequestState.Success(null))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override fun getAllInsights(): Flow<RequestState<List<DiaryInsight>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = collection
            .whereEqualTo("ownerId", currentUserId)
            .orderBy("generatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val insights = snapshot.toObjects(DiaryInsight::class.java)
                    trySend(RequestState.Success(insights))
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override suspend fun insertInsight(insight: DiaryInsight): RequestState<String> {
        return try {
            // Set ownerId if not already set
            if (insight.ownerId.isEmpty()) {
                insight.ownerId = currentUserId
            }

            // Add to Firestore
            val docRef = collection.document()
            insight.id = docRef.id
            docRef.set(insight).await()

            RequestState.Success(insight.id)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override suspend fun submitFeedback(
        insightId: String,
        rating: Int,
        correction: String
    ): RequestState<Boolean> {
        return try {
            val updates = hashMapOf<String, Any>(
                "userRating" to rating,
                "userCorrection" to correction
            )

            collection.document(insightId)
                .update(updates)
                .await()

            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }

    override suspend fun deleteInsight(insightId: String): RequestState<Boolean> {
        return try {
            collection.document(insightId).delete().await()
            RequestState.Success(true)
        } catch (e: Exception) {
            RequestState.Error(e)
        }
    }
}
