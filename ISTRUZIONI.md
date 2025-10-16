## 🎯 OBIETTIVI
1. **Migrare da Atlas Device Sync (EOL) a Firebase Cloud Firestore**
2. **Aggiornare TUTTO lo stack alle ultime versioni stabili (Ottobre 2025)**

---

## 📝 VERSIONI AGGIORNATE DA IMPLEMENTARE

Firebase BOM v34.4.0 è la versione corrente, che ha rimosso i moduli KTX dalla v34.0.0. Kotlin 2.2.20 è l'ultima versione stabile rilasciata. Compose BOM 2025.01.00 è disponibile. Android Gradle Plugin 8.13.0 è la versione più recente.

### **gradle/libs.versions.toml - VERSIONI TARGET**
```toml
[versions]
# Core - ULTIME STABILI OTTOBRE 2025
agp = "8.13.0"                    # Da 8.2.2
kotlin = "2.2.20"                  # Da 1.9.22 
ksp = "2.2.20-2.0.2"             # Aggiornato per K2
compose-bom = "2025.01.00"         # Da 2024.02.00
compose-compiler = "1.5.15"        # Auto-gestito dal BOM
gradle-wrapper = "8.11.1"          # Gradle stesso

# Firebase - LATEST con Firestore
firebase-bom = "34.4.0"            # Da 33.7.0 - SENZA KTX!
google-services = "4.4.2"          # Da 4.4.1

# Database 
room = "2.7.2"                     # Da 2.6.1
# RIMUOVERE realm = "1.13.0"      # DA ELIMINARE COMPLETAMENTE

# DI & Architecture
dagger-hilt = "2.54"               # Da 2.50
hilt-navigation = "1.2.0"          # Da 1.1.0

# Async
kotlin-coroutines = "1.9.0"        # Da 1.7.3

# AndroidX Core
core-ktx = "1.15.0"                # Da 1.12.0
lifecycle = "2.8.7"                # Da 2.7.0
activity-compose = "1.9.3"         # Da 1.8.2
navigation-compose = "2.8.5"       # Da 2.7.7
appcompat = "1.7.0"                # Da 1.6.1

# UI Libraries
coil = "3.0.4"                     # Da 2.5.0 - MAJOR UPDATE
accompanist = "0.36.0"             # Da 0.32.0
material3 = "1.3.1"                # Via Compose BOM

# Network
okhttp = "4.12.0"                  # Resta su 4.x per stabilità
retrofit = "2.11.0"                # Se usi REST APIs

# AI/ML
generative-ai = "0.9.0"            # Da 0.2.0
```

---

## 🚀 ROADMAP OPERATIVA DETTAGLIATA

### **FASE 0: PREPARAZIONE AMBIENTE** ✅ COMPLETATA
- ✅ Backup completo del progetto
- ✅ Creato branch `migration/firebase-2025`
- ✅ Documentato stato attuale database Realm
- ✅ Export dati esistenti pianificato (FASE 12)

### **FASE 1: AGGIORNAMENTO GRADLE & KOTLIN** ✅ COMPLETATA
- ✅ Updated Gradle Wrapper a 8.11.1
- ✅ Updated AGP a 8.13.0
- ✅ Updated Kotlin a 2.2.20
- ✅ Abilitato K2 compiler (kotlin.experimental.useK2Compiler=true)
- ✅ Updated KSP 2.2.20-1.0.29 per K2 compatibility
- ✅ Updated tutte le dipendenze AndroidX October 2025

### **FASE 2: FIREBASE SETUP CON FIRESTORE** ✅ COMPLETATA
- ✅ Rimosso `realm-sync` da dependencies
- ✅ Aggiunto Firebase BOM 34.4.0 SENZA moduli -ktx
- ✅ Aggiunto `firebase-firestore` (no -ktx)
- ✅ Aggiunto `firebase-auth` (no -ktx)
- ✅ google-services.json già presente
- ✅ Creato FirestoreModule con Hilt DI
- ✅ Configurato offline persistence (100MB cache)

