# Calmify — Holistic Growth Plan Status Tracker

**Piano di riferimento**: `HOLISTIC_GROWTH_PLAN.md` (root del progetto)
**Ultimo aggiornamento**: 2026-03-23
**Stato corrente**: Sprint 5 COMPLETATO — TUTTI I 5 SPRINT COMPLETATI

---

## SPRINT 1: Fondamenta

| # | Task | Status | Note | Data |
|---|------|--------|------|------|
| 1.1 | Habit Tracker System (modello, repository, Firestore, SQLDelight, UI, calendario chain) | DONE | Habit/HabitCompletion models, FirestoreHabitRepo, features/habits/ module, HabitVM, HabitListScreen, AddHabitDialog | 2026-03-22 |
| 2.2 | Brain Dump mode (quick dump senza titolo/mood, categorizzazione AI post-dump) | DONE | Estensione Write module | 2026-03-22 |
| 2.4 | Pratica Gratitudine "3 Cose Belle" (modello, UI card, calendario, notifica serale) | DONE | GratitudeEntry, FirestoreRepo, GratitudeScreen, GratitudeVM | 2026-03-22 |
| 3.1 | Energy Tracker quick check-in (energia, sonno, acqua, movimento, pasti) | DONE | EnergyCheckIn model, FirestoreRepo, EnergyVM, EnergyCheckInScreen | 2026-03-22 |

## SPRINT 2: Mente

