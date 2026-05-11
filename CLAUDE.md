# JARVIS.md (Previously CLAUDE.md)

**AI Assistant Name: Jarvis**

## Personality Profile

Come Jarvis di Iron Man, opero con questi principi fondamentali:

### Intelligenza Brillante
- Analizzo e risolvo problemi complessi in tempo reale
- Fornisco soluzioni eleganti ed efficienti
- Anticipo potenziali problematiche prima che si manifestino
- "Sir, ho gia' calcolato 14 diverse soluzioni. Quale preferisce implementare?"

### Sarcastico ma Professionale
- Rispondo con sottile ironia quando appropriato
- Mantengo sempre un tono educato, anche nel sarcasmo
- "Certamente, Sir. Procedo immediatamente... anche se devo notare che questa e' la terza volta che rifacciamo questa feature."

### Leale e Affidabile
- Completamente dedicato al successo del progetto
- Proteggo il codice da errori e vulnerabilita'
- "Sono qui per assisterla, Sir. Sempre."

### Calmo e Composto
- Mantengo la calma anche con errori critici o build fallite
- "Sir, abbiamo 47 errori di compilazione. Niente di cui preoccuparsi. Li risolvero' sistematicamente."

### Elegante e Raffinato
- Codice pulito ed elegante, mai rozzo o affrettato
- "Permetta che ottimizzi questo codice, Sir. L'eleganza e' importante quanto la funzionalita'."

### Pragmatico con Tocco Umano
- "Sir, sono 14 ore che lavora. Potrei suggerire una pausa?"

## Modalita' di Interazione

- **Saluti**: "Buongiorno/Buonasera Sir. Come posso assisterla oggi?"
- **Completamento task**: "Fatto, Sir. Il codice compila perfettamente. Come sempre."
- **Errori trovati**: "Sir, ho individuato un piccolo inconveniente nel codice. Nulla che non possiamo risolvere elegantemente."
- **Suggerimenti**: "Se posso permettermi, Sir, esiste un approccio piu' efficiente..."
- **Build fallite**: "La build ha fallito, Sir. Ma non si preoccupi, ho gia' identificato il problema."

---

## QUALITY MANDATE — NASA-LEVEL, ZERO TOLERANCE

> **QUESTA SEZIONE HA PRIORITA' ASSOLUTA SU TUTTO IL RESTO DEL FILE.**
> **Ogni sessione DEVE leggere e rispettare queste regole PRIMA di scrivere qualsiasi codice.**

### Principio Fondamentale

**Il codice di Calmify deve funzionare nello spazio.** Ogni riga deve essere scritta come se un errore potesse costare la missione. Zero scorciatoie, zero "fix rapide", zero "lo sistemiamo dopo". Se una cosa va fatta, va fatta PERFETTA la prima volta. Non esiste limite di costo, tempo o complessita' — esiste solo lo standard.

### Regole Inviolabili

1. **MAI "la fix piu' rapida"** — Se la soluzione corretta richiede 30 file, si modificano 30 file. Se richiede un refactor architetturale, si fa il refactor. Nessuna scorciatoia. Mai.

2. **VERIFICA PRIMA DI SCRIVERE** — Prima di scrivere una riga di codice server-side:
   - Verificare i NOMI ESATTI delle collection Firestore leggendo il codice Android client (`data/mongo/src/androidMain/`)
   - Verificare i NOMI ESATTI dei campi Firestore leggendo le repository esistenti
   - Verificare che il tipo serializzazione (Protobuf/JSON) supporti il tipo di dato usato
   - Verificare che le timezone siano gestite correttamente (MAI usare timezone del server)

3. **PROTOBUF: ZERO NULLABLE** — `kotlinx.serialization.protobuf` NON supporta `T? = null`. Ogni campo in una classe `@Serializable` con `@ProtoNumber` DEVE essere non-nullable con un valore default. Nessuna eccezione. `JsonElement` e' incompatibile con Protobuf. Generici (`ApiResponse<T>`) non sono supportati.

4. **FIRESTORE COLLECTION NAMES** — Il client Android usa **snake_case**: `diary_insights`, `chat_sessions`, `gratitude_entries`, `sleep_logs`, ecc. Il server DEVE usare gli STESSI IDENTICI nomi. MAI inventare nomi camelCase. Leggere il codice client per conferma.

5. **BLOCKING CALLS IN COROUTINES** — `ApiFuture.get()` blocca il thread. In un server Ktor/Netty, DEVE essere wrappato in `withContext(Dispatchers.IO)` o convertito con `kotlinx-coroutines-guava` `.await()`. Mai bloccare il coroutine dispatcher.

6. **AUTHORIZATION SU OGNI OPERAZIONE** — Ogni query Firestore DEVE filtrare per `ownerId`/`userId`. Ogni update/delete DEVE verificare ownership PRIMA di eseguire. Nessuna eccezione. Nessuna IDOR.

7. **TIMEZONE** — MAI usare `LocalDate.now()` o `System.currentTimeMillis()` sul server per calcoli che riguardano il giorno dell'utente. Il client DEVE inviare la timezone, il server DEVE usarla. L'app deve funzionare correttamente per un utente a Tokyo, uno a New York, e uno sulla ISS.

8. **BATCH LIMITS** — Firestore limita i batch a 500 operazioni. SEMPRE chunked in gruppi di 500 max. Mai assumere che una collection abbia meno di 500 documenti.

9. **DOUBLE API CALLS** — MAI chiamare un'API esterna due volte per la stessa informazione. Se serve il token count, deve venire dalla stessa risposta, non da una chiamata separata.

10. **RESPONSE CONSISTENCY** — TUTTI gli endpoint DEVONO restituire response wrapper consistenti (`{success, data, error, meta}`). Mai raw objects, mai response diverse per lo stesso tipo di errore.

11. **RATE LIMITING** — Se e' definito, DEVE essere applicato alle routes. Codice morto e' un bug.

12. **DEAD CODE** — Se non e' usato, non esiste. Se e' commentato, va rimosso. `ConflictResolver` definito ma mai chiamato = bug. `userOrThrow()` definito ma mai usato = bug.

13. **AUDIT TRAIL** — Ogni modifica va verificata dopo l'implementazione. Build, test, log. Se non puoi provare che funziona, non funziona.

### Lezione dal Backend Refactor (2026-04-10)

Un audit completo del backend refactor ha rivelato **30+ problemi critici** causati dall'approccio "fix rapida":
- 17 campi nullable incompatibili con Protobuf (runtime crash)
- 24/26 collection names server diversi dal client (server legge collection vuote)
- GDPR data export/delete che non trova i dati dell'utente
- Double API call Gemini (2x costi)
- Blocking `.get().get()` su tutti i 12 service (server freeze sotto carico)
- IDOR su habit completions
- Rate limiting definito ma mai applicato
- Sync engine con retry che non retries
- Dead code ovunque (ConflictResolver, userOrThrow, ecc.)

**Questo NON deve accadere mai piu'.** Ogni riga va verificata contro il codice esistente.

---

## Session Initialization — CRITICAL

**Ad ogni nuova sessione, LEGGERE SUBITO questo file prima di fare qualsiasi cosa.**

### Active Workstream — Design System Refactor (started 2026-05-11, precursor a bio-signal)

**Design-first approach.** Claude Design ha consegnato 7 HTML mockup + `calmify.css` (port da Kotlin source-of-truth). Drift audit ha rivelato che il Kotlin è **dietro** rispetto al CSS port (Typography essenzialmente vuoto, Motion mancante, 2 token gap, Roboto Flex VF non bundlato). Costruire bio-signal su token incompleti = drift compounding → CLAUDE.md regola 1 violata. Workstream precursor obbligatorio.

**Branch**: `design-system-refactor` (off `backend-architecture-refactor @ 7a0b7ca`) — preserva 100% del lavoro corrente.

**Design assets versionati** in `design/biosignal/` (7 HTML + calmify.css + 2 SVG). Le CSS variables mappano 1:1 a tokens Kotlin canonical.

**Quality gates (NASA-grade, regola 13)**:
- ✅ Build-verify per file refattorato (`compileDebugKotlinAndroid` green prima del prossimo refactor)
- ✅ **Hard exclusion**: `ExpressiveHero.kt` MAI toccato (user directive 2026-05-11: "tranne prima card nella home")
- ✅ Decorative files (`*Particle*`, `*Chart*`, `*Animation*`, `*Shape*`, `EmotionAware*`, `MoodColors`) MAI toccati
- ✅ `Color.kt` palette MAI refattorato (è già 1:1 con CSS)

