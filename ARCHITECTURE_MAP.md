# Calmify Android Architecture Map

> Mappa architetturale completa per LLM con capacità di ragionamento.
> Generata da Jarvis - 24 Dicembre 2025

---

## 1. Overview Architettura Multi-Modulo

```
CalmifyAppAndroid/
├── app/                    # Modulo principale (MainActivity, Navigation, Hilt Setup)
├── core/
│   ├── ui/                 # Componenti UI condivisi, Theme Material3
│   └── util/               # Utility, costanti, modelli condivisi
├── data/
│   └── mongo/              # Layer dati (Room + MongoDB Realm)
└── features/
    ├── auth/               # Autenticazione (Firebase Auth)
    ├── home/               # Home screen + Settings + Navigazione
    ├── history/            # Storico diari e chat
    ├── insight/            # Analisi e insights
    ├── write/              # Scrittura diari
    └── chat/               # Chat AI + Voice + Live Mode
```

---

## 2. Feature Module: HOME

### 2.1 Screen e Componenti

| File | Tipo | Responsabilità |
|------|------|----------------|
| `HomeScreen.kt` | Screen | Schermata principale con unified search, navigation bar, content area |
| `HomeContent.kt` | Component | Area contenuti: diaries + chat sessions unificate |
| `HomeTopBar.kt` | Component | Top bar con search, profile, settings actions |
| `SettingsScreen.kt` | Screen | Configurazioni app (notifiche, tema, privacy) |
| `SnapshotScreen.kt` | Screen | Visualizzazione snapshot diari |
| `LoadingScreen.kt` | Screen | Schermata di caricamento con `LoadingSystem` |

### 2.2 ViewModel e State

```kotlin
// HomeViewModel.kt
class HomeViewModel : ViewModel() {
    val uiState: StateFlow<HomeUiState>
    val diaries: StateFlow<List<Diary>>
    val chatSessions: StateFlow<List<ChatSession>>

    // Actions
    fun loadData()
    fun search(query: String)
    fun filterByType(type: ContentType)
    fun deleteItem(id: String)
}

// SnapshotViewModel.kt
class SnapshotViewModel : ViewModel() {
    val snapshot: StateFlow<DiarySnapshot?>
    fun loadSnapshot(diaryId: String)
}
```

### 2.3 Componenti UI Condivisi

| Componente | Descrizione |
|------------|-------------|
| `UnifiedSearchBar` | Barra ricerca con filtri, chips, suggerimenti |
| `UnifiedContentCard` | Card unificata per diari e chat |
| `FilterChipRow` | Row di chips per filtro contenuti (All, Diaries, Chat) |
| `EnterpriseNavigationBar` | Bottom navigation bar principale |

### 2.4 Navigation

```kotlin
// HomeNavigation.kt
fun NavGraphBuilder.homeRoute(
    navigateToWrite: (String?) -> Unit,
    navigateToChat: () -> Unit,
    navigateToSettings: () -> Unit,
    navigateToSnapshot: (String) -> Unit
)

fun NavController.navigateToHome()
fun NavController.navigateToSnapshot(diaryId: String)

// SettingsNavigation.kt
fun NavGraphBuilder.settingsRoute(navigateBack: () -> Unit)
fun NavController.navigateToSettings()
```

---

## 3. Feature Module: HISTORY

### 3.1 Architettura Screen

```kotlin
// HistoryScreen.kt - Screen principale
@Composable
fun HistoryScreen(
    navigateBack: () -> Unit,
    navigateToDiaryDetail: (String) -> Unit,
    navigateToChatDetail: (String) -> Unit
)

// Sotto-schermate
DiaryHistoryFullScreen   // Lista completa diari con ricerca
ChatHistoryFullScreen    // Lista completa sessioni chat
```

### 3.2 HistoryViewModel

