# Calmify Improvement Plan — Progress Tracker

**Piano di riferimento**: `FEATURE_IMPROVEMENT_ANALYSIS.md` (root del progetto)
**Ultimo aggiornamento**: 2026-03-05
**Fase corrente**: FASE 4 — COMPLETATA. Cleanup tecnico parziale completato. PIANO COMPLETATO.

---

## FASE 1: "L'Anima" — Dare carattere all'app

| # | Task | Status | Note | Data |
|---|------|--------|------|------|
| 1.1 | Ristrutturare bottom nav: [Home] [Journal] [AI Chat] [Il Mio Percorso] | DONE | Bottom nav 4 tab, JournalHome screen, drawer con Community/Storico/Avatar | 2026-03-05 |
| 1.2 | Social come layer: rimuovere Feed tab, spostare in drawer/Home | DONE | Feed rimosso da bottom nav, accessibile da drawer "Community". History e Avatar nel drawer. | 2026-03-05 |
| 1.3 | Notifiche nella top bar globale (badge + icona) | DONE | Badge su Home + JournalHome top bar via NotificationRepository.getUnreadCount(). Hamburger menu per drawer. | 2026-03-05 |
| 1.4 | Ridisegnare Welcome Screen (brand, non logo Google) | DONE | Logo Calmify + tagline "Conosci te stesso, un giorno alla volta" + subtitle. Google button in basso. | 2026-03-05 |
| 1.5 | Semplificare Onboarding (da 5 a 3 step conversazionali) | DONE | 3 step: Nome, Motivazioni (chip multi-select), Preferenza scrivi/parla/entrambi. Rimossi 5 vecchi step. Tutto italiano. | 2026-03-05 |
| 1.6 | Context Engine: iniettare diari nel system prompt Gemini | DONE | Migliorato prompt text chat: tutti 5 diari con mood+titolo+150char, nome utente, stile scrittura. Live chat gia' aveva contesto. | 2026-03-05 |
| 1.7 | Personalita' AI (nome, tono, memoria conversazionale) | DONE | Text chat AI = "Eve". Personalita' calda/diretta, anti-positivita'-tossica. Collega diari alla conversazione. Live chat resta "Karen" (avatar). | 2026-03-05 |
| 1.8 | Ridisegnare Home: Today's Reflection AI + "Dalla community" | DONE | Rimossi DailyInsightsChart, MoodDistribution, CognitivePatterns, TopicsCloud. Aggiunta TodayReflectionCard narrativa. Empty state in italiano. Community preview in Task 3.2. | 2026-03-05 |

## FASE 2: "Il Valore" — Rendere ogni feature utile

