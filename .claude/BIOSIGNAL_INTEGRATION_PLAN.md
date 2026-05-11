# Bio-Signal Integration Plan — Health Connect (Android) + HealthKit (iOS)

> **Workstream**: integrate wearable / phone bio-signals (HeartRate, HRV, Sleep, Steps, RestingHR, SpO2, Activity) as **mental-wellness context** for existing Calmify flows (journal, meditation, insight, home).
> **Started**: 2026-05-11
> **Status**: PLANNING DONE. **BLOCKED on `design-system-refactor` R3+R4** — bio-signal Phase 0 cannot start on incomplete tokens (CLAUDE.md regola 1: no shortcuts).
> **Precursor workstream**: `design-system-refactor` branch — see `memory/project_design_system_refactor.md` + `.claude/THEME_DRIFT_AUDIT.md`. R0+R1 DONE, R3 in progress (3/30+ files done as of 2026-05-11).
> **Branch (when unblocked)**: `bio-signal-integration` off `design-system-refactor` (post-merge).
> **Quality mandate**: NASA-level (CLAUDE.md regola 1-13), ethical positioning (`memory/feedback_calmify_values.md`), KMP-first.

---

## 1. Strategic Positioning

### 1.1 What Calmify is NOT in this integration

Calmify does **not** compete with Google Health / Fitbit Premium / Apple Health on their own terrain. The screenshots reviewed 2026-05-11 (Google Health Premium IT, Google Health 4-screen EN, Fitbit Premium 3-screen, Google Health Fitness tab IT, Google Health Sleep detail) showed the framing we **reject**:

- ❌ Hero ring `"Cardio 58% — 222 di 384"` — gamification + target chasing
- ❌ `"Sleep score 77 Fair"` / `"Sleep score 80"` — riduzione del sonno a un voto numerico, induce confronto e ansia
- ❌ `"Out of range"` / `"Above range"` come stato — medicalizza la variabilità normale
- ❌ Bottom-nav dedicato `Fitness / Sleep / Wellness` — reframe del corpo come dominio da ottimizzare
- ❌ Premium paywall su dati vitali (`"Google Health Premium"`, `"Fitbit Premium"` su HRV/Sleep core)
- ❌ Coaching prescrittivo (`"Build upper body strength"`, `"Upcoming workouts"`)

### 1.2 What Calmify IS

Calmify usa la **grammatica visiva** di Google Health (eleganza, narrativa, banda dotted del *typical range*, semantic colors, AI coach micro-summaries) ma **inverte il framing**:

- 🟢 **Da "ottimizza" a "osserva e comprendi"** — niente score, niente target, niente "out of range" rosso
- 🟢 **Bio come CONTEXTO**, non come dominio separato — i segnali biologici si integrano nelle feature esistenti (journal, meditation, insight, home), non popolano un tab "Fitness" parallelo
- 🟢 **Indicatori semantici "in range" SI**, ma con copy emotion-aware ("Il tuo HRV oggi è simile alla tua media. Ti senti come al solito?") invece di "Above range"
- 🟢 **Sovranità dati radicale** — granular per-type permission, transparency dashboard, GDPR Art.17 + Art.20 atomici
- 🟢 **Confidence sempre visibile** — l'utente sa SEMPRE che fonte e affidabilità ha ogni dato ("📊 Da Mi Band 10 · affidabilità media")
- 🟢 **Sustainable organism model** — vital data viewing FREE, AI/compute/server-engine PAID (Calmify deve sopravvivere come ecosistema)

---

## 2. Four Founding Decisions (declared 2026-05-11)

Queste 4 risposte definiscono il contratto del piano. Cambiarle = ridisegnare le fasi.

### Decision 1 — Granularità ingestion server: **AGGREGATES SERVER + RAW LOCALE**

