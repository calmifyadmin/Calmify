# Piano di Integrazione: Gemini Live API su Avatar Chat Screen

## Executive Summary

Questo documento descrive il piano per integrare le **Gemini Live API** con **streaming audio bidirezionale** e **trasmissione fotocamera** nella schermata **Avatar Chat Screen**, riutilizzando il codice esistente della `LiveScreen`.

---

## 1. Architettura Attuale

### 1.1 Sistema Gemini Live (features/chat)

Il sistema Live attuale implementa:

| Componente | File | Funzionalita |
|------------|------|--------------|
| WebSocket Client | [GeminiLiveWebSocketClient.kt](features/chat/src/main/java/com/lifo/chat/data/websocket/GeminiLiveWebSocketClient.kt) | Connessione WSS, messaggi JSON, VAD server-side |
| Audio Manager | [GeminiLiveAudioManager.kt](features/chat/src/main/java/com/lifo/chat/data/audio/GeminiLiveAudioManager.kt) | Recording 16kHz, Playback 24kHz, Barge-in detection |
| Camera Manager | [GeminiLiveCameraManager.kt](features/chat/src/main/java/com/lifo/chat/data/camera/GeminiLiveCameraManager.kt) | Cattura JPEG, invio ogni 3 secondi |
| Live Audio Source | [GeminiLiveAudioSource.kt](features/chat/src/main/java/com/lifo/chat/audio/GeminiLiveAudioSource.kt) | Bridge per lip-sync sincronizzato |
| ViewModel | [LiveChatViewModel.kt](features/chat/src/main/java/com/lifo/chat/presentation/viewmodel/LiveChatViewModel.kt) | Orchestrazione completa |

### 1.2 Sistema Avatar/Humanoid (features/humanoid)

| Componente | File | Funzionalita |
|------------|------|--------------|
| Controller API | [HumanoidController.kt](features/humanoid/src/main/java/com/lifo/humanoid/api/HumanoidController.kt) | Interfaccia pubblica per controllo avatar |
| Controller Impl | [HumanoidControllerImpl.kt](features/humanoid/src/main/java/com/lifo/humanoid/api/HumanoidControllerImpl.kt) | Implementazione con eventi sincronizzati |
| Lip-Sync | [LipSyncController.kt](features/humanoid/src/main/java/com/lifo/humanoid/lipsync/LipSyncController.kt) | Conversione audio -> visemi -> blend shapes |
| Blend Shapes | [VrmBlendShapeController.kt](features/humanoid/src/main/java/com/lifo/humanoid/data/vrm/VrmBlendShapeController.kt) | Applicazione pesi morph target |
| Emotions | [Emotion.kt](features/humanoid/src/main/java/com/lifo/humanoid/domain/model/Emotion.kt) | Happy, Sad, Thinking, Excited, etc. |
| Renderer | [FilamentRenderer.kt](features/humanoid/src/main/java/com/lifo/humanoid/rendering/FilamentRenderer.kt) | Rendering VRM con Filament |

### 1.3 Integrazione Esistente (app module)

| Componente | File | Funzionalita |
|------------|------|--------------|
| Avatar Chat Screen | [AvatarChatScreen.kt](app/src/main/java/com/lifo/app/presentation/screen/AvatarChatScreen.kt) | UI con avatar + chat testuale |
| Emotion Bridge | [EmotionBridge.kt](app/src/main/java/com/lifo/app/integration/EmotionBridge.kt) | Mapping emozioni chat -> avatar |
| TTS Lip-Sync Adapter | [TTSLipSyncAdapter.kt](app/src/main/java/com/lifo/app/integration/TTSLipSyncAdapter.kt) | Sincronizzazione TTS -> lip-sync |

---

## 2. Obiettivo dell'Integrazione

Creare una nuova schermata **AvatarLiveChatScreen** che combini:

