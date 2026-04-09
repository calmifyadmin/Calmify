# Calmify — Stato Progetto e Guida per Nuove Sessioni

> Ultimo aggiornamento: **2026-04-09**
> Per tracker dettagliato migrazione: vedi `.claude/KMP_MIGRATION_STATUS.md`
> Per piani backend: vedi `.claude/BACKEND_*.md` (4 file)

## TL;DR — Stato Attuale

**Fase 1 KMP COMPLETATA (2026-04-09). 72.5% codice in commonMain. Backend refactor in corso.**

- **18 moduli** usano convention plugin KMP (`calmify.kmp.library` o `calmify.kmp.compose`)
- **Solo `app`** resta Android-only (`com.android.application`)
- **assembleDebug OK**: 943 tasks, 0 errori
- **DI**: Koin 4.1.1 (Hilt completamente rimosso)
- **Database**: SQLDelight 2.0.2 (5 tabelle: ChatMessage, ChatSession, CachedThread, ImageToUpload, ImageToDelete)
- **Navigation**: Decompose 3.4.0 (18 @Serializable destinations)
- **MVI**: Tutti i 18+ ViewModel usano `MviViewModel<Intent, State, Effect>`
- **Repository**: 36 interfaces in core/util, 36 Firestore implementations in data/mongo
- **Branch attivo**: `backend-architecture-refactor` (base: master @ `08ef101`)

### Stato Codice (verificato 2026-04-09)
- **commonMain**: **237 file** (72.5%) — UI, ViewModel, domain, contracts, utility, Ktor clients
- **androidMain**: **90 file** (27.5%) — tutti con blockers reali (Firebase, audio, Filament, permissions)
- **Legacy src/main/java**: 0 file
- **app module**: 13 file (Android-only entry point, non KMP)
- **SavedStateHandle rimasti**: 2 (HomeViewModel, WriteViewModel) — gli altri rimossi

### Moduli 100% commonMain (dopo Fase 1)
- history, habits (4/5), meditation (4/6), insight, messaging, avatar-creator, notifications, onboarding, profile, search, subscription

### Cosa blocca i 90 file androidMain restanti

| Categoria | File | Blockers |
|-----------|------|----------|
| **Firebase/Google SDK** | ~15 | FirebaseStorage, FirebaseAuth, GoogleSignIn, Filament |
| **Android platform APIs** | ~25 | AudioTrack/Record, Camera2, Permissions, Toast, Context |
| **java.time con UI complessa** | ~10 | CalendarDialog (maxkeppeler), date pickers |
| **Coil/Accompanist** | ~8 | Image loading, HorizontalPager |
| **Transitive deps** | ~32 | Entry points/screens bloccati dai VM ancora in androidMain |

### Prossima evoluzione: Backend Architecture

| Workstream | Effort | Stato | Plan file |
|---|---|---|---|
| Protobuf (shared-models) | 1 sett. | NOT STARTED | `.claude/BACKEND_PROTOBUF.md` |
| Ktor Server (Cloud Run) | 3-5 sett. | NOT STARTED | `.claude/BACKEND_KTOR_SERVER.md` |
| AI Server-Side | 1 sett. | NOT STARTED | `.claude/BACKEND_AI_SERVER.md` |
| Sync Engine (offline-first) | 2 sett. | NOT STARTED | `.claude/BACKEND_SYNC_ENGINE.md` |

### Analisi androidMain — Cosa resta davvero platform-specific (LEGACY — da aggiornare)

| Categoria | File | % del totale | Strategia |
|-----------|------|-------------|-----------|
| **Già migrabili → commonMain** | 91 | 66% | Spostare fisicamente (zero import Android) |
| **Migrabili con Ktor** | 28 | 20% | Firebase SDK → Ktor + Firebase REST API (commonMain) |
| **expect/actual necessario** | 17 | 12% | Interface commonMain + actual per piattaforma |
| **Platform-only** | 1 | <1% | `PlayBillingSubscriptionRepository` (Android forever) |