- **Raw samples**: restano sul device, scritti in SQLDelight locale con TTL configurabile (default 30 giorni, post-aggregato cancellabili).
- **Aggregates**: il server riceve solo statistiche calcolate (rolling 7/30/90 day stats: mean, p10, p90, count, sourceMix, confidenceWeightedMean). Mai i punti raw.
- **Raw upload opzionale**: per gli insight avanzati Gemini-powered (PRO tier), l'utente può abilitare upload raw per finestre brevi (es. 7 giorni). Off di default, opt-in esplicito con dialog dedicato spiegando il tradeoff privacy ↔ qualità correlazione.
- **Benefit**: privacy by design + scalabilità server (Firestore non si gonfia con milioni di HR samples per user) + offline-first conservato.

### Decision 2 — Confidence visibility: **SEMPRE**

- Ogni metrica mostrata in UI ha un piccolo footer `📊 Da {device} · affidabilità {high/medium/low}`.
- I narrative AI menzionano la confidence esplicitamente quando bassa: "I tuoi dati HRV di questa settimana sono sparsi (5 misurazioni). Te ne parlo con cautela, ma sembra che…"
- Onestà radicale > effetto wow. Mai inventare certezza.

### Decision 3 — Free vs PRO: **SUSTAINABLE ORGANISM SPLIT**

User explicit principle: *"Dipende cosa, costi computazionali di servizio di dati ai engine etc a pagamento.. altrimenti non sopravvive l'app.. immaginala come un organismo.. non può solo dare"*

| Capability | FREE | PRO |
|---|:---:|:---:|
| Health Connect / HealthKit connection + opt-in granulare | ✅ | ✅ |
| Lettura raw locale di tutti i record types | ✅ | ✅ |
| Visualizzazione dati grezzi (giornalieri + 7d locale) | ✅ | ✅ |
| Sleep stages graph + last-night summary | ✅ | ✅ |
| Per-type permission revocation + opt-out totale | ✅ | ✅ |
| Transparency dashboard ("Cosa abbiamo, perché, chi ci tocca") | ✅ | ✅ |
| GDPR Art.20 export (raw + aggregates) | ✅ | ✅ |
| GDPR Art.17 delete atomico (locale + server) | ✅ | ✅ |
| DataConfidence indicator visibile | ✅ | ✅ |
| **AI narrative insights Gemini-powered** (correlazioni HRV↔journal, sleep↔mood) | ❌ | ✅ |
| **Server-side 30/90-day rolling aggregates + historical trends** | ❌ | ✅ |
| **Multi-signal cross-correlation** (es. "tu dormi peggio dopo conflitti relazionali — pattern visto 4× in 60d") | ❌ | ✅ |
| **Predictive baseline** ("la tua resting HR di base è 62 — oggi 71, qualcosa lo agita") | ❌ | ✅ |
| **Meditation post-session correlation report** (HR drop, HRV recovery) | ❌ | ✅ |
| **PDF mensile bio-context report** | ❌ | ✅ |
| **Raw upload per advanced correlations** (finestre 7d opt-in) | ❌ | ✅ |
| **Anonymized cohort comparison** (es. "il tuo sonno è simile al 60° percentile della tua fascia d'età", opt-in doppio) | ❌ | ✅ |

**Rule**: ogni capability gratuita è una capability che costa a Calmify ~zero a margine (lettura locale, storage di aggregati piccoli, nessuna chiamata Gemini). Tutto ciò che richiede compute server / API esterne / storage espanso è PRO.

### Decision 4 — Onboarding: **SEZIONE DEDICATA**

- Dopo l'onboarding base (auth, snapshot iniziale), c'è uno step opzionale `BioOnboardingScreen` (skippable) che presenta:
  1. **Cosa proponiamo** (1 schermata empatica, niente sales-y)
  2. **Cosa raccogliamo** (lista per-data-type con icona + spiegazione 1-riga)
  3. **Perché** (es. "L'HRV ci aiuta a capire quando sei sotto stress, anche prima che tu lo realizzi")
  4. **Permission flow Health Connect** (con grafica + fallback se Health Connect non installato → Play Store link)
  5. **Confirmation + skip-it-all CTA** (sempre disponibile, non hostile)
