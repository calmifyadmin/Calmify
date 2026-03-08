# Calmify Nexus — Social Wellness Platform: Technical Documentation

> **Data:** 2 Marzo 2026
> **Branch:** `migration/firebase-2025`
> **Stato:** BUILD SUCCESSFUL (739 tasks, 0 errors)
> **Autore implementazione:** Utente + Jarvis AI Assistant

---

## Executive Summary

Calmify si e' evoluto da **app wellness personale** a **piattaforma social per il benessere mentale** (nome in codice: **Nexus**). In una singola giornata di lavoro sono stati implementati **7 moduli social completi** con architettura MVI, 7 repository Firestore, 3 Cloud Functions con AI Gemini, regole di sicurezza, push notifications, e sistema di billing.

L'architettura e' ispirata a **Threads by Meta** ma con focus unico sul benessere mentale: mood tagging, journal sharing, crisis detection, e profili psicologici AI-powered.

---

## Architettura Generale

```
┌──────────────────────────────────────────────────────────────────┐
│                        CALMIFY NEXUS                              │
│                                                                    │
│   ┌──────────────┐  ┌───────────────┐  ┌──────────────────┐      │
│   │  WELLNESS    │  │    SOCIAL     │  │   AI-NATIVE      │      │
│   │  (Existing)  │  │   (Nexus)     │  │  (Enhanced)      │      │
│   ├──────────────┤  ├───────────────┤  ├──────────────────┤      │
│   │ Journaling   │  │ Feed/Threads  │  │ Genkit + Gemini  │      │
│   │ Mood Tracking│  │ Follow Graph  │  │ Sentiment AI     │      │
│   │ Avatar AI    │  │ DM Messaging  │  │ Crisis Detection │      │
│   │ Insights     │  │ Search        │  │ Weekly Profiles  │      │
│   │ Onboarding   │  │ Notifications │  │ CBT Patterns     │      │
│   │ Snapshots    │  │ Subscription  │  │ Push Notifs      │      │
│   └──────────────┘  └───────────────┘  └──────────────────┘      │
└──────────────────────────────────────────────────────────────────┘
```

---

## 1. Moduli Social Implementati (7 totali)

### 1.1 Feed (`features/feed/`)

