package com.lifo.mongo.repository

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.firebase.auth.FirebaseAuth
import com.lifo.mongo.database.dao.ChatMessageDao
import com.lifo.mongo.database.dao.ChatSessionDao
import com.lifo.mongo.database.entity.ChatMessageEntity
import com.lifo.mongo.database.entity.ChatSessionEntity
import com.lifo.util.Constants.GEMINI_API_KEY
import com.lifo.util.model.RequestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val auth: FirebaseAuth
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        private const val STREAMING_BUFFER_SIZE = 10
    }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 1
            topP = 1f
            maxOutputTokens = 2048
        }
    )

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    override fun getAllSessions(): Flow<RequestState<List<ChatSession>>> {
        return chatSessionDao.getAllSessions(currentUserId)
            .map<List<ChatSessionEntity>, RequestState<List<ChatSession>>> { entities ->
                RequestState.Success(entities.map { it.toDomain() })
            }
            .catch { e ->
                emit(RequestState.Error(Exception(e)))
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getSession(sessionId: String): RequestState<ChatSession> {
        return withContext(Dispatchers.IO) {
            try {
                val session = chatSessionDao.getSession(sessionId, currentUserId)
                if (session != null) {
                    RequestState.Success(session.toDomain())
                } else {
                    RequestState.Error(Exception("Session not found"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting session", e)
                RequestState.Error(e)
            }
        }
    }

    override suspend fun createSession(title: String?): RequestState<ChatSession> {
        return withContext(Dispatchers.IO) {
            try {
                val session = ChatSession(
                    id = UUID.randomUUID().toString(),
                    title = title ?: generateSessionTitle(),
                    createdAt = Instant.now(),
                    lastMessageAt = Instant.now(),
                    aiModel = "gemini-2.0-flash",
                    messageCount = 0,
                    ownerId = currentUserId
                )
                chatSessionDao.insertSession(session.toEntity())
                RequestState.Success(session)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating session", e)
                RequestState.Error(e)
            }
        }
    }

    override suspend fun updateSession(session: ChatSession): RequestState<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                chatSessionDao.updateSession(session.toEntity())
                RequestState.Success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating session", e)
                RequestState.Error(e)
            }
        }
    }

    override suspend fun deleteSession(sessionId: String): RequestState<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                chatMessageDao.deleteMessagesForSession(sessionId)
                chatSessionDao.deleteSession(sessionId, currentUserId)
                RequestState.Success(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting session", e)
                RequestState.Error(e)
            }
        }
    }

    override fun getMessagesForSession(sessionId: String): Flow<RequestState<List<ChatMessage>>> {
        return chatMessageDao.getMessagesForSession(sessionId)
            .map<List<ChatMessageEntity>, RequestState<List<ChatMessage>>> { entities ->
                RequestState.Success(entities.map { it.toDomain() })
            }
            .catch { e ->
                emit(RequestState.Error(Exception(e)))
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun sendMessage(sessionId: String, content: String): RequestState<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val message = ChatMessage(
                    sessionId = sessionId,
                    content = content,
                    isUser = true,
                    status = MessageStatus.SENT
                )
                chatMessageDao.insertMessage(message.toEntity())
                chatSessionDao.incrementMessageCount(sessionId, Instant.now().toEpochMilli())
                RequestState.Success(message)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                RequestState.Error(e)
            }
        }
    }

    override suspend fun deleteMessage(messageId: String): RequestState<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                chatMessageDao.deleteMessage(messageId)
                RequestState.Success(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting message", e)
                RequestState.Error(e)
            }
        }
    }

    override suspend fun retryMessage(messageId: String): RequestState<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val message = chatMessageDao.getMessage(messageId)?.toDomain()
                    ?: return@withContext RequestState.Error(Exception("Message not found"))

                if (!message.isUser) {
                    return@withContext RequestState.Error(Exception("Cannot retry AI messages"))
                }

                chatMessageDao.updateMessageStatus(messageId, MessageStatus.SENDING.name)
                chatMessageDao.updateMessageStatus(messageId, MessageStatus.SENT.name)

                RequestState.Success(message.copy(status = MessageStatus.SENT))
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying message", e)
                RequestState.Error(e)
            }
        }
    }

    override suspend fun generateAiResponse(
        sessionId: String,
        userMessage: String,
        context: List<ChatMessage>
    ): Flow<RequestState<String>> = flow {
        try {
            Log.d(TAG, "Starting AI response generation for message: $userMessage")

            val prompt = buildConversationPrompt(userMessage, context)
            val responseBuffer = StringBuilder()

            try {
                generativeModel.generateContentStream(prompt)
                    .buffer(STREAMING_BUFFER_SIZE)
                    .collect { chunk ->
                        chunk.text?.let { text ->
                            responseBuffer.append(text)
                            Log.d(TAG, "Streaming chunk: ${text.take(50)}...")
                            emit(RequestState.Success(responseBuffer.toString()))
                        }
                    }

                if (responseBuffer.isNotEmpty()) {
                    emit(RequestState.Success(responseBuffer.toString()))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during AI generation", e)
                emit(RequestState.Error(e))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI response", e)
            emit(RequestState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun saveAiMessage(sessionId: String, content: String): RequestState<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                val message = ChatMessage(
                    sessionId = sessionId,
                    content = content,
                    isUser = false,
                    status = MessageStatus.SENT
                )
                chatMessageDao.insertMessage(message.toEntity())
                chatSessionDao.incrementMessageCount(sessionId, Instant.now().toEpochMilli())
                Log.d(TAG, "AI message saved: ${content.take(50)}...")
                RequestState.Success(message)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving AI message", e)
                RequestState.Error(e)
            }
        }
    }

    override suspend fun exportSessionToDiary(sessionId: String): RequestState<String> {
        return withContext(Dispatchers.IO) {
            try {
                val session = chatSessionDao.getSession(sessionId, currentUserId)?.toDomain()
                    ?: return@withContext RequestState.Error(Exception("Session not found"))

                val messages = chatMessageDao.getMessagesForSession(sessionId).first()
                    .map { it.toDomain() }
                    .filter { it.status == MessageStatus.SENT }

                val diaryContent = buildDiaryContent(session, messages)
                RequestState.Success(diaryContent)
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting session", e)
                RequestState.Error(e)
            }
        }
    }

    private fun generateSessionTitle(): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
            .withZone(ZoneId.systemDefault())
        return "Chat - ${formatter.format(Instant.now())}"
    }

    private fun buildConversationPrompt(userMessage: String, context: List<ChatMessage>): String {
        val contextMessages = context.takeLast(10)

        val conversationHistory = contextMessages.joinToString("\n") { message ->
            if (message.isUser) "User: ${message.content}" else "Assistant: ${message.content}"
        }

        return """
            You are Lifo, a supportive and empathetic AI companion in a personal diary app called Calmify. 
            Your role is to:
            - Provide emotional support and understanding
            - Help users reflect on their thoughts and feelings
            - Encourage mindfulness and self-awareness
            - Offer gentle, non-judgmental responses
            - Suggest healthy coping strategies when appropriate
            
            Important guidelines:
            - Be warm, caring, and conversational
            - Respond in Italian
            - Avoid giving medical advice
            - Encourage professional help for serious concerns
            - Keep responses concise but meaningful
            - Use simple, clear language
            
            Previous conversation:
            $conversationHistory
            
            User: $userMessage
            
            Please respond with empathy and understanding in Italian.
        """.trimIndent()
    }

    private fun buildDiaryContent(session: ChatSession, messages: List<ChatMessage>): String {
        val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a")
            .withZone(ZoneId.systemDefault())

        val header = """
            # ${session.title}
            *Exported from AI Chat on ${formatter.format(Instant.now())}*
            
            ---
            
        """.trimIndent()

        val conversation = messages.joinToString("\n\n") { message ->
            val role = if (message.isUser) "**Me**" else "**Lifo AI**"
            val time = DateTimeFormatter.ofPattern("h:mm a")
                .withZone(ZoneId.systemDefault())
                .format(message.timestamp)

            "$role ($time):\n${message.content}"
        }

        return header + conversation
    }

    // Extension functions per mapping
    private fun ChatSessionEntity.toDomain() = ChatSession(
        id = id,
        title = title,
        createdAt = Instant.ofEpochMilli(createdAt),
        lastMessageAt = Instant.ofEpochMilli(lastMessageAt),
        aiModel = aiModel,
        messageCount = messageCount,
        ownerId = ownerId
    )

    private fun ChatSession.toEntity() = ChatSessionEntity(
        id = id,
        title = title,
        createdAt = createdAt.toEpochMilli(),
        lastMessageAt = lastMessageAt.toEpochMilli(),
        aiModel = aiModel,
        messageCount = messageCount,
        ownerId = ownerId
    )

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        sessionId = sessionId,
        content = content,
        isUser = isUser,
        timestamp = Instant.ofEpochMilli(timestamp),
        status = MessageStatus.valueOf(status),
        error = error
    )

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        id = id,
        sessionId = sessionId,
        content = content,
        isUser = isUser,
        timestamp = timestamp.toEpochMilli(),
        status = status.name,
        error = error
    )
}