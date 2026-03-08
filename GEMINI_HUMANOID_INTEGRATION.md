# 🎭 Gemini Live ↔ Humanoid VRM Integration

## ✅ Integration Completed - AAA Grade Quality

Questa documentazione descrive l'integrazione completa tra Gemini Live API e il modulo Humanoid VRM, permettendo al VRM di reagire in tempo reale alle emozioni e all'audio di Gemini.

---

## 📦 Componenti Implementati

### 1. **GeminiLiveStateManager**
**Location:** `features/chat/src/main/java/com/lifo/chat/integration/GeminiLiveStateManager.kt`

**Responsabilità:**
- Singleton @Inject che fa da ponte tra Gemini Live e Humanoid
- Observable StateFlows per stati reattivi
- Parsing automatico emozioni dalle trascrizioni
- Eventi audio per coordinare lip-sync

**API Pubblica:**
```kotlin
class GeminiLiveStateManager @Inject constructor(
    private val emotionParser: GeminiEmotionParser
) {
    // Connection state
    val connectionStatus: StateFlow<ConnectionStatus>
    val turnState: StateFlow<TurnState>
    val isMuted: StateFlow<Boolean>

    // Audio levels
    val userVoiceLevel: StateFlow<Float>
    val aiVoiceLevel: StateFlow<Float>
    val emotionalIntensity: StateFlow<Float>

    // Transcript & Events
    val currentTranscript: StateFlow<String>
    val partialTranscript: StateFlow<String>
    val emotionEvents: StateFlow<EmotionEvent?>  // ⭐ Key for emotions
    val audioPlaybackEvents: StateFlow<AudioPlaybackEvent?> // ⭐ Key for lip-sync

    // Update methods (called by LiveChatViewModel)
    fun updateConnectionStatus(status: ConnectionStatus)
    fun updateTranscript(text: String, isPartial: Boolean)
    fun notifyAudioPlaybackStart(text: String, durationMs: Long)
    fun notifyAudioPlaybackStop()
}
```

**Utilizzo in LiveChatViewModel:**
```kotlin
// Già integrato - sinc ronizza automaticamente tutti gli stati
geminiLiveStateManager.updateConnectionStatus(ConnectionStatus.Connected)
geminiLiveStateManager.updateTranscript("[happy] Che bella giornata!", false)
geminiLiveStateManager.notifyAudioPlaybackStart("Hello!", 2000L)
```

---

### 2. **GeminiEmotionParser**
**Location:** `features/chat/src/main/java/com/lifo/chat/integration/GeminiEmotionParser.kt`

**Responsabilità:**
- Parsing tag `[emotion]` stile Amica
- Supporto 14 emozioni standard + alias
- Validazione e normalizzazione

**Emozioni Supportate:**
```kotlin
// Amica standard
"neutral", "happy", "angry", "sad", "relaxed", "surprised",
"shy", "jealous", "bored", "serious", "suspicious", "victory",
"sleep", "love"

// Humanoid extended
"thinking", "excited", "calm", "confused", "worried", "disappointed"

// Aliases
"joyful" → "happy"
"upset" → "sad"
"furious" → "angry"
// ... e molti altri
```

**Utilizzo:**
```kotlin
val result = emotionParser.parseEmotion("[happy] I'm so excited!")
// result.emotion = "happy"
// result.intensity = 1.0f
// result.cleanText = "I'm so excited!"
```

---

### 3. **GeminiEmotionMapper**
**Location:** `features/humanoid/src/main/java/com/lifo/humanoid/integration/GeminiEmotionMapper.kt`

**Responsabilità:**
- Mapping emotion string → Humanoid Emotion sealed class
- Fallback semantico intelligente
- Preservazione intensità

**Utilizzo:**
```kotlin
val emotion = emotionMapper.mapEmotion("happy", 1.0f)
// Returns: Emotion.Happy(1.0f)

val emotion2 = emotionMapper.mapEmotion("joyful", 0.8f)
// Returns: Emotion.Happy(0.8f) - via alias
```

---

### 4. **HumanoidGeminiIntegration**
**Location:** `features/humanoid/src/main/java/com/lifo/humanoid/integration/HumanoidGeminiIntegration.kt`

**Responsabilità:**
- Coordinatore principale dell'integrazione
- Osserva GeminiLiveStateManager
- Applica emozioni e lip-sync al HumanoidViewModel

**Architettura:**
```
GeminiLiveStateManager (emits events)
    ↓
HumanoidGeminiIntegration (observes & coordinates)
    ↓
HumanoidViewModel (applies to VRM)
```

**Inizializzazione Automatica:**
```kotlin
// In HumanoidViewModel.init {}
humanoidGeminiIntegration.startObserving(viewModelScope, this)
```

