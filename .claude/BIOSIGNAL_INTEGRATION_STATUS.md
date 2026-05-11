# Bio-Signal Integration — Live Status Tracker

> **Plan**: `.claude/BIOSIGNAL_INTEGRATION_PLAN.md`
> **Started**: 2026-05-11
> **Current phase**: **Phase 0+1+2 DONE 2026-05-11** (Phase 2 data layer; UI deferred to 2.UI follow-up)
> **Branch**: `design-system-refactor` (Phase 0+1 commits live here; bio-signal Phase 2+ will continue or branch)
> **Last update**: 2026-05-11 — design-system-refactor COMPLETE (4 commits) + Bio-Signal Phase 0+1 DONE same day.

## Dependency on design-system-refactor

Bio-signal UI components (BioMetricCard, BioNarrativeCard, BioContextScreen, BioOnboardingScreen, etc.) must be built on **canonical tokens** matching `design/biosignal/calmify.css`. The precursor workstream `design-system-refactor` aligns Kotlin theme to the CSS port. See `memory/project_design_system_refactor.md` for that workstream's tracker.

Bio-signal Phase 0 starts when:
- ✅ R1 token canonical refactor (DONE 2026-05-11)
- ⬜ R3 surgical refactor of high-impact UI files (~30 files, 3/30+ done)
- ⬜ R4 theme README (final documentation)

---

## Phase 0 — Foundations ✅ DONE 2026-05-11
**Goal**: Domain models + Provider interface compile-green on all KMP targets.
**Est**: 2-3 days. **Actual: same day**.
**Status**: ✅ DONE

- [x] `core/util/.../model/BioSignal.kt` — sealed class hierarchy (consolidated single file)
  - [x] `HeartRateSample(timestamp, bpm, source, confidence)`
  - [x] `HrvSample(timestamp, rmssdMillis, source, confidence)`
  - [x] `SleepSession(start, end, stages, efficiencyPercent, source, confidence)` + `SleepStage` + `SleepStageKind` enum
  - [x] `StepCount(timestamp, date, count, source, confidence)`
  - [x] `RestingHeartRate(timestamp, date, bpm, source, confidence)`
  - [x] `OxygenSaturationSample(timestamp, percent, source, confidence)`
  - [x] `ActivitySession(start, end, activityType, calories, distance, source, confidence)` + `ActivityType` enum
  - [x] `BioSignalDataType` enum (HEART_RATE/HRV/SLEEP/STEPS/RESTING_HR/SPO2/ACTIVITY)
  - [x] `DataConfidence` + `ConfidenceLevel` enum (HIGH/MEDIUM/LOW)
  - [x] `BioSignalSource` + `SourceKind` enum (WEARABLE/PHONE/MANUAL/DERIVED)
  - [x] `BioAggregate` server contract + `AggregatePeriod` enum (DAILY/WEEKLY/MONTHLY)
- [x] `core/util/.../repository/HealthDataProvider.kt` — interface
  - [x] `checkAvailability()`, `requestPermissions()`, `revokePermissions()`
  - [x] 7 `read*` functions, one per data type
  - [x] `ProviderStatus` sealed class (NotInstalled/NotSupported/NeedsUpdate/NeedsPermission/Ready)
- [x] `core/util/.../repository/BioSignalRepository.kt` — repository contract
  - [x] `observeRawSamples()` Flow, `getRawSamples()` one-shot
  - [x] `getAggregate()`, `observeAggregate()` Flow
  - [x] `ingestFromProvider()`, `syncPendingAggregates()`
  - [x] `exportAll()` GDPR Art.20, `deleteAll()` GDPR Art.17
  - [x] `pruneExpiredSamples()`, `pruneSamplesInRange()` TTL management
  - [x] `IngestionResult` data class
- [x] `shared/models/.../model/BioSignalProto.kt` — Protobuf-safe DTOs (zero-nullable, regola 3)
  - [x] 7 raw sample Protos + `SleepStageProto`
  - [x] `BioAggregateProto` + `BioAggregateBatchProto` + `BioAggregateResponseProto`
  - [x] `BioSignalSourceProto`, `DataConfidenceProto`
  - [x] `BioConsentEventProto` (GDPR Art.7 demonstrability)
- [x] **Build check**: `:core:util:compileDebugKotlinAndroid` + `:shared:models:compileDebugKotlinAndroid` GREEN 27s

---

## Phase 1 — Android Health Connect ✅ DONE 2026-05-11
**Goal**: Read-only ingestion working on S24 + Mi Band 10.
**Est**: 3-5 days. **Actual: same day**.
**Status**: ✅ DONE