| # | Task | Status | Note | Data |
|---|------|--------|------|------|
| 2.1 | Smart Capture: AI inferisce metriche dal testo del diario | DONE | TextAnalyzer locale (keyword IT), SmartCaptureCard in WriteContent, rimosso wizard obbligatorio. Build OK. | 2026-03-05 |
| 2.2 | Prompt giornaliero intelligente nel journaling | DONE | getContextualPrompt(): mood follow-up, streak, gap >3gg, time-of-day, weekend. Build OK. | 2026-03-05 |
| 2.3 | Weekly Reflection AI nel profilo (sostituisce grafico pillole) | DONE | WeeklyReflectionCard narrativa + JourneyLineCard sparkline. Vecchio pill chart rinominato _Legacy. Build OK. | 2026-03-05 |
| 2.4 | Rimuovere dead code profilo (ExpressiveLineChart, Vico dep) | DONE | Rimossi ~888 righe (VerticalPills, ExpressiveLineChart, WeeklyChart*, LegendItem, StatCard). Vico dep eliminata. Build OK 816 tasks. | 2026-03-05 |
| 2.5 | Integrare avatar nella chat voice | DONE | HumanoidController creato in DecomposeApp e collegato a LiveScreen via speechAnimationTarget. LaunchedEffect chiama attachHumanoidController. Build OK. | 2026-03-05 |
| 2.6 | Rimuovere UI debug dall'avatar | DONE | Rimossi ~550 righe: EmotionButton, AnimationButton, DebugPanel, StatusChip, ControlPanel, lip-sync test. HumanoidScreen ora pulito. Build OK. | 2026-03-05 |
| 2.7 | Timeline unificata nella History | DONE | HistoryContent riscritto: stream cronologico unico diari+chat, date header (Oggi/Ieri/data), testo italiano. Build OK. | 2026-03-05 |
| 2.8 | CTA "Condividi" post-salvataggio diario | DONE | Dialog post-save con emoji mood + excerpt. Composer accetta prefilledContent via chain completa (Destination→Component→Koin→VM). Build OK 809 tasks. | 2026-03-05 |
| 2.9 | Implementare billing reale (Google Play Billing 7.x) | DONE | launchPurchaseFlow + PurchasesUpdatedListener → SharedFlow → VM auto-acknowledge → Firestore. ProductDetails cached. Activity bridge via Effect. Build OK 809 tasks. | 2026-03-05 |
| 2.10 | Semplificare tier: Free + Pro (eliminare Premium) | DONE | Rimosso PREMIUM da enum (backward-compat PREMIUM→PRO). PaywallScreen: 2 colonne Free/Pro, 1 card, testo IT. Solo calmify_pro come SKU. Build OK 809 tasks. | 2026-03-05 |

## FASE 3: "La Community" — Social come moltiplicatore

| # | Task | Status | Note | Data |
|---|------|--------|------|------|
| 3.1 | Feed come "circolo di crescita" (contenuti guidati) | DONE | Tabs ALL/Scoperte/Sfide/Domande con filtering client-side. PostCategory enum (SCOPERTA/SFIDA/DOMANDA). Composer: category selector + mood obbligatorio. Feed title "Community". Testo IT. Build OK 809 tasks. | 2026-03-05 |
| 3.2 | Sezione "Dalla community" nella Home (2-3 thread curati) | DONE | CommunityPreviewSection in HomeContent: 3 thread curati via FeedRepository+ThreadHydrator. Card con header "Dalla community", preview author+mood+text+engagement, "Vedi tutto" naviga al feed. Build OK 809 tasks. | 2026-03-05 |
| 3.3 | Implementare repost | DONE | repostThread/unrepostThread in ThreadRepository + Firestore (subcollection reposts + atomic counter). FeedVM toggle con optimistic update. ThreadHydrator arricchisce isRepostedByCurrentUser. postCategory in snapshotToThread/threadToMap. Build OK 809 tasks. | 2026-03-05 |
| 3.4 | Implementare ThreadOptions (Save, Hide, Mute, Block, Report) | DONE | Intents Save/Hide/Mute/Block/Report in FeedContract. Hide/Mute client-side (hiddenThreadIds). Block via SocialGraphRepository. Sheet labels IT. Toast feedback. Build OK 809 tasks. | 2026-03-05 |
| 3.5 | Follow/follower list navigation | DONE | FollowListScreen con tab Follower/Seguiti. Decompose destination. Build cache fix con --no-build-cache. Build OK 816 tasks. | 2026-03-05 |
| 3.6 | Avatar upload reale nell'edit profile | DONE | MediaUploadRepository per upload. Image picker con ActivityResultContracts.GetContent. Scale 512x512 JPEG 85%. Build OK 809 tasks. | 2026-03-05 |
| 3.7 | Notifiche: click naviga + raggruppamento | DONE | GroupedNotification con displayMessage. Raggruppa LIKE/NEW_FOLLOWER per tipo+threadId entro 24h. Testo IT. Build OK 809 tasks. | 2026-03-05 |
| 3.8 | Search: filtri per mood + ricerca globale | DONE | SelectMoodFilter intent. MoodFilterChips dinamiche. filteredThreadResults computed property. Build OK 809 tasks. | 2026-03-05 |
| 3.9 | User picker per nuove conversazioni messaging | DONE | UserPickerScreen con search bar + debounce 300ms. 3 stati in MessagingScreen. SearchRepository dep. Build OK 809 tasks. | 2026-03-05 |
| 3.10 | Caching thread locale con SQLDelight | DONE | CachedThread.sq con 5 query. Cache-first loading in FirestoreFeedRepository. Koin cachedThreadQueries. Schema migration. Build OK 809 tasks. | 2026-03-05 |

