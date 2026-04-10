# Ktor Server — Piano Implementazione

> **Parent task**: KMP Full Migration (Option C Hybrid)
> **Prerequisiti**: Fase 1 KMP completata (72.5% commonMain), BACKEND_SYNC_ENGINE.md, BACKEND_PROTOBUF.md
> **Effort stimato**: 3-5 settimane
> **Risultato**: Server Kotlin che media TUTTE le operazioni client-Firestore
>
> ## STATUS: RE-ENGINEERED — PRONTO PER E2E TEST (2026-04-10)
>
> **Audit completo (30+ bug) → re-engineering completo in commit `39499eb` (42 file).**
> Tutto verificato contro il codice client Android. Build server + client passano.
>
> ### Cosa e' deployato:
> - Server su Cloud Run: `https://calmify-server-23546263069.europe-west1.run.app`
> - Firestore DB: `calmify-native` (NON `(default)` che e' Datastore Mode)
> - Health endpoint + auth funzionano
>
> ### Problemi RISOLTI (commit `39499eb`):
> - [x] 24/26 collection names → TUTTI corretti snake_case (verificati vs client)
> - [x] 17 campi Protobuf nullable → ZERO nullable, tutti con default non-null
> - [x] Blocking `.get().get()` → TUTTI wrappati in `withContext(Dispatchers.IO)`
> - [x] GDPR data export/delete → tutte 25+ collection + subcollection corrette
> - [x] Double Gemini API calls → `generateJson()` ora ritorna `GeminiResult`, singola chiamata
> - [x] Response wrapper inconsistenti → pattern `success: Boolean` su tutti gli endpoint
> - [x] Streaming senza safety settings → aggiunto `safetySettings` a stream + JSON mode
> - [x] SyncService collection map → riscritta con 22 entity, snake_case corretto
> - [x] TokenTracker collection `profiles` → corretto in `profile_settings`
> - [x] TokenTracker blocking calls → wrappati in `withContext(Dispatchers.IO)`
> - [x] Social graph flat collection → subcollection pattern (`social_graph/{uid}/following`)
> - [x] DeltaApplier `List<JsonElement>` → `List<String>` (Protobuf-safe)
> - [x] Auth checks su ogni write operation
> - [x] Batch 500 chunking su operazioni bulk
>
> ### Prossimo passo:
> 1. Deploy su Cloud Run: `cd ~/Calmify && git pull && gcloud builds submit --config=calmify-server/cloudbuild.yaml`
> 2. E2E test: flip `DIARY_REST = true` su emulatore, verificare CRUD
> 3. Se funziona, abilitare un dominio alla volta via BackendConfig
>
> ### Standard qualita':
> Seguire SEMPRE le regole NASA-level in CLAUDE.md sezione "QUALITY MANDATE".

---

## Architettura Target

```
┌─────────────────────────────────────────────────────┐
│  Calmify Client (Android / iOS / Web / Desktop)     │
│  Ktor Client + Protobuf + SQLDelight cache          │
└──────────────┬──────────────────────┬───────────────┘
               │ REST/Protobuf        │ WebSocket/SSE
               ▼                      ▼
┌─────────────────────────────────────────────────────┐
│  Ktor Server (Cloud Run)                            │
│  ├── Auth Middleware (Firebase token validation)     │
│  ├── REST API (36 repository endpoints)             │
│  ├── WebSocket hub (messaging, live chat)           │
│  ├── SSE streams (feed, notifications)              │
│  ├── AI Orchestrator (Gemini server-side)           │
│  ├── Rate Limiter (per-user, per-endpoint)          │
│  ├── Cache Layer (Redis / in-memory)                │
│  └── Audit Logger                                   │
└──────────────┬──────────────────────┬───────────────┘
               │                      │
    ┌──────────▼──────────┐  ┌───────▼────────┐
    │  Firestore          │  │  Cloud Storage  │
    │  (dati + real-time) │  │  (media)        │
    └─────────────────────┘  └────────────────┘
```

---

## Struttura Progetto Server

```
calmify-server/
├── build.gradle.kts
├── src/main/kotlin/com/lifo/server/
│   ├── Application.kt              # Entry point, plugin install
│   ├── plugins/
│   │   ├── Authentication.kt       # Firebase token validation
│   │   ├── Serialization.kt        # Protobuf + JSON fallback
│   │   ├── RateLimiting.kt         # Token bucket per-user
│   │   ├── Monitoring.kt           # Metrics, health check
│   │   └── CORS.kt                 # Web client support
│   ├── routing/
│   │   ├── DiaryRoutes.kt          # /api/v1/diaries/**
│   │   ├── ChatRoutes.kt           # /api/v1/chat/**
│   │   ├── InsightRoutes.kt        # /api/v1/insights/**
│   │   ├── ProfileRoutes.kt        # /api/v1/profile/**
│   │   ├── SocialRoutes.kt         # /api/v1/social/** (feed, threads, graph)
│   │   ├── WellnessRoutes.kt       # /api/v1/wellness/** (gratitude, energy, sleep, etc.)
│   │   ├── MediaRoutes.kt          # /api/v1/media/** (upload, delete)
│   │   ├── NotificationRoutes.kt   # /api/v1/notifications/**
│   │   ├── SubscriptionRoutes.kt   # /api/v1/subscription/**
│   │   ├── AdminRoutes.kt          # /api/v1/admin/** (feature flags, moderation)
│   │   └── RealtimeRoutes.kt       # WebSocket + SSE endpoints
│   ├── service/
│   │   ├── DiaryService.kt         # Business logic + Firestore
│   │   ├── ChatService.kt
│   │   ├── InsightService.kt
│   │   ├── ProfileService.kt
│   │   ├── SocialService.kt
│   │   ├── WellnessService.kt      # Aggregates: gratitude, energy, sleep, habits, etc.
│   │   ├── MediaService.kt
│   │   ├── NotificationService.kt
│   │   ├── FeedService.kt          # For-you ranking, following feed
│   │   ├── SearchService.kt        # Keyword + semantic search
│   │   └── ModerationService.kt    # Content moderation
│   ├── ai/                         # → Vedi BACKEND_AI_SERVER.md
│   │   ├── AiOrchestrator.kt
│   │   ├── PromptRegistry.kt
│   │   └── ResponseCache.kt
│   ├── realtime/
│   │   ├── WebSocketManager.kt     # Connection registry, broadcast
│   │   ├── MessagingChannel.kt     # 1-to-1 messaging real-time
│   │   ├── FeedChannel.kt          # Feed updates via SSE
│   │   └── PresenceTracker.kt      # Heartbeat → online/offline
│   ├── firebase/
│   │   ├── FirebaseAdmin.kt        # Firebase Admin SDK init
│   │   ├── FirestoreClient.kt      # Server-side Firestore operations
│   │   └── StorageClient.kt        # Cloud Storage server-side
│   ├── cache/
│   │   ├── CacheManager.kt         # In-memory + optional Redis
│   │   └── CacheKeys.kt            # Key patterns per entity
│   ├── security/
│   │   ├── TokenValidator.kt       # Firebase JWT validation
│   │   ├── RateLimiter.kt          # Token bucket implementation
│   │   ├── AuditLogger.kt          # Log ogni operazione sensibile
│   │   └── InputSanitizer.kt       # XSS, injection prevention
│   └── model/
│       ├── ApiResponse.kt          # Wrapper standard {data, error, meta}
│       ├── PaginationParams.kt     # cursor, limit, direction
│       └── ErrorCodes.kt           # Codici errore standard
├── src/test/kotlin/
│   ├── routes/                     # Test per ogni route group
│   ├── services/                   # Test business logic
│   └── TestApplication.kt         # Setup test con Firestore emulator
├── Dockerfile
├── cloudbuild.yaml                 # CI/CD per Cloud Run
└── firebase-admin-key.json         # Service account (NOT in git)
```

---

## Fase 1: Bootstrap (3-4 giorni)

### 1.1 Progetto Ktor Server

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("io.ktor.plugin") version "3.1.1"
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-sse")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-rate-limit")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-call-logging")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-serialization-kotlinx-protobuf")

    // Firebase Admin
    implementation("com.google.firebase:firebase-admin:9.4.2")

    // Cache
    implementation("io.github.reactivecircus.cache4k:cache4k:0.13.0")

    // DI
    implementation("io.insert-koin:koin-ktor:4.1.1")

    // Test
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
```

### 1.2 Application.kt + Auth Middleware

```kotlin
// Application.kt
fun main() {
    embeddedServer(Netty, port = 8080) {
        configureAuthentication()   // Firebase JWT
        configureSerialization()    // Protobuf + JSON
        configureRateLimiting()     // Per-user limits
        configureCORS()             // Web client
        configureMonitoring()       // Health + metrics
        configureRouting()          // All routes
    }.start(wait = true)
}