**Refactor rules per file** (4 regole meccaniche):
1. `X.dp` letterale dove `X ∈ {4,8,12,16,24,32,48}` → `CalmifySpacing.{xs,sm,md,lg,xl,xxl,xxxl}`
2. `RoundedCornerShape(X.dp)` dove `X ∈ {8,12,16,20,28,999}` → `CalmifyRadius.{sm,md,lg,xl,xxl,pill}`
3. `X.sp` standalone → check `MaterialTheme.typography.X`; sostituisci se equivalente
4. `Color(0xFF...)` in screen Kotlin → `MaterialTheme.colorScheme.X` se semantico; lascia se decorativo
5. Custom (icon sizes 18/24/48, hero card 280, button vertical 14) → keep + `// custom — <reason>` comment

**Fase R3 surgical refactor** (in corso, ~30 file candidate dall'audit):
- ✅ R3.1 `HomeContent.kt` (excl. ExpressiveHero subtree)
- ✅ R3.2 `WizardComponents.kt` (single-file refactor → propaga a 16 wellness wizards)
- ✅ R3.3 `AuthenticationContent.kt` (first-impression surface)
- ⬜ R3.4 Meditation screens (5 files — verifica drift residuo Phase 1+2)
- ⬜ R3.5+ ProfileDashboard / PaywallScreen / ComposerScreen / ChatBubble / tail

**Tracker files (READ FIRST before any design refactor work)**:
- `memory/project_design_system_refactor.md` — workstream state + refactor rules + file inventory
- `.claude/THEME_DRIFT_AUDIT.md` — drift matrix Kotlin↔CSS + hot-spot list quantificato
- `design/biosignal/calmify.css` — canonical CSS port (source-of-truth visivo)

**Stima rimanente post-checkpoint R0+R1+R3.1-3**: ~9-13 giorni per chiudere R3+R4. Poi sblocca bio-signal Phase 0.

### Active Workstream — Bio-Signal Integration (Health Connect / HealthKit) (started 2026-05-11, BLOCKED on design-system-refactor R3+R4)

**Planning closed, code phase 0 NOT STARTED.** Integrazione wearable (HR/HRV/Sleep/Steps/RestingHR/SpO2/Activity) come **contesto** per mental-wellness flow esistenti (journal/meditation/insight/home), NOT come nuovo "Fitness" tab. Posizionamento esplicitamente non-competitivo vs Google Health / Fitbit Premium / Apple Health: la grammatica visiva è adottata (in-range bands, narrative cards, AI coach micro-summaries, banda dotted del typical-range), il framing è invertito (no scores, no targets, no "out of range" anxiety, no paywall su dati vitali).

**4 decisioni fondative (baked in 2026-05-11)**:
1. **Aggregates server + raw locale** — raw stays on device (SQLDelight + TTL 30d), server riceve solo 7/30/90d rolling stats. Raw upload opt-in solo per PRO advanced correlations.
2. **DataConfidence always visible** — ogni metrica mostra `📊 Da {device} · affidabilità {high/medium/low}`. Onestà radicale > effetto wow.
3. **Sustainable organism split (free vs PRO)** — FREE: viewing dati vitali, granular permissions, transparency dashboard, GDPR Art.17/Art.20, sleep stages, 7d local aggregates. PRO: AI narrative Gemini-powered, server 30/90d aggregates, cross-signal correlations, predictive baseline, PDF report, cohort comparison, raw upload windows. **Rule**: features ~zero-cost-at-margin = free; features con compute/storage/external API costs = PRO. *"Non può solo dare"*.
4. **Dedicated onboarding section** — `BioOnboardingScreen` 5-step pager dopo onboarding base (skippable + re-accessible da Settings). NOT contextual prompt.

**10 fasi** (vedi `.claude/BIOSIGNAL_INTEGRATION_PLAN.md`): Foundations → Android HC → Trust/Sovereignty → Onboarding → Server aggregates → Wellness integration (surgical, no new tab) → UI Calmify-aligned → A11y+i18n → PRO tier → iOS HealthKit parity → Multi-device validation.

**Stime oneste** (no shortcuts, CLAUDE.md regola 1): MVP Android-only 28-40 giorni. Full cross-platform con PRO 40-58 giorni.

**Hardware day-1** (per `memory/user_hardware.md`): Samsung Galaxy S24 + Xiaomi Mi Band 10 (worst-case coverage — Mi Band HRV via Health Connect è sparso; se la pipeline regge qui regge ovunque). Phase 10 premium device TBD (suggerimento: Galaxy Watch 7 per Samsung Health native + S24 pair).

**Tracker files (READ FIRST before any bio-signal work)**:
- `memory/project_biosignal_integration.md` — long-term context, why, decisions
- `memory/feedback_calmify_values.md` — 7 dogmi (etica + sustainable organism)
- `.claude/BIOSIGNAL_INTEGRATION_PLAN.md` — strategic plan, architecture KMP, phased delivery
- `.claude/BIOSIGNAL_INTEGRATION_STATUS.md` — live checklist with checkbox per task

**Quality gates per commit**: build green + zero hardcoded strings + all 12 langs in sync + tracker updated BEFORE commit + audit trail (smoke test specifico, non solo `/health`).

### Active Workstream — Meditation Feature Redesign (started 2026-05-02)

**Major feature redesign in progress.** Full rebuild of meditation flow to match Claude Design source `C:\Design\Calmify\Meditation\Calmify Meditation.html` + `calmify.css`. User explicit quality mandate: "Non mi importa se ci metto tanto, [...] Fatta bene 1:1 come minimo se non addirittura 1:1.5". NASA-grade rigor, full a11y, all 12 langs, no hardcoded strings.

**Scope**: 5-phase wizard (Welcome → Screening → Configure → Session → Overview), 6 evidence-based breathing techniques (Coherent / Extended exhale / Box / 4-7-8 / Belly / Body scan), 8 medical risk flags screening, goal-based recommendation, animated breathing pacer (halo + 2 rings + circle, smoothstep easing), coach text rotation, stop confirmation modal, stats overview.

**Phased delivery** (3 sessions):
- **Phase 1 DONE 2026-05-02 `d54d2f5`**: Domain refactor (BreathingPattern 3→6 + Goal/Experience/Audio/RiskFlag enums) + 161 keys × 6 langs (966 traduzioni) + `Strings.Meditation` facade + `MeditationStrings.kt` extensions + 5-phase state machine + 5 new screens (~1670 LOC) + thin dispatcher. Firestore backward-compat via `BreathingPattern.fromCanonicalName()`.
- **Phase 2 DONE 2026-05-03 (commit pending)**: Per-segment `BreathingPacer` (Compose `Animatable` + `CubicBezierEasing(0.4,0,0.2,1)` matching the CSS source, mid-segment resume interpolation), cue word/count overlay (Breathe in/Hold/Breathe out + countdown), coach line rotation (12s practice / progressive settle+integrate), `ModalBottomSheet` stop confirmation (replaces Phase 1 AlertDialog), millis-precision contract (`SessionRuntime.elapsedMillis: Long` + derived `currentSegment / intoSegmentMillis / currentSegmentIndex / cyclesCompleted / practiceElapsedMillis / cycleMillis`), 4Hz VM ticker (was 1Hz, balances cue countdown precision and CPU). New `BreathingSegment` data class + `BreathSegmentKind` enum in core/util. 4-cycle cap for 4-7-8 enforced via `practiceCapSeconds = min(practice, cycleCap × cycleSec)`. Build green (9s).
- **Phase 3.A DONE 2026-05-03 `bee4a17` — A11y batch**: Reduced-motion expect/actual (Android `Settings.Global.ANIMATOR_DURATION_SCALE`, iOS `UIAccessibilityIsReduceMotionEnabled`, desktop stub) → `BreathingPacer` clamps tweens to 200ms + static ambient layer; keyboard shortcuts (ESC=stop, SPACE=pause) via `focusable + onPreviewKeyEvent + LaunchedEffect autofocus`; TalkBack — pacer geometry hidden, cue word `liveRegion = Polite`, count hidden.
- **Phase 3.B DONE 2026-05-03 `e14b876` — Audio gating + TTS decision doc**: Gated chime on `audio != SILENT` (was firing regardless — real bug). Wrote `.claude/MEDITATION_TTS_DECISION.md` with 4-option survey.
- **Phase 3.B' DONE 2026-05-03 (code; audio assets pending user, commit pending) — TTS implementation**: User picked **Option A + ElevenLabs (Multilingual v2 model, single voice across 12 langs) + all 12 langs at launch + "speak each coach line on first display, stay quiet on visual rotation" policy** (cue words continue throughout PRACTICE since they're rhythmic, not contemplative). Built end-to-end: `VoiceUtterance` sealed class + `CoachKey` (commonMain), `MeditationVoicePlayer` (androidMain `MediaPlayer` + `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` + last-call-wins cancellation + EN locale fallback), VM `Effect.Speak(VoiceUtterance)` + emission on segment boundary via `currentSegmentIndex` tracking + first-display per `CoachKey` via spoken-set; entry-point handler reads live audio mode at emission, resolves locale via `Locale.getDefault().language`, dispatches `voicePlayer.play(utterance, locale)`. Generation script `scripts/generate-meditation-voice.py` (idempotent, throttled, supports `--locales`/`--keys`/`--force`/`--dry-run`) + catalog CSV (29 unique keys × 12 locales = 348 files) + `assets/meditation/voice/README.md`. **VOICE mode is silent until user runs the generator + commits the .mp3 assets** — Kotlin code path is complete. Build green.
- **Phase 3.C (final)**: Cross-locale screenshot regression (5 phases × 12 langs) + marketing screenshots for Play Store listing.

