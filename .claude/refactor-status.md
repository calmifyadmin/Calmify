# KMP Refactoring — Stato di Avanzamento

> Questo file viene letto da Jarvis all'inizio di ogni sessione.
> Aggiornato automaticamente dopo ogni operazione completata.

## STATO ATTUALE 2026-04-19 (Sprint i18n A→E COMPLETE)

**Level 1 KMP REST CHIUSO + DEPLOYED**. Smoke test E2E 91/92 verdi.
**Sprint i18n COMPLETE** (2026-04-19): 140 keys × 12 lingue ≈ 1680 translation entries + ~145 hardcoded migrati su 31 file Kotlin (27 feature + 4 core/social-ui) in 11 commit atomic (A→E). `Strings` facade + `AppText` helpers + LocaleController (12 SupportedLocale, AR RTL) + Detekt wired. Default lang IT→EN, `values-en/` rimosso. Phase E: I18N_GUIDE.md consolidato (default EN, 12-locale table, typed-facade preferred call site, Noto fonts deferred post-sprint, PR checklist extended). Tutti i tracker allineati "COMPLETE". **Level 3 (iOS+Web) unblocked** — Compose MP Resources garantisce ereditarieta' automatica su tutte le piattaforme.
Known deferrals (non-blocking): Noto fonts bundle before Level 3, full translation 6 new locales on-demand (AR/ZH/JA/KO/HI/TH), Detekt error mode dopo 1 mese.
Vedi `memory/i18n_strategy.md`, `I18N_GUIDE.md`, `memory/project_phase5_deploy_results.md`.

## EVENTO: Backend Audit (2026-04-10) — RISOLTO 2026-04-18

Audit completo di 124 file del backend refactor aveva rivelato **30+ bug critici**.
Il backend e' stato **RE-ENGINEERATO** (commit `39499eb`) e tutti i bug sono stati fixati.
Vedi `.claude/BACKEND_AUDIT.md` per catalogo storico, `memory/backend_refactor_tracker.md` per i commit.

Top issues (RISOLTI): 24/26 collection names server ≠ client ✓, 17 Protobuf nullable fields ✓,
blocking calls ovunque ✓, GDPR broken ✓ (verified 2026-04-19 full E2E), double Gemini API calls ✓, IDOR ✓.

**BackendConfig flags**: tutti `true` operativi. Level 1 KMP REST 36/36 DEPLOYED.
**Firestore DB**: `calmify-native`. 30+ composite indexes deployati (vedi `firestore.indexes.json`).

---

## Fase Corrente: KMP Migration — ONDA 10 COMPLETATA (Full Build OK)

## KMP Migration (10 Onde) — 2026-03-01/02

### ONDA 10: Integrazione finale + pulizia (2026-03-02)
- **Full assembleDebug BUILD SUCCESSFUL** — 746 tasks, 0 errori
- All 17 feature/core/data modules now use KMP convention plugins
- Only `app` module remains Android-only (as planned)

### ONDA 9: features/humanoid KMP parziale (2026-03-02)
- build.gradle → `calmify.kmp.compose` plugin
- Removed javax.inject from VrmaAnimationPlayerFactory.kt (only file with it)
- All code stays in androidMain via `kotlin.srcDirs('src/main/java')` (Filament is Android-native)
- Preserved: Filament deps, CameraX deps, media3, gson
- BUILD SUCCESSFUL (compileDebugKotlinAndroid + compileCommonMainKotlinMetadata)

### ONDA 8: features/chat KMP parziale (2026-03-01)
- build.gradle → `calmify.kmp.compose` with NDK/CMake config preserved
- Removed javax.inject from 21 files
- Added compose.material.icons.extended to androidMain deps
- All code stays in androidMain (audio pipeline, WebSocket, Camera2 are Android-native)
- BUILD SUCCESSFUL

### ONDA 7: Room → SQLDelight (2026-03-01)
- Created 4 .sq schema files: ChatSession.sq, ChatMessage.sq, ImageToUpload.sq, ImageToDelete.sq
- data/mongo build.gradle → `calmify.kmp.library` + SQLDelight 2.0.2
- Deleted 12 Room files (AppDatabase, Converters, 4 DAOs, 4 Entities, MongoDatabaseProvider, StringListConverter)
- Rewrote ChatRepositoryImpl + UnifiedContentRepositoryImpl for SQLDelight
- Updated KoinModules.kt: AndroidSqliteDriver → CalmifyDatabase
- Fixed: platform(firebase.bom) incompatibility → explicit versioned deps with `-kmp` suffix
- Fixed: SQLDelight `AS Boolean`/`AS Int` codegen bug → plain INTEGER with manual conversion
- Fixed: CloudFunctions `.data` → `.getData()` for Firebase Functions 21.1.0
- BUILD SUCCESSFUL

### ONDA 5-6: Simple features + home/write KMP (2026-03-01)
- 8 feature modules converted: auth, insight, profile, history, settings, onboarding, home, write
- All use `calmify.kmp.compose` plugin with code in androidMain
- Replaced viewModelScope→scope, collectAsStateWithLifecycle→collectAsState, FirebaseAuth→AuthProvider
- BUILD SUCCESSFUL

### ONDA 3-4: Social features + Firebase models (2026-03-01)
- 7 social features converted in parallel: feed, composer, social-profile, search, notifications, messaging, subscription
- Firebase models migrated, AuthProvider registered in Koin
- BUILD SUCCESSFUL