// Authentication.kt
fun Application.configureAuthentication() {
    install(Authentication) {
        bearer("firebase") {
            authenticate { tokenCredential ->
                val decoded = FirebaseAuth.getInstance()
                    .verifyIdToken(tokenCredential.token)
                UserPrincipal(uid = decoded.uid, email = decoded.email)
            }
        }
    }
}
```

### 1.3 Dockerfile + Cloud Run

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY build/libs/calmify-server-all.jar /app/server.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/server.jar"]
```

### Checklist Fase 1:
- [ ] Creare repo `calmify-server/` (o modulo nel monorepo)
- [ ] Setup Ktor con Netty, plugins base
- [ ] Firebase Admin SDK init con service account
- [ ] Auth middleware: validate Firebase JWT → UserPrincipal
- [ ] Health check endpoint: `GET /health`
- [ ] Dockerfile + deploy su Cloud Run (staging)
- [ ] Test: chiamata autenticata end-to-end
- [ ] Rate limiter base (100 req/min per user)

---

## Fase 2: CRUD Endpoints — Batch 1 (1 settimana)

### Mapping Repository → Route

Mappare i 36 repository esistenti in gruppi di route. Pattern standard per ogni CRUD:

```kotlin
// Pattern per ogni route group
fun Route.diaryRoutes() {
    authenticate("firebase") {
        route("/api/v1/diaries") {
            // List with pagination
            get {
                val user = call.principal<UserPrincipal>()!!
                val cursor = call.parameters["cursor"]
                val limit = call.parameters["limit"]?.toInt() ?: 20
                val result = diaryService.getDiaries(user.uid, cursor, limit)
                call.respond(ApiResponse.success(result))
            }
            // Get single
            get("/{id}") { ... }
            // Create
            post { ... }
            // Update
            put("/{id}") { ... }
            // Delete
            delete("/{id}") { ... }
        }
    }
}
```