### **FASE 3: MIGRAZIONE COMPOSE & MATERIAL 3**

#### Update Dependencies:
- ✅ Compose BOM a 2025.01.00
- ✅ Rimuovere version esplicite Compose
- ✅ Material 3 completo via BOM

#### Material You Implementation:
- ✅ Dynamic color theming
- ✅ Migrate to M3 components:
  - `AlertDialog` → `AlertDialog` M3
  - `TextField` → `OutlinedTextField` M3
  - `TopAppBar` → `TopAppBar` con scroll behavior
  - `NavigationBar` → `NavigationBar` M3
  - Surface tints e tone elevation

### **FASE 4: KOTLIN 2.2 FEATURES**
- ✅ Context parameters (preview)
- ✅ Value classes per type safety
- ✅ Data objects per singleton
- ✅ Sealed interfaces dove appropriato
- ✅ Trailing comma in lambda

### **FASE 5: RIMOZIONE REALM/MONGODB** ✅ COMPLETATA

#### Files backed up (.old):
```
✅ data/mongo/repository/MongoDB.kt.old
✅ data/mongo/repository/MongoRepository.kt.old
```

#### Files trasformati:
```
✅ Diary: RealmObject → data class Firestore-compatible
⏳ ChatMessageEntity → da migrare in FASE 10
⏳ ChatSessionEntity → da migrare in FASE 10
⏳ ImageToUpload → da migrare in FASE 10
⏳ ImageToDelete → da migrare in FASE 10
```

### **FASE 6: FIRESTORE REPOSITORY IMPLEMENTATION** 🔄 IN CORSO

#### Nuova struttura collections:
```
diaries/
  {diaryId}/
    - ownerId, title, description, mood, images[], date

chats/ (TO DO in FASE 10)
  {chatId}/
    messages/
      {messageId}
```

#### Implementato:
- ✅ `FirestoreDiaryRepository` con offline-first
- ✅ Real-time listeners con Flow
- ✅ Firestore Timestamp utilities
- ✅ Type-safe conversions (Date ↔ Instant ↔ Timestamp)
- ⏳ `FirestoreChatRepository` (FASE 10)
- ⏳ Batch operations per chat (FASE 10)

### **FASE 7: ROOM PER CACHE LOCALE**
- ✅ Room 2.7.2 setup
- ✅ Entities per cache offline
- ✅ DAOs per operazioni locali
- ✅ Sync logic Firestore ↔ Room

### **FASE 8: HILT 2.54 MIGRATION**
- ✅ Update Hilt a 2.54
- ✅ `@HiltViewModel` ovunque
- ✅ Assisted Injection
- ✅ Navigation integration
- ✅ Remove `MongoDataModule`
- ✅ Create `FirestoreModule`

### **FASE 9: COMPOSE PERFORMANCE**
- ✅ Strong skipping mode
- ✅ Stable/Immutable annotations
- ✅ `contentType` in lazy layouts
- ✅ Shared element transitions
- ✅ Predictive back gesture

### **FASE 10: MIGRAZIONE AI FEATURES**

#### Chat Module:
- ✅ Update per Firestore real-time
- ✅ Rimuovere -ktx imports
- ✅ WebSocket → Firestore listeners
- ✅ Gemini AI latest APIs

#### Home Module:
- ✅ Firestore queries con paginazione
- ✅ Snapshot listeners per updates
- ✅ Search con Firestore indexes

#### Write Module:
- ✅ Firestore document creation
- ✅ Offline queue handling

### **FASE 11: TESTING**
- ✅ Unit tests repositories
- ✅ Integration tests Firestore
- ✅ UI tests con Compose Testing
- ✅ Performance profiling
- ✅ Offline scenarios

