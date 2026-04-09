package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.PaginationMeta
import com.lifo.shared.model.CognitivePatternProto
import com.lifo.shared.model.DiaryInsightProto
import com.lifo.server.model.PaginationParams
import org.slf4j.LoggerFactory

class InsightService(private val db: Firestore?) {
    private val logger = LoggerFactory.getLogger(InsightService::class.java)
    private val collection = "diaryInsights"

    data class PagedInsights(val items: List<DiaryInsightProto>, val meta: PaginationMeta)

    suspend fun getInsightByDiaryId(userId: String, diaryId: String): DiaryInsightProto? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        val snapshot = firestore.collection(collection)
            .whereEqualTo("ownerId", userId)
            .whereEqualTo("diaryId", diaryId)
            .limit(1)
            .get().get()

        val doc = snapshot.documents.firstOrNull() ?: return null
        return docToInsight(doc)
    }

    suspend fun getInsights(userId: String, params: PaginationParams): PagedInsights {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        var query = firestore.collection(collection)
            .whereEqualTo("ownerId", userId)
            .orderBy("generatedAtMillis", Query.Direction.DESCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = firestore.collection(collection).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { docToInsight(it) }

        return PagedInsights(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) items.last().id else "",
                hasMore = hasMore,
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun docToInsight(doc: com.google.cloud.firestore.DocumentSnapshot): DiaryInsightProto {
        val patterns = (doc.get("cognitivePatterns") as? List<Map<String, Any>>)?.map { p ->
            CognitivePatternProto(
                patternType = p["patternType"] as? String ?: "",
                patternName = p["patternName"] as? String ?: "",
                description = p["description"] as? String ?: "",
                evidence = p["evidence"] as? String ?: "",
                confidence = (p["confidence"] as? Number)?.toFloat() ?: 0f,
            )
        } ?: emptyList()

        return DiaryInsightProto(
            id = doc.id,
            diaryId = doc.getString("diaryId") ?: "",
            ownerId = doc.getString("ownerId") ?: "",
            generatedAtMillis = doc.getLong("generatedAtMillis") ?: 0L,
            dayKey = doc.getString("dayKey") ?: "",
            sourceTimezone = doc.getString("sourceTimezone") ?: "",
            sentimentPolarity = doc.getDouble("sentimentPolarity")?.toFloat() ?: 0f,
            sentimentMagnitude = doc.getDouble("sentimentMagnitude")?.toFloat() ?: 0f,
            topics = (doc.get("topics") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            keyPhrases = (doc.get("keyPhrases") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            cognitivePatterns = patterns,
            summary = doc.getString("summary") ?: "",
            suggestedPrompts = (doc.get("suggestedPrompts") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            confidence = doc.getDouble("confidence")?.toFloat() ?: 0f,
            modelUsed = doc.getString("modelUsed") ?: "gemini-2.0-flash-exp",
        )
    }
}
