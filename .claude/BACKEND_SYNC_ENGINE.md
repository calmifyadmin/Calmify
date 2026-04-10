# Sync Engine — Piano Implementazione

> **Parent task**: KMP Full Migration (Option C Hybrid)
> **Dipende da**: BACKEND_KTOR_SERVER.md (server deve esistere), BACKEND_PROTOBUF.md (serializzazione)
> **Effort stimato**: 2 settimane
> **Risultato**: Offline-first vero — l'app funziona senza rete, sync automatico in background
>
> ## STATUS: COMPLETE + WIRED (2026-04-10)
> - Week 1-2 implementate: SyncEngine, SyncDiaryRepository, SyncChatRepository, 13 SyncWellnessRepository
> - SQLDelight tables: SyncOperation, SyncMetadata, Diary, ChatSession, ChatMessage, WellnessEntry
> - ConnectivityObserver expect/actual, ConflictResolver (LWW), SyncIndicator UI
> - DeltaApplier routes server deltas to correct sync repos
> - KtorSyncExecutor bridges SyncEngine to server /sync endpoints
> - SyncEngine.start() in Application.onCreate() lifecycle

---

## Architettura Sync Engine

```
┌─────────────────────────────────────────────────────────────┐
│  UI Layer (Compose)                                         │
│  Legge SOLO da SQLDelight (mai dal network direttamente)    │
└──────────────────────────┬──────────────────────────────────┘
                           │ Flow<List<Diary>>
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  Repository (commonMain)                                     │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  SyncAwareRepository                                 │    │
│  │  - read() → SQLDelight (istantaneo)                  │    │
│  │  - write() → SQLDelight + enqueue sync               │    │
│  │  - observe() → SQLDelight Flow (reattivo)            │    │
│  └────────────┬────────────────────────┬────────────────┘    │
│               │                        │                      │
│  ┌────────────▼──────────┐  ┌─────────▼──────────────┐      │
│  │  SQLDelight           │  │  SyncQueue              │      │
│  │  (source of truth)    │  │  (operazioni pending)   │      │
│  │  - Diary table        │  │  - SyncOperation table  │      │
│  │  - ChatSession table  │  │  - retry count          │      │
│  │  - ChatMessage table  │  │  - created_at           │      │
│  │  - CachedThread table │  │  - status (pending/     │      │
│  │  - Wellness tables    │  │    syncing/failed)      │      │
│  └───────────────────────┘  └─────────┬──────────────┘      │
└───────────────────────────────────────┼──────────────────────┘
                                        │
┌───────────────────────────────────────▼──────────────────────┐
│  SyncWorker (background)                                      │
│  - Drains SyncQueue when online                               │
│  - Pulls server changes (delta sync)                          │
│  - Conflict resolution (last-write-wins + merge per entity)   │
│  - Retry with exponential backoff                             │
│  - Batch operations (reduce HTTP calls)                       │
└───────────────────────────┬──────────────────────────────────┘
                            │ Ktor Client
                            ▼
                    ┌───────────────┐
                    │  Ktor Server  │
                    └───────────────┘
```

---

## Componenti Core

### 1. SyncOperation — La coda di sync

```sql
-- data/mongo/src/commonMain/sqldelight/com/lifo/mongo/database/SyncOperation.sq

CREATE TABLE SyncOperation (
    id TEXT NOT NULL PRIMARY KEY,
    entity_type TEXT NOT NULL,       -- "diary", "chat_message", "gratitude", etc.
    entity_id TEXT NOT NULL,         -- ID dell'entita'
    operation TEXT NOT NULL,         -- "CREATE", "UPDATE", "DELETE"
    payload TEXT NOT NULL,           -- JSON/Protobuf serializzato
    status TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING, SYNCING, FAILED, COMPLETED
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,     -- epoch millis
    last_attempt_at INTEGER,
    error_message TEXT
);

CREATE INDEX idx_sync_status ON SyncOperation(status);
CREATE INDEX idx_sync_created ON SyncOperation(created_at);

-- Operazioni pending ordinate per creazione
getPendingOperations:
SELECT * FROM SyncOperation
WHERE status = 'PENDING' OR (status = 'FAILED' AND retry_count < 5)
ORDER BY created_at ASC
LIMIT :limit;

-- Marca come in sync
markSyncing:
UPDATE SyncOperation SET status = 'SYNCING', last_attempt_at = :now
WHERE id = :id;

-- Sync riuscito
markCompleted:
DELETE FROM SyncOperation WHERE id = :id;

-- Sync fallito
markFailed:
UPDATE SyncOperation
SET status = 'FAILED', retry_count = retry_count + 1, error_message = :error
WHERE id = :id;

-- Count pending (per UI badge)
countPending:
SELECT COUNT(*) FROM SyncOperation WHERE status != 'COMPLETED';
```