```kotlin
class HistoryViewModel : ViewModel() {
    // State
    val diaryHistory: StateFlow<List<Diary>>
    val chatHistory: StateFlow<List<ChatSession>>
    val isLoading: StateFlow<Boolean>
    val searchQuery: StateFlow<String>

    // Filter
    val selectedFilter: StateFlow<HistoryFilter>

    // Actions
    fun loadHistory()
    fun search(query: String)
    fun setFilter(filter: HistoryFilter)
    fun deleteDiary(id: String)
    fun deleteChatSession(id: String)
}

enum class HistoryFilter { ALL, DIARIES, CHATS }
```

### 3.3 Componenti

| Componente | Descrizione |
|------------|-------------|
| `HistoryTopBar` | Top bar con titolo, search toggle, filter chips |
| `HistoryContent` | Lazy list contenuti con swipe-to-delete |

---

## 4. Feature Module: INSIGHT

### 4.1 Struttura

```kotlin
// InsightScreen.kt
@Composable
fun InsightScreen(
    navigateBack: () -> Unit
) {
    // Visualizzazione analytics:
    // - Mood trends
    // - Writing frequency
    // - Topic analysis
    // - Emotional patterns
}

// InsightViewModel.kt
class InsightViewModel : ViewModel() {
    val moodTrend: StateFlow<List<MoodDataPoint>>
    val writingStats: StateFlow<WritingStatistics>
    val topTopics: StateFlow<List<TopicFrequency>>
    val emotionalPatterns: StateFlow<EmotionalAnalysis>

    fun loadInsights(dateRange: DateRange)
    fun exportReport()
}
```

---

## 5. Feature Module: CHAT (Core Module)

### 5.1 Architettura Generale

```
chat/
├── presentation/
│   ├── screen/
│   │   ├── ChatScreen.kt       # Chat testuale
│   │   └── LiveScreen.kt       # Chat vocale real-time
│   ├── viewmodel/
│   │   ├── ChatViewModel.kt    # ViewModel chat testuale
│   │   └── LiveChatViewModel.kt # ViewModel Live mode
│   └── components/
│       ├── ChatBubble.kt
│       ├── ChatInput.kt
│       ├── FluidAudioIndicator.kt
│       ├── GeminiLiquidVisualizer.kt
│       ├── LiveCameraPreview.kt
│       └── SimpleLiveCameraPreview.kt
├── domain/
│   ├── model/
│   │   ├── ChatUiModels.kt
│   │   └── LiveChatState.kt
│   └── audio/
│       ├── AdaptiveBargeinDetector.kt
│       ├── AudioQualityAnalyzer.kt
│       └── ConversationContextManager.kt
├── data/
│   ├── websocket/
│   │   └── GeminiLiveWebSocketClient.kt
│   ├── audio/
│   │   └── GeminiLiveAudioManager.kt
│   └── camera/
│       └── GeminiLiveCameraManager.kt
├── audio/
│   ├── GeminiNativeVoiceSystem.kt
│   ├── GeminiVoiceAudioSource.kt
│   ├── GeminiLiveAudioSource.kt
│   ├── SynchronizedSpeechControllerImpl.kt
│   └── vad/
│       └── SileroVadEngine.kt
├── config/
│   └── ApiConfigManager.kt
├── di/
│   ├── ChatModule.kt
│   ├── AudioModule.kt
│   └── GeminiLiveModule.kt
└── navigation/
    └── ChatNavigation.kt
```

### 5.2 State Models

```kotlin
// ChatUiModels.kt - Chat testuale
data class ChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSession: ChatSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = "",
    val streamingMessage: StreamingMessage? = null,
    val isNavigating: Boolean = false,
    val exportedContent: String? = null
)

data class StreamingMessage(
    val id: String,
    val content: StringBuilder,
    val isComplete: Boolean
)

// LiveChatState.kt - Live mode
enum class ConnectionStatus { Disconnected, Connecting, Connected, Error }
enum class TurnState { UserTurn, AgentTurn, WaitingForUser }
enum class AIEmotion { Neutral, Happy, Thinking, Speaking }

data class LiveChatUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val turnState: TurnState = TurnState.WaitingForUser,
    val aiEmotion: AIEmotion = AIEmotion.Neutral,
    val isMuted: Boolean = false,
    val isCameraEnabled: Boolean = false,
    val currentTranscript: String = "",
    val aiResponse: String = "",
    val error: String? = null
)
```

