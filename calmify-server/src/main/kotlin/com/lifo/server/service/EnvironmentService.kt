package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class EnvironmentService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(EnvironmentService::class.java)

    companion object {
        private const val COLLECTION = "environment_design"
    }

    @Serializable
    data class ChecklistItemDto(
        val id: String = "",
        val text: String = "",
        val isCompleted: Boolean = false,
        val category: String = "GENERALE",
    )

    @Serializable
    data class RoutineStepDto(
        val id: String = "",
        val text: String = "",
        val durationMinutes: Int = 5,
        val isCompleted: Boolean = false,
    )

    @Serializable
    data class ChecklistDto(
        val id: String = "",
        val ownerId: String = "",
        val items: List<ChecklistItemDto> = emptyList(),
        val morningRoutine: List<RoutineStepDto> = emptyList(),
        val eveningRoutine: List<RoutineStepDto> = emptyList(),
        val detoxTimerMinutes: Int = 60,
    )

    suspend fun getChecklist(userId: String): ChecklistDto? = withContext(Dispatchers.IO) {
        val doc = db.collection(COLLECTION).document(userId).get().get()
        if (!doc.exists()) return@withContext null
        @Suppress("UNCHECKED_CAST")
        val itemsRaw = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
        val items = itemsRaw.map {
            ChecklistItemDto(
                id = it["id"] as? String ?: "",
                text = it["text"] as? String ?: "",
                isCompleted = it["isCompleted"] as? Boolean ?: false,
                category = it["category"] as? String ?: "GENERALE",
            )
        }
        @Suppress("UNCHECKED_CAST")
        val morningRaw = doc.get("morningRoutine") as? List<Map<String, Any>> ?: emptyList()
        val morning = morningRaw.map {
            RoutineStepDto(
                id = it["id"] as? String ?: "",
                text = it["text"] as? String ?: "",
                durationMinutes = (it["durationMinutes"] as? Long ?: 5).toInt(),
                isCompleted = it["isCompleted"] as? Boolean ?: false,
            )
        }
        @Suppress("UNCHECKED_CAST")
        val eveningRaw = doc.get("eveningRoutine") as? List<Map<String, Any>> ?: emptyList()
        val evening = eveningRaw.map {
            RoutineStepDto(
                id = it["id"] as? String ?: "",
                text = it["text"] as? String ?: "",
                durationMinutes = (it["durationMinutes"] as? Long ?: 5).toInt(),
                isCompleted = it["isCompleted"] as? Boolean ?: false,
            )
        }
        ChecklistDto(
            id = doc.id,
            ownerId = userId,
            items = items,
            morningRoutine = morning,
            eveningRoutine = evening,
            detoxTimerMinutes = (doc.getLong("detoxTimerMinutes") ?: 60).toInt(),
        )
    }

    suspend fun saveChecklist(userId: String, dto: ChecklistDto): ChecklistDto = withContext(Dispatchers.IO) {
        val data = hashMapOf<String, Any>(
            "items" to dto.items.map {
                mapOf(
                    "id" to it.id,
                    "text" to it.text,
                    "isCompleted" to it.isCompleted,
                    "category" to it.category,
                )
            },
            "morningRoutine" to dto.morningRoutine.map {
                mapOf(
                    "id" to it.id,
                    "text" to it.text,
                    "durationMinutes" to it.durationMinutes,
                    "isCompleted" to it.isCompleted,
                )
            },
            "eveningRoutine" to dto.eveningRoutine.map {
                mapOf(
                    "id" to it.id,
                    "text" to it.text,
                    "durationMinutes" to it.durationMinutes,
                    "isCompleted" to it.isCompleted,
                )
            },
            "detoxTimerMinutes" to dto.detoxTimerMinutes,
            "updatedAt" to System.currentTimeMillis(),
        )
        db.collection(COLLECTION).document(userId).set(data).get()
        logger.info("Saved environment checklist for user $userId")
        dto.copy(id = userId, ownerId = userId)
    }
}