```
+--------------------------------------------------+
|  [Status: Live]              [Camera] [X Close]  |  <- Top Bar
+--------------------------------------------------+
|                                                  |
|                                                  |
|              VRM AVATAR (Fullscreen)             |  <- Layer 1: Avatar
|              - Lip-sync real-time                |
|              - Emozioni dinamiche                |
|              - Animazioni gestuali               |
|                                                  |
+--------------------------------------------------+
|         [Camera Preview PIP - Optional]          |  <- Layer 2: Camera
+--------------------------------------------------+
|                                                  |
|  "Ciao! Come posso aiutarti oggi?"               |  <- Layer 3: Transcript
|                                                  |
+--------------------------------------------------+
|     [Mute]  [Liquid Visualizer]  [Camera]        |  <- Layer 4: Controls
+--------------------------------------------------+
```

---

## 3. Architettura Target

### 3.1 Flusso Dati Completo

```
                    UTENTE
                      |
         +------------+------------+
         |                         |
    [Microphone]              [Camera]
         |                         |
         v                         v
+------------------+    +----------------------+
| GeminiLiveAudio  |    | GeminiLiveCamera     |
| Manager          |    | Manager              |
| - PCM 16kHz      |    | - JPEG 1024px        |
| - Base64         |    | - Every 3s           |
+------------------+    +----------------------+
         |                         |
         +------------+------------+
                      |
                      v
         +------------------------+
         | GeminiLiveWebSocket    |
         | Client                 |
         | - WSS Connection       |
         | - VAD Server-side      |
         | - Function Calling     |
         +------------------------+
                      |
                      v
              [Gemini Live API]
                      |
                      v
         +------------------------+
         | Server Response        |
         | - Audio PCM 24kHz      |
         | - Text Transcript      |
         | - Turn Events          |
         +------------------------+
                      |
         +------------+------------+
         |                         |
         v                         v
+------------------+    +----------------------+
| GeminiLiveAudio  |    | GeminiLiveAudio      |
| Source           |    | Manager              |
| (Lip-Sync Bridge)|    | (Playback)           |
+------------------+    +----------------------+
         |                         |
         v                         |
+------------------+               |
| Synchronized     |               |
| SpeechController |               |
+------------------+               |
         |                         |
         v                         v
+------------------+    +----------------------+
| HumanoidController|   | Audio Output         |
| - Lip-Sync       |    | - Speaker/Earpiece   |
| - Emotions       |    +----------------------+
| - Gestures       |
+------------------+
         |
         v
+------------------+
| FilamentRenderer |
| - VRM Avatar     |
| - Blend Shapes   |
+------------------+
         |
         v
      [DISPLAY]
```

### 3.2 Nuovi Componenti da Creare

| # | Componente | Location | Descrizione |
|---|------------|----------|-------------|
| 1 | `AvatarLiveChatScreen.kt` | `app/src/main/java/com/lifo/app/presentation/screen/` | UI principale |
| 2 | `AvatarLiveChatViewModel.kt` | `app/src/main/java/com/lifo/app/presentation/viewmodel/` | Orchestrazione avatar + live |
| 3 | `LiveEmotionBridge.kt` | `app/src/main/java/com/lifo/app/integration/` | Mapping emozioni live -> avatar |
| 4 | `AvatarLiveChatNavigation.kt` | `app/src/main/java/com/lifo/app/navigation/` | Route e navigazione |

### 3.3 Componenti da Riutilizzare (Senza Modifiche)

| Componente | Motivo |
|------------|--------|
| `GeminiLiveWebSocketClient` | Gia completo con VAD, function calling |
| `GeminiLiveAudioManager` | Full-duplex, barge-in, audio levels |
| `GeminiLiveCameraManager` | Cattura e invio immagini funzionante |
| `GeminiLiveAudioSource` | Bridge per SpeechPlaybackEvents |
| `SynchronizedSpeechController` | Orchestrazione lip-sync esistente |
| `HumanoidController` | API avatar completa |
| `LipSyncController` | Conversione visemi funzionante |
| `FilamentRenderer` | Rendering VRM stabile |

---

## 4. Piano di Implementazione

### FASE 1: Setup e Navigazione

#### 1.1 Creare Navigation Route

**File:** `app/src/main/java/com/lifo/app/navigation/AvatarLiveChatNavigation.kt`

