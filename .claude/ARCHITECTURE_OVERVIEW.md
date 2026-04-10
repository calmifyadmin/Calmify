# Calmify — Architettura Sistema Completa

> Documento per il team di sistemistica. Aggiornato: 2026-04-10

---

## 1. Vista d'Insieme

```
+------------------------------------------------------+
|                    CLIENT (KMP)                       |
|                                                       |
|  +---------------+  +---------------+  +----------+   |
|  | Android (APK) |  | iOS (future)  |  | Web (f.) |   |
|  +-------+-------+  +-------+-------+  +----+-----+   |
|          |                  |                |          |
|          +------------------+----------------+          |
|                        |                               |
|               Kotlin Multiplatform                     |
|            (shared business logic)                     |
+------------------------+------------------------------+
                         |
              HTTPS / Bearer Token
          Protobuf (primary) + JSON (fallback)
                         |
+------------------------v------------------------------+
|              KTOR SERVER (Cloud Run)                   |
|                                                       |
|  Port: 8080  |  Region: europe-west1                  |
|  Engine: Netty  |  Runtime: JDK 17                    |
|                                                       |
|  +----------+  +---------+  +----------+  +--------+  |
|  | Firebase  |  | Rate    |  | Content  |  | Audit  |  |
|  | Auth JWT  |  | Limit   |  | Filter   |  | Log    |  |
|  +----------+  +---------+  +----------+  +--------+  |
|                                                       |
|  +--------------------------------------------------+ |
|  |            12 Route Files (~40+ endpoints)        | |
|  |  diary | chat | wellness | ai | sync | gdpr ...   | |
|  +--------------------------------------------------+ |
+------------------------+------------------------------+
                         |
             Firebase Admin SDK 9.4.2
                         |
+------------------------v------------------------------+
|                  GOOGLE CLOUD                          |
|                                                       |
|  +------------------+     +------------------------+  |
|  | Firestore        |     | Gemini API             |  |
|  | DB: calmify-     |     | (AI chat, insights,    |  |
|  |     native       |     |  text analysis)        |  |
|  | 32+ collections  |     | Model: Flash 2.0       |  |
|  +------------------+     +------------------------+  |
|                                                       |
|  +------------------+     +------------------------+  |
|  | Cloud Storage    |     | Firebase Auth          |  |
|  | (media uploads)  |     | (identity provider)    |  |
|  +------------------+     +------------------------+  |
+-------------------------------------------------------+
```

---

## 2. Flusso di una Richiesta (Request Lifecycle)

```
  Client App                   Cloud Run                      GCP
  =========                    =========                      ===

  1. User action
       |
  2. ViewModel → Repository
       |
  3. KtorApiClient.post()
       |
       |  HTTP POST /api/v1/diary
       |  Authorization: Bearer {firebaseIdToken}
       |  Content-Type: application/x-protobuf
       |  Body: [binary protobuf]
       |
       +------- HTTPS (TLS 1.3) ------->  4. Netty receives
                                               |
                                           5. Security pipeline:
                                              a. Request ID (UUID)
                                              b. HTTPS check
                                              c. Body size limit (1MB)
                                              d. Rate limit check
                                              e. CORS headers
                                               |
                                           6. Auth pipeline:
                                              Firebase.verifyIdToken()
                                              Extract userId
                                               |
                                           7. Content negotiation:
                                              Protobuf → deserialize
                                               |
                                           8. Route handler:
                                              DiaryService.create()
                                               |
                                               +------ withContext(IO) ----->  9. Firestore
                                               |                                 write
                                               |<-------- document ID ------  10. Response
                                               |
                                          11. Serialize response
                                              (Protobuf + success flag)
                                               |
       <------ HTTP 201 ----------------  12. Send response
       |
  13. Deserialize Proto
       |
  14. RequestState.Success(data)
       |
  15. StateFlow update → UI
```

---

## 3. Autenticazione