### Strategia Cross-Platform (Android + iOS + Web Wasm)

```
commonMain (Kotlin)          → Condiviso 1 volta, gira ovunque
  ├── UI: Compose Multiplatform
  ├── Data: Ktor HTTP → Firebase REST API (Firestore, Auth, Storage)
  ├── DB: SQLDelight (driver per piattaforma)
  ├── Nav: Decompose
  ├── DI: Koin
  └── Logic: ViewModel, UseCase, domain

androidMain (actual)         → Solo adapter hardware
  ├── Audio I/O: AudioTrack/AudioRecord (9 file)
  ├── 3D render: Filament JNI (4 file)
  ├── Camera: CameraX (2 file)
  ├── Theme: Dynamic Colors Android 12+ (1 file)
  ├── Permissions: android.Manifest (1 file)
  └── Billing: Play Billing (1 file)

iosMain (actual, futuro)     → Stessi adapter
  ├── Audio I/O: AVAudioEngine
  ├── 3D render: Filament Metal/SceneKit
  ├── Camera: AVCaptureSession
  ├── Theme: UIKit appearance
  ├── Permissions: Info.plist
  └── Billing: StoreKit2

wasmJsMain (actual, futuro)  → Stessi adapter
  ├── Audio I/O: WebAudio API
  ├── 3D render: Three.js / Filament WASM
  ├── Camera: getUserMedia
  ├── Theme: CSS media query
  ├── Permissions: browser Permissions API
  └── Billing: Stripe/web checkout
```

**Nota chiave**: Con Compose Wasm il codice Web è **Kotlin**, non JavaScript.
Firebase REST API funziona via Ktor da qualsiasi piattaforma — stessa implementazione.

---

## Architettura Moduli (18 totali)

```
app/                          Android-only (MainActivity, DecomposeApp, RootComponent, Koin setup) — 13 file
│
├── core/util/                KMP Library — commonMain: 44 file | androidMain: 2 file
├── core/ui/                  KMP Compose — commonMain: 12 file | androidMain: 6 file
├── data/mongo/               KMP Library + SQLDelight — commonMain: 4 .sq + 2 .kt | androidMain: 24 file
│
├── features/auth/            commonMain: 2 | androidMain: 5
├── features/home/            commonMain: 32 | androidMain: 23
├── features/write/           commonMain: 12 | androidMain: 9
├── features/chat/            commonMain: 13 | androidMain: 27 (+ NDK/CMake)
├── features/humanoid/        commonMain: 10 | androidMain: 21 (Filament)
├── features/history/         commonMain: 3 | androidMain: 5
├── features/insight/         commonMain: 4 | androidMain: 0
├── features/profile/         commonMain: 3 | androidMain: 0
├── features/settings/        commonMain: 10 | androidMain: 1
├── features/onboarding/      commonMain: 3 | androidMain: 0
├── features/feed/            commonMain: 3 | androidMain: 2
├── features/composer/        commonMain: 1 | androidMain: 4
├── features/social-profile/  commonMain: 4 | androidMain: 3
├── features/search/          commonMain: 5 | androidMain: 0
├── features/notifications/   commonMain: 5 | androidMain: 0
├── features/messaging/       commonMain: 8 | androidMain: 0
├── features/subscription/    commonMain: 4 | androidMain: 1
├── features/thread-detail/   commonMain: 3 | androidMain: 2
└── features/avatar-creator/  commonMain: 19 | androidMain: 0
```

### Source Set Status
- **Zero** `kotlin.srcDirs('src/main/java')` in qualsiasi build.gradle
- **Zero** file .kt in `src/main/java` (solo 2 .md/README residui in chat e humanoid)
- Tutti i file sono fisicamente in `src/commonMain/kotlin/` o `src/androidMain/kotlin/`

---

## Tecnologie Chiave

