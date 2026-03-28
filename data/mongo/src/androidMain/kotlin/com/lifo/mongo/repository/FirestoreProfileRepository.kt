package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ProfileRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
class FirestoreProfileRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ProfileRepository {

    private val collection by lazy {
        // CRITICAL: Use calmify-native database (not default database)
        // The Cloud Function writes to this specific database
        val db = firestore
        // Note: FirebaseFirestore.getInstance() defaults to "(default)" database
        // To use calmify-native, the Cloud Function should specify databaseId
        // For now, we'll use the default collection path
        db.collection("psychological_profiles")
    }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    override fun getProfilesForUser(
        userId: String,
        weeks: Int
    ): Flow<RequestState<List<PsychologicalProfile>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = collection
            .whereEqualTo("ownerId", userId)
            .orderBy("year", Query.Direction.DESCENDING)
            .orderBy("weekNumber", Query.Direction.DESCENDING)
            .limit(weeks.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val profiles = snapshot.documents.mapNotNull { it.toPsychologicalProfile() }
                    trySend(RequestState.Success(profiles))
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override fun getLatestProfile(userId: String): Flow<RequestState<PsychologicalProfile?>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = collection
            .whereEqualTo("ownerId", userId)
            .orderBy("year", Query.Direction.DESCENDING)
            .orderBy("weekNumber", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val profile = snapshot.documents.first().toPsychologicalProfile()
                    trySend(RequestState.Success(profile))
                } else {
                    trySend(RequestState.Success(null))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    override fun getProfileByWeek(
        userId: String,
        weekNumber: Int,
        year: Int
    ): Flow<RequestState<PsychologicalProfile?>> = callbackFlow {
        trySend(RequestState.Loading)

        // Profile ID format: "{ownerId}_week_{weekNumber}_{year}"
        val documentId = "${userId}_week_${weekNumber}_$year"

        val listenerRegistration = collection
            .document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val profile = snapshot.toPsychologicalProfile()
                    trySend(RequestState.Success(profile))
                } else {
                    trySend(RequestState.Success(null))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }
}
