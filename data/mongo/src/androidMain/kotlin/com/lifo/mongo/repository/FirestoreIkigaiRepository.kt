package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.model.IkigaiExploration
import com.lifo.util.repository.IkigaiRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock

class FirestoreIkigaiRepository(
    firestore: FirebaseFirestore,
) : IkigaiRepository {

    private val collection = firestore.collection("ikigai_exploration")

    @Suppress("UNCHECKED_CAST")
    override fun getExploration(userId: String): Flow<IkigaiExploration?> = callbackFlow {
        val reg = collection.document(userId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            try {
                val exploration = IkigaiExploration(
                    id = snapshot.id,
                    ownerId = userId,
                    passionItems = snapshot.get("passionItems") as? List<String> ?: emptyList(),
                    talentItems = snapshot.get("talentItems") as? List<String> ?: emptyList(),
                    missionItems = snapshot.get("missionItems") as? List<String> ?: emptyList(),
                    professionItems = snapshot.get("professionItems") as? List<String> ?: emptyList(),
                    aiInsight = snapshot.getString("aiInsight") ?: "",
                    createdAtMillis = snapshot.getLong("createdAtMillis") ?: 0L,
                    updatedAtMillis = snapshot.getLong("updatedAtMillis") ?: 0L,
                )
                trySend(exploration)
            } catch (_: Exception) {
                trySend(null)
            }
        }
        awaitClose { reg.remove() }
    }

    override suspend fun saveExploration(exploration: IkigaiExploration): Result<Unit> = runCatching {
        val data = hashMapOf(
            "passionItems" to exploration.passionItems,
            "talentItems" to exploration.talentItems,
            "missionItems" to exploration.missionItems,
            "professionItems" to exploration.professionItems,
            "aiInsight" to exploration.aiInsight,
            "createdAtMillis" to exploration.createdAtMillis,
            "updatedAtMillis" to Clock.System.now().toEpochMilliseconds(),
        )
        collection.document(exploration.ownerId).set(data).await()
    }

    override suspend fun deleteExploration(explorationId: String): Result<Unit> = runCatching {
        collection.document(explorationId).delete().await()
    }
}
