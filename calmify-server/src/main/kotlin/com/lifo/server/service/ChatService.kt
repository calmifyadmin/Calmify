package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.PaginationMeta
import com.lifo.shared.model.ChatMessageProto
import com.lifo.shared.model.ChatSessionProto
import com.lifo.shared.model.MessageStatusProto
import com.lifo.server.model.PaginationParams
import org.slf4j.LoggerFactory

class ChatService(private val db: Firestore?) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)
    private val sessionsCollection = "chatSessions"
    private val messagesCollection = "chatMessages"

    data class PagedSessions(val items: List<ChatSessionProto>, val meta: PaginationMeta)
    data class PagedMessages(val items: List<ChatMessageProto>, val meta: PaginationMeta)

    suspend fun getSessions(userId: String, params: PaginationParams): PagedSessions {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        var query = firestore.collection(sessionsCollection)
            .whereEqualTo("ownerId", userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = firestore.collection(sessionsCollection).document(params.cursor).get().get()
            if (cursorDoc.exists()) query = query.startAfter(cursorDoc)
        }

        val docs = query.get().get().documents
        val hasMore = docs.size > params.limit
        val items = docs.take(params.limit).map { doc ->
            ChatSessionProto(
                id = doc.id,
                ownerId = doc.getString("ownerId") ?: "",
                title = doc.getString("title") ?: "",
                createdAtMillis = doc.getLong("createdAt") ?: 0L,
                lastMessageAtMillis = doc.getLong("lastMessageAt") ?: 0L,
                aiModel = doc.getString("aiModel") ?: "gemini-2.0-flash",
                messageCount = doc.getLong("messageCount")?.toInt() ?: 0,
            )
        }

        return PagedSessions(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) items.last().id else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun getSessionById(userId: String, sessionId: String): ChatSessionProto? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val doc = firestore.collection(sessionsCollection).document(sessionId).get().get()
        if (!doc.exists() || doc.getString("ownerId") != userId) return null

        return ChatSessionProto(
            id = doc.id,
            ownerId = doc.getString("ownerId") ?: "",
            title = doc.getString("title") ?: "",
            createdAtMillis = doc.getLong("createdAt") ?: 0L,
            lastMessageAtMillis = doc.getLong("lastMessageAt") ?: 0L,
            aiModel = doc.getString("aiModel") ?: "gemini-2.0-flash",
            messageCount = doc.getLong("messageCount")?.toInt() ?: 0,
        )
    }

    suspend fun createSession(userId: String, session: ChatSessionProto): ChatSessionProto {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val now = System.currentTimeMillis()

        val data = mapOf(
            "ownerId" to userId,
            "title" to session.title,
            "createdAt" to now,
            "lastMessageAt" to now,
            "aiModel" to session.aiModel,
            "messageCount" to 0,
        )

        val docRef = firestore.collection(sessionsCollection).document()
        docRef.set(data).get()
        logger.info("Created chat session ${docRef.id} for user $userId")
        return session.copy(id = docRef.id, ownerId = userId, createdAtMillis = now, lastMessageAtMillis = now)
    }

    suspend fun deleteSession(userId: String, sessionId: String): Boolean {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val doc = firestore.collection(sessionsCollection).document(sessionId).get().get()
        if (!doc.exists() || doc.getString("ownerId") != userId) return false

        // Delete all messages in the session
        val messages = firestore.collection(messagesCollection)
            .whereEqualTo("sessionId", sessionId)
            .get().get()
        val batch = firestore.batch()
        messages.documents.forEach { batch.delete(it.reference) }
        batch.delete(firestore.collection(sessionsCollection).document(sessionId))
        batch.commit().get()

        logger.info("Deleted chat session $sessionId and its messages for user $userId")
        return true
    }

    suspend fun getMessages(userId: String, sessionId: String, params: PaginationParams): PagedMessages {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        // Verify session ownership
        val session = firestore.collection(sessionsCollection).document(sessionId).get().get()
        if (!session.exists() || session.getString("ownerId") != userId) {
            return PagedMessages(emptyList(), PaginationMeta())
        }

        var query = firestore.collection(messagesCollection)
            .whereEqualTo("sessionId", sessionId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(params.limit + 1)

        if (params.cursor != null) {
            val cursorDoc = firestore.collection(messagesCollection).document(params.cursor).get().get()
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

        return PagedMessages(
            items = items,
            meta = PaginationMeta(
                cursor = if (hasMore && items.isNotEmpty()) items.last().id else "",
                hasMore = hasMore,
            ),
        )
    }

    suspend fun deleteAllSessions(userId: String): Int {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val sessions = firestore.collection(sessionsCollection)
            .whereEqualTo("ownerId", userId)
            .get().get().documents

        if (sessions.isEmpty()) return 0

        val batch = firestore.batch()
        for (session in sessions) {
            // Delete messages for this session
            val messages = firestore.collection(messagesCollection)
                .whereEqualTo("sessionId", session.id)
                .get().get()
            messages.documents.forEach { batch.delete(it.reference) }
            batch.delete(session.reference)
        }
        batch.commit().get()
        logger.info("Deleted ${sessions.size} sessions and their messages for user $userId")
        return sessions.size
    }

    suspend fun exportSessionToDiary(userId: String, sessionId: String): String {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        // Get session
        val session = getSessionById(userId, sessionId)
            ?: throw IllegalArgumentException("Session not found")

        // Get all messages
        val messages = firestore.collection(messagesCollection)
            .whereEqualTo("sessionId", sessionId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get().get()

        val transcript = messages.documents.joinToString("\n\n") { doc ->
            val role = if (doc.getBoolean("isUser") == true) "Tu" else "AI"
            "$role: ${doc.getString("content") ?: ""}"
        }

        // Create diary entry
        val now = System.currentTimeMillis()
        val diaryData = hashMapOf<String, Any>(
            "ownerId" to userId,
            "mood" to "Neutral",
            "title" to "Chat: ${session.title}",
            "description" to transcript,
            "images" to emptyList<String>(),
            "dateMillis" to now,
            "dayKey" to "",
            "timezone" to "",
            "emotionIntensity" to 5,
            "stressLevel" to 5,
            "energyLevel" to 5,
            "calmAnxietyLevel" to 5,
            "primaryTrigger" to "NONE",
            "dominantBodySensation" to "NONE",
        )

        val diaryRef = firestore.collection("diary").document()
        diaryRef.set(diaryData).get()
        logger.info("Exported session $sessionId to diary ${diaryRef.id}")
        return diaryRef.id
    }

    suspend fun retryMessage(userId: String, messageId: String): ChatMessageProto? {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")
        val doc = firestore.collection(messagesCollection).document(messageId).get().get()
        if (!doc.exists()) return null

        val sessionId = doc.getString("sessionId") ?: return null
        // Verify session ownership
        val session = firestore.collection(sessionsCollection).document(sessionId).get().get()
        if (!session.exists() || session.getString("ownerId") != userId) return null

        // Reset message status to pending for re-processing
        firestore.collection(messagesCollection).document(messageId).update(
            mapOf("status" to MessageStatusProto.SENDING.name),
        ).get()

        return ChatMessageProto(
            id = doc.id,
            sessionId = sessionId,
            content = doc.getString("content") ?: "",
            isUser = doc.getBoolean("isUser") ?: false,
            timestampMillis = doc.getLong("timestamp") ?: 0L,
            status = MessageStatusProto.SENDING,
        )
    }

    suspend fun sendMessage(userId: String, sessionId: String, message: ChatMessageProto): ChatMessageProto {
        val firestore = db ?: throw IllegalStateException("Firestore not initialized")

        // Verify session ownership
        val session = firestore.collection(sessionsCollection).document(sessionId).get().get()
        if (!session.exists() || session.getString("ownerId") != userId) {
            throw IllegalArgumentException("Session not found or unauthorized")
        }

        val now = System.currentTimeMillis()
        val data = mapOf(
            "sessionId" to sessionId,
            "content" to message.content,
            "isUser" to message.isUser,
            "timestamp" to now,
            "status" to MessageStatusProto.SENT.name,
        )

        val docRef = firestore.collection(messagesCollection).document()
        docRef.set(data).get()

        // Update session lastMessageAt and messageCount
        firestore.collection(sessionsCollection).document(sessionId).update(
            mapOf(
                "lastMessageAt" to now,
                "messageCount" to com.google.cloud.firestore.FieldValue.increment(1),
            ),
        ).get()

        return message.copy(id = docRef.id, timestampMillis = now, status = MessageStatusProto.SENT)
    }
}