### Batch 1 — Core (15 endpoints)

| Route Group | Repository | Endpoints | Note |
|---|---|---|---|
| `/api/v1/diaries` | MongoRepository | CRUD + filter by date | Piu' usato |
| `/api/v1/diaries/{id}/insight` | InsightRepository | GET + POST feedback | AI-generated |
| `/api/v1/chat/sessions` | ChatRepository | CRUD sessions | |
| `/api/v1/chat/sessions/{id}/messages` | ChatRepository | List + send | Stream per AI |
| `/api/v1/profile` | ProfileRepository | GET + update | |
| `/api/v1/profile/settings` | ProfileSettingsRepository | GET + update | |
| `/api/v1/notifications` | NotificationRepository | List + mark read | |
| `/api/v1/media/upload` | MediaUploadRepository | POST multipart | Cloud Storage |
| `/api/v1/media/{id}` | MediaUploadRepository | DELETE | |
| `/api/v1/avatar` | AvatarRepository | CRUD | |
| `/api/v1/subscription` | SubscriptionRepository | GET status | Read-only server-side |
| `/api/v1/feature-flags` | FeatureFlagRepository | GET all | Cached aggressivamente |
| `/api/v1/search` | SearchRepository | GET query | Vertex AI Vector Search |
| `/api/v1/moderation/check` | ContentModerationRepository | POST text | Pre-publish |
| `/api/v1/waitlist` | WaitlistRepository | POST join | |

### Checklist Fase 2:
- [ ] DiaryService + DiaryRoutes (CRUD + date filter + pagination)
- [ ] ChatService + ChatRoutes (sessions + messages)
- [ ] InsightService + InsightRoutes (get + feedback)
- [ ] ProfileService + ProfileRoutes (profile + settings)
- [ ] NotificationService + NotificationRoutes
- [ ] MediaService + MediaRoutes (upload via Cloud Storage signed URL)
- [ ] AvatarService + AvatarRoutes
- [ ] SearchService + SearchRoutes
- [ ] ModerationService + ModerationRoutes
- [ ] FeatureFlagService (cached 5 min)
- [ ] Test per ogni route group (almeno happy path)
- [ ] Pagination standard: cursor-based, limit param

---

## Fase 3: CRUD Endpoints — Batch 2 Wellness (4 giorni)

