# Calmify — Stato Progetto e Guida per Nuove Sessioni

> **LEGGERE SEMPRE all'inizio di ogni sessione.**
> Ultimo aggiornamento: 2026-03-02

## TL;DR — Stato Attuale

**Migrazione KMP completata al 100%.** L'app compila e funziona.

- **17/18 moduli** usano convention plugin KMP (`calmify.kmp.library` o `calmify.kmp.compose`)
- **Solo `app`** resta Android-only (`com.android.application`)
- **assembleDebug OK**: 746 tasks, 0 errori
- **DI**: Koin 4.1.1 (Hilt completamente rimosso, javax.inject rimosso)
- **Database**: SQLDelight 2.0.2 (Room completamente rimosso)
- **Navigation**: Decompose 3.4.0 (Navigation Compose rimosso)
- **MVI**: Tutti i 18+ ViewModel usano `MviViewModel<Intent, State, Effect>`

---

## Architettura Moduli (18 totali)

```
app/                          Android-only (MainActivity, DecomposeApp, RootComponent, Koin setup)
│
├── core/util/                KMP Library — commonMain: 38 file (modelli, repository interfaces, MVI, AuthProvider)
│                                          androidMain: 2 file (FirebaseAuthProvider, PlatformViewModel)
│
├── core/ui/                  KMP Compose — commonMain: 11 file (Theme, componenti, providers)
│                                          androidMain: 1 file (Theme.android.kt) + 3 legacy in src/main/java
│
├── data/mongo/               KMP Library + SQLDelight — commonMain: 4 .sq files (schema database)
│                                                        androidMain: 21 file Firestore repos (src/main/java)
│
├── features/auth/            KMP Compose — tutto in androidMain (src/main/java) — 7 file
├── features/home/            KMP Compose — tutto in androidMain (src/main/java) — 49 file
├── features/write/           KMP Compose — tutto in androidMain (src/main/java) — 17 file
├── features/chat/            KMP Compose — tutto in androidMain (src/main/java) — 40 file + NDK/CMake
├── features/humanoid/        KMP Compose — tutto in androidMain (src/main/java) — 31 file + Filament
├── features/history/         KMP Compose — tutto in androidMain (src/main/java) — 8 file
├── features/insight/         KMP Compose — tutto in androidMain (src/main/java) — 4 file
├── features/profile/         KMP Compose — tutto in androidMain (src/main/java) — 3 file
├── features/settings/        KMP Compose — tutto in androidMain (src/main/java) — 10 file
├── features/onboarding/      KMP Compose — tutto in androidMain (src/main/java) — 8 file
├── features/feed/            KMP Compose — tutto in androidMain (src/main/java) — 5 file
├── features/composer/        KMP Compose — tutto in androidMain (src/main/java) — 5 file
├── features/social-profile/  KMP Compose — tutto in androidMain (src/main/java) — 5 file
├── features/search/          KMP Compose — tutto in androidMain (src/main/java) — 5 file
├── features/notifications/   KMP Compose — tutto in androidMain (src/main/java) — 5 file
├── features/messaging/       KMP Compose — tutto in androidMain (src/main/java) — 7 file
└── features/subscription/    KMP Compose — tutto in androidMain (src/main/java) — 5 file
```

### Pattern Source Set

I moduli feature e data/mongo usano `kotlin.srcDirs('src/main/java')` nel blocco `androidMain` del build.gradle. Questo mappa il codice legacy alla source set KMP senza spostare fisicamente i file. Quando si vorrà supportare iOS/Desktop, i file puri Kotlin andranno spostati in `src/commonMain/kotlin/`.

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

## Prossimi Passi (Opzionali)

### Per supportare iOS/Desktop
1. Spostare file puri da `src/main/java/` → `src/commonMain/kotlin/` nei moduli feature
2. Creare `iosApp/` con SwiftUI che consuma i moduli commonMain
3. Implementare `actual` per: Theme, AuthProvider (già fatto), SqlDriver (iOS = NativeSqliteDriver)
4. I moduli chat e humanoid sono "KMP parziale" (~60% spostabile in commonMain)

### Cleanup Opzionale
- Eliminare `ProfileScreen.kt` dal modulo app (dead code)
- Rimuovere `kotlin.srcDirs('src/main/java')` quando i file vengono effettivamente spostati
- Spostare `Gallery.kt`, `MoodUiProvider.kt`, `FirebaseImageHelper.kt` da `core/ui/src/main/java` a `androidMain/kotlin`

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
