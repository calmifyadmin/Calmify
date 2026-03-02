package com.lifo.util.repository

import com.lifo.util.model.RequestState
import kotlinx.coroutines.flow.Flow

/**
 * SocialMessagingRepository Interface
 *
 * Manages direct messaging between users.
 * Real-time via Firestore listeners.
 */
interface SocialMessagingRepository {
    fun getConversations(userId: String): Flow<RequestState<List<Conversation>>>
    fun getMessages(conversationId: String, limit: Int = 50): Flow<RequestState<List<Message>>>
    suspend fun sendMessage(conversationId: String, message: Message): RequestState<String>
    suspend fun createConversation(participantIds: List<String>): RequestState<String>
    suspend fun markConversationRead(conversationId: String, userId: String): RequestState<Boolean>
    fun getTypingStatus(conversationId: String): Flow<List<String>>
    suspend fun setTyping(conversationId: String, userId: String, isTyping: Boolean)

    data class Conversation(
        val id: String = "",
        val participantIds: List<String> = emptyList(),
        val lastMessage: String? = null,
        val lastMessageAt: Long = 0,
        val unreadCount: Int = 0
    )

    data class Message(
        val id: String = "",
        val senderId: String = "",
        val text: String = "",
        val createdAt: Long = 0,
        val isRead: Boolean = false
    )
}