| Componente | Tecnologia | Versione | Note |
|-----------|-----------|----------|------|
| **Build** | Convention plugins | build-logic/ | `calmify.kmp.library`, `calmify.kmp.compose` |
| **DI** | Koin | 4.1.1 | Sole DI. Hilt/javax.inject rimossi |
| **Navigation** | Decompose | 3.4.0 | 18 @Serializable destinations, StackNavigation |
| **Database** | SQLDelight | 2.0.2 | 4 tabelle. Room rimosso completamente |
| **UI** | Compose Multiplatform | + Material3 | Compose BOM per Android, MP per common |
| **Auth** | Firebase Auth | via AuthProvider | Interface in commonMain, impl in androidMain |
| **Storage** | Firebase Firestore | + Storage | 19 repository implementations |
| **Feature Flags** | Firestore document | config/flags | Migrato da Remote Config (real-time listener) |
| **Presence** | Firestore collection | presence/{userId} | Migrato da Realtime Database |
| **Cloud Functions** | Ktor HTTP | commonMain | KMP client per callable functions |
| **AI/Voice** | Gemini API | Live WebSocket | Full-duplex AEC, Silero VAD, Sherpa-ONNX TTS |
| **3D** | Filament | 1.68.2 | VRM avatar, lip-sync, bone animation |
| **Async** | Coroutines + Flow | | StateFlow ovunque |
| **Images** | Coil | 3.x | KMP-compatible |
| **Serialization** | kotlinx-serialization | | JSON, Decompose configs |

---

## Convention Plugins (build-logic/)

### `calmify.kmp.library`
- Plugin: `kotlin-multiplatform` + `com.android.library`
- Target: Android, JVM Desktop, iOS (x64, arm64, simulatorArm64)
- commonMain: coroutines-core
- Gerarchia iOS automatica (iosMain)

### `calmify.kmp.compose`
- Estende `calmify.kmp.library`
- Plugin: + `org.jetbrains.compose` + `kotlin.plugin.compose`
- commonMain: + runtime, foundation, material3, ui, components.resources
- androidMain: + activity-compose
- desktop: + compose.desktop.currentOs

---

## commonMain — File Condivisi (Pronti per iOS/Desktop)

### core/util/src/commonMain/kotlin/com/lifo/util/

```
auth/AuthProvider.kt                    — Interface autenticazione
connectivity/ConnectivityObserver.kt    — Interface connettività
mvi/MviContract.kt                      — Intent/State/Effect interfaces
mvi/MviViewModel.kt                     — Base ViewModel KMP (CoroutineScope, no AndroidX)
mvi/PlatformViewModel.kt                — expect/actual per lifecycle
model/Mood.kt                           — Enum mood (puro, senza Color/drawable)
model/ChatEmotion.kt                    — Enum emozioni (puro)
model/ChatModels.kt                     — ChatSession, ChatMessage, MessageStatus
model/Diary.kt                          — Modello diario
model/DiaryInsight.kt                   — Modello insight
model/HomeContentItem.kt                — Modello feed unificato
model/ProfileSettings.kt                — Impostazioni profilo
model/PsychologicalProfile.kt           — Profilo psicologico
model/RequestState.kt                   — Loading/Success/Error sealed class
model/WellbeingSnapshot.kt              — Snapshot benessere
repository/ (19 interfaces)             — Tutti i contratti repository
audio/AudioVisemeAnalyzer.kt            — Analisi visemi audio
speech/SynchronizedSpeechBridge.kt      — Bridge TTS sincronizzato
usecase/UseCase.kt                      — UseCase, FlowUseCase, NoParam variants
Constants.kt                            — Costanti app
Screen.kt                               — Enum schermate
```

### core/ui/src/commonMain/kotlin/com/lifo/ui/

