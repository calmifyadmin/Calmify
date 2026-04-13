package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.util.model.RequestState
import com.lifo.util.repository.SocialMessagingRepository
import com.lifo.util.repository.SocialMessagingRepository.Conversation
import com.lifo.util.repository.SocialMessagingRepository.Message
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/**
 * REST + WebSocket implementation of SocialMessagingRepository.
 *
 * REST is the source of truth for reads (initial snapshot) and all writes.
 * WebSocket delivers server-pushed [MessagingEvent]s for near-real-time updates.
 *
 * A single WebSocket connection is multiplexed across all active Flows via
 * [events], which is a shared [MutableSharedFlow]. Downstream Flows subscribe to
 * it and filter by conversationId / event type.
 *
 * Reconnection: exponential backoff (1s → 2s → 4s → 8s → max 30s). On reconnect
 * the server is the source of truth — callers must refetch via REST.
 */
class KtorSocialMessagingRepository(
    private val api: KtorApiClient,
) : SocialMessagingRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val events = MutableSharedFlow<JsonObject>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Signals a successful (re)connect — subscribers refetch their REST snapshot. */
    private val reconnects = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val connectMutex = Mutex()
    private var connectionJob: Job? = null
    private var currentSession: DefaultClientWebSocketSession? = null

    private suspend fun ensureConnected() = connectMutex.withLock {
        if (connectionJob?.isActive == true) return@withLock
        connectionJob = scope.launch { runConnectionLoop() }
    }

    private suspend fun runConnectionLoop() {
        var attempt = 0
        while (scope.isActive) {
            val token = api.getIdToken() ?: run {
                delay(backoff(attempt++)); continue
            }
            val wsUrl = buildWsUrl(api.baseUrl, token)
            try {
                api.client.webSocket(urlString = wsUrl) {
                    currentSession = this
                    attempt = 0
                    reconnects.tryEmit(Unit)
                    // Token refresh loop — rotate JWT every ~45 min (Firebase expires @ 60m)
                    val refreshJob = launch(Dispatchers.Default) {
                        while (isActive) {
                            delay(45 * 60_000L)
                            val fresh = api.getIdToken(forceRefresh = true) ?: continue
                            runCatching {
                                send(Frame.Text("""{"type":"auth.refresh","token":"$fresh"}"""))
                            }
                        }
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val obj = runCatching { json.parseToJsonElement(frame.readText()).jsonObject }.getOrNull() ?: continue
                            events.tryEmit(obj)
                        }
                    } finally {
                        refreshJob.cancel()
                        currentSession = null
                    }
                }
            } catch (_: Throwable) {
                // fall through to backoff
            }
            delay(backoff(attempt++))
        }
    }

    private fun backoff(attempt: Int): Long {
        val base = (1_000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
        return base + Random.nextLong(0, 500)
    }

    private fun buildWsUrl(baseUrl: String, token: String): String {
        val wsBase = when {
            baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
            else -> baseUrl
        }.trimEnd('/')
        return "$wsBase/api/v1/messaging/ws?token=$token"
    }

    // ---------- Flows ----------

    override fun getConversations(userId: String): Flow<RequestState<List<Conversation>>> = flow {
        emit(RequestState.Loading)
        ensureConnected()

        // Initial snapshot + re-fetch on reconnect
        val trigger = reconnects.onStart { emit(Unit) }
        trigger.collect {
            when (val r = api.get<ConversationsResponse>("/api/v1/messaging/conversations")) {
                is RequestState.Success -> emit(RequestState.Success(r.data.data.map { it.toDomain() }))
                is RequestState.Error -> emit(RequestState.Error(r.error))
                else -> Unit
            }
        }
    }

    override fun getMessages(conversationId: String, limit: Int): Flow<RequestState<List<Message>>> = flow {
        emit(RequestState.Loading)
        ensureConnected()

        var cache: MutableList<Message> = mutableListOf()

        // Initial snapshot
        when (val r = api.get<MessagesResponse>("/api/v1/messaging/conversations/$conversationId/messages?limit=$limit")) {
            is RequestState.Success -> {
                cache = r.data.data.map { it.toDomain() }.toMutableList()
                emit(RequestState.Success(cache.toList()))
            }
            is RequestState.Error -> emit(RequestState.Error(r.error))
            else -> Unit
        }

        // Merge incoming message.created for this conversation
        events
            .filter { it["type"]?.jsonPrimitive?.content == "message.created" && it["conversationId"]?.jsonPrimitive?.content == conversationId }
            .collect { obj ->
                val msgObj = obj["message"] ?: return@collect
                val msg = runCatching { json.decodeFromJsonElement(MessageDto.serializer(), msgObj) }.getOrNull() ?: return@collect
                val domain = msg.toDomain()
                if (cache.none { it.id == domain.id }) {
                    cache.add(domain)
                    emit(RequestState.Success(cache.toList()))
                }
            }
    }

    override suspend fun sendMessage(conversationId: String, message: Message): RequestState<String> {
        val body = SendMessageRequest(text = message.text, imageUrls = message.imageUrls)
        return when (val r = api.post<MessageDto>(
            path = "/api/v1/messaging/conversations/$conversationId/messages",
            body = body,
        )) {
            is RequestState.Success -> RequestState.Success(r.data.id)
            is RequestState.Error -> RequestState.Error(r.error)
            else -> RequestState.Error(Exception("unexpected state"))
        }
    }

    override suspend fun createConversation(participantIds: List<String>): RequestState<String> {
        val body = CreateConversationRequest(participantIds = participantIds)
        return when (val r = api.post<ConversationDto>(path = "/api/v1/messaging/conversations", body = body)) {
            is RequestState.Success -> RequestState.Success(r.data.id)
            is RequestState.Error -> RequestState.Error(r.error)
            else -> RequestState.Error(Exception("unexpected state"))
        }
    }

    override suspend fun markConversationRead(conversationId: String, userId: String): RequestState<Boolean> {
        return when (val r = api.post<MarkReadResponse>(
            path = "/api/v1/messaging/conversations/$conversationId/read",
        )) {
            is RequestState.Success -> RequestState.Success(true)
            is RequestState.Error -> RequestState.Error(r.error)
            else -> RequestState.Error(Exception("unexpected state"))
        }
    }

    override fun getTypingStatus(conversationId: String): Flow<List<String>> = flow {
        ensureConnected()
        // Snapshot
        when (val r = api.get<TypingStatusResponse>("/api/v1/messaging/conversations/$conversationId/typing")) {
            is RequestState.Success -> emit(r.data.userIds)
            else -> emit(emptyList())
        }
        val live = mutableSetOf<String>()
        events
            .filter { it["type"]?.jsonPrimitive?.content == "typing.updated" && it["conversationId"]?.jsonPrimitive?.content == conversationId }
            .collect { obj ->
                val uid = obj["userId"]?.jsonPrimitive?.content ?: return@collect
                val typing = obj["isTyping"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: return@collect
                val changed = if (typing) live.add(uid) else live.remove(uid)
                if (changed) emit(live.toList())
            }
    }

    override suspend fun setTyping(conversationId: String, userId: String, isTyping: Boolean) {
        api.post<Unit>(
            path = "/api/v1/messaging/conversations/$conversationId/typing",
            body = TypingRequest(isTyping = isTyping),
        )
    }

    // ---------- Serialization DTOs ----------

    @Serializable
    private data class ConversationsResponse(val data: List<ConversationDto> = emptyList())

    @Serializable
    private data class MessagesResponse(val data: List<MessageDto> = emptyList())

    @Serializable
    private data class TypingStatusResponse(val userIds: List<String> = emptyList())

    @Serializable
    private data class ConversationDto(
        val id: String = "",
        val participantIds: List<String> = emptyList(),
        val lastMessage: String? = null,
        val lastMessageAt: Long = 0,
        val unreadCount: Int = 0,
    ) {
        fun toDomain() = Conversation(
            id = id,
            participantIds = participantIds,
            lastMessage = lastMessage,
            lastMessageAt = lastMessageAt,
            unreadCount = unreadCount,
        )
    }

    @Serializable
    private data class MessageDto(
        val id: String = "",
        val senderId: String = "",
        val text: String = "",
        val imageUrls: List<String> = emptyList(),
        val createdAt: Long = 0,
        val isRead: Boolean = false,
    ) {
        fun toDomain() = Message(
            id = id,
            senderId = senderId,
            text = text,
            imageUrls = imageUrls,
            createdAt = createdAt,
            isRead = isRead,
        )
    }

    @Serializable
    private data class SendMessageRequest(
        val text: String = "",
        val imageUrls: List<String> = emptyList(),
    )

    @Serializable
    private data class CreateConversationRequest(val participantIds: List<String> = emptyList())

    @Serializable
    private data class TypingRequest(val isTyping: Boolean = false)

    @Serializable
    private data class MarkReadResponse(val markedCount: Int = 0)
}