```
+-------------------+        +------------------+       +------------------+
|   Client App      |        |   Ktor Server    |       |  Firebase Auth   |
+-------------------+        +------------------+       +------------------+
        |                            |                          |
   [1] Login (email/Google)          |                          |
        +-------------------------------------------------------->
        |                            |                          |
        <------ Firebase ID Token (JWT, 1h TTL) ---------------+
        |                            |                          |
   [2] API call                      |                          |
        |  Authorization: Bearer     |                          |
        |  {idToken}                 |                          |
        +--------------------------->|                          |
        |                    [3] verifyIdToken()                |
        |                            +------------------------->
        |                            |<--- uid, email ---------+
        |                    [4] UserPrincipal(uid)             |
        |                            |                          |
        |                    [5] Route handler                  |
        |                       uses userId                     |
        |<--- 200 OK + data --------+                          |
        |                            |                          |
   [6] If 401:                       |                          |
        |  getIdToken(forceRefresh)  |                          |
        +-------------------------------------------------------->
        |<------ new token -----------------------------------------+
        |  retry original request    |                          |
        +--------------------------->|                          |
```

**Token lifecycle:**
- **Emesso da**: Firebase Auth (Google Identity Platform)
- **Formato**: JWT (RS256)
- **TTL**: 1 ora
- **Refresh**: automatico sul client (401 trigger)
- **Validazione server**: `FirebaseAuth.getInstance().verifyIdToken(token)`

---

## 4. Serializzazione Client ↔ Server

```
                    +-------------------+
                    |  shared/models    |
                    |  (KMP module)     |
                    +-------------------+
                    | @Serializable     |
                    | @ProtoNumber(1)   |
                    | data class        |
                    | DiaryProto(...)   |
                    +--------+----------+
                             |
              +--------------+--------------+
              |                             |
     +--------v--------+          +--------v--------+
     |  Client (KMP)   |          |  Server (JVM)   |
     +-----------------+          +-----------------+
     | Ktor HttpClient |          | Ktor Netty      |
     | ContentNeg:     |          | ContentNeg:     |
     |  1. Protobuf    |          |  1. Protobuf    |
     |  2. JSON        |          |  2. JSON        |
     +-----------------+          +-----------------+

  Protobuf (default):
  - Content-Type: application/x-protobuf
  - 3-5x smaller than JSON
  - Binary, non human-readable
  - ZERO nullable fields (crashes otherwise)
  - ZERO JsonElement, ZERO generics

  JSON (fallback/debug):
  - Content-Type: application/json
  - Human-readable, browser-friendly
  - Used for debugging + web clients
```

**Regole Protobuf inviolabili:**
- Ogni campo **NON-nullable** con valore default
- Zero `T? = null`, zero `JsonElement`, zero `ApiResponse<T>`
- Pattern response: `success: Boolean = false` + `data: T = T()` + `error: ApiError = ApiError()`

---

## 5. Struttura API Endpoints

```
/health                          [GET]  No auth — liveness probe
  |
/api/v1/                         Requires Firebase Bearer Token
  |
  +-- /diary                     Diary CRUD
  |     +-- GET    /             List diaries (paginated)
  |     +-- GET    /{id}         Get single diary
  |     +-- POST   /             Create diary
  |     +-- PUT    /{id}         Update diary
  |     +-- DELETE /{id}         Delete diary
  |
  +-- /chat                      Chat Sessions
  |     +-- GET    /sessions     List sessions
  |     +-- POST   /sessions     Create session
  |     +-- GET    /sessions/{id}/messages
  |     +-- POST   /sessions/{id}/messages
  |     +-- POST   /sessions/{id}/export   Export to diary
  |
  +-- /insights                  AI-Generated Insights
  |     +-- GET    /{diaryId}    Get insight for diary
  |
  +-- /profile                   User Profile
  |     +-- GET    /             Get profile
  |     +-- PUT    /             Update profile
  |
  +-- /dashboard                 Aggregated Home Data
  |     +-- GET    /             Today pulse + streak + habits
  |
  +-- /social                    Social Features
  |     +-- /threads             Thread CRUD + likes
  |     +-- /feed                Curated feed
  |     +-- /follow              Follow/unfollow
  |     +-- /graph               Social graph queries
  |
  +-- /notifications             User Notifications
  |     +-- GET    /             List notifications
  |     +-- PUT    /{id}/read    Mark as read
  |
  +-- /wellness/{type}           13 Wellness Entity Types
  |     +-- GET    /             List by type
  |     +-- POST   /             Create
  |     +-- PUT    /{id}         Update
  |     +-- DELETE /{id}         Delete
  |     Types: gratitude, energy, sleep, meditation,
  |            habits, movement, reframe, wellbeing,
  |            awe, connection, recurring, block, values
  |
  +-- /ai                        AI Pipeline
  |     +-- POST   /chat         Non-streaming chat
  |     +-- POST   /chat/stream  SSE streaming chat
  |     +-- POST   /insight      Generate diary insight
  |     +-- POST   /analyze      Text sentiment analysis
  |     +-- GET    /usage        Token usage stats
  |
  +-- /sync                      Offline-First Sync
  |     +-- POST   /batch        Push local changes
  |     +-- GET    /changes      Pull server deltas
  |
  +-- /gdpr                      GDPR Compliance
        +-- GET    /export       Data export (Art. 20)
        +-- DELETE /delete       Account deletion (Art. 17)
```