```
theme/Color.kt, Elevation.kt, Type.kt  — Design tokens
theme/Theme.kt                          — expect CalmifyAppTheme
components/AlertDialog.kt               — Dialog riutilizzabile
components/ContainedLoadingIndicator.kt — Loading indicator
components/GoogleButton.kt              — Bottone Google (iconPainter, no R.drawable)
components/stepper/ExpressiveStepper.kt — Stepper animato
providers/ChatEmotionUiProvider.kt      — Colori emozioni
ErrorBoundary.kt                        — Error boundary composable
GalleryState.kt                         — Stato galleria immagini
```

### data/mongo/src/commonMain/kotlin/ (Ktor clients — KMP)

```
repository/KtorCloudFunctionsClient.kt     — Shared Ktor helper per Firebase callable functions (HTTPS POST)
repository/KtorContentModerationRepository.kt — Content moderation via Cloud Functions (commonMain-ready)
```

### data/mongo/src/commonMain/sqldelight/ (SQLDelight schema)

```
ChatSession.sq      — 7 queries (getAllSessions, getSession, insert, update, delete, incrementMessageCount, updateLastMessage)
ChatMessage.sq      — 8 queries (getMessagesForSession, getMessage, insert, update, delete, deleteForSession, updateStatus, getCount)
ImageToUpload.sq    — 4 queries (getAll, add, delete, cleanup)
ImageToDelete.sq    — 3 queries (getAll, add, cleanup)
```

---

## Dependency Injection (Koin)

### allKoinModules (app/di/KoinModules.kt)

```
databaseModule          — CalmifyDatabase (SQLDelight), Queries, ConnectivityObserver
firebaseModule          — FirebaseAuth, AuthProvider, Storage, Firestore, Functions, Analytics
repositoryModule        — 19 repository implementations (tutte singleton)
networkModule           — (vuoto, riservato per Ktor)
socialModule            — (vuoto, repos in repositoryModule)
chatKoinModule          — 13 servizi audio + ChatViewModel + LiveChatViewModel
homeKoinModule          — 6 use case + HomeViewModel + SnapshotViewModel
humanoidKoinModule      — 8 servizi Filament/VRM + HumanoidViewModel
authKoinModule          — AuthenticationViewModel
writeKoinModule         — WriteViewModel
historyKoinModule       — HistoryViewModel
insightKoinModule       — InsightViewModel
profileKoinModule       — ProfileViewModel
settingsKoinModule      — SettingsViewModel
onboardingKoinModule    — OnboardingViewModel
feedKoinModule          — FeedViewModel
composerKoinModule      — ComposerViewModel
socialProfileKoinModule — SocialProfileViewModel
searchKoinModule        — SearchViewModel
notificationsKoinModule — NotificationsViewModel
messagingKoinModule     — MessagingViewModel
subscriptionKoinModule  — SubscriptionViewModel
```

### AuthProvider (Pattern Cruciale)

```kotlin
// commonMain — core/util/src/commonMain/kotlin/com/lifo/util/auth/AuthProvider.kt
interface AuthProvider {
    val currentUserId: String?
    val isAuthenticated: Boolean
    val currentUserDisplayName: String?
    val currentUserEmail: String?
    val currentUserPhotoUrl: String?
    suspend fun signOut()
}

// androidMain — core/util/src/androidMain/kotlin/com/lifo/util/auth/FirebaseAuthProvider.kt
class FirebaseAuthProvider(private val auth: FirebaseAuth) : AuthProvider { ... }

// Registrazione Koin — data/mongo/di/MongoKoinModule.kt firebaseModule
single<AuthProvider> { FirebaseAuthProvider(get()) }
```

**TUTTI i ViewModel usano AuthProvider, MAI FirebaseAuth direttamente** (tranne ChatViewModel e LiveChatViewModel che usano ancora FirebaseAuth per funzionalità specifiche Gemini).

---

## Navigation (Decompose 3.4.0)

### File Chiave
- `app/.../navigation/decompose/RootDestination.kt` — 18 @Serializable destinations
- `app/.../navigation/decompose/RootComponent.kt` — 18 typed Child classes + 15 navigation methods
- `app/.../DecomposeApp.kt` — Children stack renderer (709 LOC), bottom bar, drawer, FAB, dialogs

