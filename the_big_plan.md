# 🎙️ VoiceAI Live — Piano di Progetto

## Conversational AI Assistant Full-Duplex, 100% On-Device

> Riprodurre l'esperienza di Gemini Live con inferenza completamente locale:
> LLM, STT, TTS e VAD on-device, zero cloud, zero latenza di rete.

**Stack**: Kotlin Multiplatform · Compose Multiplatform · Material 3 · Multi-modulo
**Target**: Android · Desktop (JVM/Linux/macOS/Windows)

---

## 1. Vision e Obiettivi

### 1.1 Cosa stiamo costruendo

Un assistente vocale conversazionale che offre un'interazione **full-duplex in tempo reale** — l'utente parla naturalmente, l'AI risponde con voce, e l'utente può interrompere in qualsiasi momento. Tutto gira in locale sul dispositivo, senza connessione internet.

### 1.2 Principi chiave

- **Privacy first**: nessun dato audio o testuale lascia il dispositivo
- **Conversazione naturale**: barge-in, bassa latenza, risposte brevi e colloquiali
- **Modularità**: ogni engine (LLM, STT, TTS, VAD) è un modulo sostituibile
- **Multiplatform**: codice condiviso al massimo, platform-specific solo dove necessario
- **Agentico**: l'AI può eseguire azioni reali tramite function calling — meteo, timer, contatti, smart home, file system, e qualsiasi tool custom registrato

### 1.3 Target di latenza

| Metrica | Obiettivo | Accettabile |                                                                                          
|---|---|---|
| Fine parlato utente → primo audio AI | < 800ms | < 1500ms |
| Barge-in (interruzione) | < 150ms | < 300ms |
| VAD speech detection | < 100ms | < 200ms |
| STT streaming partial results | < 300ms | < 500ms |
| LLM time-to-first-token | < 300ms | < 600ms |
| TTS primo chunk audio | < 150ms | < 300ms |
| Tool call: parse + execute + re-inject | < 1500ms | < 3000ms |
| Tool call: feedback vocale ("un momento...") | < 500ms | < 800ms |

---

## 2. Architettura

### 2.1 Pipeline Streaming Full-Duplex

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│   ┌────────────┐    ┌───────┐    ┌───────────────┐              │
│   │ MICROPHONE │───►│  VAD  │───►│ STT Streaming │              │
│   │ (always on)│    │Silero │    │  Vosk/Whisper  │              │
│   └────────────┘    └───┬───┘    └──────┬────────┘              │
│                         │               │                        │
│                    VadEvent         SttResult                    │
│                         │               │                        │
│                    ┌────▼───────────────▼─────┐                 │
│                    │   CONVERSATION MANAGER    │                 │
│                    │  (Stato, Orchestrazione,  │                 │
│                    │   History, Barge-in)      │                 │
│                    └────────────┬──────────────┘                │
│                                │                                 │
│                     Prompt (con tool definitions)                │
│                                │                                 │
│                    ┌───────────▼───────────┐                    │
│                    │     LLM Streaming     │◄──────────┐        │
│                    │   llama.cpp / MLKit    │           │        │
│                    └───────────┬───────────┘           │        │
│                                │                       │        │
│                     ┌──────────▼──────────┐            │        │
│                     │    OUTPUT PARSER     │            │        │
│                     │ (testo vs tool_call) │            │        │
│                     └──┬──────────────┬───┘            │        │
│                        │              │                 │        │
│                   Testo puro    ToolCall detected       │        │
│                        │              │                 │        │
│                        │    ┌─────────▼──────────┐     │        │
│                        │    │   TOOL EXECUTOR    │     │        │
│                        │    │ (esegue la tool,   │     │        │
│                        │    │  restituisce       │     │        │
│                        │    │  risultato)        │     │        │
│                        │    └─────────┬──────────┘     │        │
│                        │              │                 │        │
│                        │         Tool Result ──────────┘        │
│                        │      (re-inject in LLM)                │
│                        │                                         │
│                    ┌───▼───────────────────┐                    │
│                    │   SENTENCE BUFFER     │                    │
│                    │  (accumula → frasi)   │                    │
│                    └───────────┬───────────┘                    │
│                                │                                 │
│                          Frasi complete                           │
│                                │                                 │
│                    ┌───────────▼───────────┐                    │
│                    │    TTS Streaming      │                    │
│                    │       Piper           │                    │
│                    └───────────┬───────────┘                    │
│                                │                                 │
│                          Audio chunks                            │
│                                │                                 │
│                    ┌───────────▼───────────┐                    │
│                    │    AUDIO PLAYER       │──── ► Speaker      │
│                    └───────────────────────┘                    │
│                                                                  │
│   ◄── INTERRUPT LOOP ──────────────────────────────────────►    │
│   Se VAD rileva voce durante AiSpeaking/ToolExecuting:          │
│     1. Cancel LLM generation / Tool execution                   │
│     2. Cancel TTS synthesis                                     │
│     3. Stop audio playback                                      │
│     4. Torna a stato Listening                                  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 2.2 State Machine della Conversazione

```
                    ┌──────────┐
          ┌────────►│   IDLE   │◄──── endConversation()
          │         └────┬─────┘
          │              │ startConversation()
          │         ┌────▼─────┐
          │    ┌───►│LISTENING │◄─────────────────────────┐
          │    │    └────┬─────┘                           │
          │    │         │ VAD: SpeechStart                │
          │    │    ┌────▼──────────┐                      │
          │    │    │ USER_SPEAKING │                      │
          │    │    │ (partial STT) │                      │ barge-in
          │    │    └────┬──────────┘                      │
          │    │         │ VAD: SpeechEnd                  │
          │    │    ┌────▼─────┐                           │
          │    │    │ THINKING │ (LLM loading)             │
          │    │    └────┬─────┘                           │
          │    │         │                                 │
          │    │    ┌────▼──────────────┐                  │
          │    │    │ LLM genera output │                  │
          │    │    └────┬─────────┬────┘                  │
          │    │         │         │                       │
          │    │    testo puro  tool_call detected         │
          │    │         │         │                       │
          │    │         │    ┌────▼───────────┐           │
          │    │         │    │TOOL_EXECUTING  │           │
          │    │         │    │(esegue action, │───────────┘
          │    │         │    │ feedback vocale│
          │    │         │    └────┬───────────┘
          │    │         │         │ tool result → re-inject LLM
          │    │         │         │
          │    │    ┌────▼─────────▼──┐
          │    │    │  AI_SPEAKING    │────────────────────┘
          │    │    │ (streaming TTS) │
          │    │    └────┬───────────┘
          │    │         │ TTS finished
          │    └─────────┘
          │
          └── error / timeout
```

---

## 3. Struttura Multi-Modulo

