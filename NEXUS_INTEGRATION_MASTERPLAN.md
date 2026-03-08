# Nexus Integration Masterplan — Calmify → Social Wellness Platform

> **Autore:** Jarvis AI Assistant
> **Data:** 2026-02-28
> **Branch:** `migration/firebase-2025`
> **Prerequisito completato:** KMP Refactoring Preparatorio (15/15 fasi — DONE)
> **Strategia:** Evoluzione incrementale, mai Big Bang — ogni macro-fase è compilabile e deployabile

---

## Indice

1. [Analisi del Gap: Calmify vs Nexus](#1-analisi-del-gap)
2. [Visione: Social Wellness Platform](#2-visione)
3. [Principi Guida dell'Integrazione](#3-principi-guida)
4. [Mappa delle Macro-Fasi (7 Epiche)](#4-macro-fasi)
5. [EPICA 1 — Foundation: KMP + Build System](#epica-1)
6. [EPICA 2 — Architecture Shift: Hilt → Koin + MVVM → MVI](#epica-2)
7. [EPICA 3 — Navigation: Compose Navigation → Decompose](#epica-3)
8. [EPICA 4 — Data Layer: Firestore + Spanner Graph + Vertex AI](#epica-4)
9. [EPICA 5 — Social Features: Feed, Threads, Profili Sociali](#epica-5)
10. [EPICA 6 — Messaging + Real-time + Media Pipeline](#epica-6)
11. [EPICA 7 — Federation, AI Engine, FinOps](#epica-7)
12. [Gestione della Coesistenza: Features Wellness + Social](#8-coesistenza)
13. [Risk Assessment & Mitigazioni](#9-risk-assessment)
14. [Timeline Stimata & Milestones](#10-timeline)
15. [Decision Log (ADR)](#11-adr)

---

## 1. Analisi del Gap

### Stato Attuale di Calmify (Post KMP Refactoring Prep)

| Aspetto | Calmify Oggi | Nexus Target |
|---------|-------------|--------------|
| **Piattaforme** | Android only | Android + iOS + Desktop + Web |
| **UI** | Jetpack Compose + Material3 | Compose Multiplatform 1.8+ |
| **DI** | Hilt 2.55 (Android-only, annotation processing) | Koin 4.x (KMP-first, zero codegen) |
| **Navigation** | Navigation Compose (type-safe routes) | Decompose 3.x (lifecycle-aware, deep linking KMP) |
| **Pattern** | MVVM-ish (StateFlow ma senza contratto MVI) | MVI rigoroso (Intent/State/SideEffect) |
| **Database** | Room + Firestore (Native Mode) | Firestore + Spanner Graph + Vertex AI Vector Search |
| **Networking** | OkHttp 4.12 + Ktor 2.3 (solo Gemini SDK) | Ktor 3.1 + HTTP/3 (QUIC) per tutto |
| **Firebase SDK** | Firebase Android SDK (BOM 34.7.0) | KFire (Official KMP Firebase SDK, 2026) |
| **Auth** | Firebase Auth (Android) | Firebase Auth via KFire (multiplatform) |
| **AI/Voice** | Gemini Live + Sherpa-ONNX TTS + Silero VAD | Genkit Agents + Vertex AI Streaming |
| **3D Avatar** | Filament + VRM/VRMA (Android JNI) | Filament (cross-platform: JNI/C++/Metal/WASM) |
| **Social Features** | Nessuna | Feed, Threads, Profili, Messaging, Follow Graph |
| **Federation** | Nessuna | ActivityPub Bridge (Mastodon/PixelFed interop) |
| **Media Pipeline** | Firebase Storage | Cloud Storage + Media CDN + Transcoder API |
| **Serialization** | Gson/Room converters | kotlinx.serialization (compile-time safe) |
| **Testing** | JUnit + Mockk + Turbine (Android-only) | commonTest pyramid (70% unit, 25% integration, 5% E2E) |
| **Build** | Gradle + AGP + KSP | Gradle + Amper metadata + Convention Plugins |

### Cosa Calmify ha GIÀ che Nexus necessita

| Asset Esistente | Riutilizzo in Nexus |
|----------------|---------------------|
| **Architettura multi-modulo** | Base per la modularizzazione KMP feature-first |
| **StateFlow ovunque** | Fondamenta per MVI (già reactive, serve solo il contratto Intent/State) |
| **Firebase Auth iniettato via DI** | Pronto per wrapping in KFire |
| **Repository interfaces in core/util** | Domain layer separation già fatta |
| **android.util.Log eliminato** | Zero dipendenze Android nei log |
| **Uri → String refactoring** | Tipi platform-independent |
| **Filament rendering** | Cross-platform ready (JNI → C++/Metal/WASM) |
| **KMP dependencies commentate in TOML** | Pronti per l'attivazione |
| **ProjectConfig.KMP_READY flag** | Gate per la migrazione graduale |

### Cosa manca completamente

1. **Nessun social graph** — niente follow/follower, niente feed, niente threads
2. **Nessun sistema di contenuti pubblici** — tutto è privato (journal personale)
3. **Nessun messaging peer-to-peer** — solo chat con AI
4. **Nessun backend scalabile** — Firestore usato solo come storage, non come piattaforma social
5. **Nessuna federazione** — app completamente siloed
6. **Nessun media pipeline** — upload diretto a Firebase Storage senza transcoding
7. **Nessun sistema di moderazione** — non necessario per app personale

---

## 2. Visione: Social Wellness Platform

Calmify evolve da **app wellness personale** a **piattaforma social per il benessere mentale**. L'identità di Calmify (journaling, mood tracking, avatar AI) diventa il **differenziatore** rispetto a Threads/Mastodon generico.

### Unique Value Proposition

```
┌─────────────────────────────────────────────────────────────────┐
│                    CALMIFY NEXUS                                │
│                                                                 │
│   "Un social network dove il benessere mentale è al centro"     │
│                                                                 │
│   ┌──────────────┐  ┌───────────────┐  ┌──────────────────┐    │
│   │  WELLNESS    │  │    SOCIAL     │  │   AI-NATIVE      │    │
│   │  (Existing)  │  │   (Nexus)     │  │  (Enhanced)      │    │
│   ├──────────────┤  ├───────────────┤  ├──────────────────┤    │
│   │ Journaling   │  │ Feed/Threads  │  │ Genkit Agents    │    │
│   │ Mood Tracking│  │ Follow Graph  │  │ Semantic Feed    │    │
│   │ Avatar AI    │  │ DM Messaging  │  │ Content Moderat. │    │
│   │ Insights     │  │ Communities   │  │ Wellness AI      │    │
│   │ Onboarding   │  │ ActivityPub   │  │ Vector Search    │    │
│   └──────────────┘  └───────────────┘  └──────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### Feature Mapping: Wellness → Social

| Calmify Feature | Evoluzione Social |
|----------------|-------------------|
| **Journal privato** | "Shared Reflections" — post opzionalmente pubblici |
| **Mood tracking** | Mood aggregato anonimo nella community |
| **Chat con AI** | AI come moderatore/facilitatore nelle conversazioni |
| **Avatar 3D** | Avatar personalizzato come profilo social |
| **Insights** | Dashboard benessere community |
| **Onboarding** | Onboarding social + wellness setup |

---

## 3. Principi Guida

1. **Mai rompere l'esistente** — ogni fase compila e funziona. Le features wellness continuano a operare durante tutta la migrazione.

2. **Coesistenza Hilt + Koin** — nella fase di transizione, i due DI coesistono. Hilt gestisce i moduli legacy, Koin i nuovi moduli KMP.

3. **Feature flag per tutto** — ogni social feature è dietro `ProjectConfig.SOCIAL_ENABLED = false`. Deployable in produzione sempre.

4. **Backend prima, UI dopo** — Spanner Graph e Vertex AI vanno configurati prima di scrivere UI social.

5. **KMP first per tutto il nuovo** — ogni nuovo modulo nasce KMP. Mai creare codice Android-only per features nuove.

6. **Moduli ibridi** — durante la transizione, un modulo può avere sia `src/main/` (Android legacy) sia `src/commonMain/` (KMP new). Gradle supporta questa coesistenza.

7. **Un'epica alla volta** — non parallelizzare le epiche. Ognuna ha dipendenze dalla precedente.

---

## 4. Mappa delle Macro-Fasi (7 Epiche)

```
EPICA 1: Foundation (KMP + Build System)
    ↓
EPICA 2: Architecture Shift (Hilt→Koin, MVVM→MVI)
    ↓
EPICA 3: Navigation (Compose Nav → Decompose)
    ↓
EPICA 4: Data Layer (Spanner Graph + Vertex AI + KFire)
    ↓
EPICA 5: Social Features (Feed, Threads, Profiles)
    ↓
EPICA 6: Messaging + Real-time + Media Pipeline
    ↓
EPICA 7: Federation + AI Engine + FinOps
```

### Dipendenze tra Epiche

```
EPICA 1 ─── blocca ──→ EPICA 2, 3, 4, 5, 6, 7
EPICA 2 ─── blocca ──→ EPICA 5, 6
EPICA 3 ─── blocca ──→ EPICA 5, 6
EPICA 4 ─── blocca ──→ EPICA 5, 6, 7
EPICA 5 ─── blocca ──→ EPICA 6 (parziale), EPICA 7
EPICA 6 ─── indipendente da ──→ EPICA 7 (parallelizzabile)
```

---

## EPICA 1 — Foundation: KMP + Build System

**Obiettivo:** Convertire il progetto da Android-only a KMP, attivando Compose Multiplatform e il build system Convention Plugins.

### Fase 1.1: Convention Plugins (`build-logic/`)

**Cosa:** Creare convention plugins Gradle per standardizzare la configurazione di tutti i moduli.

```
build-logic/
└── convention/
    ├── build.gradle.kts              # Plugin Gradle project
    ├── src/main/kotlin/
    │   ├── KmpLibraryConvention.kt   # Config base per moduli KMP library
    │   ├── KmpComposeConvention.kt   # Config per moduli con Compose Multiplatform
    │   ├── KmpTestConvention.kt      # Config test multiplatform
    │   └── AndroidAppConvention.kt   # Config per il modulo :app (Android entry point)
    └── settings.gradle.kts
```

**Azioni:**
1. Creare `build-logic/` con convention plugins
2. Migrare `ProjectConfig.kt` → convention plugin DSL
3. Aggiungere `build-logic` a `settings.gradle` via `includeBuild("build-logic")`
4. Applicare convention plugins a `core/util` (primo modulo di test)
5. Verificare che `assembleDebug` compili

**File impattati:** `settings.gradle`, `buildSrc/`, tutti i `build.gradle`

### Fase 1.2: Attivazione KMP Plugin + Compose Multiplatform

**Cosa:** Abilitare il plugin `kotlin-multiplatform` e `org.jetbrains.compose` nei moduli core.

**Strategia di coesistenza:**
- I moduli migrano da `com.android.library` a `kotlin("multiplatform")` + `com.android.library`
- Il codice Android esistente va in `androidMain/`, il nuovo shared in `commonMain/`
- I test esistenti restano in `androidTest/`, i nuovi in `commonTest/`

**Ordine di migrazione (dall'indice di KMP_REFACTOR_MASTERPLAN):**
1. `core/util` → `core-common` (rinominare per allineamento Nexus)
2. `core/ui` → `core-ui` (attivare Compose Multiplatform)
3. `data/mongo` → `core-database` (split Firestore logic)
4. Feature modules: uno alla volta

**Azioni per core/util → core-common:**
1. Decommentare versioni KMP in `libs.versions.toml`
2. Cambiare `build.gradle` da `com.android.library` a KMP multiplatform
3. Spostare `src/main/java/` → `src/androidMain/kotlin/`
4. Creare `src/commonMain/kotlin/` con le classi già platform-independent
5. Spostare modelli puri (Mood, ChatEmotion, ChatModels, repository interfaces) in `commonMain`
6. Compilare: `./gradlew :core:util:build`

### Fase 1.3: KFire Integration

**Cosa:** Sostituire Firebase Android SDK con KFire (Official KMP Firebase SDK 2026).

**Stato attuale Firebase:**
- Firebase Auth → iniettato via Hilt (`FirestoreModule.kt`)
- Firestore → `"calmify-native"` database (Native Mode)
- Firebase Storage → iniettato via Hilt
- FCM → `CalmifyFirebaseMessagingService`

**Strategia:**
- KFire è un drop-in replacement per Firebase Android SDK
- Stesse API, stessi nomi di classi, ma in `commonMain`
- La migrazione è quasi meccanica: cambiare imports + rimuovere expect/actual

**Azioni:**
1. Aggiungere dipendenze KFire in `libs.versions.toml`
2. Creare `core-firebase/` module KMP con wrapper KFire
3. Migrare `FirestoreModule.kt` da Hilt → Koin (vedi EPICA 2)
4. Aggiornare tutti i repository implementations per usare KFire imports
5. Testare auth flow end-to-end

### Fase 1.4: Ktor 3.1 Migration

**Cosa:** Aggiornare Ktor 2.3.12 → Ktor 3.1 con HTTP/3 (QUIC).

**Stato attuale:** Ktor usato solo dal Gemini AI SDK internamente. OkHttp usato per chat REST.

**Azioni:**
1. Aggiornare versione Ktor in `libs.versions.toml`
2. Migrare `ChatModule.kt` OkHttpClient → Ktor HttpClient
3. Configurare HTTP/3 engine
4. Creare `core-network/` module KMP con Ktor client condiviso
5. Aggiungere interceptors: Auth Bearer, logging, retry

### Fase 1.5: kotlinx.serialization Migration

**Cosa:** Migrare da Gson/Room converters a `kotlinx.serialization`.

**Azioni:**
1. Aggiungere `kotlinx-serialization-json` in `libs.versions.toml`
2. Annotare tutte le data class con `@Serializable`
3. Sostituire Gson converters in Room con kotlinx.serialization
4. Aggiornare Ktor content negotiation per usare `kotlinx.serialization`

### Fase 1.6: Amper Metadata (Opzionale, Bassa Priorità)

**Cosa:** Aggiungere Amper per configurazione dichiarativa dei target KMP.

**Nota:** Amper è ancora sperimentale (marzo 2026). Valutare se adottarlo o restare con Gradle puro. Convention plugins coprono l'80% dei benefici di Amper.

**Decisione: DEFER.** Monitorare la maturità di Amper. Convention plugins sono sufficienti.

---

## EPICA 2 — Architecture Shift: Hilt → Koin + MVVM → MVI

**Obiettivo:** Migrare il sistema DI da Hilt (Android-only) a Koin 4.x (KMP) e standardizzare il pattern architetturale su MVI.

### Fase 2.1: Koin Setup (Coesistenza con Hilt)

**Strategia della coesistenza:**
```
Fase iniziale:        Hilt gestisce TUTTI i moduli legacy
                      Koin gestisce TUTTI i nuovi moduli KMP

Fase intermedia:      Hilt gestisce solo il modulo :app + features legacy
                      Koin gestisce core/* + nuove features

Fase finale:          Koin gestisce TUTTO
                      Hilt rimosso completamente
```

**Azioni:**
1. Aggiungere Koin 4.x in `libs.versions.toml` + dipendenze
2. Creare `NexusKoinApplication.kt` nel modulo :app
3. Inizializzare Koin in `CalmifyApp` Application class ACCANTO a Hilt
4. Creare `coreModule`, `networkModule`, `databaseModule` in Koin
5. Bridge Hilt → Koin: le dipendenze Hilt esistenti vengono wrappate in Koin `factory { get<HiltProvidedClass>() }`
6. Testare che entrambi i DI framework coesistono

**Rischio:** Koin e Hilt possono conflictare sulla gestione del lifecycle delle Activity. Mitigazione: Koin è inizializzato a livello Application, Hilt gestisce solo Activity/Fragment scoping fino alla rimozione.

### Fase 2.2: MVI Contract per ogni Feature Esistente

**Cosa:** Definire il contratto MVI (Intent, State, SideEffect) per ogni ViewModel esistente.

**Stato attuale dei ViewModel:**
- `HomeViewModel` → StateFlow<HomeUiState> (già ha UiState, manca Intent sealed class)
- `ChatViewModel` → StateFlow<ChatUiState> (già ha UiState, manca Intent)
- `WriteViewModel` → StateFlow<WriteUiState> (già ha UiState, manca Intent)
- `AuthenticationViewModel` → StateFlow<Boolean> (minimale)
- `SettingsViewModel` → StateFlow<SettingsUiState>
- `HistoryViewModel` → mix StateFlow
- `InsightViewModel` → StateFlow
- `ProfileViewModel` → StateFlow
- `OnboardingViewModel` → StateFlow
- `HumanoidViewModel` → mix StateFlow + mutableState
- `LiveChatViewModel` → StateFlow complesso (audio pipeline)

**Pattern di migrazione per ogni ViewModel:**

```kotlin
// PRIMA (MVVM-ish):
class HomeViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    fun loadDiaries() { /* ... */ }
    fun refreshFeed() { /* ... */ }
    fun signOut() { /* ... */ }
}

// DOPO (MVI rigoroso):
// 1. Contratto
sealed interface HomeIntent {
    data object LoadDiaries : HomeIntent
    data object RefreshFeed : HomeIntent
    data object SignOut : HomeIntent
    data class SelectMood(val mood: Mood) : HomeIntent
}

sealed interface HomeSideEffect {
    data object NavigateToAuth : HomeSideEffect
    data class ShowError(val message: String) : HomeSideEffect
}

// 2. ViewModel
class HomeViewModel(
    private val getDiaries: GetDiariesUseCase,
    // ... Koin inject
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects = Channel<HomeSideEffect>(Channel.BUFFERED)
    val effects: Flow<HomeSideEffect> = _effects.receiveAsFlow()

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LoadDiaries -> loadDiaries()
            is HomeIntent.RefreshFeed -> refreshFeed()
            is HomeIntent.SignOut -> signOut()
            is HomeIntent.SelectMood -> selectMood(intent.mood)
        }
    }
}
```

**Ordine di migrazione MVI (complessità crescente):**
1. `OnboardingViewModel` (più semplice)
2. `InsightViewModel`
3. `ProfileViewModel`
4. `SettingsViewModel`
5. `AuthenticationViewModel`
6. `HistoryViewModel`
7. `WriteViewModel`
8. `HomeViewModel`
9. `ChatViewModel`
10. `HumanoidViewModel`
11. `LiveChatViewModel` (più complesso — audio pipeline stateful)

### Fase 2.3: Use Case Layer

**Cosa:** Estrarre la business logic dai ViewModel in Use Cases espliciti.

**Stato attuale:** I ViewModel contengono sia orchestrazione che business logic. Il refactoring KMP prep ha già estratto repository interfaces, ma mancano gli Use Cases.

**Pattern:**
```kotlin
// commonMain
class GetFeedUseCase(private val repository: FeedRepository) {
    suspend operator fun invoke(page: Int): Result<FeedPage> =
        withTimeout(10.seconds) {
            repository.getFeed(page)
        }
}
```

**Use Cases da creare per le feature ESISTENTI:**
- `auth/`: `SignInWithGoogleUseCase`, `SignOutUseCase`, `GetCurrentUserUseCase`
- `home/`: `GetDiariesUseCase`, `GetMoodDistributionUseCase` (già parziale)
- `write/`: `SaveDiaryUseCase`, `UploadMediaUseCase`, `DeleteDiaryUseCase`
- `chat/`: `SendMessageUseCase`, `StartVoiceSessionUseCase`
- `history/`: `GetDiaryHistoryUseCase`, `FilterDiariesUseCase`
- `insight/`: `GetInsightsUseCase`, `CalculateWellbeingScoreUseCase`
- `settings/`: `UpdateProfileSettingsUseCase`, `GetSettingsUseCase`

### Fase 2.4: Rimozione Hilt (Fase Finale)

**Prerequisito:** TUTTI i moduli migrati a Koin.

**Azioni:**
1. Rimuovere `@HiltViewModel`, `@Inject`, `@Module`, `@InstallIn` da tutti i file
2. Rimuovere `@AndroidEntryPoint` da Activity/Fragment
3. Rimuovere `@HiltAndroidApp` da Application class
4. Rimuovere Hilt + KSP plugin da `build.gradle`
5. Rimuovere Hilt dependencies da `libs.versions.toml`
6. Verificare che Koin gestisce tutto correttamente
7. Build test completo

---

## EPICA 3 — Navigation: Compose Navigation → Decompose

**Obiettivo:** Migrare da Jetpack Compose Navigation (Android-only) a Decompose 3.x (KMP lifecycle-aware).

### Fase 3.1: Decompose Setup

**Azioni:**
1. Aggiungere Decompose 3.x in `libs.versions.toml`
2. Creare `RootComponent` nel modulo :app
3. Definire `sealed interface RootDestination` con tutte le route esistenti
4. Implementare `childStack` con Decompose `StackNavigation`

**Mapping delle route esistenti:**

```kotlin
@Serializable
sealed interface RootDestination {
    // Wellness Features (esistenti)
    @Serializable data object Auth : RootDestination
    @Serializable data object Onboarding : RootDestination
    @Serializable data object Home : RootDestination
    @Serializable data class Write(val diaryId: String? = null) : RootDestination
    @Serializable data class Chat(val sessionId: String? = null) : RootDestination
    @Serializable data object LiveChat : RootDestination
    @Serializable data object History : RootDestination
    @Serializable data object Humanoid : RootDestination
    @Serializable data object Settings : RootDestination
    @Serializable data object Profile : RootDestination
    @Serializable data object Insight : RootDestination

    // Social Features (nuove — EPICA 5)
    @Serializable data object Feed : RootDestination
    @Serializable data class ThreadDetail(val threadId: String) : RootDestination
    @Serializable data class UserProfile(val userId: String) : RootDestination
    @Serializable data object Composer : RootDestination
    @Serializable data object Search : RootDestination
    @Serializable data class Messaging(val conversationId: String? = null) : RootDestination
    @Serializable data object Notifications : RootDestination
}
```

### Fase 3.2: Migrazione Feature Navigation

**Per ogni feature module:**
1. Creare `XxxComponent(componentContext: ComponentContext)` che wrappa il ViewModel
2. Sostituire `NavGraphBuilder.xxxRoute()` con `RootComponent.createChild()`
3. Rimuovere `NavHostController` references
4. Aggiornare deep linking per usare Decompose `DeepLinkHandler`

**Ordine:** Stessa priorità della migrazione MVI (semplice → complesso)

### Fase 3.3: Bottom Navigation Rework

**Stato attuale:** Custom `NavigationBar` con scroll-hide behavior in `CalmifyApp.kt`.

**Target:** Decompose `ChildSlot` per bottom navigation con 5 tab:
1. Home (Wellness dashboard)
2. Feed (Social — nuovo)
3. Compose (Crea post/thread — nuovo)
4. Chat (AI Chat)
5. Profile (Unificato)

### Fase 3.4: Rimozione Navigation Compose

**Prerequisito:** TUTTE le route migrate a Decompose.

**Azioni:**
1. Rimuovere `NavHostController` da `CalmifyApp.kt`
2. Rimuovere tutti i `NavGraphBuilder` extension functions
3. Rimuovere `navigation-compose` da `libs.versions.toml`
4. Rimuovere `NavGraph.kt`
5. Verificare deep linking FCM funziona con Decompose

---

## EPICA 4 — Data Layer: Firestore + Spanner Graph + Vertex AI

**Obiettivo:** Evolvere il data layer da "Firestore-only" a "Hybrid Graph" con Spanner Graph per il social graph e Vertex AI per il feed semantico.

### Fase 4.1: Core Database Module Refactoring

**Cosa:** Ristrutturare `data/mongo` in moduli KMP separati.

```
# PRIMA
data/
└── mongo/                    # Tutto in un modulo

# DOPO
core/
├── core-database/            # KFire Firestore + Spanner access layer (commonMain)
│   ├── firestore/            # Firestore data sources
│   ├── spanner/              # Spanner Graph client
│   └── di/                   # Koin database module
├── core-network/             # Ktor client config (commonMain)
│   ├── client/               # HttpClient setup, interceptors
│   ├── auth/                 # Auth token provider
│   └── di/                   # Koin network module
└── core-datastore/           # Preferences (DataStore KMP)
```

### Fase 4.2: Spanner Graph Setup (Backend)

**Cosa:** Configurare Cloud Spanner con Graph capabilities per il social graph.

**Schema Spanner Graph per il Social Graph:**

```sql
-- Tabelle nodo (entità)
CREATE TABLE Users (
    userId STRING(36) NOT NULL,
    displayName STRING(255),
    avatarUrl STRING(1024),
    bio STRING(500),
    createdAt TIMESTAMP NOT NULL,
    lastActive TIMESTAMP,
    isVerified BOOL DEFAULT false,
) PRIMARY KEY(userId);

CREATE TABLE Threads (
    threadId STRING(36) NOT NULL,
    authorId STRING(36) NOT NULL,
    parentThreadId STRING(36),
    text STRING(10000) NOT NULL,
    embedding ARRAY<FLOAT64>,         -- Vertex AI embedding
    likeCount INT64 DEFAULT 0,
    replyCount INT64 DEFAULT 0,
    visibility STRING(20) DEFAULT 'public',
    createdAt TIMESTAMP NOT NULL,
    CONSTRAINT FK_Author FOREIGN KEY (authorId) REFERENCES Users(userId),
) PRIMARY KEY(threadId);

-- Tabelle arco (relazioni)
CREATE TABLE Follows (
    followerId STRING(36) NOT NULL,
    followeeId STRING(36) NOT NULL,
    createdAt TIMESTAMP NOT NULL,
    CONSTRAINT FK_Follower FOREIGN KEY (followerId) REFERENCES Users(userId),
    CONSTRAINT FK_Followee FOREIGN KEY (followeeId) REFERENCES Users(userId),
) PRIMARY KEY(followerId, followeeId);

CREATE TABLE Likes (
    userId STRING(36) NOT NULL,
    threadId STRING(36) NOT NULL,
    createdAt TIMESTAMP NOT NULL,
    CONSTRAINT FK_LikeUser FOREIGN KEY (userId) REFERENCES Users(userId),
    CONSTRAINT FK_LikeThread FOREIGN KEY (threadId) REFERENCES Threads(threadId),
) PRIMARY KEY(userId, threadId);

-- Graph definition (GQL)
CREATE PROPERTY GRAPH SocialGraph
    NODE TABLES (Users, Threads)
    EDGE TABLES (
        Follows SOURCE KEY (followerId) REFERENCES Users
                DESTINATION KEY (followeeId) REFERENCES Users,
        Likes   SOURCE KEY (userId) REFERENCES Users
                DESTINATION KEY (threadId) REFERENCES Threads
    );
```

**Query GQL esempio:**
```sql
-- Feed: post dai follower dei miei follower (2° grado)
GRAPH SocialGraph
MATCH (me:Users {userId: @currentUserId})
      -[:Follows]->(:Users)
      -[:Follows]->(author:Users)
      <-[:AuthoredBy]-(thread:Threads)
WHERE thread.createdAt > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
ORDER BY thread.likeCount DESC
LIMIT 50
```

### Fase 4.3: Vertex AI Vector Search Integration

**Cosa:** Configurare Vertex AI per generare embeddings dei thread e calcolare il feed semantico.

**Pipeline:**
```
Nuovo Thread → Cloud Function trigger → Gemini 2.5 Flash-Lite → Embedding (768-dim)
    → Vertex AI Vector Search (Streaming index, <5s latency)
    → Feed query: nearest neighbors + social graph filter
```

**Azioni:**
1. Creare Cloud Function per embedding generation
2. Configurare Vertex AI Vector Search index (streaming mode)
3. Creare `EmbeddingService` in `core-database/` (KMP)
4. Integrare nella `FeedRepository`

### Fase 4.4: Firestore Collection Design per Social Features

**Nuove collections Firestore (real-time state):**

```
firestore/
├── users/{userId}/                    # Profilo utente (existente, esteso)
│   ├── profile                        # Display name, avatar, bio
│   ├── settings                       # Privacy, notification preferences
│   └── wellness/                      # Dati wellness PRIVATI (esistenti)
│       ├── diaries/{diaryId}
│       ├── moods/{moodId}
│       └── insights/{insightId}
│
├── threads/{threadId}/                # Thread content (NEW)
│   ├── content                        # Text, media, author
│   ├── likes_count                    # Sharded counter per hotspot prevention
│   └── replies/{replyId}             # Nested replies
│
├── conversations/{conversationId}/    # DM Messaging (NEW)
│   ├── metadata                       # Participants, last message
│   └── messages/{messageId}          # Individual messages
│
├── notifications/{userId}/            # User notifications (NEW)
│   └── items/{notificationId}
│
└── feed_cache/{userId}/               # Pre-computed feed cache (NEW)
    └── pages/{pageNumber}
```

### Fase 4.5: Repository Interfaces Update

**Nuovi repository interfaces (in `core-common/`):**

```kotlin
// commonMain
interface ThreadRepository {
    suspend fun postThread(content: String, mediaUri: String? = null): Result<String>
    fun observeThread(threadId: String): Flow<Thread>
    suspend fun toggleLike(threadId: String): Result<Unit>
    suspend fun deleteThread(threadId: String): Result<Unit>
}

interface FeedRepository {
    suspend fun getFeed(page: Int): Result<FeedPage>
    suspend fun refreshFeed(): Result<FeedPage>
    fun observeFeedUpdates(): Flow<FeedUpdate>
}

interface SocialGraphRepository {
    suspend fun follow(userId: String): Result<Unit>
    suspend fun unfollow(userId: String): Result<Unit>
    fun observeFollowers(userId: String): Flow<List<UserSummary>>
    fun observeFollowing(userId: String): Flow<List<UserSummary>>
    suspend fun getSuggestions(): Result<List<UserSummary>>
}

interface SocialMessagingRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(conversationId: String, content: String): Result<Unit>
    suspend fun createConversation(participantIds: List<String>): Result<String>
}

interface SearchRepository {
    suspend fun searchUsers(query: String): Result<List<UserSummary>>
    suspend fun searchThreads(query: String): Result<List<Thread>>
    suspend fun semanticSearch(query: String): Result<List<Thread>>  // Vector search
}

interface NotificationRepository {
    fun observeNotifications(): Flow<List<Notification>>
    suspend fun markAsRead(notificationId: String): Result<Unit>
}
```

### Fase 4.6: Room Migration / SQLDelight

**Decisione:** Migrare Room (Android-only) → SQLDelight (KMP) per caching locale.

**Stato attuale Room:**
- `AppDatabase` con DAO per ChatSession, ChatMessage, ImageToUpload, ImageToDelete
- Schema in `app/schemas/`

**Azioni:**
1. Aggiungere SQLDelight in `libs.versions.toml`
2. Creare schema `.sq` files equivalenti alle Room entities
3. Migrare DAO queries → SQLDelight queries
4. Testare migrazione dati esistenti
5. Rimuovere Room quando tutti i consumer sono migrati

---

## EPICA 5 — Social Features: Feed, Threads, Profili Sociali

**Obiettivo:** Implementare le core features social che trasformano Calmify in una piattaforma.

### Fase 5.1: Feature Module Setup

**Nuovi moduli feature (tutti KMP-first):**

```
feature/
├── feature-feed/              # Feed principale
│   ├── src/commonMain/
│   │   ├── domain/            # GetFeedUseCase, RefreshFeedUseCase
│   │   ├── data/              # FeedRepositoryImpl, FeedRemoteDataSource
│   │   └── presentation/     # FeedViewModel, FeedUiState, FeedScreen
│   └── src/commonTest/
│
├── feature-composer/          # Creazione post/thread
│   ├── src/commonMain/
│   │   ├── domain/            # PostThreadUseCase, AttachMediaUseCase
│   │   ├── data/              # ComposerRepositoryImpl
│   │   └── presentation/     # ComposerViewModel, ComposerScreen
│   └── src/commonTest/
│
├── feature-social-profile/    # Profili sociali (estende features/profile)
│   ├── src/commonMain/
│   │   ├── domain/            # GetUserProfileUseCase, FollowUserUseCase
│   │   ├── data/              # SocialProfileRepositoryImpl
│   │   └── presentation/     # SocialProfileViewModel, SocialProfileScreen
│   └── src/commonTest/
│
├── feature-search/            # Ricerca utenti e contenuti
│   ├── src/commonMain/
│   │   ├── domain/            # SearchUsersUseCase, SemanticSearchUseCase
│   │   ├── data/              # SearchRepositoryImpl
│   │   └── presentation/     # SearchViewModel, SearchScreen
│   └── src/commonTest/
│
└── feature-notifications/     # Centro notifiche
    ├── src/commonMain/
    │   ├── domain/            # GetNotificationsUseCase
    │   ├── data/              # NotificationRepositoryImpl
    │   └── presentation/     # NotificationsViewModel, NotificationsScreen
    └── src/commonTest/
```

### Fase 5.2: Feed Implementation

**MVI Contract:**
```kotlin
sealed interface FeedIntent {
    data object LoadFeed : FeedIntent
    data object RefreshFeed : FeedIntent
    data class ToggleLike(val threadId: String) : FeedIntent
    data class OpenThread(val threadId: String) : FeedIntent
    data object LoadNextPage : FeedIntent
    data class SwitchFeedSource(val source: FeedSource) : FeedIntent
}

@Immutable
data class FeedUiState(
    val threads: List<ThreadUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: UiError? = null,
    val hasMorePages: Boolean = true,
    val feedSource: FeedSource = FeedSource.ForYou,
)

enum class FeedSource { ForYou, Following, Wellness }  // Wellness = contenuti wellness della community

sealed interface FeedSideEffect {
    data class NavigateToThread(val threadId: String) : FeedSideEffect
    data class ShowError(val message: String) : FeedSideEffect
}
```

**Feed Sources:**
- **For You:** Vertex AI Vector Search (semantico) + Social Graph (2° grado)
- **Following:** Thread dai follow diretti (1° grado), ordinati cronologicamente
- **Wellness:** Feed curato di contenuti wellness dalla community (mood check-in pubblici, riflessioni condivise)

### Fase 5.3: Thread Composer

**Feature:** Composizione di thread con:
- Testo (max 10.000 caratteri)
- Media (immagini, video)
- Mood tag opzionale (integrazione con il sistema Mood di Calmify)
- Visibility (public, followers-only, private)
- "Share from Journal" — condivisione opzionale di un diary entry come thread

**Integrazione Wellness:**
Il composer offre la possibilità di trasformare un journal entry privato in un thread pubblico (anonimizzato o meno). Questo è il bridge tra la feature wellness e quella social.

### Fase 5.4: Social Profile

**Estensione del profilo esistente:**
```kotlin
data class SocialProfile(
    // Esistente (da ProfileRepository)
    val displayName: String,
    val email: String,
    val avatarUrl: String?,

    // Nuovo (Social)
    val bio: String = "",
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val threadCount: Int = 0,
    val isFollowedByMe: Boolean = false,

    // Wellness (opzionale, privacy-controlled)
    val publicMoodStreak: Int? = null,      // Mostra solo se l'utente ha abilitato
    val wellnessScore: Float? = null,        // Punteggio benessere anonimizzato
    val avatarModelUrl: String? = null,      // VRM avatar 3D come profilo
)
```

### Fase 5.5: Search & Discovery

**Dual search:**
1. **Keyword search:** Full-text su Firestore (semplice, per nomi utente/hashtag)
2. **Semantic search:** Vertex AI Vector Search per contenuti ("trova thread su ansia da esame")

### Fase 5.6: Notification Center

**Tipi di notifica:**
- Follow (nuovo follower)
- Like (like su un thread)
- Reply (risposta a un thread)
- Mention (@username in un thread)
- Wellness reminder (esistente, integrato)
- AI insight (esistente, integrato)

---

## EPICA 6 — Messaging + Real-time + Media Pipeline

**Obiettivo:** Implementare DM, real-time updates, e il media pipeline professionale.

### Fase 6.1: Feature Messaging

```
feature/
└── feature-messaging/
    ├── src/commonMain/
    │   ├── domain/
    │   │   ├── ObserveConversationsUseCase.kt
    │   │   ├── SendMessageUseCase.kt
    │   │   └── CreateConversationUseCase.kt
    │   ├── data/
    │   │   └── MessagingRepositoryImpl.kt    # Firestore real-time listener
    │   └── presentation/
    │       ├── ConversationListViewModel.kt
    │       ├── ChatRoomViewModel.kt          # Non confondere con features/chat (AI chat)
    │       ├── ConversationListScreen.kt
    │       └── ChatRoomScreen.kt
    └── src/commonTest/
```

**Distinzione critica:**
- `features/chat` = Chat con l'AI (Gemini, on-device LLM, voice) — **MANTIENE**
- `feature-messaging` = DM tra utenti (testo, media) — **NUOVO**

### Fase 6.2: Real-time Architecture

**Firestore listeners per real-time:**
```kotlin
// Esempio: observeMessages con KFire
override fun observeMessages(conversationId: String): Flow<List<Message>> =
    firestore.collection("conversations/$conversationId/messages")
        .orderBy("timestamp")
        .snapshots()          // KFire real-time listener → Flow
        .map { snapshot ->
            snapshot.documents.map { it.toObject<MessageDto>().toDomain() }
        }
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.Default)
```

**Sharded counters per like virali:**
```kotlin
// Cloud Function: incrementa un shard random per evitare hotspot
// Client: legge tutti gli shard e somma per il conteggio totale
```

### Fase 6.3: Media Pipeline

**Cloud Architecture:**
```
Upload → Cloud Storage → Transcoder API (auto-VMAF)
    → Multi-resolution output → Media CDN (edge caching)
    → Client: adaptive streaming based on network quality
```

**Azioni:**
1. Configurare Cloud Storage bucket per media sociali (separato da Firebase Storage wellness)
2. Configurare Transcoder API per video (multi-risoluzione, VMAF-optimized)
3. Configurare Media CDN per caching edge
4. Implementare `MediaUploadService` (KMP, usa Ktor per upload)
5. Implementare `MediaPlayerComponent` (Compose Multiplatform)

### Fase 6.4: Presenza e Typing Indicators

**Firestore real-time per presence:**
- User online/offline status
- "Sta scrivendo..." indicators
- Last seen timestamp

---

## EPICA 7 — Federation, AI Engine, FinOps

**Obiettivo:** Implementare le features avanzate di differenziazione (ActivityPub, AI Engine, scalabilità).

### Fase 7.1: Genkit AI Engine per il Feed

**Cosa:** Implementare un agente Genkit che modera e curera il feed.

**Pipeline AI per ogni nuovo thread:**
```
Thread creato → Genkit Agent Pipeline:
  1. Toxicity check (Gemini 2.5 Flash-Lite)
  2. Duplicate detection (embedding similarity > 0.95)
  3. Wellness sentiment analysis
  4. Mood classification
  5. Embedding generation (768-dim)
  6. Index update (Vertex AI Vector Search, streaming)
```

**Wellness-specific AI features:**
- **Mood aggregation:** Aggregazione anonima dei mood della community
- **Crisis detection:** Se un thread contiene segnali di crisi, notifica moderatori + risorse
- **Supportive responses:** AI suggerisce risposte supportive (non automatiche, suggerite)

### Fase 7.2: ActivityPub Bridge

**Cosa:** Permettere interoperabilità con Mastodon/PixelFed/Fediverse.

**Architecture:**
```
Calmify Nexus ←→ Cloud Run Worker (Kotlin) ←→ ActivityPub Protocol ←→ Mastodon/PixelFed
```

**Azioni:**
1. Creare Cloud Run service in Kotlin per ActivityPub translation
2. Implementare WebFinger discovery (`/.well-known/webfinger`)
3. Implementare ActivityPub inbox/outbox
4. Federare thread pubblici come Note ActivityPub
5. Ricevere follow/reply da istanze Mastodon

### Fase 7.3: FinOps & Autoscaling

**Spanner Autoscaling:**
- Managed Autoscaler per ridurre nodi durante la notte (40% risparmio)
- Enterprise edition per multi-region

**Cost monitoring:**
- Budget alerts via GCP Billing
- Per-feature cost tracking (Cloud Monitoring)
- Predictive scaling basato su pattern di traffico

### Fase 7.4: Monetizzazione (Futuro)

- Premium subscriptions via Google Cloud Commerce API
- Ad-free tier
- Enhanced AI features (unlimited AI chat, advanced insights)
- Custom avatar marketplace

---

## 8. Gestione della Coesistenza: Features Wellness + Social

### Il problema della coesistenza

Calmify ha features **profondamente personali** (journaling, mood tracking, AI therapy chat). Aggiungere social features non deve compromettere la privacy e l'intimità dell'esperienza wellness.

### Principi di coesistenza

1. **Privacy by default:** I dati wellness sono SEMPRE privati. L'utente sceglie ESPLICITAMENTE cosa condividere.

2. **Wellness first:** Il feed mostra prima contenuti wellness-positivi. L'AI moderatore filtra contenuti potenzialmente triggering.

3. **Separazione visiva:** La bottom navigation separa chiaramente le aree:
   ```
   [Home/Wellness] [Feed/Social] [+Compose] [AI Chat] [Profile]
   ```

4. **Opt-in social:** L'utente può usare Calmify SENZA mai attivare le features social. `ProjectConfig.SOCIAL_ENABLED` controlla la visibilità.

### Feature Flags

```kotlin
// ProjectConfig.kt
object ProjectConfig {
    // Existing
    const val KMP_READY = false

    // Social Features (Nexus)
    const val SOCIAL_ENABLED = false           // Master toggle
    const val FEED_ENABLED = false             // Feed/Threads
    const val MESSAGING_ENABLED = false        // DM tra utenti
    const val FEDERATION_ENABLED = false       // ActivityPub bridge
    const val SEMANTIC_SEARCH_ENABLED = false  // Vertex AI Vector Search
    const val MEDIA_PIPELINE_ENABLED = false   // Cloud transcoding
}
```

### Moduli che NON cambiano (o cambiano minimamente)

| Modulo | Impatto |
|--------|---------|
| `features/write` | Aggiunta opzionale "Share as Thread" button |
| `features/chat` | Nessun cambio (AI chat resta privata) |
| `features/humanoid` | Avatar riusato come profilo social |
| `features/insight` | Aggiunta dashboard community wellness |
| `features/onboarding` | Aggiunto step "Connetti con la community" |
| `features/history` | Nessun cambio |

---

## 9. Risk Assessment & Mitigazioni

| # | Rischio | Probabilità | Impatto | Mitigazione |
|---|---------|-------------|---------|-------------|
| 1 | **KFire non maturo** | Media | Alto | Fallback: community wrapper GitLive/Firebase-KMP. Interfacce astratte permettono swap |
| 2 | **Koin + Hilt conflitto** | Media | Medio | Fase di coesistenza breve. Koin Application-scoped, Hilt Activity-scoped. Test su device fisico |
| 3 | **Decompose learning curve** | Alta | Basso | Decompose ha buona documentazione. Migrazione incrementale, una feature alla volta |
| 4 | **Spanner Graph costi** | Bassa | Alto | Partire con Spanner dev instance. Autoscaler da day 1. Budget alert a $100/mese |
| 5 | **Privacy breach** | Bassa | Critico | Privacy by default. Audit di sicurezza prima del launch social. E2E encryption per DM |
| 6 | **Feature creep** | Alta | Medio | Feature flags per tutto. MVP: solo Feed + Threads + Follow. Messaging e Federation in fasi successive |
| 7 | **Performance regression** | Media | Alto | Benchmark prima/dopo ogni EPICA. Profiling Compose recomposition. Lazy loading aggressive |
| 8 | **Gemini/Vertex AI latency** | Bassa | Medio | Embedding pre-computati. Feed cache in Firestore. Fallback a chronological feed |
| 9 | **Build time explosion** | Alta | Medio | Convention plugins riducono boilerplate. Gradle build cache. Module-level incremental build |
| 10 | **Filament cross-platform** | Media | Medio | Filament supporta già iOS(Metal) e Web(WASM). Test su iOS simulator early |

---

## 10. Timeline Stimata & Milestones

> **Nota:** Le stime sono indicative. Jarvis preferisce non dare stime temporali, ma Sir le ha richieste implicitamente strutturando il documento come Q1 2026.

### Milestone Overview

| Milestone | Epiche | Deliverable |
|-----------|--------|-------------|
| **M1: KMP Foundation** | EPICA 1 | Build system KMP funzionante, core modules in commonMain |
| **M2: Architecture Ready** | EPICA 2 + 3 | MVI + Koin + Decompose su tutte le feature esistenti |
| **M3: Backend Ready** | EPICA 4 | Spanner Graph + Firestore social collections + Vertex AI pipeline |
| **M4: Social MVP** | EPICA 5 | Feed + Threads + Follow + Search funzionanti |
| **M5: Communication** | EPICA 6 | DM Messaging + Real-time + Media pipeline |
| **M6: Differentiation** | EPICA 7 | ActivityPub Federation + AI Engine + FinOps |

### Priorità di implementazione per EPICA 5 (Social MVP)

```
P0 (Must Have per MVP):
  ├── Feed (For You + Following)
  ├── Thread creation (testo)
  ├── Like/Unlike
  ├── Follow/Unfollow
  └── User profile (basico)

P1 (Should Have):
  ├── Search (keyword)
  ├── Reply to thread
  ├── Notification center
  └── Share from Journal

P2 (Nice to Have):
  ├── Semantic search (Vector)
  ├── Media in threads (foto)
  ├── Wellness feed source
  └── Mood tag su thread

P3 (Future):
  ├── Video threads
  ├── Avatar come profilo
  ├── Community mood dashboard
  └── Crisis detection AI
```

---

## 11. Decision Log (ADR)

| # | Decisione | Scelta | Alternative Valutate | Motivazione |
|---|-----------|--------|---------------------|-------------|
| ADR-001 | UI Framework | Compose Multiplatform 1.8+ | Mantenere Jetpack Compose Android-only | >90% code sharing, single codebase, aligned con Nexus vision |
| ADR-002 | DI Framework | Koin 4.x (con coesistenza Hilt transitoria) | Mantenere Hilt (Android-only), Kodein | KMP first-class, zero codegen, alignment con Nexus |
| ADR-003 | Navigation | Decompose 3.x | Voyager, Appyx, mantenere Navigation Compose | Type-safety, lifecycle KMP-native, deep linking, back stack management |
| ADR-004 | Presentation Pattern | MVI unidirezionale | Mantenere MVVM-ish | Stato predicibile, debug-friendly, composable, aligned con reactive Compose |
| ADR-005 | Firebase SDK | KFire (2026 official) | Community wrappers (GitLive, Firebase-KMP) | Official Google SDK, zero expect/actual, production-grade |
| ADR-006 | Graph Database | Spanner Graph (GQL) | Neo4j, Dgraph, Firestore-only | Strong consistency, GQL native, ScaNN integration, managed by GCP |
| ADR-007 | Feed Engine | Vertex AI Vector Search (Streaming) | Algolia, Elasticsearch, chronological-only | Semantic understanding, <5s index latency, GCP-native |
| ADR-008 | Serialization | kotlinx.serialization | Gson, Moshi | KMP native, compile-time safety, aligned con Ktor |
| ADR-009 | Local Cache | SQLDelight | Mantenere Room | KMP multiplatform, SQL-first, type-safe queries |
| ADR-010 | Networking | Ktor 3.1 + HTTP/3 | Mantenere OkHttp | Multiplatform, QUIC support, aligned con KFire |
| ADR-011 | Social-Wellness Boundary | Privacy by default, opt-in sharing | Social-first, wellness as add-on | Calmify's DNA è wellness. Social è l'estensione, non il core |
| ADR-012 | Feature Activation | Feature flags in ProjectConfig | Separate app builds | Single binary, server-controlled rollout, gradual activation |
| ADR-013 | Amper | DEFERRED | Adozione immediata | Troppo immaturo (Q1 2026). Convention plugins sufficienti |
| ADR-014 | Federation Protocol | ActivityPub | AT Protocol (Bluesky), Nostr | Più grande ecosistema (Mastodon), standard W3C, proven at scale |
| ADR-015 | Hilt Removal Timing | Dopo EPICA 3 (post Decompose) | Prima di Decompose | Decompose elimina Fragment dependency, rendendo Hilt removal più pulito |

---

## Appendice A: Confronto Struttura Modulare

```
# CALMIFY OGGI                          # CALMIFY NEXUS (Target)
CalmifyAppAndroid/                       CalmifyNexus/
├── app/                                 ├── build-logic/
├── buildSrc/                            │   └── convention/
├── core/                                ├── core/
│   ├── ui/                              │   ├── core-model/
│   └── util/                            │   ├── core-common/          (← core/util)
├── data/                                │   ├── core-network/         (NEW)
│   └── mongo/                           │   ├── core-database/        (← data/mongo)
├── features/                            │   ├── core-datastore/       (NEW)
│   ├── auth/                            │   ├── core-domain/          (NEW)
│   ├── chat/                            │   ├── core-ui/              (← core/ui)
│   ├── history/                         │   └── core-testing/         (NEW)
│   ├── home/                            ├── feature/
│   ├── humanoid/                        │   ├── feature-auth/         (← features/auth)
│   ├── insight/                         │   ├── feature-home/         (← features/home)
│   ├── onboarding/                      │   ├── feature-write/        (← features/write)
│   ├── profile/                         │   ├── feature-chat/         (← features/chat)
│   ├── settings/                        │   ├── feature-history/      (← features/history)
│   └── write/                           │   ├── feature-humanoid/     (← features/humanoid)
└── gradle/                              │   ├── feature-insight/      (← features/insight)
                                         │   ├── feature-onboarding/   (← features/onboarding)
                                         │   ├── feature-profile/      (← features/profile)
                                         │   ├── feature-settings/     (← features/settings)
                                         │   ├── feature-feed/         (NEW — Social)
                                         │   ├── feature-composer/     (NEW — Social)
                                         │   ├── feature-social-profile/ (NEW — Social)
                                         │   ├── feature-search/       (NEW — Social)
                                         │   ├── feature-messaging/    (NEW — Social)
                                         │   └── feature-notifications/ (NEW — Social)
                                         ├── platform/
                                         │   ├── platform-android/     (← app/)
                                         │   ├── platform-ios/         (NEW)
                                         │   └── platform-desktop/     (NEW)
                                         ├── app/
                                         │   ├── androidApp/
                                         │   ├── iosApp/
                                         │   └── desktopApp/
                                         └── gradle/
```

---

## Appendice B: Dipendenze tra i Nuovi Moduli

```
core-model ← (nessuna dipendenza, pure data classes)
    ↑
core-common ← core-model
    ↑
core-network ← core-common, core-model
core-database ← core-common, core-model, core-network
core-datastore ← core-common
core-domain ← core-common, core-model
core-ui ← core-common, core-model, core-domain
    ↑
feature-feed ← core-ui, core-domain, core-database
feature-composer ← core-ui, core-domain, core-database
feature-social-profile ← core-ui, core-domain, core-database
feature-search ← core-ui, core-domain, core-database
feature-messaging ← core-ui, core-domain, core-database
feature-notifications ← core-ui, core-domain, core-database
    ↑
platform-android ← ALL features, core-*
platform-ios ← ALL features, core-*
```

---

## Appendice C: `expect/actual` Necessari (Minimali)

| Capability | Interface (commonMain) | Android (actual) | iOS (actual) | Desktop (actual) |
|---|---|---|---|---|
| Push Notifications | `PushTokenProvider` | FCM | APNs via KFire | N/A (mock) |
| Biometria | `BiometricAuth` | BiometricPrompt | LAContext | N/A |
| Image Compression | `ImageCompressor` | Android Bitmap API | UIImage JPEG | Java ImageIO |
| Deep Linking | `DeepLinkHandler` | Intent Filters | Universal Links | URI handler |
| File Picker | `MediaPicker` | ActivityResult API | PHPicker | JFileChooser |
| Secure Storage | `SecureStore` | EncryptedSharedPrefs | Keychain | Java KeyStore |
| Share Sheet | `ShareHandler` | Android ShareSheet | UIActivity | Clipboard |
| Audio Engine | `NativeAudioBridge` | Oboe/AudioTrack | AVAudioEngine | javax.sound |
| 3D Rendering | `FilamentBridge` | Filament JNI (existing) | Filament C++ | Filament JVM |
| Camera | `CameraProvider` | CameraX | AVCaptureSession | N/A |

---

## Appendice D: Parole Chiave per Ricerca nel Codebase

Per Jarvis — riferimenti rapidi per navigare il codebase durante l'implementazione:

| Concetto | File/Pattern da cercare |
|----------|------------------------|
| DI modules | `*Module.kt`, `@Module`, `@InstallIn`, `startKoin` |
| Navigation | `NavGraph.kt`, `xxxRoute()`, `RootComponent`, `StackNavigation` |
| ViewModels | `*ViewModel.kt`, `@HiltViewModel`, `StateFlow`, `MutableStateFlow` |
| Repository interfaces | `core/util/repository/`, `Repository.kt` |
| Repository impl | `data/mongo/repository/Firestore*.kt` |
| Firebase | `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`, `KFire` |
| Feature flags | `ProjectConfig.kt`, `SOCIAL_ENABLED`, `KMP_READY` |
| Audio pipeline | `features/chat/audio/`, `FullDuplexAudioSession`, `SileroVadEngine` |
| Avatar/3D | `features/humanoid/`, `FilamentRenderer`, `VrmLoader` |
| Build config | `buildSrc/ProjectConfig.kt`, `libs.versions.toml` |

---

> *"Sir, il piano è redatto. Stiamo essenzialmente costruendo un Threads + Mastodon specializzato nel wellness, sopra le fondamenta di un'app di journaling con avatar AI. Ambizioso? Certamente. Impossibile? Mai con la giusta architettura."* — Jarvis