### 2. SyncMetadata — Tracking delta sync

```sql
-- SyncMetadata.sq

CREATE TABLE SyncMetadata (
    entity_type TEXT NOT NULL PRIMARY KEY,
    last_sync_at INTEGER NOT NULL,          -- epoch millis dell'ultimo sync riuscito
    server_version TEXT                      -- opzionale: version token dal server
);

getLastSync:
SELECT last_sync_at FROM SyncMetadata WHERE entity_type = :entityType;

upsertLastSync:
INSERT OR REPLACE INTO SyncMetadata(entity_type, last_sync_at, server_version)
VALUES (:entityType, :lastSyncAt, :serverVersion);
```

### 3. Tabelle Cache Locali (espansione SQLDelight esistente)

```sql
-- Diary.sq (NUOVO — espande le 5 tabelle esistenti)

CREATE TABLE Diary (
    id TEXT NOT NULL PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    mood INTEGER NOT NULL DEFAULT 3,
    date_epoch INTEGER NOT NULL,
    images TEXT NOT NULL DEFAULT '[]',       -- JSON array di URL
    triggers TEXT NOT NULL DEFAULT '[]',     -- JSON array
    body_sensations TEXT NOT NULL DEFAULT '[]',
    sentiment_label TEXT,
    sentiment_magnitude REAL,
    cognitive_patterns TEXT,                  -- JSON
    topics TEXT NOT NULL DEFAULT '[]',       -- JSON array
    is_bookmarked INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    is_dirty INTEGER NOT NULL DEFAULT 0      -- 1 = modificato localmente, non ancora sincato
);

CREATE INDEX idx_diary_user ON Diary(user_id);
CREATE INDEX idx_diary_date ON Diary(date_epoch);
CREATE INDEX idx_diary_dirty ON Diary(is_dirty);

getAllByUser:
SELECT * FROM Diary WHERE user_id = :userId ORDER BY date_epoch DESC;

getByDate:
SELECT * FROM Diary WHERE user_id = :userId AND date_epoch BETWEEN :start AND :end
ORDER BY date_epoch DESC;

getDirty:
SELECT * FROM Diary WHERE is_dirty = 1;

upsert:
INSERT OR REPLACE INTO Diary(id, user_id, title, description, mood, date_epoch,
    images, triggers, body_sensations, sentiment_label, sentiment_magnitude,
    cognitive_patterns, topics, is_bookmarked, created_at, updated_at, is_dirty)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

markClean:
UPDATE Diary SET is_dirty = 0 WHERE id = :id;

deleteById:
DELETE FROM Diary WHERE id = :id;
```

---

## 4. SyncEngine (commonMain)

