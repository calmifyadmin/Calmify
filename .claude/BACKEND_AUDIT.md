# Backend Architecture — Audit Completo (2026-04-10)

> **Audit eseguito su 124 file modificati dal commit `08ef101` (base master).**
> **Risultato: 30+ problemi critici. Il backend richiede RE-ENGINEERING COMPLETO.**
> **Standard richiesto: NASA-level, zero tolerance, nessun limite di costo.**

---

## CRITICAL — Server non puo' funzionare

### C1. Collection Names Server ≠ Client (24/26 sbagliate)

Il server usa camelCase, il client Android usa snake_case. Il server legge/scrive in collection vuote.

| Server (SBAGLIATO) | Client Android (CORRETTO) | File Server |
|---|---|---|
| `diary` | `diaries` | DiaryService.kt:13 |
| `chatSessions` | `chat_sessions` | ChatService.kt:15 |
| `chatMessages` | `chat_messages` | ChatService.kt:16 |
| `diaryInsights` | `diary_insights` | InsightService.kt:12 |
| `profileSettings` | `profile_settings` | ProfileService.kt:12 |
| `psychologicalProfiles` | `psychological_profiles` | ProfileService.kt:13 |
| `gratitudeEntries` | `gratitude_entries` | WellnessServiceFactory |
| `energyCheckIns` | `energy_checkins` | WellnessServiceFactory |
| `sleepLogs` | `sleep_logs` | WellnessServiceFactory |
| `meditationSessions` | `meditation_sessions` | WellnessServiceFactory |
| `movementLogs` | `movement_logs` | WellnessServiceFactory |
| `thoughtReframes` | `thought_reframes` | WellnessServiceFactory |
| `wellbeingSnapshots` | `wellbeing_snapshots` | WellnessServiceFactory |
| `aweEntries` | `awe_entries` | WellnessServiceFactory |
| `connectionEntries` | `connection_entries` | WellnessServiceFactory |
| `recurringThoughts` | `recurring_thoughts` | WellnessServiceFactory |
| `valuesDiscovery` | `values_discovery` | WellnessServiceFactory |
| `habitCompletions` | `habit_completions` | DashboardService.kt:94 |
| `follows` (flat) | `social_graph/{userId}/following` (subcollection) | SocialService.kt:16 |
| `threadLikes` | sconosciuto — verificare | SocialService.kt:14 |
| `threadReposts` | sconosciuto — verificare | SocialService.kt:15 |

**SyncService.kt collection map (linee 23-39)**: usa un terzo set di nomi ancora diverso.
**GdprService.kt (linee 21-37)**: usa un quarto set di nomi. GDPR delete non cancella niente.

### C2. 17 Campi Nullable in Protobuf (runtime crash)

`kotlinx.serialization.protobuf` NON supporta `T? = null`. Ogni serializzazione crasha.

| File | Linea | Campo | Fix |
|---|---|---|---|
| DiaryApi.kt | 10 | `data: DiaryProto? = null` | `success: Boolean = false` + `data: DiaryProto = DiaryProto()` |
| DiaryApi.kt | 11 | `error: ApiError? = null` | `error: ApiError = ApiError()` |
| DiaryApi.kt | 17 | `error: ApiError? = null` | `error: ApiError = ApiError()` |
| DiaryApi.kt | 18 | `meta: PaginationMeta? = null` | `meta: PaginationMeta = PaginationMeta()` |
| DiaryApi.kt | 31 | `data: DiaryInsightProto? = null` | `success: Boolean` + non-nullable |
| DiaryApi.kt | 32,38 | `error: ApiError? = null` | non-nullable |
| DiaryApi.kt | 39 | `meta: PaginationMeta? = null` | non-nullable |
| ChatApi.kt | 10,11,17,18,22,23 | 6 campi nullable | tutti non-nullable |
| SocialApi.kt | 9,10,15,16 | 4 campi nullable | tutti non-nullable |
| DashboardApi.kt | 8,9 | 2 campi nullable | tutti non-nullable |
| DashboardApi.kt | 15 | `todayPulse: TodayPulseProto? = null` | non-nullable |
| AiApi.kt | 12 | `context: AiContextProto? = null` | non-nullable |
| AiApi.kt | 30,48,65,81 | 4 x `error: ApiError? = null` | tutti non-nullable |
| SyncApi.kt | 57 | `error: String? = null` | `error: String = ""` |