### Destinations
Auth, Onboarding, Home, Write(diaryId?), Chat(sessionId?), LiveChat, History, ChatHistoryFull, DiaryHistoryFull, Humanoid, Settings, SettingsPersonalInfo, SettingsHealthInfo, SettingsLifestyle, SettingsGoals, Profile, Insight(diaryId), WellbeingSnapshot

### Entry Points (Bridge Moduli)
I feature module hanno `*EntryPoint.kt` composable che espongono le schermate interne al modulo app:
- `features/auth/navigation/AuthEntryPoint.kt`
- `features/home/navigation/HomeEntryPoint.kt`
- `features/write/navigation/WriteEntryPoint.kt`
- `features/history/navigation/HistoryEntryPoint.kt`
- `features/settings/navigation/SettingsEntryPoint.kt`

---

## MVI Pattern

### Base Class (commonMain)
```kotlin
abstract class MviViewModel<Intent : MviContract.Intent, State : MviContract.State, Effect : MviContract.Effect>(
    initialState: State
) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val state: StateFlow<State>       // UI osserva questo
    val effects: Flow<Effect>          // One-shot events
    fun onIntent(intent: Intent)       // UI chiama questo
    protected abstract fun handleIntent(intent: Intent)
    protected fun updateState(reducer: State.() -> State)
    protected fun sendEffect(effect: Effect)
    open fun onCleared()               // koin-compose-viewmodel lo chiama automaticamente
}
```

### Pattern in Ogni Feature
```
FeatureContract.kt    — Intent sealed interface + State data class + Effect sealed interface
FeatureViewModel.kt   — extends MviViewModel<Intent, State, Effect>
FeatureScreen.kt      — Composable che observa state e dispatcha intent
```

---

## Database (SQLDelight)

### Creazione
```kotlin
// app/di/KoinModules.kt
val databaseModule = module {
    single {
        val driver = AndroidSqliteDriver(
            schema = CalmifyDatabase.Schema,
            context = androidContext(),
            name = DATABASE_NAME  // "calmify_db"
        )
        CalmifyDatabase(driver)
    }
    single { get<CalmifyDatabase>().imageToUploadQueries }
    single { get<CalmifyDatabase>().imageToDeleteQueries }
}
```

### Gotcha Importanti
- **`AS Boolean`/`AS Int` NON funzionano** in SQLDelight 2.0.2 — generano `import Boolean` errato. Usare `INTEGER` e convertire manualmente (`messageCount.toInt()`, `isLiveMode == 1L`)
- Le query reattive usano `.asFlow().mapToList(Dispatchers.IO)` (da `sqldelight-coroutines-extensions`)
- Le query one-shot usano `.executeAsOneOrNull()` o `.executeAsList()`

---

## Build System

### Comandi Principali
```bash
./gradlew assembleDebug          # Build APK debug
./gradlew installDebug            # Install su device
./gradlew :module:compileDebugKotlinAndroid    # Compila singolo modulo
./gradlew :module:compileCommonMainKotlinMetadata  # Verifica commonMain
```

### Gotcha Build KMP
- `platform(libs.firebase.bom)` NON funziona in `sourceSets.androidMain.dependencies` → usare versioni esplicite con suffisso `-kmp` nel version catalog
- Convention plugins non possono usare `libs.versions.xxx.get()` nel proprio build.gradle.kts → hardcodare versioni
- Root build.gradle DEVE dichiarare `apply false` per: kotlin-multiplatform, org.jetbrains.compose, kotlin-serialization
- gradle.properties: `kotlin.native.ignoreDisabledTargets=true` + `kotlin.mpp.applyDefaultHierarchyTemplate=false`

---

## Audio Pipeline (features/chat)

### Architettura Dual
1. **Text Chat** (ChatViewModel): Gemini API → GeminiNativeVoiceSystem → TTS
2. **Live Chat** (LiveChatViewModel): Mic → VAD → WebSocket → Gemini Live → AudioTrack

