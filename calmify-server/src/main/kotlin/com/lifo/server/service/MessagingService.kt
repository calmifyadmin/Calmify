package com.lifo.server.service

import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/**
 * MessagingService — direct messaging CRUD with strict ownership.
 *
 * Layout:
 *   conversations/{conversationId}                         metadata
 *   conversations/{conversationId}/messages/{messageId}
 *   conversations/{conversationId}/typing/{userId}
 *
 * Every read/write verifies caller is in participantIds — no IDOR.
 * Real-time fan-out happens in MessagingHub; this service is the source of truth.
 */
class MessagingService(private val db: Firestore) {
    private val log = LoggerFactory.getLogger(MessagingService::class.java)

    @Serializable
    data class ConversationDto(
        val id: String = "",
        val participantIds: List<String> = emptyList(),
        val lastMessage: String? = null,
        val lastMessageAt: Long = 0,
        val unreadCount: Int = 0,
    )

    @Serializable
    data class MessageDto(
        val id: String = "",
        val senderId: String = "",
        val text: String = "",
        val imageUrls: List<String> = emptyList(),
        val createdAt: Long = 0,
        val isRead: Boolean = false,
    )

    @Serializable
    data class ConversationsResponse(val data: List<ConversationDto> = emptyList())

    @Serializable
    data class MessagesResponse(val data: List<MessageDto> = emptyList())

    @Serializable
    data class TypingStatusResponse(val userIds: List<String> = emptyList())

