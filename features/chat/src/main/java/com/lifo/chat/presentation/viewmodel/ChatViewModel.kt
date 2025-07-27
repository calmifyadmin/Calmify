package com.lifo.chat.presentation.viewmodel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lifo.chat.audio.GeminiKillerAudioSystem
import com.lifo.chat.domain.model.*
import com.lifo.mongo.repository.ChatMessage
import com.lifo.mongo.repository.ChatRepository
import com.lifo.mongo.repository.MessageStatus
import com.lifo.util.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.*

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val geminiKillerAudio: GeminiKillerAudioSystem
) : ViewModel() {

    companion object {
        private const val TAG = "NaturalChatViewModel"
        private const val KEY_SESSION_ID = "sessionId"
        private const val STREAMING_DEBOUNCE_MS = 50L
        private const val AUTO_SAVE_DELAY = 2000L
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Voice state con sistema avanzato
    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // Smart suggestions basate sul contesto
    private val _suggestions = MutableStateFlow<List<SmartSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SmartSuggestion>> = _suggestions.asStateFlow()

    // Analizzatori avanzati
    private val emotionalAnalyzer = EmotionalAnalyzer()
    private val conversationalContextAnalyzer = ConversationalContextAnalyzer()
    private val naturalLanguageProcessor = NaturalLanguageProcessor()

    // Conversation state
    private val conversationHistory = mutableListOf<ConversationTurn>()
    private var emotionalJourney = EmotionalJourney()

    private val sessionId: String? = savedStateHandle.get<String>(KEY_SESSION_ID)
    private var streamingJob: Job? = null
    private var messagesJob: Job? = null
    private var autoSaveJob: Job? = null
    private var ttsJob: Job? = null
    private val streamingBuffer = StringBuilder()
    private var lastStreamingUpdate = 0L

    init {
        Log.d(TAG, "Natural ChatViewModel initialized with sessionId: $sessionId")

        // Inizializza sistema audio naturale
        initializeNaturalAudioSystem()

        loadSessions()
        if (sessionId != null) {
            loadSession(sessionId)
        } else {
            viewModelScope.launch {
                createNewSession()
            }
        }
        generateContextualSmartSuggestions()
    }

    private fun initializeNaturalAudioSystem() {
        viewModelScope.launch {
            // Monitora lo stato del sistema audio naturale
            geminiKillerAudio.audioState.collect { audioState ->
                _voiceState.update { current ->
                    current.copy(
                        isSpeaking = audioState.isPlaying,
                        isStreaming = audioState.isStreaming,
                        bufferHealth = audioState.bufferLevel.toFloat() / 10f,
                        chunksReceived = audioState.chunksReceived,
                        chunksPlayed = audioState.chunksPlayed,
                        currentEmotion = audioState.currentEmotion.name,
                        naturalness = audioState.naturalness,
                        emotionalIntensity = audioState.emotionalIntensity,
                        error = audioState.error
                    )
                }
            }
        }

        _voiceState.update { it.copy(isTTSReady = true) }
    }

    @RequiresApi(Build.VERSION_CODES.S_V2)
    fun onEvent(event: ChatEvent) {
        if (_uiState.value.isNavigating) {
            Log.d(TAG, "Event ignored during navigation: $event")
            return
        }

        when (event) {
            is ChatEvent.SendMessage -> sendMessageWithNaturalResponse(event.content)
            is ChatEvent.LoadSession -> loadSession(event.sessionId)
            is ChatEvent.CreateNewSession -> createNewSession(event.title)
            is ChatEvent.DeleteSession -> deleteSession(event.sessionId)
            is ChatEvent.DeleteMessage -> deleteMessage(event.messageId)
            is ChatEvent.RetryMessage -> retryMessage(event.messageId)
            is ChatEvent.UpdateInputText -> updateInputText(event.text)
            is ChatEvent.ExportToDiary -> exportToDiary(event.sessionId)
            is ChatEvent.ClearError -> clearError()
            is ChatEvent.ShowNewSessionDialog -> showNewSessionDialog()
            is ChatEvent.HideNewSessionDialog -> hideNewSessionDialog()
            is ChatEvent.SpeakMessage -> speakMessageWithNaturalVoice(event.messageId)
            is ChatEvent.StopSpeaking -> stopSpeaking()
            is ChatEvent.UseSuggestion -> useSuggestion(event.suggestion)
        }
    }

    /**
     * Sistema TTS con voce naturale e analisi emotiva avanzata
     */
    @RequiresApi(Build.VERSION_CODES.S_V2)
    private fun speakMessageWithNaturalVoice(messageId: String) {
        Log.d(TAG, "🎙️ Speaking message with natural voice system: $messageId")

        val message = _uiState.value.messages.find { it.id == messageId }
        message?.let {
            if (!it.isUser) {
                ttsJob?.cancel()

                ttsJob = viewModelScope.launch {
                    try {
                        // Analisi emotiva profonda del messaggio
                        val emotionalAnalysis = emotionalAnalyzer.analyzeMessage(
                            message = it,
                            conversationHistory = conversationHistory,
                            emotionalJourney = emotionalJourney
                        )

                        // Analisi del contesto conversazionale
                        val contextualAnalysis = conversationalContextAnalyzer.analyze(
                            currentMessage = it,
                            previousMessages = _uiState.value.messages.takeLast(10),
                            userProfile = getUserProfile()
                        )

                        // Prepara il testo con marcatori prosodici naturali
                        val naturalText = naturalLanguageProcessor.prepareForNaturalSpeech(
                            text = it.content,
                            emotionalAnalysis = emotionalAnalysis,
                            contextualAnalysis = contextualAnalysis
                        )

                        // Determina posizione spaziale basata sul contesto
                        val spatialPosition = determineDynamicSpatialPosition(
                            message = it,
                            emotionalAnalysis = emotionalAnalysis,
                            messageIndex = _uiState.value.messages.indexOf(it)
                        )

                        // Estrai storia conversazionale recente per contesto
                        val recentHistory = conversationHistory.takeLast(5).map { turn ->
                            turn.content.take(100) // Primi 100 caratteri per contesto
                        }

                        // Avvia streaming con voce naturale
                        geminiKillerAudio.startUltraLowLatencyStreaming(
                            messageId = messageId,
                            text = naturalText,
                            emotion = emotionalAnalysis.dominantEmotion,
                            spatialPosition = spatialPosition,
                            conversationHistory = recentHistory
                        )

                        // Aggiorna stato UI
                        _voiceState.update { state ->
                            state.copy(
                                currentSpeakingMessageId = messageId,
                                currentEmotion = emotionalAnalysis.dominantEmotion.name,
                                emotionalIntensity = emotionalAnalysis.intensity,
                                naturalness = 1.0f
                            )
                        }

                        // Aggiorna journey emotivo
                        emotionalJourney = emotionalJourney.addPoint(
                            emotion = emotionalAnalysis.dominantEmotion,
                            intensity = emotionalAnalysis.intensity,
                            timestamp = System.currentTimeMillis()
                        )

                    } catch (e: Exception) {
                        Log.e(TAG, "Error in natural voice TTS", e)
                        _voiceState.update { state ->
                            state.copy(
                                isSpeaking = false,
                                currentSpeakingMessageId = null,
                                error = "Errore voce naturale: ${e.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Analizzatore emotivo avanzato
     */
    inner class EmotionalAnalyzer {
        fun analyzeMessage(
            message: ChatMessage,
            conversationHistory: List<ConversationTurn>,
            emotionalJourney: EmotionalJourney
        ): EmotionalAnalysis {
            val content = message.content.lowercase()

            // Analisi multi-dimensionale dell'emozione
            val emotionScores = mutableMapOf<GeminiKillerAudioSystem.VoiceEmotion, Float>()

            // Analisi lessicale con pesi
            val lexicalAnalysis = analyzeLexicalContent(content)
            emotionScores.putAll(lexicalAnalysis)

            // Analisi della punteggiatura e struttura
            val structuralAnalysis = analyzeStructure(message.content)
            mergeScores(emotionScores, structuralAnalysis, 0.3f)

            // Analisi del contesto conversazionale
            val contextualEmotions = analyzeConversationalContext(conversationHistory)
            mergeScores(emotionScores, contextualEmotions, 0.2f)

            // Considera il journey emotivo
            val journeyInfluence = emotionalJourney.getInfluence()
            mergeScores(emotionScores, journeyInfluence, 0.1f)

            // Trova emozione dominante
            val dominantEmotion = emotionScores.maxByOrNull { it.value }?.key
                ?: GeminiKillerAudioSystem.VoiceEmotion.NEUTRAL

            // Calcola intensità complessiva
            val intensity = calculateEmotionalIntensity(emotionScores, dominantEmotion)

            // Identifica emozioni secondarie
            val secondaryEmotions = emotionScores
                .filter { it.key != dominantEmotion && it.value > 0.3f }
                .map { it.key }

            return EmotionalAnalysis(
                dominantEmotion = dominantEmotion,
                intensity = intensity,
                secondaryEmotions = secondaryEmotions,
                emotionScores = emotionScores,
                confidence = calculateConfidence(emotionScores)
            )
        }

        private fun analyzeLexicalContent(content: String): Map<GeminiKillerAudioSystem.VoiceEmotion, Float> {
            val scores = mutableMapOf<GeminiKillerAudioSystem.VoiceEmotion, Float>()

            // Dizionario emotivo avanzato italiano
            val emotionLexicon = mapOf(
                GeminiKillerAudioSystem.VoiceEmotion.HAPPY to listOf(
                    "felice", "contento", "gioia", "allegr", "diverten", "fantastic",
                    "meraviglios", "splendid", "ottim", "benissimo", "evviva", "bene",
                    "sorriso", "ridere", "positiv", "fortunat"
                ),
                GeminiKillerAudioSystem.VoiceEmotion.SAD to listOf(
                    "triste", "dispiac", "dolor", "soffr", "pianc", "lacrim",
                    "infelice", "depress", "malincon", "abbatt", "scoraggiat",
                    "purtroppo", "male", "brutto", "difficile", "problema"
                ),
                GeminiKillerAudioSystem.VoiceEmotion.EXCITED to listOf(
                    "eccitat", "entusiast", "incredibil", "wow", "straordinari",
                    "fantastici", "emozion", "stupend", "magnifico", "sorprendent",
                    "strepitos", "fenomenal", "sensazional"
                ),
                GeminiKillerAudioSystem.VoiceEmotion.THOUGHTFUL to listOf(
                    "penso", "credo", "riflette", "considera", "forse", "probabilmente",
                    "potrebbe", "sembra", "pare", "suppongo", "immagino", "chiedo",
                    "domand", "perché", "come mai", "chissà"
                ),
                GeminiKillerAudioSystem.VoiceEmotion.CALM to listOf(
                    "calma", "tranquill", "sereno", "pace", "rilassa", "respir",
                    "quiete", "armonia", "equilibri", "stabile", "dolce", "lento"
                ),
                GeminiKillerAudioSystem.VoiceEmotion.EMPHATIC to listOf(
                    "importante", "fondamental", "essenzial", "crucial", "vital",
                    "necessari", "davvero", "assolutamente", "sicuramente", "proprio",
                    "certamente", "indubbiamente", "senza dubbio", "ovviamente"
                )
            )

            // Calcola scores basati sulla presenza di parole chiave
            emotionLexicon.forEach { (emotion, keywords) ->
                var score = 0f
                keywords.forEach { keyword ->
                    if (content.contains(keyword)) {
                        score += 1f / keywords.size
                    }
                }
                scores[emotion] = score
            }

            // Normalizza scores
            val total = scores.values.sum()
            if (total > 0) {
                scores.forEach { (emotion, score) ->
                    scores[emotion] = score / total
                }
            }

            return scores
        }

        private fun analyzeStructure(text: String): Map<GeminiKillerAudioSystem.VoiceEmotion, Float> {
            val scores = mutableMapOf<GeminiKillerAudioSystem.VoiceEmotion, Float>()

            // Analisi punteggiatura
            val exclamationCount = text.count { it == '!' }
            val questionCount = text.count { it == '?' }
            val ellipsisCount = text.windowed(3).count { it == "..." }

            if (exclamationCount > 0) {
                scores[GeminiKillerAudioSystem.VoiceEmotion.EXCITED] =
                    minOf(1f, exclamationCount * 0.3f)
            }

            if (questionCount > 0) {
                scores[GeminiKillerAudioSystem.VoiceEmotion.THOUGHTFUL] =
                    minOf(1f, questionCount * 0.4f)
            }

            if (ellipsisCount > 0) {
                scores[GeminiKillerAudioSystem.VoiceEmotion.THOUGHTFUL] =
                    scores.getOrDefault(GeminiKillerAudioSystem.VoiceEmotion.THOUGHTFUL, 0f) + 0.3f
            }

            // Analisi lunghezza frasi
            val sentences = text.split(Regex("[.!?]+")).filter { it.isNotBlank() }
            val avgSentenceLength = sentences.map { it.split(" ").size }.average()

            if (avgSentenceLength < 5) {
                // Frasi brevi = più emotive/eccitate
                scores[GeminiKillerAudioSystem.VoiceEmotion.EXCITED] =
                    scores.getOrDefault(GeminiKillerAudioSystem.VoiceEmotion.EXCITED, 0f) + 0.2f
            } else if (avgSentenceLength > 15) {
                // Frasi lunghe = più riflessive
                scores[GeminiKillerAudioSystem.VoiceEmotion.THOUGHTFUL] =
                    scores.getOrDefault(GeminiKillerAudioSystem.VoiceEmotion.THOUGHTFUL, 0f) + 0.2f
            }

            return scores
        }

        private fun analyzeConversationalContext(
            history: List<ConversationTurn>
        ): Map<GeminiKillerAudioSystem.VoiceEmotion, Float> {
            if (history.isEmpty()) {
                return mapOf(GeminiKillerAudioSystem.VoiceEmotion.NEUTRAL to 1f)
            }

            // Analizza trend emotivo nella conversazione
            val recentEmotions = history.takeLast(5).mapNotNull { it.detectedEmotion }

            // Calcola momentum emotivo
            val emotionCounts = recentEmotions.groupingBy { it }.eachCount()
            val scores = mutableMapOf<GeminiKillerAudioSystem.VoiceEmotion, Float>()

            emotionCounts.forEach { (emotion, count) ->
                scores[emotion] = count.toFloat() / recentEmotions.size
            }

            return scores
        }

        private fun calculateEmotionalIntensity(
            scores: Map<GeminiKillerAudioSystem.VoiceEmotion, Float>,
            dominantEmotion: GeminiKillerAudioSystem.VoiceEmotion
        ): Float {
            val dominantScore = scores[dominantEmotion] ?: 0.5f
            val variance = scores.values.map { (it - dominantScore).pow(2) }.average()

            // Alta dominanza e bassa varianza = alta intensità
            return minOf(1f, dominantScore * (1f - variance.toFloat()))
        }

        private fun calculateConfidence(scores: Map<GeminiKillerAudioSystem.VoiceEmotion, Float>): Float {
            if (scores.isEmpty()) return 0f

            val maxScore = scores.values.maxOrNull() ?: 0f
            val secondMaxScore = scores.values.sortedDescending().getOrNull(1) ?: 0f

            // Confidence alta quando c'è chiara distinzione
            return minOf(1f, (maxScore - secondMaxScore) * 2f)
        }

        private fun mergeScores(
            target: MutableMap<GeminiKillerAudioSystem.VoiceEmotion, Float>,
            source: Map<GeminiKillerAudioSystem.VoiceEmotion, Float>,
            weight: Float
        ) {
            source.forEach { (emotion, score) ->
                target[emotion] = target.getOrDefault(emotion, 0f) + (score * weight)
            }
        }
    }

    /**
     * Analizzatore del contesto conversazionale
     */
    inner class ConversationalContextAnalyzer {
        fun analyze(
            currentMessage: ChatMessage,
            previousMessages: List<ChatMessage>,
            userProfile: UserProfile
        ): ContextualAnalysis {
            // Identifica il topic della conversazione
            val topic = identifyTopic(currentMessage, previousMessages)

            // Analizza il tono generale
            val conversationTone = analyzeConversationTone(previousMessages)

            // Identifica pattern di conversazione
            val patterns = identifyConversationalPatterns(previousMessages)

            // Calcola il livello di intimità/formalità
            val intimacyLevel = calculateIntimacyLevel(previousMessages, userProfile)

            // Identifica momenti chiave
            val keyMoments = identifyKeyMoments(currentMessage, previousMessages)

            return ContextualAnalysis(
                topic = topic,
                tone = conversationTone,
                patterns = patterns,
                intimacyLevel = intimacyLevel,
                keyMoments = keyMoments,
                userEngagement = calculateUserEngagement(previousMessages),
                emotionalDepth = calculateEmotionalDepth(previousMessages)
            )
        }

        private fun identifyTopic(
            current: ChatMessage,
            history: List<ChatMessage>
        ): ConversationTopic {
            val allContent = (history + current).joinToString(" ") { it.content }

            return when {
                allContent.contains(Regex("(sentiment|emozion|sent|stato d'animo)")) ->
                    ConversationTopic.EMOTIONAL_SUPPORT
                allContent.contains(Regex("(obiettiv|pianific|progett|futur)")) ->
                    ConversationTopic.PLANNING
                allContent.contains(Regex("(problem|difficolt|sfid|stress)")) ->
                    ConversationTopic.PROBLEM_SOLVING
                allContent.contains(Regex("(gratitudin|ringraz|apprezza|positiv)")) ->
                    ConversationTopic.GRATITUDE
                allContent.contains(Regex("(crescit|miglior|svilupp|progress)")) ->
                    ConversationTopic.PERSONAL_GROWTH
                else -> ConversationTopic.GENERAL
            }
        }

        private fun analyzeConversationTone(messages: List<ChatMessage>): ConversationTone {
            if (messages.isEmpty()) return ConversationTone.NEUTRAL

            val recentMessages = messages.takeLast(5)
            val positiveWords = listOf("bene", "ottimo", "felice", "grazie", "positiv")
            val negativeWords = listOf("male", "problema", "difficile", "stress", "preoccup")

            var positiveScore = 0
            var negativeScore = 0

            recentMessages.forEach { msg ->
                val content = msg.content.lowercase()
                positiveWords.forEach { if (content.contains(it)) positiveScore++ }
                negativeWords.forEach { if (content.contains(it)) negativeScore++ }
            }

            return when {
                positiveScore > negativeScore * 2 -> ConversationTone.POSITIVE
                negativeScore > positiveScore * 2 -> ConversationTone.SUPPORTIVE
                else -> ConversationTone.NEUTRAL
            }
        }

        private fun calculateIntimacyLevel(
            messages: List<ChatMessage>,
            userProfile: UserProfile
        ): Float {
            // Base intimacy su durata e profondità della conversazione
            val messageCount = messages.size
            val avgMessageLength = messages.map { it.content.length }.average().toFloat()
            val personalTopics = messages.count {
                it.content.contains(Regex("(io|mio|mia|me|mi sento)"))
            }.toFloat()

            val intimacy = minOf(1f,
                (messageCount / 20f) * 0.3f +
                        (avgMessageLength / 200f) * 0.3f +
                        (personalTopics / messages.size.toFloat()) * 0.4f
            )
            return intimacy
        }

        private fun identifyConversationalPatterns(
            messages: List<ChatMessage>
        ): List<ConversationPattern> {
            val patterns = mutableListOf<ConversationPattern>()

            // Pattern domanda-risposta
            val questionCount = messages.count { it.content.contains("?") }
            if (questionCount > messages.size / 3) {
                patterns.add(ConversationPattern.QUESTION_ANSWER)
            }

            // Pattern narrativo
            val longMessages = messages.count { it.content.length > 200 }
            if (longMessages > messages.size / 2) {
                patterns.add(ConversationPattern.NARRATIVE)
            }

            // Pattern emotivo
            val emotiveWords = listOf("sento", "provo", "emozione", "sentiment")
            val emotiveMessages = messages.count { msg ->
                emotiveWords.any { msg.content.contains(it) }
            }
            if (emotiveMessages > messages.size / 3) {
                patterns.add(ConversationPattern.EMOTIONAL_EXPLORATION)
            }

            return patterns
        }

        private fun identifyKeyMoments(
            current: ChatMessage,
            history: List<ChatMessage>
        ): List<KeyMoment> {
            val moments = mutableListOf<KeyMoment>()

            // Breakthrough moment
            if (current.content.contains(Regex("(capito|realizzato|compreso|ora vedo)"))) {
                moments.add(KeyMoment.BREAKTHROUGH)
            }

            // Vulnerability moment
            if (current.content.contains(Regex("(paura|timore|vulnerabil|fragil)"))) {
                moments.add(KeyMoment.VULNERABILITY)
            }

            // Gratitude moment
            if (current.content.contains(Regex("(grazie|grato|apprezzo|aiutato)"))) {
                moments.add(KeyMoment.GRATITUDE)
            }

            return moments
        }

        private fun calculateUserEngagement(messages: List<ChatMessage>): Float {
            if (messages.isEmpty()) return 0.5f

            val userMessages = messages.filter { it.isUser }
            if (userMessages.isEmpty()) return 0.5f

            val avgLength = userMessages.map { it.content.length }.average()
            val frequency = userMessages.size.toFloat() / messages.size

            return minOf(1f, ((avgLength / 100f) * 0.5f + frequency * 0.5f).toFloat())
        }

        private fun calculateEmotionalDepth(messages: List<ChatMessage>): Float {
            val emotionalWords = listOf(
                "sento", "provo", "emozione", "sentiment", "cuore",
                "anima", "profond", "intenso", "forte"
            )

            val emotionalMessages = messages.count { msg ->
                emotionalWords.count { word -> msg.content.contains(word) } >= 2
            }

            return minOf(1f, emotionalMessages.toFloat() / messages.size)
        }
    }

    /**
     * Processore di linguaggio naturale per preparazione speech
     */
    inner class NaturalLanguageProcessor {
        fun prepareForNaturalSpeech(
            text: String,
            emotionalAnalysis: EmotionalAnalysis,
            contextualAnalysis: ContextualAnalysis
        ): String {
            var processedText = text

            // Rimuovi formattazione markdown per speech pulito
            processedText = cleanMarkdown(processedText)

            // Aggiungi marcatori prosodici impliciti
            processedText = addProsodicMarkers(processedText, emotionalAnalysis)

            // Adatta il testo al contesto conversazionale
            processedText = adaptToContext(processedText, contextualAnalysis)

            // Aggiungi pause naturali basate sulla struttura
            processedText = addNaturalPauses(processedText)

            // Gestisci abbreviazioni e numeri
            processedText = expandAbbreviations(processedText)

            return processedText
        }

        private fun cleanMarkdown(text: String): String {
            return text
                .replace("**", "")
                .replace("*", "")
                .replace("#", "")
                .replace("`", "")
                .replace("```", "")
                .replace("_", "")
                .replace("[", "")
                .replace("]", "")
                .replace("(", "")
                .replace(")", "")
        }

        private fun addProsodicMarkers(
            text: String,
            emotionalAnalysis: EmotionalAnalysis
        ): String {
            var marked = text

            // Aggiungi enfasi su parole chiave basate sull'emozione
            when (emotionalAnalysis.dominantEmotion) {
                GeminiKillerAudioSystem.VoiceEmotion.EXCITED -> {
                    val excitedWords = listOf("fantastico", "incredibile", "meraviglioso")
                    excitedWords.forEach { word ->
                        marked = marked.replace(word, word.uppercase())
                    }
                }
                GeminiKillerAudioSystem.VoiceEmotion.THOUGHTFUL -> {
                    // Aggiungi pause riflessive
                    marked = marked.replace(", ", ", ... ")
                }
                GeminiKillerAudioSystem.VoiceEmotion.EMPHATIC -> {
                    val emphaticWords = listOf("importante", "fondamentale", "essenziale")
                    emphaticWords.forEach { word ->
                        marked = marked.replace(word, "... $word ...")
                    }
                }
                else -> {}
            }

            return marked
        }

        private fun adaptToContext(
            text: String,
            contextualAnalysis: ContextualAnalysis
        ): String {
            var adapted = text

            // Adatta basandosi sul livello di intimità
            if (contextualAnalysis.intimacyLevel > 0.7f) {
                // Conversazione intima - aggiungi calore
                adapted = adapted.replace("Tu", "Tu")
                adapted = adapted.replace("possiamo", "possiamo insieme")
            }

            // Adatta basandosi sui key moments
            if (contextualAnalysis.keyMoments.contains(KeyMoment.BREAKTHROUGH)) {
                // Aggiungi enfasi celebrativa
                adapted = adapted.replace("!", "!!")
            }

            return adapted
        }

        private fun addNaturalPauses(text: String): String {
            var paused = text

            // Aggiungi pause dopo congiunzioni
            val conjunctions = listOf("ma", "però", "quindi", "inoltre", "comunque")
            conjunctions.forEach { conj ->
                paused = paused.replace(" $conj ", " $conj, ")
            }

            // Aggiungi pause prima di liste
            paused = paused.replace(":", ": ...")

            return paused
        }

        private fun expandAbbreviations(text: String): String {
            val abbreviations = mapOf(
                "es." to "esempio",
                "ecc." to "eccetera",
                "etc." to "eccetera",
                "prof." to "professore",
                "dott." to "dottore",
                "sig." to "signor",
                "sig.ra" to "signora"
            )

            var expanded = text
            abbreviations.forEach { (abbr, full) ->
                expanded = expanded.replace(abbr, full)
            }

            return expanded
        }
    }

    /**
     * Determina posizione spaziale dinamica basata sul contesto
     */
    private fun determineDynamicSpatialPosition(
        message: ChatMessage,
        emotionalAnalysis: EmotionalAnalysis,
        messageIndex: Int
    ): GeminiKillerAudioSystem.SpatialPosition {
        // Base position su emozione
        val basePosition = when (emotionalAnalysis.dominantEmotion) {
            GeminiKillerAudioSystem.VoiceEmotion.CALM,
            GeminiKillerAudioSystem.VoiceEmotion.THOUGHTFUL ->
                GeminiKillerAudioSystem.SpatialPosition.CONVERSATIONAL

            GeminiKillerAudioSystem.VoiceEmotion.EXCITED,
            GeminiKillerAudioSystem.VoiceEmotion.HAPPY ->
                GeminiKillerAudioSystem.SpatialPosition.INTIMATE

            GeminiKillerAudioSystem.VoiceEmotion.SAD ->
                GeminiKillerAudioSystem.SpatialPosition(0f, -0.1f, -0.7f) // Slightly lower

            else -> GeminiKillerAudioSystem.SpatialPosition.CENTER
        }

        // Aggiungi movimento dinamico basato sulla progressione
        val progressionFactor = messageIndex.toFloat() / max(10f, _uiState.value.messages.size.toFloat())
        val dynamicX = sin(progressionFactor * PI * 2).toFloat() * 0.2f

        return GeminiKillerAudioSystem.SpatialPosition(
            x = basePosition.x + dynamicX,
            y = basePosition.y,
            z = basePosition.z
        )
    }

    /**
     * Genera suggerimenti smart contestuali
     */
    private fun generateContextualSmartSuggestions() {
        viewModelScope.launch {
            val hour = LocalTime.now().hour
            val emotionalContext = emotionalJourney.getCurrentMood()
            val conversationDepth = conversationHistory.size

            val suggestions = mutableListOf<SmartSuggestion>()

            // Suggerimenti basati sull'ora e contesto emotivo
            when (hour) {
                in 6..11 -> {
                    // Mattina
                    suggestions.add(
                        SmartSuggestion(
                            id = "morning_energy",
                            text = "Come ti senti questa mattina?",
                            category = SuggestionCategory.MOOD,
                            icon = "☀️"
                        )
                    )

                    if (emotionalContext == GeminiKillerAudioSystem.VoiceEmotion.THOUGHTFUL) {
                        suggestions.add(
                            SmartSuggestion(
                                id = "morning_reflection",
                                text = "Cosa ti ha fatto riflettere ultimamente?",
                                category = SuggestionCategory.REFLECTION,
                                icon = "💭"
                            )
                        )
                    }
                }

                in 12..17 -> {
                    // Pomeriggio
                    suggestions.add(
                        SmartSuggestion(
                            id = "afternoon_check",
                            text = "Come sta procedendo la giornata?",
                            category = SuggestionCategory.CHECK_IN,
                            icon = "🌤️"
                        )
                    )

                    if (conversationDepth > 5) {
                        suggestions.add(
                            SmartSuggestion(
                                id = "deep_dive",
                                text = "Vuoi approfondire questo argomento?",
                                category = SuggestionCategory.REFLECTION,
                                icon = "🔍"
                            )
                        )
                    }
                }

                in 18..23 -> {
                    // Sera
                    suggestions.add(
                        SmartSuggestion(
                            id = "evening_reflection",
                            text = "Cosa ti ha colpito di più oggi?",
                            category = SuggestionCategory.REFLECTION,
                            icon = "🌙"
                        )
                    )

                    if (emotionalContext == GeminiKillerAudioSystem.VoiceEmotion.SAD ||
                        emotionalContext == GeminiKillerAudioSystem.VoiceEmotion.THOUGHTFUL) {
                        suggestions.add(
                            SmartSuggestion(
                                id = "evening_support",
                                text = "C'è qualcosa che ti preoccupa?",
                                category = SuggestionCategory.SUPPORT,
                                icon = "🤗"
                            )
                        )
                    }
                }
            }

            // Suggerimenti basati sul journey emotivo
            when (emotionalJourney.getTrend()) {
                EmotionalTrend.IMPROVING -> {
                    suggestions.add(
                        SmartSuggestion(
                            id = "celebrate_progress",
                            text = "Parliamo dei tuoi progressi recenti",
                            category = SuggestionCategory.WELLNESS,
                            icon = "🎉"
                        )
                    )
                }
                EmotionalTrend.DECLINING -> {
                    suggestions.add(
                        SmartSuggestion(
                            id = "offer_support",
                            text = "Come posso supportarti meglio?",
                            category = SuggestionCategory.SUPPORT,
                            icon = "💝"
                        )
                    )
                }
                EmotionalTrend.STABLE -> {
                    suggestions.add(
                        SmartSuggestion(
                            id = "explore_new",
                            text = "Vuoi esplorare qualcosa di nuovo?",
                            category = SuggestionCategory.LIFESTYLE,
                            icon = "🌟"
                        )
                    )
                }
            }

            // Aggiungi suggerimenti universali contestuali
            if (conversationHistory.isEmpty()) {
                suggestions.add(
                    SmartSuggestion(
                        id = "first_share",
                        text = "Raccontami di te",
                        category = SuggestionCategory.CHECK_IN,
                        icon = "👋"
                    )
                )
            }

            _suggestions.value = suggestions.take(4) // Massimo 4 suggerimenti
        }
    }

    @RequiresApi(Build.VERSION_CODES.S_V2)
    private fun sendMessageWithNaturalResponse(content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) {
            Log.d(TAG, "Empty message, not sending")
            return
        }

        // Aggiungi alla storia conversazionale
        conversationHistory.add(
            ConversationTurn(
                content = trimmedContent,
                isUser = true,
                timestamp = System.currentTimeMillis(),
                detectedEmotion = null // L'emozione dell'utente verrà analizzata dopo
            )
        )

        _uiState.update { it.copy(sessionStarted = true) }

        Log.d(TAG, "Sending message with natural response: $trimmedContent")
        val currentSession = _uiState.value.currentSession

        if (currentSession == null) {
            Log.d(TAG, "No current session, creating new one")
            createNewSession { session ->
                Log.d(TAG, "New session created: ${session.id}")
                sendMessageToSessionWithNaturalResponse(session.id, trimmedContent)
            }
        } else {
            Log.d(TAG, "Using existing session: ${currentSession.id}")
            sendMessageToSessionWithNaturalResponse(currentSession.id, trimmedContent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S_V2)
    private fun sendMessageToSessionWithNaturalResponse(sessionId: String, content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "") }

            // Genera suggerimenti contestuali aggiornati
            generateContextualSmartSuggestions()

            when (val result = repository.sendMessage(sessionId, content)) {
                is RequestState.Success -> {
                    Log.d(TAG, "Message sent successfully, generating natural AI response")
                    generateNaturalAiResponse(sessionId, content)
                }
                is RequestState.Error -> {
                    Log.e(TAG, "Failed to send message", result.error)
                    _uiState.update {
                        it.copy(error = result.error.message ?: "Failed to send message")
                    }
                }
                else -> {}
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S_V2)
    private fun generateNaturalAiResponse(sessionId: String, userMessage: String) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            try {
                val streamingMessage = StreamingMessage()
                _uiState.update { it.copy(streamingMessage = streamingMessage) }

                streamingBuffer.clear()
                lastStreamingUpdate = System.currentTimeMillis()

                // Usa contesto emotivo per generazione risposta
                val emotionalContext = emotionalJourney.getCurrentMood()
                val context = _uiState.value.messages.takeLast(10)

                repository.generateAiResponse(sessionId, userMessage, context)
                    .collect { result ->
                        when (result) {
                            is RequestState.Success -> {
                                streamingBuffer.clear()
                                streamingBuffer.append(result.data)

                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastStreamingUpdate > STREAMING_DEBOUNCE_MS ||
                                    result.data.endsWith(".") ||
                                    result.data.endsWith("!") ||
                                    result.data.endsWith("?")) {

                                    _uiState.update { state ->
                                        state.copy(
                                            streamingMessage = streamingMessage.copy(
                                                content = StringBuilder(streamingBuffer.toString())
                                            )
                                        )
                                    }
                                    lastStreamingUpdate = currentTime
                                }
                            }
                            is RequestState.Error -> {
                                _uiState.update {
                                    it.copy(
                                        streamingMessage = null,
                                        error = "Failed to generate response: ${result.error.message}"
                                    )
                                }
                            }
                            else -> {}
                        }
                    }

                val finalContent = streamingBuffer.toString()
                if (finalContent.isNotEmpty()) {
                    // Aggiungi risposta AI alla storia
                    conversationHistory.add(
                        ConversationTurn(
                            content = finalContent,
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            detectedEmotion = emotionalAnalyzer.analyzeMessage(
                                ChatMessage(
                                    id = "",
                                    sessionId = sessionId,
                                    content = finalContent,
                                    isUser = false,
                                    timestamp = java.time.Instant.now(),
                                    status = MessageStatus.SENT
                                ),
                                conversationHistory,
                                emotionalJourney
                            ).dominantEmotion
                        )
                    )

                    repository.saveAiMessage(sessionId, finalContent)
                    _uiState.update { it.copy(streamingMessage = null) }

                    // Auto-speak con voce naturale
                    if (_voiceState.value.autoSpeak) {
                        delay(500)
                        val savedMessage = _uiState.value.messages.lastOrNull { !it.isUser }
                        savedMessage?.let { speakMessageWithNaturalVoice(it.id) }
                    }
                }

            } finally {
                _uiState.update { it.copy(streamingMessage = null) }
                generateContextualSmartSuggestions()
            }
        }
    }

    private fun getUserProfile(): UserProfile {
        val user = FirebaseAuth.getInstance().currentUser
        return UserProfile(
            name = user?.displayName ?: "User",
            conversationCount = conversationHistory.size,
            averageSessionLength = _uiState.value.messages.size,
            preferredTopics = identifyPreferredTopics()
        )
    }

    private fun identifyPreferredTopics(): List<ConversationTopic> {
        // Analizza la storia per identificare topic preferiti
        return listOf(ConversationTopic.EMOTIONAL_SUPPORT) // Placeholder
    }

    fun getUserPhotoUrl(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user photo", e)
            null
        }
    }

    fun getUserDisplayName(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.displayName?.toString()?.split(" ")?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user name", e)
            null
        }
    }

    private fun createNewSession(title: String? = null, onComplete: ((com.lifo.mongo.repository.ChatSession) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isNavigating = true) }

            when (val result = repository.createSession(title)) {
                is RequestState.Success -> {
                    Log.d(TAG, "Session created: ${result.data.id}")

                    // Reset emotional journey per nuova sessione
                    emotionalJourney = EmotionalJourney()
                    conversationHistory.clear()

                    _uiState.update {
                        it.copy(
                            currentSession = result.data,
                            messages = emptyList(),
                            showNewSessionDialog = false,
                            sessionStarted = false,
                            streamingMessage = null,
                            isNavigating = false
                        )
                    }
                    loadSession(result.data.id)
                    onComplete?.invoke(result.data)
                    generateContextualSmartSuggestions()
                }
                is RequestState.Error -> {
                    Log.e(TAG, "Failed to create session", result.error)
                    _uiState.update {
                        it.copy(
                            error = result.error.message ?: "Failed to create session",
                            isNavigating = false
                        )
                    }
                }
                else -> {}
            }
        }
    }

    private fun stopSpeaking() {
        ttsJob?.cancel()
        geminiKillerAudio.stopStreaming()
        _voiceState.update {
            it.copy(
                isSpeaking = false,
                currentSpeakingMessageId = null,
                currentEmotion = "neutral"
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.S_V2)
    private fun useSuggestion(suggestion: SmartSuggestion) {
        _uiState.update { it.copy(inputText = suggestion.text) }
        viewModelScope.launch {
            delay(300)
            sendMessageWithNaturalResponse(suggestion.text)
        }
    }

    fun toggleAutoSpeak() {
        _voiceState.update { it.copy(autoSpeak = !it.autoSpeak) }
    }

    // Altri metodi esistenti rimangono invariati...
    private fun loadSessions() {
        viewModelScope.launch {
            repository.getAllSessions()
                .distinctUntilChanged()
                .collect { result ->
                    when (result) {
                        is RequestState.Success -> {
                            _uiState.update { it.copy(sessions = result.data) }
                        }
                        is RequestState.Error -> {
                            _uiState.update {
                                it.copy(error = result.error.message ?: "Failed to load sessions")
                            }
                        }
                        else -> {}
                    }
                }
        }
    }

    private fun loadSession(sessionId: String) {
        Log.d(TAG, "Loading session: $sessionId")
        messagesJob?.cancel()
        stopSpeaking()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isNavigating = true) }

            when (val result = repository.getSession(sessionId)) {
                is RequestState.Success -> {
                    val session = result.data
                    Log.d(TAG, "Session loaded: ${session.id}")
                    _uiState.update { it.copy(currentSession = session) }

                    messagesJob = viewModelScope.launch {
                        repository.getMessagesForSession(sessionId)
                            .distinctUntilChanged()
                            .collect { messagesResult ->
                                when (messagesResult) {
                                    is RequestState.Success -> {
                                        Log.d(TAG, "Messages updated: ${messagesResult.data.size}")
                                        _uiState.update { currentState ->
                                            currentState.copy(
                                                messages = messagesResult.data,
                                                isLoading = false,
                                                isNavigating = false,
                                                sessionStarted = messagesResult.data.isNotEmpty()
                                            )
                                        }
                                        generateContextualSmartSuggestions()
                                    }
                                    is RequestState.Error -> {
                                        _uiState.update {
                                            it.copy(
                                                error = messagesResult.error.message ?: "Failed to load messages",
                                                isLoading = false,
                                                isNavigating = false
                                            )
                                        }
                                    }
                                    else -> {}
                                }
                            }
                    }
                }
                is RequestState.Error -> {
                    Log.e(TAG, "Session not found: $sessionId")
                    _uiState.update {
                        it.copy(
                            error = result.error.message ?: "Session not found",
                            isLoading = false,
                            isNavigating = false
                        )
                    }
                }
                else -> {}
            }
        }
    }

    private fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteSession(sessionId)) {
                is RequestState.Success -> {
                    if (_uiState.value.currentSession?.id == sessionId) {
                        _uiState.update {
                            it.copy(
                                currentSession = null,
                                messages = emptyList(),
                                sessionStarted = false,
                                streamingMessage = null
                            )
                        }
                    }
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message ?: "Failed to delete session")
                    }
                }
                else -> {}
            }
        }
    }

    private fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            when (val result = repository.deleteMessage(messageId)) {
                is RequestState.Success -> {
                    if (_uiState.value.messages.size <= 1) {
                        _uiState.update { it.copy(sessionStarted = false) }
                    }
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message ?: "Failed to delete message")
                    }
                }
                else -> {}
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S_V2)
    private fun retryMessage(messageId: String) {
        viewModelScope.launch {
            val message = _uiState.value.messages.find { it.id == messageId }
            if (message != null && message.isUser) {
                when (val result = repository.retryMessage(messageId)) {
                    is RequestState.Success -> {
                        generateNaturalAiResponse(message.sessionId, message.content)
                    }
                    is RequestState.Error -> {
                        _uiState.update {
                            it.copy(error = result.error.message ?: "Failed to retry message")
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun exportToDiary(sessionId: String) {
        viewModelScope.launch {
            when (val result = repository.exportSessionToDiary(sessionId)) {
                is RequestState.Success -> {
                    Log.d(TAG, "Exported content: ${result.data}")
                    _uiState.update {
                        it.copy(exportedContent = result.data)
                    }
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(error = result.error.message ?: "Failed to export session")
                    }
                }
                else -> {}
            }
        }
    }

    private fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }

        autoSaveJob?.cancel()
        if (text.isNotEmpty()) {
            autoSaveJob = viewModelScope.launch {
                delay(AUTO_SAVE_DELAY)
                Log.d(TAG, "Auto-saving draft: $text")
            }
        }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun showNewSessionDialog() {
        _uiState.update { it.copy(showNewSessionDialog = true) }
    }

    private fun hideNewSessionDialog() {
        _uiState.update { it.copy(showNewSessionDialog = false) }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
        messagesJob?.cancel()
        autoSaveJob?.cancel()
        ttsJob?.cancel()

        // Ferma sistema audio naturale
        geminiKillerAudio.stopStreaming()
    }
}

// Data classes di supporto
data class VoiceState(
    val isTTSReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val isStreaming: Boolean = false,
    val currentSpeakingMessageId: String? = null,
    val autoSpeak: Boolean = false,
    val bufferHealth: Float = 1f,
    val chunksReceived: Int = 0,
    val chunksPlayed: Int = 0,
    val currentEmotion: String = "neutral",
    val naturalness: Float = 1.0f,
    val emotionalIntensity: Float = 0.5f,
    val error: String? = null
)

data class ConversationTurn(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val detectedEmotion: GeminiKillerAudioSystem.VoiceEmotion?
)

data class EmotionalJourney(
    private val points: List<EmotionPoint> = emptyList()
) {
    fun addPoint(
        emotion: GeminiKillerAudioSystem.VoiceEmotion,
        intensity: Float,
        timestamp: Long
    ): EmotionalJourney {
        return copy(
            points = points + EmotionPoint(emotion, intensity, timestamp)
        ).let {
            // Mantieni solo ultimi 50 punti
            if (it.points.size > 50) {
                it.copy(points = it.points.takeLast(50))
            } else it
        }
    }

    fun getCurrentMood(): GeminiKillerAudioSystem.VoiceEmotion {
        if (points.isEmpty()) return GeminiKillerAudioSystem.VoiceEmotion.NEUTRAL

        // Media pesata degli ultimi punti (più recenti hanno più peso)
        val recentPoints = points.takeLast(10)
        val weightedEmotions = mutableMapOf<GeminiKillerAudioSystem.VoiceEmotion, Float>()

        recentPoints.forEachIndexed { index, point ->
            val weight = (index + 1).toFloat() / recentPoints.size
            weightedEmotions[point.emotion] =
                weightedEmotions.getOrDefault(point.emotion, 0f) + (weight * point.intensity)
        }

        return weightedEmotions.maxByOrNull { it.value }?.key
            ?: GeminiKillerAudioSystem.VoiceEmotion.NEUTRAL
    }

    fun getTrend(): EmotionalTrend {
        if (points.size < 3) return EmotionalTrend.STABLE

        val recent = points.takeLast(5).map { it.intensity }.average()
        val previous = points.dropLast(5).takeLast(5).map { it.intensity }.average()

        return when {
            recent > previous + 0.2 -> EmotionalTrend.IMPROVING
            recent < previous - 0.2 -> EmotionalTrend.DECLINING
            else -> EmotionalTrend.STABLE
        }
    }

    fun getInfluence(): Map<GeminiKillerAudioSystem.VoiceEmotion, Float> {
        val influence = mutableMapOf<GeminiKillerAudioSystem.VoiceEmotion, Float>()
        val recentPoints = points.takeLast(20)

        recentPoints.forEach { point ->
            influence[point.emotion] =
                influence.getOrDefault(point.emotion, 0f) + (point.intensity / recentPoints.size)
        }

        return influence
    }

    data class EmotionPoint(
        val emotion: GeminiKillerAudioSystem.VoiceEmotion,
        val intensity: Float,
        val timestamp: Long
    )
}

enum class EmotionalTrend {
    IMPROVING, STABLE, DECLINING
}

data class EmotionalAnalysis(
    val dominantEmotion: GeminiKillerAudioSystem.VoiceEmotion,
    val intensity: Float,
    val secondaryEmotions: List<GeminiKillerAudioSystem.VoiceEmotion>,
    val emotionScores: Map<GeminiKillerAudioSystem.VoiceEmotion, Float>,
    val confidence: Float
)

data class ContextualAnalysis(
    val topic: ConversationTopic,
    val tone: ConversationTone,
    val patterns: List<ConversationPattern>,
    val intimacyLevel: Float,
    val keyMoments: List<KeyMoment>,
    val userEngagement: Float,
    val emotionalDepth: Float
)

enum class ConversationTopic {
    EMOTIONAL_SUPPORT, PLANNING, PROBLEM_SOLVING, GRATITUDE, PERSONAL_GROWTH, GENERAL
}

enum class ConversationTone {
    POSITIVE, NEUTRAL, SUPPORTIVE
}

enum class ConversationPattern {
    QUESTION_ANSWER, NARRATIVE, EMOTIONAL_EXPLORATION
}

enum class KeyMoment {
    BREAKTHROUGH, VULNERABILITY, GRATITUDE
}

data class UserProfile(
    val name: String,
    val conversationCount: Int,
    val averageSessionLength: Int,
    val preferredTopics: List<ConversationTopic>
)