### C3. JsonElement in Protobuf (incompatibile)

`GenericDeltaResponse` (SyncApi.kt:18-19) usa `List<JsonElement>`. `JsonElement` e' un tipo JSON-only, non ha serializer Protobuf. Serve: `List<String>` (JSON-encoded) o typed delta per entity.

### C4. Blocking `.get().get()` su tutti i 12 Service

Ogni service usa `ApiFuture.get()` che blocca il thread Ktor/Netty. Sotto carico, esaurisce il thread pool e il server si congela. DEVE usare `withContext(Dispatchers.IO)` o `kotlinx-coroutines-guava` `.await()`.

Files: DiaryService, ChatService, DashboardService, InsightService, ProfileService, SocialService, SyncService, GdprService, NotificationService, FeatureFlagService, GenericWellnessService, TokenTracker, PromptRegistry.

### C5. Double Gemini API Call (AiOrchestrator.kt)

`generateInsight()` (linee 136-156) chiama `generateJson()` E `generate()` per la stessa richiesta.
`analyzeText()` (linee 170-188) stesso problema.
Raddoppia costi e latenza su ogni insight/analisi.

### C6. GDPR Export/Delete Non Funziona

`GdprService.kt` usa collection names sbagliate (vedi C1). Data export restituisce oggetti vuoti. Account deletion non cancella i dati reali dell'utente. Violazione GDPR.

### C7. Social Graph Data Model Incompatibile

Server usa flat collection `follows` con `followerId`/`followedId`.
Client usa subcollection `social_graph/{userId}/following` e `social_graph/{userId}/followers`.
Strutture dati completamente diverse. Follow/unfollow non funziona.

### C8. SyncService applyBatch No Ownership Check

Linee 183-190: `CREATE` e `UPDATE` scrivono su qualsiasi documento senza verificare che appartenga all'utente. Un client malevolo puo' sovrascrivere documenti di altri utenti.

### C9. DashboardService.calculateStreak() Rotto

Linee 67-85: incrementa `streak` per ogni entry senza verificare che i giorni siano consecutivi. Utente con entries il 1, 5, e 20 gen ottiene streak=3 invece di streak=1.

### C10. SSE Streaming Catch Blocks Dead Code

AiRoutes.kt linee 57-63: dopo `respondBytesWriter` la response HTTP e' gia' committata. I catch tentano `call.respond()` di nuovo → `ResponseAlreadySentException`.

---

## HIGH — Funzionalita' rotta o fragile

### H1. IDOR su Habit Completions (WellnessRoutes.kt:113-131)
Toggle endpoint non verifica che l'habit appartenga all'utente. Qualsiasi utente autenticato puo' toggle habit di altri.

### H2. Rate Limiting Definito Ma Mai Applicato
`RateLimiting.kt` definisce 5 configurazioni ma nessuna route usa `rateLimit(RateLimitName(...))`. Rate limiting effettivamente disabilitato.

### H3. Streaming Bypassa Quota (AiOrchestrator.kt:95-112)
`chatStream()` non chiama `tokenTracker.record()`. Utenti possono bypassare limiti usando streaming.

### H4. Streaming Senza Safety Settings (GeminiClient.kt)
`generateStream()` non include `safetySettings`. Usa default Gemini (BLOCK_MEDIUM_AND_ABOVE) invece di BLOCK_ONLY_HIGH. Contenuto terapeutico bloccato.

### H5. Client Chat Repos Inviano Map Raw (KtorChatRepository.kt)
`createSession`, `sendMessage`, `saveAiMessage`, `saveLiveMessage` inviano `mapOf(...)` invece di DTO tipizzati. Con Content-Type protobuf, serializzazione imprevedibile.

### H6. WellnessListDto<T> Generico (KtorHabitRepository.kt:60)
`kotlinx.serialization.protobuf` non supporta classi generiche. Funziona solo se server risponde JSON.

### H7. SyncEngine Retry Non Funziona (SyncEngine.kt:117-134)
`markFailed()` cambia status a FAILED. Ma `drainQueue()` fetcha solo PENDING. Le operazioni fallite non vengono mai ritentate.

### H8. SyncDiaryRepository.asFlow() Non Reattivo (SyncDiaryRepository.kt:258-264)
Emette una sola volta poi completa. Write locali non triggerano update UI. L'offline-first e' rotto.