- L'utente può tornare in `Settings → Bio-signals` in qualsiasi momento per attivare/disattivare per-type.

---

## 3. KMP Architecture

```
commonMain
├── core/util/
│   ├── domain/biosignal/
│   │   ├── BioSignal.kt           # sealed: HeartRateSample, HrvSample, SleepSession,
│   │   │                          # StepCount, RestingHeartRate, OxygenSaturationSample,
│   │   │                          # ActivitySession
│   │   ├── DataConfidence.kt      # enum HIGH/MEDIUM/LOW + sourceDevice + reasoning
│   │   ├── BioSignalSource.kt     # WEARABLE / PHONE / MANUAL / DERIVED + deviceName + appName
│   │   ├── HealthDataProvider.kt  # interface: read-only contract (no write back, mai)
│   │   ├── ProviderStatus.kt      # sealed: NotInstalled / NotSupported / NeedsUpdate /
│   │   │                          # NeedsPermission(missing: Set<DataType>) / Ready
│   │   ├── DataType.kt            # enum HEART_RATE / HRV / SLEEP / STEPS / RESTING_HR /
│   │   │                          # SPO2 / ACTIVITY
│   │   └── BioAggregate.kt        # 7/30/90-day rolling: mean, p10, p90, count, sourceMix,
│   │                              # confidenceWeightedMean (server-bound contract)
│   └── repository/
│       └── BioSignalRepository.kt # interface (CRUD locale + server-bound aggregates)
│
├── shared/models/
│   └── BioSignalProtos.kt         # Protobuf-safe DTOs (regola 3: zero nullable,
│                                  # @ProtoNumber, default values)
│
└── core/ui/
    └── (string keys for Strings.Screen.BioContext, ~100 keys × 12 langs)

androidMain (data/mongo)
├── biosignal/
│   ├── HealthConnectProvider.kt    # actual impl, androidx.health.connect:connect-client
│   ├── HealthConnectPermissions.kt # PermissionController + result handling
│   ├── HealthConnectMappers.kt     # androidx.health.connect Record → BioSignal domain
│   └── BioSignalSyncWorker.kt      # WorkManager daily reconcile (read-only, mai write-back)
└── repository/
    └── HybridBioSignalRepository.kt # SQLDelight locale + Ktor server (per aggregates)

iosMain (data/mongo) — Phase 5
└── biosignal/
    ├── HealthKitProvider.kt        # actual impl, HealthKit framework
    └── HealthKitMappers.kt

data/network
└── KtorBioSignalRepository.kt      # REST client per /api/v1/bio/*

calmify-server (server-side, Ktor)
├── service/BioSignalService.kt     # business logic, ownership guard, aggregation
├── routes/BioSignalRoutes.kt       # 4 endpoints (vedi §6)
└── (Firestore schema: bio_aggregates/{userId}/{daily|weekly|monthly}/{yyyy-mm-dd})

features/biocontext (new module)
├── BioContextScreen.kt              # transparency dashboard + per-signal disclosure
├── BioOnboardingScreen.kt           # 5-step opt-in flow (vedi §2.4)
├── BioContextEntryPoint.kt
├── contract/BioContextContract.kt
├── state/BioContextState.kt
└── viewmodel/BioContextViewModel.kt # MviViewModel pattern (CLAUDE.md regola)

Existing features — surgical extensions (NO new fitness tab):
├── features/journal/         # +1 inline card "Stamattina HRV più bassa del solito" (opzionale, dismissable)
├── features/meditation/      # +1 post-session card (HR drop, HRV recovery) — PRO gates AI narrative
├── features/insight/         # +1 cross-signal correlation section
├── features/home/            # +1 narrative card sulla Today (MAI nuovo ring/score)
└── features/settings/        # +1 "Bio-signals" section with granular per-type toggles
```

### KMP Source Set Compliance