### 5.3 ChatViewModel (Chat Testuale)

```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val voiceSystem: GeminiNativeVoiceSystem,
    private val audioSource: GeminiVoiceAudioSource,
    private val speechController: SynchronizedSpeechController,
    private val configManager: ApiConfigManager
) : ViewModel() {

    val uiState: StateFlow<ChatUiState>
    val voiceState: StateFlow<VoiceState>
    val suggestions: StateFlow<List<SmartSuggestion>>

    // Chat Actions
    fun sendMessage(content: String)
    fun loadSession(sessionId: String)
    fun createNewSession(title: String?)
    fun deleteMessage(messageId: String)
    fun exportToDiary(sessionId: String)

    // Voice Actions
    fun speakCompleteMessage(messageId: String, text: String)
    fun stopSpeaking()

    // Avatar Integration
    fun attachHumanoidController(controller: HumanoidController)
    fun detachHumanoidController()

    // Internal
    private val emotionDetector = SimpleEmotionDetector() // Italian emotion detection
}
```

### 5.4 LiveChatViewModel (Live Mode)

```kotlin
@HiltViewModel
class LiveChatViewModel @Inject constructor(
    private val webSocketClient: GeminiLiveWebSocketClient,
    private val audioManager: GeminiLiveAudioManager,
    private val cameraManager: GeminiLiveCameraManager,
    private val contextManager: ConversationContextManager,
    private val qualityAnalyzer: AudioQualityAnalyzer,
    private val configManager: ApiConfigManager,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val uiState: StateFlow<LiveChatUiState>
    val userAudioLevel: StateFlow<Float>
    val aiAudioLevel: StateFlow<Float>
    val isCameraActive: StateFlow<Boolean>

    // Connection
    fun connect()
    fun disconnect()

    // Audio Controls
    fun toggleMute()
    fun startRecording()
    fun stopRecording()

    // Camera Controls
    fun toggleCamera()
    fun startCamera(surfaceTexture: SurfaceTexture)
    fun stopCamera()

    // Text Input (in Live mode)
    fun sendTextMessage(text: String)

    // Callbacks from WebSocket
    internal fun onAudioReceived(audioBase64: String)
    internal fun onTextReceived(text: String)
    internal fun onTurnStarted()
    internal fun onTurnCompleted()
    internal fun onInterrupted()
}
```

### 5.5 WebSocket Client (Gemini Live API)

```kotlin
@Singleton
class GeminiLiveWebSocketClient @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    // Connection State
    val connectionState: StateFlow<ConnectionState>
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    // Model Configuration
    private const val MODEL = "models/gemini-2.5-flash-native-audio-preview-12-2025"
    private const val HOST = "generativelanguage.googleapis.com"

    // Callbacks
    var onTextReceived: ((String) -> Unit)?
    var onAudioReceived: ((String) -> Unit)?  // Base64 PCM audio
    var onError: ((String) -> Unit)?
    var onPartialTranscript: ((String) -> Unit)?
    var onFinalTranscript: ((String) -> Unit)?
    var onTurnStarted: (() -> Unit)?
    var onTurnCompleted: (() -> Unit)?
    var onInterrupted: (() -> Unit)?
    var onToolCallReceived: ((String) -> Unit)?
    var onChatMessageSaved: ((String, String, Boolean) -> Unit)?

    // Tool Calling Support
    var onNeedUserData: (suspend () -> Pair<String, String>)?
    var onExecuteFunction: (suspend (String, JSONObject) -> JSONObject)?

    // Methods
    fun connect(apiKey: String)
    fun disconnect()
    fun sendAudioData(audioBase64: String)  // PCM 16kHz mono
    fun sendImageData(imageBase64: String)  // JPEG
    fun sendTextMessage(text: String)
    fun sendEndOfStream()
    fun isConnected(): Boolean

    // Tool Functions
    suspend fun executeGetRecentDiaries(args: JSONObject): JSONObject
    suspend fun executeSearchDiary(args: JSONObject): JSONObject
}
```