```
voiceai-live/
│
├── build-logic/                          # Convention plugins Gradle
│   └── convention/
│       ├── KmpLibraryConventionPlugin    # Setup KMP base
│       ├── ComposeConventionPlugin       # Compose Multiplatform
│       └── AndroidAppConventionPlugin    # Android specifics
│
├── core/
│   ├── common/                           # Utilities, estensioni, Result wrapper
│   │   └── src/commonMain/
│   ├── model/                            # Data classes condivise (Message, ConversationTurn, etc.)
│   │   └── src/commonMain/
│   ├── ui/                               # Design system, tema M3, componenti
│   │   └── src/commonMain/
│   ├── audio/                            # AudioCapture, AudioPlayer (expect/actual)
│   │   ├── src/commonMain/               #   Interfacce
│   │   ├── src/androidMain/              #   AudioRecord / AudioTrack
│   │   └── src/desktopMain/              #   javax.sound.sampled
│   ├── conversation/                     # ConversationManager, SentenceBuffer, State
│   │   └── src/commonMain/
│   └── function-calling/                 # Tool registry, parser, executor, output router
│       └── src/commonMain/
│           ├── ToolRegistry.kt           #   Registro tool disponibili
│           ├── ToolDefinition.kt         #   Schema definizione tool (nome, params, descrizione)
│           ├── ToolCall.kt               #   Data class per chiamata tool parsed
│           ├── ToolResult.kt             #   Risultato esecuzione tool
│           ├── ToolExecutor.kt           #   Dispatcher che esegue la tool corretta
│           ├── LlmOutputParser.kt        #   Parsing output LLM → testo o tool_call
│           └── PromptBuilder.kt          #   Inietta definizioni tool nel prompt
│
├── engine/
│   ├── vad/                              # Voice Activity Detection
│   │   ├── src/commonMain/               #   VadEngine interface
│   │   ├── src/androidMain/              #   Silero VAD via ONNX Runtime Android
│   │   └── src/desktopMain/              #   Silero VAD via ONNX Runtime JVM
│   ├── stt/                              # Speech-to-Text
│   │   ├── src/commonMain/               #   SttEngine interface
│   │   ├── src/androidMain/              #   Vosk Android SDK
│   │   └── src/desktopMain/              #   Vosk JNI
│   ├── llm/                              # Large Language Model
│   │   ├── src/commonMain/               #   LlmEngine interface
│   │   ├── src/androidMain/              #   llama.cpp Android (JNI/NDK)
│   │   └── src/desktopMain/              #   java-llama.cpp
│   └── tts/                              # Text-to-Speech
│       ├── src/commonMain/               #   TtsEngine interface
│       ├── src/androidMain/              #   Piper Android / Android native TTS
│       └── src/desktopMain/              #   Piper native
│
├── tools/                                    # Implementazioni concrete delle tool
│   ├── tools-core/                           # Interfacce base + tool built-in pure Kotlin
│   │   └── src/commonMain/
│   │       ├── DateTimeTool.kt               #   Data, ora, fuso orario
│   │       ├── TimerTool.kt                  #   Timer e allarmi
│   │       ├── MathTool.kt                   #   Calcoli matematici
│   │       ├── UnitConverterTool.kt          #   Conversioni unità
│   │       └── KnowledgeTool.kt              #   Lookup su DB locale
│   ├── tools-device/                         # Tool platform-specific (expect/actual)
│   │   ├── src/commonMain/
│   │   ├── src/androidMain/
│   │   │   ├── ContactsTool.kt               #   Ricerca contatti (ContentProvider)
│   │   │   ├── CalendarTool.kt               #   Eventi calendario
│   │   │   ├── NotificationTool.kt           #   Invio notifiche
│   │   │   ├── SystemSettingsTool.kt         #   Brightness, volume, wifi, bluetooth
│   │   │   ├── AppLauncherTool.kt            #   Apri app
│   │   │   └── SmsTool.kt                    #   Invio SMS (con conferma utente)
│   │   └── src/desktopMain/
│   │       ├── FileSystemTool.kt             #   Operazioni file
│   │       ├── ClipboardTool.kt              #   Copia/incolla
│   │       ├── SystemInfoTool.kt             #   Info sistema (CPU, RAM, disco)
│   │       └── ShellCommandTool.kt           #   Esecuzione comandi (sandboxed)
│   ├── tools-network/                        # Tool che richiedono rete (opzionali)
│   │   └── src/commonMain/
│   │       ├── WeatherTool.kt                #   Meteo (API open-meteo, gratuita)
│   │       ├── WebSearchTool.kt              #   Ricerca web (SearXNG locale o API)
│   │       └── HttpRequestTool.kt            #   Richieste HTTP generiche
│   └── tools-smarthome/                      # Integrazione domotica (opzionale)
│       └── src/commonMain/
│           ├── HomeAssistantTool.kt          #   Home Assistant API
│           └── MqttTool.kt                   #   Controllo dispositivi MQTT
│
├── feature/
│   ├── conversation/                     # UI principale della conversazione live
│   │   └── src/commonMain/
│   │       ├── ConversationScreen.kt
│   │       ├── ConversationViewModel.kt
│   │       ├── components/
│   │       │   ├── AnimatedOrb.kt        # Blob/orb animato stile Gemini
│   │       │   ├── LiveSubtitles.kt      # Sottotitoli real-time
│   │       │   └── ConversationControls.kt
│   │       └── state/
│   │           └── ConversationUiState.kt
│   ├── settings/                         # Configurazione modelli, voci, lingua
│   │   └── src/commonMain/
│   ├── models/                           # Download e gestione modelli
│   │   └── src/commonMain/
│   └── history/                          # Storico conversazioni
│       └── src/commonMain/
│
├── app/
│   ├── android/                          # Android Application entry point
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   └── MainActivity.kt
│   │   └── build.gradle.kts
│   └── desktop/                          # Desktop Application entry point
│       ├── src/main/
│       │   └── Main.kt
│       └── build.gradle.kts
│
├── gradle/
│   └── libs.versions.toml               # Version catalog
├── settings.gradle.kts
└── build.gradle.kts
```

---

## 4. Engine Specifications

### 4.1 VAD — Voice Activity Detection

**Tecnologia**: Silero VAD v5 (modello ONNX, ~2MB)

**Funzionamento**: riceve chunk audio dal microfono (16kHz, mono, 16-bit PCM), classifica ogni frame come speech/non-speech.

```kotlin
// commonMain
interface VadEngine {
    suspend fun loadModel()
    fun processAudio(audioStream: Flow<AudioFrame>): Flow<VadEvent>
    fun release()
}

sealed class VadEvent {
    object SpeechStart : VadEvent()
    object SpeechEnd : VadEvent()
    data class SpeechSegment(val audio: ByteArray, val durationMs: Long) : VadEvent()
}

data class VadConfig(
    val sampleRate: Int = 16_000,
    val frameSizeMs: Int = 30,              // 30ms per frame
    val speechThreshold: Float = 0.5f,
    val minSpeechDurationMs: Int = 250,     // ignora suoni < 250ms
    val silenceDurationMs: Int = 700,       // silenzio per considerare fine speech
    val speechPadMs: Int = 30               // padding intorno allo speech
)
```

**Dipendenze**:
- `com.microsoft.onnxruntime:onnxruntime-android` (Android)
- `com.microsoft.onnxruntime:onnxruntime` (Desktop)
- Modello: `silero_vad.onnx` (~2MB)

### 4.2 STT — Speech-to-Text

**Tecnologia primaria**: Vosk (streaming, bassa latenza)
**Alternativa**: Whisper.cpp (migliore qualità, peggiore latenza)