**Flow Completo:**
```kotlin
// 1. Emotion Event
geminiLiveStateManager.emotionEvents.collectLatest { event ->
    val emotion = emotionMapper.mapEmotion(event.emotion, event.intensity)
    humanoidViewModel.setEmotion(emotion)
}

// 2. Audio Playback Event (lip-sync)
geminiLiveStateManager.audioPlaybackEvents.collectLatest { event ->
    if (event.isPlaying) {
        humanoidViewModel.speakText(event.text, event.durationMs)
        humanoidViewModel.setSpeaking(true)
    } else {
        humanoidViewModel.stopSpeaking()
        humanoidViewModel.setSpeaking(false)
    }
}

// 3. Connection Status
geminiLiveStateManager.connectionStatus.collectLatest { status ->
    when (status) {
        Connected -> // Avatar active
        Disconnected -> humanoidViewModel.setEmotion(Emotion.Neutral)
        Error -> humanoidViewModel.setEmotion(Emotion.Worried(0.6f))
    }
}

// 4. Turn State (listening indicator)
geminiLiveStateManager.turnState.collectLatest { turnState ->
    when (turnState) {
        UserTurn -> humanoidViewModel.setListening(true)
        AgentTurn -> humanoidViewModel.setListening(false)
        WaitingForUser -> humanoidViewModel.setListening(false)
    }
}
```

---

### 5. **Hilt Modules**

#### GeminiIntegrationModule
**Location:** `features/chat/src/main/java/com/lifo/chat/di/GeminiIntegrationModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object GeminiIntegrationModule {
    @Provides @Singleton
    fun provideGeminiEmotionParser(): GeminiEmotionParser

    @Provides @Singleton
    fun provideGeminiLiveStateManager(
        emotionParser: GeminiEmotionParser
    ): GeminiLiveStateManager
}
```

#### HumanoidIntegrationModule
**Location:** `features/humanoid/src/main/java/com/lifo/humanoid/di/HumanoidIntegrationModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object HumanoidIntegrationModule {
    @Provides @Singleton
    fun provideGeminiEmotionMapper(): GeminiEmotionMapper

    @Provides @Singleton
    fun provideHumanoidGeminiIntegration(
        geminiLiveStateManager: GeminiLiveStateManager,
        emotionMapper: GeminiEmotionMapper
    ): HumanoidGeminiIntegration
}
```

---

## 🔄 Flow Completo: User → Gemini → VRM

### Scenario: Utente parla con Gemini, VRM reagisce

```
1. User: "Come va oggi?"
   ↓
2. LiveChatViewModel riceve trascrizione utente
   ↓
3. Gemini processa e risponde: "[happy] Benissimo! Che bella giornata!"
   ↓
4. LiveChatViewModel.onTextReceived()
   ├─> geminiLiveStateManager.updateTranscript("[happy] Benissimo!", false)
   │   ├─> GeminiEmotionParser estrae: emotion="happy", intensity=1.0f
   │   └─> Emette EmotionEvent("happy", 1.0f, "Benissimo!")
   │
   └─> geminiAudioManager starts TTS playback
       └─> geminiLiveStateManager.notifyAudioPlaybackStart("Benissimo!", 2500L)
           └─> Emette AudioPlaybackEvent(isPlaying=true, text="Benissimo!", durationMs=2500)

5. HumanoidGeminiIntegration osserva gli eventi:

   ├─> EmotionEvent ricevuto
   │   ├─> GeminiEmotionMapper: "happy" → Emotion.Happy(1.0f)
   │   └─> HumanoidViewModel.setEmotion(Emotion.Happy(1.0f))
   │       └─> VrmBlendShapeController.setCategoryWeights(EMOTION, {"happy": 1.0f})
   │           └─> 🎭 VRM mostra espressione felice!
   │
   └─> AudioPlaybackEvent ricevuto
       └─> HumanoidViewModel.speakText("Benissimo!", 2500L)
           └─> LipSyncController.speak()
               ├─> PhonemeConverter: "Benissimo!" → [B, E, N, I, S, S, I, M, O]
               ├─> VisemeMapper: phonemes → visemes
               └─> VrmBlendShapeController.setCategoryWeights(LIPSYNC, viseme weights)
                   └─> 👄 VRM muove le labbra sincronizzate!

6. Gemini TTS finisce
   ↓
7. geminiLiveStateManager.notifyAudioPlaybackStop()
   ↓
8. HumanoidViewModel.stopSpeaking()
   └─> 😌 VRM torna a stato idle
```

---

## 🎨 Emotion Tag System (Amica-Compatible)

### Come Gemini deve rispondere

Per attivare le emozioni, Gemini deve includere tag all'inizio della risposta:

```
✅ CORRETTO:
"[happy] Sono felice di aiutarti!"
"[sad] Mi dispiace sentire questo."
"[thinking] Fammi riflettere un attimo..."
"[surprised] Wow! Non me lo aspettavo!"

❌ SBAGLIATO:
"Sono felice [happy] di aiutarti!"  // Tag a metà
"Sono felice di aiutarti! [happy]"  // Tag alla fine
```

### Prompt System per Gemini