### 5.6 Audio System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    AUDIO SYSTEM ARCHITECTURE                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐     ┌──────────────────────────────────┐  │
│  │  GeminiNative    │     │  GeminiLiveAudioManager          │  │
│  │  VoiceSystem     │     │  (Real-time streaming)           │  │
│  │  (TTS Streaming) │     │                                  │  │
│  │                  │     │  - AudioRecord (16kHz PCM)       │  │
│  │  - API: Gemini   │     │  - AudioTrack (24kHz PCM)        │  │
│  │    2.5-flash-tts │     │  - SileroVadEngine              │  │
│  │  - PCM 24kHz     │     │  - AdaptiveBargeinDetector      │  │
│  │  - Streaming SSE │     │  - Real-time levels             │  │
│  └────────┬─────────┘     └─────────────┬────────────────────┘  │
│           │                             │                        │
│           ▼                             ▼                        │
│  ┌──────────────────┐     ┌──────────────────────────────────┐  │
│  │ GeminiVoice      │     │ GeminiLiveAudioSource            │  │
│  │ AudioSource      │     │ (SpeechAudioSource impl)         │  │
│  │                  │     │                                  │  │
│  │ Adapts TTS to    │     │ Adapts Live streaming to         │  │
│  │ SpeechAudioSource│     │ SpeechAudioSource                │  │
│  └────────┬─────────┘     └─────────────┬────────────────────┘  │
│           │                             │                        │
│           └──────────────┬──────────────┘                        │
│                          ▼                                       │
│           ┌──────────────────────────────┐                       │
│           │ SynchronizedSpeechController │                       │
│           │                              │                       │
│           │ - Collects playback events   │                       │
│           │ - Forwards to animation      │                       │
│           │ - Real-time audio intensity  │                       │
│           └──────────────┬───────────────┘                       │
│                          ▼                                       │
│           ┌──────────────────────────────┐                       │
│           │ SpeechAnimationTarget        │                       │
│           │ (HumanoidController)         │                       │
│           │                              │                       │
│           │ - Lip-sync visemes           │                       │
│           │ - Emotion expressions        │                       │
│           │ - Audio intensity modulation │                       │
│           └──────────────────────────────┘                       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 5.7 Voice Activity Detection (VAD)

```kotlin
@Singleton
class SileroVadEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Dual-Engine Architecture
    // Stage 1: WebRTC VAD (< 0.1ms) - Fast pre-filter
    // Stage 2: Silero VAD v6 (< 1ms) - DNN precision

    // Configuration
    const val SAMPLE_RATE = 16000
    const val FRAME_SIZE_SAMPLES = 512  // 32ms
    const val BARGE_IN_THRESHOLD = 0.55f
    const val BARGE_IN_CONFIRMATION_MS = 64

    // State
    val vadState: StateFlow<VadState>      // IDLE, CALIBRATING, SILENCE, POSSIBLE_SPEECH, SPEECH
    val isSpeechDetected: StateFlow<Boolean>
    val speechProbability: StateFlow<Float>
    val bargeInEvent: StateFlow<BargeInEvent?>
    val metrics: StateFlow<VadMetrics>

    // Methods
    fun initialize(): Boolean
    fun processFrame(audioFrame: ShortArray, frameSize: Int): VadResult
    fun enableBargeInMode()
    fun disableBargeInMode()
    fun reset()
    fun release()

    // Benefits
    // - 60-70% CPU savings through WebRTC pre-filtering
    // - Combined accuracy > 98%
    // - Processing latency < 1ms per frame
}
```