---

## 6. Database: Firestore

```
Progetto GCP: calmify-388723
Database: calmify-native (named, NOT default)

  calmify-native/
  |
  +-- diaries/{docId}                    Diary entries
  +-- chat_sessions/{docId}              Chat sessions
  +-- chat_messages/{docId}              Chat messages
  +-- diary_insights/{docId}             AI insights
  +-- profile_settings/{userId}          User profiles
  +-- psychological_profiles/{userId}    Psych profiles
  |
  +-- threads/{docId}                    Social threads
  |     +-- /likes/{userId}              Thread likes (subcollection)
  |
  +-- social_graph/{userId}              Social graph root
  |     +-- /following/{targetId}        Following (subcollection)
  |     +-- /followers/{followerId}      Followers (subcollection)
  |
  +-- notifications/{docId}              User notifications
  +-- feed_items/{docId}                 Curated feed
  |
  +-- gratitude_entries/{docId}          Gratitude journal
  +-- energy_check_ins/{docId}           Energy check-ins
  +-- sleep_logs/{docId}                 Sleep tracking
  +-- meditation_sessions/{docId}        Meditation
  +-- habits/{docId}                     Habit definitions
  +-- habit_completions/{docId}          Habit completions
  +-- movement_logs/{docId}              Exercise/movement
  +-- thought_reframes/{docId}           Cognitive reframes
  +-- wellbeing_snapshots/{docId}        Wellbeing scores
  +-- awe_entries/{docId}                Awe experiences
  +-- connection_entries/{docId}         Social connections
  +-- recurring_thoughts/{docId}         Recurring thoughts
  +-- blocks/{docId}                     Mental blocks
  +-- values_discovery/{docId}           Values exercises
  +-- garden_items/{docId}               Wellness garden
  |
  +-- ai_usage/{userId}                  AI token tracking
  |     +-- /daily/{YYYY-MM-DD}          Daily counters
  |     +-- /monthly/{YYYY-MM}           Monthly counters
  |
  +-- config/flags                       Feature flags (single doc)
  +-- presence/{userId}                  Online presence

  REGOLE:
  - Ogni document ha un campo owner (ownerId, userId, o authorId)
  - Ogni query FILTRA per owner (zero IDOR)
  - Nomi collection: SEMPRE snake_case
  - Batch operations: max 500 per batch
  - Tutte le date: Long (millis epoch) oppure Firestore Timestamp
```

---

## 7. Sync Engine (Offline-First)

