# Calmify — Stato Progetto e Guida per Nuove Sessioni

> **LEGGERE SEMPRE all'inizio di ogni sessione.**
> Ultimo aggiornamento: 2026-03-18

## TL;DR — Stato Attuale

**Migrazione KMP source set COMPLETATA. Zero file legacy. Zero `kotlin.srcDirs` mapping.**

- **17/18 moduli** usano convention plugin KMP (`calmify.kmp.library` o `calmify.kmp.compose`)
- **Solo `app`** resta Android-only (`com.android.application`)
- **assembleDebug OK**: 562 tasks, 0 errori
- **DI**: Koin 4.1.1 (Hilt completamente rimosso)
- **Database**: SQLDelight 2.0.2 (Room completamente rimosso)
- **Navigation**: Decompose 3.4.0 (Navigation Compose rimosso)
- **MVI**: Tutti i 18+ ViewModel usano `MviViewModel<Intent, State, Effect>`
- **Production Plan**: `PRODUCTION_READY_PLAN.md` — PRO Switch, Play Store, compliance, i18n

### Stato Codice (verificato 2026-03-18)
- **commonMain**: 200 file (60%) — UI, ViewModel, domain, contracts, utility
- **androidMain**: 135 file (40%) — Firebase, Filament, Camera, Audio, Platform-specific UI
- **Legacy src/main/java**: 0 file (eliminati tutti i `kotlin.srcDirs` mapping)
- **app module**: 13 file (Android-only entry point, non KMP)
- Repository interfaces in `core/util` commonMain, implementations Firebase in `data/mongo` androidMain
- Firebase AI, Functions, RemoteConfig, Realtime DB, Play Billing → restano androidMain (no KMP SDK)
- Feature Flags: 10 flag in Firebase Remote Config (incl. `premium_enabled` per PRO Switch)

---

## Architettura Moduli (18 totali)

```
app/                          Android-only (MainActivity, DecomposeApp, RootComponent, Koin setup) — 13 file
│
├── core/util/                KMP Library — commonMain: 44 file | androidMain: 2 file
├── core/ui/                  KMP Compose — commonMain: 12 file | androidMain: 6 file
├── data/mongo/               KMP Library + SQLDelight — commonMain: 4 .sq | androidMain: 24 file
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
| **Storage** | Firebase Firestore | + Storage, Functions | 19 repository implementations |
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
firebaseModule          — FirebaseAuth, AuthProvider, Storage, Firestore, Functions, RemoteConfig
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

## Prossimi Passi

### Production Launch (vedi `PRODUCTION_READY_PLAN.md`)
1. Fase 0: Fix signing, ProGuard, Crashlytics, Privacy Policy, Prominent Disclosure
2. Implementare WaitlistSubscriptionRepository + WaitlistDialog (PRO Switch)
3. Compilare Play Console: Data Safety, Health Apps Declaration, IARC
4. Alpha → Beta → Production (roadmap 20 settimane)

### Per supportare iOS/Desktop (futuro)
1. Creare `iosApp/` con SwiftUI che consuma i moduli commonMain (200 file pronti)
2. Implementare `actual` per: Theme, AuthProvider, SqlDriver (iOS = NativeSqliteDriver)
3. Firebase repos: GitLive per Firestore/Auth/Storage, alternative per AI/Functions/Billing
4. Filament: iOS wrapper C++/Swift, Desktop wrapper Metal/OpenGL

---

## File di Riferimento

| File | Scopo |
|------|-------|
| `.claude/KMP_STATUS.md` | **Questo file** — stato progetto completo |
| `.claude/refactor-status.md` | Log cronologico di TUTTE le operazioni (470+ righe) |
| `.claude/plans/valiant-noodling-prism.md` | Piano originale migrazione KMP (10 onde) |
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
