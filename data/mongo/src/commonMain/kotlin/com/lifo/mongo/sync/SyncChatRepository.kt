package com.lifo.mongo.sync

import com.lifo.mongo.database.CalmifyDatabase
import com.lifo.util.currentTimeMillis
import com.lifo.util.model.*
import com.lifo.util.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sync-aware chat repository.
 *
 * READ: Always from SQLDelight (instant, offline).
 * WRITE: SQLDelight first + enqueue for server sync.
 * AI operations delegate to network (require connectivity).
 */
class SyncChatRepository(
    private val database: CalmifyDatabase,
    private val syncEngine: SyncEngine,
    private val userId: () -> String?,
) : ChatRepository {

    private val sessionQueries get() = database.chatSessionQueries
    private val messageQueries get() = database.chatMessageQueries
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    // ─── Sessions ─────────────────────────────────────────────────────

    override fun getAllSessions(): Flow<RequestState<List<ChatSession>>> = flow {
        val uid = userId() ?: run { emit(RequestState.Error(Exception("Not authenticated"))); return@flow }
        val sessions = sessionQueries.getAllSessions(uid).executeAsList().map { it.toDomain() }
        emit(RequestState.Success(sessions))
    }

    override suspend fun getSession(sessionId: String): RequestState<ChatSession> {
        val uid = userId() ?: return RequestState.Error(Exception("Not authenticated"))
        val entity = sessionQueries.getSession(sessionId, uid).executeAsOneOrNull()
        return if (entity != null) {
            RequestState.Success(entity.toDomain())
        } else {
            RequestState.Error(Exception("Session not found"))
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createSession(title: String?): RequestState<ChatSession> = withContext(Dispatchers.Default) {
        val uid = userId() ?: return@withContext RequestState.Error(Exception("Not authenticated"))
        val now = Clock.System.now()
        val session = ChatSession(
            id = Uuid.random().toString(),
            title = title ?: "New Chat",
            createdAt = now,
            lastMessageAt = now,
            aiModel = "gemini-2.0-flash",
            messageCount = 0,
            ownerId = uid,
        )

        sessionQueries.insertSession(
            id = session.id,
            title = session.title,
            createdAt = session.createdAt.toEpochMilliseconds(),
            lastMessageAt = session.lastMessageAt.toEpochMilliseconds(),
            aiModel = session.aiModel,
            messageCount = session.messageCount.toLong(),
            ownerId = session.ownerId,
            summary = null,
            lastMessage = null,
            mood = null,
            isLiveMode = 0L,
            is_dirty = 1L,
            updated_at = now.toEpochMilliseconds(),
        )

        syncEngine.enqueue(
            entityType = SyncEntityType.CHAT_SESSION.name,
            entityId = session.id,
            operation = "CREATE",
            payload = serializeSession(session),
        )

        RequestState.Success(session)
    }

    override suspend fun updateSession(session: ChatSession): RequestState<Unit> = withContext(Dispatchers.Default) {
        val now = currentTimeMillis()
        sessionQueries.updateSession(
            title = session.title,
            createdAt = session.createdAt.toEpochMilliseconds(),
            lastMessageAt = session.lastMessageAt.toEpochMilliseconds(),
            aiModel = session.aiModel,
            messageCount = session.messageCount.toLong(),
            ownerId = session.ownerId,
            summary = null,
            lastMessage = null,
            mood = null,
            isLiveMode = 0L,
            is_dirty = 1L,
            updated_at = now,
            id = session.id,
        )

        syncEngine.enqueue(
            entityType = SyncEntityType.CHAT_SESSION.name,
            entityId = session.id,
            operation = "UPDATE",
            payload = serializeSession(session),
        )

        RequestState.Success(Unit)
    }

    override suspend fun deleteSession(sessionId: String): RequestState<Boolean> = withContext(Dispatchers.Default) {
        val uid = userId() ?: return@withContext RequestState.Error(Exception("Not authenticated"))
        messageQueries.deleteMessagesForSession(sessionId)
        sessionQueries.deleteSession(sessionId, uid)

        syncEngine.enqueue(
            entityType = SyncEntityType.CHAT_SESSION.name,
            entityId = sessionId,
            operation = "DELETE",
            payload = "",
        )

        RequestState.Success(true)
    }

    override suspend fun deleteAllSessions(): RequestState<Boolean> = withContext(Dispatchers.Default) {
        val uid = userId() ?: return@withContext RequestState.Error(Exception("Not authenticated"))
        val sessions = sessionQueries.getAllSessions(uid).executeAsList()
        for (s in sessions) {
            messageQueries.deleteMessagesForSession(s.id)
        }
        for (s in sessions) {
            sessionQueries.deleteSession(s.id, uid)
        }
        RequestState.Success(true)
    }

    // ─── Messages ─────────────────────────────────────────────────────

    override fun getMessagesForSession(sessionId: String): Flow<RequestState<List<ChatMessage>>> = flow {
        val messages = messageQueries.getMessagesForSession(sessionId).executeAsList().map { it.toDomain() }
        emit(RequestState.Success(messages))
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun sendMessage(sessionId: String, content: String): RequestState<ChatMessage> =
        withContext(Dispatchers.Default) {
            val message = ChatMessage(
                id = Uuid.random().toString(),
                sessionId = sessionId,
                content = content,
                isUser = true,
                status = MessageStatus.SENT,
            )

            messageQueries.insertMessage(
                id = message.id,
                sessionId = message.sessionId,
                content = message.content,
                isUser = 1L,
                timestamp = message.timestamp.toEpochMilliseconds(),
                status = message.status.name,
                error = null,
                is_dirty = 1L,
            )

            sessionQueries.incrementMessageCount(
                lastMessageAt = message.timestamp.toEpochMilliseconds(),
                id = sessionId,
            )

            syncEngine.enqueue(
                entityType = SyncEntityType.CHAT_MESSAGE.name,
                entityId = message.id,
                operation = "CREATE",
                payload = serializeMessage(message),
            )

            RequestState.Success(message)
        }

    override suspend fun deleteMessage(messageId: String): RequestState<Boolean> = withContext(Dispatchers.Default) {
        messageQueries.deleteMessage(messageId)

        syncEngine.enqueue(
            entityType = SyncEntityType.CHAT_MESSAGE.name,
            entityId = messageId,
            operation = "DELETE",
            payload = "",
        )

        RequestState.Success(true)
    }

    override suspend fun retryMessage(messageId: String): RequestState<ChatMessage> {
        val entity = messageQueries.getMessage(messageId).executeAsOneOrNull()
            ?: return RequestState.Error(Exception("Message not found"))
        val message = entity.toDomain()
        messageQueries.updateMessageStatus(status = MessageStatus.SENDING.name, error = null, id = messageId)
        return sendMessage(message.sessionId, message.content)
    }

    // ─── AI operations (require network — no local-first) ─────────────

    override suspend fun generateAiResponse(
        sessionId: String,
        userMessage: String,
        context: List<ChatMessage>,
    ): Flow<RequestState<String>> = flow {
        // AI generation requires network — delegate to SyncExecutor or return error
        emit(RequestState.Error(Exception("AI responses require network connectivity. Use network repository.")))
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun saveAiMessage(sessionId: String, content: String): RequestState<ChatMessage> =
        withContext(Dispatchers.Default) {
            val message = ChatMessage(
                id = Uuid.random().toString(),
                sessionId = sessionId,
                content = content,
                isUser = false,
                status = MessageStatus.SENT,
            )

            messageQueries.insertMessage(
                id = message.id,
                sessionId = message.sessionId,
                content = message.content,
                isUser = 0L,
                timestamp = message.timestamp.toEpochMilliseconds(),
                status = message.status.name,
                error = null,
                is_dirty = 0L, // AI messages come from server, already synced
            )

            sessionQueries.incrementMessageCount(
                lastMessageAt = message.timestamp.toEpochMilliseconds(),
                id = sessionId,
            )

            RequestState.Success(message)
        }

    override suspend fun exportSessionToDiary(sessionId: String): RequestState<String> {
        return RequestState.Error(Exception("Export requires network. Use network repository."))
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun saveLiveMessage(
        sessionId: String,
        content: String,
        isUser: Boolean,
    ): RequestState<ChatMessage> = withContext(Dispatchers.Default) {
        val message = ChatMessage(
            id = Uuid.random().toString(),
            sessionId = sessionId,
            content = content,
            isUser = isUser,
            status = MessageStatus.SENT,
        )

        messageQueries.insertMessage(
            id = message.id,
            sessionId = message.sessionId,
            content = message.content,
            isUser = if (message.isUser) 1L else 0L,
            timestamp = message.timestamp.toEpochMilliseconds(),
            status = message.status.name,
            error = null,
            is_dirty = if (isUser) 1L else 0L,
        )

        sessionQueries.incrementMessageCount(
            lastMessageAt = message.timestamp.toEpochMilliseconds(),
            id = sessionId,
        )

        if (isUser) {
            syncEngine.enqueue(
                entityType = SyncEntityType.CHAT_MESSAGE.name,
                entityId = message.id,
                operation = "CREATE",
                payload = serializeMessage(message),
            )
        }

        RequestState.Success(message)
    }

    override suspend fun getCrossSessionContext(
        currentSessionId: String,
        fromLiveSessions: Boolean,
        maxMessages: Int,
    ): String {
        val uid = userId() ?: return ""
        val isLiveMode = if (fromLiveSessions) 1L else 0L
        val recentSessions = sessionQueries.getRecentSessionsByMode(uid, isLiveMode, maxMessages.toLong()).executeAsList()

        val contextParts = mutableListOf<String>()
        for (session in recentSessions) {
            if (session.id == currentSessionId) continue
            val messages = messageQueries.getMessagesForSession(session.id).executeAsList()
            for (msg in messages.takeLast(3)) {
                val role = if (msg.isUser == 1L) "User" else "AI"
                contextParts.add("[$role] ${msg.content}")
            }
        }
        return contextParts.takeLast(maxMessages).joinToString("\n")
    }

    // ─── Delta sync: apply server changes ──────────────────────────────

    suspend fun applySessionChanges(
        created: List<ChatSession>,
        updated: List<ChatSession>,
        deletedIds: List<String>,
    ) = withContext(Dispatchers.Default) {
        for (session in created + updated) {
            sessionQueries.insertSession(
                id = session.id,
                title = session.title,
                createdAt = session.createdAt.toEpochMilliseconds(),
                lastMessageAt = session.lastMessageAt.toEpochMilliseconds(),
                aiModel = session.aiModel,
                messageCount = session.messageCount.toLong(),
                ownerId = session.ownerId,
                summary = null,
                lastMessage = null,
                mood = null,
                isLiveMode = 0L,
                is_dirty = 0L,
                updated_at = session.lastMessageAt.toEpochMilliseconds(),
            )
        }
        for (id in deletedIds) {
            messageQueries.deleteMessagesForSession(id)
            userId()?.let { uid -> sessionQueries.deleteSession(id, uid) }
        }
    }

    suspend fun applyMessageChanges(
        created: List<ChatMessage>,
        updated: List<ChatMessage>,
        deletedIds: List<String>,
    ) = withContext(Dispatchers.Default) {
        for (message in created + updated) {
            messageQueries.insertMessage(
                id = message.id,
                sessionId = message.sessionId,
                content = message.content,
                isUser = if (message.isUser) 1L else 0L,
                timestamp = message.timestamp.toEpochMilliseconds(),
                status = message.status.name,
                error = message.error,
                is_dirty = 0L,
            )
        }
        for (id in deletedIds) {
            messageQueries.deleteMessage(id)
        }
    }

    // ─── Mapping ──────────────────────────────────────────────────────

    private fun com.lifo.mongo.database.Chat_sessions.toDomain() = ChatSession(
        id = id,
        title = title,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        lastMessageAt = Instant.fromEpochMilliseconds(lastMessageAt),
        aiModel = aiModel,
        messageCount = messageCount.toInt(),
        ownerId = ownerId,
    )

    private fun com.lifo.mongo.database.Chat_messages.toDomain() = ChatMessage(
        id = id,
        sessionId = sessionId,
        content = content,
        isUser = isUser == 1L,
        timestamp = Instant.fromEpochMilliseconds(timestamp),
        status = try { MessageStatus.valueOf(status) } catch (_: Exception) { MessageStatus.SENT },
        error = error,
    )

    private fun serializeSession(session: ChatSession): String = json.encodeToString(
        mapOf(
            "id" to session.id,
            "title" to session.title,
            "createdAt" to session.createdAt.toEpochMilliseconds().toString(),
            "lastMessageAt" to session.lastMessageAt.toEpochMilliseconds().toString(),
            "aiModel" to session.aiModel,
            "messageCount" to session.messageCount.toString(),
            "ownerId" to session.ownerId,
        )
    )

    private fun serializeMessage(message: ChatMessage): String = json.encodeToString(
        mapOf(
            "id" to message.id,
            "sessionId" to message.sessionId,
            "content" to message.content,
            "isUser" to message.isUser.toString(),
            "timestamp" to message.timestamp.toEpochMilliseconds().toString(),
            "status" to message.status.name,
        )
    )
}
