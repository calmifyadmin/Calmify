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

### Stato Attuale (aggiornato 2026-04-13)

- **Branch attivo**: `backend-architecture-refactor` (base: master @ `08ef101`)
- **Fase 1 KMP COMPLETATA**: 237 file commonMain / 90 file androidMain (72.5% shared)
- **Backend Refactor: FULLY OPERATIONAL**:
  - W1 Ktor Server: deployed su Cloud Run (`https://calmify-server-23546263069.europe-west1.run.app`)
  - W2 Sync Engine: wired (KtorSyncExecutor + Koin + lifecycle)
  - W3 Protobuf: client/server protobuf CN con JSON fallback
  - W4 AI Server: GeminiClient con error handling, safety settings, API key server-side
  - **Firestore DB**: database `calmify-native` (NON `(default)` che e' in Datastore Mode)
  - **29 Ktor REST repos** implementati e registrati in Koin (17 base + 7 Phase 1 + 4 Phase 2 + Subscription)
  - **FeatureFlagService**: legge da Firebase **Remote Config** (non piu' Firestore `config/flags`) — commit `36d7a39`
- **BackendConfig**: 7 flag tutti `true` — FUNZIONANTE, verificato dall'utente
- **100% KMP REST Migration**: 29/36 repos done, 7 rimanenti (~1.5 settimane)
  - Phase 1 (COMPLETATA `e4e36ec`): Waitlist, ProfileSettings, ThreadHydrator, Awe, Block, Recurring, Wellbeing
  - Phase 2 (COMPLETATA `001c084`): Search, Presence, UnifiedContent, ContentModeration (+3 server services/routes)
  - Subscription (DONE `3db122e`+`8e838ac`): Stripe web-first, webhook hardening, SDK dahlia
  - **Phase 3 IN CORSO (2026-04-13)**: MediaUpload (presigned URL), SocialMessaging (REST + WebSocket)
  - Phase 4: Avatar pipeline
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
  1. **Repo layer**: 29/36 (80%) — 7 rimanenti
  2. **Infrastructure services**: Stripe ✅, MediaUpload IN CORSO, SocialMessaging, Avatar
  3. **Full multiplatform (iOS+Web)**: Option C hybrid strategy — NOT STARTED
- **Prossimo**: Phase 3 ATTIVA — MediaUpload presigned URL (GCS V4 signed URLs, client uploads direct). Server: `MediaService` + `MediaRoutes`. Client: `KtorMediaUploadRepository` in data/network. Flag `MEDIA_REST` in `BackendConfig`.

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
