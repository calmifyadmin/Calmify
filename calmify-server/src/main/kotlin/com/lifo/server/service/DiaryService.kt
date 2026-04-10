package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.ApiError
import com.lifo.shared.model.DiaryProto
import com.lifo.server.model.PaginationParams
import com.lifo.shared.api.PaginationMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class DiaryService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(DiaryService::class.java)

    companion object {
        const val COLLECTION = "diaries"
        private const val OWNER_FIELD = "ownerId"
        private const val DATE_FIELD = "date"
        private const val BATCH_LIMIT = 500
    }

    data class PagedResult(
        val items: List<DiaryProto>,
        val meta: PaginationMeta,
    )

    suspend fun getDiaries(userId: String, params: PaginationParams): PagedResult = withContext(Dispatchers.IO) {
        var query = db.collection(COLLECTION)
            .whereEqualTo(OWNER_FIELD, userId)
            .orderBy(DATE_FIELD, if (params.direction == "asc") Query.Direction.ASCENDING else Query.Direction.DESCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = db.collection(COLLECTION).document(params.cursor).get().get()
            if (cursorDoc.exists()) {
                query = query.startAfter(cursorDoc)
            }
        }

        val snapshot = query.get().get()
        val docs = snapshot.documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { doc -> docToDiary(doc) }

        val nextCursor = if (hasMore && items.isNotEmpty()) items.last().id else null

        PagedResult(
            items = items,
            meta = PaginationMeta(
                cursor = nextCursor ?: "",
                hasMore = hasMore,
                totalCount = 0,
            ),
        )
    }

    suspend fun getDiaryById(userId: String, diaryId: String): DiaryProto? = withContext(Dispatchers.IO) {
        val doc = db.collection(COLLECTION).document(diaryId).get().get()
        if (!doc.exists()) return@withContext null
        if (doc.getString(OWNER_FIELD) != userId) return@withContext null
        docToDiary(doc)
    }

    suspend fun createDiary(userId: String, diary: DiaryProto): DiaryProto = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        val data = diaryToFirestoreMap(diary, userId).toMutableMap()
        data["updatedAt"] = now
        data["createdAt"] = now

        val docRef = if (diary.id.isNotEmpty()) {
            db.collection(COLLECTION).document(diary.id)
        } else {
            db.collection(COLLECTION).document()
        }

        docRef.set(data).get()
        logger.info("Created diary ${docRef.id} for user $userId")

        diary.copy(id = docRef.id, ownerId = userId)
    }

    suspend fun updateDiary(userId: String, diaryId: String, diary: DiaryProto): DiaryProto? = withContext(Dispatchers.IO) {
        val docRef = db.collection(COLLECTION).document(diaryId)
        val existing = docRef.get().get()

        if (!existing.exists()) return@withContext null
        if (existing.getString(OWNER_FIELD) != userId) return@withContext null

        val now = System.currentTimeMillis()
        val updates = diaryToFirestoreMap(diary, userId).toMutableMap()
        updates["updatedAt"] = now

        docRef.update(updates).get()
        logger.info("Updated diary $diaryId for user $userId")

        diary.copy(id = diaryId, ownerId = userId)
    }

    suspend fun deleteDiary(userId: String, diaryId: String): Boolean = withContext(Dispatchers.IO) {
        val docRef = db.collection(COLLECTION).document(diaryId)
        val existing = docRef.get().get()

        if (!existing.exists()) return@withContext false
        if (existing.getString(OWNER_FIELD) != userId) return@withContext false

        docRef.delete().get()
        logger.info("Deleted diary $diaryId for user $userId")
        true
    }

    suspend fun deleteAllDiaries(userId: String): Int = withContext(Dispatchers.IO) {
        val docs = db.collection(COLLECTION)
            .whereEqualTo(OWNER_FIELD, userId)
            .get().get().documents

        if (docs.isEmpty()) return@withContext 0

        // Chunk into batches of 500 (Firestore batch limit)
        var deleted = 0
        for (chunk in docs.chunked(BATCH_LIMIT)) {
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().get()
            deleted += chunk.size
        }
        logger.info("Deleted $deleted diaries for user $userId")
        deleted
    }

    suspend fun getDiariesByDateRange(
        userId: String,
        startMillis: Long,
        endMillis: Long,
    ): List<DiaryProto> = withContext(Dispatchers.IO) {
        val snapshot = db.collection(COLLECTION)
            .whereEqualTo(OWNER_FIELD, userId)
            .whereGreaterThanOrEqualTo(DATE_FIELD, com.google.cloud.Timestamp.ofTimeMicroseconds(startMillis * 1000))
            .whereLessThanOrEqualTo(DATE_FIELD, com.google.cloud.Timestamp.ofTimeMicroseconds(endMillis * 1000))
            .orderBy(DATE_FIELD, Query.Direction.DESCENDING)
            .get()
            .get()

        snapshot.documents.map { doc -> docToDiary(doc) }
    }

    // ─── Mapping helpers ─────────────────────────────────────────────

    private fun docToDiary(doc: com.google.cloud.firestore.DocumentSnapshot): DiaryProto {
        // Handle date field: could be Firestore Timestamp or Long millis
        val dateMillis = when (val dateVal = doc.get(DATE_FIELD)) {
            is com.google.cloud.Timestamp -> dateVal.toDate().time
            is Long -> dateVal
            is Number -> dateVal.toLong()
            else -> doc.getLong("dateMillis") ?: 0L
        }

        return DiaryProto(
            id = doc.id,
            ownerId = doc.getString(OWNER_FIELD) ?: "",
            mood = doc.getString("mood") ?: "Neutral",
            title = doc.getString("title") ?: "",
            description = doc.getString("description") ?: "",
            images = (doc.get("images") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            dateMillis = dateMillis,
            dayKey = doc.getString("dayKey") ?: "",
            timezone = doc.getString("timezone") ?: "",
            emotionIntensity = doc.getLong("emotionIntensity")?.toInt() ?: 5,
            stressLevel = doc.getLong("stressLevel")?.toInt() ?: 5,
            energyLevel = doc.getLong("energyLevel")?.toInt() ?: 5,
            calmAnxietyLevel = doc.getLong("calmAnxietyLevel")?.toInt() ?: 5,
            primaryTrigger = doc.getString("primaryTrigger") ?: "NONE",
            dominantBodySensation = doc.getString("dominantBodySensation") ?: "NONE",
        )
    }

    private fun diaryToFirestoreMap(diary: DiaryProto, userId: String): Map<String, Any> = mapOf(
        OWNER_FIELD to userId,
        "mood" to diary.mood,
        "title" to diary.title,
        "description" to diary.description,
        "images" to diary.images,
        DATE_FIELD to com.google.cloud.Timestamp.ofTimeMicroseconds(diary.dateMillis * 1000),
        "dateMillis" to diary.dateMillis,
        "dayKey" to diary.dayKey,
        "timezone" to diary.timezone,
        "emotionIntensity" to diary.emotionIntensity,
        "stressLevel" to diary.stressLevel,
        "energyLevel" to diary.energyLevel,
        "calmAnxietyLevel" to diary.calmAnxietyLevel,
        "primaryTrigger" to diary.primaryTrigger,
        "dominantBodySensation" to diary.dominantBodySensation,
    )
}
