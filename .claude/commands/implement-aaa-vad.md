# Implement AAA VAD System

Implementa il sistema VAD enterprise-grade seguendo il piano in `AAA_VAD_IMPLEMENTATION_PLAN.md`.

## Contesto

Stai implementando un Voice Activity Detector di classe AAA (Google-level) per Calmify che combina:
- **WebRTC VAD** (GMM): Pre-filtro ultra-rapido (< 0.1ms)
- **Silero VAD v6** (DNN): Classificazione ad alta precisione (< 1ms, 98%+ accuracy)

## Step di Implementazione

### FASE 1: Dependencies (libs.versions.toml)

Aggiungi le versioni:
```toml
[versions]
android-vad = "2.0.10"

[libraries]
vad-silero = { module = "com.github.gkonovalov.android-vad:silero", version.ref = "android-vad" }
vad-webrtc = { module = "com.github.gkonovalov.android-vad:webrtc", version.ref = "android-vad" }
```

Aggiungi JitPack in `settings.gradle.kts` se non presente:
```kotlin
maven { url = uri("https://jitpack.io") }
```

Aggiungi dependencies in `features/chat/build.gradle.kts`:
```kotlin
implementation(libs.vad.silero)
implementation(libs.vad.webrtc)
```

### FASE 2: Core Implementation

Crea il file `features/chat/src/main/java/com/lifo/chat/audio/vad/AAAVoiceActivityDetector.kt` con l'implementazione completa dal piano (sezione 3.1).

Requisiti chiave:
- Dual-engine: WebRTC pre-filter + Silero precision
- StateFlows: `isSpeechDetected`, `speechProbability`, `vadState`
- SharedFlows: `bargeInDetected`, `speechSegments`
- Adaptive calibration con noise floor learning
- Barge-in detection durante AI speech
- Metrics tracking per debugging

### FASE 3: Dependency Injection

Crea/aggiorna `features/chat/src/main/java/com/lifo/chat/di/VadModule.kt`:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object VadModule {
    @Provides
    @Singleton
    fun provideAAAVoiceActivityDetector(
        @ApplicationContext context: Context
    ): AAAVoiceActivityDetector {
        return AAAVoiceActivityDetector(context).also {
            it.initialize()
        }
    }
}
```

### FASE 4: Integration with GeminiLiveAudioManager

Modifica `GeminiLiveAudioManager.kt`:

1. Aggiungi inject del nuovo VAD:
```kotlin
@Singleton
class GeminiLiveAudioManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adaptiveBargeinDetector: AdaptiveBargeinDetector,
    private val aaaVad: AAAVoiceActivityDetector  // NEW
)
```

2. Nel recording loop, usa il nuovo VAD:
```kotlin
val vadResult = aaaVad.processFrame(buffer.copyOf(readSize))
if (aiCurrentlySpeaking && vadResult.isSpeech && vadResult.probability >= 0.7f) {
    onBargeInDetected?.invoke()
}
```

3. Aggiungi observer per barge-in events nell'init:
```kotlin
scope.launch {
    aaaVad.bargeInDetected.collect { event ->
        Log.d(TAG, "AAA VAD barge-in: confidence=${event.confidence}")
        onBargeInDetected?.invoke()
    }
}
```

4. Chiama `aaaVad.setAiSpeaking(speaking)` quando cambia lo stato AI

### FASE 5: Integration with FirebaseLiveManager

In `FirebaseLiveManager.kt`, propaga lo stato AI speaking al VAD tramite callback.

### FASE 6: Testing

Dopo l'implementazione:
1. Esegui build: `./gradlew :features:chat:compileDebugKotlin`
2. Verifica che non ci siano errori di compilazione
3. Stampa metrics report: `aaaVad.getMetricsReport()`

## Configurazione Audio

- Sample Rate: 16000 Hz
- Frame Size: 512 samples (32ms)
- Encoding: PCM 16-bit Mono

## Threshold Defaults

- Speech threshold: 0.5 (50%)
- Silence threshold: 0.35 (35%)
- Barge-in threshold: 0.7 (70%)
- Min speech duration: 100ms
- Min silence duration: 300ms
- Barge-in confirmation: 80ms (~2.5 frames)

## Output Attesi

```
AAA VAD System initialized successfully
  - WebRTC VAD: VERY_AGGRESSIVE mode
  - Silero VAD v6: NORMAL mode
  - Frame size: 512 samples (32ms)

=== AAA VAD Metrics ===
Total frames: 1500
WebRTC pass rate: 35%
Silero process rate: 35%
CPU savings: 65%
Avg processing time: 450us
Noise floor: 8%
Adaptive threshold: 52%
```

## Note Importanti

1. **NON rimuovere** `AdaptiveBargeinDetector` esistente - mantienilo come fallback
2. Il nuovo VAD deve essere **additivo**, non sostitutivo
3. Usa `USE_AAA_VAD = true` flag per toggle facile
4. Logga con tag `AAAVad` per debugging separato
5. Rilascia risorse in `release()` del manager

Procedi con l'implementazione step-by-step, verificando la compilazione dopo ogni fase.
