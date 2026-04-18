package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class GardenService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(GardenService::class.java)

    companion object {
        private const val COLLECTION = "garden"
    }

    @Serializable
    data class GardenStateDto(
        val exploredActivities: List<String> = emptyList(),
        val favorites: List<String> = emptyList(),
    )

    suspend fun getState(userId: String): GardenStateDto = withContext(Dispatchers.IO) {
        val doc = db.collection(COLLECTION).document(userId).get().get()
        if (!doc.exists()) return@withContext GardenStateDto()
        @Suppress("UNCHECKED_CAST")
        val explored = doc.get("exploredActivities") as? List<String> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val favs = doc.get("favorites") as? List<String> ?: emptyList()
        GardenStateDto(exploredActivities = explored, favorites = favs)
    }

    suspend fun markExplored(userId: String, activityId: String): GardenStateDto = withContext(Dispatchers.IO) {
        val docRef = db.collection(COLLECTION).document(userId)
        // Firestore transaction to read-modify-write idempotently
        db.runTransaction { tx ->
            val snap = tx.get(docRef).get()
            @Suppress("UNCHECKED_CAST")
            val current = snap.get("exploredActivities") as? List<String> ?: emptyList()
            if (activityId !in current) {
                @Suppress("UNCHECKED_CAST")
                val favs = snap.get("favorites") as? List<String> ?: emptyList()
                tx.set(
                    docRef,
                    mapOf(
                        "exploredActivities" to current + activityId,
                        "favorites" to favs,
                        "updatedAt" to System.currentTimeMillis(),
                    ),
                    SetOptions.merge(),
                )
            }
        }.get()
        logger.info("markExplored user=$userId activity=$activityId")
        getState(userId)
    }

    suspend fun toggleFavorite(userId: String, activityId: String): GardenStateDto = withContext(Dispatchers.IO) {
        val docRef = db.collection(COLLECTION).document(userId)
        db.runTransaction { tx ->
            val snap = tx.get(docRef).get()
            @Suppress("UNCHECKED_CAST")
            val current = snap.get("favorites") as? List<String> ?: emptyList()
            val updated = if (activityId in current) current - activityId else current + activityId
            @Suppress("UNCHECKED_CAST")
            val explored = snap.get("exploredActivities") as? List<String> ?: emptyList()
            tx.set(
                docRef,
                mapOf(
                    "favorites" to updated,
                    "exploredActivities" to explored,
                    "updatedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            )
        }.get()
        logger.info("toggleFavorite user=$userId activity=$activityId")
        getState(userId)
    }
}
