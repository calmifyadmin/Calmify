package com.lifo.server.service

import com.lifo.server.service.MessagingService.MessageDto
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * MessagingHub — in-memory registry of live WebSocket sessions per userId.
 *
 * Single-instance fan-out: if user A and user B of the same conversation are
 * served by different Cloud Run instances, B won't receive A's events. Accepted
 * MVP tradeoff; future fix is Pub/Sub bridge. See project_phase3_socialmessaging.md.
 */
class MessagingHub {
    private val log = LoggerFactory.getLogger(MessagingHub::class.java)
    private val sessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val mutex = Mutex()

    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun register(userId: String, session: WebSocketSession) {
        mutex.withLock {
            sessions.getOrPut(userId) { mutableSetOf() }.add(session)
        }
        log.info("ws.register user=$userId total=${sessions[userId]?.size ?: 0}")
    }

    suspend fun unregister(userId: String, session: WebSocketSession) {
        mutex.withLock {
            val set = sessions[userId] ?: return@withLock
            set.remove(session)
            if (set.isEmpty()) sessions.remove(userId)
        }
        log.info("ws.unregister user=$userId remaining=${sessions[userId]?.size ?: 0}")
    }

    /** Broadcast event to all live sessions of all recipients. Non-fatal per-session failures. */
    suspend fun broadcast(event: MessagingEvent, recipients: Collection<String>) {
        if (recipients.isEmpty()) return
        val payload = json.encodeToString(MessagingEvent.serializer(), event)
        val targets = mutex.withLock {
            recipients.flatMap { uid -> sessions[uid]?.toList() ?: emptyList() }
        }
        targets.forEach { session ->
            runCatching { session.send(Frame.Text(payload)) }
                .onFailure { log.warn("ws.send.fail ${it.message}") }
        }
    }

    fun liveUserCount(): Int = sessions.size
}

@Serializable
sealed class MessagingEvent {
    @Serializable
    @SerialName("message.created")
    data class MessageCreated(
        val conversationId: String,
        val message: MessageDto,
    ) : MessagingEvent()

    @Serializable
    @SerialName("conversation.updated")
    data class ConversationUpdated(
        val conversationId: String,
        val lastMessage: String,
        val lastMessageAt: Long,
    ) : MessagingEvent()

    @Serializable
    @SerialName("typing.updated")
    data class TypingUpdated(
        val conversationId: String,
        val userId: String,
        val isTyping: Boolean,
    ) : MessagingEvent()

    @Serializable
    @SerialName("auth.ok")
    data class AuthOk(val uid: String) : MessagingEvent()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : MessagingEvent()
}
