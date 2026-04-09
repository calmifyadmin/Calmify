> **NOTA (2026-04-09)**: Questo file e' stato scritto PRIMA della Fase 1. Molte azioni sono gia' completate.
> Per lo stato attuale, vedi `.claude/KMP_MIGRATION_STATUS.md` (tracker aggiornato).
> Per i piani backend, vedi `.claude/BACKEND_*.md`.

# KMP TIER 2 — Mostly Ready (3 moduli, lavoro mirato)

> commonMain pulito, ma androidMain ha logica significativa da astrarre.
> Blocker comune: SavedStateHandle (Android-only in ViewModel).

---

## 1. features/home (40 commonMain / 27 androidMain)

### Il modulo piu' grande del progetto

**commonMain (40 file — TUTTO PULITO):**
- Dashboard components (ExpressiveHero, ExpressiveMoodCard, ExpressiveWeekStrip, etc.)
- Domain models (DailyInsight, MoodDistribution, DominantMood, TimeRange, etc.)
- Presentation components (badges, animations, charts)
- Use cases
- MVI contract (HomeContract.kt)

**androidMain (27 file) — cosa c'e':**
- `HomeViewModel.kt` — SavedStateHandle
- `SnapshotViewModel.kt` — SavedStateHandle
- `HomeScreen.kt` — navigation integration
- `HomeContent.kt` — Compose UI (potenzialmente spostabile)
- Date picker components (usa `date.time.picker` library — Android-only)
- Firebase Storage integration
- Screens con navigation wiring

### Blockers specifici

| Blocker | File | Soluzione |
|---------|------|-----------|
| SavedStateHandle | HomeViewModel.kt, SnapshotViewModel.kt | Passare parametri via Decompose component |
| Date/Time Picker | androidMain date components | expect/actual con picker nativo per piattaforma |
| Firebase Storage | build.gradle androidMain | Coperto da strategia Option C (GitLive) |
| Accompanist permissions | build.gradle | expect/actual per runtime permissions |

### Azioni

```
STEP 1: Rimuovere SavedStateHandle dai ViewModel
  - Passare diaryId/params via costruttore (Decompose gia' li fornisce)
  - File: HomeViewModel.kt, SnapshotViewModel.kt

STEP 2: Spostare HomeContent.kt in commonMain
  - Verificare zero import android.* (gia' pulito)
  - Il coach mark system e' gia' in commonMain

STEP 3: Creare expect/actual per date picker
  - commonMain: expect fun DatePickerDialog(...)
  - androidMain: actual usa date.time.picker library
  - iosMain: actual usa UIDatePicker wrapper

STEP 4: Spostare altri screen components in commonMain dove possibile
```

**Effort stimato: 1-2 giorni**

---

## 2. features/write (13 commonMain / 56 androidMain)

### Il piu' sbilanciato — 56 file androidMain!

**commonMain (13 file — PULITO):**
- MVI contracts
- Domain models (journal entries, metrics, triggers)
- Base UI components

**androidMain (56 file) — analisi per categoria:**

**Spostabili in commonMain (20+ file):**
- Wizard system completo: `PsychologicalMetricsWizardDialog`, `WizardSteps`, `WizardAnimations`, `WizardComponents` — puro Compose
- `TextAnalyzer.kt` — pura logica testo
- Use cases: `SaveDiaryUseCase`, `GetDiaryByIdUseCase`, `DeleteDiaryUseCase`
- Domain models aggiuntivi
- Psychological metrics calculation — pura matematica

**Restano androidMain:**
- `WriteViewModel.kt` — SavedStateHandle + File I/O + Intent/Uri
- `DashboardViewModel.kt` — SavedStateHandle
- Media picker / upload components — Android ActivityResult
- Firebase Storage integration
- File system operations

### Blockers specifici

| Blocker | File | Soluzione |
|---------|------|-----------|
| SavedStateHandle | WriteViewModel.kt, DashboardViewModel.kt | Decompose params |
| File I/O (Intent, Uri) | WriteViewModel.kt | expect/actual FileProvider |
| Media picker | Android ActivityResult | expect/actual (come Gallery in core/ui) |
| Firebase Storage | Image upload | Coperto da Option C (GitLive) |

