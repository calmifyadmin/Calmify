package com.lifo.mongo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.lifo.util.model.*
import com.lifo.util.repository.EnvironmentRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreEnvironmentRepository(
    firestore: FirebaseFirestore,
) : EnvironmentRepository {

    private val collection = firestore.collection("environment_design")

    override fun getChecklist(userId: String): Flow<EnvironmentChecklist?> = callbackFlow {
        val reg = collection.document(userId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(snapshot.toChecklist(userId))
        }
        awaitClose { reg.remove() }
    }

    override suspend fun saveChecklist(checklist: EnvironmentChecklist): Result<Unit> = runCatching {
        val data = hashMapOf(
            "items" to checklist.items.map { item ->
                hashMapOf(
                    "id" to item.id,
                    "text" to item.text,
                    "isCompleted" to item.isCompleted,
                    "category" to item.category.name,
                )
            },
            "morningRoutine" to checklist.morningRoutine.map { step ->
                hashMapOf("id" to step.id, "text" to step.text, "durationMinutes" to step.durationMinutes, "isCompleted" to step.isCompleted)
            },
            "eveningRoutine" to checklist.eveningRoutine.map { step ->
                hashMapOf("id" to step.id, "text" to step.text, "durationMinutes" to step.durationMinutes, "isCompleted" to step.isCompleted)
            },
            "detoxTimerMinutes" to checklist.detoxTimerMinutes,
        )
        collection.document(checklist.ownerId).set(data).await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun com.google.firebase.firestore.DocumentSnapshot.toChecklist(userId: String): EnvironmentChecklist? = try {
        val itemsRaw = get("items") as? List<Map<String, Any>> ?: emptyList()
        val items = itemsRaw.map { m ->
            ChecklistItem(
                id = m["id"] as? String ?: "",
                text = m["text"] as? String ?: "",
                isCompleted = m["isCompleted"] as? Boolean ?: false,
                category = try { ChecklistCategory.valueOf(m["category"] as? String ?: "GENERALE") } catch (_: Exception) { ChecklistCategory.GENERALE },
            )
        }
        val morningRaw = get("morningRoutine") as? List<Map<String, Any>> ?: emptyList()
        val morning = morningRaw.map { m ->
            RoutineStep(id = m["id"] as? String ?: "", text = m["text"] as? String ?: "", durationMinutes = (m["durationMinutes"] as? Long ?: 5).toInt(), isCompleted = m["isCompleted"] as? Boolean ?: false)
        }
        val eveningRaw = get("eveningRoutine") as? List<Map<String, Any>> ?: emptyList()
        val evening = eveningRaw.map { m ->
            RoutineStep(id = m["id"] as? String ?: "", text = m["text"] as? String ?: "", durationMinutes = (m["durationMinutes"] as? Long ?: 5).toInt(), isCompleted = m["isCompleted"] as? Boolean ?: false)
        }
        EnvironmentChecklist(
            id = id,
            ownerId = userId,
            items = items.ifEmpty { defaultChecklist() },
            morningRoutine = morning.ifEmpty { defaultMorningRoutine() },
            eveningRoutine = evening.ifEmpty { defaultEveningRoutine() },
            detoxTimerMinutes = (getLong("detoxTimerMinutes") ?: 60).toInt(),
        )
    } catch (_: Exception) { null }
}