## FASE 4: "Il Business" — Monetizzazione e compliance

| # | Task | Status | Note | Data |
|---|------|--------|------|------|
| 4.1 | Paywall contestuale (nel momento del bisogno) | DONE | InlinePaywallCard in core/ui. Chat free limit (5 msg) con check SubscriptionTier. NavigateToPaywall effect. Build OK 809 tasks. | 2026-03-05 |
| 4.2 | Export dati utente (GDPR compliance) | DONE | ExportUserData intent in SettingsVM. StringBuilder JSON (profilo+diari+chat). FileProvider + Intent.ACTION_SEND per share. Build OK 809 tasks. | 2026-03-05 |
| 4.3 | Firebase Analytics / Mixpanel integration | DONE | AnalyticsTracker interface in core/util. FirebaseAnalyticsTracker in data/mongo. AnalyticsEvents object con standard events. Koin registered. Build OK 816 tasks. | 2026-03-05 |
| 4.4 | AI Preferences nelle settings | DONE | AiTone/ReminderFrequency enum + TopicsToAvoid in ProfileSettings. AiPreferencesScreen con dropdown tono/frequenza + filter chips argomenti. Decompose navigation completa. Build OK 809 tasks. | 2026-03-05 |
| 4.5 | Completare sub-schermate settings | DONE | Tutte 5 sub-screens gia' funzionali: PersonalInfo, HealthInfo, Lifestyle, Goals, AiPreferences. Form completi con validazione e save. | 2026-03-05 |

---

## CLEANUP TECNICO (parallelizzabile)

| Task | Status | File |
|------|--------|------|
| Rimuovere 30+ println() da HomeViewModel | DONE | Rimossi 37 println debug. Build OK. |
| Centralizzare Task<T>.await() in core/util | DONE | Rimossa custom extension, aggiunto kotlinx-coroutines-play-services dep. Usa standard await(). |
| Rimuovere codice unifiedContent inutilizzato dalla Home | DONE | Eliminati UnifiedContentCard.kt + FilterChipRow.kt + import unused. VM state fields rimasti (safe). Build OK. |
| Rinominare stringhe cliniche in Insight | DONE | 9 label: Analisi Sentimento→Come ti sentivi, Pattern Cognitivi→Schemi di pensiero, Riepilogo AI→Cosa ho notato, etc. |
| Fix stringhe miste italiano/inglese in Search | DONE | contentDescription: Back→Indietro, Clear→Cancella, Verified→Verificato. |
| Rimuovere placeholder "15k / Obiettivo" dal profilo | DONE | Gia' rimosso in sessione precedente. |
| Collegare UseCase al VM in Auth | DONE | VM usa AuthProvider+SignInWithGoogleUseCase+SignOutUseCase. Rimosso @Inject. Koin: factory+viewModel(get(),get(),get()). |

---

## LOG SESSIONI

