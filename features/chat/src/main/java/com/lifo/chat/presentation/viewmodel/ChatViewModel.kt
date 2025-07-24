package com.lifo.chat.presentation.viewmodel

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lifo.chat.domain.model.*
import com.lifo.mongo.repository.ChatRepository
import com.lifo.mongo.repository.MessageStatus
import com.lifo.util.Constants.GOOGLE_CLOUD_API_KEY
import com.lifo.util.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val KEY_SESSION_ID = "sessionId"
        private const val STREAMING_DEBOUNCE_MS = 50L
        private const val AUTO_SAVE_DELAY = 2000L
        private const val CLOUD_TTS_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Voice support
    private var mediaPlayer: MediaPlayer? = null
    private val _voiceState = MutableStateFlow(VoiceState())
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // Smart suggestions based on context
    private val _suggestions = MutableStateFlow<List<SmartSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SmartSuggestion>> = _suggestions.asStateFlow()

    // OkHttp client for Cloud TTS
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val sessionId: String? = savedStateHandle.get<String>(KEY_SESSION_ID)
    private var streamingJob: Job? = null
    private var messagesJob: Job? = null
    private var autoSaveJob: Job? = null
    private var ttsJob: Job? = null
    private val streamingBuffer = StringBuilder()
    private var lastStreamingUpdate = 0L

    init {
        Log.d(TAG, "ChatViewModel initialized with sessionId: $sessionId")
        _voiceState.update { it.copy(isTTSReady = true) } // Cloud TTS sempre pronto
        loadSessions()
        if (sessionId != null) {
            loadSession(sessionId)
        } else {
            viewModelScope.launch {
                createNewSession()
            }
        }
        generateSmartSuggestions()
    }

    fun onEvent(event: ChatEvent) {
        if (_uiState.value.isNavigating) {
            Log.d(TAG, "Event ignored during navigation: $event")
            return
        }

        when (event) {
            is ChatEvent.SendMessage -> sendMessage(event.content)
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
            is ChatEvent.SpeakMessage -> speakMessage(event.messageId)
            is ChatEvent.StopSpeaking -> stopSpeaking()
            is ChatEvent.UseSuggestion -> useSuggestion(event.suggestion)
        }
    }

    private fun speakMessage(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId }
        message?.let {
            if (!it.isUser) {
                // Cancella qualsiasi TTS in corso
                ttsJob?.cancel()
                stopSpeaking()

                ttsJob = viewModelScope.launch {
                    _voiceState.update { state ->
                        state.copy(
                            isSpeaking = true,
                            currentSpeakingMessageId = messageId
                        )
                    }

                    try {
                        // Prepara il testo
                        val textToSpeak = prepareTextForCloudTTS(it.content)

                        // Genera audio con Cloud TTS
                        val audioFile = generateCloudTTSAudio(textToSpeak)

                        if (audioFile != null) {
                            // Riproduci l'audio
                            playAudioFile(audioFile, messageId)
                        } else {
                            Log.e(TAG, "Failed to generate audio")
                            _voiceState.update { state ->
                                state.copy(
                                    isSpeaking = false,
                                    currentSpeakingMessageId = null
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in TTS", e)
                        _voiceState.update { state ->
                            state.copy(
                                isSpeaking = false,
                                currentSpeakingMessageId = null
                            )
                        }
                    }
                }
            }
        }
    }

    private fun prepareTextForCloudTTS(text: String): String {
        return text
            // Rimuovi emoji
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            // Rimuovi markdown
            .replace("**", "")
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .replace("```", "")
            // Gestisci abbreviazioni
            .replace("es.", "esempio")
            .replace("ecc.", "eccetera")
            .replace("etc.", "eccetera")
            .replace("dott.", "dottore")
            .replace("sig.", "signor")
            .replace("sig.ra", "signora")
            // Pulisci spazi
            .replace("  ", " ")
            .trim()
    }

    // 1. Aggiorna la funzione generateCloudTTSAudio con configurazioni avanzate
    private suspend fun generateCloudTTSAudio(text: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                // Preprocessa il testo per ottimizzare la naturalezza
                val processedText = preprocessTextForNaturalSpeech(text)
                val ssmlText = buildAdvancedSSMLText(processedText)

                val requestBody = JSONObject().apply {
                    put("input", JSONObject().apply {
                        put("ssml", ssmlText)
                    })
                    put("voice", JSONObject().apply {
                        put("languageCode", "it-IT")
                        put("name", "it-IT-Wavenet-A") // Voce femminile WaveNet italiana
                        put("ssmlGender", "FEMALE")
                    })
                    put("audioConfig", JSONObject().apply {
                        put("audioEncoding", "MP3")
                        put("speakingRate", 1.3) // Velocità leggermente più lenta per naturalezza
                        put("pitch", 1.1) // Tono più profondo e naturale
                        put("volumeGainDb", 2.0) // Volume leggermente aumentato
                        // Aggiungi effetti audio per maggiore naturalezza
                        put("effectsProfileId", JSONArray().apply {
                            put("handset-class-device") // Simula voce telefonica per intimità
                        })
                    })
                }

                Log.d(TAG, "Enhanced Cloud TTS Request: ${requestBody.toString(2)}")

                val request = Request.Builder()
                    .url("$CLOUD_TTS_URL?key=$GOOGLE_CLOUD_API_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    response.body?.let { responseBody ->
                        val json = JSONObject(responseBody.string())
                        val audioContent = json.getString("audioContent")

                        val audioBytes = Base64.decode(audioContent, Base64.DEFAULT)
                        val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")

                        FileOutputStream(tempFile).use { fos ->
                            fos.write(audioBytes)
                        }

                        Log.d(TAG, "Enhanced audio file created: ${tempFile.absolutePath}")
                        return@withContext tempFile
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "Cloud TTS error: ${response.code} - $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating Cloud TTS audio", e)
            }
            null
        }
    }
    // 2. Preprocessing avanzato del testo per naturalezza
    private fun preprocessTextForNaturalSpeech(text: String): String {
        return text
            // Gestione emoticon testuali
            .replace(":)", "")
            .replace(":(", "")
            .replace(":D", "")
            .replace("XD", "")

            // Gestione punteggiatura per pause naturali
            .replace("...", "...")
            .replace("!!", "!")
            .replace("??", "?")

            // Sostituzione abbreviazioni comuni italiane
            .replace("cmq", "comunque")
            .replace("nn", "non")
            .replace("xché", "perché")
            .replace("x", "per")
            .replace("ke", "che")
            .replace("tt", "tutto")
            .replace("qlc", "qualcosa")
            .replace("qlcn", "qualcuno")

            // Gestione numeri
            .replace(Regex("(\\d+)%"), "$1 percento")
            .replace(Regex("€(\\d+)"), "$1 euro")
            .replace(Regex("\\$(\\d+)"), "$1 dollari")

            // Rimuovi parentesi mantenendo il contenuto
            .replace(Regex("\\(([^)]+)\\)"), ", $1,")
            .replace(Regex("\\[([^]]+)\\]"), ", $1,")

            // Normalizza spazi
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // 3. SSML avanzato con variazioni naturali
    private fun buildAdvancedSSMLText(text: String): String {
        val escapedText = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        val ssmlBuilder = StringBuilder("<speak>")

        // Aggiungi variazione iniziale per naturalezza
        ssmlBuilder.append("<prosody rate=\"95%\" pitch=\"-2st\">")

        // Dividi in frasi e applica variazioni
        val sentences = escapedText.split(Regex("(?<=[.!?])\\s+"))

        sentences.forEachIndexed { index, sentence ->
            val trimmedSentence = sentence.trim()
            if (trimmedSentence.isNotEmpty()) {
                // Varia leggermente velocità e tono per ogni frase
                val rateVariation = (90..100).random()
                val pitchVariation = (-3..-1).random()

                when {
                    // Domande
                    trimmedSentence.endsWith("?") -> {
                        ssmlBuilder.append(
                            "<prosody rate=\"${rateVariation}%\" pitch=\"+20%\">" +
                                    "<emphasis level=\"moderate\">$trimmedSentence</emphasis>" +
                                    "</prosody>"
                        )
                        ssmlBuilder.append("<break time=\"400ms\"/>")
                    }

                    // Esclamazioni
                    trimmedSentence.endsWith("!") -> {
                        ssmlBuilder.append(
                            "<prosody rate=\"${rateVariation + 5}%\" pitch=\"+10%\" volume=\"+2dB\">" +
                                    "<emphasis level=\"strong\">$trimmedSentence</emphasis>" +
                                    "</prosody>"
                        )
                        ssmlBuilder.append("<break time=\"350ms\"/>")
                    }

                    // Frasi con virgole (pause interne)
                    trimmedSentence.contains(",") -> {
                        val parts = trimmedSentence.split(",")
                        parts.forEachIndexed { partIndex, part ->
                            ssmlBuilder.append(
                                "<prosody rate=\"${rateVariation}%\" pitch=\"${pitchVariation}st\">" +
                                        part.trim() +
                                        "</prosody>"
                            )
                            if (partIndex < parts.size - 1) {
                                ssmlBuilder.append("<break time=\"200ms\"/>")
                            }
                        }
                        ssmlBuilder.append("<break time=\"300ms\"/>")
                    }

                    // Frasi normali
                    else -> {
                        // Aggiungi micro-pause casuali per naturalezza
                        val words = trimmedSentence.split(" ")
                        words.forEachIndexed { wordIndex, word ->
                            ssmlBuilder.append(word)

                            // Pause casuali tra parole per effetto naturale
                            if (wordIndex < words.size - 1 && (0..10).random() > 7) {
                                ssmlBuilder.append("<break time=\"50ms\"/>")
                            } else if (wordIndex < words.size - 1) {
                                ssmlBuilder.append(" ")
                            }
                        }
                        ssmlBuilder.append("<break time=\"250ms\"/>")
                    }
                }

                // Pausa più lunga tra paragrafi
                if (index < sentences.size - 1 && trimmedSentence.endsWith(".") && (index + 1) % 3 == 0) {
                    ssmlBuilder.append("<break time=\"500ms\"/>")
                }
            }
        }

        ssmlBuilder.append("</prosody>")

        // Aggiungi respiro occasionale per lunghi testi
        val ssmlText = ssmlBuilder.toString()
        val breathPattern = Regex("(<break time=\"\\d+ms\"/>)")
        var breathCount = 0
        val finalSsml = breathPattern.replace(ssmlText) { matchResult ->
            breathCount++
            if (breathCount % 5 == 0) {
                // Simula respiro ogni 5 pause
                "${matchResult.value}<break time=\"100ms\"/><prosody volume=\"-6dB\"><break time=\"200ms\"/></prosody>"
            } else {
                matchResult.value
            }
        }

        return "$finalSsml</speak>"
    }

    // 4. Migliora playAudioFile con effetti audio
    private fun playAudioFile(audioFile: File, messageId: String) {
        try {
            mediaPlayer?.let { player ->
                try {
                    player.reset()
                    player.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing previous media player", e)
                }
            }
            mediaPlayer = null

            mediaPlayer = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                }

                setDataSource(audioFile.absolutePath)

                // Aggiungi effetto fade-in
                setVolume(0f, 0f)

                setOnPreparedListener { mp ->
                    mp.start()

                    // Fade-in graduale del volume
                    viewModelScope.launch {
                        for (i in 0..10) {
                            val volume = i / 10f
                            mp.setVolume(volume, volume)
                            delay(50)
                        }
                    }

                    Log.d(TAG, "Started playing enhanced audio")
                }

                setOnCompletionListener { mp ->
                    Log.d(TAG, "Audio playback completed")

                    // Fade-out prima di terminare
                    viewModelScope.launch {
                        for (i in 10 downTo 0) {
                            val volume = i / 10f
                            try {
                                mp.setVolume(volume, volume)
                                delay(30)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error during fade-out", e)
                            }
                        }

                        _voiceState.update { state ->
                            state.copy(
                                isSpeaking = false,
                                currentSpeakingMessageId = null
                            )
                        }

                        try {
                            audioFile.delete()
                            mp.reset()
                            mp.release()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error cleaning up", e)
                        }
                        mediaPlayer = null
                    }
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _voiceState.update { state ->
                        state.copy(
                            isSpeaking = false,
                            currentSpeakingMessageId = null
                        )
                    }
                    try {
                        audioFile.delete()
                        mp.reset()
                        mp.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error on cleanup", e)
                    }
                    mediaPlayer = null
                    true
                }

                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            _voiceState.update { state ->
                state.copy(
                    isSpeaking = false,
                    currentSpeakingMessageId = null
                )
            }
            try {
                audioFile.delete()
            } catch (ex: Exception) {
                Log.e(TAG, "Error deleting temp file", ex)
            }
            mediaPlayer = null
        }
    }

    // 5. Aggiungi gestione intelligente delle emozioni nel testo
    private fun detectEmotionAndAdjustSSML(text: String): String {
        val emotions = mapOf(
            "felice" to mapOf("rate" to "105%", "pitch" to "+5%", "volume" to "+2dB"),
            "triste" to mapOf("rate" to "90%", "pitch" to "-5%", "volume" to "-2dB"),
            "arrabbiato" to mapOf("rate" to "110%", "pitch" to "+10%", "volume" to "+3dB"),
            "preoccupato" to mapOf("rate" to "95%", "pitch" to "-2%", "volume" to "0dB"),
            "entusiasta" to mapOf("rate" to "115%", "pitch" to "+15%", "volume" to "+4dB")
        )

        // Rileva emozioni basiche nel testo
        for ((emotion, settings) in emotions) {
            if (text.toLowerCase().contains(emotion)) {
                return "<prosody rate=\"${settings["rate"]}\" pitch=\"${settings["pitch"]}\" volume=\"${settings["volume"]}\">"
            }
        }

        // Default neutro ma naturale
        return "<prosody rate=\"95%\" pitch=\"-2st\" volume=\"+1dB\">"
    }
    private fun buildSSMLText(text: String): String {
        // Escape caratteri speciali per XML/SSML
        val escapedText = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        // Costruisci SSML con pause naturali e enfasi
        val sentences = escapedText.split(Regex("(?<=[.!?])\\s+"))
        val ssmlBuilder = StringBuilder("<speak>")

        sentences.forEach { sentence ->
            val trimmedSentence = sentence.trim()
            if (trimmedSentence.isNotEmpty()) {
                when {
                    trimmedSentence.endsWith("?") -> {
                        // Domande con intonazione ascendente
                        ssmlBuilder.append("<prosody pitch=\"+10%\">$trimmedSentence</prosody>")
                    }
                    trimmedSentence.endsWith("!") -> {
                        // Esclamazioni con enfasi
                        ssmlBuilder.append("<emphasis level=\"moderate\">$trimmedSentence</emphasis>")
                    }
                    else -> {
                        // Frasi normali
                        ssmlBuilder.append(trimmedSentence)
                    }
                }
                // Aggiungi pausa tra frasi
                ssmlBuilder.append("<break time=\"300ms\"/>")
            }
        }

        ssmlBuilder.append("</speak>")

        val ssml = ssmlBuilder.toString()
        Log.d(TAG, "Generated SSML: $ssml")
        return ssml
    }


    private fun stopSpeaking() {
        ttsJob?.cancel()
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error stopping media player", e)
        } finally {
            mediaPlayer = null
            _voiceState.update { it.copy(isSpeaking = false, currentSpeakingMessageId = null) }
        }
    }

    private fun generateSmartSuggestions() {
        viewModelScope.launch {
            val hour = java.time.LocalTime.now().hour
            val dayOfWeek = java.time.LocalDate.now().dayOfWeek

            val baseSuggestions = mutableListOf<SmartSuggestion>()

            when (hour) {
                in 6..11 -> {
                    baseSuggestions.add(
                        SmartSuggestion(
                            id = "morning_1",
                            text = "Come stai iniziando la giornata?",
                            category = SuggestionCategory.MOOD,
                            icon = "☀️"
                        )
                    )
                    baseSuggestions.add(
                        SmartSuggestion(
                            id = "morning_2",
                            text = "Quali sono i tuoi obiettivi per oggi?",
                            category = SuggestionCategory.PLANNING,
                            icon = "📋"
                        )
                    )
                }
                in 12..17 -> {
                    baseSuggestions.add(
                        SmartSuggestion(
                            id = "afternoon_1",
                            text = "Come sta andando la tua giornata?",
                            category = SuggestionCategory.CHECK_IN,
                            icon = "🌤️"
                        )
                    )
                    baseSuggestions.add(
                        SmartSuggestion(
                            id = "afternoon_2",
                            text = "Hai bisogno di una pausa?",
                            category = SuggestionCategory.WELLNESS,
                            icon = "☕"
                        )
                    )
                }
                in 18..23 -> {
                    baseSuggestions.add(
                        SmartSuggestion(
                            id = "evening_1",
                            text = "Vuoi riflettere sulla giornata?",
                            category = SuggestionCategory.REFLECTION,
                            icon = "🌙"
                        )
                    )
                    baseSuggestions.add(
                        SmartSuggestion(
                            id = "evening_2",
                            text = "C'è qualcosa che ti preoccupa?",
                            category = SuggestionCategory.SUPPORT,
                            icon = "💭"
                        )
                    )
                }
            }

            if (dayOfWeek == java.time.DayOfWeek.SATURDAY ||
                dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                baseSuggestions.add(
                    SmartSuggestion(
                        id = "weekend_1",
                        text = "Come stai trascorrendo il weekend?",
                        category = SuggestionCategory.LIFESTYLE,
                        icon = "🎉"
                    )
                )
            }

            baseSuggestions.addAll(listOf(
                SmartSuggestion(
                    id = "general_1",
                    text = "Ho bisogno di sfogarmi",
                    category = SuggestionCategory.SUPPORT,
                    icon = "💬"
                ),
                SmartSuggestion(
                    id = "general_2",
                    text = "Aiutami a gestire lo stress",
                    category = SuggestionCategory.WELLNESS,
                    icon = "🧘"
                ),
                SmartSuggestion(
                    id = "general_3",
                    text = "Voglio parlare dei miei progressi",
                    category = SuggestionCategory.REFLECTION,
                    icon = "📈"
                )
            ))

            _suggestions.value = baseSuggestions.take(4)
        }
    }

    private fun useSuggestion(suggestion: SmartSuggestion) {
        _uiState.update { it.copy(inputText = suggestion.text) }
        viewModelScope.launch {
            delay(300)
            sendMessage(suggestion.text)
        }
    }

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
                                        generateSmartSuggestions()
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

    private fun sendMessage(content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) {
            Log.d(TAG, "Empty message, not sending")
            return
        }

        _uiState.update { it.copy(sessionStarted = true) }

        Log.d(TAG, "Sending message: $trimmedContent")
        val currentSession = _uiState.value.currentSession

        if (currentSession == null) {
            Log.d(TAG, "No current session, creating new one")
            createNewSession { session ->
                Log.d(TAG, "New session created: ${session.id}")
                sendMessageToSession(session.id, trimmedContent)
            }
        } else {
            Log.d(TAG, "Using existing session: ${currentSession.id}")
            sendMessageToSession(currentSession.id, trimmedContent)
        }
    }

    private fun sendMessageToSession(sessionId: String, content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "") }

            generateSmartSuggestions()

            when (val result = repository.sendMessage(sessionId, content)) {
                is RequestState.Success -> {
                    Log.d(TAG, "Message sent successfully")
                    generateAiResponseOptimized(sessionId, content)
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

    private fun generateAiResponseOptimized(sessionId: String, userMessage: String) {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            try {
                val streamingMessage = StreamingMessage()
                _uiState.update { it.copy(streamingMessage = streamingMessage) }

                streamingBuffer.clear()
                lastStreamingUpdate = System.currentTimeMillis()

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
                    repository.saveAiMessage(sessionId, finalContent)
                    _uiState.update { it.copy(streamingMessage = null) }

                    if (_voiceState.value.autoSpeak) {
                        delay(500)
                        val savedMessage = _uiState.value.messages.lastOrNull { !it.isUser }
                        savedMessage?.let { speakMessage(it.id) }
                    }
                }

            } finally {
                _uiState.update { it.copy(streamingMessage = null) }
                generateSmartSuggestions()
            }
        }
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
                    generateSmartSuggestions()
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

    private fun retryMessage(messageId: String) {
        viewModelScope.launch {
            val message = _uiState.value.messages.find { it.id == messageId }
            if (message != null && message.isUser) {
                when (val result = repository.retryMessage(messageId)) {
                    is RequestState.Success -> {
                        generateAiResponseOptimized(message.sessionId, message.content)
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

    fun toggleAutoSpeak() {
        _voiceState.update { it.copy(autoSpeak = !it.autoSpeak) }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
        messagesJob?.cancel()
        autoSaveJob?.cancel()
        ttsJob?.cancel()

        // Rilascia MediaPlayer in modo sicuro
        try {
            mediaPlayer?.let { player ->
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing media player in onCleared", e)
        }
        mediaPlayer = null

        // Shutdown OkHttp
        try {
            okHttpClient.dispatcher.executorService.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down OkHttp", e)
        }
    }
}