### ONDA 1-2: core/util + core/ui commonMain (2026-03-01)
- MviViewModel rewritten for KMP (CoroutineScope instead of AndroidX ViewModel)
- AuthProvider interface in commonMain, FirebaseAuthProvider in androidMain
- 27+ files moved to commonMain in core/util
- Theme expect/actual, UI components in commonMain
- BUILD SUCCESSFUL

## Post-KMP Migrations (Pre-KMP prep work)

### Wave 6B: Replace Navigation Compose with Decompose Navigation (2026-03-01)
- **Decompose 3.4.0** + **Essenty 2.5.0** — StackNavigation replaces NavHostController
- **RootDestination.kt**: 18 @Serializable destinations (Auth, Onboarding, Home, Write(diaryId?), Chat(sessionId?), LiveChat, History, ChatHistoryFull, DiaryHistoryFull, Humanoid, Settings, SettingsPersonalInfo/HealthInfo/Lifestyle/Goals, Profile, Insight(diaryId), WellbeingSnapshot)
- **RootComponent.kt**: 18 typed Child classes + 15 convenience navigation methods + replaceAll() for stack clearing
- **DecomposeApp.kt (NEW)**: Children stack renderer with ModalNavigationDrawer, Scaffold, custom DecomposeBottomBar, FAB, sign out/delete dialogs, deep link support via parseDeepLinkRoute()
- **Entry Points (5 NEW files)**: AuthEntryPoint.kt, HomeEntryPoint.kt, WriteEntryPoint.kt, HistoryEntryPoint.kt, SettingsEntryPoint.kt — bridge internal screens to app module
- **MainActivity.kt**: RootComponent created via defaultComponentContext(), replaceAll() for start destination resolution, DecomposeApp replaces CalmifyApp
- **Key fixes**:
  - HomeScreen navController parameter made nullable (unused internally)
  - WriteViewModel: added setDiaryIdAndLoad() for Decompose parameter passing (SavedStateHandle no longer populated)
  - Settings shared ViewModel: koinViewModel(key = SETTINGS_VM_KEY) replaces NavController backStackEntry scoping
  - RootComponent initial destination race: Auth default + replaceAll() in initializeApp() before AppState.Ready
- **Deleted 12 files**: CalmifyApp.kt, NavGraph.kt, NavigationState.kt, AuthNavigation.kt, HomeNavigation.kt, HomeNavigation/SettingsNavigation.kt, WriteNavigation.kt, HistoryNavigation.kt, SettingsNavigation.kt, OnboardingNavigation.kt, HumanoidNavigation.kt, ChatNavigation.kt
- **Dead code noted**: ProfileScreen.kt in app module (unused, uses NavController — harmless, can be deleted later)
- BUILD: assembleDebug OK (606 tasks, 0 errori, 3 warnings deprecation only)

## Fasi Completate (KMP Refactoring)

### Fase 15: Build System & Gradle (2026-02-28)
- **libs.versions.toml:**
  - Aggiunte 7 versioni KMP commentate: compose-multiplatform, sqldelight, voyager, koin, kotlinx-datetime, kotlinx-serialization, multiplatform-settings
  - Aggiunte 11 librerie KMP commentate: sqldelight (3), koin (3), voyager (2), kotlinx-datetime, kotlinx-serialization-json, multiplatform-settings
  - Aggiunti 3 plugin KMP commentati: kotlin-multiplatform, compose-multiplatform, sqldelight
  - Rimosse entry AR orfane: arcore version, sceneview version, arcore library, arsceneview library
- **ProjectConfig.kt:** aggiunto `const val KMP_READY = false`
- **KmpModuleTemplate.md:** creato in buildSrc/ con guida completa per conversione moduli
- VERIFICA: zero android.util.Log in TUTTO il codebase (app/, core/, data/, features/)
- VERIFICA: zero riferimenti AR nel version catalog
- BUILD: assembleDebug OK (470 tasks, 0 errori)

### Fase 14: app module Cleanup (2026-02-28)
- **Log→println (41 calls, 6 file):**
  - MainActivity.kt: 25x Log→println, rimosso import android.util.Log
  - CalmifyFirebaseMessagingService.kt: 11x Log→println, rimosso TAG dal companion (costanti mantenute)
  - GestureAnimationAdapter.kt: 4x Log→println, rimosso import + TAG companion
  - TTSLipSyncAdapter.kt: 6x Log→println, rimosso import + TAG companion
  - CalmifyApp.kt: 1x android.util.Log.d→println
  - NavGraph.kt: rimosso unused import android.util.Log + android.os.Build + @RequiresApi(N)
- **Firebase injection:**
  - MainActivity.kt: aggiunto `@Inject lateinit var auth: FirebaseAuth`, 3x getInstance()→auth
  - CalmifyApp.kt: aggiunto parametro `auth: FirebaseAuth`, 2x getInstance()→auth
  - ProfileScreen.kt: parametro `user: FirebaseUser?` (dead code, ma pulito per coerenza)
  - NavGraph.kt: aggiunto parametro `auth` per passaggio a CalmifyApp
  - CalmifyFirebaseMessagingService: lasciato com'è (Service senza @AndroidEntryPoint, 1x getInstance())
- VERIFICA: zero android.util.Log in app/src/main/java/**/*.kt
- VERIFICA: zero FirebaseAuth.getInstance() nei metodi delle classi (1 solo nel FCM Service, accettabile)
- VERIFICA: zero @RequiresApi ridondanti
- BUILD: assembleDebug OK (467 tasks, 0 errori)

