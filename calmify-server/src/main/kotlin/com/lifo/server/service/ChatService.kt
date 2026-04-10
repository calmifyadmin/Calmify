package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.PaginationMeta
import com.lifo.shared.model.ChatMessageProto
import com.lifo.shared.model.ChatSessionProto
import com.lifo.shared.model.MessageStatusProto
import com.lifo.server.model.PaginationParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class ChatService(private val db: Firestore) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    companion object {
        const val SESSIONS_COLLECTION = "chat_sessions"
        const val MESSAGES_COLLECTION = "chat_messages"
        private const val OWNER_FIELD = "ownerId"
        private const val BATCH_LIMIT = 500
    }

    data class PagedSessions(val items: List<ChatSessionProto>, val meta: PaginationMeta)
    data class PagedMessages(val items: List<ChatMessageProto>, val meta: PaginationMeta)

    suspend fun getSessions(userId: String, params: PaginationParams): PagedSessions = withContext(Dispatchers.IO) {
        var query = db.collection(SESSIONS_COLLECTION)
            .whereEqualTo(OWNER_FIELD, userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = db.collection(SESSIONS_COLLECTION).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { doc ->
            ChatSessionProto(
                id = doc.id,
                ownerId = doc.getString(OWNER_FIELD) ?: "",
                title = doc.getString("title") ?: "",
                createdAtMillis = doc.getLong("createdAt") ?: 0L,
                lastMessageAtMillis = doc.getLong("lastMessageAt") ?: 0L,
                aiModel = doc.getString("aiModel") ?: "gemini-2.0-flash",
                messageCount = doc.getLong("messageCount")?.toInt() ?: 0,
            )
        }

        PagedSessions(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) items.last().id else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun getSessionById(userId: String, sessionId: String): ChatSessionProto? = withContext(Dispatchers.IO) {
        val doc = db.collection(SESSIONS_COLLECTION).document(sessionId).get().get()
        if (!doc.exists() || doc.getString(OWNER_FIELD) != userId) return@withContext null

        ChatSessionProto(
            id = doc.id,
            ownerId = doc.getString(OWNER_FIELD) ?: "",
            title = doc.getString("title") ?: "",
            createdAtMillis = doc.getLong("createdAt") ?: 0L,
            lastMessageAtMillis = doc.getLong("lastMessageAt") ?: 0L,
            aiModel = doc.getString("aiModel") ?: "gemini-2.0-flash",
            messageCount = doc.getLong("messageCount")?.toInt() ?: 0,
        )
    }

    suspend fun createSession(userId: String, session: ChatSessionProto): ChatSessionProto = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        val data = mapOf(
            OWNER_FIELD to userId,
            "title" to session.title,
            "createdAt" to now,
            "lastMessageAt" to now,
            "aiModel" to session.aiModel,
            "messageCount" to 0,
            "updatedAt" to now,
        )

        val docRef = db.collection(SESSIONS_COLLECTION).document()
        docRef.set(data).get()
        logger.info("Created chat session ${docRef.id} for user $userId")
        session.copy(id = docRef.id, ownerId = userId, createdAtMillis = now, lastMessageAtMillis = now)
    }

    suspend fun deleteSession(userId: String, sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val doc = db.collection(SESSIONS_COLLECTION).document(sessionId).get().get()
        if (!doc.exists() || doc.getString(OWNER_FIELD) != userId) return@withContext false

        // Delete all messages in the session, chunked by 500
        val messages = db.collection(MESSAGES_COLLECTION)
            .whereEqualTo("sessionId", sessionId)
            .get().get().documents

        for (chunk in messages.chunked(BATCH_LIMIT)) {
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().get()
        }

        // Delete the session itself
        db.collection(SESSIONS_COLLECTION).document(sessionId).delete().get()

        logger.info("Deleted chat session $sessionId and ${messages.size} messages for user $userId")
        true
    }

    suspend fun getMessages(userId: String, sessionId: String, params: PaginationParams): PagedMessages = withContext(Dispatchers.IO) {
        // Verify session ownership
        val session = db.collection(SESSIONS_COLLECTION).document(sessionId).get().get()
        if (!session.exists() || session.getString(OWNER_FIELD) != userId) {
            return@withContext PagedMessages(emptyList(), PaginationMeta())
        }

        var query = db.collection(MESSAGES_COLLECTION)
            .whereEqualTo("sessionId", sessionId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = db.collection(MESSAGES_COLLECTION).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { doc ->
            ChatMessageProto(
                id = doc.id,
                sessionId = doc.getString("sessionId") ?: "",
                content = doc.getString("content") ?: "",
                isUser = doc.getBoolean("isUser") ?: false,
                timestampMillis = doc.getLong("timestamp") ?: 0L,
                status = try {
                    MessageStatusProto.valueOf(doc.getString("status") ?: "SENT")
                } catch (_: Exception) {
                    MessageStatusProto.SENT
                },
                error = doc.getString("error") ?: "",
            )
        }

        PagedMessages(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) items.last().id else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun deleteAllSessions(userId: String): Int = withContext(Dispatchers.IO) {
        val sessions = db.collection(SESSIONS_COLLECTION)
            .whereEqualTo(OWNER_FIELD, userId)
            .get().get().documents

        if (sessions.isEmpty()) return@withContext 0

        // Delete messages for all sessions, chunked by 500
        for (session in sessions) {
            val messages = db.collection(MESSAGES_COLLECTION)
                .whereEqualTo("sessionId", session.id)
                .get().get().documents

            for (chunk in messages.chunked(BATCH_LIMIT)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().get()
            }
        }

        // Delete sessions themselves, chunked by 500
        for (chunk in sessions.chunked(BATCH_LIMIT)) {
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().get()
        }

        logger.info("Deleted ${sessions.size} sessions and their messages for user $userId")
        sessions.size
    }

    suspend fun exportSessionToDiary(userId: String, sessionId: String): String = withContext(Dispatchers.IO) {
        // Get session (ownership verified inside)
        val session = getSessionById(userId, sessionId)
            ?: throw IllegalArgumentException("Session not found")

        // Get all messages
        val messages = db.collection(MESSAGES_COLLECTION)
            .whereEqualTo("sessionId", sessionId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get().get()

        val transcript = messages.documents.joinToString("\n\n") { doc ->
            val role = if (doc.getBoolean("isUser") == true) "Tu" else "AI"
            "$role: ${doc.getString("content") ?: ""}"
        }

        // Create diary entry — collection is "diaries" (NOT "diary")
        val now = System.currentTimeMillis()
        val diaryData = hashMapOf<String, Any>(
            "ownerId" to userId,
            "mood" to "Neutral",
            "title" to "Chat: ${session.title}",
            "description" to transcript,
            "images" to emptyList<String>(),
            "date" to com.google.cloud.Timestamp.ofTimeMicroseconds(now * 1000),
            "dateMillis" to now,
            "dayKey" to "",
            "timezone" to "",
            "emotionIntensity" to 5,
            "stressLevel" to 5,
            "energyLevel" to 5,
            "calmAnxietyLevel" to 5,
            "primaryTrigger" to "NONE",
            "dominantBodySensation" to "NONE",
            "createdAt" to now,
            "updatedAt" to now,
        )

        val diaryRef = db.collection(DiaryService.COLLECTION).document()
        diaryRef.set(diaryData).get()
        logger.info("Exported session $sessionId to diary ${diaryRef.id}")
        diaryRef.id
    }

    suspend fun retryMessage(userId: String, messageId: String): ChatMessageProto? = withContext(Dispatchers.IO) {
        val doc = db.collection(MESSAGES_COLLECTION).document(messageId).get().get()
        if (!doc.exists()) return@withContext null

        val sessionId = doc.getString("sessionId") ?: return@withContext null
        // Verify session ownership
        val session = db.collection(SESSIONS_COLLECTION).document(sessionId).get().get()
        if (!session.exists() || session.getString(OWNER_FIELD) != userId) return@withContext null

        // Reset message status to pending for re-processing
        db.collection(MESSAGES_COLLECTION).document(messageId).update(
            mapOf("status" to MessageStatusProto.SENDING.name),
        ).get()

        ChatMessageProto(
            id = doc.id,
            sessionId = sessionId,
            content = doc.getString("content") ?: "",
            isUser = doc.getBoolean("isUser") ?: false,
            timestampMillis = doc.getLong("timestamp") ?: 0L,
            status = MessageStatusProto.SENDING,
        )
    }

    suspend fun sendMessage(userId: String, sessionId: String, message: ChatMessageProto): ChatMessageProto = withContext(Dispatchers.IO) {
        // Verify session ownership
        val session = db.collection(SESSIONS_COLLECTION).document(sessionId).get().get()
        if (!session.exists() || session.getString(OWNER_FIELD) != userId) {
            throw IllegalArgumentException("Session not found or unauthorized")
        }

        val now = System.currentTimeMillis()
        val data = mapOf(
            "sessionId" to sessionId,
            "content" to message.content,
            "isUser" to message.isUser,
            "timestamp" to now,
            "status" to MessageStatusProto.SENT.name,
            "updatedAt" to now,
        )

        val docRef = db.collection(MESSAGES_COLLECTION).document()
        docRef.set(data).get()

        // Update session lastMessageAt and messageCount
        db.collection(SESSIONS_COLLECTION).document(sessionId).update(
            mapOf(
                "lastMessageAt" to now,
                "messageCount" to com.google.cloud.firestore.FieldValue.increment(1),
                "updatedAt" to now,
            ),
        ).get()

        message.copy(id = docRef.id, timestampMillis = now, status = MessageStatusProto.SENT)
    }
}