- [x] Add `androidx.health.connect:connect-client:1.1.0-rc03` + `androidx.work:work-runtime-ktx:2.10.0` to `libs.versions.toml` + `data/mongo/build.gradle` (androidMain)
- [x] `HealthConnectProvider.kt` actual implementation — read-only, all `withContext(Dispatchers.IO)`, lazy `HealthConnectClient.getOrCreate`, complete SDK_AVAILABLE / UNAVAILABLE / PROVIDER_UPDATE_REQUIRED status mapping
- [x] `HealthConnectPermissions.kt` — `BioSignalDataType` ↔ HC permission string bidirectional mapping + `createRequestPermissionResultContract` exposed to UI
- [x] `HealthConnectMappers.kt` — Record → BioSignal mappers for all 7 record types (HeartRate, HRV-Rmssd, SleepSession w/ stages, Steps, RestingHeartRate, OxygenSaturation, ExerciseSession) + `inferConfidence` helper + `sourceFrom(Metadata)` device+app provenance extraction
- [x] `BioSignalSyncWorker.kt` — WorkManager `CoroutineWorker`, 24h period + 4h flex window, UNMETERED + battery-not-low constraints, idempotent (matches Repository contract), exponential-backoff retry on uncaught exception, periodic TTL prune piggyback
- [x] **Tester `BioCoverageDumper.kt`** — debug-only probe, returns `Report` with row-per-type (sample count, unique sources, time span). Surfaces real coverage on Mi Band 10 + S24 (worst-case test target per `memory/user_hardware.md`)
- [x] Manifest: `<queries>` declaration for `com.google.android.apps.healthdata` package (Android 11+ visibility) + ACTION_SHOW_PERMISSIONS_RATIONALE intent declaration
- [ ] Manifest: `<intent-filter>` for rationale activity (Play Store ship requirement) — **DEFERRED to Phase 3 onboarding** when PrivacyRationaleActivity exists
- [ ] Privacy policy URL updated with bio-data section — **DEFERRED to Phase 3** alongside rationale activity
- [x] **Build check**: `:app:assembleDebug` green (2m 56s)
- [ ] **Device test**: install on S24, pair Mi Band 10, capture 48h, verify dumper output — **DEFERRED** (requires physical wearable wear-time, not blocking Phase 2)

---

## Phase 2 — Trust & Sovereignty (data layer) ✅ DONE 2026-05-11; UI deferred to 2.UI follow-up
**Goal**: Transparency + GDPR atomic operations live.
**Est**: 3-4 days. **Actual data-layer: same day**. UI: separate focused commit.
**Status**: ✅ DATA LAYER DONE / ⬜ UI PENDING

Data layer (this commit):
- [x] SQLDelight schemas (`BioSignal.sq` + `BioAggregate.sq` + `BioConsent.sq`)
- [x] `BioSignalRepositoryImpl` — full impl (observeRawSamples Flow, getAggregate/observeAggregate, ingestFromProvider with daily aggregate computation, syncPendingAggregates STUB for Phase 4, exportAll Art.20, deleteAll Art.17 atomic, pruneExpiredSamples TTL, logConsentEvent audit)
- [x] `@Serializable` on BioSignal sealed + 7 subtypes + DataConfidence + BioSignalSource + SleepStage + BioAggregate
- [x] Koin wiring: new `bioSignalModule` in MongoKoinModule, added to `allKoinModules` in app/di/KoinModules.kt
- [x] GDPR Art.17 atomic delete (local SQLDelight; server-side fan-out deferred to Phase 4)
- [x] GDPR Art.20 export — JSON includes raw_samples + aggregates + consent_log
- [x] Local consent audit log (`bio_consent_log` table) — server push deferred to Phase 4
- [x] `:app:assembleDebug` verde 17s

UI (deferred to 2.UI follow-up commit):
- [ ] `BioContextScreen.kt` — transparency dashboard (data inventory + sources + last sync + CTA)
- [ ] Settings → Bio-signals section (granular per-type toggles)
- [ ] Revoke flow UI: per-type permission + local data wipe (default) / preserve-readonly (toggle)
- [ ] New `:features:biocontext` Gradle module + Decompose destination
- [ ] `Strings.BioContext` i18n facade (~30 keys × 12 langs)

Deferred to Phase 4 (server-side):
- [ ] Server-side `bio_consent_log/{userId}/{timestamp}` audit trail (Ktor endpoint + Firestore)
- [ ] Real `syncPendingAggregates` (currently STUB returning 0)
- [ ] Server-side delete fan-out for Art.17

