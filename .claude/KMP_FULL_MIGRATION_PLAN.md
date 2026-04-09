> **NOTA (2026-04-09)**: Questo file e' stato scritto PRIMA della Fase 1. Molte azioni sono gia' completate.
> Per lo stato attuale, vedi `.claude/KMP_MIGRATION_STATUS.md` (tracker aggiornato).
> Per i piani backend, vedi `.claude/BACKEND_*.md`.

# Piano Migrazione KMP Completa — Calmify

> **Obiettivo**: Tutto il codice in `commonMain`, con solo 1-2 `actual` per piattaforma.
> **Stato attuale**: 55 file commonMain (15%) / 307 file Android-only (84%)
> **Target**: ~280 file commonMain (~77%) / ~85 file platform-specific (~23%)
> **Creato**: 2026-03-16

---

## Indice

1. [Architettura Target](#1-architettura-target)
2. [Strategia expect/actual](#2-strategia-expectactual)
3. [Fase 0 — Preparazione](#fase-0--preparazione-1-giorno)
4. [Fase 1 — Core Puri](#fase-1--core-puri-2-giorni)
5. [Fase 2 — Feature Semplici](#fase-2--feature-semplici-3-giorni)
6. [Fase 3 — Feature Medie](#fase-3--feature-medie-3-giorni)
7. [Fase 4 — Feature Complesse](#fase-4--feature-complesse-5-giorni)
8. [Fase 5 — Data Layer (Firebase → GitLive)](#fase-5--data-layer-firebase--gitlive-3-giorni)
9. [Fase 6 — iOS/Desktop actual](#fase-6--iosdesktop-actual-3-giorni)
10. [Riepilogo File per Modulo](#riepilogo-file-per-modulo)
11. [Rischi e Mitigazioni](#rischi-e-mitigazioni)

---

## 1. Architettura Target

```
commonMain/ (77% del codice — condiviso Android/iOS/Desktop)
├── Models, Enums, DTOs
├── Repository INTERFACES (19)
├── UseCase classes
├── ViewModel + Contract (MVI)
├── Screen Composable (Compose Multiplatform)
├── UI Components (Compose Multiplatform)
├── Navigation (Decompose — gia' KMP)
├── DI Modules (Koin — gia' KMP)
├── WebSocket client (Ktor — gia' KMP)
├── VAD logic (Silero ONNX — cross-platform)
├── Animation logic (lip-sync, blink, idle — pure Kotlin)
├── Theme expect
├── Audio expect (recorder, player)
├── Firebase expect (auth, firestore, storage)
├── Camera/Gallery expect
└── 3D Renderer expect

androidMain/ (solo actual implementations)
├── Theme.android.kt              — Dynamic colors, status bar
├── FirebaseAuthProvider.kt       — Firebase Auth SDK (gia' esiste)
├── FirestoreRepositories (19)    — Firebase Firestore SDK (o GitLive)
├── AudioRecorder.android.kt     — AudioRecord + Oboe NDK
├── AudioPlayer.android.kt       — AudioTrack
├── ImagePicker.android.kt       — MediaStore / ActivityResult
├── FilamentRenderer.android.kt  — Filament JNI + TextureView
├── SqlDriver.android.kt         — AndroidSqliteDriver
└── PlatformViewModel.android.kt — (gia' esiste)

iosMain/ (mirror degli actual Android)
├── Theme.ios.kt                  — (gia' esiste)
├── FirebaseAuthProvider.ios.kt   — Firebase iOS SDK (o GitLive)
├── FirestoreRepositories (19)    — Firebase iOS SDK (o GitLive)
├── AudioRecorder.ios.kt         — AVAudioEngine
├── AudioPlayer.ios.kt           — AVAudioSession
├── ImagePicker.ios.kt           — PHPickerViewController
├── SceneKitRenderer.ios.kt      — SceneKit/RealityKit (alternativa a Filament)
├── SqlDriver.ios.kt             — NativeSqliteDriver
└── PlatformViewModel.ios.kt     — (gia' esiste)

desktopMain/ (mirror semplificato)
├── Theme.desktop.kt              — (gia' esiste)
├── AudioRecorder.desktop.kt     — javax.sound.sampled
├── AudioPlayer.desktop.kt       — javax.sound.sampled
├── SqlDriver.desktop.kt         — JdbcSqliteDriver
└── PlatformViewModel.desktop.kt — (gia' esiste)
```

---

## 2. Strategia expect/actual

### 2.1 Interfacce da creare in commonMain

Queste `expect` declarations vanno create PRIMA di spostare i file feature:

```kotlin
// --- core/util/src/commonMain/kotlin/com/lifo/util/platform/ ---

// Audio I/O
expect class PlatformAudioRecorder {
    fun startRecording(sampleRate: Int = 16000)
    fun stopRecording()
    fun getAudioFlow(): Flow<ByteArray>
}

expect class PlatformAudioPlayer {
    fun play(pcmData: ByteArray, sampleRate: Int)
    fun stop()
    fun isPlaying(): Boolean
}

// Camera/Gallery
expect class PlatformImagePicker {
    suspend fun pickImage(): ByteArray?
    suspend fun takePhoto(): ByteArray?
}

// 3D Rendering
expect class PlatformRenderer {
    fun initialize(width: Int, height: Int)
    fun loadModel(assetPath: String)
    fun setBlendShape(name: String, weight: Float)
    fun playAnimation(name: String)
    fun render()
    fun destroy()
}

// SQLite Driver
expect class PlatformSqlDriverFactory {
    fun create(): SqlDriver
}

// Logging
expect fun platformLog(tag: String, message: String)

// Permissions
expect class PlatformPermissions {
    suspend fun requestMicrophonePermission(): Boolean
    suspend fun requestCameraPermission(): Boolean
}
```

### 2.2 Firebase — Due strade

| Opzione | Implementazione | Pro | Contro |
|---------|----------------|-----|--------|
| **A) GitLive SDK** | Sostituire Google Firebase SDK con `dev.gitlive:firebase-*` in commonMain | API unica, gia' nel version catalog (v2.4.0) | Dipendenza community, possibili bug |
| **B) expect/actual** | Interface in commonMain, Google SDK in androidMain, Apple SDK in iosMain | Controllo totale | 19 repo x piattaforma da scrivere |

**Raccomandazione**: **Opzione A (GitLive)** — e' gia' nel `libs.versions.toml`, riduce il lavoro del 70%.

Con GitLive, le 19 repository Firestore diventano **commonMain** senza modifiche strutturali:
```kotlin
// commonMain — funziona su Android, iOS, Desktop
import dev.gitlive.firebase.firestore.FirebaseFirestore

class FirestoreDiaryRepository(
    private val firestore: FirebaseFirestore,
    private val auth: AuthProvider
) : DiaryRepository {
    override suspend fun getAll(): List<Diary> {
        return firestore.collection("users/${auth.currentUserId}/diaries")
            .get().documents.map { it.data() }
    }
}
```

---

## Fase 0 — Preparazione (1 giorno)

### 0.1 Creare directory structure

```bash
# Per ogni modulo, creare le directory commonMain
mkdir -p core/util/src/commonMain/kotlin/com/lifo/util/platform
mkdir -p core/social-ui/src/commonMain/kotlin/com/lifo/socialui
mkdir -p data/mongo/src/commonMain/kotlin/com/lifo/mongo

# Per ogni feature module
for feature in auth avatar-creator chat composer feed history home humanoid \
  insight messaging notifications onboarding profile search settings \
  social-profile subscription thread-detail write; do
  mkdir -p features/$feature/src/commonMain/kotlin/com/lifo/$feature
done
```

### 0.2 Aggiungere GitLive Firebase al version catalog

```toml
# gradle/libs.versions.toml — GIA' PRESENTE, solo verificare
gitlive-firebase = "2.4.0"
```

### 0.3 Creare expect declarations platform

**File**: `core/util/src/commonMain/kotlin/com/lifo/util/platform/PlatformExpects.kt`

Creare tutte le `expect` class/fun elencate in sezione 2.1.

### 0.4 Creare actual Android per ogni expect

**File**: `core/util/src/androidMain/kotlin/com/lifo/util/platform/PlatformActuals.kt`

Wrappare le implementazioni Android esistenti.

### 0.5 Verificare build

```bash
./gradlew :core:util:compileCommonMainKotlinMetadata
./gradlew :core:util:compileDebugKotlinAndroid
```

---

## Fase 1 — Core Puri (2 giorni)

### 1.1 core/social-ui → commonMain

**18 file** — tutti Compose-only, zero import Android.

| File | Da | A | Note |
|------|-----|---|------|
| `AnimatedCount.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `LikeParticleBurst.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `ShimmerLoadingSkeleton.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `SocialAnimations.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `ClusteredAvatars.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `OverlappingAvatars.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `UserAvatar.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `CodeBlock.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `FullscreenImageViewer.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `LinkPreviewCard.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `MediaCarousel.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `MediaGrid.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `EngagementBar.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `ExpandableText.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `RichPostText.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `ThreadPostCard.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `ThreadLine.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `ThreadOptionsSheet.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |

**Dopo lo spostamento**: rimuovere `kotlin.srcDirs('src/main/java')` dal build.gradle.

**Verifica**: `./gradlew :core:social-ui:compileCommonMainKotlinMetadata`

### 1.2 core/ui — spostare file legacy

| File | Da | A | Note |
|------|-----|---|------|
| `MoodShapeDefinitions.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Verificare `androidx.graphics.shapes` KMP compat |
| `MoodShapeIndicator.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `MoodUiProvider.kt` | `src/main/java/` | `src/commonMain/kotlin/` | Compose puro |
| `Gallery.kt` | `src/main/java/` | `src/androidMain/kotlin/` | Usa Android APIs |
| `FirebaseImageHelper.kt` | `src/main/java/` | `src/androidMain/kotlin/` | Firebase Storage — resta androidMain |

**Verifica**: `./gradlew :core:ui:compileCommonMainKotlinMetadata`

---

## Fase 2 — Feature Semplici (3 giorni)

Moduli con pochi file, zero dipendenze Android nei ViewModel/Contract.

### Pattern per ogni modulo

Per ogni feature module, il lavoro e':
1. Creare `src/commonMain/kotlin/com/lifo/<feature>/`
2. Spostare `*Contract.kt` → commonMain
3. Spostare `*ViewModel.kt` → commonMain (verificare zero import `android.*`)
4. Spostare `*Screen.kt` → commonMain (se Compose-only)
5. Spostare `di/*KoinModule.kt` → commonMain (se non ha factory Android)
6. Lasciare in androidMain solo file con import Android irremovibili
7. Aggiornare build.gradle: rimuovere `kotlin.srcDirs('src/main/java')` se svuotato
8. Verificare: `./gradlew :<module>:compileCommonMainKotlinMetadata`

### 2.1 features/profile (5 file → 5 commonMain, 0 androidMain)

| File | Destinazione | Categoria |
|------|-------------|-----------|
| `ProfileContract.kt` | commonMain | PURE_KOTLIN |
| `ProfileViewModel.kt` | commonMain | PURE_KOTLIN |
| `ProfileDashboard.kt` | commonMain | COMPOSE_ONLY |
| `ProfileScreen.kt` | commonMain | COMPOSE_ONLY |
| `di/ProfileKoinModule.kt` | commonMain | PURE_KOTLIN |

### 2.2 features/insight (4-6 file → tutti commonMain)

| File | Destinazione | Categoria |
|------|-------------|-----------|
| `InsightContract.kt` | commonMain | PURE_KOTLIN |
| `InsightViewModel.kt` | commonMain | PURE_KOTLIN |
| `InsightScreen.kt` | commonMain | COMPOSE_ONLY |
| `InsightEntryPoint.kt` | commonMain | COMPOSE_ONLY |
| `di/InsightKoinModule.kt` | commonMain | PURE_KOTLIN |

### 2.3 features/onboarding (5 file → tutti commonMain)

| File | Destinazione | Categoria |
|------|-------------|-----------|
| `OnboardingContract.kt` | commonMain | PURE_KOTLIN |
| `OnboardingViewModel.kt` | commonMain | PURE_KOTLIN |
| `OnboardingScreen.kt` | commonMain | COMPOSE_ONLY |
| `di/OnboardingKoinModule.kt` | commonMain | PURE_KOTLIN |

### 2.4 features/search (5-7 file → tutti commonMain)

| File | Destinazione | Categoria |
|------|-------------|-----------|
| `SearchContract.kt` | commonMain | PURE_KOTLIN |
| `SearchViewModel.kt` | commonMain | PURE_KOTLIN |
| `SearchScreen.kt` | commonMain | COMPOSE_ONLY |
| `di/SearchKoinModule.kt` | commonMain | PURE_KOTLIN |

### 2.5 features/notifications (5-7 file → tutti commonMain)

| File | Destinazione | Categoria |
|------|-------------|-----------|
| `NotificationsContract.kt` | commonMain | PURE_KOTLIN |
| `NotificationsViewModel.kt` | commonMain | PURE_KOTLIN |
| `NotificationsScreen.kt` | commonMain | COMPOSE_ONLY |
| `di/NotificationsKoinModule.kt` | commonMain | PURE_KOTLIN |

### 2.6 features/subscription (5 file)

| File | Destinazione | Categoria | Note |
|------|-------------|-----------|------|
| `SubscriptionContract.kt` | commonMain | PURE_KOTLIN | |
| `SubscriptionViewModel.kt` | commonMain | PURE_KOTLIN | Se usa PlayBilling → creare expect |
| `PaywallScreen.kt` | commonMain | COMPOSE_ONLY | |
| `di/SubscriptionKoinModule.kt` | commonMain | PURE_KOTLIN | |

**Attenzione**: Se `SubscriptionViewModel` usa Google Play Billing direttamente, servira' un'interfaccia `PlatformBillingService` con expect/actual.

### 2.7 features/social-profile (5-7 file → tutti commonMain)

| File | Destinazione | Categoria |
|------|-------------|-----------|
| `SocialProfileContract.kt` | commonMain | PURE_KOTLIN |
| `SocialProfileViewModel.kt` | commonMain | PURE_KOTLIN |
| `SocialProfileScreen.kt` | commonMain | COMPOSE_ONLY |
| `di/SocialProfileKoinModule.kt` | commonMain | PURE_KOTLIN |

### 2.8 features/thread-detail (5 file → tutti commonMain)

| File | Destinazione | Categoria |
|------|-------------|-----------|
| `ThreadDetailContract.kt` | commonMain | PURE_KOTLIN |
| `ThreadDetailViewModel.kt` | commonMain | PURE_KOTLIN |
| `ThreadDetailScreen.kt` | commonMain | COMPOSE_ONLY |
| `di/ThreadDetailKoinModule.kt` | commonMain | PURE_KOTLIN |

**Verifica globale fine Fase 2**:
```bash
./gradlew assembleDebug
```

---

## Fase 3 — Feature Medie (3 giorni)

Moduli con qualche dipendenza Android da astrarre.

### 3.1 features/auth (7-9 file)

| File | Destinazione | Note |
|------|-------------|------|
| `AuthenticationContract.kt` | commonMain | PURE_KOTLIN |
| `AuthenticationViewModel.kt` | commonMain | Usa AuthProvider (gia' astratto) |
| `AuthenticationContent.kt` | commonMain | COMPOSE_ONLY |
| `AuthenticationScreen.kt` | commonMain | Rimuovere `@SuppressLint` se presente |
| `AuthEntryPoint.kt` | commonMain | COMPOSE_ONLY |
| `di/AuthKoinModule.kt` | commonMain | PURE_KOTLIN |
| `SignInWithGoogleUseCase.kt` | **androidMain** | Usa FirebaseAuth direttamente |
| `SignOutUseCase.kt` | commonMain | Usa AuthProvider interface |

**Azione**: Creare `expect` per Google Sign-In:
```kotlin
// commonMain
expect class PlatformGoogleSignIn {
    suspend fun signIn(): AuthResult?
}

// androidMain — wrappa Firebase Google Sign-In
// iosMain — wrappa Firebase iOS Google Sign-In
```

### 3.2 features/feed (5-7 file → tutti commonMain)

| File | Destinazione | Categoria |
|------|-------------|-----------|
| `FeedContract.kt` | commonMain | PURE_KOTLIN |
| `FeedViewModel.kt` | commonMain | PURE_KOTLIN |
| `FeedScreen.kt` | commonMain | COMPOSE_ONLY |
| `FeedEntryPoint.kt` | commonMain | COMPOSE_ONLY |
| `di/FeedKoinModule.kt` | commonMain | PURE_KOTLIN |

### 3.3 features/composer (5-7 file → quasi tutti commonMain)

| File | Destinazione | Note |
|------|-------------|------|
| `ComposerContract.kt` | commonMain | PURE_KOTLIN |
| `ComposerViewModel.kt` | commonMain | Verificare import Android |
| `ComposerScreen.kt` | commonMain | COMPOSE_ONLY |
| `ComposerEntryPoint.kt` | commonMain | COMPOSE_ONLY |
| `di/ComposerKoinModule.kt` | commonMain | PURE_KOTLIN |

### 3.4 features/messaging (8 file)

| File | Destinazione | Categoria |
|------|-------------|-----------|
| `MessagingContract.kt` | commonMain | PURE_KOTLIN |
| `MessagingViewModel.kt` | commonMain | PURE_KOTLIN |
| `MessagingScreen.kt` | commonMain | COMPOSE_ONLY |
| `ChatRoomScreen.kt` | commonMain | COMPOSE_ONLY |
| `ConversationListScreen.kt` | commonMain | COMPOSE_ONLY |
| `UserPickerScreen.kt` | commonMain | COMPOSE_ONLY |
| `MessagingEntryPoint.kt` | commonMain | COMPOSE_ONLY |
| `di/MessagingKoinModule.kt` | commonMain | PURE_KOTLIN |

### 3.5 features/history (8-10 file)

| File | Destinazione | Categoria |
|------|-------------|-----------|
| `HistoryContract.kt` | commonMain | PURE_KOTLIN |
| `HistoryViewModel.kt` | commonMain | PURE_KOTLIN |
| `HistoryScreen.kt` | commonMain | COMPOSE_ONLY |
| `ChatHistoryScreen.kt` | commonMain | COMPOSE_ONLY |
| `DiaryHistoryScreen.kt` | commonMain | COMPOSE_ONLY |
| `HistoryEntryPoint.kt` | commonMain | COMPOSE_ONLY |
| `di/HistoryKoinModule.kt` | commonMain | PURE_KOTLIN |

### 3.6 features/settings (10-13 file)

| File | Destinazione | Note |
|------|-------------|------|
| `SettingsContract.kt` | commonMain | PURE_KOTLIN |
| `SettingsViewModel.kt` | commonMain | PURE_KOTLIN |
| `SettingsScreen.kt` | commonMain | COMPOSE_ONLY |
| `PersonalInfoScreen.kt` | commonMain | COMPOSE_ONLY |
| `HealthInfoScreen.kt` | commonMain | COMPOSE_ONLY |
| `LifestyleScreen.kt` | commonMain | COMPOSE_ONLY |
| `GoalsScreen.kt` | commonMain | COMPOSE_ONLY |
| `SettingsEntryPoint.kt` | commonMain | COMPOSE_ONLY |
| `di/SettingsKoinModule.kt` | commonMain | PURE_KOTLIN |

### 3.7 features/avatar-creator (19 file)

| File | Destinazione | Note |
|------|-------------|------|
| `AvatarCreatorContract.kt` | commonMain | PURE_KOTLIN |
| `AvatarCreatorViewModel.kt` | commonMain | PURE_KOTLIN |
| `AvatarCreatorScreen.kt` | commonMain | COMPOSE_ONLY |
| `AvatarListScreen.kt` | commonMain | COMPOSE_ONLY |
| Tutti i componenti UI (15) | commonMain | COMPOSE_ONLY |
| `di/AvatarCreatorKoinModule.kt` | commonMain | PURE_KOTLIN |

**Verifica globale fine Fase 3**:
```bash
./gradlew assembleDebug
```

---

## Fase 4 — Feature Complesse (5 giorni)

### 4.1 features/write (21-23 file) — 2 refactor necessari

**Problema**: `WriteViewModel` usa direttamente `FirebaseStorage`, `Application`, `Uri`, `Intent`.

**Soluzione**:
1. Creare interface `ImageUploadService` in commonMain
2. Implementare `FirebaseImageUploadService` in androidMain
3. Iniettare via Koin

```kotlin
// commonMain
interface ImageUploadService {
    suspend fun uploadImage(imageData: ByteArray, path: String): String // returns URL
    suspend fun deleteImage(url: String)
}
```

| File | Destinazione | Note |
|------|-------------|------|
| `WriteContract.kt` | commonMain | PURE_KOTLIN |
| `WriteViewModel.kt` | commonMain | **DOPO refactor** — rimuovere Firebase/Context |
| `WriteScreen.kt` | commonMain | COMPOSE_ONLY |
| `WriteContent.kt` | commonMain | COMPOSE_ONLY |
| `WriteTopBar.kt` | commonMain | COMPOSE_ONLY |
| `DiaryDetailScreen.kt` | commonMain | COMPOSE_ONLY |
| `JournalHomeScreen.kt` | commonMain | COMPOSE_ONLY |
| `PsychologicalMetricsSheet.kt` | commonMain | COMPOSE_ONLY |
| `wizard/*.kt` (5 file) | commonMain | COMPOSE_ONLY |
| `WriteEntryPoint.kt` | commonMain | COMPOSE_ONLY |
| `di/WriteKoinModule.kt` | commonMain | PURE_KOTLIN |

### 4.2 features/home (49-57 file) — il piu' grande

**Problemi**:
- `DiaryHolder.kt` usa `android.widget.Toast` → sostituire con Effect nel MVI
- `MissingPermissionsApi.kt` usa Android permissions → expect/actual

| Categoria | File | Destinazione |
|-----------|------|-------------|
| ViewModel | `HomeViewModel.kt`, `HomeContract.kt` | commonMain |
| Models | `AchievementModels.kt`, `HomeUiModels.kt`, `InsightAggregations.kt` | commonMain |
| UseCases (6) | `Get*UseCase.kt` | commonMain |
| Screens | `HomeScreen.kt`, `HomeContent.kt`, `SnapshotScreen.kt`, `LoadingScreen.kt` | commonMain |
| Components (30+) | `components/**/*.kt` | commonMain |
| Utilities | `ColorUtils.kt`, `EmotionAwareColors.kt`, `EmotionShapes.kt` | commonMain |
| Platform | `MissingPermissionsApi.kt` | **androidMain** (expect/actual) |
| Platform | `DiaryHolder.kt` | commonMain (**dopo** rimuovere Toast) |
| Navigation | `HomeEntryPoint.kt` | commonMain |
| DI | `HomeKoinModule.kt` | commonMain |

### 4.3 features/chat (40 file) — il piu' complesso

**Architettura target**:
```
commonMain/                              androidMain/
├── ChatContract.kt                      ├── audio/
├── ChatViewModel.kt (refactored)        │   ├── AAAudioEngine.kt
├── LiveChatContract.kt                  │   ├── AndroidAudioRecorder.kt
├── LiveChatViewModel.kt (refactored)    │   ├── AndroidAudioPlayer.kt
├── domain/                              │   └── OboeNativeEngine.kt (NDK)
│   ├── LiveChatState.kt                 ├── camera/
│   ├── AudioSessionConfig.kt           │   └── GeminiLiveCameraManager.kt
│   ├── HeadphoneDetector.kt (expect)    ├── vad/
│   └── BargeinDetector.kt              │   └── SileroVadEngine.kt (ONNX Android)
├── websocket/                           ├── tts/
│   └── GeminiLiveWebSocketClient.kt     │   └── SherpaOnnxTtsEngine.kt
│       (Ktor WebSocket — gia' KMP!)     └── platform/
├── presentation/                            ├── HeadphoneDetector.android.kt
│   ├── ChatScreen.kt                       └── AudioPermissions.android.kt
│   ├── LiveScreen.kt
│   ├── ChatBubble.kt
│   ├── ChatInput.kt
│   └── FluidAudioIndicator.kt
└── di/ChatKoinModule.kt
```

**Refactor necessari per ChatViewModel e LiveChatViewModel**:

1. **Rimuovere `FirebaseAuth` diretto** → usare `AuthProvider` (gia' esiste)
2. **Rimuovere `android.content.Context`** → iniettare servizi via Koin
3. **Astrarre audio I/O** → `PlatformAudioRecorder` / `PlatformAudioPlayer`
4. **WebSocket**: `GeminiLiveWebSocketClient` usa OkHttp → migrare a **Ktor WebSocket** (KMP)

```kotlin
// commonMain — WebSocket KMP
import io.ktor.client.plugins.websocket.*

class GeminiLiveWebSocketClient(
    private val httpClient: HttpClient  // Ktor, iniettato via Koin
) {
    suspend fun connect(url: String): WebSocketSession { ... }
}
```

| File | Destinazione | Note |
|------|-------------|------|
| `ChatContract.kt` | commonMain | |
| `ChatViewModel.kt` | commonMain | **Refactor**: rimuovere FirebaseAuth, Context |
| `LiveChatContract.kt` | commonMain | |
| `LiveChatViewModel.kt` | commonMain | **Refactor**: rimuovere FirebaseAuth, Context |
| `LiveChatState.kt` | commonMain | PURE_KOTLIN |
| `GeminiLiveWebSocketClient.kt` | commonMain | **Refactor**: OkHttp → Ktor WebSocket |
| `GeminiLiveAudioManager.kt` | commonMain | **Refactor**: astrarre audio I/O |
| `AdaptiveBargeinDetector.kt` | commonMain | PURE_KOTLIN |
| `HeadphoneDetector.kt` | commonMain (expect) | expect/actual per platform |
| `ChatScreen.kt` | commonMain | COMPOSE_ONLY |
| `LiveScreen.kt` | commonMain | COMPOSE_ONLY |
| `ChatBubble.kt` | commonMain | COMPOSE_ONLY |
| `ChatInput.kt` | commonMain | COMPOSE_ONLY |
| `FluidAudioIndicator.kt` | commonMain | COMPOSE_ONLY |
| `GeminiLiquidVisualizer.kt` | commonMain | COMPOSE_ONLY |
| `di/ChatKoinModule.kt` | commonMain | |
| `AAAudioEngine.kt` | **androidMain** | Android AudioTrack/AudioRecord |
| `SileroVadEngine.kt` | **androidMain** | ONNX Runtime Android |
| `GeminiNativeVoiceSystem.kt` | **androidMain** | Android TTS |
| `SpeechToTextManager.kt` | **androidMain** | Android Speech API |
| `GeminiAudioPlayer.kt` | **androidMain** | AudioTrack specifico |
| `GeminiLiveCameraManager.kt` | **androidMain** | Camera2 API |
| `LiveCameraPreview.kt` | **androidMain** | AndroidView composable |
| `FullDuplexAudioSession.kt` | **androidMain** | AEC hardware |
| CMake/NDK code | **androidMain** | Oboe C++ engine |

### 4.4 features/humanoid (31-33 file) — 3D rendering

**Architettura target**:
```
commonMain/                              androidMain/
├── HumanoidContract.kt                  ├── rendering/
├── HumanoidViewModel.kt                │   ├── FilamentRenderer.kt
├── domain/                              │   ├── FilamentView.kt
│   ├── LipSyncController.kt            │   ├── VrmLoader.kt
│   ├── PhonemeConverter.kt              │   └── VrmaAnimationLoader.kt
│   ├── VisemeMapper.kt                  ├── animation/
│   ├── AnimationCoordinator.kt          │   └── (platform-specific animation bindings)
│   ├── BlinkController.kt              └── platform/
│   └── IdleAnimationController.kt           └── FilamentPlatformRenderer.kt
├── presentation/
│   └── HumanoidScreen.kt
└── di/HumanoidKoinModule.kt
```

| File | Destinazione | Note |
|------|-------------|------|
| `HumanoidContract.kt` | commonMain | PURE_KOTLIN |
| `HumanoidViewModel.kt` | commonMain | PURE_KOTLIN |
| `LipSyncController.kt` | commonMain | PURE_KOTLIN (logica) |
| `PhonemeConverter.kt` | commonMain | PURE_KOTLIN |
| `VisemeMapper.kt` | commonMain | PURE_KOTLIN |
| `AnimationCoordinator.kt` | commonMain | PURE_KOTLIN (logica, no Filament API) |
| `BlinkController.kt` | commonMain | PURE_KOTLIN |
| `IdleAnimationController.kt` | commonMain | PURE_KOTLIN |
| `IdlePoseController.kt` | commonMain | PURE_KOTLIN |
| `VrmBlendShapeController.kt` | commonMain/androidMain | Dipende — verificare se usa Filament API |
| `HumanoidScreen.kt` | commonMain | COMPOSE_ONLY |
| `di/HumanoidKoinModule.kt` | commonMain | |
| `FilamentRenderer.kt` | **androidMain** | Filament JNI |
| `FilamentView.kt` | **androidMain** | AndroidView(TextureView) |
| `VrmLoader.kt` | **androidMain** | Asset loading + Filament |
| `VrmaAnimationLoader.kt` | **androidMain** | GLTF parsing + Filament |
| `AvatarCreatorTemplates.kt` | commonMain | Se e' solo dati/config |

**Verifica globale fine Fase 4**:
```bash
./gradlew assembleDebug
```

---

## Fase 5 — Data Layer: Firebase → GitLive (3 giorni)

### 5.1 Aggiungere GitLive dependencies

```kotlin
// data/mongo/build.gradle
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation project(':core:util')
            implementation libs.gitlive.firebase.auth
            implementation libs.gitlive.firebase.firestore
            implementation libs.gitlive.firebase.storage
            // SQLDelight
            implementation libs.sqldelight.runtime
            implementation libs.sqldelight.coroutines
            implementation libs.kotlinx.serialization
            implementation libs.kotlinx.datetime
        }
        androidMain.dependencies {
            implementation libs.sqldelight.android.driver
            // Google Play Billing resta androidMain
            implementation libs.play.billing
        }
        iosMain.dependencies {
            implementation libs.sqldelight.native.driver
        }
        desktopMain.dependencies {
            implementation libs.sqldelight.sqlite.driver
        }
    }
}
```

### 5.2 Migrare repository (19 file)

Per ogni repository:
1. Sostituire `com.google.firebase.firestore.*` → `dev.gitlive.firebase.firestore.*`
2. Adattare API (molto simili, ma non identiche)
3. Spostare da `src/main/java/` → `src/commonMain/kotlin/`

**Mappatura API principale**:

| Google Firebase | GitLive KMP |
|----------------|-------------|
| `FirebaseFirestore.getInstance()` | `Firebase.firestore` |
| `collection("x").document("y").get()` | `collection("x").document("y").get()` (uguale!) |
| `document.toObject(Diary::class.java)` | `document.data<Diary>()` (kotlinx-serialization) |
| `Task<T>` | `suspend fun` (gia' coroutine-native) |
| `addSnapshotListener` | `.snapshots` (Flow) |

**File da migrare**:

| File | Complessita' |
|------|-------------|
| `FirestoreDiaryRepository.kt` | Media — mapping Diary |
| `FirestoreInsightRepository.kt` | Bassa |
| `FirestoreProfileRepository.kt` | Bassa |
| `FirestoreProfileSettingsRepository.kt` | Bassa |
| `FirestoreFeedRepository.kt` | Media |
| `FirestoreThreadRepository.kt` | Media |
| `FirestoreThreadHydrator.kt` | Alta — query complesse |
| `FirestoreSocialGraphRepository.kt` | Media |
| `FirestoreSocialMessagingRepository.kt` | Media |
| `FirestoreSearchRepository.kt` | Bassa |
| `FirestoreNotificationRepository.kt` | Bassa |
| `FirestoreWellbeingRepository.kt` | Media |
| `FirebaseAvatarRepository.kt` | Media — Storage + Firestore |
| `FirebaseFeatureFlagRepository.kt` | Bassa — RemoteConfig |
| `FirebaseMediaUploadRepository.kt` | Media — Storage |
| `FirebasePresenceRepository.kt` | Bassa |
| `ChatRepositoryImpl.kt` | Media |
| `CloudFunctionsContentModerationRepository.kt` | Media — Cloud Functions |
| `UnifiedContentRepositoryImpl.kt` | Alta — aggregazione |

### 5.3 File che RESTANO androidMain

| File | Motivo |
|------|--------|
| `PlayBillingSubscriptionRepository.kt` | Google Play Billing — no KMP equivalent |
| `FirebaseAnalyticsTracker.kt` | GitLive analytics disponibile, ma valutare |
| `MongoKoinModule.kt` | Parzialmente — split in commonMain (repos) + androidMain (drivers) |

### 5.4 SQLDelight Driver — expect/actual

```kotlin
// commonMain
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

// androidMain
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(CalmifyDatabase.Schema, context, "calmify_db")
}

// iosMain
actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(CalmifyDatabase.Schema, "calmify_db")
}

// desktopMain
actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY) // o file-based
}
```

---

## Fase 6 — iOS/Desktop actual (3 giorni)

Questa fase e' necessaria solo quando si vuole compilare per iOS/Desktop.

### 6.1 iosMain actual implementations

| Expect | iOS Actual | Libreria |
|--------|-----------|----------|
| `PlatformCalmifyTheme` | Static MaterialTheme | **Gia' esiste** |
| `PlatformViewModel` | Empty class + manual scope | **Gia' esiste** |
| `AuthProvider` → `FirebaseAuthProvider` | Firebase iOS SDK via GitLive | GitLive |
| `PlatformAudioRecorder` | AVAudioEngine + AVAudioSession | Apple frameworks |
| `PlatformAudioPlayer` | AVAudioPlayer / AVAudioEngine | Apple frameworks |
| `PlatformImagePicker` | PHPickerViewController | PhotosUI |
| `PlatformRenderer` | SceneKit / Filament C++ | Da valutare |
| `DriverFactory` | NativeSqliteDriver | SQLDelight |
| `PlatformPermissions` | AVCaptureDevice.requestAccess | Apple frameworks |
| `PlatformGoogleSignIn` | Google Sign-In iOS SDK | GoogleSignIn-iOS |
| `platformLog` | `NSLog` / `print` | Foundation |

### 6.2 desktopMain actual implementations

| Expect | Desktop Actual | Libreria |
|--------|---------------|----------|
| `PlatformCalmifyTheme` | Static MaterialTheme | **Gia' esiste** |
| `PlatformViewModel` | Plain class | **Gia' esiste** |
| `PlatformAudioRecorder` | `javax.sound.sampled.TargetDataLine` | JDK |
| `PlatformAudioPlayer` | `javax.sound.sampled.SourceDataLine` | JDK |
| `PlatformImagePicker` | `JFileChooser` / Compose `FileDialog` | JDK/Compose |
| `PlatformRenderer` | Filament JVM / Scene3D (se disponibile) | Filament |
| `DriverFactory` | `JdbcSqliteDriver` | SQLDelight |
| `platformLog` | `println` / SLF4J | JDK |

### 6.3 iosApp module

```
iosApp/
├── iosApp/
│   ├── iOSApp.swift          — Entry point
│   ├── ContentView.swift     — ComposeUIViewController wrapper
│   ├── GoogleService-Info.plist — Firebase iOS config
│   └── Info.plist
├── iosApp.xcodeproj/
└── Podfile                    — Firebase iOS pods (se non usi SPM)
```

---

## Riepilogo File per Modulo

### Stato Finale Atteso

| Modulo | commonMain | androidMain | Totale | % KMP |
|--------|-----------|------------|--------|-------|
| **core/util** | 45 | 2 | 47 | 96% |
| **core/ui** | 15 | 2 | 17 | 88% |
| **core/social-ui** | 18 | 0 | 18 | 100% |
| **data/mongo** | 22 | 4 | 26 | 85% |
| **features/auth** | 7 | 1 | 8 | 88% |
| **features/avatar-creator** | 19 | 0 | 19 | 100% |
| **features/chat** | 20 | 15 | 35 | 57% |
| **features/composer** | 5 | 0 | 5 | 100% |
| **features/feed** | 5 | 0 | 5 | 100% |
| **features/history** | 8 | 0 | 8 | 100% |
| **features/home** | 50 | 2 | 52 | 96% |
| **features/humanoid** | 12 | 15 | 27 | 44% |
| **features/insight** | 4 | 0 | 4 | 100% |
| **features/messaging** | 8 | 0 | 8 | 100% |
| **features/notifications** | 5 | 0 | 5 | 100% |
| **features/onboarding** | 4 | 0 | 4 | 100% |
| **features/profile** | 5 | 0 | 5 | 100% |
| **features/search** | 5 | 0 | 5 | 100% |
| **features/settings** | 10 | 0 | 10 | 100% |
| **features/social-profile** | 5 | 0 | 5 | 100% |
| **features/subscription** | 4 | 1 | 5 | 80% |
| **features/thread-detail** | 5 | 0 | 5 | 100% |
| **features/write** | 18 | 1 | 19 | 95% |
| **TOTALE** | **~299** | **~43** | **~342** | **~87%** |

**Da 15% a 87% commonMain** — con solo ~43 file platform-specific.

---

## Rischi e Mitigazioni

### R1: GitLive API non 100% compatibile con Google Firebase
- **Rischio**: Alcune query complesse (compound queries, transactions) potrebbero non essere supportate
- **Mitigazione**: Testare prima le repository piu' complesse (`ThreadHydrator`, `UnifiedContent`). Fallback: mantenere quelle in androidMain con expect/actual

### R2: Compose Multiplatform differenze rendering
- **Rischio**: Alcuni Composable potrebbero renderizzare diversamente su iOS/Desktop
- **Mitigazione**: Testare ogni screen dopo lo spostamento. I componenti Material3 sono ben supportati

### R3: ONNX Runtime (Silero VAD) su iOS
- **Rischio**: ONNX Runtime iOS ha API diverse
- **Mitigazione**: Creare `expect class VadEngine` con actual per piattaforma. Su iOS usare CoreML come alternativa

### R4: Filament su iOS
- **Rischio**: Filament iOS richiede Metal backend, configurazione diversa
- **Mitigazione**: Mantenere tutto il rendering in androidMain/iosMain. Alternativa iOS: SceneKit (nativo Apple, meno lavoro)

### R5: Build time increase
- **Rischio**: KMP compila per piu' target, build piu' lento
- **Mitigazione**: Usare `kotlin.native.ignoreDisabledTargets=true` (gia' attivo) — compila solo il target richiesto

### R6: Google Play Billing non ha KMP equivalent
- **Rischio**: Subscription flow diverso per piattaforma
- **Mitigazione**: Interface `BillingService` in commonMain, `PlayBillingService` in androidMain, `StoreKitService` in iosMain

---

## Checklist di Validazione per Ogni Fase

```
Per ogni modulo migrato:
[ ] File spostati fisicamente in src/commonMain/kotlin/
[ ] Zero import android.* / java.* in commonMain
[ ] ./gradlew :<module>:compileCommonMainKotlinMetadata → OK
[ ] ./gradlew :<module>:compileDebugKotlinAndroid → OK
[ ] ./gradlew assembleDebug → OK (app compila)
[ ] kotlin.srcDirs('src/main/java') rimosso se src/main/java e' vuoto
[ ] Koin module registra correttamente i nuovi path
[ ] Test unitari passano (se esistono)
```

---

## Timeline Riassuntiva

| Fase | Durata | File Spostati | Milestone |
|------|--------|--------------|-----------|
| **0 — Preparazione** | 1 giorno | 0 | expect/actual infrastruttura |
| **1 — Core Puri** | 2 giorni | ~23 | core/social-ui + core/ui legacy |
| **2 — Feature Semplici** | 3 giorni | ~45 | 8 moduli feature completi |
| **3 — Feature Medie** | 3 giorni | ~70 | 7 moduli feature + auth refactor |
| **4 — Feature Complesse** | 5 giorni | ~100 | home + chat + humanoid + write |
| **5 — Data Layer** | 3 giorni | ~22 | Firebase → GitLive, SQLDelight drivers |
| **6 — iOS/Desktop** | 3 giorni | ~15 (actual) | iosApp compilabile |
| **TOTALE** | **~20 giorni** | **~275 file** | **87% commonMain** |