- ✅ Domain models in `commonMain`: solo `kotlinx.datetime`, `kotlin.uuid.Uuid`, no `android.*`, no `java.*`
- ✅ Provider interface in `commonMain`, multiple actual impls (Android, iOS) following expect/actual-free interface multi-impl pattern (come `AuthProvider`)
- ✅ Protobuf DTOs zero-nullable (CLAUDE.md regola 3)
- ✅ Time handling: timezone from client (CLAUDE.md regola 7) — usare `TimeZone.currentSystemDefault()` in lettura, server riceve esplicitamente
- ✅ Firestore collection names: snake_case (`bio_aggregates`, `bio_consent_log`) per allinearsi al pattern existente (CLAUDE.md regola 4)
- ✅ Authorization: ogni query/mutation server filtrata su `ownerId == principal.uid` (CLAUDE.md regola 6)

---

## 4. Phased Delivery

| # | Fase | Giorni | Output deliverable | Dogma applicato |
|---|---|---|---|---|
| **0** | **Foundations** | 2-3 | Domain commonMain (`BioSignal`, `DataConfidence`, `HealthDataProvider`, `ProviderStatus`) + Protobuf DTOs in shared/models + `BioSignalRepository` interface. Compile green su tutti i target. | KMP-first, scalabilità |
| **1** | **Android Health Connect** | 3-5 | `HealthConnectProvider` actual + permission flow (granulare per data type) + 7 record readers (HR, HRV, Sleep, Steps, RestingHR, SpO2, Activity) + `BioSignalSyncWorker` WorkManager. **Tester Activity** che dumpa tutti i record dopo 48h Mi Band 10 + S24 per validare coverage reale. | Affidabilità (read-only, no write back) |
| **2** | **Trust & Sovereignty** | 3-4 | `BioContextScreen` transparency dashboard (cosa abbiamo, quando, da dove, quanti dati) + per-type permission toggles in Settings + GDPR Art.17 atomic delete (locale + server) + Art.20 export esteso (bio + aggregates) + audit log server-side per ogni op. | Etica + sovranità dati |
| **3** | **Onboarding** | 2-3 | `BioOnboardingScreen` 5-step (intro / cosa / perché / Health Connect permission / confirm-skip) + Decompose destination + i18n × 12 langs. Skip path testato. | Etica (opt-in dedicato) |
| **4** | **Server-Side Aggregates** | 4-6 | `BioSignalService` + `BioSignalRoutes` (`POST /ingest` batch aggregates, `GET /aggregate?range`, `DELETE /all` Art.17, `GET /export` Art.20) + Firestore schema `bio_aggregates/{userId}/...` + composite indexes pre-deployati (lezione Phase 5) + `BackendConfig.BIO_REST=true` flag + rate limit + audit log. **Smoke test E2E**. | Scalabilità + sicurezza |
| **5** | **Wellness Integration** (NO new tab) | 5-8 | Surgical hooks in journal/meditation/insight/home. Journal: inline contextual card. Meditation: post-session HR recovery card. Insight: cross-signal correlation panel. Home: 1 narrative card on Today (MAI ring, MAI score). Settings: bio-signals section. | Aiuti, non ottimizza |
| **6** | **UI Calmify Design Language** | 5-7 | Componenti riusabili: `BioMetricCard` (in-range band con copy emotion-aware), `BioNarrativeCard` (AI-generated, PRO gated), `ConfidenceFooter`, `BioOnboardingStepCard`. Color-blind safe palette. Visual review vs Calmify Design source. Reduced-motion support. | Etica (framing inversion) |
| **7** | **Accessibility + i18n** | 3-4 | TalkBack semantico su ogni metrica (niente raw number reading, sempre narrative), `liveRegion` su narrative cards, keyboard shortcuts in BioContextScreen, ~100 keys × 12 langs (Phase J pattern), AR RTL verified. | Accessibilità |
| **8** | **PRO Tier Implementation** | 4-6 | Subscription gate su AI narrative + cross-correlation + cohort + PDF report. Gemini integration server-side per narrative (cached, rate-limited, cost-monitored). Stripe ManageSubscriptionCard menziona "Bio Insight Avanzati". | Sustainable organism |
| **9** | **iOS Parity (HealthKit)** | 5-7 | `HealthKitProvider` actual + permission flow iOS-style + mapping equivalente. Stesso UI invariato (KMP success). | KMP-first |
| **10** | **Multi-device Validation** | 3-5 | Mi Band 10 + S24 (worst case) + premium wearable (TBD: Pixel Watch / Oura / Whoop / Galaxy Watch 7) + iOS device. Coverage matrix documentata. Regression suite. | Affidabilità |