```kotlin
// Definire route: Screen.AvatarLiveChat
// Aggiungere a NavGraph con parametri opzionali
// Transizioni animate (fadeIn, scaleIn)
```

**Riferimenti:**
- Pattern esistente: [AvatarChatNavigation.kt](app/src/main/java/com/lifo/app/navigation/AvatarChatNavigation.kt)
- Route esistente: [ChatNavigation.kt](features/chat/src/main/java/com/lifo/chat/navigation/ChatNavigation.kt)

#### 1.2 Aggiornare Screen Enum

**File:** `app/src/main/java/com/lifo/app/navigation/Screen.kt` (o equivalente)

Aggiungere:
```kotlin
object AvatarLiveChat : Screen("avatar_live_chat")
```

---

### FASE 2: ViewModel Integrato

#### 2.1 Creare AvatarLiveChatViewModel

**File:** `app/src/main/java/com/lifo/app/presentation/viewmodel/AvatarLiveChatViewModel.kt`

**Dipendenze da iniettare:**
```kotlin
@HiltViewModel
class AvatarLiveChatViewModel @Inject constructor(
    // === Gemini Live Components ===
    private val geminiWebSocketClient: GeminiLiveWebSocketClient,
    private val geminiAudioManager: GeminiLiveAudioManager,
    private val geminiCameraManager: GeminiLiveCameraManager,
    private val liveAudioSource: GeminiLiveAudioSource,

    // === Synchronized Speech ===
    private val synchronizedSpeechController: SynchronizedSpeechController,

    // === Audio Intelligence ===
    private val audioQualityAnalyzer: AudioQualityAnalyzer,
    private val conversationContextManager: ConversationContextManager,

    // === Repositories ===
    private val chatRepository: ChatRepository,
    private val diaryRepository: MongoRepository,

    // === Config ===
    private val apiConfigManager: ApiConfigManager,
    private val firebaseAuth: FirebaseAuth,

    @ApplicationContext private val context: Context
) : ViewModel()
```

**Funzionalita chiave:**
1. Connessione WebSocket con setup iniziale
2. Gestione recording/playback audio
3. Gestione camera preview
4. Bridge per lip-sync sincronizzato
5. Mapping emozioni in tempo reale
6. Gestione barge-in e interruzioni
7. Salvataggio messaggi in ChatRepository

**Riferimento principale:** [LiveChatViewModel.kt:36-850](features/chat/src/main/java/com/lifo/chat/presentation/viewmodel/LiveChatViewModel.kt)

#### 2.2 UI State Model

```kotlin
data class AvatarLiveChatUiState(
    // Connection
    val connectionStatus: ConnectionStatus,
    val isChannelOpen: Boolean,

    // Permissions
    val hasAudioPermission: Boolean,
    val hasCameraPermission: Boolean,

    // Audio
    val isMuted: Boolean,
    val userAudioLevel: Float,
    val aiAudioLevel: Float,

    // Camera
    val isCameraActive: Boolean,
    val wantsCameraOn: Boolean,

    // Turn Management
    val turnState: TurnState,
    val aiEmotion: AIEmotion,

    // Transcripts
    val currentTranscript: String,
    val partialTranscript: String,

    // Avatar
    val avatarEmotion: Emotion,
    val isSpeaking: Boolean,

    // Errors
    val error: String?
)
```

---

### FASE 3: Bridge Emozioni Live

#### 3.1 Creare LiveEmotionBridge

**File:** `app/src/main/java/com/lifo/app/integration/LiveEmotionBridge.kt`