### Wellness Routes (14 repository → 1 route group)

```kotlin
fun Route.wellnessRoutes() {
    authenticate("firebase") {
        route("/api/v1/wellness") {
            route("/gratitude")   { crudRoutes(gratitudeService) }
            route("/energy")      { crudRoutes(energyService) }
            route("/sleep")       { crudRoutes(sleepService) }
            route("/habits")      { crudRoutes(habitService) }
            route("/meditation")  { crudRoutes(meditationService) }
            route("/movement")    { crudRoutes(movementService) }
            route("/values")      { crudRoutes(valuesService) }
            route("/ikigai")      { crudRoutes(ikigaiService) }
            route("/awe")         { crudRoutes(aweService) }
            route("/reframe")     { crudRoutes(reframeService) }
            route("/recurring")   { crudRoutes(recurringThoughtService) }
            route("/connection")  { crudRoutes(connectionService) }
            route("/environment") { crudRoutes(environmentService) }
            route("/garden")      { crudRoutes(gardenService) }
        }
        // Aggregated dashboard
        get("/api/v1/home/dashboard") {
            // UNA chiamata = diaries + mood + pulse + achievements + weekly insights
            val dashboard = dashboardService.getHomeDashboard(user.uid)
            call.respond(ApiResponse.success(dashboard))
        }
    }
}
```

### Endpoint Aggregati (il vero valore del server)

| Endpoint | Aggrega | Vantaggio |
|---|---|---|
| `GET /api/v1/home/dashboard` | diaries + mood + pulse + streaks + achievements | 1 call invece di 5 |
| `GET /api/v1/wellness/weekly-summary` | tutte le wellness entries della settimana | 1 call invece di 14 |
| `GET /api/v1/profile/complete` | profile + settings + subscription + stats | 1 call invece di 4 |
| `GET /api/v1/insights/trends` | mood distribution + cognitive patterns + topics | 1 call invece di 3 |

### Checklist Fase 3:
- [ ] Generic CRUD route builder (DRY per i 14 wellness repos)
- [ ] Ogni wellness service wrappa il rispettivo Firestore collection
- [ ] Dashboard aggregato endpoint
- [ ] Weekly summary endpoint
- [ ] Complete profile endpoint
- [ ] Insight trends endpoint
- [ ] Cache per aggregati (TTL 2-5 min)
- [ ] Test

---

## Fase 4: Social Endpoints (4 giorni)

| Route Group | Repository | Tipo |
|---|---|---|
| `/api/v1/feed/for-you` | FeedRepository | GET paginated (ranking server-side) |
| `/api/v1/feed/following` | FeedRepository | GET paginated (cronologico) |
| `/api/v1/threads` | ThreadRepository | CRUD + like/unlike/repost |
| `/api/v1/threads/{id}/replies` | ThreadRepository | GET paginated |
| `/api/v1/social/graph` | SocialGraphRepository | follow/unfollow/block |
| `/api/v1/social/graph/suggestions` | SocialGraphRepository | GET (amici suggeriti) |
| `/api/v1/users/{id}/profile` | ProfileRepository | GET (profilo altrui) |
| `/api/v1/users/delete-all-data` | MongoRepository | DELETE (GDPR) |

### Checklist Fase 4:
- [ ] FeedService (for-you ranking: sentiment similarity + recency + engagement)
- [ ] ThreadService (CRUD + engagement actions)
- [ ] SocialGraphService (follow/unfollow + suggestions algorithm)
- [ ] GDPR delete endpoint (cancella TUTTO: Firestore + Storage + cache)
- [ ] Content moderation pre-publish su threads
- [ ] Test

---

## Fase 5: Real-time (3-4 giorni)

### WebSocket — Messaging

```kotlin
fun Route.realtimeRoutes() {
    authenticate("firebase") {
        // 1-to-1 messaging
        webSocket("/ws/messaging") {
            val user = call.principal<UserPrincipal>()!!
            webSocketManager.register(user.uid, this)
            try {
                for (frame in incoming) {
                    val message = frame.toMessage()
                    messagingService.send(user.uid, message)
                    // Forward to recipient if online
                    webSocketManager.sendTo(message.recipientId, message)
                }
            } finally {
                webSocketManager.unregister(user.uid)
            }
        }
    }
}
```

### SSE — Feed + Notifications