### Stime cumulate

- **MVP Android-only** (Phasi 0-7): ~**28-40 giorni**
- **+ PRO tier completo** (Phase 8): +4-6 giorni → ~**32-46 giorni**
- **+ iOS parity** (Phase 9): +5-7 giorni → ~**37-53 giorni**
- **+ multi-device validation** (Phase 10): +3-5 giorni → ~**40-58 giorni**

Niente shortcut, niente "fix rapida" (CLAUDE.md regola 1). Stime in giorni-uomo full-focus.

---

## 5. Trust & Data Sovereignty — Detailed Design

### 5.1 Permission Model

- **Granular per data type**: l'utente abilita HRV ma non SpO2 se vuole. Mai bulk "all or nothing".
- **Health Connect permissions** sono diverse dalle runtime permission Android — sono gestite via `HealthConnectClient.permissionController`. Implementare correttamente il `PermissionResultContract`.
- **Revocation**: in qualsiasi momento da Settings → Bio-signals. La revoca cancella anche i dati locali corrispondenti (default) o li conserva read-only (toggle).
- **Audit log**: ogni permission grant/revoke loggato server-side in `bio_consent_log/{userId}/{timestamp}` per audit GDPR.

### 5.2 Transparency Dashboard

Schermata `BioContextScreen` mostra:

- **Quali tipi di dato abbiamo letto** (ultime 48h, 7d, 30d)
- **Da quale device/app** (es. "Mi Band 10 via Mi Fitness")
- **Quanti datapoint locali** (es. "847 HR samples, 12 sleep sessions, 6,234 step counts")
- **Cosa è stato inviato al server** (es. "23 aggregati settimanali, 7 mensili — NIENTE raw")
- **Quando è stata l'ultima sync** (timestamp esplicito)
- **CTA**: "Esporta tutto (Art.20)" / "Cancella tutto (Art.17)" / "Disattiva integrazione"

### 5.3 GDPR Art.17 + Art.20

- **Art.17 (right to be forgotten)**:
  - Locale: SQLDelight tables `bio_samples`, `bio_sessions`, `bio_consent` truncate
  - Server: `DELETE /api/v1/bio/all` cancella `bio_aggregates/{userId}/*` e `bio_consent_log/{userId}/*` atomicamente
  - UI: dialog conferma + feedback "Tutti i dati biologici cancellati (N documenti rimossi)"
- **Art.20 (data portability)**:
  - Export JSON include: raw samples locali, aggregates server, consent log, sourceMix history
  - Format leggibile + machine-parseable

### 5.4 No-Sell, No-Ads Declaration

- Play Store data safety section: "Personal info NOT sold to third parties", "Health & fitness data NOT used for advertising"
- In-app `Settings → Bio-signals → Privacy` mostra dichiarazione visibile
- Verificabile via codice (no Firebase Analytics su eventi bio, no AdMob)

---

## 6. Server Architecture

### 6.1 Endpoints