### Fase 13: features/humanoid + AR Removal + Chat Audio Pipeline (2026-02-28)
- **PARTE A — AR Removal completa:**
  - Eliminati 11 file AR: domain/ar/ (2), data/ar/ (4), rendering/ArFilamentRenderer, api/ArHumanoidAvatarView, presentation/components/ArFilamentView+ArPlacementOverlay, di/ArModule
  - CalmifyApp.kt: rimossi 2 import AR, navigateToAvatarLiveChat lambda, intero composable AvatarLiveChat (~115 righe)
  - AvatarIntegrationEntryPoint.kt: rimosso ArSessionManager import + method
  - NavigationState.kt: rimossa route AvatarLiveChat
  - features/humanoid/build.gradle: rimossa dipendenza arsceneview
  - ChatNavigation.kt: rimosso parametro navigateToAvatarLiveChat + extension function
  - ChatScreen.kt: rimosso parametro navigateToAvatarLiveChat + bottone AR dalla MinimalTopBar
  - LiveScreen.kt: rimossi parametri arContent/isArMode/onToggleArMode + toggle AR button + import ViewInAr
- **PARTE B — Humanoid Log cleanup (20 file, ~286 calls):**
  - Rendering: FilamentRenderer (53), FilamentView (12), PointCloudMaterialBuilder (7) = 72
  - Animation: VrmaAnimationPlayer (26), VrmaAnimationLoader (25), AnimationCoordinator (11), IdleRotationController (8), IdleAnimationController (6), IdlePoseController (4), VrmaAnimationPlayerFactory (4), BlinkController (3) = 87
  - VRM data: GltfBoneOptimizer (27), VrmLoader (22), VrmHumanoidBoneMapper (16), VrmBlendShapeController (1) = 66
  - API+lipsync: HumanoidViewModel (26), LipSyncController (16), HumanoidControllerImpl (16), HumanoidIntegrationHelper (2), PhonemeConverter (1) = 61
- **PARTE C — Chat Audio Pipeline Log cleanup (24 file, ~421 calls):**
  - Big: GeminiLiveCameraManager (62), GeminiLiveWebSocketClient (46), GeminiLiveAudioManager (46), GeminiAudioPlayer (43) = 197
  - Medium: AAAudioEngine (38), SpeechToTextManager (28), GeminiNativeVoiceSystem (27), FullDuplexAudioSession (23) = 116
  - Small pt1: SileroVadEngine (19), HighPriorityAudioThread (15), SynchronizedSpeechControllerImpl (11), ReferenceSignalBargeInDetector (10), AdaptiveJitterBuffer (7), AudioQualityAnalyzer (7) = 69
  - Small pt2: LiveCameraPreview (7), ConversationContextManager (6), GeminiVoiceAudioSource (5), GeminiLiveAudioSource (5), SimpleLiveCameraPreview (5), AdaptiveBargeinDetector (3), LockFreeAudioRingBuffer (3), AudioEngineMetrics (2), PacketLossConcealmentEngine (2), ChatInput (1) = 39
- **TOTALE FASE 13: 11 file AR eliminati, ~707 Log→println, 44 file modificati**
- VERIFICA: zero Log.(d|e|w|i|v) in features/humanoid/src/**/*.kt
- VERIFICA: zero Log.(d|e|w|i|v) in features/chat/src/**/*.kt
- VERIFICA: zero ArHumanoid/ArSession/arsceneview/AvatarLiveChat in app/src
- BUILD: assembleDebug OK (467 tasks, 0 errori)

### Fase 12: features/chat Cleanup (2026-02-28)
- ChatViewModel: Iniettato FirebaseAuth via Hilt constructor (2x FirebaseAuth.getInstance() rimossi)
- ChatViewModel: Sostituiti 20x Log.d/e/w → println("[ChatViewModel] ..."), rimosso TAG companion
- ChatViewModel: Rimosso @RequiresApi(Build.VERSION_CODES.O) + import Build/RequiresApi
- LiveChatViewModel: Sostituiti 72x Log.d/e/w/v → println("[LiveChatViewModel] ..."), rimosso TAG companion
- LiveChatViewModel: FirebaseAuth già iniettato (NON toccato)
- ChatScreen: Sostituiti 5x android.util.Log.d/w → println("[ChatScreen] ...")
- ChatScreen: Rimosso @RequiresApi(Build.VERSION_CODES.O) + import Build/RequiresApi
- LiveScreen: Rimosso @RequiresApi(Build.VERSION_CODES.O) + import RequiresApi (Build mantenuto per runtime SDK check)
- ChatNavigation: Rimosso @RequiresApi(Build.VERSION_CODES.O) da liveRoute() + import Build/RequiresApi
- Audio pipeline (25+ file): Completata in Fase 13 (421 Log→println)
- VERIFICA: zero android.util.Log in presentation/navigation
- VERIFICA: zero FirebaseAuth.getInstance() in features/chat/src
- VERIFICA: zero @RequiresApi in presentation/navigation (eccetto GeminiLiquidVisualizer — componente)
- BUILD: assembleDebug OK (467 tasks, 0 errori)

### Fase 11: features/profile Cleanup (2026-02-28)
- SKIP — già pulito: @HiltViewModel, FirebaseAuth iniettato via constructor, hiltViewModel(), zero Log, zero @RequiresApi
- Solo 4 file sorgente, tutti conformi