| Data | Sessione | Cosa e' stato fatto | Task completati |
|------|----------|---------------------|-----------------|
| 2026-03-05 | #1 | Analisi strategica completa di tutti i 15 moduli. Creato FEATURE_IMPROVEMENT_ANALYSIS.md. Deciso target user (self-improver 25-40), product thesis, core loop, navigazione (Opzione A: social come layer). | Analisi e pianificazione |
| 2026-03-05 | #2 | Task 1.1 + 1.2: Ristrutturata bottom nav [Home][Journal][AI Chat][Percorso]. Creato JournalHomeScreen (prompt giornaliero + lista diari + FAB). Feed/History/Avatar spostati nel drawer. FAB rimossi da Home. Build OK 809 tasks. | 1.1, 1.2 |
| 2026-03-05 | #2 | Task 1.3: Notifiche nella top bar globale. Badge con unread count su Home e JournalHome top bar. Inject diretto NotificationRepository per conteggio reattivo. Hamburger menu aggiunto a JournalHome per drawer. Home title rinominata "Calmify". Build OK. | 1.3 |
| 2026-03-05 | #3 | FASE 1 COMPLETATA. Task 1.4: Welcome screen con logo Calmify + tagline italiana. 1.5: Onboarding da 5 a 3 step conversazionali (Nome/Motivazioni/Preferenza). 1.6: Context engine migliorato con tutti 5 diari + nome utente. 1.7: AI "Eve" con personalita' definita. 1.8: Home redesign — rimossi donut/pills/wordcloud, aggiunta TodayReflectionCard narrativa. Build OK 809 tasks. | 1.4, 1.5, 1.6, 1.7, 1.8 |
| 2026-03-05 | #4 | FASE 2 avviata. Task 2.1: Smart Capture — creato TextAnalyzer.kt (keyword-based IT), SmartCaptureCard sostituisce PsychologicalMetricsWizard in WriteContent, RunSmartCapture Intent nel VM. Build OK 809 tasks. | 2.1 |
| 2026-03-05 | #5 | FASE 2 COMPLETATA. 2.2: Prompt contestuale diario (mood/streak/gap/ora). 2.3: WeeklyReflectionCard narrativa + JourneyLineCard sparkline. 2.4: Rimossi ~888 righe dead code profilo + Vico. 2.5: Avatar lip-sync collegato a LiveChat. 2.6: Debug UI avatar rimossa (~550 righe). 2.7: Timeline unificata History. 2.8: CTA Condividi post-save con prefilledContent. 2.9: Google Play Billing reale (launchBillingFlow + PurchasesUpdatedListener + auto-acknowledge). 2.10: Tier semplificato Free+Pro (PREMIUM rimosso). Build OK 809 tasks. | 2.2-2.10 |
| 2026-03-05 | #6 | FASE 3 COMPLETATA. 3.1-3.10: Feed come circolo di crescita, Community nella Home, Repost, ThreadOptions, FollowList, Avatar upload, Notifiche raggruppate, Search mood filter, UserPicker messaging, SQLDelight thread cache. Build OK 809 tasks. | 3.1-3.10 |
| 2026-03-05 | #7 | FASE 4 COMPLETATA + CLEANUP. 4.1: Paywall contestuale (InlinePaywallCard + free limit chat). 4.2: GDPR export (JSON share via FileProvider). 4.3: Firebase Analytics (AnalyticsTracker interface + impl). 4.4: AI Preferences (AiPreferencesScreen + tono/frequenza/argomenti). 4.5: Settings sub-screens gia' complete. Cleanup: rimossi 37 println, fixed IT strings Search, rimossi UnifiedContentCard + FilterChipRow dead code. Build OK 809 tasks. | 4.1-4.5, cleanup x4 |

---

## NOTE PER LA PROSSIMA SESSIONE

- **TUTTE LE 4 FASI COMPLETATE** — 30 task done (1.1-1.8, 2.1-2.10, 3.1-3.10, 4.1-4.5) + 4 cleanup done.
- **Cleanup residuo**: Centralizzare Task<T>.await(), rinominare stringhe cliniche Insight, collegare UseCase in Auth.
- La roadmap completa e' in `FEATURE_IMPROVEMENT_ANALYSIS.md`
- SubscriptionTier: FREE e PRO (PREMIUM rimosso, backward-compat in Firestore reads)
- Analytics: AnalyticsTracker in core/util, FirebaseAnalyticsTracker in data/mongo. Da integrare nei VM per tracking eventi.
- AI Preferences: aiTone, reminderFrequency, topicsToAvoid in ProfileSettings. Da iniettare nel system prompt Gemini.