**Requisito critico**: deve supportare **partial results** (testo parziale mentre l'utente sta ancora parlando).

```kotlin
// commonMain
interface SttEngine {
    suspend fun loadModel(modelPath: String)
    fun startRecognition(): Flow<SttResult>
    fun feedAudio(frame: AudioFrame)
    fun stopRecognition(): String  // restituisce trascrizione finale
    fun release()
}

data class SttResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 0f
)

data class SttConfig(
    val language: String = "it",            // italiano
    val sampleRate: Int = 16_000,
    val enablePartialResults: Boolean = true,
    val enablePunctuation: Boolean = true
)
```

**Modelli consigliati Vosk**:
- `vosk-model-small-it-0.22` (~50MB) — italiano, leggero
- `vosk-model-it-0.22` (~1.2GB) — italiano, alta qualità
- `vosk-model-en-us-0.22` (~50MB) — inglese

**Modelli consigliati Whisper**:
- `whisper-tiny` (~75MB) — velocissimo, qualità discreta
- `whisper-base` (~150MB) — buon compromesso
- `whisper-small` (~500MB) — alta qualità

### 4.3 LLM — Large Language Model

**Tecnologia**: llama.cpp via JNI binding

```kotlin
// commonMain
interface LlmEngine {
    suspend fun loadModel(modelPath: String, config: LlmConfig)
    fun generate(messages: List<ChatMessage>): Flow<String>  // token streaming
    fun cancelGeneration()
    fun isLoaded(): Boolean
    fun release()
}

data class ChatMessage(
    val role: Role,         // SYSTEM, USER, ASSISTANT, TOOL
    val content: String
)

data class LlmConfig(
    val contextSize: Int = 2048,
    val batchSize: Int = 512,
    val threads: Int = 4,               // CPU threads
    val gpuLayers: Int = 0,             // layers offloaded to GPU
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 256,           // risposte brevi per voce
    val stopTokens: List<String> = emptyList()
)
```

**System prompt per conversazione vocale**:

```
You are a friendly, conversational voice assistant. You are having a real-time 
spoken conversation with the user.

Rules:
- Keep ALL responses between 1-3 sentences. Be concise.
- Be natural and conversational, as if talking to a friend.
- NEVER use markdown, bullet points, lists, or any formatting.
- NEVER use emojis or special characters.
- If a topic is complex, give a brief answer and ask if they want more detail.
- Use natural speech patterns — contractions, casual phrasing.
- If you don't understand, ask for clarification briefly.
- Respond in the same language the user speaks.

## Tool Use
You have access to tools that let you perform real actions (see Available Tools below).
When the user asks you to DO something (check weather, set a timer, send a message, 
control devices), use the appropriate tool.

Rules for tool use:
- Only call a tool when the user's request REQUIRES it. For general conversation, 
  just respond normally.
- After receiving a tool result, incorporate it naturally into your spoken response.
  Do NOT read raw data — summarize it conversationally.
  Example: Instead of "Temperature is 18.5 celsius, humidity 45 percent, condition sunny"
  say "It's about 18 degrees and sunny in Milan right now, nice weather!"
- If a tool fails, let the user know briefly and offer an alternative.
- You can chain multiple tool calls if needed, but keep it to 3 max.
- For sensitive actions (sending messages, making calls), ALWAYS confirm with the user first.
```

**Modelli consigliati** (quantizzati GGUF):

| Modello | Dimensione | RAM richiesta | Note |
|---|---|---|---|
| Phi-3 Mini 3.8B Q4_K_M | ~2.3 GB | ~3 GB | Ottimo rapporto qualità/dimensione |
| Gemma 2 2B Q5_K_M | ~1.8 GB | ~2.5 GB | Piccolo e veloce |
| Mistral 7B Q4_K_M | ~4.4 GB | ~5.5 GB | Alta qualità, serve device potente |
| Llama 3.2 3B Q4_K_M | ~2.0 GB | ~3 GB | Meta, buona qualità |
| Qwen 2.5 3B Q4_K_M | ~2.1 GB | ~3 GB | Molto buono in multilingua |

### 4.4 TTS — Text-to-Speech

**Tecnologia primaria**: Piper TTS (ONNX, voci di alta qualità)
**Fallback Android**: Android native `TextToSpeech` API

```kotlin
// commonMain
interface TtsEngine {
    suspend fun loadVoice(voicePath: String, config: TtsConfig)
    suspend fun synthesize(text: String): AudioData         // singola frase
    fun synthesizeStream(sentences: Flow<String>): Flow<AudioChunk>  // streaming
    fun cancelCurrent()
    fun release()
}

data class TtsConfig(
    val sampleRate: Int = 22_050,
    val speakingRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f
)

data class AudioChunk(
    val data: ByteArray,
    val sampleRate: Int,
    val isLast: Boolean
)
```

**Voci Piper consigliate**:
- Italiano: `it_IT-riccardo-x_low` (~18MB), `it_IT-paola-medium` (~75MB)
- Inglese: `en_US-amy-medium` (~75MB), `en_US-lessac-medium` (~75MB)
- Multilingua: voci disponibili per 30+ lingue

### 4.5 Function Calling — Tool Use

Il function calling permette all'LLM di **invocare azioni concrete** durante la conversazione — dall'ottenere l'ora corrente a controllare le luci di casa. È ciò che trasforma un chatbot vocale in un **assistente agentico**.

#### 4.5.1 Come funziona (il loop)

1. L'utente dice: "Che tempo fa domani a Milano?"
2. L'LLM riceve il messaggio + le definizioni delle tool disponibili nel prompt
3. Invece di rispondere in testo, l'LLM genera un **tool call** strutturato
4. Il `LlmOutputParser` rileva il tool call e lo estrae
5. Il `ToolExecutor` esegue la tool corrispondente (es. `WeatherTool`)
6. Il risultato viene re-iniettato nell'LLM come messaggio `tool_result`
7. L'LLM genera la risposta finale in linguaggio naturale: "Domani a Milano ci saranno 18 gradi con cielo sereno"
8. La risposta va al TTS normalmente

Questo loop può ripetersi: l'LLM può chiamare **più tool in sequenza** se necessario (es. "Quanto manca alla mia prossima riunione?" → CalendarTool → DateTimeTool → risposta).

#### 4.5.2 Formato Tool Call

I modelli locali non hanno un formato nativo di function calling come le API cloud. Serve un **formato strutturato nel prompt** che il modello impari a seguire. Due approcci:

**Approccio A — XML-style tags** (più robusto con modelli piccoli):

```
<tool_call>
<name>get_weather</name>
<arguments>{"location": "Milano", "date": "tomorrow"}</arguments>
</tool_call>
```

**Approccio B — JSON nativo** (per modelli che lo supportano, es. Hermes, Functionary):

```json
{"tool_call": {"name": "get_weather", "arguments": {"location": "Milano", "date": "tomorrow"}}}
```

**Approccio C — ChatML function calling** (modelli fine-tuned per tool use):

Alcuni modelli GGUF sono già fine-tuned per function calling nativo (es. `NousResearch/Hermes-2-Pro`, `meetkai/functionary`). Questi usano token speciali e formato ChatML con ruolo `tool`.

**Raccomandazione**: iniziare con Approccio A (XML tags), è il più affidabile con qualsiasi modello. Migrare a C se si usano modelli specializzati.

#### 4.5.3 Core Interfaces

```kotlin
// core/function-calling/

/**
 * Definizione di una tool che l'LLM può invocare.
 * Viene serializzata nel system prompt.
 */
@Serializable
data class ToolDefinition(
    val name: String,                       // "get_weather"
    val description: String,                // "Get weather forecast for a location"
    val parameters: List<ToolParameter>,    // parametri con tipo e descrizione
    val requiresConfirmation: Boolean = false // se true, chiedi conferma vocale prima di eseguire
)

@Serializable
data class ToolParameter(
    val name: String,                       // "location"
    val type: ParameterType,                // STRING, INT, FLOAT, BOOLEAN, ENUM
    val description: String,                // "City name or coordinates"
    val required: Boolean = true,
    val enumValues: List<String>? = null,   // valori possibili per ENUM
    val default: String? = null
)

enum class ParameterType { STRING, INT, FLOAT, BOOLEAN, ENUM }

/**
 * Tool call parsed dall'output dell'LLM
 */
data class ToolCall(
    val id: String = uuid4(),               // ID univoco per tracciamento
    val name: String,                       // nome della tool
    val arguments: Map<String, Any>,        // argomenti parsed
    val rawText: String                     // testo originale per debug
)

/**
 * Risultato dell'esecuzione di una tool
 */
sealed class ToolResult {
    abstract val toolCallId: String
    abstract val toolName: String

    data class Success(
        override val toolCallId: String,
        override val toolName: String,
        val data: String,                   // risultato serializzato (JSON o testo)
        val displayData: ToolDisplayData? = null  // dati per UI opzionale
    ) : ToolResult()

    data class Error(
        override val toolCallId: String,
        override val toolName: String,
        val error: String,
        val isRetryable: Boolean = false
    ) : ToolResult()

    data class ConfirmationRequired(
        override val toolCallId: String,
        override val toolName: String,
        val message: String,                // "Vuoi che invii un SMS a Mario?"
        val onConfirm: suspend () -> ToolResult,
        val onDeny: suspend () -> ToolResult
    ) : ToolResult()
}

/**
 * Dati opzionali per mostrare risultati tool nella UI
 * (es. card meteo, mappa, lista contatti)
 */
sealed class ToolDisplayData {
    data class Weather(val temp: Double, val condition: String, val icon: String) : ToolDisplayData()
    data class Timer(val durationSeconds: Int, val label: String) : ToolDisplayData()
    data class Contact(val name: String, val phone: String?) : ToolDisplayData()
    data class Map(val lat: Double, val lng: Double, val label: String) : ToolDisplayData()
    data class Generic(val title: String, val body: String) : ToolDisplayData()
}
```

#### 4.5.4 Tool Registry e Executor

```kotlin
/**
 * Registro centrale di tutte le tool disponibili.
 * Le tool si auto-registrano al bootstrap dell'app via Koin.
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.definition.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun getDefinitions(): List<ToolDefinition> = tools.values.map { it.definition }

    fun getTool(name: String): Tool? = tools[name]

    fun getEnabledDefinitions(): List<ToolDefinition> =
        tools.values.filter { it.isEnabled() }.map { it.definition }
}

/**
 * Interfaccia base per ogni tool.
 */
interface Tool {
    val definition: ToolDefinition
    fun isEnabled(): Boolean = true          // può dipendere da permessi, rete, ecc.
    suspend fun execute(arguments: Map<String, Any>): ToolResult
}

/**
 * Esegue tool calls, gestisce retry, timeout e conferme.
 */
class ToolExecutor(
    private val registry: ToolRegistry,
    private val config: ToolExecutorConfig = ToolExecutorConfig()
) {
    suspend fun execute(toolCall: ToolCall): ToolResult {
        val tool = registry.getTool(toolCall.name)
            ?: return ToolResult.Error(
                toolCallId = toolCall.id,
                toolName = toolCall.name,
                error = "Tool '${toolCall.name}' not found"
            )

        return withTimeout(config.timeoutMs) {
            try {
                tool.execute(toolCall.arguments)
            } catch (e: Exception) {
                ToolResult.Error(
                    toolCallId = toolCall.id,
                    toolName = toolCall.name,
                    error = e.message ?: "Unknown error",
                    isRetryable = e is TimeoutCancellationException
                )
            }
        }
    }
}

data class ToolExecutorConfig(
    val timeoutMs: Long = 10_000,           // 10s max per tool
    val maxRetries: Int = 1,
    val maxChainedCalls: Int = 5            // max tool calls in sequenza
)
```

#### 4.5.5 LLM Output Parser

Componente critico: analizza l'output token-by-token dell'LLM e determina se sta generando testo normale o un tool call.

```kotlin
/**
 * Parsing streaming dell'output LLM.
 * Biforca il flusso: testo puro → TTS, tool calls → ToolExecutor
 */
class LlmOutputParser(
    private val format: ToolCallFormat = ToolCallFormat.XML_TAGS
) {
    /**
     * Processa il token stream e emette eventi tipizzati.
     */
    fun parse(tokenStream: Flow<String>): Flow<LlmOutputEvent> = flow {
        val buffer = StringBuilder()

        tokenStream.collect { token ->
            buffer.append(token)
            val text = buffer.toString()

            when (format) {
                ToolCallFormat.XML_TAGS -> {
                    if (text.contains("<tool_call>") && text.contains("</tool_call>")) {
                        // Emetti testo prima del tool call
                        val preText = text.substringBefore("<tool_call>").trim()
                        if (preText.isNotEmpty()) {
                            emit(LlmOutputEvent.TextChunk(preText))
                        }

                        // Parsa ed emetti il tool call
                        val toolCallXml = text.substringBetween("<tool_call>", "</tool_call>")
                        val toolCall = parseToolCallXml(toolCallXml)
                        emit(LlmOutputEvent.ToolCallDetected(toolCall))

                        // Reset buffer con testo dopo il tool call
                        buffer.clear()
                        buffer.append(text.substringAfter("</tool_call>"))
                    } else if (!text.contains("<tool_call>")) {
                        // Nessun tool call in corso, emetti token
                        emit(LlmOutputEvent.TextChunk(token))
                    }
                    // Altrimenti: stiamo accumulando un tool call, aspetta
                }
                // ... altri formati
            }
        }

        // Flush buffer finale
        if (buffer.isNotEmpty() && !buffer.contains("<tool_call>")) {
            emit(LlmOutputEvent.TextChunk(buffer.toString()))
        }
    }
}

sealed class LlmOutputEvent {
    data class TextChunk(val text: String) : LlmOutputEvent()
    data class ToolCallDetected(val toolCall: ToolCall) : LlmOutputEvent()
    object StreamEnd : LlmOutputEvent()
}

enum class ToolCallFormat {
    XML_TAGS,       // <tool_call>...</tool_call>
    JSON,           // {"tool_call": ...}
    CHATML          // formato nativo del modello
}
```

#### 4.5.6 Prompt Builder con Tool Definitions

```kotlin
/**
 * Costruisce il system prompt con le definizioni delle tool.
 */
class PromptBuilder(
    private val toolRegistry: ToolRegistry,
    private val format: ToolCallFormat = ToolCallFormat.XML_TAGS
) {
    fun buildSystemPrompt(basePrompt: String): String {
        val tools = toolRegistry.getEnabledDefinitions()
        if (tools.isEmpty()) return basePrompt

        return buildString {
            append(basePrompt)
            append("\n\n")
            append("## Available Tools\n\n")
            append("You have access to the following tools. ")
            append("When you need to use a tool, respond with a tool_call block.\n")
            append("After receiving the tool result, use it to formulate your spoken response.\n")
            append("IMPORTANT: Only call tools when the user's request requires it. ")
            append("For normal conversation, just respond naturally.\n\n")

            tools.forEach { tool ->
                append("### ${tool.name}\n")
                append("${tool.description}\n")
                append("Parameters:\n")
                tool.parameters.forEach { param ->
                    val req = if (param.required) "required" else "optional"
                    append("  - ${param.name} (${param.type.name.lowercase()}, $req): ${param.description}")
                    param.default?.let { append(" [default: $it]") }
                    param.enumValues?.let { append(" [values: ${it.joinToString(", ")}]") }
                    append("\n")
                }
                append("\n")
            }

            append("To call a tool, use this format:\n")
            append("<tool_call>\n")
            append("<name>tool_name</name>\n")
            append("<arguments>{\"param1\": \"value1\", \"param2\": \"value2\"}</arguments>\n")
            append("</tool_call>\n\n")
            append("After calling a tool, wait for the result before responding to the user.\n")
        }
    }

    /**
     * Inietta il risultato di una tool nella conversation history.
     */
    fun buildToolResultMessage(result: ToolResult): ChatMessage {
        val content = when (result) {
            is ToolResult.Success -> "[Tool Result: ${result.toolName}]\n${result.data}"
            is ToolResult.Error -> "[Tool Error: ${result.toolName}]\n${result.error}"
            is ToolResult.ConfirmationRequired -> "[Tool: ${result.toolName}] Awaiting user confirmation"
        }
        return ChatMessage(role = Role.TOOL, content = content)
    }
}
```

#### 4.5.7 Tool Implementations — Esempi

```kotlin
/**
 * Tool: Data e ora corrente
 */
class DateTimeTool : Tool {
    override val definition = ToolDefinition(
        name = "get_datetime",
        description = "Get current date, time, or day of the week",
        parameters = listOf(
            ToolParameter(
                name = "format",
                type = ParameterType.ENUM,
                description = "What to return",
                enumValues = listOf("time", "date", "datetime", "day_of_week"),
                default = "datetime"
            ),
            ToolParameter(
                name = "timezone",
                type = ParameterType.STRING,
                description = "Timezone (e.g. Europe/Rome)",
                required = false,
                default = "system"
            )
        )
    )

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val format = arguments["format"] as? String ?: "datetime"
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        val result = when (format) {
            "time" -> "${now.hour}:${now.minute.toString().padStart(2, '0')}"
            "date" -> "${now.dayOfMonth}/${now.monthNumber}/${now.year}"
            "day_of_week" -> now.dayOfWeek.name.lowercase()
            else -> "$now"
        }

        return ToolResult.Success(
            toolCallId = "",
            toolName = "get_datetime",
            data = result
        )
    }
}

/**
 * Tool: Timer / Sveglia
 */
class TimerTool(
    private val timerManager: TimerManager   // platform-specific (expect/actual)
) : Tool {
    override val definition = ToolDefinition(
        name = "set_timer",
        description = "Set a countdown timer or alarm. Returns confirmation.",
        parameters = listOf(
            ToolParameter("duration_seconds", ParameterType.INT, "Timer duration in seconds"),
            ToolParameter("label", ParameterType.STRING, "Label for the timer", required = false)
        )
    )

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val seconds = (arguments["duration_seconds"] as Number).toInt()
        val label = arguments["label"] as? String ?: "Timer"
        timerManager.startTimer(seconds, label)

        return ToolResult.Success(
            toolCallId = "",
            toolName = "set_timer",
            data = "Timer set for $seconds seconds ($label)",
            displayData = ToolDisplayData.Timer(seconds, label)
        )
    }
}

/**
 * Tool: Meteo (via open-meteo.com, API gratuita, no API key)
 */
class WeatherTool(
    private val httpClient: HttpClient,
    private val geocoder: Geocoder           // risolve "Milano" → lat/lng
) : Tool {
    override val definition = ToolDefinition(
        name = "get_weather",
        description = "Get current weather or forecast for a location",
        parameters = listOf(
            ToolParameter("location", ParameterType.STRING, "City name or 'lat,lng' coordinates"),
            ToolParameter("type", ParameterType.ENUM, "Current or forecast",
                enumValues = listOf("current", "today", "tomorrow", "week"), default = "current")
        )
    )

    override fun isEnabled(): Boolean = true // richiede rete, ma graceful fallback

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val location = arguments["location"] as String
        val (lat, lng) = geocoder.resolve(location)
        // Chiamata a https://api.open-meteo.com/v1/forecast?...
        // Parse e restituisci risultato
        return ToolResult.Success(
            toolCallId = "",
            toolName = "get_weather",
            data = """{"temperature": 18.5, "condition": "sunny", "humidity": 45}""",
            displayData = ToolDisplayData.Weather(18.5, "sunny", "☀️")
        )
    }
}

/**
 * Tool: Contatti Android (platform-specific)
 */
// androidMain
class ContactsTool(
    private val contentResolver: ContentResolver
) : Tool {
    override val definition = ToolDefinition(
        name = "search_contacts",
        description = "Search phone contacts by name",
        parameters = listOf(
            ToolParameter("query", ParameterType.STRING, "Contact name to search for")
        )
    )

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val query = arguments["query"] as String
        // Query ContentProvider per contatti
        // Restituisci lista match
        return ToolResult.Success(
            toolCallId = "",
            toolName = "search_contacts",
            data = """[{"name": "Mario Rossi", "phone": "+39 333 1234567"}]"""
        )
    }
}

/**
 * Tool: Invio SMS (richiede conferma utente!)
 */
class SmsTool(
    private val smsManager: SmsManager
) : Tool {
    override val definition = ToolDefinition(
        name = "send_sms",
        description = "Send an SMS text message to a phone number",
        parameters = listOf(
            ToolParameter("phone_number", ParameterType.STRING, "Recipient phone number"),
            ToolParameter("message", ParameterType.STRING, "Message text to send")
        ),
        requiresConfirmation = true          // SEMPRE chiedere conferma!
    )

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        val phone = arguments["phone_number"] as String
        val message = arguments["message"] as String

        return ToolResult.ConfirmationRequired(
            toolCallId = "",
            toolName = "send_sms",
            message = "Vuoi che invii '$message' a $phone?",
            onConfirm = {
                smsManager.sendTextMessage(phone, null, message, null, null)
                ToolResult.Success("", "send_sms", "SMS sent successfully to $phone")
            },
            onDeny = {
                ToolResult.Success("", "send_sms", "SMS cancelled by user")
            }
        )
    }
}

/**
 * Tool: Home Assistant (domotica)
 */
class HomeAssistantTool(
    private val httpClient: HttpClient,
    private val config: HomeAssistantConfig  // URL + token locale
) : Tool {
    override val definition = ToolDefinition(
        name = "smart_home",
        description = "Control smart home devices via Home Assistant. Turn lights on/off, set temperature, etc.",
        parameters = listOf(
            ToolParameter("entity_id", ParameterType.STRING, "Device entity ID (e.g. light.living_room)"),
            ToolParameter("action", ParameterType.ENUM, "Action to perform",
                enumValues = listOf("turn_on", "turn_off", "toggle", "set_brightness", "set_temperature")),
            ToolParameter("value", ParameterType.STRING, "Value for the action (e.g. brightness 0-255)", required = false)
        )
    )

    override fun isEnabled(): Boolean = config.isConfigured()

    override suspend fun execute(arguments: Map<String, Any>): ToolResult {
        // POST a http://<ha-url>/api/services/<domain>/<service>
        // con entity_id e parametri
        return ToolResult.Success(
            toolCallId = "",
            toolName = "smart_home",
            data = "Turned on light.living_room"
        )
    }
}
```

#### 4.5.8 Modelli consigliati per Function Calling

Non tutti i modelli locali sono bravi con il function calling. Ecco quelli più affidabili:

| Modello | Dimensione | Function Calling | Note |
|---|---|---|---|
| Hermes 2 Pro 7B Q4_K_M | ~4.4 GB | ⭐⭐⭐⭐⭐ | Fine-tuned specificamente per tool use |
| Qwen 2.5 3B Instruct Q4_K_M | ~2.1 GB | ⭐⭐⭐⭐ | Ottimo per la dimensione, buon multilingua |
| Functionary Small v3.2 | ~4.4 GB | ⭐⭐⭐⭐⭐ | Nato per function calling, formato ChatML |
| Llama 3.2 3B Instruct Q4_K_M | ~2.0 GB | ⭐⭐⭐ | Discreto con prompting XML |
| Mistral 7B Instruct v0.3 Q4_K_M | ~4.4 GB | ⭐⭐⭐⭐ | Supporto nativo tool use |
| Phi-3 Mini 3.8B Q4_K_M | ~2.3 GB | ⭐⭐⭐ | Funziona con XML tags, meno affidabile |

**Raccomandazione**: per il miglior rapporto qualità/dimensione con function calling, **Qwen 2.5 3B** è la scelta top per mobile. Se il device lo regge, **Hermes 2 Pro 7B** o **Functionary** sono significativamente più affidabili.

#### 4.5.9 Sicurezza e Conferme

Alcune azioni sono **distruttive o sensibili** e richiedono conferma vocale dell'utente:

```kotlin
enum class ToolSafetyLevel {
    SAFE,           // get_datetime, get_weather → esegui subito
    MODERATE,       // set_timer, smart_home → esegui ma notifica
    SENSITIVE,      // send_sms, make_call → chiedi conferma vocale
    DANGEROUS       // delete_file, shell_command → doppia conferma
}
```

Il flusso con conferma vocale:
1. LLM genera tool call per `send_sms`
2. `ToolExecutor` rileva `requiresConfirmation = true`
3. Ritorna `ToolResult.ConfirmationRequired` con messaggio
4. `ConversationManager` lo passa al TTS: "Vuoi che invii un SMS a Mario con scritto 'arrivo tra 10 minuti'?"
5. L'utente risponde "sì" o "no"
6. STT trascrive, il manager esegue `onConfirm()` o `onDeny()`
7. Il risultato viene re-iniettato nell'LLM per la risposta finale

---

## 5. Componenti Core

### 5.1 Sentence Buffer

Componente tra LLM e TTS che accumula token e rilascia frasi complete per la sintesi.

```kotlin
class SentenceBuffer(
    private val minChars: Int = 15,             // minimo caratteri per emettere
    private val maxWaitMs: Long = 2000,         // timeout massimo prima di flush
    private val breakChars: Set<Char> = setOf('.', '!', '?', ':', ';', ',', '\n')
) {
    fun bufferTokens(tokenStream: Flow<String>): Flow<String>
}
```

**Logica**:
1. Accumula token in un buffer
2. Ad ogni token, controlla se c'è un punto di taglio naturale (`.`, `!`, `?`, ecc.)
3. Se il buffer supera `minChars` e c'è un break point → emetti la frase
4. Se passa `maxWaitMs` senza break point → emetti comunque (evita stalli)
5. Al termine dello stream → flush del buffer rimanente

### 5.2 Audio Pipeline

```kotlin
// Audio capture multiplatform
interface AudioCaptureSource {
    fun start(config: AudioConfig): Flow<AudioFrame>
    fun stop()
}

// Audio playback multiplatform
interface AudioPlayer {
    fun playStream(chunks: Flow<AudioChunk>)
    fun stop()
    fun isPlaying(): Boolean
    val playbackState: StateFlow<PlaybackState>
}

data class AudioConfig(
    val sampleRate: Int = 16_000,
    val channels: Int = 1,                // mono
    val encoding: AudioEncoding = AudioEncoding.PCM_16BIT,
    val bufferSizeMs: Int = 30            // dimensione chunk
)

data class AudioFrame(
    val data: ByteArray,
    val timestampMs: Long
)
```

### 5.3 Conversation Manager

Orchestratore centrale — collega tutti gli engine e gestisce lo stato.

```kotlin
class ConversationManager(
    private val vad: VadEngine,
    private val stt: SttEngine,
    private val llm: LlmEngine,
    private val tts: TtsEngine,
    private val sentenceBuffer: SentenceBuffer,
    private val audioCapture: AudioCaptureSource,
    private val audioPlayer: AudioPlayer,
    private val conversationHistory: ConversationHistory,
    private val toolExecutor: ToolExecutor,
    private val outputParser: LlmOutputParser,
    private val promptBuilder: PromptBuilder
) {
    private val _state = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val state: StateFlow<ConversationState>

    sealed class ConversationState {
        object Idle : ConversationState()
        object Listening : ConversationState()
        data class UserSpeaking(val partialText: String) : ConversationState()
        object Thinking : ConversationState()
        data class ToolExecuting(val toolName: String, val description: String) : ConversationState()
        data class AwaitingConfirmation(val message: String) : ConversationState()
        data class AiSpeaking(val text: String) : ConversationState()
    }

    fun startConversation()
    fun endConversation()
    private suspend fun handleSpeechStart()
    private suspend fun handleSpeechEnd(transcription: String)
    private suspend fun handleBargeIn()

    /**
     * Pipeline principale con supporto function calling.
     * Il loop LLM → parse → [tool execute → re-inject → LLM] → TTS
     * può ripetersi fino a maxChainedCalls volte.
     */
    private suspend fun runInferencePipeline(userMessage: String) {
        var chainCount = 0
        val maxChains = 5

        // Prima generazione LLM
        val tokenStream = llm.generate(
            promptBuilder.buildMessages(conversationHistory, userMessage)
        )

        outputParser.parse(tokenStream).collect { event ->
            when (event) {
                is LlmOutputEvent.TextChunk -> {
                    // Testo normale → sentence buffer → TTS
                    sentenceBuffer.feed(event.text)
                }
                is LlmOutputEvent.ToolCallDetected -> {
                    if (chainCount >= maxChains) {
                        sentenceBuffer.feed("Mi dispiace, ho raggiunto il limite di azioni consecutive.")
                        return@collect
                    }

                    _state.value = ConversationState.ToolExecuting(
                        toolName = event.toolCall.name,
                        description = "Eseguendo ${event.toolCall.name}..."
                    )

                    // Esegui la tool
                    val result = toolExecutor.execute(event.toolCall)

                    when (result) {
                        is ToolResult.ConfirmationRequired -> {
                            _state.value = ConversationState.AwaitingConfirmation(result.message)
                            // TTS chiede conferma, poi aspetta risposta utente
                            tts.synthesize(result.message)
                            val userResponse = waitForUserResponse() // ascolta sì/no
                            val finalResult = if (userResponse.isAffirmative()) {
                                result.onConfirm()
                            } else {
                                result.onDeny()
                            }
                            reInjectAndContinue(finalResult, chainCount++)
                        }
                        else -> {
                            reInjectAndContinue(result, chainCount++)
                        }
                    }
                }
                is LlmOutputEvent.StreamEnd -> {
                    sentenceBuffer.flush()
                }
            }
        }
    }

    /**
     * Re-inietta il risultato della tool nell'LLM per generare
     * la risposta in linguaggio naturale.
     */
    private suspend fun reInjectAndContinue(result: ToolResult, chainCount: Int) {
        conversationHistory.addToolResult(result)
        _state.value = ConversationState.Thinking

        // Seconda chiamata LLM con il tool result nel context
        val followUpStream = llm.generate(
            promptBuilder.buildMessagesWithToolResult(conversationHistory, result)
        )

        // Ricorsione: il follow-up potrebbe contenere un altro tool call
        outputParser.parse(followUpStream).collect { event ->
            when (event) {
                is LlmOutputEvent.TextChunk -> sentenceBuffer.feed(event.text)
                is LlmOutputEvent.ToolCallDetected -> {
                    if (chainCount < 5) {
                        // Chain: esegui la prossima tool
                        val nextResult = toolExecutor.execute(event.toolCall)
                        reInjectAndContinue(nextResult, chainCount + 1)
                    }
                }
                is LlmOutputEvent.StreamEnd -> sentenceBuffer.flush()
            }
        }
    }
}
```

---

## 6. UI / UX Design

### 6.1 Screen principali

**Conversation Screen** (schermo principale):
- Orb/blob animato centrale che reagisce allo stato
- Sottotitoli in tempo reale (partial STT + risposta AI)
- Pulsante end conversation
- Indicatore stato (Listening / Thinking / Speaking)
- Nessun input testuale — solo voce

**Settings Screen**:
- Selezione modello LLM (lista modelli scaricati)
- Selezione voce TTS
- Lingua STT
- Parametri avanzati (temperatura, max tokens, ecc.)
- Test audio (mic check)

**Models Screen**:
- Lista modelli disponibili (LLM, STT, TTS, VAD)
- Stato download con progress bar
- Dimensione su disco
- Pulsante download / elimina

**History Screen**:
- Lista conversazioni passate
- Trascrizione completa con turni user/AI
- Durata, data, modello utilizzato

### 6.2 Animated Orb — Specifiche

L'orb è il cuore dell'UI, ispirato a Gemini Live:

| Stato | Animazione | Colore |
|---|---|---|
| Idle | Pulsazione lenta, piccolo | Grigio tenue |
| Listening | Pulsazione media, pronto | Primary color M3, soft glow |
| UserSpeaking | Reagisce all'ampiezza audio, scala dinamica | Primary variant, onde |
| Thinking | Rotazione/morphing fluido | Tertiary, shimmer |
| ToolExecuting | Orbita veloce, particelle che ruotano | Tertiary + accent, sparkle effect |
| AwaitingConfirmation | Pulsazione lenta con "?" overlay, attesa | Warning color M3, glow intermittente |
| AiSpeaking | Pulsazione ritmica con l'audio TTS | Secondary, glow intenso |
| Error | Shake breve | Error color M3 |

Implementazione: `Canvas` Compose con `animateFloatAsState`, shader opzionali per effetti glow. L'ampiezza audio viene calcolata in tempo reale dal `AudioFrame` RMS.

### 6.3 Material 3 Theme

```kotlin
// Palette dinamica con supporto Dynamic Color (Android 12+)
@Composable
fun VoiceAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = Color(0xFF90CAF9),
            secondary = Color(0xFFA5D6A7),
            tertiary = Color(0xFFCE93D8)
        )
        else -> lightColorScheme(...)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

---

## 7. Dipendenze

### 7.1 Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
kotlin = "2.1.0"
agp = "8.7.3"
compose-multiplatform = "1.7.3"
compose-material3 = "1.3.1"
coroutines = "1.9.0"
koin = "4.0.1"
ktor = "3.0.3"
voyager = "1.1.0-beta03"
sqldelight = "2.0.2"
kotlinx-serialization = "1.7.3"
onnxruntime = "1.20.0"
okio = "3.9.1"

[libraries]
# Compose & Material 3
compose-material3 = { module = "androidx.compose.material3:material3", version.ref = "compose-material3" }
compose-material3-window = { module = "androidx.compose.material3:material3-window-size-class", version.ref = "compose-material3" }

# Kotlin
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# DI
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }

# Navigation
voyager-navigator = { module = "cafe.adriel.voyager:voyager-navigator", version.ref = "voyager" }
voyager-screenmodel = { module = "cafe.adriel.voyager:voyager-screenmodel", version.ref = "voyager" }

# Network (per download modelli)
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }

# Database
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-jvm = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }

# AI / ML
onnxruntime-android = { module = "com.microsoft.onnxruntime:onnxruntime-android", version.ref = "onnxruntime" }
onnxruntime-desktop = { module = "com.microsoft.onnxruntime:onnxruntime", version.ref = "onnxruntime" }

# File system
okio = { module = "com.squareup.okio:okio", version.ref = "okio" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

### 7.2 Librerie native / JNI

| Componente | Libreria | Piattaforma | Integrazione |
|---|---|---|---|
| VAD | Silero VAD | Tutte | ONNX Runtime |
| STT | Vosk | Android | AAR Maven |
| STT | Vosk | Desktop | JNI + native libs |
| LLM | llama.cpp | Android | CMake NDK build |
| LLM | java-llama.cpp | Desktop | JNI pre-built |
| TTS | Piper | Android | JNI + ONNX Runtime |
| TTS | Piper | Desktop | JNI + ONNX Runtime |
| Audio | — | Android | AudioRecord / AudioTrack |
| Audio | — | Desktop | javax.sound.sampled |

---

## 8. Roadmap e Fasi

### Fase 0 — Setup Progetto (Settimana 1)

- [ ] Creare progetto KMP con Compose Multiplatform
- [ ] Configurare struttura multi-modulo
- [ ] Setup build-logic con convention plugins
- [ ] Configurare version catalog
- [ ] Setup CI base (build + test)
- [ ] Tema Material 3 base
- [ ] Navigation con Voyager

**Deliverable**: progetto compila su Android e Desktop con schermata vuota.

### Fase 1 — Audio Foundation (Settimana 2)

- [ ] Implementare `AudioCaptureSource` (Android: `AudioRecord`, Desktop: `javax.sound`)
- [ ] Implementare `AudioPlayer` (Android: `AudioTrack`, Desktop: `javax.sound`)
- [ ] Test: registra audio e riproducilo
- [ ] Permessi Android (`RECORD_AUDIO`)
- [ ] UI: schermata test microfono con visualizzazione waveform

**Deliverable**: l'app registra e riproduce audio su entrambe le piattaforme.

### Fase 2 — VAD Integration (Settimana 3)

- [ ] Integrare ONNX Runtime (Android + Desktop)
- [ ] Integrare modello Silero VAD
- [ ] Implementare `VadEngine` con detect speech start/end
- [ ] Configurare parametri VAD (threshold, durata, silence)
- [ ] Test: indicatore visivo quando si parla
- [ ] UI: orb che reagisce a speech/silence

**Deliverable**: l'app rileva quando l'utente parla e quando smette.

### Fase 3 — STT Integration (Settimana 3-4)

- [ ] Integrare Vosk SDK (Android + Desktop)
- [ ] Download e gestione modello Vosk italiano/inglese
- [ ] Implementare `SttEngine` con streaming partial results
- [ ] Collegare VAD → STT pipeline
- [ ] Test: trascrizione in tempo reale visualizzata su schermo
- [ ] UI: sottotitoli live del parlato utente

**Deliverable**: l'utente parla, il testo appare in tempo reale sullo schermo.

### Fase 4 — LLM Integration (Settimana 4-5)

- [ ] Build llama.cpp per Android (CMake/NDK) e Desktop (JNI)
- [ ] Implementare `LlmEngine` con token streaming
- [ ] Download manager per modelli GGUF
- [ ] System prompt ottimizzato per conversazione vocale
- [ ] Gestione conversation history (context window management)
- [ ] Implementare `cancelGeneration()` per barge-in
- [ ] Test: chat testuale funzionante con streaming

**Deliverable**: si può chattare con l'LLM in testo, con risposte streaming.

### Fase 5 — TTS Integration (Settimana 5-6)

- [ ] Integrare Piper TTS (ONNX model loading)
- [ ] Implementare `TtsEngine` con sintesi frase-per-frase
- [ ] Implementare `SentenceBuffer` (token stream → frasi)
- [ ] Pipeline: LLM streaming → SentenceBuffer → TTS → AudioPlayer
- [ ] Implementare `cancelCurrent()` per barge-in
- [ ] Test: l'AI risponde a voce dato un input testuale

**Deliverable**: l'AI legge le sue risposte ad alta voce, frase per frase.

### Fase 6 — Full Pipeline (Settimana 6-7)

- [ ] Implementare `ConversationManager` completo
- [ ] Collegare: Mic → VAD → STT → LLM → SentenceBuffer → TTS → Speaker
- [ ] Implementare barge-in (interruzione durante AI speaking)
- [ ] Gestione echo cancellation / mic muting durante TTS playback
- [ ] Gestione errori e recovery per ogni stage
- [ ] Test end-to-end: conversazione vocale completa

**Deliverable**: conversazione vocale funzionante end-to-end.

### Fase 7 — Function Calling Core (Settimana 7-8)

- [ ] Implementare `ToolDefinition`, `ToolCall`, `ToolResult` data classes
- [ ] Implementare `ToolRegistry` con registrazione dinamica
- [ ] Implementare `LlmOutputParser` con formato XML tags
- [ ] Implementare `ToolExecutor` con timeout e error handling
- [ ] Implementare `PromptBuilder` che inietta tool definitions nel system prompt
- [ ] Aggiornare `ConversationManager` con loop tool call → re-inject
- [ ] Implementare tool built-in: `DateTimeTool`, `TimerTool`, `MathTool`, `UnitConverterTool`
- [ ] Test: chiedere "che ore sono?" e ricevere risposta via tool
- [ ] Test: chiedere "imposta un timer di 5 minuti" e verificare esecuzione
- [ ] UI: stato `ToolExecuting` nell'orb animato

**Deliverable**: l'AI può invocare tool base durante la conversazione vocale.

### Fase 8 — Function Calling Platform Tools (Settimana 8-9)

- [ ] Implementare `ContactsTool` (Android: ContentProvider)
- [ ] Implementare `CalendarTool` (Android: CalendarContract)
- [ ] Implementare `SmsTool` con flusso di conferma vocale
- [ ] Implementare `AppLauncherTool` (Android: Intent)
- [ ] Implementare `SystemSettingsTool` (volume, brightness, wifi, bluetooth)
- [ ] Implementare `WeatherTool` con API open-meteo.com
- [ ] Implementare `FileSystemTool` (Desktop)
- [ ] Implementare `ClipboardTool` (Desktop)
- [ ] Gestione permessi Android runtime per ogni tool
- [ ] UI: stato `AwaitingConfirmation` con feedback vocale + visivo
- [ ] UI: `ToolDisplayData` cards (meteo, contatto, timer) sopra l'orb
- [ ] Test: flusso completo "Manda un messaggio a Mario" → conferma → invio

**Deliverable**: assistente agentico con accesso a tool del dispositivo.

### Fase 9 — Function Calling Advanced (Settimana 9-10)

- [ ] Supporto tool call chaining (più tool in sequenza)
- [ ] Implementare `HomeAssistantTool` (smart home)
- [ ] Implementare `MqttTool` (dispositivi IoT)
- [ ] Implementare `WebSearchTool` (opzionale, con SearXNG locale o DuckDuckGo)
- [ ] Implementare `HttpRequestTool` per API custom
- [ ] Plugin system: permettere all'utente di registrare tool custom via JSON config
- [ ] Fallback graceful: se la tool non è disponibile, l'LLM risponde con alternativa
- [ ] Testare con modelli specializzati (Hermes 2 Pro, Functionary)
- [ ] Implementare formato ChatML nativo per modelli che lo supportano
- [ ] Benchmarking: misurare reliability del function calling per modello

**Deliverable**: sistema di function calling completo, estensibile e affidabile.

### Fase 10 — Polish & UX (Settimana 10-11)

- [ ] Animated Orb con tutti gli stati (inclusi ToolExecuting, AwaitingConfirmation)
- [ ] Transizioni fluide tra stati
- [ ] Feedback aptici (Android)
- [ ] Ottimizzazione latenza (profiling ogni stage)
- [ ] Settings screen completo (inclusa gestione tool abilitate/disabilitate)
- [ ] Model manager con download e progress
- [ ] Dark mode / Dynamic Colors

**Deliverable**: esperienza utente fluida e piacevole.

### Fase 11 — Advanced Features (Settimana 12+)

- [ ] Conversation history con SQLDelight
- [ ] Noise suppression (RNNoise)
- [ ] Multi-lingua (switch runtime)
- [ ] Wake word detection (opzionale: Porcupine / OpenWakeWord)
- [ ] GPU acceleration (Vulkan Android, CUDA Desktop)
- [ ] Foreground service Android per conversazione in background
- [ ] Widget / Quick Settings tile
- [ ] Testing automatizzato per ogni engine
- [ ] Performance benchmarking suite

---

## 9. Requisiti Hardware

### Android

| Componente | Minimo | Consigliato |
|---|---|---|
| RAM | 6 GB | 8+ GB |
| Storage libero | 3 GB | 8+ GB |
| SoC | Snapdragon 7xx / Tensor G1 | Snapdragon 8 Gen 2+ / Tensor G3+ |
| Android | 10 (API 29) | 13+ (API 33) |
| GPU | Adreno 6xx | Adreno 7xx+ |

### Desktop

| Componente | Minimo | Consigliato |
|---|---|---|
| RAM | 8 GB | 16+ GB |
| Storage libero | 5 GB | 15+ GB |
| CPU | 4 core moderni | 8+ core, AVX2 support |
| GPU (opzionale) | — | NVIDIA con 6+ GB VRAM |
| OS | Windows 10 / macOS 12 / Ubuntu 22 | Latest |

---

## 10. Rischi e Mitigazioni

| Rischio | Impatto | Mitigazione |
|---|---|---|
| Latenza LLM troppo alta su device mobile | Alto | Usare modelli piccoli (2-3B), quantizzazione aggressiva, GPU offloading |
| Qualità STT insufficiente in italiano | Medio | Fallback su Whisper per trascrizione finale; Vosk per partial |
| Echo loop (TTS captato dal microfono) | Alto | AEC Android nativo; mic muting su Desktop; mode cuffie consigliato |
| Memoria insufficiente con LLM + tutti gli engine | Alto | Lazy loading: caricare engine on-demand, rilasciare quando non servono |
| Build NDK/JNI complessi per llama.cpp | Medio | Usare pre-built binaries dove possibile; CI con cache |
| Context window overflow in conversazioni lunghe | Medio | Sliding window + summarization dei turni più vecchi |
| LLM genera tool call malformati | Alto | Parsing robusto con fallback: se il XML è rotto, tratta come testo. Retry con prompt di correzione. Usare modelli specializzati (Hermes, Functionary) |
| LLM chiama tool che non esiste | Medio | Validazione nel ToolExecutor, messaggio di errore graceful re-iniettato nell'LLM |
| LLM "hallucina" tool call non richiesti | Medio | Prompt engineering chiaro ("ONLY call tools when explicitly needed"). Post-filtering nell'output parser |
| Tool call loop infinito (LLM chiama tool → genera altro tool call → ...) | Alto | Hard limit a 5 chained calls nel ToolExecutor. Timeout globale sulla pipeline |
| Azioni distruttive eseguite senza consenso | Critico | Safety levels obbligatori. Tool sensibili SEMPRE con `requiresConfirmation = true`. Doppia conferma per azioni pericolose |
| Latenza aggiuntiva del tool call loop | Medio | Feedback vocale immediato ("Un momento, controllo il meteo...") durante ToolExecuting. TTS parziale prima del tool call se c'è testo |
| Permessi Android non concessi per tool | Basso | Check permessi prima di registrare la tool. `isEnabled()` restituisce false se permesso mancante |

---

## 11. Risorse e Riferimenti

### Repository chiave
- [llama.cpp](https://github.com/ggerganov/llama.cpp) — Inferenza LLM
- [whisper.cpp](https://github.com/ggerganov/whisper.cpp) — STT alternativo
- [Piper TTS](https://github.com/rhasspy/piper) — Text-to-Speech
- [Vosk](https://github.com/alphacephei/vosk-api) — Speech Recognition
- [Silero VAD](https://github.com/snakers4/silero-vad) — Voice Activity Detection
- [java-llama.cpp](https://github.com/kherud/java-llama.cpp) — JNI wrapper per llama.cpp
- [ONNX Runtime](https://github.com/microsoft/onnxruntime) — Runtime per modelli ONNX

### Modelli
- [HuggingFace GGUF Models](https://huggingface.co/models?sort=trending&search=gguf) — Modelli LLM quantizzati
- [Piper Voices](https://github.com/rhasspy/piper/blob/master/VOICES.md) — Catalogo voci TTS
- [Vosk Models](https://alphacephei.com/vosk/models) — Modelli STT

### Modelli per Function Calling
- [NousResearch/Hermes-2-Pro](https://huggingface.co/NousResearch/Hermes-2-Pro-Llama-3-8B-GGUF) — Fine-tuned per tool use
- [meetkai/functionary](https://huggingface.co/meetkai/functionary-small-v3.2-GGUF) — Nato per function calling
- [Qwen2.5-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF) — Buon multilingua + tool use

### API per Tool
- [Open-Meteo](https://open-meteo.com/) — API meteo gratuita, no API key
- [Home Assistant REST API](https://developers.home-assistant.io/docs/api/rest/) — Domotica locale
- [SearXNG](https://github.com/searxng/searxng) — Motore di ricerca self-hosted (per WebSearchTool)

### Documentazione
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [Material 3 Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Voyager Navigation](https://voyager.adriel.cafe/)
- [Koin DI](https://insert-koin.io/docs/reference/koin-mp/kmp/)

---

## 12. Note Finali

Questo progetto è ambizioso ma assolutamente fattibile con le tecnologie attuali. La chiave è **procedere in modo incrementale**: far funzionare ogni engine singolarmente prima di collegare tutto nella pipeline full-duplex.

La qualità dell'esperienza finale dipenderà enormemente da:
1. **La scelta del modello LLM** — bilanciare qualità risposte vs velocità, e capacità di function calling
2. **L'ottimizzazione della latenza** — ogni millisecondo conta nell'interazione vocale
3. **Il sentence buffer** — il tuning dei parametri di chunking è critico per la naturalezza
4. **L'UI dell'orb** — il feedback visivo copre i tempi di attesa e rende l'esperienza viva
5. **La robustezza del function calling** — il parsing degli output e la gestione degli errori determinano l'affidabilità dell'agente
6. **La sicurezza delle tool** — le azioni distruttive devono SEMPRE richiedere conferma esplicita

> **Tempo stimato per MVP** (voce senza function calling): 6-8 settimane (1 sviluppatore senior full-time)
> **Tempo stimato per MVP con function calling base**: 9-11 settimane
> **Tempo stimato per versione polished con tool ecosystem completo**: 14-18 settimane