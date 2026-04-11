package com.lifo.server.service

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.PaginationMeta
import com.lifo.server.model.PaginationParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class GenericWellnessService<T>(
    private val db: Firestore,
    private val collectionName: String,
    private val orderByField: String = "timestampMillis",
    private val mapper: (DocumentSnapshot) -> T,
    private val toFirestoreMap: (T, String) -> Map<String, Any?>,
    private val getId: (T) -> String,
) {
    private val logger = LoggerFactory.getLogger("WellnessService[$collectionName]")

    companion object {
        private const val OWNER_FIELD = "ownerId"
    }

    data class PagedResult<T>(val items: List<T>, val meta: PaginationMeta)

    suspend fun list(userId: String, params: PaginationParams): PagedResult<T> = withContext(Dispatchers.IO) {
        var query = db.collection(collectionName)
            .whereEqualTo(OWNER_FIELD, userId)
            .orderBy(orderByField, Query.Direction.DESCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = db.collection(collectionName).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { mapper(it) }

        PagedResult(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) getId(items.last()) else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun getById(userId: String, id: String): T? = withContext(Dispatchers.IO) {
        val doc = db.collection(collectionName).document(id).get().get()
        if (!doc.exists() || doc.getString(OWNER_FIELD) != userId) return@withContext null
        mapper(doc)
    }

    suspend fun create(userId: String, item: T): T = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val data = toFirestoreMap(item, userId).toMutableMap()
        data["updatedAt"] = now
        data["createdAt"] = now

        val id = getId(item)
        val docRef = if (id.isNotEmpty()) {
            db.collection(collectionName).document(id)
        } else {
            db.collection(collectionName).document()
        }
        docRef.set(data).get()
        logger.info("Created $collectionName ${docRef.id} for user $userId")

        // Return mapped item from the data we just wrote (no extra read)
        mapper(docRef.get().get())
    }

    suspend fun update(userId: String, id: String, item: T): T? = withContext(Dispatchers.IO) {
        val existing = db.collection(collectionName).document(id).get().get()
        // If exists but wrong owner → 403/null (IDOR protection)
        if (existing.exists() && existing.getString(OWNER_FIELD) != userId) return@withContext null

        val now = System.currentTimeMillis()
        val data = toFirestoreMap(item, userId).toMutableMap()
        data["updatedAt"] = now
        // Upsert: if doc doesn't exist, set createdAt too
        if (!existing.exists()) data["createdAt"] = now

        db.collection(collectionName).document(id).set(data).get()
        logger.info("${if (existing.exists()) "Updated" else "Upserted"} $collectionName $id for user $userId")

        mapper(db.collection(collectionName).document(id).get().get())
    }

    suspend fun delete(userId: String, id: String): Boolean = withContext(Dispatchers.IO) {
        val existing = db.collection(collectionName).document(id).get().get()
        if (!existing.exists() || existing.getString(OWNER_FIELD) != userId) return@withContext false

        db.collection(collectionName).document(id).delete().get()
        logger.info("Deleted $collectionName $id for user $userId")
        true
    }

    suspend fun getByDayKey(userId: String, dayKey: String): List<T> = withContext(Dispatchers.IO) {
        val snapshot = db.collection(collectionName)
            .whereEqualTo(OWNER_FIELD, userId)
            .whereEqualTo("dayKey", dayKey)
            .get().get()
        snapshot.documents.map { mapper(it) }
    }
}
