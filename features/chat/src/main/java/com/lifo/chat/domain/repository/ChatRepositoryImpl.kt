package com.lifo.chat.data.repository

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
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
        private const val GEMINI_API_KEY = "AIzaSyBWCfLj-x7bZiYiHz1mj4b4dGSX8wBewko"
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

    override fun getAllSessions(): Flow<List<ChatSession>> {
        return chatDao.getAllSessions()
            .distinctUntilChanged() // Evita emissioni duplicate
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
            .distinctUntilChanged() // CRITICO: evita re-emit degli stessi messaggi
            .map { entities ->
                entities.map { it.toDomain() }
                    .filter { !it.isStreaming } // Filtra messaggi temporanei
            }
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

                chatDao.updateMessageStatus(messageId, MessageStatus.SENDING.name)
                chatDao.updateMessageStatus(messageId, MessageStatus.SENT.name)

                ChatResult.Success(message.copy(status = MessageStatus.SENT))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrying message", e)
            ChatResult.Error(e)
        }
    }

    // OTTIMIZZATO: Non salva in DB durante streaming
    override suspend fun generateAiResponse(
        sessionId: String,
        userMessage: String,
        context: List<ChatMessage>
    ): Flow<ChatResult<String>> = flow {
        try {
            Log.d(TAG, "Starting AI response generation for message: $userMessage")

            // NON creare messaggio nel DB durante streaming!
            val prompt = buildConversationPrompt(userMessage, context)
            val responseBuffer = StringBuilder()

            try {
                generativeModel.generateContentStream(prompt)
                    .buffer(STREAMING_BUFFER_SIZE) // Buffer per performance
                    .collect { chunk ->
                        chunk.text?.let { text ->
                            responseBuffer.append(text)
                            Log.d(TAG, "Streaming chunk: ${text.take(50)}...")

                            // Emetti solo il testo accumulato
                            emit(ChatResult.Success(responseBuffer.toString()))
                        }
                    }

                // Emissione finale
                if (responseBuffer.isNotEmpty()) {
                    emit(ChatResult.Success(responseBuffer.toString()))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during AI generation", e)
                emit(ChatResult.Error(e))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI response", e)
            emit(ChatResult.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    // NUOVO: Salva messaggio AI solo dopo streaming completo
    override suspend fun saveAiMessage(sessionId: String, content: String): ChatResult<ChatMessage> {
        return try {
            withContext(Dispatchers.IO) {
                val message = ChatMessage(
                    sessionId = sessionId,
                    content = content,
                    isUser = false,
                    status = MessageStatus.SENT
                )

                // Salva UNA SOLA VOLTA nel database
                chatDao.insertMessage(message.toEntity())
                chatDao.incrementMessageCount(sessionId, Instant.now().toEpochMilli())

                Log.d(TAG, "AI message saved: ${content.take(50)}...")
                ChatResult.Success(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving AI message", e)
            ChatResult.Error(e)
        }
    }

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
}