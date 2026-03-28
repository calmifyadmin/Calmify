package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.repository.GardenRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreGardenRepository(
    private val firestore: FirebaseFirestore,
) : GardenRepository {

    private fun gardenDoc(userId: String) =
        firestore.collection("garden").document(userId)

    @Suppress("UNCHECKED_CAST")
    override fun getExploredActivities(userId: String): Flow<Set<String>> = callbackFlow {
        val reg = gardenDoc(userId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(emptySet())
                return@addSnapshotListener
            }
            val list = snapshot.get("exploredActivities") as? List<String> ?: emptyList()
            trySend(list.toSet())
        }
        awaitClose { reg.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getFavorites(userId: String): Flow<Set<String>> = callbackFlow {
        val reg = gardenDoc(userId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(emptySet())
                return@addSnapshotListener
            }
            val list = snapshot.get("favorites") as? List<String> ?: emptyList()
            trySend(list.toSet())
        }
        awaitClose { reg.remove() }
    }

    override suspend fun markExplored(userId: String, activityId: String) {
        val doc = gardenDoc(userId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(doc)
            @Suppress("UNCHECKED_CAST")
            val current = snapshot.get("exploredActivities") as? List<String> ?: emptyList()
            if (activityId !in current) {
                transaction.set(
                    doc,
                    mapOf(
                        "exploredActivities" to current + activityId,
                        "favorites" to (snapshot.get("favorites") as? List<String> ?: emptyList()),
                    ),
                    com.google.firebase.firestore.SetOptions.merge(),
                )
            }
        }.await()
    }

    override suspend fun toggleFavorite(userId: String, activityId: String) {
        val doc = gardenDoc(userId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(doc)
            @Suppress("UNCHECKED_CAST")
            val current = snapshot.get("favorites") as? List<String> ?: emptyList()
            val updated = if (activityId in current) current - activityId else current + activityId
            transaction.set(
                doc,
                mapOf(
                    "favorites" to updated,
                    "exploredActivities" to (snapshot.get("exploredActivities") as? List<String> ?: emptyList()),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
        }.await()
    }
}