### 5.8 Conversation Context Manager

```kotlin
@Singleton
class ConversationContextManager @Inject constructor() {
    // Conversation Mode Detection
    enum class ConversationMode {
        CASUAL_CHAT,       // Relaxed, moderate noise tolerance
        BUSINESS_MEETING,  // Formal, high clarity
        PRESENTATION,      // One-way, minimal interruptions
        BRAINSTORM,        // High energy, rapid exchange
        NOISY_ENVIRONMENT, // Aggressive processing
        INTIMATE           // Quiet, subtle
    }

    // User Intent Detection
    enum class UserIntent {
        ASKING_QUESTION, GIVING_INSTRUCTION, CASUAL_TALKING,
        SEEKING_HELP, EXPLAINING, LISTENING, INTERRUPTED
    }

    // State Flows
    val conversationMode: StateFlow<ConversationMode>
    val userIntent: StateFlow<UserIntent>
    val optimizationSettings: StateFlow<AudioOptimizationSettings>
    val conversationMetrics: StateFlow<ConversationMetrics>

    // Audio Optimization Settings
    data class AudioOptimizationSettings(
        val bargeinSensitivity: Float,      // 0-1
        val noiseSuppressionLevel: Float,   // 0-1
        val echoCancellationLevel: Float,   // 0-1
        val gainControlLevel: Float,        // 0-1
        val latencyPriority: Float,         // 0-1
        val qualityPriority: Float          // 0-1
    )

    // Methods
    fun addMessage(content: String, isFromUser: Boolean, audioLevel: Float, duration: Long)
    fun recordSpeakingEvent(isUser: Boolean, duration: Long, averageLevel: Float, wasInterrupted: Boolean)
    fun setManualMode(mode: ConversationMode, reason: String)
    fun resetContext()
    fun getContextReport(): Map<String, Any>
    fun getCurrentEmotionalIntensity(): Float
}
```

### 5.9 Audio Quality Analyzer

```kotlin
@Singleton
class AudioQualityAnalyzer @Inject constructor() {
    // Metrics
    data class LatencyMetrics(
        val endToEndLatency: Float,
        val captureLatency: Float,
        val processLatency: Float,
        val networkLatency: Float,
        val playbackLatency: Float,
        val jitter: Float,
        val quality: LatencyQuality
    )

    data class NoiseMetrics(
        val signalToNoiseRatio: Float,
        val noiseFloor: Float,
        val noiseReduction: Float,
        val speechClarity: Float
    )

    data class EchoMetrics(
        val echoReturnLoss: Float,
        val aecEffectiveness: Float,
        val residualEcho: Float
    )

    data class OverallQualityScore(
        val totalScore: Float,      // 0-1
        val grade: QualityGrade,    // EXCELLENT, GOOD, FAIR, POOR, CRITICAL
        val primaryIssue: String,
        val recommendations: List<String>
    )

    // State Flows
    val latencyMetrics: StateFlow<LatencyMetrics>
    val noiseMetrics: StateFlow<NoiseMetrics>
    val echoMetrics: StateFlow<EchoMetrics>
    val overallQuality: StateFlow<OverallQualityScore>

    // Methods
    fun startMeasurement()
    fun stopMeasurement()
    fun processCapturedFrame(audioFrame: ShortArray, timestamp: Long)
    fun processPlaybackFrame(audioFrame: ByteArray, timestamp: Long)
    fun recordLatencyMeasurement(latencyMs: Float)
    fun getQualityReport(): Map<String, Any>
}
```

### 5.10 UI Components