Aggiungi al system prompt di Gemini:

```
You are Amica, an empathetic AI assistant with a virtual avatar.
Express emotions by starting your responses with emotion tags:
[neutral], [happy], [sad], [angry], [surprised], [thinking], [excited], [calm], etc.

Examples:
- "[happy] I'm so glad to help you!"
- "[thinking] Let me consider that..."
- "[surprised] Wow, that's interesting!"

Always use emotion tags to make your avatar express the right feelings.
```

---

## 📊 Stati e Priorità

### Blend Shape Priority System

Il VrmBlendShapeController gestisce le priorità:

```
Priority (highest to lowest):
1. LIPSYNC    - Mouth movements (highest priority)
2. BLINK      - Eye blinks (overlay on everything)
3. EMOTION    - Facial expressions
4. IDLE       - Idle/neutral state (lowest priority)
```

### Esempio Blending

```kotlin
// Durante il parlato:
VrmBlendShapeController {
    LIPSYNC: {"aa": 0.8, "oh": 0.2}  // Mouth moving
    BLINK: {"blink": 1.0}             // Eyes blinking
    EMOTION: {"happy": 0.7}           // Happy expression
}
// Result: VRM sorridente che parla e sbatte le palpebre!
```

---

## 🧪 Testing

### Test Manuale dell'Integrazione

1. **Avvia l'app** e naviga a `LiveScreen` (Gemini Live)
2. **Connetti a Gemini** (pulsante Connect)
3. **Parla con Gemini** e chiedi risposte emotive:
   - "Raccontami una buona notizia!" → Dovrebbe rispondere con `[happy]`
   - "Dammi una notizia triste" → Dovrebbe rispondere con `[sad]`
4. **Osserva il VRM** (se apri HumanoidScreen contemporaneamente):
   - Le emozioni si applicano automaticamente
   - Le labbra si muovono sincronizzate con l'audio

### Debug

Abilita i log per vedere il flow:

```kotlin
// In GeminiLiveStateManager
Log.d(TAG, "🎭 Emotion event: $emotion (intensity: $intensity)")

// In HumanoidGeminiIntegration
Log.d(TAG, "✓ Avatar emotion set to: ${emotion.getName()}")

// In LipSyncController
Log.d(TAG, "👄 Lip-sync activated for ${durationMs}ms")
```

---

## 🚀 Future Enhancements

### Possibili Migliorie

1. **Intensity Control**
   - Estendere parser per supportare `[happy:0.5]` syntax
   - Modulare intensità emozione in base al contesto

2. **Emotion Blending**
   - Permettere emozioni composite: `[happy+surprised]`
   - Transizioni smooth tra emozioni

3. **Context-Aware Emotions**
   - Analisi sentiment del testo se tag non presente
   - ML model per inferire emozioni automaticamente

4. **Animation Triggers**
   - Tag per trigger animazioni: `[wave]`, `[nod]`, `[shake_head]`
   - Gesti sincronizzati con il parlato

---

## 📝 Troubleshooting

### VRM non mostra emozioni

**Problema:** L'avatar rimane neutrale anche con tag emozioni.

**Soluzioni:**
1. Verifica che Gemini includa tag all'inizio: `"[happy] text"`
2. Controlla i log per `GeminiEmotionParser` - deve stampare "✓ Parsed emotion"
3. Verifica che `HumanoidGeminiIntegration.startObserving()` sia stato chiamato

### Lip-sync non funziona

**Problema:** Le labbra non si muovono durante l'audio.

**Soluzioni:**
1. Verifica che `notifyAudioPlaybackStart()` sia chiamato
2. Controlla che `LipSyncController` riceva il testo
3. Verifica blend shapes con debug panel

### Emozioni errate

**Problema:** VRM mostra emozione sbagliata.

**Soluzioni:**
1. Verifica il mapping in `GeminiEmotionMapper`
2. Controlla tag Gemini (case-insensitive, ma deve essere valido)
3. Usa alias se necessario

---

## ✅ Checklist Integrazione Completa

- [x] GeminiLiveStateManager creato e integrato
- [x] GeminiEmotionParser con supporto 14+ emozioni
- [x] GeminiEmotionMapper con fallback semantico
- [x] HumanoidGeminiIntegration coordinator
- [x] LiveChatViewModel sincronizzato
- [x] HumanoidViewModel integrato
- [x] Hilt modules configurati
- [x] Emotion tag parsing
- [x] Lip-sync coordination
- [x] Connection status handling
- [x] Turn state management
- [x] Documentazione completa

---

## 👨‍💻 Credits

**Architecture & Implementation:** AAA-Grade Quality
**Integration Pattern:** Inspired by Amica's emotion system
**VRM Control:** Filament-based rendering with blend shapes
**Real-time Sync:** Kotlin Coroutines & StateFlow

---

**Status:** ✅ Production Ready
**Version:** 1.0.0
**Last Updated:** 2025-11-30
