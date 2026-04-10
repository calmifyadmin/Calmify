# Protobuf — Piano Implementazione

> **Parent task**: KMP Full Migration (Option C Hybrid)
> **Dipende da**: BACKEND_KTOR_SERVER.md (server endpoints), modelli in core/util/model/
> **Effort stimato**: 1 settimana
> **Risultato**: Comunicazione client-server 3-5x piu' compatta di JSON, type-safe, versionata
>
> ## STATUS: RE-ENGINEERED — PRONTO PER E2E TEST (2026-04-10)
>
> **Tutti i modelli Protobuf riscritti in commit `39499eb` (42 file).**
>
> ### Problemi RISOLTI:
> - [x] **17 campi nullable** → ZERO nullable. Tutti i campi hanno default non-null
> - [x] **`GenericDeltaResponse` con `List<JsonElement>`** → `List<String>` (Protobuf-safe)
> - [x] **Generici nelle wrapper** → rimossi, classi concrete tipate
> - [x] **Pattern `data: T? = null`** → `success: Boolean = false` + `data: T = T()` non-nullable
> - [x] **Client `.data?` nullable access** → tutti aggiornati a usare `.success` flag
> - [x] **DeltaApplier `decodeFromJsonElement`** → `decodeFromString`
>
> ### Pattern attuale (verificato e funzionante):
> ```kotlin
> @Serializable
> data class DiaryResponse(
>     @ProtoNumber(1) val success: Boolean = false,
>     @ProtoNumber(2) val diary: DiaryProto = DiaryProto(),
>     @ProtoNumber(3) val error: ApiError = ApiError(),
> )
> ```
> ZERO nullable, ZERO JsonElement, ZERO generici.

---

## Perche' Protobuf invece di JSON

```
JSON (ora):     {"sentiment_label":"POSITIVE","magnitude":0.85,"topics":["lavoro","stress"]}
                → 78 bytes + overhead parsing stringa

Protobuf:       [binario compatto, stesso contenuto]
                → 22 bytes + parsing zero-copy

Su rete 3G/4G lenta:
  - Home dashboard JSON:   ~12KB → 250ms su 3G
  - Home dashboard Proto:  ~3KB  → 60ms su 3G
  - Feed 20 post JSON:     ~45KB → 900ms su 3G
  - Feed 20 post Proto:    ~12KB → 240ms su 3G
```