### Azioni

```
STEP 1: Spostare Wizard system in commonMain (20+ file)
  - Zero dipendenze Android, puro Compose
  - Guadagno immediato enorme

STEP 2: Spostare Use Cases + TextAnalyzer in commonMain
  - Pura business logic

STEP 3: Rifattorizzare WriteViewModel
  - Estrarre SavedStateHandle
  - Creare expect/actual per file operations

STEP 4: Creare expect/actual per media picker
  - Riusare pattern Gallery da core/ui
```

**Effort stimato: 2-3 giorni**

---

## 3. features/chat (13 commonMain / 28 androidMain)

### Il piu' complesso — audio pipeline + WebRTC + VAD

**commonMain (13 file — PULITO):**
- `AdaptiveBargeinDetector.kt` — signal processing puro
- `AecReliabilityDetector.kt` — AEC detection (matematica)
- `AudioQualityAnalyzer.kt` — analisi metriche audio
- `ConversationContextManager.kt` — session state
- `ReferenceSignalBargeInDetector.kt` — barge-in logic
- `ChatUiModels.kt` — data classes
- `LiveChatState.kt` — state management
- `SaveLiveMessageUseCase.kt`, `SendMessageUseCase.kt` — business logic
- `ChatBubble.kt`, `FluidAudioIndicator.kt` — UI components
- `NativeAudioEngine.kt` — wrapper platform-agnostic

**androidMain (28 file) — categorizzati:**

| Categoria | File | KMP Path |
|-----------|------|----------|
| Audio I/O | GeminiLiveAudioSource, AAAudioEngine, HighPriorityAudioThread, LockFreeAudioRingBuffer, AdaptiveJitterBuffer, PacketLossConcealmentEngine | expect/actual AudioEngine |
| VAD | SileroVadEngine | expect/actual VadEngine |
| WebSocket | GeminiLiveWebSocketClient | Ktor WebSocket (gia' KMP) |
| Camera | GeminiLiveCameraManager, LiveCameraPreview, SimpleLiveCameraPreview | expect/actual CameraProvider |
| Speech | SpeechToTextManager | expect/actual SpeechRecognizer |
| Voice System | GeminiNativeVoiceSystem | Orchestrator — usa tutti i precedenti |
| UI | ChatInput, GeminiLiquidVisualizer | Potenzialmente commonMain |
| RichText | Markdown rendering | Serve lib KMP markdown |

### Azioni

```
STEP 1: Creare interfacce audio in commonMain
  - AudioEngine interface (record, play, stop)
  - VadEngine interface (detect voice activity)
  - SpeechRecognizer interface (speech to text)
  
STEP 2: Migrare WebSocket a Ktor (gia' in commonMain deps)
  - GeminiLiveWebSocketClient → Ktor WebSocket client
  
STEP 3: expect/actual per le implementazioni
  - androidMain: AudioTrack/AudioRecord, SileroVAD, CameraX
  - iosMain: AVAudioEngine, AVSpeechRecognizer, AVCaptureSession
  - web: Web Audio API, MediaRecorder, getUserMedia

STEP 4: Spostare UI components in commonMain
  - ChatInput, GeminiLiquidVisualizer se non hanno import Android
```

**Effort stimato: 3-5 giorni**

---

## Blocker trasversale: SavedStateHandle

Presente in TUTTI i Tier 2 ViewModel. Soluzione unica:

```kotlin
// Pattern: passare parametri via costruttore Decompose
// PRIMA (Android-only):
class HomeViewModel(
    private val savedStateHandle: SavedStateHandle,
) {
    val diaryId = savedStateHandle.get<String>("diaryId")
}

// DOPO (KMP-compatible):
class HomeViewModel(
    private val diaryId: String?,  // passato da Decompose component
) {
    // uso diretto
}
```

Decompose gia' gestisce la serializzazione dei parametri navigation.
Questo fix si applica a TUTTI i ViewModel del progetto in un colpo solo.