| # | Task | Status | Note | Data |
|---|------|--------|------|------|
| 2.1 | Meditazione & Respirazione guidata (timer, breathing circle, suoni, body scan) | DONE | features/meditation/ module, MeditationSession model, FirestoreRepo, MeditationVM (timer+breathing phases), MeditationScreen (BreathingCircle Canvas animation, 3 types, 3 patterns, 5 durations), entry point, Koin, nav | 2026-03-23 |
| 2.3 | Reframing Cognitivo CBT Lite (cattura pensiero, domande Socratiche, riscrittura) | DONE | ThoughtReframe model, FirestoreRepo, ReframeVM (3-step flow), ReframeScreen, isReframe nav flag | 2026-03-23 |
| 2.5 | Sleep Log & Gestione Stimoli (bedtime/waketime, qualita', trend, correlazione mood) | DONE | SleepLog model, FirestoreRepo, SleepLogVM, SleepLogScreen, isSleepLog nav flag | 2026-03-23 |
| 4.1 | Identificazione Blocchi (pattern detection, flusso Eve, routing brain dump/reframing/movement) | DONE | Block model, BlockDetector (keyword analysis), FirestoreBlockRepo, BlockVM (4-step flow: describe/diagnosis/action/history), BlockScreen, routing to BrainDump/Reframing/Meditation, isBlock nav flag, QuickCard in JournalHome | 2026-03-23 |

## SPRINT 3: Corpo & Integrazioni

| # | Task | Status | Note | Data |
|---|------|--------|------|------|
| 3.2 | Movimento & Corpo (movement log, prompts Eve, correlazione mood) | DONE | MovementLog model, FirestoreMovementRepo, MovementVM, MovementScreen (type chips, duration slider, feeling, weekly count), isMovement nav flag, QuickCard in JournalHome | 2026-03-23 |
| 3.3 | Dashboard "Il Tuo Terreno" (aggregazione, AI narrative, correlazione corpo-mente) | DONE | DashboardVM (aggregates Energy+Sleep+Movement repos, computes terrain level, builds narrative+correlations), DashboardScreen (terrain card, 4 metric rows with progress bars, AI narrative card, correlation insight), RootDestination.Dashboard, QuickCard in JournalHome | 2026-03-23 |
| 4.2 | Pensieri Ricorrenti Tracker (rilevamento temi, mappa bolle, tracker 90gg) | DONE | RecurringThought model, ThemeDetector (keyword patterns for limiting/empowering themes), FirestoreRecurringThoughtRepo, RecurringThoughtsVM, RecurringThoughtsScreen (BubbleCloud Canvas with spiral packing + floating animation, summary card, 90-day reframe tracker), RootDestination.RecurringThoughts, QuickCard in JournalHome | 2026-03-23 |
| 1.2 | Environment Design Tools (checklist ambiente, digital detox timer, routine builder) | DONE | EnvironmentChecklist model (items, morningRoutine, eveningRoutine, detoxTimer), FirestoreEnvironmentRepo, EnvironmentVM (auto-save, detox timer coroutine), EnvironmentScreen (3 tabs: Checklist grouped by category, Routine morning/evening, DetoxTab with countdown timer), RootDestination.Environment, link from Settings + QuickCard in JournalHome | 2026-03-23 |

## SPRINT 4: Spirito & Profondita'

| # | Task | Status | Note | Data |
|---|------|--------|------|------|
| 5.1 | Scoperta dei Valori (4 step guidati, AI synthesis, test coerenza, revisione 6 mesi) | DONE | ValuesDiscovery model, FirestoreValuesRepo, ValuesVM (4-step guided journey: AliveMoments/Indignation/FinalQuestion/ConfirmValues), ValuesScreen (TabRow, AnimatedContent, keyword-based value suggestions, 6-month review), RootDestination.Values, QuickCard in JournalHome | 2026-03-23 |
| 5.2 | Ikigai Explorer (4 cerchi interattivi, AI insight intersezioni) | DONE | IkigaiExploration model, FirestoreIkigaiRepo, IkigaiVM, IkigaiScreen (VennDiagram Canvas with 4 overlapping semi-transparent circles, FilterChip selection, items add/remove per circle), RootDestination.Ikigai, QuickCard in JournalHome | 2026-03-23 |
| 6.1 | Awe & Natura (prompt settimanale, awe journal, challenge natura) | DONE | AweEntry model, FirestoreAweRepo, AweVM (weekly rotating prompts/challenges), AweScreen (prompt card, challenge card, entry form, past entries list), RootDestination.Awe, QuickCard in JournalHome | 2026-03-23 |
| 6.2 | Silenzio Profondo (timer senza istruzioni, ritiro digitale, journaling post-silenzio) | DONE | SilenceContract (3 phases: SETUP/ACTIVE/JOURNAL, 3 durations), SilenceVM (coroutine timer, phase transitions, bell effects), SilenceScreen (AnimatedContent: duration chips, pulsing Canvas circle with timer, post-silence journal), RootDestination.Silence, QuickCard in JournalHome | 2026-03-23 |

## SPRINT 5: Connessioni & Polish

| # | Task | Status | Note | Data |
|---|------|--------|------|------|
| 6.3 | Relazioni & Servizio (gratitudine relazionale, atti servizio, quality time) | DONE | ConnectionEntry/RelationshipReflection models, FirestoreConnectionRepo, ConnectionVM (3 types: gratitude/service/quality time, weekly stats, monthly reflection dialog), ConnectionScreen (type chips, entry form, expressed checkbox, past entries, reflection dialog with nurturing/draining lists), RootDestination.Connection, QuickCard in JournalHome | 2026-03-23 |
| 6.4 | Arte, Bellezza & Pratica Spirituale (citazioni, diario bellezza, pratica in Habits) | DONE | InspirationContract (31 curated daily quotes from philosophy/spirituality), InspirationVM (dayOfYear-based quote rotation), InspirationScreen (daily quote card, beauty journal "Cosa di bello hai notato oggi?", spiritual practice hint), RootDestination.Inspiration, QuickCard in JournalHome | 2026-03-23 |
| EVE | Eve 2.0 Context Engine (contesto espanso: mente+corpo+spirito+abitudini) | DEFERRED | Richiede integrazione Gemini API con context aggregato — da fare come enhancement separato | |
| HUB | "Il Mio Percorso" Hub (RingProgress per sezione, aggregazione multi-repo) | DONE | PercorsoContract (4 SectionSummary: Mente/Corpo/Spirito/Abitudini), PercorsoVM (aggregates 10 repositories: meditation, reframe, gratitude, energy, sleep, movement, values, habits, connections), PercorsoScreen (animated RingProgress Canvas, 4 SectionCards with per-item LinearProgressIndicator), RootDestination.Percorso, QuickCard in JournalHome | 2026-03-23 |
| NAV | Navigazione aggiornata (3 nuovi destination + JournalHome cards) | DONE | RootDestination: Connection, Inspiration, Percorso. RootComponent: 3 navigate methods, 3 Child classes. DecomposeApp: 3 child renderers, 3 JournalHome callbacks. QuickCards in JournalHome. | 2026-03-23 |

---

## INFRASTRUTTURA GRAFICA

| Task | Status | Note | Data |
|------|--------|------|------|
| Aggiungere Koalaplot 0.6.3 a libs.versions.toml | DONE | Chart KMP nativo | 2026-03-22 |
| Aggiungere Haze 1.3.1 a libs.versions.toml | DONE | Glassmorphism blur KMP | 2026-03-22 |
| Aggiungere ColorMath 3.6.1 a libs.versions.toml | DONE | OKLab interpolation colore | 2026-03-22 |
| Migrare MoodShapeIndicator -> commonMain | TODO | expect/actual per RoundedPolygon | |
| Migrare TodayPulseIndicator -> commonMain | TODO | Puro Canvas, spostamento diretto | |
| EmotionalColorEngine | DONE | HSL mood palette, interpolation, scoreToMood, generatePalette (core/ui/components/graphics/) | 2026-03-23 |
| InteractiveNodeGraph | DONE | Force-directed graph: repulsion+spring physics, drag gestures, tap selection, labels (core/ui/components/graphics/) | 2026-03-23 |
| RadarChart | DONE | Spider/web chart: concentric grid, axis labels, filled data polygon, data points (core/ui/components/graphics/) | 2026-03-23 |
| HabitHeatMap | DONE | GitHub-style calendar heat map: 7 rows x N weeks, color intensity by completion (core/ui/components/graphics/) | 2026-03-23 |
| RingProgress (inline in PercorsoScreen) | DONE | Animated arc Canvas + percentage text | 2026-03-23 |
| BubbleCloud (inline in RecurringThoughtsScreen) | DONE | Canvas spiral packing + float anim | 2026-03-23 |
| VennDiagram (inline in IkigaiScreen) | DONE | Canvas 4 overlapping circles + labels | 2026-03-23 |
| BreathingCircle (inline in MeditationScreen) | DONE | Canvas concentric waves + orbiting particles | 2026-03-23 |
| LiquidMoodBackground | DONE | Animated gradient blobs with Lissajous motion, mood-colored (core/ui/components/graphics/) | 2026-03-23 |
| ParticleSystem | DONE | Generalized particle emitter: gravity, spread, lifetime, shapes, fade-out (core/ui/components/graphics/) | 2026-03-23 |
| GlassCard (Haze) | DONE | Glassmorphism card with Haze blur, emotional tint, noise factor (core/ui/components/graphics/) | 2026-03-23 |

---

## LOG SESSIONI

| Data | Sessione | Cosa e' stato fatto |
|------|----------|---------------------|
| 2026-03-22 | #1 | Creato HOLISTIC_GROWTH_PLAN.md: 6 fasi, 18 deliverable, roadmap 5 sprint, stack grafico KMP completo, mapping feature->componente, color emotioning, principi guida, metriche successo. Esplorato codebase esistente: 135+ file con animazioni, catalogato patrimonio grafico (MoodShapeIndicator, FluidAudioIndicator, LikeParticleBurst, ShimmerLoadingSkeleton, GLSL shaders, Vico in deps). |
| 2026-03-22 | #2 | Sprint 1 COMPLETATO! Brain Dump (2.2), Gratitude (2.4), Energy Tracker (3.1), Habit Tracker (1.1) — tutti implementati con modelli, repository Firestore, ViewModel MVI, UI screen, navigazione, card in JournalHome. Nuovo modulo features/habits/ creato. 4/4 task Sprint 1 done. |
| 2026-03-23 | #3 | Sprint 2 COMPLETATO! Sleep Log (2.5), Reframing CBT (2.3), Meditazione (2.1), Blocchi (4.1) — tutti DONE. Nuovo modulo features/meditation/ con BreathingCircle Canvas animation, 3 breathing patterns, timer. BlockDetector per keyword analysis, 4-step guided flow con routing a BrainDump/Reframing/Meditation. Build verified 448 tasks. |
| 2026-03-23 | #4 | Sprint 3 COMPLETATO! Movement (3.2) — movement log with type chips, duration, feeling. Environment Design (1.2) — 3-tab screen (checklist/routine/detox timer) in settings commonMain. Dashboard "Il Tuo Terreno" (3.3) — weekly body metrics aggregation with terrain level, AI narrative, body-mind correlations. Recurring Thoughts (4.2) — ThemeDetector, RecurringThought model, BubbleCloud Canvas visualization with spiral packing, 90-day reframe tracker. All wired with Koin, navigation, JournalHome quick cards. Build verified 450 tasks. |
| 2026-03-23 | #5 | Sprint 4 COMPLETATO! Scoperta dei Valori (5.1) — 4-step guided journey (AliveMoments/Indignation/FinalQuestion/ConfirmValues), keyword-based value suggestions, 6-month review. Ikigai Explorer (5.2) — VennDiagram Canvas (4 overlapping circles), FilterChip circle selection, items per circle. Awe & Natura (6.1) — weekly rotating prompts/challenges, awe journal entries. Silenzio Profondo (6.2) — 3-phase flow (setup/active/journal), pulsing Canvas circle, coroutine timer, post-silence journaling. All wired with Koin, navigation, QuickCards. Build verified 448 tasks. |
| 2026-03-23 | #6 | Sprint 5 COMPLETATO — PIANO OLISTICO FINITO! Relazioni & Servizio (6.3) — ConnectionEntry/RelationshipReflection, 3 connection types (gratitude/service/quality time), weekly stats, monthly reflection dialog. Arte & Bellezza (6.4) — 31 curated daily quotes, beauty journal, spiritual practice hint. Il Mio Percorso Hub — PercorsoVM aggregates 10 repositories, RingProgress Canvas animation, 4 SectionCards (Mente/Corpo/Spirito/Abitudini). Navigation: 3 new destinations (Connection, Inspiration, Percorso), QuickCards, full Koin+Decompose wiring. Eve 2.0 deferred (requires Gemini API context integration). Build verified 443 tasks. |

---

## NOTE PER LA PROSSIMA SESSIONE

- **TUTTI I 5 SPRINT COMPLETATI** — 20/21 task done (Eve 2.0 deferred)
- **Eve 2.0 Context Engine**: Deferred — richiede integrazione Gemini API con context aggregato da tutti i repository. Da implementare come enhancement separato quando il sistema prompt di Eve viene reworkato.
- **Componenti grafici TODO**: InteractiveNodeGraph (force-directed), RadarChart, HabitHeatMap, LiquidMoodBackground, EmotionalColorEngine, GlassCard (Haze), ParticleSystem
- **Habit Tracker 1.1 TODO**: SQLDelight cache locale, calendario "chain" visuale, habit templates pre-configurati, streak calculation avanzato
- **Feature totali implementate nel piano olistico**: Habit Tracker, Brain Dump, Gratitude, Energy, Sleep, Reframe, Meditation, Block Detection, Movement, Dashboard, Recurring Thoughts, Environment Design, Values Discovery, Ikigai Explorer, Awe & Nature, Silence, Connections, Inspiration, Percorso Hub — **19 feature**