### Fase 10: features/write Cleanup (2026-02-28)
- WriteViewModel: Iniettati FirebaseAuth + FirebaseStorage via Hilt constructor (3x FirebaseAuth.getInstance() + 2x FirebaseStorage.getInstance() rimossi)
- WriteViewModel: Sostituiti ~18 Log.d/e/w → println("[WriteViewModel] ...")
- WriteScreen: Sostituiti 3x android.util.Log.d → println, viewModel() → hiltViewModel()
- WriteScreen: import viewModel → hiltViewModel
- VERIFICA: zero android.util.Log in features/write/src
- VERIFICA: zero FirebaseAuth.getInstance() in features/write/src
- VERIFICA: zero FirebaseStorage.getInstance() in features/write/src
- VERIFICA: zero viewModel() calls (solo hiltViewModel())
- BUILD: assembleDebug OK (467 tasks, 0 errori)

### Fase 9: features/home Cleanup (2026-02-28)
- HomeViewModel: Iniettati FirebaseAuth + FirebaseStorage via Hilt constructor (6x getInstance() rimossi)
- HomeViewModel: Sostituiti ~28 Log.d/e/w → println("[HomeViewModel] ...")
- HomeViewModel: Rimosso @RequiresApi(Build.VERSION_CODES.N), TAG companion
- HomeViewModel: Aggiunto signOut() method per HomeNavigation
- HomeContent: Sostituiti 3x android.util.Log.d → println
- SnapshotViewModel: Iniettato FirebaseAuth, aggiunto getCurrentUserId()
- SnapshotScreen: Rimosso FirebaseAuth.getInstance() → viewModel.getCurrentUserId()
- HomeNavigation: Rimossi import android.util.Log + FirebaseAuth, 5x Log → println, signOut → viewModel.signOut()
- DateFormatters: Rimosso @RequiresApi(Build.VERSION_CODES.O) + import Build/RequiresApi
- HeroGreetingCard: Rimossi 2x @RequiresApi(Build.VERSION_CODES.O) + import Build/RequiresApi
- VERIFICA: zero android.util.Log in features/home/src
- VERIFICA: zero FirebaseAuth.getInstance() in features/home/src
- VERIFICA: zero @RequiresApi in features/home/src
- BUILD: assembleDebug OK (467 tasks, 0 errori)

### Fasi 5-8: Feature Modules Cleanup — Batch (2026-02-28)
- Fase 5 (insight): SKIP — già pulito (@HiltViewModel, StateFlow, zero Log/Firebase)
- Fase 6 (history): Rimosso android.util.Log (5 chiamate), iniettato FirebaseAuth via Hilt (prima: getInstance()), rimosso @RequiresApi(N) superfluo (minSdk=26)
- Fase 7 (settings): Rimosso FirebaseAuth.getInstance() da SettingsScreen, spostato userProfileImageUrl nel UiState via ViewModel
- Fase 8 (onboarding): SKIP — già pulito (@HiltViewModel, StateFlow, zero Log/Firebase)
- VERIFICA: zero android.util.Log in features/history/src e features/settings/src
- VERIFICA: zero FirebaseAuth in SettingsScreen
- VERIFICA: zero FirebaseAuth.getInstance() in HistoryViewModel
- VERIFICA: zero @RequiresApi in HistoryViewModel
- BUILD: assembleDebug OK (467 tasks, 0 errori)

### Fase 4: features/auth Cleanup (2026-02-28)
- FIX BUG CRITICO: AuthenticationScreen chiamava onSuccessfulFirebaseSignIn(tokenId) DUE VOLTE + chiamata ridondante a FirebaseAuth.signInWithCredential()
- Rimosso android.util.Log (2 chiamate) da AuthenticationScreen
- Rimosso FirebaseAuth + GoogleAuthProvider import da AuthenticationScreen (ora composable puro)
- AuthenticationViewModel: @HiltViewModel + @Inject constructor(FirebaseAuth) (prima: FirebaseAuth.getInstance() diretto)
- AuthenticationViewModel: mutableStateOf → StateFlow (KMP-ready)
- AuthNavigation: viewModel() → hiltViewModel(), state observation → collectAsStateWithLifecycle()
- VERIFICA: zero android.util.Log in features/auth/src
- VERIFICA: zero FirebaseAuth/GoogleAuthProvider in AuthenticationScreen
- VERIFICA: @HiltViewModel + StateFlow + hiltViewModel presenti
- BUILD: assembleDebug OK (467 tasks, 0 errori)

### Fase 3: Domain Extraction — data/mongo → core/util (2026-02-28)
- Spostato ChatModels.kt (ChatSession, ChatMessage, MessageStatus) → core/util/model/
- Spostati 7 repository interfaces → core/util/repository/ (MongoRepository, ChatRepository, WellbeingRepository, InsightRepository, ProfileRepository, ProfileSettingsRepository, UnifiedContentRepository)
- Aggiornati ~25 consumer imports in: data/mongo (6 impl + 2 DI), features/home (3), features/write (1), features/chat (4), features/history (1), features/insight (1), features/profile (1), features/settings (1), features/onboarding (1), app (3)
- Rimosso android.util.Log da 4 file data/mongo (sostituito con println)
- Spostato NetworkConnectivityObserver → app/connectivity/ (core/util mantiene solo interfaccia ConnectivityObserver)
- HomeViewModel: tipo iniettato NetworkConnectivityObserver → ConnectivityObserver (interfaccia)
- DatabaseModule: return type esplicito ConnectivityObserver
- VERIFICA: zero android.* in core/util/src, zero android.util.Log in data/mongo/src
- VERIFICA: zero import com.lifo.mongo.repository.* per interfacce/modelli in features/ e app/
- BUILD: assembleDebug OK (467 tasks, 0 errori)