Smoke test deferred until UI exists (Phase 2.UI):
- [ ] **Smoke test**: grant → use → revoke → verify local + server clean

---

## Phase 3 — Onboarding
**Goal**: Dedicated opt-in section in onboarding flow.
**Est**: 2-3 days
**Status**: ⬜ NOT STARTED

- [ ] `BioOnboardingScreen.kt` — 5-step pager
  - [ ] Step 1: Intro (empatica, niente sales-y)
  - [ ] Step 2: What we collect (per-data-type list with icons)
  - [ ] Step 3: Why (use-case-driven explanation per signal)
  - [ ] Step 4: Health Connect permission flow + install fallback
  - [ ] Step 5: Confirmation + skip-it-all CTA
- [ ] Decompose destination `BioOnboardingRoute` + child in `RootComponent`
- [ ] Skip path: marks `bioOnboardingSkipped=true` in user prefs, re-accessible from Settings
- [ ] i18n keys: ~20 in `Strings.BioOnboarding`
- [ ] **Smoke test**: complete path + skip path both work

---

## Phase 4 — Server-Side Aggregates
**Goal**: 4 REST endpoints live + Firestore schema + indexes.
**Est**: 4-6 days
**Status**: ⬜ NOT STARTED

- [ ] `calmify-server/service/BioSignalService.kt`
- [ ] `calmify-server/routes/BioSignalRoutes.kt`
  - [ ] `POST /api/v1/bio/ingest` (Protobuf CN + JSON fallback)
  - [ ] `GET /api/v1/bio/aggregate?range=&types=`
  - [ ] `DELETE /api/v1/bio/all` (Art.17)
  - [ ] `GET /api/v1/bio/export` (Art.20)
- [ ] Firestore schema: `bio_aggregates/{userId}/{daily|weekly|monthly}/{period}`
- [ ] Composite indexes in `firestore.indexes.json` (PRE-DEPLOY — lezione Phase 5)
  - [ ] `bio_aggregates/{userId}/daily` ORDER BY date DESC, type ASC, __name__ DESC
  - [ ] `bio_aggregates/{userId}/weekly` ORDER BY week DESC, __name__ DESC
  - [ ] `bio_consent_log/{userId}` ORDER BY timestamp DESC, __name__ DESC
- [ ] Rate limit applied (60 req/min/user on `/ingest`)
- [ ] Audit log on every mutation
- [ ] `BackendConfig.BIO_REST=false` flag (default off) — flip via Remote Config
- [ ] `KtorBioSignalRepository.kt` (data/network) client impl
- [ ] Koin module registration
- [ ] **Deploy**: commit+push → Cloud Build → smoke test new endpoints (NOT just /health)
- [ ] **Audit trail**: verify endpoints return 401 without token (smoke test pattern)

---

## Phase 5 — Wellness Integration (NO new tab)
**Goal**: Bio-signals enrich existing flows surgically.
**Est**: 5-8 days
**Status**: ⬜ NOT STARTED

- [ ] **Journal**: inline contextual card on new entry composer
  - [ ] "Stamattina il tuo HRV era leggermente più basso del solito" (dismissable)
- [ ] **Meditation**: post-session card (HR drop + HRV recovery confirmation, PRO gates AI narrative)
- [ ] **Insight**: cross-signal correlation panel (PRO gated)
- [ ] **Home Today**: single narrative card (MAI ring, MAI score)
- [ ] **Settings**: bio-signals section with granular toggles + privacy disclaimer
- [ ] **Build check**: all touched modules compile green
- [ ] **A11y check**: TalkBack reads narrative, not raw numbers

---

## Phase 6 — UI Calmify Design Language
**Goal**: Reusable components + visual review.
**Est**: 5-7 days
**Status**: ⬜ NOT STARTED

- [ ] `BioMetricCard` composable (in-range band + dotted typical-range overlay + emotion-aware copy)
- [ ] `BioNarrativeCard` composable (AI-generated, PRO gated)
- [ ] `ConfidenceFooter` composable (source + confidence chip)
- [ ] `BioOnboardingStepCard` composable
- [ ] Color-blind safe palette verified (simulator test)
- [ ] Reduced-motion support (charts fall back to static views)
- [ ] Visual review vs Calmify Design source (TBD: dedicated Figma/HTML mockup or iterate in-app)

---

## Phase 7 — Accessibility + i18n
**Goal**: A11y from day 1 + 12 langs.
**Est**: 3-4 days
**Status**: ⬜ NOT STARTED

