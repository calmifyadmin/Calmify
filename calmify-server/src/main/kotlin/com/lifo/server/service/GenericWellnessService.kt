package com.lifo.server.service

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.PaginationMeta
import com.lifo.server.model.PaginationParams
import org.slf4j.LoggerFactory

class GenericWellnessService<T>(
    private val db: Firestore?,
    private val collectionName: String,
    private val mapper: (DocumentSnapshot) -> T,
    private val toFirestoreMap: (T, String) -> Map<String, Any?>,
    private val getId: (T) -> String,
) {
    private val logger = LoggerFactory.getLogger("WellnessService[$collectionName]")

    data class PagedResult<T>(val items: List<T>, val meta: PaginationMeta)

    suspend fun list(userId: String, params: PaginationParams): PagedResult<T> {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        var query = firestore.collection(collectionName)
            .whereEqualTo("ownerId", userId)
            .orderBy("timestampMillis", Query.Direction.DESCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = firestore.collection(collectionName).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { mapper(it) }

        return PagedResult(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) getId(items.last()) else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun getById(userId: String, id: String): T? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val doc = firestore.collection(collectionName).document(id).get().get()
        if (!doc.exists() || doc.getString("ownerId") != userId) return null
        return mapper(doc)
    }

    suspend fun create(userId: String, item: T): T {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val data = toFirestoreMap(item, userId)
        val id = getId(item)
        val docRef = if (id.isNotEmpty()) {
            firestore.collection(collectionName).document(id)
        } else {
            firestore.collection(collectionName).document()
        }
        docRef.set(data).get()
        logger.info("Created $collectionName ${docRef.id} for user $userId")
        return mapper(docRef.get().get())
    }

    suspend fun update(userId: String, id: String, item: T): T? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val existing = firestore.collection(collectionName).document(id).get().get()
        if (!existing.exists() || existing.getString("ownerId") != userId) return null

        val data = toFirestoreMap(item, userId)
        firestore.collection(collectionName).document(id).set(data).get()
        logger.info("Updated $collectionName $id for user $userId")
        return mapper(firestore.collection(collectionName).document(id).get().get())
    }

    suspend fun delete(userId: String, id: String): Boolean {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val existing = firestore.collection(collectionName).document(id).get().get()
        if (!existing.exists() || existing.getString("ownerId") != userId) return false

        firestore.collection(collectionName).document(id).delete().get()
        logger.info("Deleted $collectionName $id for user $userId")
        return true
    }

    suspend fun getByDayKey(userId: String, dayKey: String): List<T> {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val snapshot = firestore.collection(collectionName)
            .whereEqualTo("ownerId", userId)
            .whereEqualTo("dayKey", dayKey)
            .get().get()
        return snapshot.documents.map { mapper(it) }
    }
}