```kotlin
// Feed updates
sse("/sse/feed/{userId}") {
    val userId = call.parameters["userId"]!!
    feedService.observeNewPosts(userId).collect { post ->
        send(SseEvent(data = json.encodeToString(post)))
    }
}

// Notifications
sse("/sse/notifications/{userId}") {
    notificationService.observe(userId).collect { notification ->
        send(SseEvent(data = json.encodeToString(notification)))
    }
}
```

### Presence — Heartbeat REST

```kotlin
post("/api/v1/presence/heartbeat") {
    val user = call.principal<UserPrincipal>()!!
    presenceTracker.heartbeat(user.uid) // sets online, auto-offline after 60s
    call.respond(HttpStatusCode.NoContent)
}
```

### Checklist Fase 5:
- [ ] WebSocketManager (connection registry, send-to-user)
- [ ] MessagingChannel (WebSocket per DMs)
- [ ] FeedChannel (SSE per nuovi post)
- [ ] NotificationChannel (SSE)
- [ ] PresenceTracker (heartbeat + TTL 60s)
- [ ] Reconnection handling (client-side token refresh)
- [ ] Test con multiple client simulati

---

## Fase 6: Security Hardening (2 giorni)

### Checklist:
- [ ] Input sanitization su tutti gli endpoint (XSS, SQL injection even if NoSQL)
- [ ] Rate limiting differenziato (write: 30/min, read: 100/min, search: 20/min)
- [ ] Request size limits (body max 5MB, upload max 20MB)
- [ ] Certificate pinning config per client
- [ ] Audit log: chi ha fatto cosa, quando, da quale IP
- [ ] GDPR: data export endpoint (`GET /api/v1/users/me/export`)
- [ ] GDPR: data delete endpoint (`DELETE /api/v1/users/me`)
- [ ] Error responses: mai esporre stack trace, sempre codici standard
- [ ] Health check non-authenticated (`GET /health`)
- [ ] Graceful shutdown (drain connections before stop)

---

## Fase 7: Client Migration (3-4 giorni)

### Pattern: Repository Interface resta, implementation cambia

```kotlin
// core/util/src/commonMain — INVARIATO
interface DiaryRepository {
    fun getDiaries(userId: String): Flow<RequestState<List<Diary>>>
    suspend fun createDiary(diary: Diary): RequestState<Diary>
    suspend fun deleteDiary(id: String): RequestState<Unit>
}

// data/network/src/commonMain — NUOVO (sostituisce data/mongo per CRUD)
class KtorDiaryRepository(
    private val client: HttpClient,
    private val cache: LocalCacheManager  // SQLDelight
) : DiaryRepository {

    override fun getDiaries(userId: String) = flow {
        // 1. Emit cached first
        emit(RequestState.Success(cache.getDiaries()))
        // 2. Fetch from server
        val response = client.get("/api/v1/diaries")
        val diaries = response.body<List<Diary>>()
        cache.saveDiaries(diaries)
        emit(RequestState.Success(diaries))
    }
}
```

### Checklist Client Migration:
- [ ] Creare modulo `data/network/` con plugin `calmify.kmp.library`
- [ ] KtorApiClient wrapper (base URL, auth header, error handling)
- [ ] Migrare repository uno alla volta (interface resta, impl cambia)
- [ ] Koin: swap `FirestoreDiaryRepository` → `KtorDiaryRepository`
- [ ] SQLDelight cache layer (vedi BACKEND_SYNC_ENGINE.md)
- [ ] Test end-to-end: client → server → Firestore → response

---

## Dipendenze tra file

```
BACKEND_KTOR_SERVER.md (questo file)
    ├── usa modelli Protobuf da → BACKEND_PROTOBUF.md
    ├── AI endpoints delegati a → BACKEND_AI_SERVER.md
    ├── Sync/cache client usa → BACKEND_SYNC_ENGINE.md
    └── prerequisito: Fase 1 KMP da → KMP_MIGRATION_STATUS.md
```

---

## Metriche di Successo

| Metrica | Target |
|---|---|
| Latenza P50 (CRUD) | < 150ms |
| Latenza P50 (aggregati) | < 200ms (vs 500ms+ con 5 query client) |
| Uptime | 99.9% |
| Cold start (Cloud Run) | < 3s |
| Test coverage server | > 80% |
| Zero credenziali nel client APK | Si |
| GDPR compliance | Export + Delete funzionanti |