```kotlin
// core/util/src/commonMain/kotlin/com/lifo/util/sync/SyncEngine.kt

class SyncEngine(
    private val syncOperationQueries: SyncOperationQueries,
    private val syncMetadataQueries: SyncMetadataQueries,
    private val apiClient: KtorApiClient,
    private val connectivityObserver: ConnectivityObserver,
    private val scope: CoroutineScope
) {
    // Stato osservabile
    val syncState: StateFlow<SyncState>   // Idle, Syncing, Error
    val pendingCount: StateFlow<Int>      // Per UI badge "2 non sincronizzati"

    // Avvia sync loop in background
    fun start() {
        // 1. Osserva connettivita'
        scope.launch {
            connectivityObserver.observe().collect { status ->
                if (status == ConnectivityStatus.Available) {
                    drainQueue()
                    pullChanges()
                }
            }
        }
        // 2. Periodic sync ogni 5 minuti
        scope.launch {
            while (isActive) {
                delay(5.minutes)
                if (connectivityObserver.isOnline()) {
                    pullChanges()
                }
            }
        }
    }

    // Enqueue operazione (chiamato dai repository)
    suspend fun enqueue(operation: SyncOperation) {
        syncOperationQueries.insert(operation)
        // Tenta sync immediato se online
        if (connectivityObserver.isOnline()) {
            drainQueue()
        }
    }

    // Drain: processa tutte le operazioni pending
    private suspend fun drainQueue() {
        val pending = syncOperationQueries.getPendingOperations(limit = 50)
        for (op in pending) {
            syncOperationQueries.markSyncing(op.id)
            try {
                executeSyncOperation(op)
                syncOperationQueries.markCompleted(op.id)
            } catch (e: Exception) {
                syncOperationQueries.markFailed(op.id, e.message)
                if (op.retryCount >= 4) {
                    // Notifica UI: "Impossibile sincronizzare"
                }
            }
        }
    }

    // Pull: scarica cambiamenti dal server
    private suspend fun pullChanges() {
        for (entityType in SyncEntityType.entries) {
            val lastSync = syncMetadataQueries.getLastSync(entityType.name)
            val changes = apiClient.getChanges(entityType, since = lastSync)
            applyChanges(entityType, changes)
            syncMetadataQueries.upsertLastSync(entityType.name, currentTimeMillis())
        }
    }

    // Applica cambiamenti dal server al DB locale
    private suspend fun applyChanges(type: SyncEntityType, changes: DeltaResponse) {
        for (item in changes.created + changes.updated) {
            localDb.upsert(type, item)
        }
        for (id in changes.deleted) {
            localDb.delete(type, id)
        }
    }
}
```

---

## 5. SyncAwareRepository Pattern

```kotlin
// Pattern base per tutti i repository sync-aware

class SyncDiaryRepository(
    private val diaryQueries: DiaryQueries,
    private val syncEngine: SyncEngine,
    private val dispatchers: CoroutineDispatchers
) : DiaryRepository {

    // READ — sempre da locale, istantaneo
    override fun getDiaries(userId: String): Flow<RequestState<List<Diary>>> {
        return diaryQueries.getAllByUser(userId)
            .asFlow()
            .mapToList(dispatchers.io)
            .map { RequestState.Success(it.toDomainList()) }
    }

    // WRITE — salva locale + enqueue sync
    override suspend fun createDiary(diary: Diary): RequestState<Diary> {
        // 1. Salva nel DB locale (UI si aggiorna istantaneamente via Flow)
        diaryQueries.upsert(diary.toEntity(isDirty = true))

        // 2. Enqueue per sync al server
        syncEngine.enqueue(
            SyncOperation(
                id = randomUuid(),
                entityType = "diary",
                entityId = diary.id,
                operation = "CREATE",
                payload = diary.serialize(),
                createdAt = currentTimeMillis()
            )
        )
        return RequestState.Success(diary)
    }

    // DELETE — rimuovi locale + enqueue delete remoto
    override suspend fun deleteDiary(id: String): RequestState<Unit> {
        diaryQueries.deleteById(id)
        syncEngine.enqueue(
            SyncOperation(
                entityType = "diary",
                entityId = id,
                operation = "DELETE",
                payload = "",
                createdAt = currentTimeMillis()
            )
        )
        return RequestState.Success(Unit)
    }
}
```

---

## 6. Delta Sync Protocol

### Server-side endpoint

```kotlin
// Server: GET /api/v1/sync/changes?entity=diary&since=1712345678000
get("/api/v1/sync/changes") {
    val entityType = call.parameters["entity"]!!
    val since = call.parameters["since"]?.toLong() ?: 0L
    val userId = call.principal<UserPrincipal>()!!.uid

    val changes = syncService.getChangesSince(userId, entityType, since)
    call.respond(DeltaResponse(
        created = changes.filter { it.createdAt > since && it.updatedAt == it.createdAt },
        updated = changes.filter { it.updatedAt > since && it.updatedAt != it.createdAt },
        deleted = deletionLog.getDeletedSince(userId, entityType, since),
        serverTime = System.currentTimeMillis()
    ))
}
```

### Conflict Resolution