**Mapping:**
```kotlin
class LiveEmotionBridge(
    private val humanoidController: HumanoidController
) {
    // Mappa AIEmotion (Live) -> Emotion (Humanoid)
    fun applyLiveEmotion(aiEmotion: AIEmotion) {
        val vrmEmotion = when (aiEmotion) {
            AIEmotion.Neutral -> Emotion.Neutral
            AIEmotion.Happy -> Emotion.Happy(1.0f)
            AIEmotion.Thinking -> Emotion.Thinking(0.8f)
            AIEmotion.Speaking -> Emotion.Neutral // Lip-sync handles mouth
            AIEmotion.Listening -> Emotion.Calm(0.5f)
        }
        humanoidController.setEmotion(vrmEmotion)
    }

    // Rilevamento emozione da testo AI
    fun detectEmotionFromText(text: String): Emotion {
        // Analisi keywords per emozione
        return when {
            text.containsAny("felice", "contento", "bene") -> Emotion.Happy(0.8f)
            text.containsAny("triste", "dispiaciuto") -> Emotion.Sad(0.7f)
            text.containsAny("interessante", "curioso") -> Emotion.Surprised(0.5f)
            text.containsAny("capisco", "comprendo") -> Emotion.Calm(0.6f)
            else -> Emotion.Neutral
        }
    }
}
```

**Riferimento:** [EmotionBridge.kt](app/src/main/java/com/lifo/app/integration/EmotionBridge.kt)

---

### FASE 4: UI Composable

#### 4.1 Creare AvatarLiveChatScreen

**File:** `app/src/main/java/com/lifo/app/presentation/screen/AvatarLiveChatScreen.kt`

**Struttura UI (4 Layer):**

```kotlin
@Composable
fun AvatarLiveChatScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AvatarLiveChatViewModel = hiltViewModel(),
    humanoidViewModel: HumanoidViewModel = hiltViewModel()
) {
    Box(modifier = modifier.fillMaxSize()) {

        // LAYER 1: Avatar VRM (Background)
        HumanoidAvatarView(
            modifier = Modifier.fillMaxSize(),
            viewModel = humanoidViewModel,
            blurAmount = 0f
        )

        // LAYER 2: Camera Preview (PIP - Optional)
        AnimatedVisibility(
            visible = uiState.wantsCameraOn && uiState.hasCameraPermission,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(120.dp, 160.dp)
        ) {
            SimpleLiveCameraPreview(...)
        }

        // LAYER 3: Liquid Visualizer (Background effect)
        GeminiLiquidVisualizer(
            isSpeaking = uiState.turnState != TurnState.WaitingForUser,
            userVoiceLevel = uiState.userAudioLevel,
            aiVoiceLevel = uiState.aiAudioLevel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
        )

        // LAYER 4: Top Bar
        AvatarLiveTopBar(
            connectionStatus = uiState.connectionStatus,
            turnState = uiState.turnState,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // LAYER 5: Transcript Overlay
        AnimatedVisibility(
            visible = uiState.currentTranscript.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
        ) {
            TranscriptCard(text = uiState.currentTranscript)
        }

        // LAYER 6: Bottom Controls
        AvatarLiveBottomControls(
            isMuted = uiState.isMuted,
            isCameraActive = uiState.isCameraActive,
            turnState = uiState.turnState,
            onToggleMute = { viewModel.toggleMute() },
            onToggleCamera = { viewModel.toggleCamera() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
```

**Riferimenti UI:**
- [LiveScreen.kt](features/chat/src/main/java/com/lifo/chat/presentation/screen/LiveScreen.kt) - Layout e controlli
- [AvatarChatScreen.kt](app/src/main/java/com/lifo/app/presentation/screen/AvatarChatScreen.kt) - Integrazione avatar
- [GeminiLiquidVisualizer.kt](features/chat/src/main/java/com/lifo/chat/presentation/components/GeminiLiquidVisualizer.kt) - Visualizzatore audio

---

### FASE 5: Integrazione Lip-Sync Live

#### 5.1 Connessione Audio Source -> Animation Target

Nel `AvatarLiveChatViewModel`:

```kotlin
private fun initializeSynchronizedSpeech() {
    // 1. Attach live audio source (fornisce eventi playback)
    synchronizedSpeechController.attachAudioSource(liveAudioSource)

    // 2. HumanoidController verra attached dalla UI
}

fun attachHumanoidController(controller: SpeechAnimationTarget) {
    synchronizedSpeechController.attachAnimationTarget(controller)
}
```

