package com.lifo.mongo.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lifo.util.model.RequestState
import com.lifo.util.repository.SocialMessagingRepository
import com.lifo.util.repository.SocialMessagingRepository.Conversation
import com.lifo.util.repository.SocialMessagingRepository.Message
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FirestoreSocialMessagingRepository Implementation
 *
 * Firestore-backed implementation of SocialMessagingRepository.
 * Structure:
 *   conversations/{conversationId}                          — conversation metadata
 *   conversations/{conversationId}/messages/{messageId}     — messages
 *   conversations/{conversationId}/typing/{userId}          — typing indicators
 *
 * Real-time messaging via Firestore snapshot listeners.
 * MVP implementation — production may use Firebase Realtime Database for lower latency.
 */
@Singleton
class FirestoreSocialMessagingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SocialMessagingRepository {

    companion object {
        private const val TAG = "FirestoreMsgRepo"
        private const val COLLECTION_CONVERSATIONS = "conversations"
        private const val SUBCOLLECTION_MESSAGES = "messages"
        private const val SUBCOLLECTION_TYPING = "typing"
    }

    private val conversationsCollection by lazy { firestore.collection(COLLECTION_CONVERSATIONS) }

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    /**
     * Get all conversations for a user, real-time.
     * Uses array-contains to find conversations where the user is a participant.
     */
    override fun getConversations(userId: String): Flow<RequestState<List<Conversation>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = conversationsCollection
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting conversations: ${error.message}")
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val conversations = snapshot.documents.mapNotNull { doc ->
                        try {
                            docToConversation(doc, userId)
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed conversation ${doc.id}")
                            null
                        }
                    }
                    trySend(RequestState.Success(conversations))
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Get messages in a conversation, real-time, ordered chronologically.
     */
    override fun getMessages(conversationId: String, limit: Int): Flow<RequestState<List<Message>>> = callbackFlow {
        trySend(RequestState.Loading)

        val listenerRegistration = conversationsCollection.document(conversationId)
            .collection(SUBCOLLECTION_MESSAGES)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting messages: ${error.message}")
                    trySend(RequestState.Error(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        try {
                            docToMessage(doc)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(RequestState.Success(messages))
                } else {
                    trySend(RequestState.Success(emptyList()))
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Send a message in a conversation.
     * Also updates the conversation's lastMessage and lastMessageAt.
     */
    override suspend fun sendMessage(conversationId: String, message: Message): RequestState<String> {
        return try {
            val batch = firestore.batch()

            // Create message document
            val messageRef = conversationsCollection.document(conversationId)
                .collection(SUBCOLLECTION_MESSAGES)
                .document()

            val messageWithId = message.copy(
                id = messageRef.id,
                senderId = currentUserId,
                createdAt = System.currentTimeMillis()
            )

            batch.set(messageRef, messageToMap(messageWithId))

            // Update conversation metadata
            val conversationRef = conversationsCollection.document(conversationId)
            batch.update(conversationRef, mapOf(
                "lastMessage" to messageWithId.text,
                "lastMessageAt" to messageWithId.createdAt
            ))

            batch.commit().await()

            // Clear typing indicator
            setTyping(conversationId, currentUserId, false)

            Log.d(TAG, "Message sent in conversation $conversationId")
            RequestState.Success(messageRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}")
            RequestState.Error(e)
        }
    }

    /**
     * Create a new conversation between participants.
     * Checks if a conversation already exists between these exact participants.
     */
    override suspend fun createConversation(participantIds: List<String>): RequestState<String> {
        return try {
            if (participantIds.size < 2) {
                return RequestState.Error(Exception("Conversation requires at least 2 participants"))
            }

            val sortedIds = participantIds.sorted()

            // Check for existing conversation with same participants
            // For 1-on-1 chats, check if one already exists
            if (sortedIds.size == 2) {
                val existing = conversationsCollection
                    .whereArrayContains("participantIds", sortedIds[0])
                    .get()
                    .await()

                val existingConvo = existing.documents.firstOrNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val participants = doc.get("participantIds") as? List<String> ?: emptyList()
                    participants.sorted() == sortedIds
                }

                if (existingConvo != null) {
                    Log.d(TAG, "Existing conversation found: ${existingConvo.id}")
                    return RequestState.Success(existingConvo.id)
                }
            }

            // Create new conversation
            val docRef = conversationsCollection.document()
            val conversationData = mapOf(
                "participantIds" to sortedIds,
                "lastMessage" to null,
                "lastMessageAt" to System.currentTimeMillis(),
                "createdAt" to System.currentTimeMillis()
            )
            docRef.set(conversationData).await()

            Log.d(TAG, "Conversation created: ${docRef.id}")
            RequestState.Success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating conversation: ${e.message}")
            RequestState.Error(e)
        }
    }

    /**
     * Mark all messages in a conversation as read for a specific user.
     */
    override suspend fun markConversationRead(conversationId: String, userId: String): RequestState<Boolean> {
        return try {
            val unreadMessages = conversationsCollection.document(conversationId)
                .collection(SUBCOLLECTION_MESSAGES)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            if (unreadMessages.isEmpty) {
                return RequestState.Success(true)
            }

            // Only mark messages sent by others as read
            val batch = firestore.batch()
            var count = 0
            unreadMessages.documents
                .filter { it.getString("senderId") != userId }
                .forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                    count++
                }

            if (count > 0) {
                batch.commit().await()
            }

            Log.d(TAG, "Marked $count messages as read in $conversationId")
            RequestState.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking conversation read: ${e.message}")
            RequestState.Error(e)
        }
    }

    /**
     * Real-time typing status for a conversation.
     * Returns list of user IDs who are currently typing.
     */
    override fun getTypingStatus(conversationId: String): Flow<List<String>> = callbackFlow {
        val listenerRegistration = conversationsCollection.document(conversationId)
            .collection(SUBCOLLECTION_TYPING)
            .whereEqualTo("isTyping", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val typingUserIds = snapshot?.documents
                    ?.map { it.id }
                    ?.filter { it != currentUserId } // Don't show own typing indicator
                    ?: emptyList()

                trySend(typingUserIds)
            }

        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Set typing indicator for the current user in a conversation.
     */
    override suspend fun setTyping(conversationId: String, userId: String, isTyping: Boolean) {
        try {
            val typingRef = conversationsCollection.document(conversationId)
                .collection(SUBCOLLECTION_TYPING)
                .document(userId)

            if (isTyping) {
                typingRef.set(mapOf(
                    "isTyping" to true,
                    "updatedAt" to System.currentTimeMillis()
                )).await()
            } else {
                typingRef.delete().await()
            }
        } catch (e: Exception) {
            // Typing indicator failure is non-critical, don't propagate
            Log.w(TAG, "Error setting typing status: ${e.message}")
        }
    }

    // -- Helpers --

    private fun docToConversation(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        userId: String
    ): Conversation {
        @Suppress("UNCHECKED_CAST")
        val participantIds = doc.get("participantIds") as? List<String> ?: emptyList()

        return Conversation(
            id = doc.id,
            participantIds = participantIds,
            lastMessage = doc.getString("lastMessage"),
            lastMessageAt = doc.getLong("lastMessageAt") ?: 0,
            unreadCount = 0 // Will be computed client-side or via Cloud Function counter
        )
    }

    private fun docToMessage(doc: com.google.firebase.firestore.DocumentSnapshot): Message {
        return Message(
            id = doc.id,
            senderId = doc.getString("senderId") ?: "",
            text = doc.getString("text") ?: "",
            createdAt = doc.getLong("createdAt") ?: 0,
            isRead = doc.getBoolean("isRead") ?: false
        )
    }

    private fun messageToMap(message: Message): Map<String, Any?> {
        return mapOf(
            "senderId" to message.senderId,
            "text" to message.text,
            "createdAt" to message.createdAt,
            "isRead" to message.isRead
        )
    }
}