```
REGOLA BASE: Last-Write-Wins (LWW) con merge per campo

Scenario: utente modifica diary offline, nel frattempo il server ha un insight aggiornato
├── Client manda: { title: "Nuovo titolo", updatedAt: T1 }
├── Server ha:     { sentiment: "positive", updatedAt: T2 }
├── T2 > T1:
│   └── Server MERGE: prende title dal client + sentiment dal server
│       { title: "Nuovo titolo", sentiment: "positive", updatedAt: T3 }
└── Nessun dato perso

Caso DELETE:
├── Client deleta offline
├── Server ha un update nel frattempo
└── DELETE vince sempre (tombstone con TTL 30 giorni)
```

---

## 7. Optimistic Updates (UX chiave)

```kotlin
// L'utente non deve MAI aspettare la rete

// PRIMA (con Firestore diretto):
// Tap "Salva" → spinner → attesa rete → conferma (500ms-3s)

// DOPO (con Sync Engine):
// Tap "Salva" → salvato localmente → UI aggiornata (0ms) → sync in background

// Il Flow SQLDelight emette immediatamente il nuovo dato
// La UI si aggiorna senza aspettare il server
// Se il sync fallisce: badge "1 non sincronizzato" + retry automatico
```

---

## 8. Connectivity Observer (expect/actual)

```kotlin
// core/util/src/commonMain
expect class ConnectivityObserver {
    fun observe(): Flow<ConnectivityStatus>
    fun isOnline(): Boolean
}

enum class ConnectivityStatus { Available, Unavailable, Losing }

// androidMain — usa ConnectivityManager
actual class ConnectivityObserver(context: Context) {
    actual fun observe(): Flow<ConnectivityStatus> = callbackFlow {
        val manager = context.getSystemService<ConnectivityManager>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(Available) }
            override fun onLost(network: Network) { trySend(Unavailable) }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
}

// iosMain — usa NWPathMonitor
// webMain — usa navigator.onLine + online/offline events
```

---

## Implementazione Step-by-Step

### Settimana 1: Core Sync

- [ ] Creare `SyncOperation.sq` + `SyncMetadata.sq`
- [ ] Espandere SQLDelight: `Diary.sq` completo (con is_dirty flag)
- [ ] Implementare `SyncEngine.kt` in `core/util/src/commonMain/`
- [ ] Implementare `ConnectivityObserver` expect/actual (Android first)
- [ ] Creare `SyncDiaryRepository` come primo repository sync-aware
- [ ] Test: scrivi offline → torna online → verifica sync
- [ ] Test: pull changes dal server → SQLDelight aggiornato → UI riflette

### Settimana 2: Full Migration + Polish

- [ ] Espandere SQLDelight: tabelle per ChatSession, ChatMessage, wellness entities
- [ ] Creare sync-aware repository per: chat, gratitude, energy, sleep, habits
- [ ] Creare sync-aware repository per: meditation, movement, values, ikigai, awe, reframe
- [ ] Delta sync endpoint server-side
- [ ] Conflict resolution (LWW + field merge)
- [ ] Retry con exponential backoff (1s, 2s, 4s, 8s, max 5 tentativi)
- [ ] Batch sync (raggruppare operazioni per ridurre HTTP calls)
- [ ] UI: indicatore "sincronizzazione in corso" / "tutto sincronizzato"
- [ ] UI: badge pending count
- [ ] Test end-to-end: offline → online → delta sync → conflict → resolution
- [ ] Stress test: 100 operazioni offline → bulk sync

---

## Dipendenze

```
BACKEND_SYNC_ENGINE.md (questo file)
    ├── richiede server da → BACKEND_KTOR_SERVER.md
    ├── serializza con → BACKEND_PROTOBUF.md
    ├── SQLDelight schema gia' in → data/mongo/
    └── ConnectivityObserver pattern gia' in → core/util (da creare expect/actual)
```

---

## Metriche di Successo

| Metrica | Target |
|---|---|
| Tempo apertura app (con cache) | < 100ms alla prima UI renderizzata |
| Write latency percepita | 0ms (optimistic) |
| Sync queue drain time | < 5s per 50 operazioni |
| Offline funzionalita' | 100% read, 100% write, 0% real-time |
| Conflitti persi | 0 (merge per campo, tombstone per delete) |
| Retry success rate | > 99% entro 5 tentativi |
