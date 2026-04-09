package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.ApiError
import com.lifo.shared.model.DiaryProto
import com.lifo.server.model.PaginationParams
import com.lifo.shared.api.PaginationMeta
import org.slf4j.LoggerFactory

class DiaryService(private val db: Firestore?) {
    private val logger = LoggerFactory.getLogger(DiaryService::class.java)
    private val collection = "diary"

    data class PagedResult(
        val items: List<DiaryProto>,
        val meta: PaginationMeta,
    )

    suspend fun getDiaries(userId: String, params: PaginationParams): PagedResult {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        var query = firestore.collection(collection)
            .whereEqualTo("ownerId", userId)
            .orderBy("dateMillis", if (params.direction == "asc") Query.Direction.ASCENDING else Query.Direction.DESCENDING)
            .limit(params.limit + 1) // +1 to check hasMore

        if (params.cursor != null) {
            val cursorDoc = firestore.collection(collection).document(params.cursor).get().get()
            if (cursorDoc.exists()) {
                query = query.startAfter(cursorDoc)
            }
        }

        val snapshot = query.get().get()
        val docs = snapshot.documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { doc ->
            DiaryProto(
                id = doc.id,
                ownerId = doc.getString("ownerId") ?: "",
                mood = doc.getString("mood") ?: "Neutral",
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                images = (doc.get("images") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                dateMillis = doc.getLong("dateMillis") ?: 0L,
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

        val nextCursor = if (hasMore && items.isNotEmpty()) items.last().id else null

        return PagedResult(
            items = items,
            meta = PaginationMeta(
                cursor = nextCursor ?: "",
                hasMore = hasMore,
                totalCount = 0, // Firestore doesn't do efficient count
            ),
        )
    }

    suspend fun getDiaryById(userId: String, diaryId: String): DiaryProto? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val doc = firestore.collection(collection).document(diaryId).get().get()

        if (!doc.exists()) return null
        if (doc.getString("ownerId") != userId) return null // Authorization check

        return DiaryProto(
            id = doc.id,
            ownerId = doc.getString("ownerId") ?: "",
            mood = doc.getString("mood") ?: "Neutral",
            title = doc.getString("title") ?: "",
            description = doc.getString("description") ?: "",
            images = (doc.get("images") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            dateMillis = doc.getLong("dateMillis") ?: 0L,
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

    suspend fun createDiary(userId: String, diary: DiaryProto): DiaryProto {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        val data = mapOf(
            "ownerId" to userId,
            "mood" to diary.mood,
            "title" to diary.title,
            "description" to diary.description,
            "images" to diary.images,
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

        val docRef = if (diary.id.isNotEmpty()) {
            firestore.collection(collection).document(diary.id)
        } else {
            firestore.collection(collection).document()
        }

        docRef.set(data).get()
        logger.info("Created diary ${docRef.id} for user $userId")

        return diary.copy(id = docRef.id, ownerId = userId)
    }

    suspend fun updateDiary(userId: String, diaryId: String, diary: DiaryProto): DiaryProto? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val docRef = firestore.collection(collection).document(diaryId)
        val existing = docRef.get().get()

        if (!existing.exists()) return null
        if (existing.getString("ownerId") != userId) return null

        val updates = mapOf(
            "mood" to diary.mood,
            "title" to diary.title,
            "description" to diary.description,
            "images" to diary.images,
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

        docRef.update(updates).get()
        logger.info("Updated diary $diaryId for user $userId")

        return diary.copy(id = diaryId, ownerId = userId)
    }

    suspend fun deleteDiary(userId: String, diaryId: String): Boolean {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val docRef = firestore.collection(collection).document(diaryId)
        val existing = docRef.get().get()

        if (!existing.exists()) return false
        if (existing.getString("ownerId") != userId) return false

        docRef.delete().get()
        logger.info("Deleted diary $diaryId for user $userId")
        return true
    }

    suspend fun getDiariesByDateRange(
        userId: String,
        startMillis: Long,
        endMillis: Long,
    ): List<DiaryProto> {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        val snapshot = firestore.collection(collection)
            .whereEqualTo("ownerId", userId)
            .whereGreaterThanOrEqualTo("dateMillis", startMillis)
            .whereLessThanOrEqualTo("dateMillis", endMillis)
            .orderBy("dateMillis", Query.Direction.DESCENDING)
            .get()
            .get()

        return snapshot.documents.map { doc ->
            DiaryProto(
                id = doc.id,
                ownerId = doc.getString("ownerId") ?: "",
                mood = doc.getString("mood") ?: "Neutral",
                title = doc.getString("title") ?: "",
                description = doc.getString("description") ?: "",
                images = (doc.get("images") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                dateMillis = doc.getLong("dateMillis") ?: 0L,
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
    }
}