```kotlin
// ChatBubble.kt
@Composable
fun ChatBubble(
    message: ChatMessage,
    isSpeaking: Boolean,
    voiceEmotion: String,
    voiceLatency: Long,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
)
// Features: Voice button, speaking indicator, emotion color, context menu

// ChatInput.kt
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean,
    currentEmotion: String,
    onNavigateToLiveMode: (() -> Unit)?
)
// Features: Voice recording, quick emotion buttons, streaming indicator

// GeminiLiquidVisualizer.kt (API 33+)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun GeminiLiquidVisualizer(
    isSpeaking: Boolean,
    userVoiceLevel: Float,
    aiVoiceLevel: Float,
    emotionalIntensity: Float,
    conversationMode: String,
    isUserSpeaking: Boolean,
    isAiSpeaking: Boolean
)
// Features: GLSL shader waves, dual-voice positioning, emotion-driven colors

// FluidAudioIndicator.kt
@Composable
fun FluidAudioIndicator(
    isSpeaking: Boolean,
    emotion: String,
    latencyMs: Long
)
// Features: Fluid wave animation, emotion-based colors, latency indicator

// LiveCameraPreview.kt / SimpleLiveCameraPreview.kt
@Composable
fun LiveCameraPreview(
    isCameraActive: Boolean,
    hasCameraPermission: Boolean,
    onToggleCamera: () -> Unit,
    onSurfaceTextureReady: (SurfaceTexture) -> Unit
)
// Features: TextureView, permission request, LIVE badge, status indicator
```

### 5.11 Navigation

```kotlin
// ChatNavigation.kt
fun NavGraphBuilder.chatRoute(
    navigateBack: () -> Unit,
    navigateToWriteWithContent: (String) -> Unit,
    navigateToLiveScreen: () -> Unit,
    navigateToAvatarLiveChat: (() -> Unit)?
)

fun NavGraphBuilder.liveRoute(navigateBack: () -> Unit)

fun NavController.navigateToChat(sessionId: String? = null)
fun NavController.navigateToLiveChat()
fun NavController.navigateToAvatarLiveChat()

// Routes
Screen.Chat.route           // Text chat
Screen.LiveChat.route       // Voice live chat
Screen.AvatarLiveChat.route // Avatar + Live chat (defined in app module)
```

### 5.12 Dependency Injection

```kotlin
// GeminiLiveModule.kt
@Module @InstallIn(SingletonComponent::class)
object GeminiLiveModule {
    @Provides @Singleton
    fun provideGeminiLiveWebSocketClient(firebaseAuth: FirebaseAuth): GeminiLiveWebSocketClient

    @Provides @Singleton
    fun provideAdaptiveBargeinDetector(): AdaptiveBargeinDetector

    @Provides @Singleton
    fun provideAudioQualityAnalyzer(): AudioQualityAnalyzer

    @Provides @Singleton
    fun provideConversationContextManager(): ConversationContextManager

    @Provides @Singleton
    fun provideSileroVadEngine(@ApplicationContext context: Context): SileroVadEngine

    @Provides @Singleton
    fun provideAAAudioEngine(@ApplicationContext context: Context): AAAudioEngine

    @Provides @Singleton
    fun provideGeminiLiveAudioManager(
        @ApplicationContext context: Context,
        adaptiveBargeinDetector: AdaptiveBargeinDetector,
        sileroVadEngine: SileroVadEngine
    ): GeminiLiveAudioManager

    @Provides @Singleton
    fun provideGeminiLiveCameraManager(@ApplicationContext context: Context): GeminiLiveCameraManager
}

// AudioModule.kt
@Module @InstallIn(SingletonComponent::class)
object AudioModule {
    @Provides @Singleton
    fun provideApiConfigManager(@ApplicationContext context: Context): ApiConfigManager

    @Provides @Singleton
    fun provideGeminiNativeVoiceSystem(@ApplicationContext context: Context): GeminiNativeVoiceSystem

    @Provides @Singleton
    fun provideGeminiVoiceAudioSource(voiceSystem: GeminiNativeVoiceSystem): GeminiVoiceAudioSource

    @Provides @Singleton
    fun provideGeminiLiveAudioSource(audioManager: GeminiLiveAudioManager): GeminiLiveAudioSource

    @Provides @Singleton
    fun provideSynchronizedSpeechController(): SynchronizedSpeechController
}

// ChatModule.kt
@Module @InstallIn(SingletonComponent::class)
object ChatModule {
    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient
}
```