### H9. `encodeDefaults = false` nel Server (Serialization.kt:16)
Campi con valori default (count=0, cached=false) omessi dal JSON. Client potrebbe non gestire campi mancanti.

### H10. `userOrThrow()` Definito Ma Mai Usato (AuthExt.kt:6)
Tutte le routes usano `call.principal<UserPrincipal>()!!`. Se principal e' null → NPE generica → 500 invece di 401.

### H11. No `updatedAt` Nei Service (DiaryService, ChatService, GenericWellnessService)
Create/update non scrivono `updatedAt`. Delta sync (`whereGreaterThan("updatedAt", since)`) non trova mai le modifiche.

### H12. Timezone Server vs Utente
`DashboardService.kt:93` usa `LocalDate.now()` (timezone server UTC). `DashboardService.kt:39-41` calcola `todayStart` in UTC. Utente a UTC+2 che scrive alle 23:00 ottiene dayKey sbagliato.

---

## MEDIUM

### M1. Firestore Batch Limit 500 Non Rispettato
`deleteAllDiaries`, `deleteAllSessions`, `markAllAsRead`, GDPR delete — nessuno chunka in batch da 500.

### M2. N+1 Query SocialService.batchCheckLikes (linee 244-256)
Ogni thread ID = query Firestore separata. 20 thread = 40 query extra. Usare `getAll()`.

### M3. Response Wrapper Inconsistenti
Alcuni endpoint restituiscono raw objects, altri wrapper. Client non sa cosa aspettarsi.

### M4. deleteAllDiaries/deleteAllSessions Locale Ma Non Sync
Cancellazione locale senza enqueue sync al server. Server mantiene i dati.

### M5. Images Double-Encoded JSON-in-JSON (SyncDiaryRepository.kt:215)
`json.encodeToString(diary.images)` produce `"[\"url1\"]"` dentro il JSON outer.

### M6. ConflictResolver Dead Code
Definito, mai chiamato. DeltaApplier sovrascrive sempre (server wins). Tutto il codice LWW merge e' inutilizzato.

### M7. KtorDiaryRepository Hardcoded limit=100 (linea 29)
Nessuna paginazione. Utenti con 100+ diary perdono le vecchie.

### M8. KtorFeatureFlagRepository Gestisce Solo 5/9 Flag (linee 50-60)
`observeBoolean()` manca FEDERATION_ENABLED, SEMANTIC_SEARCH_ENABLED, MEDIA_PIPELINE_ENABLED, AB_TEST_NEW_HOME.

### M9. DeltaApplier INSERT Non Upsert (SyncChatRepository.kt:329-344)
`applySessionChanges` usa `insertSession()` per created E updated. Update su sessione esistente → PK violation.

### M10. KtorInsightRepository.submitFeedback Dead Code (linee 36-43)
Crea `body` map ma non la invia (usa `postNoBody`). Feedback come query params.

### M11. GenericWellnessService.list() Assume `timestampMillis` (linea 26)
HabitProto e ValuesDiscoveryProto usano `createdAtMillis`, non `timestampMillis`. Listing fallisce.

### M12. SyncWellnessRepository.upsert Sempre "CREATE" (linea 97)
Enqueue operazione sempre come CREATE anche per update. Server non distingue.

### M13. SyncIndicator SYNCED Mai Visibile (SyncIndicator.kt:61)
`AnimatedVisibility` condition esclude SYNCED. Lo stato "sincronizzato" non appare mai.

### M14. Dual ConnectivityObserver (KoinModules.kt:16,100,102)
Due tipi diversi registrati in Koin. Confusione.

### M15. KtorApiClient 401 Retry Body Replay (KtorApiClient.kt:143-165)
Retry su 401 ri-esegue la lambda. Body stream gia' consumato per POST/PUT.

---

## Direttiva Finale

**NON fixare questi bug uno per uno. RE-ENGINEERIZZARE l'intero backend da zero.**

Per ogni file riscritto:
1. Leggere il corrispondente codice client Android per collection names e field names
2. Verificare compatibilita' Protobuf (zero nullable, zero JsonElement, zero generici)
3. Wrappare TUTTE le chiamate Firestore in `withContext(Dispatchers.IO)`
4. Verificare authorization su OGNI operazione
5. Testare con dati reali dal database `calmify-native`
6. Applicare le regole NASA-level da CLAUDE.md "QUALITY MANDATE"