### Fase 2: core/ui Cleanup (2026-02-27)
- Fix ErrorBoundary.kt: aggiunto package com.lifo.ui, rimosso android.util.Log
- Fix FirebaseImageHelper.kt: rimosso android.util.Log
- Theme.kt: unificati 2 SideEffect blocks conflittuali in uno solo
- GoogleButton.kt: icon: Int -> iconPainter: Painter (KMP-ready)
- GalleryState.kt: GalleryImage.image: Uri -> String
- Gallery.kt: Gallery(images: List<Uri>) -> List<String>, GalleryUploader onImageSelect: (String)
- FirebaseImageHelper.kt: onImageDownload: (Uri) -> (String)
- Aggiornati consumatori: WriteViewModel, WriteScreen, WriteContent, WriteNavigation
- Aggiornati consumatori: DiaryHolder, UnifiedContentCard (mutableStateListOf<Uri> -> <String>)
- Aggiornato features/auth/AuthenticationContent per nuovo iconPainter
- VERIFICA: zero android.util.Log in core/ui, zero android.net.Uri in core/ui
- BUILD: assembleDebug OK

### Fase 1: core/util Cleanup (2026-02-27)
- Rimosso codice morto (AnimatedShimmer, LocalBottomAppBarHeight, PermissionDialog)
- Spostato DiaryHolder.kt -> features/home/components/
- Spostato Gallery.kt -> core/ui/components/
- Spostato fetchImagesFromFirebase -> core/ui/util/FirebaseImageHelper.kt
- Refactored Mood.kt: rimosso Color/drawable, creato MoodUiProvider in core/ui
- Refactored ChatEmotion.kt: rimosso Color, creato ChatEmotionUiProvider in core/ui
- Rimossa dipendenza core/util -> core/ui (CIRCOLARE ELIMINATA)
- Rimosso Compose, Coil, activity-compose, firebase-storage da core/util
- Spostati 16 mood drawable da core/util a core/ui
- core/ui ora dipende da core/util (direzione corretta: UI -> domain)
- BUILD: assembleDebug OK

## Stato Attuale

KMP Migration 10 Onde COMPLETATA. Tutti i 17 moduli (eccetto app) usano convention plugin KMP.
- **assembleDebug OK**: 746 tasks, 0 errori
- **Moduli KMP**: core/util, core/ui, data/mongo, features/{auth,home,write,chat,humanoid,history,insight,profile,settings,onboarding,feed,composer,social-profile,search,notifications,messaging,subscription}
- **Modulo Android-only**: app (com.android.application)

## Prossime Fasi (Opzionali)

- Spostare file puri da androidMain→commonMain nei moduli chat/humanoid (Phase 2 per iOS)
- Creare iosApp/ con SwiftUI che consuma i commonMain modules
- Delete dead code ProfileScreen.kt from app module
- Cleanup residual `kotlin.srcDirs('src/main/java')` when files actually move to commonMain/

## Ultimo Aggiornamento
- Data: 2026-03-02
- Sessione: KMP Migration ONDA 10 — COMPLETATA (assembleDebug OK, 746 tasks, 0 errori)

## Log Operazioni (cronologico)

#### [2026-03-01] Wave 6B - Decompose Navigation Migration
- AZIONE: Replaced Navigation Compose with Decompose 3.4.0 stack navigation
- NEW FILES:
  - app/.../DecomposeApp.kt (708 lines — main app composable with Children renderer, bottom bar, drawer, FAB, dialogs, deep links)
  - features/auth/.../navigation/AuthEntryPoint.kt (public bridge for internal AuthenticationScreen)
  - features/home/.../navigation/HomeEntryPoint.kt (public bridge for internal HomeScreen)
  - features/write/.../navigation/WriteEntryPoint.kt (public bridge for internal WriteScreen + setDiaryIdAndLoad)
  - features/history/.../navigation/HistoryEntryPoint.kt (public bridges for 3 internal history screens)
  - features/settings/.../navigation/SettingsEntryPoint.kt (public bridges for 5 settings screens with shared ViewModel key)
- MODIFIED FILES:
  - app/.../navigation/decompose/RootDestination.kt: 18 @Serializable destinations
  - app/.../navigation/decompose/RootComponent.kt: 18 typed Children + 15 navigation methods
  - app/.../MainActivity.kt: RootComponent via defaultComponentContext(), DecomposeApp integration
  - features/home/.../HomeScreen.kt: navController parameter made nullable
  - features/write/.../WriteViewModel.kt: added setDiaryIdAndLoad() method