### 5.13 API Configuration

```kotlin
// ApiConfigManager.kt
@Singleton
class ApiConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Secure storage using EncryptedSharedPreferences (AES256_GCM)

    fun getGeminiApiKey(): String?
    fun setGeminiApiKey(apiKey: String)
    fun isGeminiApiKeyConfigured(): Boolean
    fun clearGeminiApiKey()

    // Internal
    private val PREFS_NAME = "gemini_api_config"
    private val KEY_GEMINI_API_KEY = "gemini_api_key"
}
```

---

## 6. Data Flow Diagrams

### 6.1 Text Chat Flow

```
User Input → ChatInput → ChatViewModel.sendMessage()
                              ↓
                    ChatRepository.sendMessage()
                              ↓
                    [Gemini API / Local Processing]
                              ↓
                    ChatRepository.saveMessage()
                              ↓
            ChatViewModel.uiState.messages updated
                              ↓
                    UI Recomposition (ChatBubble)
                              ↓
            [Optional] ChatViewModel.speakCompleteMessage()
                              ↓
                    GeminiNativeVoiceSystem.speakWithEmotion()
                              ↓
            GeminiVoiceAudioSource → SynchronizedSpeechController
                              ↓
                    HumanoidController (Lip-sync + Emotion)
```

### 6.2 Live Chat Flow

```
User Voice → GeminiLiveAudioManager.startRecording()
                              ↓
             SileroVadEngine.processFrame() [VAD]
                              ↓
                    [Speech Detected?]
                     ├─ No → Continue listening
                     └─ Yes ↓
             GeminiLiveWebSocketClient.sendAudioData()
                              ↓
                    [Gemini Live API via WebSocket]
                              ↓
              onAudioReceived() / onTextReceived()
                              ↓
             GeminiLiveAudioManager.queueAudioForPlayback()
                              ↓
                    AudioTrack playback (24kHz)
                              ↓
            GeminiLiveAudioSource → SynchronizedSpeechController
                              ↓
                    HumanoidController (Lip-sync)

                    [During AI Speech]
                              ↓
             SileroVadEngine (Barge-in mode)
                              ↓
                    [User interrupts?]
                     ├─ No → Continue playback
                     └─ Yes ↓
             onBargeInDetected() → stopPlayback()
                              ↓
             GeminiLiveWebSocketClient interrupted
```

### 6.3 Camera Integration Flow

```
LiveScreen / AvatarLiveChat
              ↓
    LiveCameraPreview composable
              ↓
    TextureView.SurfaceTextureListener
              ↓
    GeminiLiveCameraManager.startCameraPreview()
              ↓
    Camera2 API → ImageReader (every 3 seconds)
              ↓
    scaleBitmap() → JPEG compress → Base64
              ↓
    GeminiLiveWebSocketClient.sendImageData()
              ↓
    Gemini receives visual context
```

---

## 7. Key Interfaces

### 7.1 SpeechAudioSource (core/util)

```kotlin
interface SpeechAudioSource {
    val playbackEvents: Flow<SpeechPlaybackEvent>
    val isSpeaking: StateFlow<Boolean>
    val audioLevel: StateFlow<Float>

    fun speak(request: SpeechRequest)
    fun stop()
}

sealed class SpeechPlaybackEvent {
    data class Preparing(val text: String, val estimatedDurationMs: Long, val messageId: String)
    data class Started(val messageId: String, val timestamp: Long)
    data class Playing(val messageId: String, val progressMs: Long, val totalDurationMs: Long, val audioLevel: Float)
    data class Finishing(val messageId: String, val remainingMs: Long)
    data class Ended(val messageId: String, val actualDurationMs: Long)
    data class Interrupted(val messageId: String, val reason: InterruptionReason)
    object Idle
}
```

