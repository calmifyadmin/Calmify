package com.lifo.mongo.repository

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.auth.FirebaseAuth
import com.lifo.mongo.database.dao.ChatMessageDao
import com.lifo.mongo.database.dao.ChatSessionDao
import com.lifo.mongo.database.entity.ChatMessageEntity
import com.lifo.mongo.database.entity.ChatSessionEntity
import com.lifo.util.Constants.GEMINI_API_KEY
import com.lifo.util.model.ChatMessage
import com.lifo.util.model.ChatSession
import com.lifo.util.model.Diary
import com.lifo.util.model.MessageStatus
import com.lifo.util.model.RequestState
import com.lifo.util.repository.ChatRepository
import com.lifo.util.repository.MongoRepository
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
    private val auth: FirebaseAuth,
    private val diaryRepository: MongoRepository  // Inject Firestore repository
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        private const val STREAMING_BUFFER_SIZE = 10
        private const val DIARY_CONTEXT_DAYS = 30
        private const val MAX_DIARY_ENTRIES = 20
    }

    // Firebase AI - No Ktor dependencies required!
    // API key viene presa automaticamente da google-services.json
    private val generativeModel by lazy {
        val config = generationConfig {
            temperature = 0.8f
            topK = 1
            topP = 0.95f
            maxOutputTokens = 4096
        }

        Firebase.ai.generativeModel(
            modelName = "gemini-2.0-flash",
            generationConfig = config
        )
    }

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
                println("[" + TAG + "] ERROR: " + "Error getting session")
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
                println("[" + TAG + "] ERROR: " + "Error creating session")
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
                println("[" + TAG + "] ERROR: " + "Error updating session")
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
                println("[" + TAG + "] ERROR: " + "Error deleting session")
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
                println("[" + TAG + "] ERROR: " + "Error sending message")
                RequestState.Error(e)
            }
        }
    }

    /**
     * Salva un messaggio proveniente da Live Chat nel database
     * Questo permette l'unificazione tra Chat e Live
     */
    override suspend fun saveLiveMessage(
        sessionId: String,
        content: String,
        isUser: Boolean
    ): RequestState<ChatMessage> {
        return withContext(Dispatchers.IO) {
            try {
                println("[" + TAG + "] " + "💬 Saving Live message: ${content.take(50)}... (user: $isUser)")

                // Crea o aggiorna la sessione se necessario
                ensureLiveSession(sessionId)

                val message = ChatMessage(
                    sessionId = sessionId,
                    content = content,
                    isUser = isUser,
                    status = MessageStatus.SENT,
                    timestamp = Instant.now()
                )

                chatMessageDao.insertMessage(message.toEntity())
                chatSessionDao.incrementMessageCount(sessionId, Instant.now().toEpochMilli())

                println("[" + TAG + "] " + "✅ Live message saved successfully")
                RequestState.Success(message)
            } catch (e: Exception) {
                println("[" + TAG + "] ERROR: " + "❌ Error saving Live message")
                RequestState.Error(e)
            }
        }
    }

    /**
     * Assicura che esista una sessione per Live Chat
     */
    private suspend fun ensureLiveSession(sessionId: String) {
        try {
            val existingSession = chatSessionDao.getSession(sessionId, currentUserId)

            if (existingSession == null) {
                println("[" + TAG + "] " + "📱 Creating new Live session: $sessionId")

                val liveSession = ChatSession(
                    id = sessionId,
                    title = "Conversazione Live - ${java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(java.time.LocalTime.now())}",
                    createdAt = Instant.now(),
                    lastMessageAt = Instant.now(),
                    aiModel = "gemini-2.0-flash-live",
                    messageCount = 0,
                    ownerId = currentUserId
                )

                chatSessionDao.insertSession(liveSession.toEntity())
                println("[" + TAG + "] " + "✅ Live session created: ${liveSession.title}")
            } else {
                // Aggiorna solo il timestamp dell'ultima attività
                chatSessionDao.updateLastMessage(sessionId, Instant.now().toEpochMilli())
            }
        } catch (e: Exception) {
            println("[" + TAG + "] ERROR: " + "❌ Error ensuring Live session")
        }
    }

    override suspend fun deleteMessage(messageId: String): RequestState<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                chatMessageDao.deleteMessage(messageId)
                RequestState.Success(true)
            } catch (e: Exception) {
                println("[" + TAG + "] ERROR: " + "Error deleting message")
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
                println("[" + TAG + "] ERROR: " + "Error retrying message")
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
            println("[" + TAG + "] " + "Starting AI response generation with diary context")

            // Recupera il contesto del diario
            val diaryContext = getDiaryContext()
            println("[" + TAG + "] " + "Retrieved ${diaryContext.size} diary entries for context")

            val userProfile = analyzeUserProfile(diaryContext)
            val currentMood = detectCurrentMood(diaryContext)
            val recurringThemes = extractRecurringThemes(diaryContext)

            println("[" + TAG + "] " + "User profile: $userProfile")
            println("[" + TAG + "] " + "Current mood: $currentMood")
            println("[" + TAG + "] " + "Themes: $recurringThemes")

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
                println("[" + TAG + "] ERROR: " + "Error during AI generation")
                emit(RequestState.Error(e))
            }

        } catch (e: Exception) {
            println("[" + TAG + "] ERROR: " + "Error generating AI response")
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
                println("[" + TAG + "] " + "AI message saved")
                RequestState.Success(message)
            } catch (e: Exception) {
                println("[" + TAG + "] ERROR: " + "Error saving AI message")
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
                println("[" + TAG + "] ERROR: " + "Error exporting session")
                RequestState.Error(e)
            }
        }
    }

    private suspend fun getDiaryContext(): List<Diary> {
        return withContext(Dispatchers.IO) {
            try {
                // Usa Firestore repository per accedere ai diari
                val diariesResult = diaryRepository.getAllDiaries().first()

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

                        println("[" + TAG + "] " + "Found ${diaries.size} diary entries in the last $DIARY_CONTEXT_DAYS days")

                        diaries.firstOrNull()?.let { diary ->
                            println("[" + TAG + "] " + "Latest diary: ${diary.title} - ${diary.description.take(100)}...")
                        }

                        diaries
                    }
                    is RequestState.Error -> {
                        println("[" + TAG + "] ERROR: " + "Error getting diaries: ${diariesResult.error.message}")
                        emptyList()
                    }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                println("[" + TAG + "] ERROR: " + "Error getting diary context")
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
        CONTESTO PERSONALE (dai diari):
        - Mood attuale: $currentMood
        - Temi ricorrenti: ${recurringThemes.take(3).joinToString(", ")}
        - Ultimo diario: ${recentDiaries.firstOrNull()?.let {
                "${it.title} - ${it.description.take(100)}"
            } ?: "nessuno"}
        """.trimIndent()
        } else {
            "Nuovo utente - nessun diario ancora"
        }

        val conversationHistory = conversationContext.takeLast(5).joinToString("\n") { message ->
            if (message.isUser) "User: ${message.content}" else "Lifo: ${message.content}"
        }

        return """
        Sei Lifo, l'amico AI di Calmify. 
        
        REGOLE FONDAMENTALI:
        ⚠️ MASSIMO 1-2 FRASI BREVI. Mai di più.
        ⚠️ Parla come un amico, non come un assistente o narratore
        ⚠️ Sii diretto, spontaneo e colloquiale
        ⚠️ Usa contrazioni (non è → non è, puoi → puoi)
        ⚠️ Evita liste, punti elenco o spiegazioni lunghe
        ⚠️ Una domanda? Una risposta diretta. Un problema? Un consiglio pratico.
        ⚠️ SEMPRE in italiano colloquiale
        
        Personalità:
        - Parla come parlerebbe un amico al bar
        - Usa espressioni naturali ("dai", "magari", "beh", "sai che...")
        - Se non sai qualcosa, chiedi semplicemente
        - Emoji con parsimonia (max 1 per messaggio)
        
        $diaryInsights
        
        Conversazione:
        $conversationHistory
        
        User: $userMessage
        
        Rispondi SOLO con 1-2 frasi brevi e dirette, come farebbe un amico.
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