### Componenti Chiave
- `FullDuplexAudioSession` — AEC/NS/AGC hardware, MODE_IN_COMMUNICATION
- `SileroVadEngine` — Voice Activity Detection (ONNX)
- `GeminiLiveWebSocketClient` — WebSocket bidirezionale
- `GeminiLiveAudioManager` — Orchestrazione audio
- `AdaptiveBargeinDetector` — Barge-in intelligente
- NDK/CMake: Oboe audio engine nativo (`src/main/cpp/`)

### AEC Fix Critico
- `MODE_IN_COMMUNICATION` + `setSpeakerphoneOn(true)` attiva HAL AEC
- `USAGE_MEDIA` su AudioTrack per qualità full-bandwidth (NON VOICE_COMMUNICATION)

---

## 3D Avatar (features/humanoid)

### Stack
- **Filament 1.68.2** — Rendering engine
- **VRM** — Formato modello 3D avatar
- **VRMA** — Formato animazione
- **Lip-sync** — PhonemeConverter → VisemeMapper → VrmBlendShapeController

### Componenti
- `FilamentRenderer` — Core rendering (TextureView)
- `VrmLoader` — Caricamento modelli .vrm da assets
- `AnimationCoordinator` — Priorità e blending animazioni
- `BlinkController`, `IdleAnimationController`, `IdlePoseController` — Animazioni idle
- `LipSyncController` — Sincronizzazione labbra con TTS

---

## Prossimi Passi — Roadmap KMP

### Fase 1: Low-Hanging Fruit (91 file → commonMain) — ~2-3 giorni
Spostare tutti i file con **ZERO import Android**:
- 60+ screen/component Compose (già Compose MP, solo fisicamente in androidMain)
- 15+ use case (logica pura)
- Tutti i ViewModel senza java.time
- Tutti i Koin module, entry point, mapper
- `java.time` → `kotlinx.datetime` in 3 file (DateFormatters, HomeVM, WriteVM)
- `ColorUtils` → pure Kotlin HSL math

### Fase 2: Firebase → Ktor REST (28 file → commonMain) — ~5-7 giorni
Migrare tutti i repository da Firebase SDK a **Ktor + Firebase REST API**:
- 18 Firestore repositories → Ktor HTTP client (REST API)
- Firebase Auth → Ktor + Firebase Auth REST API
- Firebase Storage → Ktor + GCS REST API
- Firebase Analytics → Ktor + Measurement Protocol
- MongoKoinModule → setup Ktor HttpClient in commonMain
- **Risultato**: UNA sola implementazione per Android, iOS, Web
- **Compose Wasm**: tutto resta Kotlin (no JavaScript), Ktor funziona su Wasm
- **Già fatto**: Cloud Functions (KtorCloudFunctionsClient), FeatureFlags (Firestore), Presence (Firestore)

### Fase 3: expect/actual Audio & Rendering (17 file) — ~7-9 giorni
Creare interface in commonMain + actual per piattaforma:
- Audio I/O: `expect interface AudioEngine` (9 file)
- Filament 3D: `expect interface Renderer` (4 file)
- Camera: `expect interface CameraPreview` (2 file)
- Permissions + Theme (2 file)

### Fase 4: Piattaforme Target
- **Android**: Già funzionante (Play Store ready)
- **Web (Compose Wasm)**: Firebase Hosting, costo €0, stessa codebase Kotlin
- **iOS**: `iosApp/`, actual per Audio (AVAudioEngine), Filament (Metal), StoreKit2
- **Desktop**: actual per Audio (Java Sound API), Filament (OpenGL/Metal)

### Production Launch (vedi `PRODUCTION_READY_PLAN.md`)
1. Fase 0: Fix signing, ProGuard, Crashlytics, Privacy Policy, Prominent Disclosure
2. Implementare WaitlistSubscriptionRepository + WaitlistDialog (PRO Switch)
3. Compilare Play Console: Data Safety, Health Apps Declaration, IARC
4. Alpha → Beta → Production