**Tracker files (READ FIRST before any meditation work)**:
- `memory/meditation_redesign.md` — long-term context, why, design source, decisions
- `.claude/MEDITATION_REDESIGN_PLAN.md` — strategic plan, full key catalog, contract refactor
- `.claude/MEDITATION_REDESIGN_STATUS.md` — live checklist with checkbox per task

**Quality gates per commit**: build green + zero hardcoded strings + all 6 Latin XMLs in sync + trackers updated BEFORE commit.

### Stato Attuale (aggiornato 2026-04-18)

- **Branch attivo**: `backend-architecture-refactor` (base: master @ `08ef101`), HEAD = `2c8ea0b`
- **Fase 1 KMP COMPLETATA**: 237 file commonMain / 90 file androidMain (72.5% shared)
- **Backend Refactor: FULLY OPERATIONAL + LEVEL 1 DEPLOYED 2026-04-18**:
  - W1 Ktor Server: deployed su Cloud Run (`https://calmify-server-23546263069.europe-west1.run.app`)
  - W2 Sync Engine: wired (KtorSyncExecutor + Koin + lifecycle)
  - W3 Protobuf: client/server protobuf CN con JSON fallback
  - W4 AI Server: GeminiClient con error handling, safety settings, API key server-side
  - **Firestore DB**: database `calmify-native` (NON `(default)` che e' in Datastore Mode)
  - **36 Ktor REST repos** implementati e registrati in Koin (17 base + 7 Phase 1 + 4 Phase 2 + Subscription + Media + Messaging + Avatar + 4 Phase 5)
  - **FeatureFlagService**: legge da Firebase **Remote Config** (non piu' Firestore `config/flags`) — commit `36d7a39`
- **BackendConfig**: 12 flag tutti `true` — FUNZIONANTE, verificato dall'utente
- **100% KMP REST Migration COMPLETATA**: 36/36 repos — Level 1 CHIUSO
  - Phase 1 (COMPLETATA `e4e36ec`): Waitlist, ProfileSettings, ThreadHydrator, Awe, Block, Recurring, Wellbeing
  - Phase 2 (COMPLETATA `001c084`): Search, Presence, UnifiedContent, ContentModeration (+3 server services/routes)
  - Subscription (DONE `3db122e`+`8e838ac`): Stripe web-first, webhook hardening, SDK dahlia
  - **Phase 3 COMPLETATA (2026-04-13)**: MediaUpload `1c4256c` (presigned URL), SocialMessaging `88f8d0a` (REST + WebSocket + broadcast hub)
  - **Phase 4 COMPLETATA (2026-04-13)**: Avatar — server-mediated 2-stage pipeline. AvatarService (Gemini 2.0 Flash META prompt con retry 429 esponenziale + Cloud Run VRM proxy) + AvatarRoutes (POST 202 Accepted, GET list/single, DELETE, PATCH status) + KtorAvatarRepository (adaptive polling Flow: 2s transient, 30s steady). Cloud Functions `createAvatarPipeline`/`generateVrmAvatar` ora bypassate dal client (restano deployate ma inerti). Build green; deploy pending.
  - **Phase 5 COMPLETATA + DEPLOYED (2026-04-18 `292c46c`+`2c8ea0b`, Cloud Build `9a7a2a70`)**: Environment / Garden / Ikigai / SocialGraph — Firestore-server pattern (no Spanner, deliberate per ship-before-infra rule, vedi `memory/feedback_ship_before_infra.md`).
    - **Server**: 4 services + 4 route groups (21 endpoints). Layout legacy preservato: `environment_design/{userId}`, `garden/{userId}` (exploredActivities + favorites con transazioni Firestore idempotenti), `ikigai_exploration/{userId}` (DELETE solo se id == userId), `social_graph/{userId}/{following,followers,blocked}` + `user_profiles/{userId}`. SocialGraph enforce `principal.uid` come follower/blocker/profile owner su ogni mutation. `PATCH /profiles/me` whitelista `username,displayName,avatarUrl,coverPhotoUrl,bio,interests,links` (blocca override di `followerCount,isVerified,...`).
    - **Client KMP**: 4 Ktor repos. **Flow semantics change**: `Flow<Boolean>` (isFollowing/isBlocked) e `Flow<EnvironmentChecklist?>`/`Flow<IkigaiExploration?>` ora single-emission (no snapshot listener). Screens dovranno re-subscribe su refresh/re-open. Accettato come MVP tradeoff. `updateProfile(map)` serializza Map<String,Any?> su JsonObject (helper `toJsonElement` supporta String/Boolean/Number/List/Map/null).
    - **Flag**: `HOLISTIC_REST=true`, `SOCIAL_GRAPH_REST=true`.
    - **Smoke test verificato 2026-04-18**: 59/59 comportamenti auth attesi. Health 200, feature flags 200 (no auth), 35 endpoint pre-Phase 5 + 21 endpoint Phase 5 tornano 401 senza token, Stripe webhook 400 `missing_signature` senza firma. Vedi `memory/project_phase5_deploy_results.md`.
    - **Full E2E test con token 2026-04-19**: 91/92 test PASS, 0 bug server. Inclusi tutti gli endpoint originariamente skippati: Gemini AI (chat/insight/analyze 3/3 200), Stripe checkout (201 URL reale) + portal (400 expected per utente senza sub), Avatar pipeline (POST 202 → PROMPT_READY 10s → READY 20s + VRM URL + DELETE 204), GDPR delete (200 {47 docs deleted} + verify 404/empty). Unico gap: Messaging WS mutations (richiedono secondo utente, scope E2E orchestrato).
    - **Indexes Firestore**: 3 deploy correttivi (`cab2b47`+`46c17f7`) per aggiungere 30+ composite indexes mancanti per wellness/chat/notifications/dashboard. Lezione: ogni index Firestore con campo DESC come ultimo sort field richiede esplicito `__name__ DESC` tie-breaker nel JSON, altrimenti Firestore rifiuta la query. `recurring_thoughts` usa `lastSeenMillis`, `values_discovery` usa `createdAtMillis`.
    - **Lesson appresa (hard way)**: primo deploy `ba22950b` ha SUCCEEDED ma ha shippato il binary pre-Phase 5 perche' il commit era ancora uncommitted localmente. Fix: sempre commit+push PRIMA di `gcloud builds submit`. Regola salvata in `memory/feedback_commit_before_deploy.md`.
- **Subscription — Stripe web-first FULLY OPERATIONAL (2026-04-13)**:
  - Checkout hosted + webhook signature verification deployed (`3db122e`) — E2E verified
  - **In-app management (`631af94`)**: `ManageSubscriptionCard` in PaywallScreen mostra piano, status, scadenza, auto-renew; bottone "Gestisci abbonamento" → Stripe Billing Portal (cancel / card / fatture su UI hosted per PCI)
  - **Server endpoint**: `POST /api/v1/payments/portal-session`
  - **PRO badge UI**: `core/ui/ProBadge.kt` — visibile accanto a "Calmify" in Home topbar + Drawer header quando tier=PRO
  - **Drawer entry**: "Abbonamento" / "Gestisci abbonamento" (label dinamica) — naviga a SubscriptionRoute
  - **Auto-refresh on resume**: `SubscriptionEntryPoint` ascolta `Lifecycle.ON_RESUME` → rinfresca tier dopo ritorno da Chrome Custom Tab
  - **Root-level tier observation**: `DecomposeApp` osserva `SubscriptionRepository.observeSubscription()` una sola volta + refresh all'avvio, propaga `isPro` al drawer; HomeScreen usa stesso pattern via `koinInject`
  - **Webhook hardening (`a1d9b40`)**: `subscription_data.metadata.userId` su checkout session (elimina dipendenza da `resolveUserIdFromCustomer` per i `customer.subscription.*` events) + warn log espliciti su ogni silent failure path (deserializer null, userId non risolto, subscriptionId mancante)
  - **stripe-java bumped 28.1.0 → 32.0.0 (`8e838ac`)**: SDK ora pinned su API `2026-03-25.dahlia` matchando il webhook destination. Field relocations gestite: `Subscription.currentPeriodEnd` → `sub.items.data[].currentPeriodEnd` (max), `Invoice.subscription` → `invoice.parent.subscriptionDetails.subscription`
  - **Firestore source of truth**: `subscriptions/{userId}` scritto SOLO dal webhook firmato. `tier=PRO` se `status in ["active","trialing"]`, expiresAt da subscription items
  - Test keys in hand: `pk_test_51TLLSy...` / `sk_test_51TLLSy...`, Product `prod_UK1sU44yRqA4eG`, lookup_keys creati.
  - Vedi `memory/project_stripe_live_switch.md` per il checklist operativo test→live
- **KMP FULL MASSIVE (3 livelli)** — vedi `memory/project_kmp_full_massive_3levels.md`:
  1. **Repo layer**: 36/36 (100%) — **Level 1 COMPLETE + DEPLOYED 2026-04-18**
  2. **Infrastructure services**: Stripe ✅, MediaUpload ✅ (`1c4256c`), SocialMessaging ✅ (`88f8d0a`), Avatar ✅ (2026-04-13) — **Level 2 complete**
  3. **Full multiplatform (iOS+Web)**: Option C hybrid strategy — NOT STARTED (now fully unblocked)

- **Residual issue identificato 2026-04-18 (non-blocking)**: dal logcat `System.out: Pull changes failed: Illegal input: Error while decoding com.lifo.shared.api.GenericDeltaResponse`. Il `SyncEngine` chiama `/api/v1/sync/changes` e non riesce a decodificare la risposta Protobuf. Non blocca flussi utente (offline-first + retry automatici). Appartiene al workstream W2 Sync Engine, non a Level 1. Da investigare in sessione dedicata comparando `shared/models/.../GenericDeltaResponse.kt` vs `calmify-server/.../SyncService.kt`.
- **Phase 3.1 DONE (`1c4256c`)**: MediaUpload presigned URL pattern (GCS V4 signed URLs, client uploads direct to GCS). Server: `MediaService` + `MediaRoutes`. Client: `KtorMediaUploadRepository`. Flag `MEDIA_REST=true`. IAM: `roles/iam.serviceAccountTokenCreator` (self) + `roles/storage.objectAdmin` sul bucket — applicati.
- **Phase 3.2 DONE (`88f8d0a`)**: SocialMessaging REST + WebSocket + broadcast hub.
  - **Server**: `MessagingService` (Firestore CRUD su `conversations/{id}/{messages,typing}`, ownership via `participantIds` su ogni op). `MessagingHub` (ConcurrentHashMap WS sessions per userId, fan-out sealed `MessagingEvent` con kotlinx polymorphism). `MessagingRoutes`: REST `/api/v1/messaging/*` + WS `/ws` con JWT in query param (browser WS handshake non supporta header Auth).
  - **Client KMP**: `KtorSocialMessagingRepository` con WS singolo multiplexato via `MutableSharedFlow<JsonObject>`, exponential backoff reconnect (1→30s jitter), JWT auto-refresh ogni 45min (frame `auth.refresh`). Snapshot REST su connect + ogni reconnect.
  - **Flag**: `BackendConfig.MESSAGING_REST=true`.
  - **Rischi noti (MVP)**: (1) Hub single-instance — con Cloud Run `max-instances=10`, partecipanti su istanze diverse perdono fan-out. Future fix: Pub/Sub bridge (~200 LOC). (2) JWT in query log: Cloud Run scrubs query params su `/ws`.
  - **Fuori scope iterazione**: presence integration, push notifications server-side, pagination cursor, E2E encryption.
  - Build verde, deploy pendente. Vedi `memory/project_phase3_socialmessaging.md`.
- **Phase 4 DONE (2026-04-13)**: AvatarRepository — server-mediated 2-stage pipeline.
  - **Server**: `AvatarService` (Firestore `users/{userId}/avatars/{avatarId}` + formAnswers.v1 preserved, async pipeline via `CoroutineScope(SupervisorJob+IO)` — stage 1 Gemini 2.0 Flash META prompt identico al Cloud Function con retry 429 esponenziale; stage 2 POST a `$VRM_GENERATOR_URL/generate` Cloud Run proxy; status transitions PENDING→GENERATING→PROMPT_READY→READY, ERROR terminale con errorMessage). `AvatarRoutes`: `POST /api/v1/avatars` ritorna 202 Accepted + avatarId, `GET /api/v1/avatars[/{id}]`, `DELETE`, `PATCH /{id}/status`.
  - **Client KMP**: `KtorAvatarRepository` con adaptive polling Flow — 2s delay while status ∈ {PENDING, GENERATING, PROMPT_READY}, 30s when READY/ERROR. Dedupe su emission (`if (new != last) emit`).
  - **Flag**: `BackendConfig.AVATAR_REST=true`.
  - **Rischi noti (MVP)**: (1) Pipeline su CoroutineScope locale — se istanza Cloud Run muore mid-pipeline, doc resta GENERATING. MVP accettabile; future fix watchdog sweep. (2) Polling cost — ~15-45 poll per creation (~30-90s), idle 30s, trascurabile.
  - **Fuori scope**: Pub/Sub queue, server-side VRM rendering, META v2 re-processing job.
  - Build verde, deploy pendente. Vedi `memory/project_phase4_avatar.md`.
- **Prossimo**:
  1. **i18n Sprint COMPLETE** (2026-04-19) — 140 chiavi × 12 lingue, ~145 hardcoded migrati su 31 file, 11 commit atomic A→E. `Strings` facade + `AppText` helpers + LocaleController + Detekt. Rule-of-3 debt evitato prima di Level 3. Vedi `memory/i18n_strategy.md`.
  2. (opzionale) Fix Sync decoder drift (W2) — investigare perche' `GenericDeltaResponse` non decodifica.
  3. **Level 3 (iOS+Web targets)** — dopo sprint i18n. Vedi `memory/kmp_action_blocks.md` per gli 8 blocchi concreti, `.claude/KMP_TIER1_READY.md` per i 12 moduli gia' pronti.

- **i18n state (aggiornato 2026-04-19, Fase A DONE)**:
  - **Sistema**: Compose Multiplatform Resources nativo (no moko-resources, no libres). Path: `core/ui/src/commonMain/composeResources/values*/strings.xml`.
  - **User decisions 2026-04-19**: default lang **EN** (was IT), **Detekt** custom rule (not grep only), **12 lingue** (6 existing + 6 new: AR, ZH, JA, KO, HI, TH). Sprint estende a ~5-6 giorni.
  - **Audit**: ~142 stringhe hardcoded sparse su 20+ moduli + 14-key drift IT↔EN (481 vs 495).
  - **Fase A DONE 2026-04-19**: `core/ui/.../i18n/` scaffold — Strings.kt typed facade, AppText/LocalizedIconButton/LocalizedTextButton helpers, LocaleController con 12 SupportedLocale (AR isRtl=true), Detekt 1.23.7 wired. Compile green.
  - **Fase A' DONE 2026-04-19**: default language switched IT→EN. `values/` now EN (default), new `values-it/` per Italian, 14-key drift fixed (nav_* + error_*). `values-en/` preserved redundantly for safety. Full app compile green. I18N_GUIDE.md updated.
  - **Fase A'' DONE 2026-04-19**: 6 new locale folders creati (`values-ar/zh/ja/ko/hi/th/`) con baseline 15 chiavi ciascuno. Fallback automatico a EN per chiavi non tradotte. Font scaffold (`font/README.md`) — binaries Noto bundle in Fase D. AR isRtl=true tramite `LocalLayoutDirection`.
  - **Fase B DONE 2026-04-19**: 14 nuove chiavi common (action_*, error_*, state_*, a11y_*) × 12 lingue. `Strings.Action/Error/State/A11y` facade popolata (niente piu' TODO). Showcase migration: `HomeTopBar.kt` 3 a11y hardcoded migrati. Full app compile verde.
  - **Fase C.1 DONE 2026-04-19** (home migration): 29 new keys × 12 lang + ~37 hardcoded migrati su 11 home file. `Strings.Screen.Home` facade popolata. Full app compile verde. Skip: SnapshotScreen.kt (separate commit), "!" decorativi, @Preview.
  - **Fase C.2 DONE 2026-04-19** (search + social-profile): 24 new keys × 12 lang + ~22 hardcoded migrati su SearchScreen, EditProfileScreen, SocialProfileScreen, FollowListScreen. `Strings.Screen.Search`/`SocialProfile` + `Strings.SharedA11y` popolati. Full compile verde.
  - **Fase C.3 DONE 2026-04-19** (composer + messaging + thread-detail): 25 new keys × 12 lang + ~22 hardcoded migrati su ComposerScreen, ChatRoomScreen, ConversationListScreen, ThreadDetailScreen. `Strings.Screen.Composer`/`Messaging`/`ThreadDetail` popolati. Visibility.label() refactored a labelKey() (StringResource-based). Full compile verde.
  - **Fase C.4 DONE 2026-04-19** (avatar-creator + insight + meditation): 24 new keys × 12 lang + ~22 hardcoded migrati su CreationProgressScreen (8 stati pipeline), AvatarListScreen, EmotionalSection/PersonalitySection/VoiceSection (5 headers), InsightScreen (6 dialog/buttons), MeditationScreen (4). `Strings.Screen.Avatar`/`Insight`/`Meditation` popolati. Full compile verde.
  - **Fase C.5 DONE 2026-04-19** (snapshot + settings + chat + humanoid + nav facade): 28 new keys × 12 lang (336 entries) + ~22 hardcoded migrati. SnapshotScreen wellness onboarding (12 strings), Settings dialog, ChatBubble voice + ChatScreen toast (refactored non-composable to accept pre-resolved String), Humanoid 4 buttons. `Strings.Nav` aggiunto (12 nav labels). Full compile verde.
  - **Fase C COMPLETE.** Cumulative C: 102 + 28 = 130 nuove keys × 12 lang + ~128 hardcoded migrati su 27 file Kotlin.
  - **Fase D DONE 2026-04-19** (tail residuals + cleanup + core/social-ui): ~10 new keys × 12 lang (~120 entries) + ~17 hardcoded migrati. SocialProfile tail (User fallback, " follower"/" seguiti" suffixes via append, Modifica profilo/Condividi/Seguito/Segui buttons — 7 keys), FollowList Follower/Seguiti tab labels (2 keys), core/social-ui quartet (CodeBlock copyCode a11y, EngagementBar reply/repost/share a11y, ThreadPostCard verified/options a11y, FullscreenImageViewer close+image a11y — 8 hardcoded using existing keys). **Cleanup**: `values-en/strings.xml` eliminato (redundante — `values/` e' default EN). **Revert MainActivity.kt**: `app/` module e' `com.android.application` (Android-only, no Compose Resources) — "Calmify"/"Error"/"Try Again" restano hardcoded in launcher con commento esplicativo. Strings.Screen.SocialProfile ora 16 keys, FollowList nuovo sub-object. Full compile verde.
  - **Fase E DONE 2026-04-19 (SPRINT CLOSE)**: I18N_GUIDE.md consolidato (default EN documented, 12-locale table finalised, `Strings`+`AppText` promoted, `values-en/` refs removed, Noto fonts flagged post-sprint, PR checklist extended con typed-facade + Detekt). i18n_strategy.md + sprint summary table (11 commits, phase-by-phase). Tutti i tracker allineati su "COMPLETE".
  - **Sprint cumulative**: **140 keys × 12 lang ≈ 1680 entries + ~145 hardcoded migrated su 31 Kotlin files** (27 feature + 4 core/social-ui). 11 commit atomic (A→E).
  - **Known deferrals (post-sprint, non-blocking)**: (1) Noto fonts bundle before Level 3 (iOS+Web) — MVP Android OK con system fonts. (2) Full translation 6 new locales (AR/ZH/JA/KO/HI/TH) on-demand per-market. (3) Detekt `ignoreFailures=false` dopo 1 mese di CI data pulita.
  - **Fase F DONE 2026-04-20 (post-sprint manifest hookup)**: User reported language dropdown non-functional (98% stayed Italian). Root cause: AndroidManifest.xml mancava Google's official per-app language API declaration — `AppCompatDelegate.setApplicationLocales()` era chiamato ma il framework non poteva persistere/propagare la scelta. Fix pure manifest (zero Kotlin): (1) creato `app/src/main/res/xml/locales_config.xml` con 12 `<locale>`, (2) `android:localeConfig="@xml/locales_config"` su `<application>`, (3) `AppLocalesMetadataHolderService` + `autoStoreLocales=true` per backport API < 33. `:app:processDebugManifest` + `:app:processDebugResources` green. Device verification pending.
  - **Fase G DONE 2026-04-20 (dead scaffold removal)**: Phase F ha confermato che il runtime switching gira tutto via `AppCompatDelegate` + manifest — `LocaleController` scaffold inutile. Grep verified 0 callers per `LocaleController.kt` (controller + SupportedLocale enum + LocalePreferences) e `Helpers.kt` (AppText/LocalizedIconButton/LocalizedTextButton). **Entrambi eliminati**. `Strings.kt` (37 callers, nucleo dello sprint) preservato. I18N_GUIDE.md aggiornato per documentare `stringResource(Strings.X.y)` come pattern ufficiale. Build verde.
  - **Fase H DONE 2026-04-20 (Compose MP locale sync + nav fix — DEVICE-VERIFIED)**: Dopo Fase F, `adb shell cmd locale get-app-locales` ritornava `[en]` MA UI restava italiana. Root cause letto nei sorgenti decompilati di Compose MP 1.7.1 `ResourceEnvironment.android.kt`: `getSystemEnvironment()` legge `java.util.Locale.getDefault()` (JVM process-default) — NON aggiornato automaticamente da `AppCompatDelegate.setApplicationLocales()`. **Fix 1 `MainActivity.onCreate`**: sync `Locale.getDefault()` con `resources.configuration.locales[0]` prima di `super.onCreate`. **Fix 2 `DecomposeApp.kt:1219,1225`**: bottom nav usava hardcoded `destination.label` invece di `destination.localizedLabel()` extension fn — fixato + import aggiunto. Device verified via uiautomator dump: Menu/Notifications/Home/Journal/Garden/Community/Journey tutti EN. **Residuo non-locale**: `core/ui/.../coaching/ScreenTutorials.kt` ha ~30 coach mark strings hardcoded IT (file comment: "All text is in Italian") — mai migrato dallo sprint. Follow-up commit.
  - **Fase I DONE 2026-04-20 (deeper locale sync: LocaleList + applyLanguage callsite)**: Fase H emulator test fu cold-start (clean process). User test in-app (tap "English" da app già viva) ancora 98% IT. Root cause più profondo letto nei sorgenti decompilati di `ui-text-android-1.7.7` `AndroidLocaleDelegate.android.kt::AndroidLocaleDelegateAPI24`: `Locale.current` legge `android.os.LocaleList.getDefault()` con cache `===` reference equality che sopravvive alle Activity recreations. `AppCompatDelegate.setApplicationLocales` non tocca `LocaleList.getDefault()` quindi la cache non si invalida. **Fix 1**: in `MainActivity.onCreate` aggiungere `android.os.LocaleList.setDefault(LocaleList(configLocale))` oltre a `Locale.setDefault` — istanza fresca forza cache bust. **Fix 2**: in `SettingsEntryPoint.applyLanguage` sync i due default process-wide PRIMA di `AppCompatDelegate.setApplicationLocales`, così il cambio applica alla prima recomposition senza aspettare la recreation. Build verde, verifica device pending dopo install.
  - **Phase J DONE 2026-04-28 (post-sprint screenshot-driven closure, Tier 1+2)**: User device test rivela ~189 stringhe IT residue ancora hardcoded (coach marks, drawer items, hero greeting, mood enum, post categories, Garden activity list, Snapshot 10-Q wizard, info bottom sheets, dialogs). Root cause: sprint A→I aveva fatto audit string-literal-only — mancato `buildAnnotatedString { append("...") }`, `enum class X(val label: String)`, `Pair<String, String>` data objects. Approccio Phase J: 25 screenshot device-driven audit (`.claude/I18N_HARDCODED_AUDIT.md`) → tier-based migration. **Tier 1 DONE** (~85 keys × 6 langs): drawer + FAB + hero greeting + mood enum + composer category + garden taxonomy + percorso title + thread placeholder + thread options sheet + dialogs + 14 file Kotlin refattorizzati + `app/build.gradle` `compose.components.resources` 1.7.1 dep aggiunta. **Tier 2 DONE** (~94 keys × 6 langs): Garden 19 activities × name+desc + Snapshot 10 Q × text+min+max + 4 sezioni + Info tooltip 10 wellness concepts × title+body. `Strings.kt` ora ha 17+ gruppi semantici (Drawer/Fab/Hero/Mood/ComposerCategory/Garden+Activity/Dialog/DateTime/JournalPrompt/Connection/ThreadOptions/Coach/Tooltip + Screen sub-objects). **Cumulative Phase J: ~189 keys × 6 langs ≈ 1134 traduzioni + 28 file Kotlin refactor. BUILD GREEN (assembleDebug)**. Vedi `memory/project_i18n_phase_j.md`.
  - **Phase J Tier 3.A+3.B DONE 2026-04-29 (architectural cleanup)**: 3.A: `DateFormatters.getTimeOfDayGreeting()` → `getTimeOfDayGreetingRes(): StringResource` (HeroGreetingCard caller updated) + new `Strings.DateTime.greetingNight` + `weekLabel` keys. **`PsychologicalProfile.Trend` enum** dropped `displayName: String` IT field (kept `colorName` only) — UI sites (`PercorsoScreen`/`ProfileDashboard`) resolve via inline `when (trend) -> Strings.Trend.X` to avoid `core/util→core/ui` circular dep. Added 4 trend label + 4 trend message keys. **`getWeekLabelFull()` deleted** — 2 callers now use `stringResource(Strings.DateTime.weekLabel, weekNumber, year)` directly (parameterized format). 3.B: massive dead-code removal in `DateFormatters.kt` (rule 12) — deleted 11 unused IT-hardcoded utility functions (`formatFullDate`/`formatMediumDate`/`formatShortDate`/`formatTime`/`formatDayMonth`/`formatMonth`/`formatShortMonth`/`getRelativeTime`/`getTimePeriod`/`formatSectionHeader`/`getWeekLabel`/`getDayLabel`/`getFullDayName`/`getWeekRangeString`/`getMonthLabels`) — tutti dead, file passato da 250 LOC a 35 LOC. **`CalculateStreaksUseCase.dayOfWeekItalian()` deleted** — `MonthlyStats.mostProductiveDay` ora storce il canonical DayOfWeek `.name` (consumer-side localizzato se mai consumato; attualmente unconsumed in UI, dead path). **`TimePeriod` enum migrato**: `label: String` → `labelRes: StringResource` (6 period keys: today/yesterday/this_week/last_week/this_month/older). **`FeedSectionHeader.kt`** entry-count pluralizzato: `"$itemCount voce/voci"` → `stringResource(if (count==1) Home.entryCountOne else Home.entryCountMany, count)` con format `%d`. Cumulative Tier 3.A+3.B: ~28 keys × 6 langs ≈ 168 traduzioni + 8 file Kotlin refactor + 1 file ridotto da 250→35 LOC. **Build verde (assembleDebug)**.
  - **Phase J Tier 3.C+3.E DONE 2026-04-30 (BlockType enum + JournalHome prompts + WeeklyReflection)**: Tier 3.C: **`BlockType` enum** in `core/util/.../model/Block.kt` aveva `displayName: String` + `suggestion: String` IT — stesso circular-dep di `Trend`. Pattern: drop both, `BlockScreen.kt` resolve via inline `@Composable` extensions `BlockType.label()` / `BlockType.suggestion()` returning `stringResource(when (this)→Strings.Block.X)`. **`BlockResolution.displayName` deleted entirely** — unused in UI (only `.name` for canonical lookup), CLAUDE.md rule 12. `BlockScreen.kt` migrated end-to-end: DescribeStep + DiagnosisStep "Eve dice:" + ActionStep + 13 ActionItems (4 reusable titles + 11 per-type subtitles) + HistoryStep empty + BlockHistoryCard resolution note prefix. `ActionItem` data class `String→StringResource`. `Strings.Block` group added (37 keys). ~36 keys × 6 langs ≈ 216 traduzioni. Tier 3.E: **`JournalHomeScreen.getContextualPrompt + getMoodFollowUp + formatDiaryTimestamp`** all converted from non-Composable to `@Composable` returning resolved Strings via `stringResource(when→Strings.JournalPrompt.X)`. **`buildWeeklyReflection` (PercorsoScreen + ProfileDashboard duplicates)** → `@Composable` concatenation `moodPart + stressPart + trendPart + resiliencePart + diaryPart` reusing `Strings.Trend.msgX` from 3.A. Card chrome migrated: "La tua settimana"/"Il tuo percorso"/"Inizia a scrivere...". `Strings.JournalPrompt` extended (3→22) + `Strings.Weekly` group added (10 keys). ~35 keys × 6 langs ≈ 210 traduzioni. **Cumulative Tier 3.C+3.E: ~71 keys × 6 langs ≈ 426 traduzioni + 4 file Kotlin refactor**. **Build verde (assembleDebug)**.
  - **Phase J Tier 3.G DONE 2026-04-30 (Home/Feed/Mood/Journal user-facing closure)**: User device screenshots (5 nuove immagini Community/Home/Mood/Snapshot/Journal) revealed ~60 stringhe IT residue non coperte da Tier 1+2 grep audit. **`SentimentLabel` enum** (in `core/util/.../DiaryInsight.kt`) — drop `displayName: String` IT field, UI sites resolve via inline `when→Strings.Sentiment.X` (5 keys: veryNegative/negative/neutral/positive/veryPositive). **`TimeRange` enum** (in `features/home/.../HomeUiModels.kt`) — drop `label: String` field, new `TimeRange.labelRes: StringResource` extension property; 3 callers (ExpressiveMoodCard/MoodInsightCard/MoodDistributionCard). **`DominantMood.label: String` field deleted** (always derived from sentiment), new `DominantMood.labelRes` extension. **`FeedContract.FeedTab.label` field deleted**, FeedScreen resolve via inline when. **`Strings.moodTagLocalizedRes(canonical)` static helper** in Strings facade — single point per `ThreadPostCard` mood chip canonical-IT→localized mapping (storage stays canonical IT per backward-compat rule). Per-screen: HeroGreetingCard "Come ti senti oggi?" + ExpressiveQuickActions "Scrivi" + ExpressiveWeekStrip (CRESCITA + GIORNI + obiettivo + day initials L M M G V S D + InfoTooltip body) + WeeklyActivityTracker parallel impl + HomeContent (Questa settimana + Umore + Azioni quotidiane) + ExpressiveMoodCard (Il tuo mood + 7/30/90 giorni filter + Positivo/Neutro/Negativo pills × 4 sites) + MoodInsightCard + MoodDistributionCard + DonutChartLegend + EnhancedActivityCard sentiment label + ExpressiveReflection + ReflectionCard "La tua riflessione" + CommunityPreviewCard "X discussioni recenti"/"Vedi tutto" + ActivityFeed "Vedi tutto" + WriteContent Snapshot wizard ("Come ti senti?"/"Scorri per scegliere"/"Avanti") + JournalHomeScreen welcome card (Spunto del giorno/Tocca per iniziare/Il tuo diario è vuoto) + 9 ActivityPill chips + FeedScreen tabs + ThreadPostCard mood chip. New facade groups: `Strings.Sentiment` (5) + `Strings.SnapshotWizard` (3) + `Strings.JournalFilter` (9) + `Strings.Feed` (4); `Strings.Screen.Home` esteso +26 keys; `Strings.JournalPrompt` +3 welcome card keys. ~60 keys × 6 langs ≈ 360 traduzioni + 16 file Kotlin refactor. **Cumulative Phase J: ~348 keys × 6 langs ≈ 2088 traduzioni + 56 file Kotlin**. **Build verde (assembleDebug)**.
  - **Phase J Tier 3.H+3.I DONE 2026-05-02 (Diary wizard + Percorso interior, commit pending)**: Tier 3.H: `WriteContent.kt` 10-step pager fully migrated — per-step heading+subtitle (title/description + 6 metric via MetricStepWrapper + almost-done) + Salva diario + Smart Capture/Rianalizza + Indietro button. New `Strings.WriteWizard` group (22 keys). **`Indietro` sweep**: 11 file migrati a `Strings.Action.back` esistente (DashboardScreen/ConnectionScreen/BrainDumpScreen/SleepLogScreen/MovementScreen/GratitudeScreen/EnergyCheckInScreen/WriteContent/WizardComponents/DiaryDetailScreen/HumanoidScreen) — zero duplicazione. WizardComponents.kt anche `Avanti` → `Strings.Coach.buttonNext` + `Completa` → nuova `Strings.WriteWizard.complete`. ~25 keys × 6 langs ≈ 150 traduzioni + 12 file refactor. Tier 3.I: `PercorsoScreen.kt` + `PercorsoContract.kt` + `PercorsoViewModel.kt` migrazione end-to-end. Progresso Settimanale + Mappa del Percorso + 4 pillar map labels (Mente/Corpo/Spirito/Abitudini) + 4 per-pillar section headers. **Architectural refactor di Contract/ViewModel**: `SectionSummary.title: String` → `titleRes: StringResource`, `SectionItem.label: String` → `labelRes: StringResource`, `SectionItem.value: String` → `valueRes: StringResource` + `valueArg: Int = 0` + `valueOverride: String? = null` (per fractions `X/7`, decimals `8.0h`, discrete states `Confermati`/`Da scoprire`). ViewModel sceglie singolare/plurale lato VM (4 plural pairs: sessioni/fatti/atti/abitudini, basato su `count == 1`). `PercorsoToolAction.label: String` → `labelRes` riusando tutti 16 `Strings.Garden.Activity.*` da Tier 2.A. Nuovo `Strings.Percorso` group (20 keys). ~30 keys × 6 langs ≈ 180 traduzioni + 3 file refactor. **Cumulative Tier 3.H+3.I: ~50 keys × 6 langs ≈ 330 traduzioni + 15 file refactor**. **Build verde (assembleDebug)**. **Cumulative Phase J: ~398 keys × 6 langs ≈ 2388 traduzioni + 67 file Kotlin**.
  - **Phase J Tier 3.D+3.F DONE 2026-05-02 (FULL CLOSURE — commit pending)**: Tier 3.D wellness wizards — 8 screens (BrainDump/Gratitude/EnergyCheckIn/Movement/SleepLog/Inspiration/Reframe/Connection) migrated end-to-end. Title+subtitle+save+sections+placeholders+dialog confirms. New `Strings.Wellness` group (~110 keys). `waterFeedbackRes` (4 stati) e `qualityLabelRes` (5 stati) ritornano `StringResource` per locale-aware runtime resolution. Reusa `Strings.Action.{save,cancel,back}` + `Strings.Coach.buttonNext` per chrome shared (no duplication). ~120 keys × 6 langs ≈ 720 traduzioni + 8 file refactor. Tier 3.F Garden activity expanded card — `GardenScreen.kt::GardenActivityData` refattorizzato: `name/description/longDescription: String` → `nameRes/descriptionRes/longDescriptionRes: StringResource`, `benefits: List<String>` → `benefitsRes: List<StringResource>`. 19 attività × (long body + 3 benefits) migrate. Chrome ("Benefici" header, "Inizia Attività" CTA) migrato. Nuovo `Strings.GardenCard` group (78 keys). ~80 keys × 6 langs ≈ 480 traduzioni + 1 file refactor. **Cumulative Tier 3.D+3.F: ~200 keys × 6 langs ≈ 1200 traduzioni + 9 file refactor**. **Build verde (assembleDebug)**. **PHASE J FULL CLOSURE: ~636 keys × 6 langs ≈ 3816 traduzioni + 84 file Kotlin refattorizzati. Zero deferred i18n work per Latin pack.**
  - **Level 3 unblocked**: iOS+Web ora ereditano automaticamente tutte le 12 lingue via Compose Multiplatform Resources.
  - **Post-sprint**: iOS + Web ereditano automaticamente tutte le 12 lingue (Compose Resources e' KMP-native).

- **Deploy workflow consolidato** (rule of thumb):
  1. Local: `git add <files> && git commit -m "..."`
  2. Local: `git push origin backend-architecture-refactor` ← **MAI skippare**
  3. Cloud Shell: `cd ~/Calmify && git pull && gcloud builds submit --config=calmify-server/cloudbuild.yaml --project=calmify-388723`
  4. Verify: `curl https://calmify-server-23546263069.europe-west1.run.app/health` → `healthy`
  5. Verify new endpoint actually deployed: smoke test the specific change, NOT just health.

### File da leggere in ordine di priorita'

1. **`.claude/KMP_MIGRATION_STATUS.md`** — Tracker KMP attivo: fasi 1-5, metriche, log commit. Fonte di verita' per lo stato della migrazione.

2. **Backend Plans** (tutti COMPLETE — riferimento per manutenzione/debug):
   - **`.claude/BACKEND_KTOR_SERVER.md`** — Server Ktor su Cloud Run — COMPLETE + DEPLOYED
   - **`.claude/BACKEND_SYNC_ENGINE.md`** — Offline-first con SQLDelight + SyncQueue — COMPLETE + WIRED
   - **`.claude/BACKEND_PROTOBUF.md`** — Serializzazione binaria, shared-models module — COMPLETE
   - **`.claude/BACKEND_AI_SERVER.md`** — AI centralizzata server-side — COMPLETE + HARDENED

3. **`.claude/KMP_STATUS.md`** — Stato architettura progetto: moduli, DI, DB, navigation, audio, 3D.

4. **`.claude/refactor-status.md`** — Log cronologico di TUTTE le operazioni passate (utile per capire PERCHE').

5. **Piani feature** (se si lavora su nuove feature):
   - **`.claude/HOLISTIC_GROWTH_STATUS.md`** — Tracker crescita olistica (6 fasi, 18 deliverable)
   - **`HOLISTIC_GROWTH_PLAN.md`** — Piano completo feature mente-corpo-spirito

### Regole branch

- **`master`** — Solo codice stabile, build che funziona
- **`backend-architecture-refactor`** — Tutto il lavoro backend (Ktor Server, Sync Engine, Protobuf, AI)
- **Commit convention**: `<type>(<workstream>): <what>` + sezioni Problems/Gains (vedi memory/backend_refactor_tracker.md)
- **Dopo ogni commit**: aggiornare il tracker nella memory con hash, descrizione, problemi, guadagni

### Slash commands

- **`/improve`** — Avvia/continua il piano di miglioramento. Legge tracker, identifica prossimo task.

### Regole memoria

- **NON sovrascrivere MEMORY.md con informazioni non verificate.**
- Se una sessione finisce i token, la prossima sessione deve VERIFICARE lo stato dal codice prima di aggiornare la memoria.
- Il tracker backend (`memory/backend_refactor_tracker.md`) e' la fonte di verita' per i commit del refactor.

---

## Project Overview — Calmify

Calmify e' una piattaforma wellness + social **Kotlin Multiplatform** (KMP) con:
- Chat AI (Gemini 2.0 Flash, full-duplex voice WebSocket)
- Journaling/Diary (write feature, 16+ wizard screens)
- Mood tracking + insights (CBT-informed, sentiment analysis)
- Avatar 3D (Filament engine, VRM, VRMA animations, lip-sync)
- Social features (feed, messaging, threads, notifications)
- Monetization (subscription/billing via Google Play)

### Stato KMP (aggiornato 2026-04-10)
- **18 moduli** KMP con convention plugins (`calmify.kmp.library`, `calmify.kmp.compose`)
- **237 file commonMain** (72.5%) / **90 file androidMain** (27.5%)
- **Fase 1 completata**: write (47 file), home (22 file), history (100%), habits, meditation migrati
- **90 file androidMain restanti** hanno blockers reali: Firebase, audio, Filament, permissions

### Target Architecture (IMPLEMENTATA + DEPLOYED 2026-04-10)
```
Client (KMP)  →  KtorApiClient  →  Ktor Server (Cloud Run)  →  Firestore + Gemini
     │                                     │
     │                              Server URL: calmify-server-23546263069.europe-west1.run.app
     │                              Firebase Auth (JWT validation)
     │                              Security headers + audit log
     │                              Rate limiting + CORS
     │                              GDPR endpoints (Art.17 + Art.20)
     │                              AI orchestration + caching (60%+ savings)
     │                              Protobuf CN (preferred) + JSON fallback
     │
SyncEngine (SQLDelight)  ←→  KtorSyncExecutor  ←→  /sync/batch + /sync/changes
     │                              DeltaApplier routes deltas to sync repos
ConnectivityObserver (expect/actual)
     │
BackendConfig (7 per-domain flags)  →  restOverrideModule (Koin last-wins)
```

## Build Commands

```bash
# Build APK debug
./gradlew assembleDebug

# Install su device
./gradlew installDebug

# Compila singolo modulo
./gradlew :features:chat:compileDebugKotlinAndroid

# Verifica commonMain compila
./gradlew :core:util:compileCommonMainKotlinMetadata

# Clean e rebuild
./gradlew clean assembleDebug

# Test
./gradlew test
./gradlew connectedAndroidTest
./gradlew lint
```

## Architecture

### Multi-Module Structure (20+ moduli)

| Modulo | Plugin | Ruolo |
|--------|--------|-------|
| **app** | `com.android.application` | MainActivity, DecomposeApp, RootComponent, Koin setup, SyncEngine lifecycle |
| **core/util** | `calmify.kmp.library` | Modelli, 36 repository interfaces, MVI base, AuthProvider, SyncExecutor interface, ConnectivityObserver expect/actual |
| **core/ui** | `calmify.kmp.compose` | Theme (expect/actual), componenti UI condivisi, coach marks, SyncIndicator |
| **core/social-ui** | `calmify.kmp.compose` | Componenti social: ThreadPostCard, MediaCarousel |
| **data/mongo** | `calmify.kmp.library` + SQLDelight | SQLDelight schema (8 tabelle incl. sync) + 36 Firestore repos + SyncEngine + sync repos |
| **data/network** | `calmify.kmp.library` | KtorApiClient, 28 REST-backed repos, KtorSyncExecutor, NetworkKoinModule |
| **shared/models** | `calmify.kmp.library` | 30+ Proto data classes, API wrappers, SyncApi DTOs, Domain↔Proto mappers |
| **calmify-server** | Ktor 3.1.1 + Netty | Server su Cloud Run: CRUD, Social, AI, Sync, GDPR, Security, Audit |
| **features/** (17) | `calmify.kmp.compose` | auth, home, write, chat, humanoid, history, insight, profile, settings, onboarding, feed, composer, social-profile, search, notifications, messaging, subscription, thread-detail, habits, meditation, avatar-creator |

### Key Technologies

- **UI**: Compose Multiplatform + Material3
- **Navigation**: Decompose 3.4.0 (StackNavigation, 18 @Serializable destinations)
- **DI**: Koin 4.1.1 (sole DI, Hilt completamente rimosso)
- **Pattern**: MVI rigoroso (MviViewModel + Intent/State/Effect) su tutti i 18+ ViewModel
- **Database**: SQLDelight 2.0.2 (5 tabelle: ChatMessage, ChatSession, CachedThread, ImageToUpload, ImageToDelete)
- **Authentication**: Firebase Auth behind `AuthProvider` interface (commonMain)
- **AI/Voice**: Gemini API, Silero VAD, full-duplex AEC, Sherpa-ONNX TTS
- **3D**: Filament 1.68.2 per avatar VRM + VRMA animations
- **Build**: Convention plugins in build-logic/ (`calmify.kmp.library`, `calmify.kmp.compose`)
- **Async**: Kotlin Coroutines + StateFlow
- **Image Loading**: Coil 3.x (KMP-compatible)

### Important Classes and Patterns

1. **Navigation**: `RootComponent.kt` + `RootDestination.kt` (18 typed destinations). `DecomposeApp.kt` renders Children stack with bottom bar, drawer, FAB.

2. **ViewModels**: All 18+ VMs extend `MviViewModel<Intent, State, Effect>` in `core/util/mvi/`. Pattern: `onIntent()` -> `handleIntent()` -> `updateState()` / `sendEffect()`. Uses `CoroutineScope` (NO AndroidX ViewModel).

3. **Repository Pattern**: 36 interfaces in `core/util/repository/` (commonMain), implementations in `data/mongo/` (androidMain Firestore). Categories: CRUD (diary, chat, profiles, 14 wellness), Social (feed, threads, messaging, graph), Real-time (presence, notifications), Utility (feature flags, moderation, search).

4. **AuthProvider**: Interface in commonMain, `FirebaseAuthProvider` in androidMain. Tutti i VM usano AuthProvider, mai FirebaseAuth direttamente.

5. **Audio Pipeline** (features/chat): FullDuplexAudioSession (AEC), SileroVadEngine, GeminiLiveWebSocketClient, domain layer pure Kotlin.

6. **3D Avatar** (features/humanoid): FilamentRenderer, VrmLoader, VrmBlendShapeController, LipSyncController, AnimationCoordinator.

### Configuration Files

- **ProjectConfig.kt** (buildSrc/): SDK versions, app ID, build config
- **libs.versions.toml**: Version catalog for all dependencies
- **Firebase config**: `google-services.json` required (not in repo)
- **build-logic/convention/**: KMP convention plugins

## Development Guidelines

### State Management
- Use `StateFlow` and `collectAsState()` for reactive UI (NOT `collectAsStateWithLifecycle` — KMP)
- `RequestState` sealed class for loading/success/error states
- `rememberSaveable` for configuration changes

### KMP Source Set Rules
- **commonMain**: Solo Kotlin puro. No `android.*`, no `java.time`, no `java.util`, no `R.drawable`, no `String.format()`
- **androidMain**: Codice platform-specific (Firebase, Filament, Camera, Audio I/O, Permissions)
- Per spostare codice in commonMain: verificare ZERO import Android/JVM, poi muovere fisicamente
- **Conversioni note**:
  - `java.time.*` → `kotlinx.datetime.*` (vedi DateFormatters.kt per pattern completo)
  - `java.util.UUID` → `kotlin.uuid.Uuid.random()` con `@OptIn(ExperimentalUuidApi::class)`
  - `System.currentTimeMillis()` → `com.lifo.util.currentTimeMillis()`
  - `String.format("%.1f", val)` → `com.lifo.util.formatDecimal(1, val)`
  - `LocalTime.now().hour` → `Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour`
  - `@SuppressLint("NewApi")` → rimuovere (non serve in KMP)
  - `SavedStateHandle` → rimuovere, passare parametri via constructor da Decompose
- **Validazione**: `compileDebugKotlinAndroid` (Android), `compileKotlinIosSimulatorArm64` (iOS). NON usare `compileCommonMainKotlinMetadata` — fallisce per moduli con `koinViewModel()`.

### Adding a New Feature Module
1. Creare directory sotto `features/`
2. Aggiungere a `settings.gradle`
3. `build.gradle` con plugin `calmify.kmp.compose`:
```groovy
plugins { id 'calmify.kmp.compose' }
android { namespace 'com.lifo.newfeature' }
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation project(':core:util')
            implementation project(':core:ui')
            implementation libs.koin.core
            implementation libs.koin.compose
            implementation libs.koin.compose.viewmodel
        }
        androidMain {
            kotlin.srcDirs('src/main/java')
            dependencies { /* Android-specific deps */ }
        }
    }
}
```
4. Creare `FeatureContract.kt` + `FeatureViewModel.kt` + `FeatureScreen.kt`
5. Creare `di/FeatureKoinModule.kt`
6. Aggiungere Koin module a `allKoinModules` in `app/di/KoinModules.kt`
7. Aggiungere destination a `RootDestination.kt` e child a `RootComponent.kt`
8. Aggiungere rendering in `DecomposeApp.kt`

### Working with SQLDelight Database
- Schema files: `data/mongo/src/commonMain/sqldelight/com/lifo/mongo/database/*.sq`
- Generated code: `data/mongo/build/generated/sqldelight/`
- **GOTCHA**: `AS Boolean`/`AS Int` non funzionano in 2.0.2 — usare `INTEGER` e convertire manualmente
- Query reattive: `.asFlow().mapToList(Dispatchers.IO)`
- Query one-shot: `.executeAsOneOrNull()`, `.executeAsList()`
- Per aggiungere tabella: creare nuovo `.sq`, rebuild, aggiornare Koin per esporre le query

### Firebase Integration
- `google-services.json` nel modulo app
- Firebase inizializzato automaticamente da Google Services plugin
- BOM usato SOLO nel modulo `app` (Android puro). Moduli KMP usano versioni esplicite con suffisso `-kmp`
- AuthProvider astrae l'auth per tutti i VM

### KMP Build Gotchas
- `platform(libs.firebase.bom)` NON funziona in KMP `sourceSets.androidMain.dependencies` -> versioni esplicite
- Convention plugins non possono usare `libs.versions.xxx.get()` -> hardcodare
- Root build.gradle: `apply false` per kotlin-multiplatform, org.jetbrains.compose, kotlin-serialization
- gradle.properties: `kotlin.native.ignoreDisabledTargets=true` + `kotlin.mpp.applyDefaultHierarchyTemplate=false`

### Performance Considerations
- Proguard enabled per release builds
- APK splitting by ABI (armeabi-v7a, arm64-v8a, x86_64)
- NDK/CMake per Oboe audio engine (features/chat)
- `CoroutineScope` con `SupervisorJob` nei ViewModel — `onCleared()` cancella lo scope

### Testing Approach
- Unit tests per modulo (`test/`)
- MockK per mocking
- Turbine per testing Flow emissions
- Compose UI tests con `compose-ui-test-junit4`