| | JSON | Protobuf |
|---|---|---|
| **Size** | 100% | 25-35% (3-4x piu' piccolo) |
| **Parse speed** | Slow (string → object) | Fast (binary → object) |
| **Type safety** | No (stringa) | Si (schema .proto) |
| **Versionamento** | Fragile (rinomini un campo, rompi tutto) | Built-in (field numbers) |
| **Leggibilita' debug** | Si (human readable) | No (binary) — ma JSON fallback |
| **KMP support** | kotlinx.serialization | kotlinx.serialization.protobuf |

---

## Architettura

```
┌─────────────────────────────────────────┐
│  shared-models/                          │
│  (modulo Kotlin condiviso client+server) │
│                                          │
│  ├── @Serializable data classes          │
│  │   con @ProtoNumber annotations        │
│  ├── Usati sia dal client che dal server │
│  └── Single source of truth              │
└──────────────┬───────────────────────────┘
               │
    ┌──────────┴──────────┐
    ▼                     ▼
┌────────┐          ┌──────────┐
│ Client │◄─Proto──►│  Server  │
│  Ktor  │  +JSON   │  Ktor    │
│ Client │ fallback │  Server  │
└────────┘          └──────────┘
```

**Approccio**: NON usiamo file `.proto` + codegen. Usiamo `kotlinx.serialization.protobuf` con annotazioni `@ProtoNumber` sulle data class Kotlin. Stesso linguaggio, zero toolchain extra.

---

## Modelli Condivisi (shared-models)

### Setup Modulo

```kotlin
// shared-models/build.gradle.kts
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()           // Server
    androidTarget() // Client Android
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) { browser() }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }
    }
}
```

### Modelli con @ProtoNumber

```kotlin
// shared-models/src/commonMain/kotlin/com/lifo/shared/model/

@Serializable
data class DiaryProto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val userId: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val description: String = "",
    @ProtoNumber(5) val mood: Int = 3,
    @ProtoNumber(6) val dateEpoch: Long,
    @ProtoNumber(7) val images: List<String> = emptyList(),
    @ProtoNumber(8) val triggers: List<String> = emptyList(),
    @ProtoNumber(9) val bodySensations: List<String> = emptyList(),
    @ProtoNumber(10) val sentimentLabel: String? = null,
    @ProtoNumber(11) val sentimentMagnitude: Float? = null,
    @ProtoNumber(12) val cognitivePatterns: String? = null,  // JSON string for flexibility
    @ProtoNumber(13) val topics: List<String> = emptyList(),
    @ProtoNumber(14) val isBookmarked: Boolean = false,
    @ProtoNumber(15) val createdAt: Long,
    @ProtoNumber(16) val updatedAt: Long
)

@Serializable
data class ChatSessionProto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val userId: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val mood: String = "",
    @ProtoNumber(5) val aiModel: String = "gemini-2.0-flash",
    @ProtoNumber(6) val isLiveMode: Boolean = false,
    @ProtoNumber(7) val summary: String = "",
    @ProtoNumber(8) val createdAt: Long,
    @ProtoNumber(9) val lastMessageAt: Long
)

@Serializable
data class ChatMessageProto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val sessionId: String,
    @ProtoNumber(3) val content: String,
    @ProtoNumber(4) val isFromUser: Boolean,
    @ProtoNumber(5) val timestamp: Long,
    @ProtoNumber(6) val status: MessageStatusProto = MessageStatusProto.SENT
)

@Serializable
enum class MessageStatusProto {
    @ProtoNumber(0) SENDING,
    @ProtoNumber(1) SENT,
    @ProtoNumber(2) FAILED,
    @ProtoNumber(3) STREAMING
}

@Serializable
data class ThreadProto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val authorId: String,
    @ProtoNumber(3) val content: String,
    @ProtoNumber(4) val mood: String = "",
    @ProtoNumber(5) val images: List<String> = emptyList(),
    @ProtoNumber(6) val likeCount: Int = 0,
    @ProtoNumber(7) val replyCount: Int = 0,
    @ProtoNumber(8) val repostCount: Int = 0,
    @ProtoNumber(9) val isLikedByMe: Boolean = false,
    @ProtoNumber(10) val createdAt: Long
)

@Serializable
data class InsightProto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val diaryId: String,
    @ProtoNumber(3) val sentimentLabel: String,
    @ProtoNumber(4) val sentimentMagnitude: Float,
    @ProtoNumber(5) val cognitivePatterns: List<String> = emptyList(),
    @ProtoNumber(6) val topics: List<String> = emptyList(),
    @ProtoNumber(7) val summary: String = "",
    @ProtoNumber(8) val createdAt: Long
)

// Wellness entries — pattern generico
@Serializable
data class WellnessEntryProto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val userId: String,
    @ProtoNumber(3) val type: String,           // "gratitude", "energy", "sleep", etc.
    @ProtoNumber(4) val data: String,           // JSON payload (flessibile per tipo)
    @ProtoNumber(5) val createdAt: Long,
    @ProtoNumber(6) val updatedAt: Long
)

@Serializable
data class UserProfileProto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val displayName: String = "",
    @ProtoNumber(3) val photoUrl: String = "",
    @ProtoNumber(4) val bio: String = "",
    @ProtoNumber(5) val followerCount: Int = 0,
    @ProtoNumber(6) val followingCount: Int = 0,
    @ProtoNumber(7) val interests: List<String> = emptyList(),
    @ProtoNumber(8) val isVerified: Boolean = false
)
```

### API Wrappers

```kotlin
// shared-models/src/commonMain/kotlin/com/lifo/shared/api/

@Serializable
data class ApiResponse<T>(
    @ProtoNumber(1) val data: T? = null,
    @ProtoNumber(2) val error: ApiError? = null,
    @ProtoNumber(3) val meta: PaginationMeta? = null
)

@Serializable
data class ApiError(
    @ProtoNumber(1) val code: String,
    @ProtoNumber(2) val message: String
)

@Serializable
data class PaginationMeta(
    @ProtoNumber(1) val cursor: String? = null,
    @ProtoNumber(2) val hasMore: Boolean = false,
    @ProtoNumber(3) val totalCount: Int? = null
)

// Delta sync
@Serializable
data class DeltaResponse<T>(
    @ProtoNumber(1) val created: List<T> = emptyList(),
    @ProtoNumber(2) val updated: List<T> = emptyList(),
    @ProtoNumber(3) val deletedIds: List<String> = emptyList(),
    @ProtoNumber(4) val serverTime: Long
)

// Aggregati
@Serializable
data class HomeDashboardProto(
    @ProtoNumber(1) val recentDiaries: List<DiaryProto> = emptyList(),
    @ProtoNumber(2) val todayPulse: TodayPulseProto? = null,
    @ProtoNumber(3) val weeklyMood: List<DailyMoodProto> = emptyList(),
    @ProtoNumber(4) val currentStreak: Int = 0,
    @ProtoNumber(5) val achievements: List<AchievementProto> = emptyList(),
    @ProtoNumber(6) val pendingSync: Int = 0
)

@Serializable
data class TodayPulseProto(
    @ProtoNumber(1) val score: Float,
    @ProtoNumber(2) val dominantEmotion: String,
    @ProtoNumber(3) val trend: String,          // "UP", "DOWN", "STABLE"
    @ProtoNumber(4) val trendDelta: Float,
    @ProtoNumber(5) val weekSummary: String
)

@Serializable
data class DailyMoodProto(
    @ProtoNumber(1) val dateEpoch: Long,
    @ProtoNumber(2) val dayLabel: String,
    @ProtoNumber(3) val sentimentMagnitude: Float,
    @ProtoNumber(4) val dominantEmotion: String,
    @ProtoNumber(5) val diaryCount: Int
)

@Serializable
data class AchievementProto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3) val description: String,
    @ProtoNumber(4) val unlockedAt: Long? = null,
    @ProtoNumber(5) val progress: Float = 0f
)
```

---

## Content Negotiation (Client + Server)

### Server

```kotlin
// server/src/main/kotlin/com/lifo/server/plugins/Serialization.kt

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        // Protobuf preferito
        protobuf()
        // JSON come fallback (debug, browser, curl)
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        })
    }
}

// Il server risponde in base all'Accept header:
// Accept: application/protobuf → Protobuf (mobile clients)
// Accept: application/json    → JSON (browser, debug)
// Nessun header              → Protobuf (default)
```

### Client

```kotlin
// core/util/src/commonMain/kotlin/com/lifo/util/network/KtorApiClient.kt

class KtorApiClient(
    private val authProvider: AuthProvider,
    baseUrl: String
) {
    val client = HttpClient {
        install(ContentNegotiation) {
            protobuf()  // Serializza/deserializza automaticamente
        }
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(authProvider.getIdToken(), "")
                }
                refreshTokens {
                    BearerTokens(authProvider.refreshToken(), "")
                }
            }
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.ProtoBuf)
            accept(ContentType.Application.ProtoBuf)
        }
    }

    // Esempio utilizzo
    suspend inline fun <reified T> get(path: String): ApiResponse<T> {
        return client.get(path).body()
    }

    suspend inline fun <reified T, reified R> post(path: String, body: T): ApiResponse<R> {
        return client.post(path) {
            setBody(body)
        }.body()
    }
}
```

---

## Mapper Domain ↔ Proto

```kotlin
// core/util/src/commonMain/kotlin/com/lifo/util/mapper/

// I modelli Proto sono per la rete. I modelli Domain sono per la UI.
// Non inquinare i domain model con @ProtoNumber.

fun DiaryProto.toDomain(): Diary = Diary(
    id = id,
    title = title,
    description = description,
    mood = mood,
    date = Instant.fromEpochMilliseconds(dateEpoch),
    images = images,
    triggers = triggers.map { Trigger.valueOf(it) },
    // ... etc
)

fun Diary.toProto(): DiaryProto = DiaryProto(
    id = id,
    title = title,
    description = description,
    mood = mood,
    dateEpoch = date.toEpochMilliseconds(),
    images = images,
    triggers = triggers.map { it.name },
    // ... etc
)

// Pattern generico per tutti i modelli
// Domain model (UI) ←→ Proto model (rete) ←→ SQLDelight entity (cache)
```

---

## Versionamento Schema

```
REGOLA D'ORO: MAI rimuovere o rinumerare un campo @ProtoNumber

Aggiungere campo:   Aggiungi con nuovo numero → vecchi client lo ignorano
Deprecare campo:    Smetti di popolarlo → vecchi client ricevono default
Rinominare campo:   Cambia il nome Kotlin, il numero resta → zero breaking change

Esempio evoluzione:
  v1: @ProtoNumber(1) val name: String
  v2: @ProtoNumber(1) val name: String       // invariato
      @ProtoNumber(17) val nickname: String   // nuovo campo, numero 17
  v3: // name deprecato ma NON rimosso — vecchi client funzionano ancora
      @ProtoNumber(1) val name: String = ""   // default empty
      @ProtoNumber(17) val nickname: String
```

---

## Implementazione Step-by-Step

### Giorni 1-2: Setup

- [ ] Creare modulo `shared-models/` (o `shared/proto/`)
- [ ] build.gradle.kts con kotlinx-serialization-protobuf
- [ ] Aggiungere `kotlinx-serialization-protobuf` a `libs.versions.toml`
- [ ] Creare tutti i modelli Proto (Diary, Chat, Thread, Insight, Wellness, Profile)
- [ ] Creare ApiResponse, PaginationMeta, DeltaResponse, HomeDashboard
- [ ] Creare mapper Domain ↔ Proto per ogni modello
- [ ] Test: serialize → deserialize round-trip per ogni modello

### Giorni 3-4: Integrazione Server

- [ ] Server ContentNegotiation: protobuf + json fallback
- [ ] Aggiornare tutti i route per usare modelli Proto
- [ ] Verificare Accept header negotiation funziona
- [ ] Test: client Protobuf → server → Protobuf response
- [ ] Test: curl JSON → server → JSON response (fallback)

### Giorno 5: Integrazione Client

- [ ] KtorApiClient con ContentNegotiation protobuf
- [ ] Auth header automatico (Bearer token)
- [ ] Token refresh automatico
- [ ] Aggiornare repository client per usare Proto → Domain mapper
- [ ] Test end-to-end: client proto → server → Firestore → server → client proto
- [ ] Benchmark: misurare risparmio bytes vs JSON su payload reali

---

## Dipendenze

```
BACKEND_PROTOBUF.md (questo file)
    ├── modelli usati da → BACKEND_KTOR_SERVER.md (server routes)
    ├── modelli usati da → BACKEND_SYNC_ENGINE.md (sync payload)
    ├── modelli usati da → BACKEND_AI_SERVER.md (AI request/response)
    ├── mapper connettono a → core/util/model/ (domain models esistenti)
    └── shared-models modulo consumato da client + server
```

---

## Metriche di Successo

| Metrica | JSON (ora) | Protobuf (target) |
|---|---|---|
| Home dashboard payload | ~12KB | ~3KB |
| Feed 20 post | ~45KB | ~12KB |
| Singolo diary | ~800B | ~250B |
| Parse time (mobile) | ~5ms | ~0.5ms |
| Banda mensile utente medio | ~50MB | ~15MB |