```
  +------------------+                   +------------------+
  |   SQLDelight     |                   |   Ktor Server    |
  |   (locale DB)    |                   |   (Cloud Run)    |
  +--------+---------+                   +--------+---------+
           |                                      |
   [WRITE] |                                      |
   --------+                                      |
   1. UI action                                   |
   2. Write to SQLDelight (0ms)                   |
   3. Enqueue sync operation                      |
   4. UI updates instantly via Flow               |
           |                                      |
   [PUSH]  | (when online)                        |
   --------+                                      |
   5. SyncEngine drains queue                     |
   6. POST /api/v1/sync/batch    +--------------->|
      [{ entityType, entityId,   |                |
         operation, payload }]   |                | 7. Validate auth
                                 |                | 8. Apply to Firestore
   9. Mark as COMPLETED <--------+                |
      or retry with backoff                       |
           |                                      |
   [PULL]  | (every 5 min + on reconnect)         |
   --------+                                      |
  10. GET /sync/changes          +--------------->|
      ?entity=DIARY&since=T      |                | 11. Query updatedAt > T
                                 |                |
  12. DeltaApplier <-------------+  { created: [],|
      routes to correct repo        updated: [],  |
  13. Upsert into SQLDelight        deleted: [] } |
           |                                      |
   [CONFLICT]                                     |
   ----------                                     |
   ConflictResolver: Last-Write-Wins (LWW)        |
   - Field-level merge when same timestamp        |
   - DELETE always wins (tombstone)               |
           |                                      |
   [RETRY]                                        |
   --------                                       |
   Exponential backoff: 1s, 2s, 4s, 8s, 16s      |
   Max 5 attempts per operation                   |
```

---

## 8. AI Pipeline

```
  Client                    Ktor Server                    Google AI
  ======                    ===========                    =========

  POST /ai/chat
  { message, context }
        |
        +----------------> 1. ContentFilter
        |                     - Max 10K chars
        |                     - Injection detection
        |                     - Prompt extraction block
        |
        |                  2. TokenTracker.checkQuota()
        |                     - Free:    10K tokens/day
        |                     - Premium: 100K tokens/day
        |                     Collection: ai_usage/{uid}
        |
        |                  3. ResponseCache check
        |                     - In-memory (Cache4K)
        |                     - Chat: 1h TTL
        |                     - Insight: 24h TTL
        |
        |                  4. ModelRouter
        |                     - Free  → gemini-2.0-flash
        |                     - Premium → gemini-2.0-pro
        |
        |                  5. PromptRegistry
        |                     - Versioned templates
        |                     - System instruction + user
        |
        |                  6. GeminiClient.generate()
        |                     Single call (NEVER double)  -------->  Gemini API
        |                     Safety: BLOCK_ONLY_HIGH     <--------  Response
        |
        |                  7. ContentFilter.processOutput()
        |                     - Handle blocked responses
        |                     - Graceful fallback message
        |
        |                  8. TokenTracker.record()
        |                     (withContext IO)
        |
        |                  9. ResponseCache.put()
        |
  <---- { success: true,
           message: "...",
           tokensUsed: 450 }

  STREAMING (SSE):
  POST /ai/chat/stream  →  Server-Sent Events
  - Same pipeline (filter, quota, model)
  - Chunks emitted as they arrive
  - data: {"text": "chunk..."}
  - Safety settings applied to stream too
```

---

## 9. Rate Limiting

```
  Tier        Limit           Refill      Applied to
  ====        =====           ======      ==========
  default     100 req/min     1 min       GET endpoints
  write        30 req/min     1 min       POST, PUT, DELETE
  search       20 req/min     1 min       /search, /feed
  ai           10 req/min     1 min       /ai/* (expensive)
  health       60 req/30s     30 sec      /health (no auth)

  Key: userId (authenticated) oppure IP (unauthenticated)

  Risposta quando superato:
  HTTP 429 Too Many Requests
  Retry-After: {seconds}
```

---

## 10. Security Headers

```
  Header                          Valore
  ======                          ======
  Strict-Transport-Security       max-age=31536000; includeSubDomains; preload
  X-Content-Type-Options          nosniff
  X-Frame-Options                 DENY
  Content-Security-Policy         default-src 'none'; frame-ancestors 'none'
  Referrer-Policy                 strict-origin-when-cross-origin
  Permissions-Policy              camera=(), microphone=(), geolocation=()
  X-Request-Id                    {UUID per request}
  X-Server                        calmify-ktor

  Body Size Limits:
  - AI endpoints:    256 KB
  - Sync batch:      512 KB
  - Default:         1 MB
```

