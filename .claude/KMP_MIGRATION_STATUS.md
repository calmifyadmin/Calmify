# KMP Migration Status — Option C (Hybrid)

> Tracker principale. Aggiornare SOLO dopo aver verificato che il codice compila.
> Iniziato: 2026-04-08 | Ultimo update: 2026-04-19

## STATUS 2026-04-19

**Level 1 KMP REST**: ✅ **DEPLOYED** (2026-04-18) — 36/36 repos, server Cloud Run live, smoke E2E 91/92 verdi.
**Sprint i18n**: 🚧 **IN PROGRESS — Fase A→C.3 DONE 2026-04-19** (pre-Level 3, ~5-6 gg totali). Scaffold + 12 locales + EN default + facade populated. Cumulative: C.1 home + C.2 search/social-profile + C.3 composer/messaging/thread-detail = 78 keys × 12 lang + ~81 hardcoded migrati. Next: Fase C.4 (avatar-creator + insight + meditation). Vedi `memory/i18n_strategy.md`.
**Level 3 (iOS + Web)**: 🔓 **UNBLOCKED** ma parte solo DOPO sprint i18n. Vedi `memory/kmp_action_blocks.md`.

## Strategia data layer: EVOLUTA da Option C → Server-Mediated

> **Decision (2026-04-09)**: Ktor Server in mezzo > Firebase diretto
> - Sicurezza: zero credenziali nell'APK, server-side validation
> - Performance: aggregazione server-side, response caching, Protobuf 3-5x compatto
> - AI: prompt registry centralizzato, token caching 60%+ savings
> - Offline: SQLDelight cache + SyncQueue + delta sync
> - WASM: Ktor Client funziona su tutte le piattaforme (GitLive no)

- Client (KMP) → Ktor REST/Protobuf → Ktor Server (Cloud Run) → Firestore
- Real-time: Firestore snapshots per messaging/feed (resta diretto per ora)
- Auth: Firebase Auth resta (JWT validato server-side)
- AI: Gemini chiamato SOLO dal server (mai dal client)
- **Piani dettagliati**: `.claude/BACKEND_KTOR_SERVER.md`, `BACKEND_SYNC_ENGINE.md`, `BACKEND_PROTOBUF.md`, `BACKEND_AI_SERVER.md`
- **Tracker commit**: `memory/backend_refactor_tracker.md`
- **Branch**: `backend-architecture-refactor`

---

## FASE 0: Pre-flight checks
- [x] Verificare build corrente compila: `./gradlew assembleDebug` — OK
- [x] Verificare `KMP_READY` flag in buildSrc/ProjectConfig.kt — `false` (non blocca, solo doc)
- [x] Verificare convention plugins supportano iOS targets — iosX64, iosArm64, iosSimulatorArm64 configurati
- [x] Branch: `backend-architecture-refactor` (include sia KMP che backend)

**NOTA**: `compileCommonMainKotlinMetadata` fallisce per TUTTI i moduli con `koinViewModel()`
perche' `PlatformViewModel` in commonMain non e' `ViewModel`. Questo e' un limite noto KMP+Koin.
Validazione corretta: `compileDebugKotlinAndroid` (Android) e `compileKotlinIosSimulatorArm64` (iOS).

## FASE 1: Quick wins (2 giorni)