- [ ] TalkBack: every metric narrated semantically (no raw number reading)
- [ ] `liveRegion = Polite` on narrative cards
- [ ] Keyboard shortcuts in `BioContextScreen` (ESC=close, TAB nav)
- [ ] ~100 keys × 12 langs (Phase J pattern)
- [ ] AR RTL verified via screenshot
- [ ] Color contrast WCAG AA verified
- [ ] **Strings facade groups**: `BioOnboarding` / `BioContext` / `BioMetric` / `BioNarrative` / `BioSettings`

---

## Phase 8 — PRO Tier Implementation
**Goal**: Monetization aligned with sustainable organism principle.
**Est**: 4-6 days
**Status**: ⬜ NOT STARTED

- [ ] Subscription gate on AI narrative cards + cross-correlation + cohort + PDF report
- [ ] Gemini integration server-side for narratives (cached 24h, rate-limited)
- [ ] Cloud Billing cost monitor alert (threshold TBD)
- [ ] Stripe `ManageSubscriptionCard` mentions "Bio Insight Avanzati"
- [ ] PRO badge visible on gated features (existing `ProBadge.kt` pattern)
- [ ] FREE-tier path: gated features show "Diventa PRO per insight intelligenti" CTA (non-hostile)
- [ ] **Smoke test**: FREE user sees gates correctly, PRO user sees full features

---

## Phase 9 — iOS Parity (HealthKit)
**Goal**: Same UX on iOS via HealthKit.
**Est**: 5-7 days
**Status**: ⬜ NOT STARTED

- [ ] `HealthKitProvider.kt` actual in iosMain (using HealthKit via Cinterop)
- [ ] `HealthKitMappers.kt` — HKQuantitySample/HKCategorySample → BioSignal
- [ ] iOS permission flow (Info.plist `NSHealthShareUsageDescription` + request flow)
- [ ] `BioContextScreen` unchanged (KMP success: pure commonMain UI)
- [ ] **Build check**: `compileKotlinIosSimulatorArm64` green
- [ ] **Device test**: iOS simulator + physical iPhone if available

---

## Phase 10 — Multi-device Validation
**Goal**: Coverage matrix across hardware tiers.
**Est**: 3-5 days
**Status**: ⬜ NOT STARTED (premium device acquisition gated on user)

- [ ] Worst-case: Mi Band 10 + S24 (sparse HRV, no continuous, baseline test)
- [ ] Mid-tier: Samsung Galaxy Watch 7 (Samsung Health native, S24 pair)
- [ ] Premium: Pixel Watch / Oura / Whoop (TBD user choice)
- [ ] iOS: Apple Watch / Oura via HealthKit
- [ ] Coverage matrix doc: which signals work per device with what confidence
- [ ] Regression suite checklist

---

## Cumulative Estimates

| Milestone | Days |
|---|---:|
| MVP Android-only (Phases 0-7) | 28-40 |
| + PRO tier (Phase 8) | +4-6 |
| + iOS parity (Phase 9) | +5-7 |
| + Multi-device validation (Phase 10) | +3-5 |
| **Total full-featured cross-platform** | **40-58** |

---

## Open Items (decide before Phase 6)

- [ ] **Branch name**: default suggested `bio-signal-integration`
- [ ] **Premium device for validation**: default suggested Galaxy Watch 7 (Samsung Health native + S24 pair)
- [ ] **PRO pricing impact**: default suggested "incluso nel PRO esistente"
- [ ] **AI narrative engine**: Gemini direct vs Vertex AI batch — decide in Phase 8

---

## Quality Gates (per commit)

Every commit on this workstream MUST satisfy:

1. ✅ `assembleDebug` green
2. ✅ Zero hardcoded strings (Detekt rule)
3. ✅ All 12 langs in sync
4. ✅ This tracker updated BEFORE commit (same atomic commit bundles code + tracker)
5. ✅ Commit message format: `<type>(biosignal): <what>` + Problems/Gains section
6. ✅ Smoke test specific change (not just `/health`)
7. ✅ Memory updated if architectural decision made

---

## Reference

- **Plan**: `.claude/BIOSIGNAL_INTEGRATION_PLAN.md`
- **Memory**:
  - `memory/feedback_calmify_values.md` — 7 product dogmas
  - `memory/user_hardware.md` — testing fleet
  - `memory/project_biosignal_integration.md` — project state
- **Quality mandate**: `CLAUDE.md` §QUALITY MANDATE
