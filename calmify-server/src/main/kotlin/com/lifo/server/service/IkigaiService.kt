package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class IkigaiService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(IkigaiService::class.java)

    companion object {
        private const val COLLECTION = "ikigai_exploration"
    }

    @Serializable
    data class ExplorationDto(
        val id: String = "",
        val ownerId: String = "",
        val passionItems: List<String> = emptyList(),
        val talentItems: List<String> = emptyList(),
        val missionItems: List<String> = emptyList(),
        val professionItems: List<String> = emptyList(),
        val aiInsight: String = "",
        val createdAtMillis: Long = 0L,
        val updatedAtMillis: Long = 0L,
    )

    suspend fun getExploration(userId: String): ExplorationDto? = withContext(Dispatchers.IO) {
        val doc = db.collection(COLLECTION).document(userId).get().get()
        if (!doc.exists()) return@withContext null
        @Suppress("UNCHECKED_CAST")
        ExplorationDto(
            id = doc.id,
            ownerId = userId,
            passionItems = doc.get("passionItems") as? List<String> ?: emptyList(),
            talentItems = doc.get("talentItems") as? List<String> ?: emptyList(),
            missionItems = doc.get("missionItems") as? List<String> ?: emptyList(),
            professionItems = doc.get("professionItems") as? List<String> ?: emptyList(),
            aiInsight = doc.getString("aiInsight") ?: "",
            createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
            updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L,
        )
    }

    suspend fun saveExploration(userId: String, dto: ExplorationDto): ExplorationDto = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val createdAt = if (dto.createdAtMillis > 0L) dto.createdAtMillis else now
        val data = hashMapOf<String, Any>(
            "passionItems" to dto.passionItems,
            "talentItems" to dto.talentItems,
            "missionItems" to dto.missionItems,
            "professionItems" to dto.professionItems,
            "aiInsight" to dto.aiInsight,
            "createdAtMillis" to createdAt,
            "updatedAtMillis" to now,
        )
        db.collection(COLLECTION).document(userId).set(data).get()
        logger.info("Saved ikigai exploration for user $userId")
        dto.copy(
            id = userId,
            ownerId = userId,
            createdAtMillis = createdAt,
            updatedAtMillis = now,
        )
    }

    suspend fun deleteExploration(userId: String, explorationId: String): Boolean = withContext(Dispatchers.IO) {
        // Only allow deletion of caller's own doc (explorationId == userId by our doc layout)
        if (explorationId != userId) return@withContext false
        val docRef = db.collection(COLLECTION).document(userId)
        val snap = docRef.get().get()
        if (!snap.exists()) return@withContext false
        docRef.delete().get()
        logger.info("Deleted ikigai exploration for user $userId")
        true
    }
}