### **FASE 12: MIGRAZIONE DATI**
- ✅ Script export Realm → JSON
- ✅ Transform data structure
- ✅ Batch import to Firestore
- ✅ Validation checks
- ✅ Rollback plan

### **FASE 13: MONITORING**
- ✅ Firebase Crashlytics
- ✅ Firebase Performance
- ✅ Firebase Analytics
- ✅ Custom traces
- ✅ A/B testing setup

### **FASE 14: OTTIMIZZAZIONI FINALI**
- ✅ R8 full mode
- ✅ Baseline profiles
- ✅ App Bundle
- ✅ ProGuard rules
- ✅ Remove unused resources

### **FASE 15: CLEANUP & DOCUMENTAZIONE**
- ✅ Eliminare package `data/mongo`
- ✅ Update README
- ✅ Migration guide
- ✅ API documentation
- ✅ Changelog

---\

## ⚠️ BREAKING CHANGES CRITICI

### Firebase KTX Removal (v34.0.0+):
```kotlin
// PRIMA (Non funziona più!)
implementation("com.google.firebase:firebase-firestore-ktx")

// DOPO (Corretto)
implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
implementation("com.google.firebase:firebase-firestore")
```

### Material 3 Components:
- Tutti i components devono migrare a M3
- Theming completamente diverso
- Dynamic colors di default

### Coil 3.0:
- API completamente ridisegnate
- AsyncImage composable nuovo

### Room 2.7:
- Nuove annotations
- KSP obbligatorio
## **CALMIFY PROJECT - LLM-QUERYABLE FILE INDEX**

### **FILE REGISTRY BY FUNCTION**