### Tier 3 — insight (SavedStateHandle fix, template per tutti)
- [x] Fix InsightViewModel.kt — rimosso SavedStateHandle, diaryId via LaunchedEffect in Screen
- [x] Fix InsightKoinModule.kt — `viewModel<InsightViewModel> { InsightViewModel(get()) }`
- [x] RootComponent.kt — non serve modifica (diaryId gia' passato a InsightScreen)
- [x] Compilazione: `./gradlew assembleDebug` — OK

### Tier 4 — habits (spostare in commonMain)
- [x] Creare `src/commonMain/kotlin/com/lifo/habits/`
- [x] Spostare HabitContract.kt → commonMain
- [x] Spostare HabitViewModel.kt → commonMain (no SavedStateHandle, gia' pulito)
- [x] Spostare HabitListScreen.kt → commonMain
- [x] Spostare HabitKoinModule.kt → commonMain
- [x] HabitEntryPoint.kt resta in androidMain (Toast, LocalContext)
- [x] Compilazione: `./gradlew assembleDebug` — OK

### Tier 4 — meditation (spostare + fix JVM deps)
- [x] Creare `src/commonMain/kotlin/com/lifo/meditation/`
- [x] Spostare MeditationContract.kt → commonMain
- [x] Spostare MeditationViewModel.kt → commonMain (fix UUID→Uuid.random(), System.currentTimeMillis→KMP)
- [x] Spostare MeditationScreen.kt → commonMain (zero Android imports)
- [x] Spostare MeditationKoinModule.kt → commonMain
- [x] MeditationBellPlayer.kt resta androidMain (AudioTrack)
- [x] MeditationEntryPoint.kt resta androidMain (Toast, LocalContext, BellPlayer)
- [x] Compilazione: `./gradlew assembleDebug` — OK

### Tier 1 — Validazione compilazione iOS (batch)
- [ ] `./gradlew :features:avatar-creator:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:notifications:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:onboarding:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:profile:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:search:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:subscription:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:messaging:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:thread-detail:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:social-profile:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:feed:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:history:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :features:composer:compileKotlinIosSimulatorArm64`

## FASE 2: Data layer — GitLive migration (1-2 settimane)

### Setup GitLive
- [ ] Aggiungere GitLive deps in data/mongo build.gradle commonMain
- [ ] Creare Firebase init per KMP (Firebase.initialize)
- [ ] Aggiornare MongoKoinModule per inject GitLive instances

### Batch 1 — Repository CRUD semplici (20 file)
- [ ] FirestoreDiaryRepository → commonMain (GitLive)
- [ ] FirestoreProfileRepository → commonMain
- [ ] FirestoreProfileSettingsRepository → commonMain
- [ ] FirestoreGratitudeRepository → commonMain
- [ ] FirestoreEnergyRepository → commonMain
- [ ] FirestoreSleepRepository → commonMain
- [ ] FirestoreHabitRepository → commonMain
- [ ] FirestoreMeditationRepository → commonMain
- [ ] FirestoreMovementRepository → commonMain
- [ ] FirestoreValuesRepository → commonMain
- [ ] FirestoreIkigaiRepository → commonMain
- [ ] FirestoreAweRepository → commonMain
- [ ] FirestoreReframeRepository → commonMain
- [ ] FirestoreRecurringThoughtRepository → commonMain
- [ ] FirestoreEnvironmentRepository → commonMain
- [ ] FirestoreFeatureFlagRepository → commonMain
- [ ] FirestoreSearchRepository → commonMain
- [ ] FirestoreWaitlistRepository → commonMain
- [ ] FirestoreNotificationRepository → commonMain
- [ ] FirestoreBlockRepository → commonMain
- [ ] Compilazione: `./gradlew :data:mongo:compileCommonMainKotlinMetadata`

### Batch 2 — Repository con real-time listeners (10 file)
- [ ] FirestoreFeedRepository → commonMain (snapshots → Flow)
- [ ] FirestoreThreadRepository → commonMain
- [ ] FirestoreThreadHydrator → commonMain
- [ ] FirestoreSocialGraphRepository → commonMain
- [ ] FirestoreSocialMessagingRepository → commonMain
- [ ] FirestoreConnectionRepository → commonMain
- [ ] FirestorePresenceRepository → commonMain
- [ ] ChatRepositoryImpl → commonMain
- [ ] FirestoreGardenRepository → commonMain
- [ ] FirestoreWellbeingRepository → commonMain
- [ ] Compilazione dopo batch

### Batch 3 — Repository complessi (restanti)
- [ ] FirestoreInsightRepository → commonMain
- [ ] FirebaseAvatarRepository → commonMain
- [ ] FirebaseMediaUploadRepository → commonMain (Storage)
- [ ] UnifiedContentRepositoryImpl → commonMain
- [ ] HomeContentItemMappers → commonMain
- [ ] FirestoreMapper → commonMain
- [ ] FirebaseAnalyticsTracker → commonMain
- [ ] CloudFunctionsContentModerationRepository → commonMain
- [ ] PlayBillingSubscriptionRepository → resta androidMain (Google Play only)
- [ ] MongoKoinModule → split commonMain/androidMain
- [ ] Compilazione finale + test Android

## FASE 3: Tier 2 features (2 settimane)

### SavedStateHandle removal (trasversale)
- [x] Pattern stabilito in Fase 1 (insight) — rimosso, params via constructor
- [x] ChatViewModel.kt — rimosso (era unused)
- [x] LiveChatViewModel.kt — rimosso (era unused)
- [ ] HomeViewModel.kt — usa SSH per date selection persistence
- [ ] WriteViewModel.kt — usa SSH per draft restoration + diaryId nav
- [ ] SnapshotViewModel.kt
- [ ] DashboardViewModel.kt (write)
- [ ] AuthenticationViewModel.kt
- [ ] HumanoidViewModel.kt

### home — date picker + spostamenti
- [ ] Spostare HomeContent.kt → commonMain (verificare)
- [ ] Creare expect/actual DatePickerDialog
- [ ] Spostare componenti UI puri → commonMain

### write — wizard system + use cases
- [ ] Spostare Wizard system (20+ file) → commonMain
- [ ] Spostare TextAnalyzer → commonMain
- [ ] Spostare Use Cases → commonMain
- [ ] Creare expect/actual media picker

### auth — Google Sign-In abstraction
- [ ] Creare expect/actual GoogleSignInProvider
- [ ] Spostare AuthenticationContent.kt → commonMain
- [ ] Spostare AuthenticationViewModel.kt → commonMain
- [ ] Spostare SignOutUseCase.kt → commonMain

### chat — audio pipeline interfaces
- [ ] Creare AudioEngine interface in commonMain
- [ ] Creare VadEngine interface in commonMain
- [ ] Creare SpeechRecognizer interface in commonMain
- [ ] Migrare WebSocket a Ktor (se non gia' fatto)
- [ ] Spostare UI components in commonMain

## FASE 4: Tier 5 — humanoid + platform impls (3-4 settimane)

### humanoid — logica pura → commonMain
- [ ] VrmModel.kt → commonMain
- [ ] VrmHumanoidBoneMapper.kt → commonMain
- [ ] GltfBoneOptimizer.kt → commonMain
- [ ] LipSyncController.kt → commonMain (verificare)
- [ ] PhonemeConverter.kt → commonMain (verificare)
- [ ] HumanoidViewModel.kt → commonMain

### humanoid — interfacce rendering
- [ ] Creare PlatformRenderer expect/actual
- [ ] Creare PlatformModelLoader expect/actual
- [ ] Creare PlatformAnimationPlayer expect/actual
- [ ] Refactor FilamentRenderer → implementa interfaccia

### iOS implementations (se si procede)
- [ ] iosMain per auth (Google Sign-In iOS)
- [ ] iosMain per meditation (AVAudioPlayer)
- [ ] iosMain per humanoid (Metal rendering)
- [ ] iosMain per data/mongo (GitLive — automatico)

## FASE 5: Core modules gaps
- [ ] core/ui — Gallery picker expect/actual
- [ ] core/ui — MoodShapeDefinitions porting
- [ ] core/ui — TutorialStorageImpl per iOS
- [ ] core/util — OnboardingManager per iOS
- [ ] core/util — AuthProvider per iOS
- [ ] core/social-ui — MediaCarousel actual per iOS

## FASE 6: Backend Architecture (7-9 settimane) — NEW

> Branch: `backend-architecture-refactor`
> Tracker commit: `memory/backend_refactor_tracker.md`
> Piani dettagliati: `.claude/BACKEND_*.md`

### 6.1 Protobuf (1 settimana) — `.claude/BACKEND_PROTOBUF.md`
- [ ] Modulo shared-models con @ProtoNumber data classes
- [ ] Domain ↔ Proto mappers
- [ ] ContentNegotiation server + client (Protobuf + JSON fallback)

### 6.2 Ktor Server (3-5 settimane) — `.claude/BACKEND_KTOR_SERVER.md`
- [ ] Bootstrap: Ktor + Netty + Firebase Admin + Cloud Run
- [ ] CRUD Batch 1: 15 core endpoints (diary, chat, insight, profile)
- [ ] CRUD Batch 2: 14 wellness endpoints + aggregated dashboard
- [ ] Social endpoints: feed, threads, graph, GDPR
- [ ] Real-time: WebSocket messaging, SSE feed/notifications
- [ ] Security: rate limiting, audit log, input sanitization

### 6.3 AI Server-Side (1 settimana) — `.claude/BACKEND_AI_SERVER.md`
- [ ] AiOrchestrator + PromptRegistry + ModelRouter
- [ ] ResponseCache (semantic hash, 60%+ savings)
- [ ] ContentFilter + TokenTracker
- [ ] Streaming SSE + WebSocket voice proxy

### 6.4 Sync Engine (2 settimane) — `.claude/BACKEND_SYNC_ENGINE.md`
- [ ] SyncOperation.sq + SyncMetadata.sq + expanded entity tables
- [ ] SyncEngine.kt (drain queue, pull changes, delta sync)
- [ ] ConnectivityObserver expect/actual
- [ ] SyncAwareRepository pattern + conflict resolution
- [ ] Optimistic updates + UI indicators

---

## Metriche progresso

| Metrica | Inizio | Attuale | Target |
|---------|--------|---------|--------|
| File commonMain (features) | 183 | **237** | 260+ |
| File androidMain (features) | ~143 | **90** | 40- |
| Repository in commonMain | 3 | 3 | 39 |
| Moduli iOS-compilabili | 0 | 0 | 18 |
| SavedStateHandle rimasti | 4 | **2** | 0 |
| expect/actual pairs | ~3 | ~3 | 15+ |

**Ratio commonMain**: 237/(237+90) = **72.5%** (target: 85%+)

---

## Log modifiche

| Data | Fase | Azione | Risultato |
|------|------|--------|-----------|
| 2026-04-08 | Setup | Creato tracker, analisi completa, scelto Option C | 5 TIER docs creati |
| 2026-04-08 | Fase 1 | Fix insight SavedStateHandle | 0 Android imports in commonMain |
| 2026-04-08 | Fase 1 | habits: 4 file → commonMain | 4/5 file in commonMain |
| 2026-04-08 | Fase 1 | meditation: 4 file → commonMain, fix UUID+timestamp | 4/6 file in commonMain |
| 2026-04-08 | Fase 1 | home: 10 UI components → commonMain (clean) | EnterpriseNavBar, DashboardHeader, etc. |
| 2026-04-08 | Fase 1 | home: 6 UseCases java.time→kotlinx.datetime → commonMain | CalculateStreaks, MoodDistrib, etc. |
| 2026-04-08 | Fase 1 | home: DateFormatters java.time→kotlinx.datetime → commonMain | Formatter completo KMP |
| 2026-04-08 | Fase 1 | home: DailyInsightData estratto in commonMain (Instant) | Sbloccato WeekStrip+ActivityTracker |
| 2026-04-08 | Fase 1 | home: HeroGreetingCard+WeeklyActivityTracker+ExpressiveWeekStrip → commonMain | String.format→formatDecimal |
| 2026-04-08 | Fase 1 | write: 47 file → commonMain (batch) | 11 contracts, 18 screens, 16 VMs, 2 nav |
| 2026-04-08 | Fase 1 | settings: EnvironmentEntryPoint → commonMain | Unico file pulito tra moduli secondari |
| 2026-04-09 | Fase 1 | history: 5 file → commonMain (3 con java.time→kotlinx.datetime) | Modulo 100% commonMain |
| 2026-04-09 | Fase 1 | chat: rimosso SavedStateHandle da ChatVM+LiveChatVM (unused) | 2 SavedStateHandle eliminati |
| 2026-04-09 | Fase 1 | Scan completo androidMain: 90 file restanti, tutti con blockers reali | Fase 1 COMPLETATA |