- DELETED FILES (12):
  - app/.../CalmifyApp.kt, NavGraph.kt, NavigationState.kt
  - features/{auth,home,write,history,settings,onboarding,humanoid,chat}/.../navigation/*Navigation.kt (9 files)
- KEY DECISIONS:
  - Settings sub-screens flattened to top-level RootDestinations (no nested Decompose stacks)
  - Shared SettingsViewModel via koinViewModel(key = "settings_shared_vm")
  - RootComponent created with Auth default, replaceAll() to correct destination before AppState.Ready
  - Entry point composables bridge internal screen visibility across module boundaries
- BUILD: assembleDebug OK (606 tasks, 0 errori, 3 warnings deprecation only)

#### [2026-02-28] Fase 15 - Build System & Gradle (FINALE)
- libs.versions.toml: +7 versioni KMP, +11 librerie KMP, +3 plugin KMP (tutti commentati, pronti per l'uso)
- libs.versions.toml: rimossi arcore (v1.52.0), sceneview (v2.3.3), arcore library, arsceneview library (orfani da Fase 13)
- ProjectConfig.kt: aggiunto KMP_READY = false
- KmpModuleTemplate.md: creato template con guida step-by-step, priority table, library mapping
- BUILD: assembleDebug OK (470 tasks, 0 errori)

#### [2026-02-28] Fase 14 - app module Cleanup
- Log→println: 6 file, 41+ calls convertiti (MainActivity 25, FCM 11, GestureAdapter 4, TTSAdapter 6, CalmifyApp 1)
- Firebase injection: MainActivity (@Inject auth, 3x getInstance rimossi), CalmifyApp (param auth, 2x rimossi), ProfileScreen (param user)
- NavGraph: rimossi import unused (Log, Build, RequiresApi) + @RequiresApi(N) ridondante
- TAG companion rimossi: GestureAnimationAdapter, TTSLipSyncAdapter, CalmifyFCM
- FCM Service: mantenuto FirebaseAuth.getInstance() (Service senza @AndroidEntryPoint)
- BUILD: assembleDebug OK (467 tasks, 0 errori)

#### [2026-02-28] Fase 13 - features/humanoid + AR Removal + Chat Audio Pipeline
- PARTE A: AR Removal
  - ELIMINATI: 11 file AR da features/humanoid (domain/ar, data/ar, rendering, api, presentation, di)
  - PULITI: CalmifyApp.kt (imports + AvatarLiveChat composable ~115 righe), AvatarIntegrationEntryPoint.kt, NavigationState.kt, build.gradle humanoid
  - PULITI: ChatNavigation.kt (navigateToAvatarLiveChat param + extension), ChatScreen.kt (AR button), LiveScreen.kt (AR params + toggle)
- PARTE B: Humanoid Log→println (20 file, ~286 calls)
  - 8 agent paralleli hanno processato tutti i file
  - Rendering (3 file, 72), Animation (8 file, 87), VRM data (4 file, 66), API+lipsync+VM (5 file, 61)
- PARTE C: Chat Audio Pipeline Log→println (24 file, ~421 calls)
  - Big (4 file, 197), Medium (4 file, 116), Small pt1 (6 file, 69), Small pt2 (10 file, 39)
- TOTALE: 11 AR files eliminati, ~707 Log→println conversioni, 44 file modificati
- BUILD: assembleDebug OK (467 tasks, 0 errori)

#### [2026-02-28] Fase 12 - features/chat Cleanup
- AZIONE: Firebase inject + Log removal (97 totali) + @RequiresApi removal
- FILE MODIFICATI:
  - ChatViewModel.kt: auth inject, 20x Log→println, @RequiresApi rimosso, TAG rimosso, 2x FirebaseAuth.getInstance() rimossi
  - LiveChatViewModel.kt: 72x Log→println, TAG rimosso (FirebaseAuth già iniettato)
  - ChatScreen.kt: 5x android.util.Log→println, @RequiresApi rimosso
  - LiveScreen.kt: @RequiresApi rimosso (Build import mantenuto per runtime SDK check)
  - ChatNavigation.kt: @RequiresApi rimosso da liveRoute()
- NON TOCCATI: 25+ file audio pipeline (data/, domain/, audio/) — Log accettabili
- BUILD: assembleDebug OK (467 tasks, 0 errori)
- NOTA: LiveScreen.kt mantiene `import android.os.Build` per `Build.VERSION.SDK_INT >= TIRAMISU` check

#### [2026-02-28] Fase 10 - features/write Cleanup
- AZIONE: Firebase inject + Log removal + viewModel()→hiltViewModel()
- FILE MODIFICATI:
  - WriteViewModel.kt: auth+storage inject, ~18 Log→println, 3x FirebaseAuth + 2x FirebaseStorage rimossi
  - WriteScreen.kt: 3x Log.d→println, viewModel()→hiltViewModel(), import aggiornato
- BUILD: assembleDebug OK (467 tasks, 0 errori)

#### [2026-02-28] Fase 9 - features/home Cleanup
- AZIONE: Firebase inject + Log removal + @RequiresApi removal
- FILE MODIFICATI:
  - HomeViewModel.kt: auth+storage inject, ~28 Log→println, @RequiresApi rimosso, signOut() aggiunto
  - HomeContent.kt: 3x android.util.Log.d → println
  - SnapshotViewModel.kt: FirebaseAuth inject, getCurrentUserId()
  - SnapshotScreen.kt: rimosso FirebaseAuth.getInstance()
  - HomeNavigation.kt: rimossi Log+FirebaseAuth, signOut→viewModel.signOut()
  - DateFormatters.kt: rimosso @RequiresApi(O)
  - HeroGreetingCard.kt: rimossi 2x @RequiresApi(O)
- BUILD: assembleDebug OK (467 tasks, 0 errori)

#### [2026-02-27] Fase 1.1 - Eliminazione codice morto
- AZIONE: rimosso
- FILE: core/util/.../AnimatedShimmer.kt, LocalBottomAppBarHeight.kt, PermissionDialog.kt
- BUILD: OK

#### [2026-02-27] Fase 1.2 - Spostamento DiaryHolder
- AZIONE: spostato
- FILE: core/util/.../DiaryHolder.kt -> features/home/.../components/DiaryHolder.kt
- IMPORT AGGIORNATI IN: features/home/HomeContent.kt
- BUILD: OK (fix: rinominato ShowGalleryButton -> DiaryShowGalleryButton per conflitto)

#### [2026-02-27] Fase 1.3 - Spostamento Gallery
- AZIONE: spostato
- FILE: core/util/.../Gallery.kt -> core/ui/.../components/Gallery.kt
- IMPORT AGGIORNATI IN: features/home/DiaryHolder.kt, UnifiedContentCard.kt, features/write/WriteContent.kt
- BUILD: OK (fix: aggiunto Coil a core/ui deps, copiato add_24px.xml)

#### [2026-02-27] Fase 1.4 - Spostamento fetchImagesFromFirebase
- AZIONE: creato + rimosso da originale
- FILE: core/ui/.../util/FirebaseImageHelper.kt (nuovo), core/util/.../UtilFunctions.kt (rimossa funzione)
- IMPORT AGGIORNATI IN: features/home/DiaryHolder.kt, UnifiedContentCard.kt, features/write/WriteViewModel.kt
- BUILD: OK (fix: aggiunto Firebase Storage a core/ui deps)

#### [2026-02-27] Fase 1.5 - Refactor Mood.kt
- AZIONE: semplificato enum + creato MoodUiProvider
- FILE: core/util/.../model/Mood.kt (rimosso icon/Color), core/ui/.../providers/MoodUiProvider.kt (nuovo)
- IMPORT AGGIORNATI IN: features/home/DiaryHolder.kt, UnifiedContentCard.kt, features/write/WriteContent.kt, features/history/HistoryContent.kt, DiaryHistoryFullScreen.kt
- BUILD: OK (fix: aggiunto core/util dep a core/ui, rimosso core/ui dep da core/util)
- NOTE: Risolto dipendenza circolare — core/ui -> core/util (corretto), rimosso core/util -> core/ui

#### [2026-02-27] Fase 1.6 - Refactor ChatEmotion.kt
- AZIONE: semplificato enum + creato ChatEmotionUiProvider
- FILE: core/util/.../model/ChatEmotion.kt (rimosso Color), core/ui/.../providers/ChatEmotionUiProvider.kt (nuovo)
- IMPORT AGGIORNATI IN: features/chat/ChatScreen.kt (rimosso import inutilizzato)
- BUILD: OK

#### [2026-02-27] Fase 1.7 - Pulizia build.gradle core/util
- AZIONE: rimosso
- FILE: core/util/build.gradle
- RIMOSSO: Compose BOM/UI/Material3, compose plugin, activity-compose, Coil, Firebase Storage, core:ui dep
- MANTENUTO: Firebase Firestore (annotations), core-ktx, coroutines
- BUILD: assembleDebug OK

#### [2026-02-27] Fase 1.8 - Pulizia drawable duplicati
- AZIONE: rimosso
- FILE: 17 drawable XML da core/util/src/main/res/drawable/ (spostati a core/ui)
- BUILD: assembleDebug OK

#### [2026-02-27] Fase 2.1 - Fix ErrorBoundary.kt
- AZIONE: fix package + rimosso android.util.Log
- FILE: core/ui/.../ErrorBoundary.kt, features/write/WriteScreen.kt (import aggiornato)
- BUILD: OK

#### [2026-02-27] Fase 2.2 - Fix FirebaseImageHelper.kt
- AZIONE: rimosso android.util.Log + Log.d debug residuo
- FILE: core/ui/.../util/FirebaseImageHelper.kt
- BUILD: OK

#### [2026-02-27] Fase 2.3 - Refactor Theme.kt
- AZIONE: unificati 2 SideEffect blocks in uno (rimosso conflitto status bar color)
- FILE: core/ui/.../theme/Theme.kt
- BUILD: OK

#### [2026-02-27] Fase 2.4 - Refactor GoogleButton.kt
- AZIONE: icon: Int -> iconPainter: Painter (KMP-ready, no R.drawable default)
- FILE: core/ui/.../components/GoogleButton.kt, features/auth/AuthenticationContent.kt
- BUILD: OK

#### [2026-02-27] Fase 2.5 - Uri -> String (core/ui)
- AZIONE: rimosso android.net.Uri da 3 file core/ui
- FILE: GalleryState.kt (GalleryImage.image: String), Gallery.kt (List<String>, onImageSelect: (String)), FirebaseImageHelper.kt (onImageDownload: (String))
- BUILD: OK (consumatori rotti, atteso)

#### [2026-02-27] Fase 2.6 - Aggiornamento features/write
- AZIONE: aggiornati tipi per Uri->String
- FILE: WriteViewModel.kt (putFile(Uri.parse()), image.toString()), WriteScreen.kt, WriteContent.kt, WriteNavigation.kt
- NOTA CRITICA: putFile(galleryImage.image) -> putFile(Uri.parse(galleryImage.image))
- BUILD: OK

#### [2026-02-27] Fase 2.7 - Aggiornamento features/home
- AZIONE: mutableStateListOf<Uri> -> <String>, rimosso import android.net.Uri
- FILE: DiaryHolder.kt, UnifiedContentCard.kt
- BUILD: OK

#### [2026-02-27] Fase 2.8 - Build finale
- BUILD: assembleDebug OK (467 tasks, 0 errori)
- VERIFICA: grep android.util.Log core/ui/src/ -> 0 risultati
- VERIFICA: grep android.net.Uri core/ui/src/ -> 0 risultati

#### [2026-02-28] Fase 3.1 - Move ChatModels.kt
- AZIONE: spostato
- FILE: data/mongo/.../ChatModels.kt -> core/util/.../model/ChatModels.kt
- TIPI: ChatSession, ChatMessage, MessageStatus
- BUILD: OK

#### [2026-02-28] Fase 3.2 - Move 7 repository interfaces
- AZIONE: spostato
- FILE: 7 interfacce da data/mongo/.../repository/ -> core/util/.../repository/
- INTERFACCE: MongoRepository (+Diaries typealias), ChatRepository, WellbeingRepository, InsightRepository, ProfileRepository, ProfileSettingsRepository, UnifiedContentRepository
- BUILD: OK

#### [2026-02-28] Fase 3.3 - Update ALL consumer imports (~25 file)
- AZIONE: aggiornati import
- DATA/MONGO: FirestoreDiaryRepository, ChatRepositoryImpl, UnifiedContentRepositoryImpl, FirestoreWellbeingRepository, FirestoreInsightRepository, FirestoreProfileRepository, FirestoreProfileSettingsRepository
- DI: MongoDataModule (wildcard -> explicit imports), RepositoryModule (wildcard -> explicit imports)
- FEATURES: HomeViewModel, HomeScreen, SnapshotViewModel, WriteViewModel, HistoryViewModel, ChatViewModel, LiveChatViewModel, ChatUiModels, ChatBubble, InsightViewModel, ProfileViewModel, SettingsViewModel, OnboardingViewModel
- APP: CalmifyApp, MainActivity (fully qualified), NavGraph (fully qualified)
- BUILD: OK

#### [2026-02-28] Fase 3.4 - Remove android.util.Log from data/mongo
- AZIONE: sostituito Log.d/e/w con println
- FILE: FirestoreDiaryRepository.kt, FirestoreProfileSettingsRepository.kt, ChatRepositoryImpl.kt, UnifiedContentRepositoryImpl.kt
- BUILD: OK

#### [2026-02-28] Fase 3.5 - Move NetworkConnectivityObserver
- AZIONE: spostato core/util -> app/connectivity
- FILE: NetworkConnectivityObserver.kt -> app/.../connectivity/NetworkConnectivityObserver.kt
- AGGIORNATO: DatabaseModule (import + return type ConnectivityObserver), HomeViewModel (tipo: ConnectivityObserver)
- BUILD: OK

#### [2026-02-28] Fase 3.6 - Build finale
- BUILD: assembleDebug OK (467 tasks, 0 errori)
- VERIFICA: zero android.* in core/util/src
- VERIFICA: zero android.util.Log in data/mongo/src
- VERIFICA: 7 interfacce in core/util/repository/
- VERIFICA: ChatModels.kt in core/util/model/
- VERIFICA: zero import vecchi com.lifo.mongo.repository.* per interfacce in features/ e app/

#### [2026-02-28] Fase 4.1 - Fix AuthenticationScreen bug + Log removal
- AZIONE: fix bug doppia chiamata + rimosso android.util.Log + rimosso Firebase imports
- FILE: features/auth/.../AuthenticationScreen.kt
- BUG FIX: onSuccessfulFirebaseSignIn(tokenId) chiamato 2 volte (1 async + 1 sync) → 1 volta
- BUG FIX: rimossa chiamata ridondante FirebaseAuth.signInWithCredential() (il ViewModel la fa già)
- IMPORT RIMOSSI: android.util.Log, FirebaseAuth, GoogleAuthProvider
- BUILD: OK

#### [2026-02-28] Fase 4.2 - @HiltViewModel + FirebaseAuth injection
- AZIONE: migrato ViewModel a @HiltViewModel con @Inject constructor
- FILE: features/auth/.../AuthenticationViewModel.kt
- PRIMA: FirebaseAuth.getInstance() diretto
- DOPO: @Inject constructor(private val auth: FirebaseAuth) — Hilt inietta da FirestoreModule
- BUILD: OK

#### [2026-02-28] Fase 4.3 - mutableStateOf → StateFlow
- AZIONE: migrato state management
- FILE: features/auth/.../AuthenticationViewModel.kt
- PRIMA: var authenticated = mutableStateOf(false), var loadingState = mutableStateOf(false)
- DOPO: MutableStateFlow + StateFlow<Boolean> (KMP-compatible, no Compose dependency)
- BUILD: OK

#### [2026-02-28] Fase 4.4 - AuthNavigation update
- AZIONE: aggiornato DI + state observation
- FILE: features/auth/.../navigation/AuthNavigation.kt
- PRIMA: viewModel(), by viewModel.authenticated (mutableStateOf delegate)
- DOPO: hiltViewModel(), collectAsStateWithLifecycle() (StateFlow collection)
- BUILD: OK

#### [2026-02-28] Fase 4.5 - Build finale
- BUILD: assembleDebug OK (467 tasks, 0 errori)
- VERIFICA: zero android.util.Log in features/auth/src
- VERIFICA: zero FirebaseAuth/GoogleAuthProvider in AuthenticationScreen
- VERIFICA: @HiltViewModel presente in ViewModel
- VERIFICA: solo StateFlow (no mutableStateOf) in ViewModel
- VERIFICA: solo hiltViewModel (no viewModel()) in AuthNavigation

#### [2026-02-28] Fasi 5-8 (Batch) - Feature Modules Cleanup
- Fase 5 (insight): SKIP — già pulito
- Fase 6 (history/HistoryViewModel.kt):
  - Rimosso android.util.Log (5x Log.e), rimosso TAG companion
  - Rimosso @RequiresApi(Build.VERSION_CODES.N) + import android.os.Build + import RequiresApi
  - FirebaseAuth.getInstance() → iniettato via Hilt constructor
- Fase 7 (settings):
  - SettingsViewModel.kt: aggiunto userProfileImageUrl a SettingsUiState, popolato da auth.currentUser?.photoUrl
  - SettingsScreen.kt: rimosso import FirebaseAuth, ProfileOverviewCard riceve URL via parametro
- Fase 8 (onboarding): SKIP — già pulito
- BUILD: assembleDebug OK (467 tasks, 0 errori)