```
=== AUTHENTICATION & USER MANAGEMENT ===
[AUTH_SCREEN] features/auth/src/main/java/com/lifo/auth/AuthenticationScreen.kt
[AUTH_UI] features/auth/src/main/java/com/lifo/auth/AuthenticationContent.kt  
[AUTH_LOGIC] features/auth/src/main/java/com/lifo/auth/AuthenticationViewModel.kt
[AUTH_ROUTES] features/auth/src/main/java/com/lifo/auth/navigation/AuthNavigation.kt
[AUTH_STRINGS] features/auth/src/main/res/values/strings.xml
[GOOGLE_BUTTON] core/ui/src/main/java/com/lifo/ui/components/GoogleButton.kt
[USER_PROFILE] app/src/main/java/com/lifo/app/ProfileScreen.kt

=== CHAT & AI INTEGRATION ===
[CHAT_MAIN] features/chat/src/main/java/com/lifo/chat/presentation/screen/ChatScreen.kt
[CHAT_VM] features/chat/src/main/java/com/lifo/chat/presentation/viewmodel/ChatViewModel.kt
[LIVE_CHAT_VM] features/chat/src/main/java/com/lifo/chat/presentation/viewmodel/LiveChatViewModel.kt
[CHAT_ROUTES] features/chat/src/main/java/com/lifo/chat/navigation/ChatNavigation.kt
[CHAT_STATE] features/chat/src/main/java/com/lifo/chat/domain/model/LiveChatState.kt
[CHAT_MODELS] features/chat/src/main/java/com/lifo/chat/domain/model/ChatUiModels.kt

=== REALTIME COMMUNICATION ===
[OPENAI_CLIENT] features/chat/src/main/java/com/lifo/chat/data/realtime/OpenAIRealtimeClient.kt
[GEMINI_WS] features/chat/src/main/java/com/lifo/chat/data/realtime/GeminiLiveWebSocketClient.kt
[GEMINI_WS_ALT] features/chat/src/main/java/com/lifo/chat/data/websocket/GeminiLiveWebSocketClient.kt
[WEBRTC_CLIENT] features/chat/src/main/java/com/lifo/chat/data/realtime/RealtimeWebRTCClient.kt
[WEBRTC_MODELS] features/chat/src/main/java/com/lifo/chat/data/realtime/WebRTCModels.kt
[RT_SESSION] features/chat/src/main/java/com/lifo/chat/data/realtime/RealtimeSessionManager.kt
[RT_EVENTS] features/chat/src/main/java/com/lifo/chat/data/realtime/RealtimeEvent.kt
[RT_MODELS] features/chat/src/main/java/com/lifo/chat/data/realtime/RealtimeModels.kt
[EPHEMERAL_KEY] features/chat/src/main/java/com/lifo/chat/data/realtime/EphemeralKeyManager.kt

=== AUDIO SYSTEMS ===
[GEMINI_VOICE] features/chat/src/main/java/com/lifo/chat/audio/GeminiNativeVoiceSystem.kt
[OFFLINE_VOICE] features/chat/src/main/java/com/lifo/chat/audio/OnDeviceNaturalVoiceSystem.kt
[GEMINI_KILLER] features/chat/src/main/java/com/lifo/chat/audio/GeminiKillerAudioSystem.kt
[AUDIO_TEST] features/chat/src/main/java/com/lifo/chat/audio/AudioTestUtility.kt
[VOICE_DEBUG] features/chat/src/main/java/com/lifo/chat/audio/VoiceSystemDiagnostics.kt
[JSON_BUILDER] features/chat/src/main/java/com/lifo/chat/audio/JsonBuilder.kt
[GEMINI_AUDIO_MGR] features/chat/src/main/java/com/lifo/chat/data/audio/GeminiLiveAudioManager.kt

=== AUDIO PROCESSING ===
[AUDIO_CAPTURE] features/chat/src/main/java/com/lifo/chat/domain/audio/RealtimeAudioCapture.kt
[AUDIO_PLAYER] features/chat/src/main/java/com/lifo/chat/domain/audio/RealtimeAudioPlayer.kt
[PTT_CONTROL] features/chat/src/main/java/com/lifo/chat/domain/audio/PushToTalkController.kt
[BARGE_IN] features/chat/src/main/java/com/lifo/chat/domain/audio/AdaptiveBargeinDetector.kt
[AUDIO_DUCKING] features/chat/src/main/java/com/lifo/chat/domain/audio/AdvancedDuckingEngine.kt
[AUDIO_LEVEL] features/chat/src/main/java/com/lifo/chat/domain/audio/AudioLevelExtractor.kt
[AUDIO_QUALITY] features/chat/src/main/java/com/lifo/chat/domain/audio/AudioQualityAnalyzer.kt
[AUDIO_VIZ] features/chat/src/main/java/com/lifo/chat/domain/audio/AudioVisualizationProcessor.kt
[CONVERSATION_CTX] features/chat/src/main/java/com/lifo/chat/domain/audio/ConversationContextManager.kt
[MULTI_DEVICE] features/chat/src/main/java/com/lifo/chat/domain/audio/MultiDeviceAudioManager.kt

=== CAMERA & VISUAL ===
[CAMERA_MGR] features/chat/src/main/java/com/lifo/chat/data/camera/GeminiLiveCameraManager.kt
[CAMERA_PREVIEW] features/chat/src/main/java/com/lifo/chat/presentation/components/LiveCameraPreview.kt

=== CHAT UI COMPONENTS ===
[CHAT_BUBBLE] features/chat/src/main/java/com/lifo/chat/presentation/components/ChatBubble.kt
[CHAT_INPUT] features/chat/src/main/java/com/lifo/chat/presentation/components/ChatInput.kt
[PTT_BUTTON] features/chat/src/main/java/com/lifo/chat/presentation/components/PushToTalkButton.kt
[MIC_ICON] features/chat/src/main/java/com/lifo/chat/presentation/components/AnimatedMicrophoneIcon.kt
[AUDIO_INDICATOR] features/chat/src/main/java/com/lifo/chat/presentation/components/AudioLevelIndicator.kt
[FLUID_INDICATOR] features/chat/src/main/java/com/lifo/chat/presentation/components/FluidAudioIndicator.kt
[CHAT_VISUALIZER] features/chat/src/main/java/com/lifo/chat/presentation/components/LiveChatVisualizer.kt

=== VISUAL EFFECTS ===
[LIQUID_GLOBE] features/chat/src/main/java/com/lifo/chat/presentation/components/LiquidGlobe.kt
[GEMINI_LIQUID] features/chat/src/main/java/com/lifo/chat/presentation/components/GeminiLiquidVisualizer.kt
[WAVEFORM_VIZ] features/chat/src/main/java/com/lifo/chat/presentation/components/effects/LiveWaveformVisualizer.kt
[EMOTION_GRAD] features/chat/src/main/java/com/lifo/chat/presentation/components/effects/EmotionGradients.kt
[PREMIUM_AURA] features/chat/src/main/java/com/lifo/chat/presentation/components/effects/PremiumAuraEffect.kt
[LIQUID_SHADER] features/chat/src/main/res/raw/liquid_wave.glsl

=== DATABASE CORE ===
[APP_DATABASE] data/mongo/src/main/java/com/lifo/mongo/database/AppDatabase.kt
[TYPE_CONVERTERS] data/mongo/src/main/java/com/lifo/mongo/database/Converters.kt
[STRING_CONVERTER] data/mongo/src/main/java/com/lifo/mongo/database/StringListConverter.kt

=== DATABASE ENTITIES ===
[CHAT_MSG_ENTITY] data/mongo/src/main/java/com/lifo/mongo/database/entity/ChatMessageEntity.kt
[CHAT_SESSION_ENTITY] data/mongo/src/main/java/com/lifo/mongo/database/entity/ChatSessionEntity.kt
[IMG_UPLOAD_ENTITY] data/mongo/src/main/java/com/lifo/mongo/database/entity/ImageToUpload.kt
[IMG_DELETE_ENTITY] data/mongo/src/main/java/com/lifo/mongo/database/entity/ImageToDelete.kt

=== DATABASE DAO ===
[CHAT_MSG_DAO] data/mongo/src/main/java/com/lifo/mongo/database/dao/ChatMessageDao.kt
[CHAT_SESSION_DAO] data/mongo/src/main/java/com/lifo/mongo/database/dao/ChatSessionDao.kt
[IMG_UPLOAD_DAO] data/mongo/src/main/java/com/lifo/mongo/database/dao/ImageToUploadDao.kt
[IMG_DELETE_DAO] data/mongo/src/main/java/com/lifo/mongo/database/dao/ImageToDeleteDao.kt

=== REPOSITORIES ===
[CHAT_REPO] data/mongo/src/main/java/com/lifo/mongo/repository/ChatRepository.kt
[CHAT_REPO_IMPL] data/mongo/src/main/java/com/lifo/mongo/repository/ChatRepositoryImpl.kt
[UNIFIED_REPO] data/mongo/src/main/java/com/lifo/mongo/repository/UnifiedContentRepository.kt
[UNIFIED_REPO_IMPL] data/mongo/src/main/java/com/lifo/mongo/repository/UnifiedContentRepositoryImpl.kt
[MONGO_REPO] data/mongo/src/main/java/com/lifo/mongo/repository/MongoRepository.kt
[MONGODB] data/mongo/src/main/java/com/lifo/mongo/repository/MongoDB.kt
[CHAT_DB_MODELS] data/mongo/src/main/java/com/lifo/mongo/repository/ChatModels.kt
[CONTENT_MAPPERS] data/mongo/src/main/java/com/lifo/mongo/repository/HomeContentItemMappers.kt
[REQUEST_STATE_DB] data/mongo/src/main/java/com/lifo/mongo/repository/RequestState.kt

=== HOME FEATURE ===
[HOME_SCREEN] features/home/src/main/java/com/lifo/home/HomeScreen.kt
[HOME_CONTENT] features/home/src/main/java/com/lifo/home/HomeContent.kt
[HOME_VM] features/home/src/main/java/com/lifo/home/HomeViewModel.kt
[HOME_TOPBAR] features/home/src/main/java/com/lifo/home/HomeTopBar.kt
[HOME_NAV] features/home/src/main/java/com/lifo/home/navigation/HomeNavigation.kt
[LOADING_SCREEN] features/home/src/main/java/com/lifo/home/LoadingScreen.kt
[LOADING_SYSTEM] features/home/src/main/java/com/lifo/home/LoadingSystem.kt
[NAV_BAR] features/home/src/main/java/com/lifo/home/EnterpriseNavigationBar.kt
[PERMISSION_API] features/home/src/main/java/com/lifo/home/MissingPermissionsApi.kt

=== HOME COMPONENTS ===
[CONTENT_CARD] features/home/src/main/java/com/lifo/home/components/UnifiedContentCard.kt
[SEARCH_BAR] features/home/src/main/java/com/lifo/home/components/UnifiedSearchBar.kt
[FILTER_CHIPS] features/home/src/main/java/com/lifo/home/components/FilterChipRow.kt

=== WRITE FEATURE ===
[WRITE_SCREEN] features/write/src/main/java/com/lifo/write/WriteScreen.kt
[WRITE_CONTENT] features/write/src/main/java/com/lifo/write/WriteContent.kt
[WRITE_VM] features/write/src/main/java/com/lifo/write/WriteViewModel.kt
[WRITE_TOPBAR] features/write/src/main/java/com/lifo/write/WriteTopBar.kt
[WRITE_NAV] features/write/src/main/java/com/lifo/write/navigation/WriteNavigation.kt

=== MAIN APP ===
[MAIN_ACTIVITY] app/src/main/java/com/lifo/calmifyapp/MainActivity.kt
[APP_CLASS] app/src/main/java/com/lifo/calmifyapp/CalmifyApplication.kt
[APP_COMPOSE] app/src/main/java/com/lifo/app/CalmifyApp.kt
[NAV_GRAPH] app/src/main/java/com/lifo/calmifyapp/navigation/NavGraph.kt
[NAV_STATE] app/src/main/java/com/lifo/navigation/NavigationState.kt

=== DEPENDENCY INJECTION ===
[DB_MODULE] app/src/main/java/com/lifo/calmifyapp/di/DatabaseModule.kt
[MONGO_MODULE] data/mongo/src/main/java/com/lifo/mongo/di/MongoDataModule.kt
[MONGO_PROVIDER] data/mongo/src/main/java/com/lifo/mongo/di/MongoDatabaseProvider.kt
[CHAT_MODULE] features/chat/src/main/java/com/lifo/chat/di/ChatModule.kt
[AUDIO_MODULE] features/chat/src/main/java/com/lifo/chat/di/AudioModule.kt
[GEMINI_MODULE] features/chat/src/main/java/com/lifo/chat/di/GeminiLiveModule.kt

=== UI THEME ===
[THEME] core/ui/src/main/java/com/lifo/ui/theme/Theme.kt
[COLORS] core/ui/src/main/java/com/lifo/ui/theme/Color.kt
[TYPOGRAPHY] core/ui/src/main/java/com/lifo/ui/theme/Type.kt
[ELEVATION] core/ui/src/main/java/com/lifo/ui/theme/Elevation.kt

=== UI COMPONENTS ===
[ALERT_DIALOG] core/ui/src/main/java/com/lifo/ui/components/AlertDialog.kt
[ERROR_BOUNDARY] core/ui/src/main/java/com/lifo/ui/ErrorBoundary.kt
[GALLERY_STATE] core/ui/src/main/java/com/lifo/ui/GalleryState.kt
[UI_MANIFEST] core/ui/src/main/AndroidManifest.xml

=== UTILITIES ===
[CONSTANTS] core/util/src/main/java/com/lifo/util/Constants.kt
[UTIL_FUNCS] core/util/src/main/java/com/lifo/util/UtilFunctions.kt
[SCREEN_ENUM] core/util/src/main/java/com/lifo/util/Screen.kt
[GALLERY_UTIL] core/util/src/main/java/com/lifo/util/Gallery.kt
[SHIMMER] core/util/src/main/java/com/lifo/util/AnimatedShimmer.kt
[PERMISSION_DIALOG] core/util/src/main/java/com/lifo/util/PermissionDialog.kt
[DIARY_HOLDER] core/util/src/main/java/com/lifo/util/DiaryHolder.kt

=== DATA MODELS ===
[DIARY_MODEL] core/util/src/main/java/com/lifo/util/model/Diary.kt
[MOOD_MODEL] core/util/src/main/java/com/lifo/util/model/Mood.kt
[EMOTION_MODEL] core/util/src/main/java/com/lifo/util/model/ChatEmotion.kt
[CONTENT_ITEM] core/util/src/main/java/com/lifo/util/model/HomeContentItem.kt
[REQUEST_STATE] core/util/src/main/java/com/lifo/util/model/RequestState.kt

=== NETWORK ===
[CONNECTIVITY_OBS] core/util/src/main/java/com/lifo/util/connectivity/ConnectivityObserver.kt
[NETWORK_OBS] core/util/src/main/java/com/lifo/util/connectivity/NetworkConnectivityObserver.kt

=== CONFIGURATION ===
[API_CONFIG] features/chat/src/main/java/com/lifo/chat/config/ApiConfigManager.kt
[PERMISSION_HANDLER] features/chat/src/main/java/com/lifo/chat/util/PermissionHandler.kt
[PROJECT_CONFIG] buildSrc/src/main/java/ProjectConfig.kt

=== BUILD FILES ===
[ROOT_BUILD] build.gradle
[BUILDSRC] buildSrc/build.gradle.kts
[APP_BUILD] app/build.gradle
[UI_BUILD] core/ui/build.gradle
[UTIL_BUILD] core/util/build.gradle
[MONGO_BUILD] data/mongo/build.gradle
[AUTH_BUILD] features/auth/build.gradle
[CHAT_BUILD] features/chat/build.gradle
[HOME_BUILD] features/home/build.gradle
[WRITE_BUILD] features/write/build.gradle
[VERSION_CATALOG] gradle/libs.versions.toml
[GRADLE_WRAPPER] gradle/wrapper/gradle-wrapper.properties
[GRADLE_PROPS] gradle.properties
[SETTINGS] settings.gradle
```