**Flusso eventi:**
```
GeminiLiveAudioManager.playbackState
    |
    v
GeminiLiveAudioSource.handlePlaybackStateChange()
    |
    v
SpeechPlaybackEvent (Started/Playing/Ended)
    |
    v
SynchronizedSpeechController.handlePlaybackEvent()
    |
    v
HumanoidController.onPlaybackEvent()
    |
    v
LipSyncController (genera visemi)
    |
    v
VrmBlendShapeController (applica pesi)
```

#### 5.2 Gestione Audio Level per Intensity

```kotlin
// In AvatarLiveChatViewModel
viewModelScope.launch {
    geminiAudioManager.aiAudioLevel.collectLatest { level ->
        // Aggiorna intensita lip-sync basata su volume reale
        humanoidController?.updateAudioIntensity(level)
    }
}
```

**Riferimento:** [GeminiLiveAudioSource.kt:46-49](features/chat/src/main/java/com/lifo/chat/audio/GeminiLiveAudioSource.kt)

---

### FASE 6: Gestione Camera

#### 6.1 Preview Camera su Avatar

```kotlin
// Nella UI, quando l'utente attiva la camera:
SimpleLiveCameraPreview(
    isCameraActive = uiState.isCameraActive,
    hasCameraPermission = uiState.hasCameraPermission,
    onRequestCameraPermission = { /* launcher */ },
    onSurfaceTextureReady = { surfaceTexture ->
        viewModel.startCameraPreview(surfaceTexture)
    },
    onSurfaceTextureDestroyed = {
        viewModel.stopCameraPreview()
    }
)
```

#### 6.2 Invio Frame a Gemini

Gia implementato in `GeminiLiveCameraManager`:
- Cattura ogni 3 secondi
- JPEG quality 70%
- Max 1024x1024
- Callback `onImageCaptured` -> WebSocket

**Riferimento:** [GeminiLiveCameraManager.kt:321-343](features/chat/src/main/java/com/lifo/chat/data/camera/GeminiLiveCameraManager.kt)

---

### FASE 7: Gestione Barge-In e Interruzioni

#### 7.1 Barge-In Detection

```kotlin
// In AvatarLiveChatViewModel
private fun setupBargeInHandling() {
    // 1. Callback locale (rilevamento rapido)
    geminiAudioManager.onBargeInDetected = {
        handleSmartBargeIn()
    }

    // 2. Callback server (conferma VAD)
    geminiWebSocketClient.onInterrupted = {
        handleServerBargeIn()
    }
}

private fun handleSmartBargeIn() {
    // Stop immediato audio AI
    geminiAudioManager.handleInterruption()

    // Stop lip-sync
    liveAudioSource.handleInterruption(InterruptionReason.USER_BARGE_IN)

    // Reset avatar a neutral
    humanoidController?.setEmotion(Emotion.Neutral)

    // Update UI state
    _uiState.update { it.copy(turnState = TurnState.UserTurn) }
}
```

**Riferimento:** [LiveChatViewModel.kt:578-617](features/chat/src/main/java/com/lifo/chat/presentation/viewmodel/LiveChatViewModel.kt)

---

### FASE 8: Emozioni Dinamiche dal Testo

#### 8.1 Rilevamento Emozione da Transcript

```kotlin
geminiWebSocketClient.onTextReceived = { text ->
    // 1. Aggiorna transcript UI
    _uiState.update { it.copy(currentTranscript = text) }

    // 2. Rileva emozione dal testo
    val detectedEmotion = liveEmotionBridge.detectEmotionFromText(text)
    humanoidController?.setEmotion(detectedEmotion)

    // 3. Prepara lip-sync
    liveAudioSource.prepareWithText(text)
}
```

---

## 5. Diagramma Moduli e Dipendenze