### Stima Totale Migrazione Completa
| Fase | File | Giorni | Rischio |
|------|------|--------|---------|
| Fase 1: Move to commonMain | 91 | 2-3 | Minimo |
| Fase 2: Firebase → Ktor | 28 | 5-7 | Medio |
| Fase 3: expect/actual | 17 | 7-9 | Medio |
| **TOTALE** | **136/137** | **14-19 giorni** | |
| **Risultato** | **~95% commonMain** | | Solo 1 file Android-only (PlayBilling) |

---

## File di Riferimento

| File | Scopo |
|------|-------|
| `.claude/KMP_STATUS.md` | **Questo file** — stato progetto completo |
| `.claude/PROD_PROGRESS.md` | **Tracker produzione** — checklist Fase 0-5 con stato |
| `.claude/refactor-status.md` | Log cronologico di TUTTE le operazioni (470+ righe) |
| `.claude/plans/valiant-noodling-prism.md` | Piano originale migrazione KMP (10 onde) |
| `PRODUCTION_READY_PLAN.md` | Piano production-ready completo (business, legal, i18n, infra) |
| `CLAUDE.md` | Istruzioni operative per Jarvis + overview progetto |
| `build-logic/convention/` | Convention plugins KMP |
| `gradle/libs.versions.toml` | Version catalog completo |
| `buildSrc/src/main/java/ProjectConfig.kt` | Configurazione build centralizzata |

---

## Cronologia Migrazione

| Data | Evento |
|------|--------|
| 2026-02-27 | Fasi 1-3: Cleanup core/util, core/ui, domain extraction |
| 2026-02-28 | Fasi 4-15: Feature cleanup, AR removal, Log→println, Hilt→Koin |
| 2026-03-01 | Wave 6B: Navigation Compose → Decompose 3.4.0 |
| 2026-03-01 | Wave 6C-F: 6 social feature modules + Firestore repos |
| 2026-03-01 | KMP Onde 1-8: core/util, core/ui, social, Firebase, features, SQLDelight, chat |
| 2026-03-02 | KMP Onde 9-10: humanoid, integrazione finale — **BUILD OK 746 tasks** |
| 2026-03-17 | KMP source set migration: 200 commonMain / 135 androidMain / 0 legacy |
| 2026-03-17 | Rimossi TUTTI i `kotlin.srcDirs('src/main/java')` mapping |
| 2026-03-17 | Fix: java.time→kotlinx.datetime, System.currentTimeMillis→KMP, String.format→KMP |
| 2026-03-17 | Coil downgrade 3.3.0→3.1.0 (Kotlin 2.1.0 ABI compatibility) |
| 2026-03-18 | PRODUCTION_READY_PLAN.md: Play Store, business model, compliance, PRO Switch |
| 2026-03-18 | Firebase RemoteConfig → Firestore document `config/flags` (FirestoreFeatureFlagRepository) |
| 2026-03-18 | Firebase RTDB Presence → Firestore collection `presence/` (FirestorePresenceRepository) |
| 2026-03-18 | Cloud Functions → Ktor HTTP client in commonMain (KtorCloudFunctionsClient) |
| 2026-03-18 | Rimossi deps: firebase-database-kmp, firebase-config-kmp — 2 servizi Firebase in meno |
| 2026-03-19 | MediaCarousel unificato: expect/actual (M3 Expressive su Android, HorizontalPager su common) |
| 2026-03-19 | Eliminati: MediaGrid, Gallery(), FirebaseImageHelper — un solo componente per tutte le immagini |
| 2026-03-19 | WriteViewModel usa MediaUploadRepository.resolveImageUrls() (no più Firebase diretto nell'UI) |
| 2026-03-19 | DiaryHolder + ThreadPostCard + JournalHomeScreen tutti migrati a MediaCarousel |
