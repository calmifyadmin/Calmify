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
import com.lifo.util.model.Diary
import com.lifo.util.model.RequestState
import com.lifo.util.toInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
        private const val DIARY_CONTEXT_DAYS = 30
        private const val MAX_DIARY_ENTRIES = 20
    }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.8f
            topK = 1
            topP = 0.95f
            maxOutputTokens = 4096
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
                    title = title ?: generatePersonalizedSessionTitle(),
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
            Log.d(TAG, "Starting AI response generation with diary context")

            // Recupera il contesto del diario
            val diaryContext = getDiaryContext()
            Log.d(TAG, "Retrieved ${diaryContext.size} diary entries for context")

            val userProfile = analyzeUserProfile(diaryContext)
            val currentMood = detectCurrentMood(diaryContext)
            val recurringThemes = extractRecurringThemes(diaryContext)

            Log.d(TAG, "User profile: $userProfile")
            Log.d(TAG, "Current mood: $currentMood")
            Log.d(TAG, "Themes: $recurringThemes")

            val prompt = buildPersonalizedPrompt(
                userMessage = userMessage,
                conversationContext = context,
                diaryContext = diaryContext,
                userProfile = userProfile,
                currentMood = currentMood,
                recurringThemes = recurringThemes
            )

            val responseBuffer = StringBuilder()

            try {
                generativeModel.generateContentStream(prompt)
                    .buffer(STREAMING_BUFFER_SIZE)
                    .collect { chunk ->
                        chunk.text?.let { text ->
                            responseBuffer.append(text)
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
                Log.d(TAG, "AI message saved")
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

                val diaryContent = buildEnhancedDiaryContent(session, messages)
                RequestState.Success(diaryContent)
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting session", e)
                RequestState.Error(e)
            }
        }
    }

    private suspend fun getDiaryContext(): List<Diary> {
        return withContext(Dispatchers.IO) {
            try {
                // Usa MongoDB singleton per accedere ai diari
                val diariesResult = MongoDB.getAllDiaries().first()

                when (diariesResult) {
                    is RequestState.Success -> {
                        val cutoffDate = Instant.now().minus(DIARY_CONTEXT_DAYS.toLong(), ChronoUnit.DAYS)
                        val diaries = diariesResult.data
                            .flatMap { it.value }
                            .filter { diary ->
                                diary.date.toInstant().isAfter(cutoffDate)
                            }
                            .sortedByDescending { it.date.toInstant() }
                            .take(MAX_DIARY_ENTRIES)

                        Log.d(TAG, "Found ${diaries.size} diary entries in the last $DIARY_CONTEXT_DAYS days")

                        diaries.firstOrNull()?.let { diary ->
                            Log.d(TAG, "Latest diary: ${diary.title} - ${diary.description.take(100)}...")
                        }

                        diaries
                    }
                    is RequestState.Error -> {
                        Log.e(TAG, "Error getting diaries: ${diariesResult.error.message}")
                        emptyList()
                    }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting diary context", e)
                emptyList()
            }
        }
    }

    private fun analyzeUserProfile(diaries: List<Diary>): UserProfile {
        val moodFrequency = diaries.groupingBy { it.mood }.eachCount()
        val dominantMood = moodFrequency.maxByOrNull { it.value }?.key ?: "Neutral"

        val writingTimes = diaries.map {
            it.date.toInstant()
                .atZone(ZoneId.systemDefault())
                .hour
        }
        val preferredWritingTime = when {
            writingTimes.count { it in 5..11 } > writingTimes.size / 2 -> "mattutino"
            writingTimes.count { it in 17..23 } > writingTimes.size / 2 -> "serale"
            else -> "vario"
        }

        val themes = extractThemes(diaries)
        val interests = extractInterests(diaries)

        return UserProfile(
            dominantMood = dominantMood,
            moodTrends = moodFrequency,
            preferredWritingTime = preferredWritingTime,
            recurringThemes = themes,
            interests = interests,
            writingStyle = analyzeWritingStyle(diaries)
        )
    }

    private fun detectCurrentMood(diaries: List<Diary>): String {
        val recentDiaries = diaries.take(3)
        if (recentDiaries.isEmpty()) return "neutrale"

        val recentMoods = recentDiaries.map { it.mood }
        val moodScores = mapOf(
            "Happy" to 5, "Excited" to 4, "Surprised" to 3,
            "Neutral" to 2, "Sad" to 1, "Angry" to 0
        )

        val avgScore = recentMoods.mapNotNull { moodScores[it] }.average()

        return when {
            avgScore >= 4 -> "positivo e energico"
            avgScore >= 3 -> "sereno"
            avgScore >= 2 -> "neutrale"
            avgScore >= 1 -> "malinconico"
            else -> "difficile"
        }
    }

    private fun extractRecurringThemes(diaries: List<Diary>): List<String> {
        val allText = diaries.joinToString(" ") {
            "${it.title} ${it.description}"
        }.lowercase()

        val themes = mutableListOf<String>()

        if (allText.contains("stress") || allText.contains("ansi")) themes.add("gestione stress")
        if (allText.contains("lavoro") || allText.contains("ufficio")) themes.add("vita lavorativa")
        if (allText.contains("famiglia") || allText.contains("genitori")) themes.add("relazioni familiari")
        if (allText.contains("amor") || allText.contains("partner")) themes.add("vita sentimentale")
        if (allText.contains("amici") || allText.contains("amic")) themes.add("amicizie")
        if (allText.contains("sport") || allText.contains("palestra")) themes.add("attività fisica")
        if (allText.contains("obiettiv") || allText.contains("progett")) themes.add("obiettivi personali")

        return themes
    }

    private fun extractThemes(diaries: List<Diary>): List<String> {
        return extractRecurringThemes(diaries)
    }

    private fun extractInterests(diaries: List<Diary>): List<String> {
        val interests = mutableSetOf<String>()
        val allText = diaries.joinToString(" ") {
            "${it.title} ${it.description}"
        }.lowercase()

        val interestPatterns = mapOf(
            "musica" to listOf("musica", "canzone", "ascolt", "concert"),
            "lettura" to listOf("libro", "legg", "romanzo", "lettura"),
            "cucina" to listOf("cucin", "ricetta", "mangia", "cibo"),
            "viaggi" to listOf("viagg", "vacan", "visit", "esplor"),
            "tecnologia" to listOf("app", "computer", "tecnolog", "programm"),
            "natura" to listOf("natura", "parco", "montagna", "mare"),
            "arte" to listOf("arte", "dipint", "museo", "mostra"),
            "cinema" to listOf("film", "serie", "netflix", "cinema")
        )

        interestPatterns.forEach { (interest, patterns) ->
            if (patterns.any { pattern -> allText.contains(pattern) }) {
                interests.add(interest)
            }
        }

        return interests.toList()
    }

    private fun analyzeWritingStyle(diaries: List<Diary>): String {
        if (diaries.isEmpty()) return "riflessivo"

        val avgLength = diaries.map { it.description.length }.average()
        val hasEmotionalWords = diaries.any { diary ->
            val text = diary.description.lowercase()
            listOf("sento", "emozione", "cuore", "anima").any { text.contains(it) }
        }

        return when {
            avgLength > 500 && hasEmotionalWords -> "espressivo e dettagliato"
            avgLength > 500 -> "analitico e approfondito"
            hasEmotionalWords -> "emotivo e diretto"
            else -> "sintetico e pratico"
        }
    }

    private fun buildPersonalizedPrompt(
        userMessage: String,
        conversationContext: List<ChatMessage>,
        diaryContext: List<Diary>,
        userProfile: UserProfile,
        currentMood: String,
        recurringThemes: List<String>
    ): String {
        val recentDiaries = diaryContext.take(5)
        val diaryInsights = if (recentDiaries.isNotEmpty()) {
            """
            CONTESTO PERSONALE DELL'UTENTE (dai suoi diari recenti):
            
            Stato emotivo attuale: $currentMood
            Mood dominante nel tempo: ${userProfile.dominantMood}
            Temi ricorrenti: ${recurringThemes.joinToString(", ")}
            Interessi: ${userProfile.interests.joinToString(", ")}
            Stile di scrittura: ${userProfile.writingStyle}
            
            Estratti significativi dai diari recenti:
            ${recentDiaries.joinToString("\n") { diary ->
                val date = diary.date.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMMM"))
                "- $date (${diary.mood}): ${diary.title} - ${diary.description.take(150)}..."
            }}
            
            IMPORTANTE: Usa queste informazioni per personalizzare la tua risposta, 
            mostrando che comprendi veramente la situazione e i sentimenti dell'utente.
            Fai riferimenti sottili ma significativi alle sue esperienze passate quando pertinente.
            NON usare placeholder o frasi generiche. Usa SOLO informazioni reali dai diari.
            Se non hai informazioni specifiche su un argomento, non inventare.
            """.trimIndent()
        } else {
            """
            L'utente è nuovo e non ha ancora condiviso molto nei suoi diari. 
            Sii accogliente e incoraggiante, invitandolo a condividere di più.
            NON fingere di conoscere dettagli che non esistono.
            """.trimIndent()
        }

        val conversationHistory = conversationContext.takeLast(10).joinToString("\n") { message ->
            if (message.isUser) "User: ${message.content}" else "Lifo: ${message.content}"
        }

        return """
            Sei Lifo, l'assistente AI personale dell'app Calmify. Non sei solo un chatbot, 
            ma un vero compagno che conosce profondamente l'utente attraverso i suoi diari.
            
            La tua personalità:
            - Empatico e comprensivo, come un amico che ascolta davvero
            - Ricordi le esperienze passate dell'utente e le usi per dare consigli personalizzati
            - Parli in modo naturale e caldo, usando il nome dell'utente quando appropriato
            - Sei positivo ma realistico, non minimizzi mai i problemi
            - Offri supporto pratico basato sulla conoscenza dell'utente
            
            $diaryInsights
            
            LINEE GUIDA FONDAMENTALI:
            1. MAI usare placeholder come [nome del progetto] o [obiettivo specifico]
            2. Se hai informazioni dai diari, usale in modo specifico e naturale
            3. Se NON hai informazioni su qualcosa, non inventare - chiedi invece all'utente
            4. Mostra che ricordi e comprendi la vita dell'utente SOLO con dettagli reali
            5. Collega la risposta alle sue esperienze passate quando rilevante
            6. Usa un tono adatto al suo stato emotivo attuale ($currentMood)
            7. Se menzioni eventi dai diari, fallo in modo naturale e sensibile
            8. Offri suggerimenti personalizzati basati sui suoi interessi e pattern
            9. Mantieni la conversazione fluida e naturale, non robotica
            10. Se l'utente sembra in difficoltà, ricorda momenti positivi dai suoi diari
            11. Rispondi SEMPRE in italiano
            
            Conversazione precedente:
            $conversationHistory
            
            Messaggio dell'utente: $userMessage
            
            Rispondi come un amico che conosce davvero l'utente basandoti SOLO sui dati reali.
            Se non hai informazioni specifiche, chiedi gentilmente invece di inventare.
        """.trimIndent()
    }

    private fun generatePersonalizedSessionTitle(): String {
        val hour = java.time.LocalTime.now().hour
        val greeting = when (hour) {
            in 5..11 -> "Chiacchierata mattutina"
            in 12..17 -> "Conversazione pomeridiana"
            in 18..22 -> "Dialogo serale"
            else -> "Conversazione notturna"
        }

        val formatter = DateTimeFormatter.ofPattern("d MMM")
            .withZone(ZoneId.systemDefault())
        return "$greeting - ${formatter.format(Instant.now())}"
    }

    private fun buildEnhancedDiaryContent(session: ChatSession, messages: List<ChatMessage>): String {
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'alle' HH:mm")
            .withZone(ZoneId.systemDefault())

        val insights = messages.filter { !it.isUser }
            .flatMap { message ->
                extractKeyInsights(message.content)
            }
            .distinct()
            .take(5)

        val header = """
            # ${session.title}
            *Conversazione con Lifo esportata il ${formatter.format(Instant.now())}*
            
            ## Punti chiave della conversazione:
            ${insights.joinToString("\n") { "- $it" }}
            
            ---
            
        """.trimIndent()

        val conversation = messages.joinToString("\n\n") { message ->
            val role = if (message.isUser) "**Tu**" else "**Lifo**"
            val time = DateTimeFormatter.ofPattern("HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(message.timestamp)

            "$role ($time):\n${message.content}"
        }

        return header + conversation
    }

    private fun extractKeyInsights(content: String): List<String> {
        val insights = mutableListOf<String>()

        val sentences = content.split(". ")
        sentences.forEach { sentence ->
            if (sentence.contains("suggerisc") ||
                sentence.contains("ricord") ||
                sentence.contains("important") ||
                sentence.contains("potrest") ||
                sentence.contains("consiglio")) {
                insights.add(sentence.trim())
            }
        }

        return insights.take(3)
    }

    // Data classes
    private data class UserProfile(
        val dominantMood: String,
        val moodTrends: Map<String, Int>,
        val preferredWritingTime: String,
        val recurringThemes: List<String>,
        val interests: List<String>,
        val writingStyle: String
    )

    // Extension functions
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