```
+------------------------------------------------------------------+
|                         APP MODULE                                |
|  +------------------------------------------------------------+  |
|  |                  AvatarLiveChatScreen                      |  |
|  |  - HumanoidAvatarView (Layer 1)                            |  |
|  |  - SimpleLiveCameraPreview (Layer 2)                       |  |
|  |  - GeminiLiquidVisualizer (Layer 3)                        |  |
|  |  - Controls (Layer 4)                                      |  |
|  +------------------------------------------------------------+  |
|                              |                                    |
|  +------------------------------------------------------------+  |
|  |                AvatarLiveChatViewModel                     |  |
|  +------------------------------------------------------------+  |
|                              |                                    |
|  +---------------------------+---------------------------+        |
|  |                           |                           |        |
|  v                           v                           v        |
| LiveEmotionBridge    SynchronizedSpeech         (Repositories)   |
|                       Controller                                  |
+------------------------------------------------------------------+
           |                   |                      |
           v                   v                      v
+------------------+  +------------------+  +------------------+
|  HUMANOID MODULE |  |   CHAT MODULE    |  |   DATA MODULE    |
|  - Controller    |  |  - WebSocket     |  |  - ChatRepo      |
|  - LipSync       |  |  - AudioManager  |  |  - MongoRepo     |
|  - BlendShapes   |  |  - CameraManager |  +------------------+
|  - Renderer      |  |  - AudioSource   |
+------------------+  +------------------+
```

---

## 6. File da Creare/Modificare

### 6.1 Nuovi File

| # | File | Descrizione |
|---|------|-------------|
| 1 | `app/.../screen/AvatarLiveChatScreen.kt` | UI principale |
| 2 | `app/.../viewmodel/AvatarLiveChatViewModel.kt` | Logica business |
| 3 | `app/.../integration/LiveEmotionBridge.kt` | Mapping emozioni |
| 4 | `app/.../navigation/AvatarLiveChatNavigation.kt` | Route |

### 6.2 File da Modificare

| # | File | Modifica |
|---|------|----------|
| 1 | `app/.../navigation/NavGraph.kt` | Aggiungere route AvatarLiveChat |
| 2 | `app/.../navigation/Screen.kt` | Aggiungere enum AvatarLiveChat |
| 3 | `features/chat/di/GeminiLiveModule.kt` | Esporre dipendenze se necessario |

### 6.3 File da Riutilizzare (Nessuna Modifica)

- `GeminiLiveWebSocketClient.kt`
- `GeminiLiveAudioManager.kt`
- `GeminiLiveCameraManager.kt`
- `GeminiLiveAudioSource.kt`
- `SynchronizedSpeechControllerImpl.kt`
- `HumanoidController.kt` / `HumanoidControllerImpl.kt`
- `LipSyncController.kt`
- `VrmBlendShapeController.kt`
- `FilamentRenderer.kt`
- `GeminiLiquidVisualizer.kt`
- `SimpleLiveCameraPreview.kt`

---

## 7. Checklist Implementazione

### Fase 1: Setup
- [ ] Creare `AvatarLiveChatNavigation.kt`
- [ ] Aggiungere route a `NavGraph.kt`
- [ ] Aggiungere `Screen.AvatarLiveChat` enum

### Fase 2: ViewModel
- [ ] Creare `AvatarLiveChatViewModel.kt`
- [ ] Definire `AvatarLiveChatUiState`
- [ ] Iniettare tutte le dipendenze
- [ ] Implementare `connectToRealtime()`
- [ ] Implementare `disconnectFromRealtime()`
- [ ] Implementare gestione audio callbacks
- [ ] Implementare gestione camera

### Fase 3: Emotion Bridge
- [ ] Creare `LiveEmotionBridge.kt`
- [ ] Implementare mapping `AIEmotion` -> `Emotion`
- [ ] Implementare rilevamento emozione da testo

### Fase 4: UI Screen
- [ ] Creare `AvatarLiveChatScreen.kt`
- [ ] Implementare Layer 1: Avatar VRM
- [ ] Implementare Layer 2: Camera Preview PIP
- [ ] Implementare Layer 3: Liquid Visualizer
- [ ] Implementare Layer 4: Top Bar
- [ ] Implementare Layer 5: Transcript Overlay
- [ ] Implementare Layer 6: Bottom Controls
- [ ] Gestire permessi audio/camera

### Fase 5: Lip-Sync Integration
- [ ] Connettere `GeminiLiveAudioSource` a `SynchronizedSpeechController`
- [ ] Connettere `HumanoidController` come animation target
- [ ] Testare sincronizzazione audio-lip