    suspend fun listConversations(userId: String): List<ConversationDto> = withContext(Dispatchers.IO) {
        val snap = db.collection(CONVERSATIONS)
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .get()
        snap.documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            val participants = doc.get("participantIds") as? List<String> ?: return@mapNotNull null
            if (userId !in participants) return@mapNotNull null
            ConversationDto(
                id = doc.id,
                participantIds = participants,
                lastMessage = doc.getString("lastMessage"),
                lastMessageAt = doc.getLong("lastMessageAt") ?: 0,
                unreadCount = 0,
            )
        }
    }

    /** Returns existing 1:1 conversation if one with same sorted participants exists. */
    suspend fun createConversation(callerId: String, participantIds: List<String>): ConversationDto = withContext(Dispatchers.IO) {
        require(callerId in participantIds) { "caller must be a participant" }
        require(participantIds.size in 2..50) { "participants must be 2..50" }
        val sorted = participantIds.distinct().sorted()

        if (sorted.size == 2) {
            val existing = db.collection(CONVERSATIONS)
                .whereArrayContains("participantIds", sorted[0])
                .get()
                .get()
            val match = existing.documents.firstOrNull { doc ->
                @Suppress("UNCHECKED_CAST")
                val p = doc.get("participantIds") as? List<String> ?: emptyList()
                p.sorted() == sorted
            }
            if (match != null) {
                return@withContext ConversationDto(
                    id = match.id,
                    participantIds = sorted,
                    lastMessage = match.getString("lastMessage"),
                    lastMessageAt = match.getLong("lastMessageAt") ?: 0,
                )
            }
        }

        val now = System.currentTimeMillis()
        val ref = db.collection(CONVERSATIONS).document()
        ref.set(
            mapOf(
                "participantIds" to sorted,
                "lastMessage" to null,
                "lastMessageAt" to now,
                "createdAt" to now,
            )
        ).get()
        log.info("conversation.created id=${ref.id} participants=${sorted.size}")
        ConversationDto(id = ref.id, participantIds = sorted, lastMessage = null, lastMessageAt = now)
    }

    suspend fun listMessages(callerId: String, conversationId: String, limit: Int): List<MessageDto> = withContext(Dispatchers.IO) {
        requireParticipant(callerId, conversationId)
        val capped = limit.coerceIn(1, 200)
        val snap = db.collection(CONVERSATIONS).document(conversationId)
            .collection(MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(capped)
            .get()
            .get()
        snap.documents.map { doc ->
            @Suppress("UNCHECKED_CAST")
            MessageDto(
                id = doc.id,
                senderId = doc.getString("senderId") ?: "",
                text = doc.getString("text") ?: "",
                imageUrls = (doc.get("imageUrls") as? List<String>) ?: emptyList(),
                createdAt = doc.getLong("createdAt") ?: 0,
                isRead = doc.getBoolean("isRead") ?: false,
            )
        }
    }

    /** Returns the persisted message + participantIds for hub fan-out. */
    data class SendResult(val message: MessageDto, val participantIds: List<String>, val lastMessagePreview: String)

    suspend fun sendMessage(
        callerId: String,
        conversationId: String,
        text: String,
        imageUrls: List<String>,
    ): SendResult = withContext(Dispatchers.IO) {
        val participants = requireParticipant(callerId, conversationId)
        val convRef = db.collection(CONVERSATIONS).document(conversationId)
        val msgRef = convRef.collection(MESSAGES).document()
        val now = System.currentTimeMillis()
        val preview = when {
            text.isNotBlank() -> text.take(120)
            imageUrls.isNotEmpty() -> "[Photo]"
            else -> ""
        }
        val batch = db.batch()
        batch.set(
            msgRef,
            mapOf(
                "senderId" to callerId,
                "text" to text,
                "imageUrls" to imageUrls,
                "createdAt" to now,
                "isRead" to false,
            )
        )
        batch.update(
            convRef,
            mapOf(
                "lastMessage" to preview,
                "lastMessageAt" to now,
            )
        )
        batch.commit().get()

        // Clear sender's typing indicator (best-effort)
        runCatching {
            convRef.collection(TYPING).document(callerId).delete().get()
        }

        log.info("message.sent conv=$conversationId msg=${msgRef.id} sender=$callerId")
        SendResult(
            message = MessageDto(
                id = msgRef.id,
                senderId = callerId,
                text = text,
                imageUrls = imageUrls,
                createdAt = now,
                isRead = false,
            ),
            participantIds = participants,
            lastMessagePreview = preview,
        )
    }

    /** Marks unread messages NOT sent by caller as read. Returns count updated. */
    suspend fun markRead(callerId: String, conversationId: String): Int = withContext(Dispatchers.IO) {
        requireParticipant(callerId, conversationId)
        val unread = db.collection(CONVERSATIONS).document(conversationId)
            .collection(MESSAGES)
            .whereEqualTo("isRead", false)
            .get()
            .get()
        val targets = unread.documents.filter { it.getString("senderId") != callerId }
        if (targets.isEmpty()) return@withContext 0
        val batch = db.batch()
        targets.forEach { batch.update(it.reference, "isRead", true) }
        batch.commit().get()
        log.info("conv.read conv=$conversationId user=$callerId marked=${targets.size}")
        targets.size
    }

    /** Returns conversation participantIds for hub fan-out after typing change. */
    suspend fun setTyping(
        callerId: String,
        conversationId: String,
        isTyping: Boolean,
    ): List<String> = withContext(Dispatchers.IO) {
        val participants = requireParticipant(callerId, conversationId)
        val ref = db.collection(CONVERSATIONS).document(conversationId)
            .collection(TYPING).document(callerId)
        if (isTyping) {
            ref.set(
                mapOf(
                    "isTyping" to true,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).get()
        } else {
            runCatching { ref.delete().get() }
        }
        participants
    }

    suspend fun getTypingUserIds(callerId: String, conversationId: String): List<String> = withContext(Dispatchers.IO) {
        requireParticipant(callerId, conversationId)
        val snap = db.collection(CONVERSATIONS).document(conversationId)
            .collection(TYPING)
            .whereEqualTo("isTyping", true)
            .get()
            .get()
        snap.documents.map { it.id }
    }

    /** Returns participantIds or throws if caller not a member. */
    suspend fun requireParticipant(callerId: String, conversationId: String): List<String> = withContext(Dispatchers.IO) {
        val doc = db.collection(CONVERSATIONS).document(conversationId).get().get()
        if (!doc.exists()) error("conversation not found")
        @Suppress("UNCHECKED_CAST")
        val participants = doc.get("participantIds") as? List<String> ?: emptyList()
        require(callerId in participants) { "caller is not a participant" }
        participants
    }

    companion object {
        private const val CONVERSATIONS = "conversations"
        private const val MESSAGES = "messages"
        private const val TYPING = "typing"
    }
}