---

## 11. Deploy Pipeline

```
  Developer Workstation              GitHub              Cloud Shell / Cloud Build
  =====================              ======              ========================

  1. git commit
  2. git push origin
     backend-architecture-refactor
        |
        +----> Repository updated
                    |
                    |   (da Cloud Shell)
                    |
                    +----> cd ~/Calmify
                    |      git pull
                    |
                    +----> gcloud builds submit \
                           --config=calmify-server/cloudbuild.yaml \
                           --project=calmify-388723
                                |
                                v
                    +----> Step 1: Docker build
                    |      - Gradle 8.13 + JDK17
                    |      - ./gradlew :calmify-server:buildFatJar
                    |      - Runtime: temurin:17-jre-jammy
                    |
                    +----> Step 2: Push to GCR
                    |      gcr.io/calmify-388723/calmify-server:latest
                    |
                    +----> Step 3: Deploy to Cloud Run
                           - Region: europe-west1
                           - Memory: 512 Mi
                           - CPU: 1
                           - Min instances: 0
                           - Max instances: 10
                           - Port: 8080
                           - Secret: GEMINI_API_KEY
                           - SA: calmify-server@...

  URL produzione:
  https://calmify-server-23546263069.europe-west1.run.app
```

---

## 12. Infrastruttura GCP

```
  Progetto: calmify-388723
  |
  +-- Cloud Run
  |     +-- calmify-server (europe-west1)
  |           - 0-10 instances (auto-scale)
  |           - 512Mi RAM, 1 vCPU
  |           - G1GC, 75% heap
  |           - HTTPS (managed TLS)
  |
  +-- Firestore
  |     +-- (default) — Datastore Mode (NON USARE)
  |     +-- calmify-native — Native Mode (USARE QUESTO)
  |
  +-- Firebase Auth
  |     +-- Email/password
  |     +-- Google Sign-In
  |     +-- Anonymous (upgrade later)
  |
  +-- Cloud Storage
  |     +-- Media uploads (immagini diary)
  |
  +-- Secret Manager
  |     +-- GEMINI_API_KEY
  |
  +-- Container Registry (GCR)
  |     +-- calmify-server:latest
  |
  +-- Cloud Build
  |     +-- cloudbuild.yaml trigger
  |
  +-- IAM
        +-- calmify-server@...iam.gserviceaccount.com
              Roles: Firestore User, Secret Accessor
```

---

## 13. Monitoraggio e Logging

```
  Fonte                 Destinazione           Formato
  =====                 ============           =======
  Ktor CallLogging      Cloud Logging          INFO: method path status duration
  AuditLog plugin       Cloud Logging          AUDIT | req=UUID | user=UID | action | status | duration | ip
  Auth failures         Cloud Logging          AUTH_FAILURE | req=UUID | action | status | ip
  Rate limit hits       Cloud Logging          429 response logged
  Gemini errors         Cloud Logging          WARN: blocked/failed AI calls
  Cloud Run metrics     Cloud Monitoring       CPU, memory, request count, latency
  Firestore metrics     Cloud Monitoring       Read/write ops, latency
```

---

## 14. Checklist Sistemista

| Check | Comando / Azione |
|-------|------------------|
| Server running | `curl https://calmify-server-...run.app/health` |
| Logs | `gcloud logging read "resource.type=cloud_run_revision" --project=calmify-388723` |
| Instances | `gcloud run services describe calmify-server --region=europe-west1` |
| Firestore | Console > Firestore > Database: `calmify-native` |
| Secrets | `gcloud secrets list --project=calmify-388723` |
| Deploy nuovo | `cd ~/Calmify && git pull && gcloud builds submit --config=calmify-server/cloudbuild.yaml` |
| Build logs | `gcloud builds list --project=calmify-388723` |
| Scale a zero | Automatico dopo inattivita' (min-instances=0) |
| Force restart | `gcloud run services update calmify-server --region=europe-west1 --no-traffic` poi ripristina |
