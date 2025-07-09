package com.lifo.chat.data.repository

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.lifo.chat.BuildConfig
import com.lifo.chat.data.local.ChatDao
import com.lifo.chat.data.local.entities.ChatMessageEntity
import com.lifo.chat.data.local.entities.ChatSessionEntity
import com.lifo.chat.data.mapper.toDomain
import com.lifo.chat.data.mapper.toEntity
import com.lifo.chat.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        // IMPORTANTE: Usa la tua API key qui temporaneamente per testare
        // In produzione, questa dovrebbe venire da BuildConfig o essere iniettata
        private const val GEMINI_API_KEY = "AIzaSyBWCfLj-x7bZiYiHz1mj4b4dGSX8wBewko" // Sostituisci con la tua API key
    }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash", // Usa il modello flash che è più stabile
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.7f
            topK = 1
            topP = 1f
            maxOutputTokens = 2048
        }
    )

    override fun getAllSessions(): Flow<List<ChatSession>> {
        return chatDao.getAllSessions()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getSession(sessionId: String): ChatSession? {
        return withContext(Dispatchers.IO) {
            chatDao.getSession(sessionId)?.toDomain()
        }
    }

    override suspend fun createSession(title: String?): ChatResult<ChatSession> {
        return try {
            withContext(Dispatchers.IO) {
                val session = ChatSession(
                    title = title ?: generateSessionTitle()
                )
                chatDao.insertSession(session.toEntity())
                ChatResult.Success(session)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating session", e)
            ChatResult.Error(e)
        }
    }

    override suspend fun updateSession(session: ChatSession): ChatResult<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                chatDao.updateSession(session.toEntity())
                ChatResult.Success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating session", e)
            ChatResult.Error(e)
        }
    }

    override suspend fun deleteSession(sessionId: String): ChatResult<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                chatDao.deleteSessionWithMessages(sessionId)
                ChatResult.Success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting session", e)
            ChatResult.Error(e)
        }
    }

    override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun sendMessage(sessionId: String, content: String): ChatResult<ChatMessage> {
        return try {
            withContext(Dispatchers.IO) {
                val message = ChatMessage(
                    sessionId = sessionId,
                    content = content,
                    isUser = true,
                    status = MessageStatus.SENT
                )
                chatDao.insertMessage(message.toEntity())
                chatDao.incrementMessageCount(sessionId, Instant.now().toEpochMilli())
                ChatResult.Success(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            ChatResult.Error(e)
        }
    }

    override suspend fun deleteMessage(messageId: String): ChatResult<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                chatDao.deleteMessage(messageId)
                ChatResult.Success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message", e)
            ChatResult.Error(e)
        }
    }

    override suspend fun retryMessage(messageId: String): ChatResult<ChatMessage> {
        return try {
            withContext(Dispatchers.IO) {
                val message = chatDao.getMessage(messageId)?.toDomain()
                    ?: return@withContext ChatResult.Error(Exception("Message not found"))

                if (!message.isUser) {
                    return@withContext ChatResult.Error(Exception("Cannot retry AI messages"))
                }

                // Update status to sending
                chatDao.updateMessageStatus(messageId, MessageStatus.SENDING.name)

                // Mark as sent
                chatDao.updateMessageStatus(messageId, MessageStatus.SENT.name)

                ChatResult.Success(message.copy(status = MessageStatus.SENT))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrying message", e)
            ChatResult.Error(e)
        }
    }

    override suspend fun generateAiResponse(
        sessionId: String,
        userMessage: String,
        context: List<ChatMessage>
    ): Flow<ChatResult<String>> = flow {
        try {
            Log.d(TAG, "Starting AI response generation for message: $userMessage")
            Log.d(TAG, "Session ID: $sessionId")
            Log.d(TAG, "Context size: ${context.size}")

            // Create AI message entity
            val aiMessage = ChatMessage(
                sessionId = sessionId,
                content = "",
                isUser = false,
                status = MessageStatus.STREAMING
            )

            withContext(Dispatchers.IO) {
                chatDao.insertMessage(aiMessage.toEntity())
            }

            // Build conversation context
            val prompt = buildConversationPrompt(userMessage, context)
            Log.d(TAG, "Prompt built, starting generation...")

            try {
                // Generate streaming response
                val responseFlow = generativeModel.generateContentStream(prompt)
                val fullResponse = StringBuilder()

                responseFlow.collect { chunk ->
                    chunk.text?.let { text ->
                        fullResponse.append(text)
                        Log.d(TAG, "Received chunk: ${text.take(50)}...")

                        // Emit the accumulated response
                        emit(ChatResult.Success(fullResponse.toString()))

                        // Update message in database
                        withContext(Dispatchers.IO) {
                            chatDao.updateMessage(
                                aiMessage.copy(
                                    content = fullResponse.toString(),
                                    status = MessageStatus.STREAMING
                                ).toEntity()
                            )
                        }
                    }
                }

                // Mark as complete
                withContext(Dispatchers.IO) {
                    chatDao.updateMessage(
                        aiMessage.copy(
                            content = fullResponse.toString(),
                            status = MessageStatus.SENT
                        ).toEntity()
                    )
                    chatDao.incrementMessageCount(sessionId, Instant.now().toEpochMilli())
                }

                Log.d(TAG, "AI response completed: ${fullResponse.toString().take(100)}...")
                emit(ChatResult.Success(fullResponse.toString()))

            } catch (e: Exception) {
                Log.e(TAG, "Error during AI generation", e)

                // Update AI message with error
                withContext(Dispatchers.IO) {
                    chatDao.updateMessage(
                        aiMessage.copy(
                            content = "Sorry, I encountered an error. Please try again.",
                            status = MessageStatus.FAILED,
                            error = e.message
                        ).toEntity()
                    )
                }

                emit(ChatResult.Error(e))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI response", e)
            emit(ChatResult.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun exportSessionToDiary(sessionId: String): ChatResult<String> {
        return try {
            withContext(Dispatchers.IO) {
                val session = chatDao.getSession(sessionId)?.toDomain()
                    ?: return@withContext ChatResult.Error(Exception("Session not found"))

                val messages = chatDao.getMessagesForSession(sessionId).first()
                    .map { it.toDomain() }
                    .filter { it.status == MessageStatus.SENT }

                val diaryContent = buildDiaryContent(session, messages)
                ChatResult.Success(diaryContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting session", e)
            ChatResult.Error(e)
        }
    }

    private fun generateSessionTitle(): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
            .withZone(ZoneId.systemDefault())
        return "Chat - ${formatter.format(Instant.now())}"
    }

    private fun buildConversationPrompt(userMessage: String, context: List<ChatMessage>): String {
        val contextMessages = context.takeLast(10) // Last 10 messages for context

        val conversationHistory = contextMessages.joinToString("\n") { message ->
            if (message.isUser) "User: ${message.content}" else "Assistant: ${message.content}"
        }

        return """
            You are Calmify AI, a supportive and empathetic companion in a personal diary app. 
            Your role is to:
            - Provide emotional support and understanding
            - Help users reflect on their thoughts and feelings
            - Encourage mindfulness and self-awareness
            - Offer gentle, non-judgmental responses
            - Suggest healthy coping strategies when appropriate
            
            Important guidelines:
            - Be warm, caring, and conversational
            - Avoid giving medical advice
            - Encourage professional help for serious concerns
            - Keep responses concise but meaningful
            - Use simple, clear language
            
            Previous conversation:
            $conversationHistory
            
            User: $userMessage
            
            Please respond with empathy and understanding.
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
            val role = if (message.isUser) "**Me**" else "**Calmify AI**"
            val time = DateTimeFormatter.ofPattern("h:mm a")
                .withZone(ZoneId.systemDefault())
                .format(message.timestamp)

            "$role ($time):\n${message.content}"
        }

        return header + conversation
    }
}