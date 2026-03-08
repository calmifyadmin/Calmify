# KMP Migration — Piano di Refactoring Completo

> **Autore:** Jarvis AI Assistant
> **Data:** 2026-02-27
> **Branch:** `migration/firebase-2025`
> **Strategia:** UN MODULO ALLA VOLTA — ogni fase e' autocontenuta e compilabile

---

## Indice

1. [Panoramica Architetturale Attuale](#1-panoramica-architetturale-attuale)
2. [Obiettivo del Refactoring](#2-obiettivo-del-refactoring)
3. [Principi Guida](#3-principi-guida)
4. [Mappa delle Dipendenze Cross-Module](#4-mappa-delle-dipendenze-cross-module)
5. [Ordine di Migrazione](#5-ordine-di-migrazione)
6. [FASE 1 — core/util (Pulizia + Separazione)](#fase-1--coreutil)
7. [FASE 2 — core/ui (Pulizia + Assorbimento da util)](#fase-2--coreui)
8. [FASE 3 — data/mongo (Astrazione Repository)](#fase-3--datamongo)
9. [FASE 4 — features/auth](#fase-4--featuresauth)
10. [FASE 5 — features/insight](#fase-5--featuresinsight)
11. [FASE 6 — features/history](#fase-6--featureshistory)
12. [FASE 7 — features/settings](#fase-7--featuressettings)
13. [FASE 8 — features/onboarding](#fase-8--featuresonboarding)
14. [FASE 9 — features/home](#fase-9--featureshome)
15. [FASE 10 — features/write](#fase-10--featureswrite)
16. [FASE 11 — features/profile](#fase-11--featuresprofile)
17. [FASE 12 — features/chat](#fase-12--featureschat)
18. [FASE 13 — features/humanoid](#fase-13--featureshumanoid)
19. [FASE 14 — app (Modulo Principale)](#fase-14--app)
20. [FASE 15 — Build System & Gradle](#fase-15--build-system--gradle)
21. [Riepilogo Dipendenze KMP](#riepilogo-dipendenze-kmp)
22. [Risk Assessment](#risk-assessment)

---

## 1. Panoramica Architetturale Attuale

```
CalmifyAppAndroid/
├── app/                          # Entry point + Avatar integration + Navigation
│   ├── com.lifo.app/             # CalmifyApp, ProfileScreen, Avatar adapters
│   ├── com.lifo.calmifyapp/      # MainActivity, Application, FCM, DI, NavGraph
│   └── com.lifo.navigation/      # NavigationState
├── core/
│   ├── ui/                       # Theme, Components (AlertDialog, Stepper, GoogleButton)
│   └── util/                     # MISTO: Models, UI (!), Audio DSP, Connectivity, Speech
├── data/
│   └── mongo/                    # Room DB + Firestore repos + DI + Entities
├── features/
│   ├── auth/                     # Firebase Auth + Google Sign-In
│   ├── chat/                     # Gemini Live + WebSocket + Audio + VAD + Camera
│   ├── history/                  # Diary/Chat history display
│   ├── home/                     # Dashboard + Use cases + Charts + Feed
│   ├── humanoid/                 # Filament 3D + VRM + AR + Lip-sync
│   ├── insight/                  # AI diary insights
│   ├── onboarding/               # Multi-step wizard
│   ├── profile/                  # Psychological profile + Vico charts
│   ├── settings/                 # User settings CRUD
│   └── write/                    # Diary writing + Gallery + Wizard
└── buildSrc/                     # ProjectConfig
```

### Problemi Attuali (Pre-Refactoring)

| Problema | Dove | Impatto |
|----------|------|---------|
| **UI code in core/util** | AnimatedShimmer, DiaryHolder, Gallery, PermissionDialog, LocalBottomAppBarHeight | Blocca KMP: util non puo' diventare commonMain |
| **Compose Color in domain models** | Mood.kt, ChatEmotion.kt (core/util/model/) | Models non condivisibili cross-platform |
| **Android R.drawable in enum** | Mood.kt referenzia drawable resources | Impossibile in commonMain |
| **Dipendenza circolare** | core/util -> core/ui (Mood.kt usa theme colors) | Blocca separazione |
| **Firebase diretto in composables** | CalmifyApp.kt, ProfileScreen.kt | Business logic mescolata con UI |
| **android.util.Log ovunque** | 15+ file tra repositories e adapters | Richiede wrapper KMP |
| **Hilt DI non KMP** | Tutti i moduli usano Hilt | Da sostituire o astrarre |
| **java.time APIs** | Repositories, models, ViewModels | Serve kotlinx-datetime |
| **Room DB Android-only** | data/mongo/database/ | Serve SQLDelight o expect/actual |
| **3 package diversi in app/** | com.lifo.app, com.lifo.calmifyapp, com.lifo.navigation | Confusione, da unificare |
| **AR sperimentale da rimuovere** | 11 file AR in features/humanoid + riferimenti in app/ | Codice morto, dipendenza ARCore inutile |

---

## 2. Obiettivo del Refactoring

**Questo refactoring NON converte a KMP.** Prepara il codebase in modo che la conversione KMP successiva sia fluida, organizzando:

1. **Separazione netta**: UI (Compose) | Domain (pure Kotlin) | Platform (Android)
2. **Models puri**: Nessun import Compose/Android nei domain models
3. **Interfaces ovunque**: Repository, Logger, Auth dietro interfacce
4. **Eliminazione dipendenze circolari**: util non dipende da ui
5. **Codice nel modulo giusto**: UI in ui, data in data, business logic in domain

---

## 3. Principi Guida

```
UN MODULO ALLA VOLTA
    |
    v
Dopo ogni fase il progetto DEVE compilare e funzionare
    |
    v
Commit atomico per ogni fase completata
    |
    v
Test di regressione (build + run) dopo ogni fase
```

### Regole:
- **Mai** spostare piu' di un modulo per fase
- **Mai** rompere le dipendenze senza fornire il sostituto
- **Sempre** compilare dopo ogni operazione di spostamento
- **Deprecare prima, rimuovere dopo** (se necessario per backward-compat temporaneo)
- Per ogni file spostato: aggiornare TUTTI gli import nei moduli dipendenti

---

## 4. Mappa delle Dipendenze Cross-Module

```
                    ┌─────────┐
                    │   app   │ ← Entry point
                    └────┬────┘
                         │ dipende da TUTTO
        ┌────────────────┼────────────────────┐
        │                │                    │
   ┌────v────┐    ┌──────v──────┐    ┌───────v───────┐
   │ core/ui │    │ core/util   │    │  data/mongo   │
   └────┬────┘    └──────┬──────┘    └───────┬───────┘
        │                │                    │
        │         ┌──────┘                    │
        │         │   (util -> ui !!!)        │
        └─────────┘                           │
                                              │
        ┌─── features/* ──────────────────────┘
        │    (tutti dipendono da core/ui, core/util, data/mongo)
```

### Dipendenze Problematiche:
- `core/util` -> `core/ui` (Mood.kt usa theme colors) **DA ELIMINARE**
- `core/util` contiene composables **DA SPOSTARE in core/ui**

---

## 5. Ordine di Migrazione

L'ordine e' bottom-up, partendo dai moduli con meno dipendenze:

| # | Modulo | Difficolta' | Motivazione |
|---|--------|-------------|-------------|
| 1 | **core/util** | ALTA | Fondazione — tutti dipendono da questo |
| 2 | **core/ui** | MEDIA | Assorbe UI da util, taglia dipendenza circolare |
| 3 | **data/mongo** | ALTA | Astrae repositories dietro interfacce pure |
| 4 | **features/auth** | BASSA | Modulo semplice, pochi file |
| 5 | **features/insight** | BASSA | Modulo semplice, pochi file |
| 6 | **features/history** | BASSA | Modulo semplice |
| 7 | **features/settings** | BASSA | Form CRUD basico |
| 8 | **features/onboarding** | MEDIA | Wizard multi-step |
| 9 | **features/home** | MEDIA-ALTA | Molti use cases e componenti |
| 10 | **features/write** | MEDIA | Gallery + Firebase Storage |
| 11 | **features/profile** | MEDIA | Vico charts |
| 12 | **features/chat** | MOLTO ALTA | Audio pipeline + WebSocket + VAD |
| 13 | **features/humanoid** | ALTA | Rimuovere AR + separare domain da Filament rendering (Filament e' cross-platform) |
| 14 | **app** | ALTA | Unifica packages, estrae business logic |
| 15 | **Build System** | MEDIA | Prepara version catalog per KMP |

---

## FASE 1 — core/util

**Obiettivo:** Rendere core/util un modulo di PURO Kotlin (domain models + utilities) senza dipendenze Compose/Android UI.

### Stato Attuale

| File | Tipo | Problema |
|------|------|----------|
| `model/Mood.kt` | Domain model | Import Compose Color + R.drawable + core/ui theme |
| `model/ChatEmotion.kt` | Domain model | Import Compose Color |
| `model/Diary.kt` | Domain model | OK (Firebase annotations accettabili) |
| `model/DiaryInsight.kt` | Domain model | OK |
| `model/HomeContentItem.kt` | Domain model | OK |
| `model/ProfileSettings.kt` | Domain model | OK |
| `model/PsychologicalProfile.kt` | Domain model | OK |
| `model/RequestState.kt` | Domain model | OK |
| `model/WellbeingSnapshot.kt` | Domain model | OK |
| `AnimatedShimmer.kt` | **UI** | SPOSTARE -> core/ui |
| `DiaryHolder.kt` | **UI + Firebase** | SPOSTARE -> core/ui (UI) + estrarre fetch logic |
| `Gallery.kt` | **UI** | SPOSTARE -> core/ui |
| `PermissionDialog.kt` | **UI + Domain** | SPLIT: interfacce restano, composable -> core/ui |
| `LocalBottomAppBarHeight.kt` | **UI** | SPOSTARE -> core/ui |
| `audio/AudioVisemeAnalyzer.kt` | Pure Kotlin | OK — pura math/DSP |
| `connectivity/ConnectivityObserver.kt` | Interface | OK |
| `connectivity/NetworkConnectivityObserver.kt` | Android impl | OK per ora (implementation) |
| `speech/SynchronizedSpeechBridge.kt` | Interface | OK — pure abstractions |
| `Constants.kt` | Constants | OK (rimuovere API key hardcoded!) |
| `Screen.kt` | Navigation | OK |
| `UtilFunctions.kt` | Mixed | SPLIT: Firebase fetch -> data layer, conversioni restano |

### Azioni (in ordine)

#### 1.1 Spostare composables da util -> core/ui

**File da spostare:**
```
core/util/.../AnimatedShimmer.kt    -> core/ui/.../components/AnimatedShimmer.kt
core/util/.../DiaryHolder.kt        -> core/ui/.../components/DiaryHolder.kt
core/util/.../Gallery.kt            -> core/ui/.../components/Gallery.kt
core/util/.../LocalBottomAppBarHeight.kt -> core/ui/.../LocalBottomAppBarHeight.kt
```

**Per PermissionDialog.kt — SPLIT:**
```
core/util/.../PermissionDialog.kt:
  - PermissionTextProvider (interface)           -> RESTA in core/util
  - CameraPermissionTextProvider (impl)          -> RESTA in core/util
  - RecordAudioPermissionTextProvider (impl)     -> RESTA in core/util
  - PhoneCallPermissionTextProvider (impl)       -> RESTA in core/util
  - PermissionDialog (@Composable)               -> SPOSTARE a core/ui
```

**Dopo lo spostamento:** Aggiornare import in TUTTI i moduli che usavano questi file da `com.lifo.util.*` a `com.lifo.ui.*`.

Moduli da aggiornare: app, features/home, features/write, features/chat, features/history.

#### 1.2 Rimuovere Compose da Mood.kt

**Prima:**
```kotlin
enum class Mood(
    val icon: Int,              // R.drawable.* (Android)
    val contentColor: Color,    // Compose Color
    val containerColor: Color   // Compose Color
) {
    Happy(R.drawable.happy, HappyColor, HappyContainer),
    // ...
}
```

**Dopo:**
```kotlin
// core/util - PURO Kotlin
enum class Mood(
    val emoji: String,
    val displayName: String
) {
    Happy("😊", "Happy"),
    Sad("😢", "Sad"),
    // ... tutti i valori
}

// NUOVO: core/ui - Compose-specific
object MoodUiProvider {
    fun getIcon(mood: Mood): Int = when (mood) {
        Mood.Happy -> R.drawable.happy
        // ...
    }

    @Composable
    fun getContentColor(mood: Mood): Color = when (mood) {
        Mood.Happy -> HappyColor
        // ...
    }

    @Composable
    fun getContainerColor(mood: Mood): Color = when (mood) {
        Mood.Happy -> HappyContainer
        // ...
    }
}
```

**Moduli da aggiornare:** Tutti quelli che usano `mood.icon`, `mood.contentColor`, `mood.containerColor` devono passare a `MoodUiProvider.getIcon(mood)` etc.

#### 1.3 Rimuovere Compose da ChatEmotion.kt

**Prima:**
```kotlin
enum class ChatEmotion(
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color
) { ... }
```

**Dopo:**
```kotlin
// core/util - PURO Kotlin
enum class ChatEmotion {
    Calm, Happy, Sad, Anxious, Neutral
}

// NUOVO: core/ui
object ChatEmotionUiProvider {
    fun getColors(emotion: ChatEmotion): Triple<Color, Color, Color> = when (emotion) {
        ChatEmotion.Calm -> Triple(CalmPrimary, CalmSecondary, CalmTertiary)
        // ...
    }
}
```

#### 1.4 Estrarre Firebase fetch da UtilFunctions.kt

**Spostare** `fetchImagesFromFirebase()` da core/util a data/mongo (o core/ui/components se usato solo da DiaryHolder).

**Mantenere** in core/util solo le conversioni pure:
- `Timestamp.toInstant()`
- `Instant.toTimestamp()`
- `Date.toInstant()`
- `Instant.toDate()`

#### 1.5 Rimuovere API key hardcoded da Constants.kt

Spostare `GEMINI_API_KEY` nel BuildConfig o in un file di configurazione sicuro. Non deve essere in codice sorgente.

#### 1.6 Rimuovere dipendenza core/util -> core/ui

Dopo i passi 1.1-1.3, core/util NON deve piu' dipendere da core/ui nel build.gradle. Verificare ed eliminare `implementation(project(":core:ui"))` da `core/util/build.gradle`.

### Risultato Fase 1

```
core/util/ (DOPO)
├── audio/AudioVisemeAnalyzer.kt          # Pure math/DSP
├── connectivity/ConnectivityObserver.kt  # Interface
├── connectivity/NetworkConnectivityObserver.kt  # Android impl
├── model/
│   ├── ChatEmotion.kt                   # Pure enum (NO Color)
│   ├── Diary.kt                         # Data model
│   ├── DiaryInsight.kt                  # Data model
│   ├── HomeContentItem.kt               # Sealed class
│   ├── Mood.kt                          # Pure enum (NO drawable, NO Color)
│   ├── ProfileSettings.kt               # Data model
│   ├── PsychologicalProfile.kt          # Data model
│   ├── RequestState.kt                  # Generic sealed class
│   └── WellbeingSnapshot.kt             # Data model
├── speech/SynchronizedSpeechBridge.kt    # Interfaces
├── Constants.kt                          # Pure strings (NO API keys)
├── DiaryHolder.kt                        # RIMOSSO -> spostato in core/ui
├── Gallery.kt                            # RIMOSSO -> spostato in core/ui
├── PermissionDialog.kt                   # Solo interfacce (PermissionTextProvider)
├── Screen.kt                             # Navigation routes
└── UtilFunctions.kt                      # Solo conversioni pure
```

**Dipendenze core/util DOPO:** Solo Kotlin stdlib, kotlinx.coroutines, Firebase annotations (opzionale).

### Verifica
```bash
./gradlew :core:util:build
./gradlew assembleDebug  # Full app build
```

---

## FASE 2 — core/ui

**Obiettivo:** Assorbire tutti i composables spostati da util, creare UI providers per Mood e ChatEmotion, preparare per Compose Multiplatform.

### Azioni

#### 2.1 Aggiungere file ricevuti da core/util

Assicurarsi che questi file siano stati spostati con successo:
- `components/AnimatedShimmer.kt`
- `components/DiaryHolder.kt`
- `components/Gallery.kt`
- `components/PermissionDialog.kt` (solo il @Composable)
- `LocalBottomAppBarHeight.kt`

#### 2.2 Creare MoodUiProvider.kt

```
core/ui/src/main/java/com/lifo/ui/providers/MoodUiProvider.kt
```
Contiene la mappatura Mood -> drawable resource ID, Mood -> Compose Color.

#### 2.3 Creare ChatEmotionUiProvider.kt

```
core/ui/src/main/java/com/lifo/ui/providers/ChatEmotionUiProvider.kt
```
Contiene la mappatura ChatEmotion -> Color triple.

#### 2.4 Refactor GalleryState.kt

Sostituire `android.net.Uri` con `String` per i path delle immagini (o creare typealias):
```kotlin
// Prima
val image: Uri
// Dopo
val imagePath: String  // URI come stringa
```

#### 2.5 Refactor ErrorBoundary.kt

Sostituire `android.util.Log` con un logger astratto:
```kotlin
// core/util (NUOVO file)
interface AppLogger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String)
}

// core/ui o app (Android impl)
object AndroidLogger : AppLogger {
    override fun d(tag: String, message: String) = Log.d(tag, message)
    override fun e(tag: String, message: String, throwable: Throwable?) = Log.e(tag, message, throwable)
    override fun w(tag: String, message: String) = Log.w(tag, message)
}
```

#### 2.6 Refactor Theme.kt

Isolare il codice Android-specifico (window styling, status bar) in funzioni separate:
```kotlin
// Parte condivisibile
@Composable
fun CalmifyThemeColors(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme { ... }

// Parte Android-only
@Composable
fun CalmifyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = CalmifyThemeColors(darkTheme, dynamicColor)
    applyAndroidWindowStyling(colorScheme, darkTheme) // Android-only helper
    MaterialTheme(colorScheme, typography, content)
}
```

#### 2.7 Refactor GoogleButton.kt

Accettare `Painter` o `@Composable` come parametro invece di `Int` resource ID:
```kotlin
// Prima
fun GoogleButton(icon: Int = R.drawable.google_logo_ic, ...)

// Dopo
fun GoogleButton(
    icon: @Composable (() -> Unit)? = { Image(painterResource(R.drawable.google_logo_ic), ...) },
    ...
)
```

### Risultato Fase 2

```
core/ui/ (DOPO)
├── components/
│   ├── AlertDialog.kt
│   ├── AnimatedShimmer.kt      # DA util
│   ├── ContainedLoadingIndicator.kt
│   ├── DiaryHolder.kt          # DA util
│   ├── Gallery.kt              # DA util
│   ├── GoogleButton.kt         # Refactored (no R.drawable diretto)
│   ├── PermissionDialog.kt     # Solo @Composable DA util
│   └── stepper/ExpressiveStepper.kt
├── providers/
│   ├── MoodUiProvider.kt       # NUOVO
│   └── ChatEmotionUiProvider.kt # NUOVO
├── theme/
│   ├── Color.kt
│   ├── Elevation.kt
│   ├── Theme.kt               # Refactored (separata parte Android)
│   └── Type.kt
├── ErrorBoundary.kt           # Refactored (AppLogger)
├── GalleryState.kt            # Refactored (String vs Uri)
└── LocalBottomAppBarHeight.kt  # DA util
```

### Verifica
```bash
./gradlew :core:ui:build
./gradlew assembleDebug
```

---

## FASE 3 — data/mongo

**Obiettivo:** Separare interfacce (pure Kotlin) da implementazioni (Android/Firebase), creare abstraction layer per database e remote storage.

### Stato Attuale

| Categoria | File | KMP Ready |
|-----------|------|-----------|
| **Interfaces** | ChatRepository, MongoRepository, InsightRepository, ProfileRepository, ProfileSettingsRepository, WellbeingRepository, UnifiedContentRepository | SI |
| **Domain models** | ChatModels.kt (ChatSession, ChatMessage), HomeContentItemMappers.kt | SI |
| **Room entities** | ChatMessageEntity, ChatSessionEntity, ImageToDelete, ImageToUpload | NO (Room annotations) |
| **Room DAOs** | ChatMessageDao, ChatSessionDao, ImageToDeleteDao, ImageToUploadDao | NO (Room-only) |
| **Room DB** | AppDatabase, Converters, StringListConverter | NO (Room-only) |
| **Firestore impls** | 5 FirestoreXxxRepository | NO (Firebase SDK) |
| **DI** | FirestoreModule, MongoDataModule, RepositoryModule, MongoDatabaseProvider | NO (Hilt + Android) |

### Azioni

#### 3.1 Creare AppLogger in core/util

Se non gia' creato in Fase 2, aggiungere l'interfaccia `AppLogger` in core/util e l'implementazione `AndroidLogger` in core/ui o app.

#### 3.2 Sostituire android.util.Log nei repositories

In tutti i file Firestore*Repository.kt e ChatRepositoryImpl.kt:
```kotlin
// Prima
import android.util.Log
Log.e(TAG, "Error", e)

// Dopo
import com.lifo.util.AppLogger
// Iniettato via costruttore
logger.e(TAG, "Error", e)
```

#### 3.3 Sostituire java.time con kotlinx-datetime (OPZIONALE pre-KMP)

Per ora, annotare i file che usano `java.time` con un commento `// TODO KMP: replace with kotlinx-datetime`. La sostituzione effettiva avverra' durante la conversione KMP.

File interessati:
- `ChatModels.kt` (Instant, UUID)
- `Converters.kt` (Instant)
- `FirestoreDiaryRepository.kt` (LocalDate, ZonedDateTime)
- `FirestoreProfileSettingsRepository.kt` (Timestamp)
- `HomeContentItemMappers.kt` (Instant)

#### 3.4 Separare directory: domain/ vs platform/

Riorganizzare la struttura interna di data/mongo:

```
data/mongo/src/main/java/com/lifo/mongo/
├── domain/                              # PURE KOTLIN (future commonMain)
│   ├── model/
│   │   └── ChatModels.kt               # SPOSTATO da repository/
│   ├── repository/                      # Interfaces
│   │   ├── ChatRepository.kt           # SPOSTATO
│   │   ├── MongoRepository.kt          # SPOSTATO
│   │   ├── InsightRepository.kt        # SPOSTATO
│   │   ├── ProfileRepository.kt        # SPOSTATO
│   │   ├── ProfileSettingsRepository.kt # SPOSTATO
│   │   ├── WellbeingRepository.kt      # SPOSTATO
│   │   └── UnifiedContentRepository.kt # SPOSTATO
│   └── mapper/
│       └── HomeContentItemMappers.kt   # SPOSTATO
│
├── platform/                            # ANDROID-SPECIFIC (future androidMain)
│   ├── database/                        # Room (invariato)
│   │   ├── dao/
│   │   ├── entity/
│   │   ├── AppDatabase.kt
│   │   ├── Converters.kt
│   │   └── StringListConverter.kt
│   ├── repository/                      # Firestore implementations
│   │   ├── ChatRepositoryImpl.kt
│   │   ├── FirestoreDiaryRepository.kt
│   │   ├── FirestoreInsightRepository.kt
│   │   ├── FirestoreProfileRepository.kt
│   │   ├── FirestoreProfileSettingsRepository.kt
│   │   ├── FirestoreWellbeingRepository.kt
│   │   └── UnifiedContentRepositoryImpl.kt
│   └── di/                              # Hilt modules
│       ├── FirestoreModule.kt
│       ├── MongoDatabaseProvider.kt
│       ├── MongoDataModule.kt
│       └── RepositoryModule.kt
```

#### 3.5 Aggiornare imports

Tutti i moduli che importano da `com.lifo.mongo.repository.ChatRepository` devono aggiornare a `com.lifo.mongo.domain.repository.ChatRepository` (e simili).

**Suggerimento:** Usare "find and replace" su tutto il progetto per ogni package spostato.

### Risultato Fase 3

- Interfacce pure in `domain/` — pronte per `commonMain`
- Implementazioni Android in `platform/` — restano in `androidMain`
- Logger astratto usato ovunque
- Nessun `android.util.Log` diretto nei repositories

### Verifica
```bash
./gradlew :data:mongo:build
./gradlew assembleDebug
```

---

## FASE 4 — features/auth

**Obiettivo:** Separare logica di autenticazione da UI Compose, preparare per auth cross-platform.

### Stato Attuale
- `AuthenticationViewModel.kt` — Gestisce stato login (pochi metodi)
- `AuthenticationScreen.kt` + `AuthenticationContent.kt` — UI Compose
- `AuthNavigation.kt` — Route definition

### KMP Readiness: 9/10

### Azioni

#### 4.1 Estrarre AuthService interface

```kotlin
// features/auth/domain/AuthService.kt (NUOVO)
interface AuthService {
    fun isAuthenticated(): Boolean
    fun getCurrentUserId(): String?
    suspend fun signInWithGoogle(credential: Any): Result<String>  // returns userId
    suspend fun signOut()
}
```

#### 4.2 Creare implementazione Firebase

```kotlin
// features/auth/platform/FirebaseAuthService.kt (NUOVO)
class FirebaseAuthService @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthService {
    override fun isAuthenticated() = firebaseAuth.currentUser != null
    override fun getCurrentUserId() = firebaseAuth.currentUser?.uid
    // ...
}
```

#### 4.3 Refactoring ViewModel

Il ViewModel deve dipendere da `AuthService` (interfaccia), non da `FirebaseAuth` direttamente.

#### 4.4 Riorganizzare struttura

```
features/auth/
├── domain/
│   └── AuthService.kt              # Interface (future commonMain)
├── platform/
│   └── FirebaseAuthService.kt      # Firebase impl (future androidMain)
├── presentation/
│   ├── AuthenticationViewModel.kt   # Usa AuthService
│   ├── AuthenticationScreen.kt
│   └── AuthenticationContent.kt
└── navigation/
    └── AuthNavigation.kt
```

### Verifica
```bash
./gradlew :features:auth:build
```

---

## FASE 5 — features/insight

**Obiettivo:** Minima pulizia. Modulo gia' quasi pronto per KMP.

### KMP Readiness: 9/10

### Azioni

#### 5.1 Sostituire android.util.Log (se presente)

#### 5.2 Verificare che il ViewModel dipenda solo da interfacce (InsightRepository)

#### 5.3 Riorganizzare

```
features/insight/
├── presentation/
│   ├── InsightScreen.kt
│   └── InsightViewModel.kt
```

Nessuna azione significativa necessaria.

### Verifica
```bash
./gradlew :features:insight:build
```

---

## FASE 6 — features/history

**Obiettivo:** Minima pulizia.

### KMP Readiness: 8/10

### Azioni

#### 6.1 Verificare dipendenze del ViewModel

`HistoryViewModel` dovrebbe dipendere solo da interfacce repository, non da implementazioni Firebase dirette.

#### 6.2 Sostituire android.util.Log

#### 6.3 Riorganizzare

```
features/history/
├── presentation/
│   ├── HistoryScreen.kt
│   ├── HistoryContent.kt
│   ├── HistoryTopBar.kt
│   ├── ChatHistoryFullScreen.kt
│   ├── DiaryHistoryFullScreen.kt
│   └── HistoryViewModel.kt
└── navigation/
    └── HistoryNavigation.kt
```

### Verifica
```bash
./gradlew :features:history:build
```

---

## FASE 7 — features/settings

**Obiettivo:** Separare form logic da UI, verificare dipendenze pure.

### KMP Readiness: 8/10

### Azioni

#### 7.1 Verificare SettingsViewModel

Deve dipendere da `ProfileSettingsRepository` (interfaccia), non da Firestore direttamente.

#### 7.2 Riorganizzare

```
features/settings/
├── presentation/
│   ├── SettingsScreen.kt
│   ├── SettingsViewModel.kt
│   └── components/SettingsComponents.kt
├── subscreens/
│   ├── GoalsSettingsScreen.kt
│   ├── HealthInfoSettingsScreen.kt
│   ├── LifestyleSettingsScreen.kt
│   └── PersonalInfoSettingsScreen.kt
└── navigation/
    └── SettingsNavigation.kt
```

### Verifica
```bash
./gradlew :features:settings:build
```

---

## FASE 8 — features/onboarding

**Obiettivo:** Separare wizard logic da UI, verificare dipendenze.

### KMP Readiness: 8/10

### Azioni

#### 8.1 Estrarre wizard step validation logic

Se `OnboardingViewModel` contiene logica di validazione dei form, estrarre in una classe `OnboardingValidator` testabile indipendentemente.

#### 8.2 Verificare Lottie usage

Lottie e' usato per animazioni. Per KMP, sara' necessario un wrapper. Per ora, isolare le chiamate Lottie in composables dedicati.

#### 8.3 Riorganizzare

```
features/onboarding/
├── domain/
│   └── OnboardingValidator.kt       # NUOVO (se serve)
├── presentation/
│   ├── OnboardingScreen.kt
│   ├── OnboardingViewModel.kt
│   └── steps/
│       ├── GoalsStep.kt
│       ├── HealthInfoStep.kt
│       ├── LifestyleStep.kt
│       ├── PersonalInfoStep.kt
│       └── ReviewStep.kt
└── navigation/
    └── OnboardingNavigation.kt
```

### Verifica
```bash
./gradlew :features:onboarding:build
```

---

## FASE 9 — features/home

**Obiettivo:** Use cases sono gia' ben separati. Verificare che non ci siano dipendenze Android nei use cases.

### KMP Readiness: 8/10

### Azioni

#### 9.1 Verificare use cases

I seguenti devono essere PURE Kotlin (nessun import Android):
- `AggregateCognitivePatternsUseCase.kt`
- `CalculateMoodDistributionUseCase.kt`
- `CalculateStreaksUseCase.kt`
- `CalculateTodayPulseUseCase.kt`
- `CalculateTopicsFrequencyUseCase.kt`
- `GetAchievementsUseCase.kt`

Se usano `android.util.Log` -> sostituire con `AppLogger`.

#### 9.2 Verificare ColorUtils, DateFormatters

- `ColorUtils.kt` — Se usa Compose Color, spostare in presentation/
- `DateFormatters.kt` — Se usa java.time formatters, annotare per KMP
- `EmotionAwareColors.kt` — Se usa Compose, spostare in presentation/
- `EmotionShapes.kt` — Se usa Compose, spostare in presentation/

#### 9.3 Controllare HomeViewModel

Deve dipendere solo da interfacce repository + use cases.

#### 9.4 Riorganizzare

```
features/home/
├── domain/
│   ├── model/
│   │   ├── AchievementModels.kt
│   │   ├── HomeUiModels.kt
│   │   └── InsightAggregations.kt
│   └── usecase/
│       ├── AggregateCognitivePatternsUseCase.kt
│       ├── CalculateMoodDistributionUseCase.kt
│       ├── CalculateStreaksUseCase.kt
│       ├── CalculateTodayPulseUseCase.kt
│       ├── CalculateTopicsFrequencyUseCase.kt
│       └── GetAchievementsUseCase.kt
├── presentation/
│   ├── HomeScreen.kt
│   ├── HomeContent.kt
│   ├── HomeTopBar.kt
│   ├── HomeViewModel.kt
│   ├── SnapshotScreen.kt
│   ├── SnapshotViewModel.kt
│   ├── EnterpriseNavigationBar.kt
│   ├── LoadingScreen.kt
│   ├── LoadingSystem.kt
│   ├── MissingPermissionsApi.kt
│   ├── SettingsScreen.kt          # Possibile conflitto con features/settings?
│   └── components/
│       ├── FilterChipRow.kt
│       ├── UnifiedContentCard.kt
│       ├── UnifiedSearchBar.kt
│       ├── achievements/
│       ├── charts/
│       ├── common/
│       ├── feed/
│       ├── hero/
│       └── insights/
├── util/                           # Se Compose -> spostare in presentation/
│   ├── ColorUtils.kt
│   ├── DateFormatters.kt
│   ├── EmotionAwareColors.kt
│   └── EmotionShapes.kt
├── di/
│   └── HomeUseCaseModule.kt
└── navigation/
    ├── HomeNavigation.kt
    └── SettingsNavigation.kt
```

**NOTA:** C'e' un `SettingsScreen.kt` in features/home e un altro in features/settings. Verificare se e' un duplicato o ha funzioni diverse. Se duplicato, eliminare quello in home.

### Verifica
```bash
./gradlew :features:home:build
```

---

## FASE 10 — features/write

**Obiettivo:** Separare wizard logic e gallery management da UI.

### KMP Readiness: 7/10

### Azioni

#### 10.1 Verificare WriteViewModel

Deve dipendere da interfacce repository. Controllare se ha dipendenze Firebase dirette per upload immagini.

#### 10.2 Estrarre WizardModels.kt

Se `WizardModels.kt` e' pure data, spostare in domain/.

#### 10.3 Riorganizzare

```
features/write/
├── domain/
│   └── model/
│       └── WizardModels.kt          # Se pure data
├── presentation/
│   ├── WriteScreen.kt
│   ├── WriteContent.kt
│   ├── WriteTopBar.kt
│   ├── WriteViewModel.kt
│   ├── PsychologicalMetricsSheet.kt
│   └── wizard/
│       ├── CompletionStep.kt
│       ├── IntensityShapes.kt
│       ├── PsychologicalMetricsWizardDialog.kt
│       ├── WizardAnimations.kt
│       ├── WizardComponents.kt
│       └── WizardSteps.kt
└── navigation/
    └── WriteNavigation.kt
```

### Verifica
```bash
./gradlew :features:write:build
```

---

## FASE 11 — features/profile

**Obiettivo:** Separare chart data transformation da UI.

### KMP Readiness: 7/10

### Azioni

#### 11.1 Verificare ProfileViewModel

La logica di trasformazione dati per i grafici (Vico) potrebbe contenere pure Kotlin calculations. Se si, estrarre in un use case.

#### 11.2 Isolare Vico dependency

Il charting library (Vico) e' Compose-specific. Assicurarsi che il ViewModel prepari dati in formato generico, e solo la UI li converte in formato Vico.

#### 11.3 Riorganizzare

```
features/profile/
├── domain/
│   └── ChartDataTransformer.kt     # NUOVO - pure math
├── presentation/
│   ├── ProfileDashboard.kt
│   └── ProfileViewModel.kt
```

### Verifica
```bash
./gradlew :features:profile:build
```

---

## FASE 12 — features/chat

**Obiettivo:** Questo e' il modulo piu' complesso. Separare NETTAMENTE: audio pipeline (Android-only) | domain logic (pure Kotlin) | UI (Compose).

### KMP Readiness: 2/10

### Azioni

#### 12.1 Mappatura attuale

| Directory | Contenuto | KMP Status |
|-----------|-----------|------------|
| `audio/` | GeminiLiveAudioSource, GeminiNativeVoiceSystem, GeminiVoiceAudioSource, SpeechToTextManager, SynchronizedSpeechControllerImpl | Android-only |
| `audio/engine/` | AAAudioEngine, AdaptiveJitterBuffer, AudioEngineMetrics, HighPriorityAudioThread, LockFreeAudioRingBuffer, PacketLossConcealmentEngine | Android-only |
| `audio/vad/` | SileroVadEngine | Android-only |
| `config/` | ApiConfigManager | Quasi pure |
| `data/audio/` | GeminiAudioPlayer, GeminiLiveAudioManager | Android-only |
| `data/camera/` | GeminiLiveCameraManager | Android-only |
| `data/websocket/` | GeminiLiveWebSocketClient | Quasi pure (WebSocket) |
| `di/` | AudioModule, ChatModule, GeminiLiveModule | Android-only (Hilt) |
| `domain/audio/` | AdaptiveBargeinDetector, AudioQualityAnalyzer, ConversationContextManager, FullDuplexAudioSession, ReferenceSignalBargeInDetector | MISTO |
| `domain/model/` | ChatUiModels, LiveChatState | Pure Kotlin |
| `navigation/` | ChatNavigation | Pure routes |
| `presentation/` | Screens + ViewModels + Components | Compose UI |

#### 12.2 Riorganizzare in layers

```
features/chat/
├── domain/                              # PURE KOTLIN (future commonMain)
│   ├── model/
│   │   ├── ChatUiModels.kt
│   │   └── LiveChatState.kt
│   ├── audio/
│   │   ├── ConversationContextManager.kt    # Se pure logic
│   │   ├── AudioQualityAnalyzer.kt          # Se pure math
│   │   └── AdaptiveBargeinDetector.kt       # Se pure logic
│   └── config/
│       └── ApiConfigManager.kt              # Interfaccia
│
├── platform/                            # ANDROID-ONLY (future androidMain)
│   ├── audio/
│   │   ├── engine/                      # AA Audio Engine (tutto)
│   │   ├── vad/SileroVadEngine.kt
│   │   ├── GeminiLiveAudioSource.kt
│   │   ├── GeminiNativeVoiceSystem.kt
│   │   ├── GeminiVoiceAudioSource.kt
│   │   ├── SpeechToTextManager.kt
│   │   └── SynchronizedSpeechControllerImpl.kt
│   ├── data/
│   │   ├── audio/GeminiAudioPlayer.kt
│   │   ├── audio/GeminiLiveAudioManager.kt
│   │   ├── camera/GeminiLiveCameraManager.kt
│   │   └── websocket/GeminiLiveWebSocketClient.kt
│   ├── audio/
│   │   ├── FullDuplexAudioSession.kt
│   │   └── ReferenceSignalBargeInDetector.kt
│   └── di/
│       ├── AudioModule.kt
│       ├── ChatModule.kt
│       └── GeminiLiveModule.kt
│
├── presentation/                        # COMPOSE UI
│   ├── screen/
│   │   ├── ChatScreen.kt
│   │   └── LiveScreen.kt
│   ├── viewmodel/
│   │   ├── ChatViewModel.kt
│   │   └── LiveChatViewModel.kt
│   └── components/
│       ├── ChatBubble.kt
│       ├── ChatInput.kt
│       ├── FluidAudioIndicator.kt
│       ├── GeminiLiquidVisualizer.kt
│       ├── LiveCameraPreview.kt
│       └── SimpleLiveCameraPreview.kt
│
└── navigation/
    └── ChatNavigation.kt
```

#### 12.3 Estrarre interfacce per audio services

Creare interfacce per servizi audio che il ViewModel usa:
```kotlin
// domain/
interface AudioSessionManager {
    fun startSession()
    fun stopSession()
    val isActive: StateFlow<Boolean>
}

interface VoiceActivityDetector {
    val isSpeaking: StateFlow<Boolean>
    fun start()
    fun stop()
}
```

Le implementazioni Android (FullDuplexAudioSession, SileroVadEngine) implementano queste interfacce.

#### 12.4 Sostituire android.util.Log

In TUTTI i file del modulo chat.

### Verifica
```bash
./gradlew :features:chat:build
```

---

## FASE 13 — features/humanoid

**Obiettivo:** Rimuovere completamente AR (sperimentale), separare domain puro (models, interfaces, lip-sync math, VRM parsing) dal rendering Filament. Filament e' cross-platform (Android/iOS/Desktop/Web) quindi il modulo E' MIGRABILE a KMP con strategia expect/actual per il rendering.

### KMP Readiness: 5/10 (MIGRABILE con effort)

> **Nota:** Filament e' un motore 3D C++ di Google con bindings per Android (JNI),
> iOS (C++/Swift), macOS (Metal), Desktop (Vulkan/OpenGL) e Web (WebGL/WASM).
> L'unico blocco era ARCore — che rimuoviamo perche' sperimentale.

### Azioni

#### 13.1 RIMUOVERE COMPLETAMENTE AR (sperimentale)

**11 file da eliminare:**

```
# Domain AR (interfaces + models)
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/domain/ar/ArSessionManager.kt
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/domain/ar/ArModels.kt

# Data AR (ARCore implementation)
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/data/ar/ArCoreSessionManager.kt
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/data/ar/ArCameraStreamRenderer.kt
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/data/ar/ArFocusReticle.kt
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/data/ar/ArPlaneRenderer.kt

# Rendering AR
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/rendering/ArFilamentRenderer.kt

# UI/API AR
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/api/ArHumanoidAvatarView.kt
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/presentation/components/ArFilamentView.kt
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/presentation/components/ArPlacementOverlay.kt

# DI AR
DELETE: features/humanoid/src/main/java/com/lifo/humanoid/di/ArModule.kt
```

**Riferimenti esterni da pulire:**

```
# In app/src/main/java/com/lifo/app/CalmifyApp.kt:
RIMUOVERE: import com.lifo.humanoid.api.ArHumanoidAvatarView
RIMUOVERE: import com.lifo.humanoid.domain.ar.ArSessionManager
RIMUOVERE: Blocco ~95 righe per Screen.AvatarLiveChat.route con AR mode

# In app/src/main/java/com/lifo/app/integration/avatar/AvatarIntegrationEntryPoint.kt:
RIMUOVERE: import com.lifo.humanoid.domain.ar.ArSessionManager
RIMUOVERE: fun arSessionManager(): ArSessionManager
```

**Dipendenze da rimuovere in build.gradle:**

```gradle
# features/humanoid/build.gradle:
RIMUOVERE: implementation(libs.arsceneview)

# gradle/libs.versions.toml:
RIMUOVERE: arsceneview (se non usato altrove)
```

#### 13.2 Separare domain puro dal rendering

Dopo la rimozione AR, riorganizzare in layer KMP-friendly:

```
features/humanoid/
├── domain/                              # PURE KOTLIN (future commonMain)
│   ├── model/
│   │   ├── AvatarState.kt              # Pure data class
│   │   ├── Emotion.kt                  # Pure enum
│   │   └── Viseme.kt                   # Pure data class
│   ├── lipsync/
│   │   ├── PhonemeConverter.kt          # Pure string -> phoneme logic
│   │   └── VisemeMapper.kt             # Pure phoneme -> viseme mapping
│   ├── animation/
│   │   ├── AnimationCoordinator.kt      # State machine (se pure logic)
│   │   ├── BlinkController.kt          # Timer-based blink (pure math)
│   │   └── IdleAnimationController.kt  # Idle state machine (se pure)
│   └── vrm/
│       ├── VrmBlendShapePresets.kt      # Pure constants/enum
│       └── VrmModel.kt                 # Pure data class (VRM metadata)
│
├── platform/                            # PLATFORM-SPECIFIC (future androidMain/iosMain)
│   ├── rendering/
│   │   ├── FilamentRenderer.kt          # Filament JNI (Android) / C++ (iOS)
│   │   └── PointCloudMaterialBuilder.kt
│   ├── vrm/
│   │   ├── VrmLoader.kt                # Filament-specific VRM loading
│   │   ├── VrmBlendShapeController.kt  # Filament entity manipulation
│   │   ├── VrmHumanoidBoneMapper.kt    # Filament bone access
│   │   └── GltfBoneOptimizer.kt        # Filament GLTF optimization
│   ├── animation/
│   │   ├── VrmaAnimationLoader.kt      # Filament animator
│   │   ├── VrmaAnimationPlayer.kt      # Filament playback
│   │   ├── VrmaAnimationPlayerFactory.kt
│   │   ├── IdlePoseController.kt       # Filament bone transforms
│   │   ├── IdleRotationController.kt   # Filament entity transforms
│   │   └── LookAtController.kt         # Filament head tracking
│   ├── lipsync/
│   │   └── LipSyncController.kt        # Filament blend shape driving
│   └── di/
│       └── HumanoidModule.kt           # Hilt (Android-only per ora)
│
├── api/                                 # PUBLIC API
│   ├── HumanoidController.kt           # Interface -> future commonMain
│   ├── HumanoidControllerImpl.kt       # Implementation (platform)
│   ├── HumanoidComposable.kt           # Compose UI
│   └── HumanoidIntegrationHelper.kt
│
├── presentation/
│   ├── HumanoidScreen.kt
│   ├── HumanoidViewModel.kt
│   ├── components/
│   │   └── FilamentView.kt             # Android SurfaceView wrapper
│   └── navigation/
│       └── HumanoidNavigation.kt
```

#### 13.3 Estrarre HumanoidController interface

L'interfaccia `HumanoidController` e' gia' nel posto giusto. Verificare che sia pure Kotlin (nessun import Android/Filament). In futuro KMP, questa interface va in `commonMain`, le implementazioni sono per-platform:

```kotlin
// commonMain (pure Kotlin interface)
interface HumanoidController {
    fun playAnimation(name: String)
    fun setEmotion(emotion: String)
    fun startLipSync()
    fun stopLipSync()
    // ...
}

// androidMain: HumanoidControllerImpl (Filament JNI)
// iosMain:     HumanoidControllerImpl (Filament C++/Swift bridge)
// webMain:     HumanoidControllerImpl (Filament WASM/WebGL)
```

#### 13.4 Estrarre domain models puri in core/util (opzionale)

Se altri moduli necessitano di `Emotion`, `Viseme`, `AvatarState`:
```
core/util/model/
├── AvatarState.kt   # Pure data class
├── Emotion.kt       # Pure enum
└── Viseme.kt        # Pure data class
```

Altrimenti possono restare in `features/humanoid/domain/model/`.

#### 13.5 Strategia KMP futura per Filament

Per la conversione KMP effettiva, il rendering Filament richiedera':

| Piattaforma | Bindings Filament | Backend Grafico |
|---|---|---|
| Android | `filament-android` AAR (JNI) — attuale | OpenGL ES 3.0 / Vulkan |
| iOS | `libfilament.a` (C++ static lib) + Swift wrapper | Metal |
| macOS Desktop | `libfilament.a` (C++ static lib) | Metal / OpenGL |
| Web | `filament.js` (Emscripten/WASM) | WebGL 2.0 |

Tutti condividono lo stesso API C++ di Filament — solo i bindings cambiano.

### Verifica
```bash
./gradlew :features:humanoid:build
./gradlew assembleDebug  # Verificare che la rimozione AR non rompa nulla
```

---

## FASE 14 — app

**Obiettivo:** Unificare i 3 package, estrarre business logic dai composables, creare un app module pulito.

### Stato Attuale (3 package diversi!)

```
app/src/main/java/
├── com/lifo/app/                    # Package 1
│   ├── CalmifyApp.kt
│   ├── ProfileScreen.kt
│   └── integration/avatar/
│       ├── AvatarIntegrationEntryPoint.kt
│       ├── GestureAnimationAdapter.kt
│       ├── TTSLipSyncAdapter.kt
│       └── VrmEmotionBridge.kt
├── com/lifo/calmifyapp/             # Package 2
│   ├── CalmifyApplication.kt
│   ├── CalmifyFirebaseMessagingService.kt
│   ├── MainActivity.kt
│   ├── di/DatabaseModule.kt
│   └── navigation/NavGraph.kt
└── com/lifo/navigation/             # Package 3
    └── NavigationState.kt
```

### Azioni

#### 14.1 Unificare sotto un unico package

Scegliere `com.lifo.calmifyapp` come package principale (gia' usato per Application e MainActivity):

```
app/src/main/java/com/lifo/calmifyapp/
├── CalmifyApplication.kt
├── MainActivity.kt
├── ui/
│   ├── CalmifyApp.kt              # DA com.lifo.app
│   ├── ProfileScreen.kt           # DA com.lifo.app
│   └── navigation/
│       ├── NavGraph.kt
│       └── NavigationState.kt     # DA com.lifo.navigation
├── integration/
│   └── avatar/
│       ├── AvatarIntegrationEntryPoint.kt
│       ├── GestureAnimationAdapter.kt
│       ├── TTSLipSyncAdapter.kt
│       └── VrmEmotionBridge.kt
├── service/
│   └── CalmifyFirebaseMessagingService.kt
└── di/
    └── DatabaseModule.kt
```

#### 14.2 Estrarre business logic da MainActivity

**Da MainActivity, estrarre:**
- `AppState` sealed class -> `core/util/model/AppState.kt`
- Onboarding check logic -> ViewModel
- FCM token registration -> Service layer
- API key configuration -> Config module
- Image cleanup logic -> Data layer

#### 14.3 Estrarre business logic da CalmifyApp.kt

**Da CalmifyApp, estrarre:**
- Sign-out logic -> AuthService
- Delete-all-data logic -> Repository method
- Deep link handling -> NavigationRouter
- User state management -> ViewModel

#### 14.4 Estrarre pure Kotlin da VrmEmotionBridge

La logica di keyword-based emotion detection e' pure Kotlin:
```kotlin
// Estrarre in core/util o features/humanoid/domain/
object EmotionKeywordDetector {
    fun detectFromText(text: String): Emotion { ... }
}
```

#### 14.5 Aggiornare AndroidManifest.xml

Dopo la rinomina dei package, aggiornare tutti i riferimenti nel manifest.

### Verifica
```bash
./gradlew :app:build
./gradlew assembleDebug
# Test manuale: avviare app, verificare navigazione, login, deep links
```

---

## FASE 15 — Build System & Gradle

**Obiettivo:** Preparare version catalog e build configuration per KMP, SENZA convertire effettivamente a KMP.

### Azioni

#### 15.1 Aggiornare libs.versions.toml

Aggiungere le versioni KMP (commentate, pronte per l'uso):

```toml
[versions]
# === KMP-READY (uncomment when converting) ===
# compose-multiplatform = "1.7.1"
# sqldelight = "2.0.2"
# voyager = "1.0.0"
# koin = "3.6.0"
# kotlinx-datetime = "0.6.0"

[libraries]
# === KMP ALTERNATIVES (uncomment when converting) ===
# sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
# koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
# voyager-navigator = { module = "cafe.adriel.voyager:voyager-navigator", version.ref = "voyager" }
# kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
```

#### 15.2 Aggiornare ProjectConfig.kt

Aggiungere flag per KMP readiness:
```kotlin
object ProjectConfig {
    // Existing config...

    // KMP Migration flags
    const val KMP_READY = false  // Set to true after all phases complete
}
```

#### 15.3 Creare template per modulo KMP

Creare un file template `buildSrc/src/main/java/KmpModuleTemplate.md` che documenta come convertire un modulo da Android-only a KMP quando sara' il momento.

### Verifica
```bash
./gradlew build
```

---

## Riepilogo Dipendenze KMP

### Da sostituire durante la conversione KMP effettiva (DOPO questo refactoring)

| Attuale | KMP Alternativa | Moduli Impattati |
|---------|-----------------|------------------|
| **Room** | SQLDelight | data/mongo |
| **Hilt** | Koin | tutti |
| **Navigation Compose** | Voyager / Decompose | tutti |
| **java.time** | kotlinx-datetime | data/mongo, features/* |
| **java.util.UUID** | kotlin-uuid library | data/mongo |
| **android.util.Log** | AppLogger (gia' creato) | tutti |
| **android.net.Uri** | String paths | core/ui, features/write |
| **Firebase SDK** | Firebase Kotlin SDK / REST | data/mongo, features/auth |
| **Accompanist Permissions** | Custom expect/actual | features/home |
| **Lottie** | Skottie (Compose MP) | features/onboarding |
| **Vico Charts** | KMP chart lib o custom Canvas | features/profile |

### Richiede bindings platform-specific (expect/actual)

| Libreria | Strategia KMP | Modulo |
|----------|---------------|--------|
| **Filament 3D** | Cross-platform C++ engine: JNI (Android), C++/Swift (iOS), WASM (Web) | features/humanoid |
| **ARCore** | **RIMOSSO** (era sperimentale) | ~~features/humanoid~~ |
| **CameraX** | Android Jetpack; iOS usa AVFoundation | features/chat |
| **Silero VAD** | ONNX Runtime Android | features/chat |
| **WebRTC** | Platform-specific native | features/chat |

---

## Risk Assessment

| Rischio | Probabilita' | Impatto | Mitigazione |
|---------|-------------|---------|-------------|
| **Build break dopo spostamento file** | ALTA | MEDIO | Compilare dopo OGNI file spostato |
| **Import rotti cross-module** | ALTA | MEDIO | Find & replace sistematico per ogni package |
| **Mood.kt refactor rompe 13 moduli** | ALTA | ALTO | Fase 1 critica: testare tutti i moduli |
| **DiaryHolder.kt spostamento** | MEDIA | MEDIO | Verificare chi lo importa prima dello spostamento |
| **Hilt injection breaks** | MEDIA | ALTO | Non toccare Hilt DI fino alla conversione KMP |
| **AndroidManifest dopo rename** | MEDIA | ALTO | Aggiornare manifest INSIEME al rename |
| **Performance regression** | BASSA | MEDIO | Questo refactoring non cambia logica, solo organizzazione |

---

## Checklist per Ogni Fase

- [ ] Leggere TUTTI i file coinvolti prima di modificare
- [ ] Creare branch feature dedicato (`refactor/fase-N-module-name`)
- [ ] Spostare/modificare file
- [ ] Aggiornare TUTTI gli import nei moduli dipendenti
- [ ] `./gradlew :modulo:build` — modulo singolo
- [ ] `./gradlew assembleDebug` — build completo
- [ ] Test manuale: avviare app e verificare funzionalita' core
- [ ] Commit con messaggio descrittivo
- [ ] Merge in branch principale

---

## Timeline Stimata

| Fase | Modulo | Giorni stimati | Priorita' |
|------|--------|---------------|-----------|
| 1 | core/util | 3-4 | CRITICA |
| 2 | core/ui | 2-3 | CRITICA |
| 3 | data/mongo | 3-4 | CRITICA |
| 4 | features/auth | 1 | ALTA |
| 5 | features/insight | 0.5 | MEDIA |
| 6 | features/history | 0.5 | MEDIA |
| 7 | features/settings | 1 | MEDIA |
| 8 | features/onboarding | 1 | MEDIA |
| 9 | features/home | 2 | ALTA |
| 10 | features/write | 1-2 | MEDIA |
| 11 | features/profile | 1 | MEDIA |
| 12 | features/chat | 3-4 | ALTA |
| 13 | features/humanoid | 2-3 | ALTA (rimozione AR + riorganizzazione domain/platform) |
| 14 | app | 2-3 | CRITICA |
| 15 | Build System | 1 | MEDIA |
| **TOTALE** | | **~23-30 giorni** | |

---

> *"Sir, ho analizzato 150+ file in 13 moduli. Il piano e' pronto — elegante, sistematico, e soprattutto SICURO. Un modulo alla volta, come costruire un'armatura pezzo per pezzo. Quando desidera iniziare?"*
> — Jarvis