**Scopo:** Feed social con due algoritmi — "Per Te" (popolarita') e "Seguiti" (cronologico).

**File chiave:**
- `FeedContract.kt` — MVI contract (Intent/State/Effect)
- `FeedViewModel.kt` — State management con pagination
- `FeedScreen.kt` — UI Compose con tabs
- `FeedKoinModule.kt` — DI Koin

**Funzionalita':**
- Tab "Per Te" (ordinato per likeCount DESC) e "Seguiti" (cronologico)
- Pull-to-refresh + infinite scroll con cursor pagination
- Like/Unlike con **optimistic UI** (aggiorna subito, rollback su errore)
- Navigazione al profilo utente o thread

**MVI Contract:**
```
Intent: LoadFeed, RefreshFeed, LoadMore, SelectTab, LikeThread, UnlikeThread
State:  threads, selectedTab, isLoading, isRefreshing, hasMore, nextCursor, error
Effect: ShowError, NavigateToThread, NavigateToUserProfile
```

---

### 1.2 Composer (`features/composer/`)

**Scopo:** Creazione e pubblicazione post social con controlli di visibilita' e mood tagging.

**File chiave:**
- `ComposerContract.kt`, `ComposerViewModel.kt`, `ComposerScreen.kt`, `ComposerKoinModule.kt`

**Funzionalita':**
- Testo libero con **character count** (max 500 caratteri)
- **Mood tag** selezionabile (8 opzioni: Happy, Calm, Grateful, Motivated, Anxious, Sad, Angry, Neutral)
- **Visibilita'**: PUBLIC / FOLLOWERS_ONLY / PRIVATE
- Opzione "Condividi dal diario" (link journal entry → post pubblico)
- Validazione form + submission ottimistica

**MVI Contract:**
```
Intent: UpdateContent, SetVisibility, SetMoodTag, SetShareFromJournal, Submit, Discard
State:  content, visibility, moodTag, isFromJournal, characterCount, maxCharacters, isSubmitting
Effect: PostCreated, Discarded, ShowError
```

---

### 1.3 Social Profile (`features/social-profile/`)

**Scopo:** Profili utente pubblici con sistema follow/unfollow/block.

**File chiave:**
- `SocialProfileContract.kt`, `SocialProfileViewModel.kt`, `SocialProfileScreen.kt`, `SocialProfileKoinModule.kt`

**Funzionalita':**
- Visualizzazione profilo (displayName, avatar, bio, verified badge)
- Contatori: follower, following, thread count
- **Follow/Unfollow** con batch write atomico
- **Block** (rimuove anche follow reciproci)
- Rilevamento "proprio profilo" vs profilo altrui
- Storico thread dell'utente

**Data Model:**
```kotlin
data class SocialUser(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val bio: String?,
    val isVerified: Boolean,
    val followerCount: Long,
    val followingCount: Long,
    val threadCount: Long
)
```

---

### 1.4 Search (`features/search/`)

**Scopo:** Ricerca full-text su thread e utenti con filtri.

**File chiave:**
- `SearchContract.kt`, `SearchViewModel.kt`, `SearchScreen.kt`, `SearchKoinModule.kt`

**Funzionalita':**
- Ricerca parallela (thread + utenti simultanei)
- **Thread search**: Fetch 200 thread pubblici recenti, filtro client-side su testo + mood tag
- **User search**: Prefix matching su displayName (`>= query` e `<= query\uf8ff`)
- Filtri: ALL / THREADS / USERS
- Predisposto per **Vertex AI Vector Search** (semantic search — attualmente keyword fallback)

---

### 1.5 Notifications (`features/notifications/`)

**Scopo:** Notifiche social in real-time (like, follower, reply, mention, wellness reminder).

**File chiave:**
- `NotificationsContract.kt`, `NotificationsViewModel.kt`, `NotificationsScreen.kt`, `NotificationsKoinModule.kt`

**Funzionalita':**
- Stream real-time via Firestore snapshot listener
- **Unread count reattivo** (Flow che si aggiorna automaticamente)
- Mark singola notifica come letta (optimistic)
- **Mark all read** (batch operation, max 500 per batch)
- Tipi: NEW_FOLLOWER, LIKE, REPLY, MENTION, WELLNESS_REMINDER, OTHER

**Data Model:**
```kotlin
data class Notification(
    val id: String,
    val userId: String,        // Destinatario
    val type: NotificationType,
    val actorId: String,       // Chi ha triggerato
    val actorName: String?,
    val actorAvatarUrl: String?,
    val threadId: String?,     // Post collegato
    val message: String,
    val isRead: Boolean,
    val createdAt: Long
)
```

---

### 1.6 Messaging (`features/messaging/`)

**Scopo:** Direct message 1-on-1 con real-time updates e typing indicators.

**File chiave:**
- `MessagingContract.kt`, `MessagingViewModel.kt`
- `MessagingScreen.kt` (container)
- `ConversationListScreen.kt` (lista conversazioni)
- `ChatRoomScreen.kt` (singola chat)
- `MessagingKoinModule.kt`

**Funzionalita':**
- Lista conversazioni con ultimo messaggio e timestamp
- Chat room con messaggi ordinati cronologicamente
- **Typing indicators** real-time (auto-timeout 3 secondi)
- **Deduplicazione conversazioni**: participant IDs ordinati → unicita' 1-on-1
- Creazione nuova conversazione
- Mark read automatico all'apertura
- Draft text in-memory
- Job management separati per messages/typing/conversations

**Data Models:**
```kotlin
data class Conversation(
    val id: String,
    val participantIds: List<String>,  // Ordinati per deduplicazione
    val lastMessage: String?,
    val lastMessageAt: Long,
    val unreadCount: Int
)

data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAt: Long,
    val isRead: Boolean
)
```

---

### 1.7 Subscription (`features/subscription/`)

**Scopo:** Gestione abbonamenti premium via Google Play Billing con persistenza Firestore.

**File chiave:**
- `SubscriptionContract.kt`, `SubscriptionViewModel.kt`, `PaywallScreen.kt`, `SubscriptionKoinModule.kt`

**Funzionalita':**
- Tier: FREE / PREMIUM / PRO
- Query prodotti da Google Play Console
- **Purchase flow**: `BillingClient.launchBillingFlow()` → `acknowledgePurchase()`
- **Restore purchases** con auto-acknowledge
- Verifica scadenza con auto-downgrade
- Listener real-time su cambio stato subscription
- Product IDs: `calmify_premium`, `calmify_pro`

---

## 2. Repository Layer

Tutti i repository social seguono il pattern **interface in `core/util/` → implementazione Firestore in `data/mongo/`**.

### Repository Interfaces (`core/util/src/commonMain/kotlin/com/lifo/util/repository/`)

| Repository | Metodi Principali |
|-----------|-------------------|
| **FeedRepository** | `getForYouFeed()`, `getFollowingFeed()`, `refreshFeed()` |
| **ThreadRepository** | `createThread()`, `deleteThread()`, `likeThread()`, `unlikeThread()`, `getThreadsByAuthor()`, `getReplies()` |
| **SocialGraphRepository** | `follow()`, `unfollow()`, `block()`, `unblock()`, `isFollowing()`, `getFollowers()`, `getFollowing()`, `getSuggestions()` |
| **SearchRepository** | `searchThreads()`, `searchUsers()`, `semanticSearch()` |
| **NotificationRepository** | `getNotifications()`, `getUnreadCount()`, `markAsRead()`, `markAllAsRead()` |
| **SocialMessagingRepository** | `getConversations()`, `getMessages()`, `sendMessage()`, `createConversation()`, `getTypingStatus()`, `setTyping()` |
| **SubscriptionRepository** | `getSubscriptionState()`, `getAvailableProducts()`, `acknowledgePurchase()`, `restorePurchases()`, `observeSubscription()` |

### Implementazioni Firestore (`data/mongo/`)

| Implementazione | Tecniche Chiave |
|----------------|-----------------|
| **FirestoreFeedRepository** | Cursor pagination via timestamp, `whereIn` chunking (limit 30), snapshot listeners |
| **FirestoreSocialGraphRepository** | Batch writes atomici, `FieldValue.increment()` per contatori, `resolveUserProfiles()` con chunking |
| **FirestoreSearchRepository** | Client-side filtering (MVP), prefix matching per utenti |
| **FirestoreNotificationRepository** | Real-time snapshot listener, batch mark-all-read (500/batch) |
| **FirestoreSocialMessagingRepository** | Sorted participant IDs per dedup, typing auto-cleanup su send |
| **PlayBillingSubscriptionRepository** | BillingClient suspend wrappers, Firestore state persistence |

---

## 3. Firebase Backend

### 3.1 Progetto Firebase

| Proprieta' | Valore |
|-----------|--------|
| **Project ID** | `calmify-388723` |
| **Firestore Database** | `calmify-native` (Native Mode) |
| **Region** | `eur3` (Europe) |
| **Cloud Functions Region** | `europe-west1` |
| **Runtime** | Node.js 22 |

### 3.2 Cloud Functions (3 totali)

#### `onDiaryCreated` — AI Insight Generation

| Proprieta' | Valore |
|-----------|--------|
| **Trigger** | Firestore `diaries/{diaryId}` onCreate |
| **AI Model** | Gemini 2.0 Flash Exp (via Genkit) |
| **Timeout** | 60 secondi |
| **Memory** | 512 MB |
| **Max Instances** | 10 |

**Cosa fa:**
1. Intercetta creazione diario in Firestore
2. Invia testo al modello Gemini 2.0 Flash via **Google Genkit**
3. Analisi CBT-informed: sentiment (polarity -1/+1, magnitude 0-10), topic extraction, cognitive pattern detection (9 tipi)
4. Salva risultato in collection `diary_insights`
5. **Crisis Detection**: Se polarity < -0.7 AND magnitude > 7 → invia push notification FCM di supporto
6. Notifica "Insight pronto" se confidence > 0.5

**Cognitive Patterns rilevati:**
- CATASTROPHIZING, ALL_OR_NOTHING, OVERGENERALIZATION
- MIND_READING, FORTUNE_TELLING, EMOTIONAL_REASONING
- SHOULD_STATEMENTS, LABELING, PERSONALIZATION, NONE

---

#### `computeWeeklyProfiles` — Profili Psicologici Settimanali

| Proprieta' | Valore |
|-----------|--------|
| **Trigger** | HTTP (Cloud Scheduler, domenica 2:00 AM) |
| **Timeout** | 540 secondi (9 min) |
| **Memory** | 1 GB |

**Cosa fa:**
1. Identifica utenti attivi (≥1 diario negli ultimi 14 giorni)
2. Calcola profilo psicologico settimanale:
   - **Stress Dynamics**: baseline, volatilita', picchi con trigger
   - **Mood Dynamics**: baseline, volatilita', trend (IMPROVING/STABLE/DECLINING)
   - **Resilience Index**: 0-1, basato su velocita' di recupero
   - **Clarity & Coherence**: trend di chiarezza narrativa
   - **Social & Purpose**: trend supporto sociale e senso di scopo
3. Salva in `psychological_profiles` con ID: `{userId}_week_{weekNumber}_{year}`

**Algoritmi statistici:**
- `calculateWeightedAverage()` — Exponential decay weighting
- `calculateStdDev()` — Metrica volatilita'
- `detectPeaks()` — Stress peak detection
- `calculateResilience()` — Recovery time index
- `detectTrend()` — Linear regression slope
- `calculateConfidence()` — Data density scoring (70% diary, 30% snapshot)

---

#### `sendWeeklyReminders` — Push Notification Settimanali

| Proprieta' | Valore |
|-----------|--------|
| **Trigger** | HTTP (Cloud Scheduler, domenica 21:00) |
| **Timeout** | 300 secondi |
| **Memory** | 512 MB |

**Cosa fa:**
1. Identifica utenti attivi (≥1 diario negli ultimi 30 giorni)
2. Verifica se hanno completato il wellbeing snapshot settimanale
3. Se NO → invia reminder FCM: "Il tuo check-in settimanale ti aspetta!"
4. Se SI → skip (non disturbare)

---

### 3.3 FCM Push Notifications

**Canali:**
- `calmify_reminders` — Reminder settimanali
- Crisis counseling — Notifiche supporto urgente
- Insight ready — Nuovi insight disponibili

**Deep linking actions:**
- `OPEN_HOME_SCREEN`
- `OPEN_INSIGHTS_SCREEN`
- `OPEN_SNAPSHOT_SCREEN`

---

### 3.4 Dipendenze Backend (package.json)

| Pacchetto | Versione | Scopo |
|-----------|----------|-------|
| `firebase-admin` | 12.7.0 | Firebase Admin SDK |
| `firebase-functions` | 6.0.1 | Cloud Functions runtime |
| `genkit` | 1.21.0 | Google Genkit AI framework |
| `@genkit-ai/googleai` | 1.21.0 | Integrazione Gemini |
| `@google-cloud/firestore` | 7.11.6 | Firestore client |
| `zod` | 3.25.76 | Schema validation |
| TypeScript | 5.7.3 | Build language |

---

## 4. Firestore Collections Map

### Wellness (originali)

| Collection | Documento | Regole | Scopo |
|-----------|-----------|--------|-------|
| `diaries` | diaryId | Owner read/write | Diari utente |
| `wellbeing_snapshots` | snapshotId | Owner read/write | Check-in settimanali |
| `diary_insights` | insightId | Owner read, server write | Insight AI generati |
| `psychological_profiles` | profileId | Owner read-only | Profili settimanali |
| `users` | userId | Self read/write | FCM tokens, settings |
| `profile_settings` | userId | Self read/write | Dati onboarding |

### Social (nuovi)

| Collection | Sub-collection | Regole | Scopo |
|-----------|---------------|--------|-------|
| `threads` | `likes/` | Auth read, self like/unlike | Post/thread social |
| `user_profiles` | — | Auth read, self write | Profili pubblici |
| `social_graph/{userId}` | `following/` | Self manage | Lista seguiti |
| `social_graph/{userId}` | `followers/` | Self manage | Lista follower |
| `social_graph/{userId}` | `blocked/` | Self manage | Utenti bloccati |
| `conversations` | `messages/` | Solo partecipanti | Direct messages |
| `conversations` | `typing/` | Self manage | Typing indicators |
| `notifications` | — | Target user only | Notifiche social |
| `subscriptions` | — | Self manage | Stato abbonamento |

---

## 5. Security Rules

### Firestore Rules (`firestore.rules`)

```
- diaries: solo owner (ownerId == request.auth.uid)
- threads: read autenticato, write solo autore
- threads/likes: read autenticato, write solo il liker
- user_profiles: read autenticato, write solo il proprietario
- social_graph: subcollections gestite solo dal proprietario del nodo
- conversations: solo partecipanti (array-contains request.auth.uid)
- conversations/messages: solo partecipanti della conversazione parent
- notifications: solo destinatario (userId == request.auth.uid)
- subscriptions: solo proprietario
```

### Storage Rules (`storage.rules`)

| Path | Regole | Limite |
|------|--------|--------|
| `social-media/{userId}/**` | Auth required, owner write, tutti auth read | 10 MB |
| `images/{userId}/**` | Auth required, owner only, solo immagini | 5 MB |

---

## 6. Navigazione

### Destinazioni Decompose (`RootDestination.kt`)

```kotlin
// Social destinations
@Serializable data object Feed : RootDestination
@Serializable data class UserProfile(val userId: String) : RootDestination
@Serializable data object Composer : RootDestination
@Serializable data object Search : RootDestination
@Serializable data object Notifications : RootDestination
@Serializable data class Messaging(val conversationId: String? = null) : RootDestination
@Serializable data object Subscription : RootDestination
```

---

## 7. Pattern Architetturali

### MVI Rigoroso
Tutti i 7 moduli social usano `MviViewModel<Intent, State, Effect>`:
- **Intent**: azioni utente (click, input, scroll)
- **State**: singola source of truth immutabile
- **Effect**: eventi one-shot (navigazione, toast, errori)

### Optimistic UI
Feed likes, notification reads, message sends — tutti aggiornano lo stato UI immediatamente, con rollback automatico su errore Firestore.

### Cursor Pagination
Feed usa cursor basato su timestamp (non offset) — scala meglio con inserimenti frequenti.

### Batch Operations
Follow/unfollow e mark-all-read usano `WriteBatch` Firestore per consistenza atomica e aggiornamento contatori via `FieldValue.increment()`.

### Real-time First
Notifiche, typing indicators, unread counts — tutti basati su Firestore snapshot listeners con Flow reactive.

---

## 8. Koin DI Modules

Ogni modulo sociale registra il proprio Koin module:

```kotlin
val feedKoinModule = module {
    viewModel { FeedViewModel(get(), get(), get()) }
}
val composerKoinModule = module {
    viewModel { ComposerViewModel(get(), get()) }
}
val socialProfileKoinModule = module {
    viewModel { SocialProfileViewModel(get(), get(), get()) }
}
val searchKoinModule = module {
    viewModel { SearchViewModel(get(), get(), get()) }
}
val notificationsKoinModule = module {
    viewModel { NotificationsViewModel(get(), get()) }
}
val messagingKoinModule = module {
    viewModel { MessagingViewModel(get(), get()) }
}
val subscriptionKoinModule = module {
    viewModel { SubscriptionViewModel(get()) }
}
```

Tutti registrati nel root Koin setup dell'app module.

---

## 9. Google Cloud Console — Servizi Deployati

| Servizio | Stato | Dettagli |
|---------|-------|----------|
| **Firebase Auth** | Attivo | Google Sign-In + Email/Password |
| **Cloud Firestore** | Attivo | Native Mode, database `calmify-native`, region `eur3` |
| **Cloud Functions** | 3 deployate | `onDiaryCreated`, `computeWeeklyProfiles`, `sendWeeklyReminders` |
| **Cloud Storage** | Attivo | Bucket per immagini diario e media social |
| **Firebase Cloud Messaging** | Attivo | Push notifications (crisis, insights, reminders) |
| **Cloud Scheduler** | 2 job | Weekly profiles (dom 2AM), reminders (dom 9PM) |
| **Gemini API** | Attivo | Via Genkit, modello `gemini-2.0-flash-exp` |
| **Firebase Remote Config** | Configurato | Template in `remoteconfig.template.json` |
| **Firebase Emulators** | Configurati | Auth:9099, Functions:5001, Firestore:8080 |

---

## 10. Roadmap Produzione

### MVP Attuale (implementato)
- Feed con ranking semplice (likeCount)
- Search keyword-only client-side
- Messaging 1-on-1
- Subscription via Play Billing

### Evoluzioni Pianificate
1. **Vertex AI Vector Search** — Ricerca semantica su thread
2. **Spanner Graph** — Social graph queries ottimizzate
3. **Feed Fan-out** — Cloud Function per rebuild feed_cache on follow
4. **Algolia/Typesense** — Full-text search server-side
5. **Firebase RTDB** — Lower latency per messaging
6. **Cloud Storage + Transcoder API** — Media pipeline per thread con immagini/video
7. **Content Moderation** — Text toxicity + image moderation
8. **ActivityPub Federation** — Interop con Mastodon/PixelFed

---

## 11. Riepilogo Numerico

| Metrica | Valore |
|---------|--------|
| Moduli social | 7 (feed, composer, social-profile, search, notifications, messaging, subscription) |
| Repository interfaces | 7 |
| Repository Firestore impl | 7 |
| ViewModels MVI | 7 |
| Firestore collections | 17 (6 wellness + 11 social) |
| Cloud Functions | 3 |
| Push notification types | 5 (crisis, insight, reminder, follower, like) |
| Navigazione routes social | 7 |
| Koin modules social | 7 |
| Mood tags | 8 |
| Cognitive patterns CBT | 9 |
| Build tasks | 739 (0 errors) |