### **QUICK LOOKUP TAGS**

```
#authentication: AUTH_SCREEN, AUTH_UI, AUTH_LOGIC, GOOGLE_BUTTON
#chat-ai: CHAT_MAIN, OPENAI_CLIENT, GEMINI_WS, WEBRTC_CLIENT
#audio: GEMINI_VOICE, OFFLINE_VOICE, AUDIO_CAPTURE, AUDIO_PLAYER, PTT_CONTROL
#database: APP_DATABASE, CHAT_MSG_DAO, CHAT_REPO, UNIFIED_REPO
#ui-components: CHAT_BUBBLE, LIQUID_GLOBE, WAVEFORM_VIZ
#home: HOME_SCREEN, HOME_VM, CONTENT_CARD, SEARCH_BAR
#write-diary: WRITE_SCREEN, WRITE_VM, DIARY_MODEL
#navigation: NAV_GRAPH, HOME_NAV, CHAT_ROUTES, AUTH_ROUTES
#dependency-injection: DB_MODULE, CHAT_MODULE, AUDIO_MODULE
#theme: THEME, COLORS, TYPOGRAPHY
#network: CONNECTIVITY_OBS, NETWORK_OBS
#models: DIARY_MODEL, MOOD_MODEL, EMOTION_MODEL
#build: ROOT_BUILD, VERSION_CATALOG, PROJECT_CONFIG
```