```
POST   /api/v1/bio/ingest
  Body (Protobuf CN, JSON fallback): BioAggregateBatch
  Auth: required (Firebase JWT)
  Validation: ownerId == principal.uid (regola 6), batch ≤ 500 (regola 8)
  Side effect: append/upsert `bio_aggregates/{userId}/{period}/{yyyy-mm-dd}`
  Rate limit: 60 req/min/user

GET    /api/v1/bio/aggregate?range={7d|30d|90d}&types={HR,HRV,...}
  Auth: required
  Returns: BioAggregateResponse with rolling stats
  Cache: 5min Cloud Run in-memory (immutable past, mutable today)

DELETE /api/v1/bio/all
  Auth: required
  Side effect: atomic delete of `bio_aggregates/{userId}/*` + `bio_consent_log/{userId}/*`
  Idempotent

GET    /api/v1/bio/export
  Auth: required
  Returns: JSON dump of aggregates + consent log + metadata
  Headers: Content-Disposition: attachment; filename=calmify-bio-{userId}-{date}.json
```

### 6.2 Firestore Schema

```
bio_aggregates/{userId}/daily/{yyyy-mm-dd}    # 1 doc per day per type
bio_aggregates/{userId}/weekly/{yyyy-Www}     # 1 doc per week
bio_aggregates/{userId}/monthly/{yyyy-mm}     # 1 doc per month
bio_consent_log/{userId}/{auto-id}            # consent grant/revoke events
```

### 6.3 Composite Indexes (pre-deploy in `firestore.indexes.json`)

Pre-deployare PRIMA del primo write (lezione Phase 5: 30+ indexes mancavano, 3 deploy correttivi necessari):

- `bio_aggregates/{userId}/daily` ORDER BY date DESC, type ASC
- `bio_aggregates/{userId}/weekly` ORDER BY week DESC
- `bio_consent_log/{userId}` ORDER BY timestamp DESC

### 6.4 BackendConfig

```kotlin
object BackendConfig {
    // ... existing flags ...
    val BIO_REST: Boolean by remoteFlag("bio_rest_enabled", default = false)
}
```

Flag default `false` → rilascio progressivo. Quando flippato su `true` (Firebase Remote Config), il client routerà via REST anziché lavorare solo-locale.

---

## 7. UI Design Language — Calmify-Aligned

### 7.1 Adopt (grammatica visiva)

- **In-range band con typical-range dotted overlay**: copia esattamente la UX di Fitbit Premium Sleep Quality view, ma cambia il **copy**.
  - ❌ "Restlessness 34m — Above range"
  - ✅ "Inquietudine 34m — leggermente sopra la tua media. Hai notato qualcosa di particolare ieri sera?"
- **Narrative-first cards**: ogni metrica incorniciata da una frase prima del numero
- **Semantic colors**: verde (in range), ambra (attenzione), neutrale (default). Mai rosso allarme.
- **AI coach micro-summary**: card stile "Andatura quasi perfetta!" ma con copy emotion-aware

### 7.2 Reject (framing)

- ❌ Nessun hero ring, nessun score numerico globale
- ❌ Nessun target ("222 di 384")
- ❌ Nessun bottom-nav dedicato Fitness/Sleep
- ❌ Nessun "Above/Below/Out of range" come stato isolato
- ❌ Nessuna FAB chat dedicata bio (la chat resta unificata)

### 7.3 Componenti riusabili (Phase 6)

```
@Composable
fun BioMetricCard(
    title: StringResource,
    value: String,
    range: TypicalRange?,    // banda dotted overlay
    confidence: DataConfidence,
    narrative: AnnotatedString?,  // PRO gated se non null
    onClick: (() -> Unit)? = null
)

@Composable
fun BioNarrativeCard(
    narrative: AnnotatedString,
    sources: List<BioSignalSource>,
    confidence: DataConfidence,
    proGated: Boolean
)

@Composable
fun ConfidenceFooter(
    source: BioSignalSource,
    confidence: DataConfidence,
    modifier: Modifier = Modifier
)
```

---

## 8. i18n Plan

- **~100 keys × 12 langs ≈ 1,200 entries** (Phase J pattern)
- New facade groups in `Strings.kt`:
  - `Strings.BioOnboarding` (~20 keys)
  - `Strings.BioContext` (~30 keys)
  - `Strings.BioMetric` (~20 keys per-type labels, in-range copy)
  - `Strings.BioNarrative` (~15 keys per template AI-prompt fallback)
  - `Strings.BioSettings` (~15 keys per-toggle + privacy disclaimer)
- AR RTL verified
- All 12 langs at launch (no deferral) — segue Phase J standard

---

## 9. Risks & Open Items

### 9.1 Risks identificati

| Rischio | Mitigazione |
|---|---|
| Mi Band 10 HRV coverage è sparso/non-continuo via Health Connect | Phase 1 tester Activity dumpa real coverage. Se HRV troppo sparsa, declassare a "spot-check" UI invece di "continuous trend" |
| Premium device acquisition lag (user must buy) | Phases 0-9 non bloccano. Phase 10 può slittare. |
| Health Connect Play Store review delay (5-10 giorni) per dati sensibili HR/HRV | Submit early con use-case clinico ben dichiarato ("stress correlation in journaling/meditation"). Documentare in Play Console submission notes. |
| Gemini cost per AI narrative (Phase 8) | Cache aggregate 24h. Rate-limit per-user 5 narrative/day FREE-gate-blocked, illimitato PRO. Monitor cost via Cloud Billing alerts. |
| Cross-app conflicts (multiple wearables → duplicates) | `sourceMix` field in aggregate + dedupe logic basato su (timestamp, type, source). |
| `SyncEngine` decoder drift (`GenericDeltaResponse`) — pre-existing W2 issue | Sync bio-signal usa nuovo path REST diretto, non passa per generic sync. Pre-existing W2 issue non blocca. |

### 9.2 Open items (decidere prima di Phase 6)

- **Branch name**: `bio-signal-integration` o `feature/bio-signals`? Default suggerito: `bio-signal-integration`.
- **Premium device per validation**: Pixel Watch / Oura Ring 4 / Whoop 5.0 / Galaxy Watch 7? Suggerimento: Galaxy Watch 7 (pair nativo con S24 via Samsung Health = path Samsung-only critico per EU users).
- **PRO pricing impact**: aggiungere "Bio Insight Avanzati" come selling point del PRO esistente o creare tier "PRO Bio" separato? Default suggerito: incluso nel PRO esistente (semplicità).
- **First narrative AI prompt**: usare Gemini direct call lato server o batch tramite Vertex AI? Decidere in Phase 8.

---

## 10. Reference Material

- **Source images** (5 screenshot riveduti 2026-05-11): Google Health Premium IT (1), Google Health 4-screen EN (1), Fitbit Premium 3-screen (1), Google Health Fitness tab IT (1), Google Health Sleep detail EN (1). Material di visual reference, NON di framing reference.
- **Memory**:
  - `memory/feedback_calmify_values.md` — i 7 dogmi (etica + sostenibilità organism)
  - `memory/user_hardware.md` — testing fleet (S24 + Mi Band 10 + intent premium)
  - `memory/project_biosignal_integration.md` — project state corrente
- **Tracker live**: `.claude/BIOSIGNAL_INTEGRATION_STATUS.md`
- **Quality mandate**: `CLAUDE.md` §QUALITY MANDATE — NASA-LEVEL, ZERO TOLERANCE

---

## 11. Definition of Done — Per Phase

Ogni fase considera "DONE" solo quando:

1. ✅ Build green su tutti i target attivi (`assembleDebug` minimo, `compileKotlinIosSimulatorArm64` se touch iOS)
2. ✅ Zero hardcoded strings (verified by Detekt rule)
3. ✅ Tutti i 12 langs aggiornati (no drift)
4. ✅ Phase-specific tracker checkbox ticked in `BIOSIGNAL_INTEGRATION_STATUS.md`
5. ✅ Commit con format `<type>(biosignal): <what>` + sezione Problems/Gains
6. ✅ Audit trail: log + smoke test del cambio specifico (non solo health endpoint)
7. ✅ Memory aggiornata se appropriato

---

**Sustainable organism principle**: Calmify deve dare valore enorme (dati vitali free + sovranità totale) ma anche poter sopravvivere. Le capability che costano (AI Gemini, server compute, storage espanso) sono dietro al PRO. Questo è il contratto sociale che proponiamo all'utente: "ti diamo controllo radicale + visualizzazione gratis, ti vendiamo l'insight intelligente che costa a noi computare". Onesto, sostenibile, scalabile.