### Fase 6: Camera Integration
- [ ] Connettere preview camera
- [ ] Verificare invio frame a Gemini
- [ ] Testare reazioni AI a contenuto visivo

### Fase 7: Barge-In
- [ ] Implementare gestione barge-in locale
- [ ] Implementare gestione barge-in server
- [ ] Testare interruzione fluida

### Fase 8: Testing
- [ ] Test connessione WebSocket
- [ ] Test streaming audio bidirezionale
- [ ] Test lip-sync sincronizzato
- [ ] Test emozioni dinamiche
- [ ] Test camera streaming
- [ ] Test barge-in e interruzioni
- [ ] Test permessi e fallback

---

## 8. Considerazioni Tecniche

### 8.1 Performance

- **Thread Safety**: Usare `synchronized` per AudioTrack come in `GeminiLiveAudioManager`
- **Memory**: Limitare coda audio a 50 chunks / 500KB
- **Rendering**: Filament su thread dedicato, non bloccare UI

### 8.2 Latency

- **Audio**: 20ms chunk size per bassa latenza
- **Lip-Sync**: 30fps updates (~33ms) per smoothness
- **Camera**: 3s interval e accettabile per contesto

### 8.3 Error Handling

- **Network**: Retry su disconnessione WebSocket
- **Audio**: Fallback su errori AudioTrack
- **Camera**: Cascade di risoluzioni fallback

### 8.4 Lifecycle

- **DisposableEffect**: Cleanup risorse su exit screen
- **ViewModel.onCleared()**: Release audio/camera managers
- **Filament**: Stop controllers PRIMA di cleanup engine

---

## 9. Riferimenti Codice Chiave

| Funzionalita | File | Linee |
|--------------|------|-------|
| WebSocket Setup | `GeminiLiveWebSocketClient.kt` | 158-252 |
| Audio Recording | `GeminiLiveAudioManager.kt` | 111-228 |
| Audio Playback | `GeminiLiveAudioManager.kt` | 291-362 |
| Camera Capture | `GeminiLiveCameraManager.kt` | 321-376 |
| Lip-Sync Events | `GeminiLiveAudioSource.kt` | 63-131 |
| Speech Sync | `SynchronizedSpeechControllerImpl.kt` | 75-117 |
| Avatar Control | `HumanoidControllerImpl.kt` | 136-179 |
| Emotion Mapping | `EmotionBridge.kt` | Full file |
| UI Layout | `LiveScreen.kt` | 109-226 |
| Avatar View | `AvatarChatScreen.kt` | 136-236 |

---

## 10. Timeline Suggerita

| Fase | Durata Stimata | Priorita |
|------|----------------|----------|
| Fase 1: Setup | 2-4 ore | Alta |
| Fase 2: ViewModel | 4-6 ore | Alta |
| Fase 3: Emotion Bridge | 1-2 ore | Media |
| Fase 4: UI Screen | 4-6 ore | Alta |
| Fase 5: Lip-Sync | 2-4 ore | Alta |
| Fase 6: Camera | 1-2 ore | Media |
| Fase 7: Barge-In | 2-3 ore | Media |
| Fase 8: Testing | 4-8 ore | Alta |

**Totale stimato**: 20-35 ore di sviluppo

---

## 11. Note Finali

Questo piano riutilizza al massimo il codice esistente, minimizzando la duplicazione e sfruttando l'architettura pulita gia implementata. I componenti core (WebSocket, Audio, Camera, Lip-Sync) sono gia production-ready e richiedono solo orchestrazione nel nuovo ViewModel.

L'integrazione principale e nel `AvatarLiveChatViewModel` che funge da "cervello" collegando:
1. Input utente (audio + camera) -> Gemini Live API
2. Output Gemini (audio + testo) -> Avatar (lip-sync + emozioni)

---

*Documento generato da JARVIS per Sir.*
*"Se posso permettermi, Sir, questo e un progetto elegante. La sincronizzazione audio-lipsync sara impeccabile."*