### 7.2 SpeechAnimationTarget (core/util)

```kotlin
interface SpeechAnimationTarget {
    fun onPlaybackEvent(event: SpeechPlaybackEvent)
    fun updateAudioIntensity(level: Float)
    fun setEmotion(emotion: SpeechEmotion)
}
```

### 7.3 SynchronizedSpeechController (core/util)

```kotlin
interface SynchronizedSpeechController {
    val isSpeaking: StateFlow<Boolean>

    fun attachAudioSource(source: SpeechAudioSource)
    fun attachAnimationTarget(target: SpeechAnimationTarget)
    fun detachAudioSource()
    fun detachAnimationTarget()
    fun speakSynchronized(request: SpeechRequest)
    fun stopSynchronized()
    fun release()
}
```

---

## 8. Quick Reference - File Locations

| Componente | Path |
|------------|------|
| **Chat ViewModel** | `features/chat/src/main/java/com/lifo/chat/presentation/viewmodel/ChatViewModel.kt` |
| **Live ViewModel** | `features/chat/src/main/java/com/lifo/chat/presentation/viewmodel/LiveChatViewModel.kt` |
| **WebSocket Client** | `features/chat/src/main/java/com/lifo/chat/data/websocket/GeminiLiveWebSocketClient.kt` |
| **Audio Manager** | `features/chat/src/main/java/com/lifo/chat/data/audio/GeminiLiveAudioManager.kt` |
| **VAD Engine** | `features/chat/src/main/java/com/lifo/chat/audio/vad/SileroVadEngine.kt` |
| **Voice System** | `features/chat/src/main/java/com/lifo/chat/audio/GeminiNativeVoiceSystem.kt` |
| **Speech Controller** | `features/chat/src/main/java/com/lifo/chat/audio/SynchronizedSpeechControllerImpl.kt` |
| **Context Manager** | `features/chat/src/main/java/com/lifo/chat/domain/audio/ConversationContextManager.kt` |
| **Quality Analyzer** | `features/chat/src/main/java/com/lifo/chat/domain/audio/AudioQualityAnalyzer.kt` |
| **Bargein Detector** | `features/chat/src/main/java/com/lifo/chat/domain/audio/AdaptiveBargeinDetector.kt` |
| **Camera Manager** | `features/chat/src/main/java/com/lifo/chat/data/camera/GeminiLiveCameraManager.kt` |
| **API Config** | `features/chat/src/main/java/com/lifo/chat/config/ApiConfigManager.kt` |
| **DI Modules** | `features/chat/src/main/java/com/lifo/chat/di/` |

---

## 9. Technology Stack Summary

| Area | Tecnologia |
|------|------------|
| **UI Framework** | Jetpack Compose + Material3 |
| **Navigation** | Navigation Compose |
| **DI** | Hilt/Dagger |
| **Async** | Kotlin Coroutines + Flow |
| **Database** | Room + MongoDB Realm |
| **Auth** | Firebase Auth |
| **Storage** | Firebase Storage |
| **AI Chat** | Gemini API (Text + TTS) |
| **Live Voice** | Gemini Live API (WebSocket) |
| **VAD** | Silero VAD v6 + WebRTC VAD |
| **Audio** | AudioRecord/AudioTrack (16kHz/24kHz PCM) |
| **Camera** | Camera2 API |
| **HTTP** | OkHttp |
| **Image Loading** | Coil |
| **Security** | EncryptedSharedPreferences (AES256_GCM) |

---

*Generato da Jarvis AI Assistant - Calmify Project*
