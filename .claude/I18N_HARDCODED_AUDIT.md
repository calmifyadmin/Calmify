# i18n Hardcoded Strings Audit — Screenshot-Driven (2026-04-28)

> Working doc. Source: 25 screenshots in `C:\Users\lifoe\Downloads\drive-download-20260428T141937Z-3-001`.
> Device locale: English. Anything visible in Italian = hardcoded.
> Method: ONE screenshot at a time, log findings, then next.
>
> **Coverage gaps acknowledged by user (need follow-up audit pass after this batch):**
> - Garden activity descriptions — not all screenshotted, ALL still need migration
> - Snapshot wizard — not all steps screenshotted
> - Diary creation flow — not all steps screenshotted

## Method

For each screenshot:
1. Identify visible Italian (= hardcoded) text
2. Note coordinates / context to help locate
3. After all 25 read: grep codebase for each → file:line
4. Group by source file → atomic migration commits
5. Translate to all 12 locales when migrating

## Migration Batches (Phase J) — **Tier 1+2 DONE 2026-04-28**

| Batch | Source files | Strings | Status |
|---|---|---|---|
| J.1 — Coach marks Home | `ScreenTutorials.kt` + `HomeContent.kt:83` | 10 (4 titles + 4 descs + 2 buttons) | **DONE** — keys added EN+IT+ES+FR+DE+PT, Strings.Coach group added, dead percorso/chat/write deleted, build green |
| J.2 — Mood enum chips | `TextAnalyzer.kt` + `WriteContent.kt` + `ComposerContract.kt` + `ComposerScreen.kt::moodTagDisplay()` | 20 mood + 8 MOOD_TAGS canonical (storage IT, display localized) | **DONE Tier 1** |
| J.3 — Dashboard hero greeting | `DashboardHeader.kt` + `ExpressiveHero.kt` | 16 (greeting + 4 ToD + 3 trend + 6 score + 2 wellbeing) | **DONE Tier 1** |
| J.4 — Drawer items + FAB + dialogs | `DecomposeApp.kt` (incl. `FabConfig.useLogo` field) | 6 drawer + 3 FAB + 4 dialog | **DONE Tier 1** |
| J.5 — Snapshot questionnaire | `SnapshotScreen.kt` + `Strings.Screen.Snapshot` extended | 10 questions × 3 fields + 4 sections = 34 | **DONE Tier 2.B** |
| J.6 — Garden activities + private GardenCategory | `ActivityGardenScreen.kt` (data class + enum + 19 entries) | 19 × 2 + 6 cat + 1 a11y = 40 | **DONE Tier 2.A** |
| J.7 — Percorso title (PercorsoScreen + ProfileDashboard) | 2 callers | 1 (`Strings.Screen.Percorso.title`) | **DONE Tier 1** |
| J.8 — Garden taxonomy enums | `GardenContract.kt` (Category/Difficulty/IkigaiCircle) + `IkigaiContract.Circle` | 12 taxonomy keys | **DONE Tier 1** |
| J.9 — Info tooltips bottom sheets | `InfoTooltip.kt` + `TooltipContent` (10 wellness concepts) + `PsychologicalMetricsSheet::MetricSlider` signature update | 10 × 2 = 20 | **DONE Tier 2.C** (dual overload kept for legacy ad-hoc callers) |
| J.10 — Composer chip labels | `ComposerContract.kt::PostCategory` + `ComposerScreen.kt` | 3 PostCategory + 8 MOOD_TAGS dedup (covered by J.2) | **DONE Tier 1** |
| J.11 — Thread Detail composer placeholder | `ThreadDetailScreen.kt` | 2 (replyTo, replyPlaceholder) | **DONE Tier 1** |
| J.12 — Thread options sheet | `ThreadOptionsSheet.kt` | 5 (Salva/Nascondi/Silenzia/Blocca/Segnala) + report sent toast | **DONE Tier 1** |

**Cumulative Tier 1 + 2: ~189 keys × 6 langs (EN+IT+ES+FR+DE+PT) ≈ 1134 translations, 28 Kotlin files refactored, BUILD GREEN (assembleDebug).**

## Tier 3 — DEFERRED (architectural refactor, sessione futura)

These all share the same anti-pattern: **non-`@Composable` utility functions** returning user-facing Italian strings. Localization requires moving call sites to `@Composable` scope OR refactoring to take pre-resolved Strings. Risk of breaking flows is higher; needs dedicated session.

| Target | Strings approx | Refactor type |
|---|---|---|
| `DateFormatters.kt::getTimeOfDayGreeting/formatSectionHeader/getWeekLabel/getDayLabel/getFullDayName` | ~25 weekday + greeting + period labels | Move to caller `@Composable` site |
| `JournalHomeScreen.kt::getDailyPrompt + getMoodFollowUp` | 13+ time-of-day adaptive prompts (when-driven) | Largest — caller resolves StringResource via `@Composable` selection |
| `CalculateStreaksUseCase.kt::dayOfWeekItalian` | 7 weekday names (cosmetic; consumer field unused in UI) | Could be safely deleted (dead path) |
| Wellness wizard screens: `BrainDumpScreen`, `GratitudeScreen`, `EnergyCheckInScreen`, `MovementScreen`, `SleepLogScreen`, `ConnectionScreen`, `InspirationScreen`, `ReframeScreen` | ~80 strings (titles, sections, "Salva"/"Aggiorna" CTAs, body copy) | Same pattern as Tier 2.A but spread across ~8 files |
| `BlockScreen.kt` action items + body | ~20 strings | Per-screen migration |
| `HabitListScreen.kt` save labels | ~5 strings | Per-screen migration |
| `MainActivity.kt` "Calmify"/"Error"/"Try Again" | 3 strings | Notes: app/ launcher is `com.android.application` Android-only; possibly via `app/src/main/res/values/strings.xml` since `compose.components.resources` 1.7.1 dep was added in Tier 1 |
| `ScreenTutorials.kt` Percorso/Chat/Write tours | Already deleted as dead code in J.1 — re-add only if tours re-introduced |
| Screenshot 22-25 audit | TBD — image limit hit at #21, four pending | Read in next session before declaring full-app translation complete |

## Status 2026-04-28 (this session, before Tier 3)

- **20/25 screenshots read** (image dimension cumulative limit hit at #21).
- **Tier 1 + 2 migrated and build-verified**.
- **Working tree**: ~110 file modificati, NON committati.
- **Next**: commit Tier 1+2 atomic (code + docs), then Tier 3 in same or new session per user choice.

## Progress

- [x] Screenshot 01/25 — coach mark home step 1 (`Bentornato, amico`)
- [x] Screenshot 02/25 — coach mark home step 2 (`Le tue attività rapide`)
- [x] Screenshot 03/25 — coach mark home step 3 (`Come stai oggi?`)
- [x] Screenshot 04/25 — coach mark home step 4 (`Il tuo compagno AI`)
- [x] Screenshot 05/25 — Home full scroll (heavy IT)
- [x] Screenshot 06/25 — Snapshot/Weekly Check-in (partial migration: titles EN, bodies IT)
- [x] Screenshot 07/25 — Snapshot questionnaire Q1 (questions hardcoded IT)
- [x] Screenshot 08/25 — Garden full scroll (massive IT — sections + every activity title/description hardcoded; user confirmed: ALL Garden descriptions need migration)
- [x] Screenshot 09/25 — Journal home (incl. nav `Percorso` regression!)
- [x] Screenshot 10/25 — Community/Feed (tabs hardcoded IT, nav `Percorso` confirmed regression)
- [x] Screenshot 11/25 — Thread Detail (sort chips + empty state + composer placeholder IT)
- [x] Screenshot 12/25 — Composer Reply (mood chips IT, mostly migrated)
- [x] Screenshot 13/25 — New Post (Type chips IT, mood chips IT, XML escape bug visible)
- [x] Screenshot 14/25 — Feed post-options bottom sheet (5 actions hardcoded IT)
- [x] Screenshot 15/25 — Il Mio Percorso (Journey dashboard — almost entirely hardcoded IT)
- [x] Screenshot 16/25 — Habit Stacking info bottom sheet (1 long IT body)
- [x] Screenshot 17/25 — Daily Streak info bottom sheet (title EN, body IT)
- [x] Screenshot 18/25 — Minimum Action info sheet (title EN, body IT)
- [x] Screenshot 19/25 — Trend di benessere info sheet (title + body both IT)
- [x] Screenshot 20/25 — DUPLICATE of #19 (same Trend sheet)
- [x] Screenshot 21/25 — Recurring themes info sheet (title EN, body IT)
- [ ] Screenshot 22/25 ... 25/25

---

## Findings per screenshot

### 01 — `Screenshot_20260428_161113_Calmify.jpg` (Coach mark Home #1)

Italian strings visible:
- `Bentornato, amico` (title)
- `Questa è la tua home. Ogni giorno trovi un saluto personalizzato e un riepilogo di come stai andando.` (description)
- `Avanti` (button)
- `Salta` (skip)

Likely source: `core/ui/.../coaching/ScreenTutorials.kt` (already identified, hardcoded data object)

---

### 02 — `Screenshot_20260428_161138_Calmify.jpg` (Coach mark Home #2)

Italian strings visible:
- `Le tue attività rapide` (title)
- `Da qui puoi accedere con un tocco a diario, meditazione, check-in energia e molto altro. Scegli cosa ti serve oggi.` (description)
- `Avanti` (button)
- `Salta` (skip)

Source: `ScreenTutorials.kt`

---

### 03 — `Screenshot_20260428_161155_Calmify.jpg` (Coach mark Home #3)

Italian strings visible:
- `Come stai oggi?` (title)
- `Il tuo umore del giorno è mostrato qui. Toccalo per aggiornarlo o per vedere come è cambiato nel tempo.` (description)
- `Avanti` (button)
- `Salta` (skip)
- `ssioni recenti` (background visible, partial)

Source: `ScreenTutorials.kt`

---

### 04 — `Screenshot_20260428_161208_Calmify.jpg` (Coach mark Home #4 — final)

Italian strings visible:
- `Il tuo compagno AI` (title)
- `Il tuo avatar è sempre pronto ad ascoltarti. Toccalo per iniziare una conversazione vocale o testuale, in qualsiasi momento.` (description)
- `Capito!` (button — final step uses `Capito!` instead of `Avanti`)
- `Salta` (skip)

Source: `ScreenTutorials.kt`

---

### 05 — `Screenshot_20260428_161214_Calmify.jpg` (Home — full scroll)

**Context:** main Home screen, top → bottom scroll. PRO badge visible. Device locale = English. Most strings visible are Italian = hardcoded.

#### Greeting card (top)
- `Ciao, Amine` (greeting prefix `"Ciao, "` hardcoded — already grep-confirmed in `DashboardHeader.kt:98` and `ExpressiveHero.kt:97`)
- `il tuo benessere e' nella media` (status sentence — hardcoded `"il tuo benessere e' "` in `DashboardHeader.kt:115` + `ExpressiveHero.kt:114`; `nella media` is a TrendDirection label)
- `Stabile` (chip — `TrendDirection.STABLE -> "Stabile"` in `DashboardHeader.kt:199` + `ExpressiveHero.kt:197`)

#### Hero CTA (English ✓)
- `Talk to Eve` ✓
- `Your voice AI assistant` ✓

#### Action buttons
- `Scrivi` (write button — hardcoded in `ExpressiveQuickActions.kt:62` `label = "Scrivi"`)
- `Snapshot` ✓ (English)

#### Weekly section
- `Questa settimana` (section header — `HomeContent.kt:237`, `WeeklyActivityTracker.kt:64`, `HomeUiModels.kt:163` enum label)
- `CRESCITA` (label — `ExpressiveWeekStrip.kt:97`)
- `0 GIORNI` (count — likely `WeeklyActivityTracker` or similar; suffix `GIORNI` hardcoded)
- Day initials `L M M G V S D` (Italian abbreviations for Lun/Mar/Mer/Gio/Ven/Sab/Dom — almost certainly hardcoded array somewhere; should use `DayOfWeek.getDisplayName(TextStyle.NARROW, locale)`)
- `0/5 obiettivo` (progress label — "obiettivo" hardcoded; need to grep)

#### Daily Actions section
- `Azioni quotidiane` (section header — hardcoded)
- `Gratitudine` (chip)
- `Energia` (chip)
- `Sonno` (chip)
- More chips off-screen (visible at right edge)

#### Reflection card
- `La tua riflessione` (card title — hardcoded section header)
- Insight body text mostly server-generated by Gemini (out of client-side scope) BUT containing IT phrases like:
  - `"Amine, questa settimana sei in equilibrio. Inizia a scrivere per vedere i trend Ultimamente hai riflettuto su Relax, Benessere. Ogni giorno di scrittura conta."` — likely hybrid: tags from data (`Relax`, `Benessere`) + boilerplate template hardcoded IT
- `Relax` `Benessere` chips (tags — possibly enum labels or server data)

#### Mood section
- `Umore` (section header — hardcoded)
- `Il tuo mood` (subsection label — mixed IT/EN)
- `7 giorni` `30 giorni` `90 giorni` (filter pills — hardcoded "giorni" suffix)
- `Molto Positivo` (mood label — hardcoded enum?)
- `Positivo` `Neutro` `Negativo` (mood breakdown chips — hardcoded enum labels)
- `5 entries` ✓ (English)

#### Recurring themes
- `Temi ricorrenti` (section header — hardcoded)
- Tag chips: `Relax` `Benessere` (likely enum/data labels)

#### Community preview
- `Community` ✓ (same in both)
- `3 discussioni recenti` (subtitle — hardcoded "discussioni recenti")
- `Vedi tutto` (button — hardcoded)
- User-generated content (Ciaooo / stupendo / heila / H3tnjUSP) — NOT translatable, ignore
- `Sereno` chip (mood enum label — hardcoded)

#### Estimated additional unique IT strings (this screenshot only)
~25-30 unique hardcoded strings, distributed across at least 6 distinct files (DashboardHeader/ExpressiveHero, HomeContent, WeeklyActivityTracker, ExpressiveWeekStrip, ExpressiveQuickActions, mood enum, daily actions chips, community preview, insight card).

---

### 06 — `Screenshot_20260428_161246_Calmify.jpg` (Snapshot / Weekly Check-in landing)

**Context:** entry screen for the weekly wellness questionnaire. Shows partial migration — section TITLES are English but the BODY descriptions under each section are still hardcoded Italian.

#### English ✓
- `Weekly Check-in` (top bar)
- `Your moment of awareness` (hero title)
- `Take 2 minutes to check in on yourself` (subtitle)
- `Why is it important?` (section header)
- `Self-awareness` (subsection title)
- `Tracking over time` (subsection title)
- `Personalized insights` (subsection title)
- `How it works` (section header)
- `Your data is private` (privacy box title)
- `Answers are visible only to you and used to generate your insights.` (privacy box body)
- `Start questionnaire` (CTA button)

#### Italian (hardcoded — to fix)
- Self-awareness body: `Fermarsi a riflettere su come stai e' il primo passo per migliorare. Spesso non ci rendiamo conto di come ci sentiamo finche' non ce lo chiediamo esplicitamente.`
- Tracking over time body: `Ogni settimana costruisci un quadro sempre piu' completo del tuo benessere. Potrai vedere trend, progressi e aree su cui lavorare.`
- Personalized insights body: `Le tue risposte vengono analizzate dall'AI per offrirti riflessioni psicologiche su misura: pattern emotivi, punti di forza e suggerimenti concreti.`
- Step 1: `10 domande, una alla volta — rispondi d'istinto con lo slider`
- Step 2: `Copri 4 aree: vita quotidiana, salute psicologica, autodeterminazione e connessione sociale`
- Step 3: `Ricevi un'analisi AI personalizzata basata sulle tue risposte`

**Likely source:** `features/home/.../SnapshotScreen.kt` — sprint Phase C.5 migrated SOME strings here ("Your moment of awareness", "Why is it important?", titles) but left the BODY descriptions hardcoded. Classic partial-migration pattern from grep audit miss.

---

### 07 — `Screenshot_20260428_161258_Calmify.jpg` (Snapshot questionnaire — Q1)

**Context:** Question 1 of the 10-question wellness questionnaire. Slider 0-10 with min/max labels.

#### English ✓
- `Question 1 of 10` (top bar — parametric, already migrated)
- `Next →` (button)

#### Italian (hardcoded — to fix)
- `Domini di Vita` (category chip — likely enum label like `Domain.LIFE -> "Domini di Vita"`)
- `Quanto sei soddisfatto della tua vita in generale?` (question text — one of 10 hardcoded questions in a list)
- `Insoddisfatto` (slider min label)
- `Molto soddisfatto` (slider max label)

**Likely source:** `features/home/.../snapshot/SnapshotQuestions.kt` or similar data object containing 10 `Question(text = "...", ...)` entries. **Same anti-pattern as `ScreenTutorials.kt`** — UI-visible strings hardcoded inside a data list, missed by the sprint grep.

User note: not all 10 questions screenshotted. Will need to find the file and migrate ALL 10 questions + 4 category labels (Domini di Vita / Salute Psicologica / Autodeterminazione / Connessione Sociale per the Snapshot landing page).

---

### 08 — `Screenshot_20260428_161325_Calmify.jpg` (Garden — full scroll)

**Context:** Garden screen. User explicitly flagged this as worst-case: *"garden tutte le descrizioni delle attività sono da fare"*. The screenshot confirms it — most of this screen is hardcoded Italian. Also visible: **duplicated sections** (Mente appears twice, Spirito appears twice, several activities appear 2x or 3x) — likely a separate UI bug where the data list contains repeated entries.

#### Top — Ikigai card
- `Garden` (top bar — same word both languages)
- `Il Tuo Ikigai` (card title, IT)
- `Tocca per approfondire` (subtitle, IT)
- `IKIGAI` (center label, neutral)
- `Passione`, `Talento`, `Missione`, `Professione` (4 axis labels, IT)
- `Passion` `Talento` `Mission` `Profess` (filter pills — **MIXED IT/EN, partial migration!** "Passion" and "Mission" got translated, "Talento" and "Profess" did not — both inconsistent and possibly truncated)

#### Progress
- `16 di 19 attività esplorate` (IT — "X di Y" pattern hardcoded; should be `stringResource(Strings.X.activitiesExplored, 16, 19)`)

#### Section: Scrittura (3/3)
- Section title `Scrittura` (IT)
- `Diario` / `Scrivi i tuoi pensieri`
- `Brain Dump` / `Svuota la mente`
- `Gratitudine` / `3 cose belle`

#### Section: Mente (4/4) — appears DUPLICATED
- Section title `Mente` (IT)
- `Meditazione` / `Respira e centra`
- `Gratitudine` / `3 cose belle`
- (second Mente block:)
- `Meditazione` / `Respira e centra`
- `Reframing` / `Cambia prospettiva` (×2)
- `Blocchi` / `Riconosci gli ostacoli`
- `Pensieri Ricorrenti` / `Osserva i pattern`

#### Section: Corpo (4/4)
- Section title `Corpo` (IT)
- `Energia` / `Come stai oggi?`
- `Sonno` / `Traccia il riposo` (×2)
- `Movimento` / `Registra attività`
- `Dashboard` / `Panoramica corpo`

#### Section: Spirito (3/6) — appears DUPLICATED
- Section title `Spirito` (IT)
- `Valori` / `Scopri cosa conta`
- `Dashboard` / `Panoramica corpo`
- (second Spirito block:)
- `Valori` / `Scopri cosa conta`
- `Ikigai` ([nuovo] badge IT) / `Trova il tuo scopo` (×2)
- `Awe` / `Meraviglia quotidiana` (×3)
- `Silenzio` ([nuovo]) / `Pratica il vuoto` (×3)
- `Connessioni` ([nuovo]) / `Relazioni che contano` (×2)
- `Ispirazione` / `Raccogli spunti`

#### Section: Abitudini (2/2)
- Section title `Abitudini` (IT)
- `Abitudini` / `Costruisci routine`
- `Ambiente` / `Disegna il contesto`

#### Badges
- `nuovo` (IT badge label, hardcoded)

#### Categorization
- This is data-driven UI. Each activity is `Activity(name = "Diario", description = "Scrivi i tuoi pensieri", ...)` in a list — same anti-pattern as `ScreenTutorials.kt` and snapshot questions.
- Likely source: `features/garden/.../GardenActivities.kt` or similar — find via grep.
- ~50+ unique IT strings just here. **Highest-volume single screen in the audit.**
- Also: file investigation needed for the duplicated sections bug (separate from i18n).

---

### 09 — `Screenshot_20260428_161402_Calmify.jpg` (Journal home)

**Context:** Journal hub (entry list + filter chips). User-generated content (diary titles, dates) is NOT translatable — only chrome.

#### English ✓
- `Journal` (top bar)
- Mood chips on entries: `Humorous`, `Angry`, `Happy`
- Dates: `Mar 21, 2026`, `Apr 11, 2026` (locale-formatted)

#### Italian (hardcoded — to fix)
- `Spunto del giorno` (welcome card title)
- `Bentornato. Non importa quanto tempo e' passato -- sei qui adesso.` (welcome message — already grep-confirmed at `JournalHomeScreen.kt:681`)
- `Tocca per iniziare a scrivere` (subtle CTA in welcome card)
- Filter chip row: `Tutte` / `Diario` / `Gratitudine` / `Energia` (chip labels — first chip "Tutte" suggests an enum-based set)
- `I tuoi ricordi` (section header — "Your memories")
- `5 diari con foto` (subtitle — "5 diaries with photos", parametric)
- `Le tue riflessioni` (section header — "Your reflections")

#### User-generated content (NOT translatable, skip)
- `Felice per un film`, `contento che guardo un film chill con gli amici`, `Ho preso il telefono di Ami...`, `...rso a legue of leg...`, `...soluta` — diary entries, ignore

#### Bottom nav — REGRESSION
- `Home` ✓ `Garden` ✓ `Journal` ✓ `Community` ✓
- **`Percorso` (Italian)** ❌ — but my Fase H fix at `DecomposeApp.kt:1219,1225` uses `localizedLabel()` and `Res.string.nav_journey` is "Journey" in EN. **This is a regression OR a different nav bar**. Need to check: is there another nav bar component (e.g. `EnterpriseNavigationBar`) used somewhere that still passes `destination.label`? Or is the user's installed APK not the latest?

**Action**: when migrating, double-check there's only ONE nav bar render path; if two paths exist, fix both or unify.

---

### 10 — `Screenshot_20260428_161412_Calmify.jpg` (Community / Feed)

**Context:** Community feed with 4 tabs at top. Posts shown with mood chip + engagement bar.

#### English ✓
- `Community` (top bar)
- `2w`, `1mo` (relative timestamps)
- Bottom nav: `Home`, `Garden`, `Journal`, `Community`

#### Italian (hardcoded — to fix)
- Tab labels (all 4 IT): `Tutti` / `Scoperte` / `Sfide` / `Domande`
- `Sereno` (mood chip on first post — confirms `Mood.SERENE -> "Sereno"` enum hardcoded label)
- Bottom nav: `Percorso` (REGRESSION — same as screenshot 09)

#### User-generated content (NOT translatable)
- Username `H3tnjUSPHMUb`, post bodies `Ciaooo`/`stupendo`/`heila`/`hahah`/`No` — skip

**Confirms** that `nav_journey` lookup for `Percorso → Journey` is NOT being applied somewhere in the actual nav rendering path. Highest-priority bug.

---

### 11 — `Screenshot_20260428_161418_Calmify.jpg` (Thread Detail)

**Context:** Open thread view — root post + replies area + composer.

#### English ✓
- `Thread` (top bar)
- `Replies` (section header)
- `Replying to H3tnjUSPHMUbpSJcvpNrY8xbeg63` (parametric — migrated in Phase C.3)
- `2w` (relative time)

#### Italian (hardcoded — to fix)
- `Sereno` (mood chip on root post — confirms same enum hardcoded as screenshot 10)
- `Popolari` (sort chip — selected state)
- `Recenti` (sort chip)
- `Nessuna risposta` (empty state title)
- `Sii il primo a rispondere` (empty state subtitle)
- `Scrivi una risposta...` (composer placeholder)

---

### 12 — `Screenshot_20260428_161426_Calmify.jpg` (Composer — Reply mode)

**Context:** Reply composer with quoted post, mood selector, visibility chip, char counter.

#### English ✓ (mostly migrated)
- `Reply` (top bar)
- `You` (current-user label)
- `Reply...` (composer placeholder)
- `Mood` (mood selector label)
- `Public` (visibility chip)
- `0/500` (char counter)
- `Everyone can reply` (reply-permissions chip)
- `Publish` (CTA)

#### Italian (hardcoded — to fix)
- Mood chips: `Felice`, `Sereno`, `Grato`, `Motivato` (visible — partial cut on right with another chip)
- These are Mood enum labels — same root issue as `Sereno` chip on posts. **One fix point** = enum migration.

---

### 13 — `Screenshot_20260428_161432_Calmify.jpg` (Composer — New Post)

**Context:** Empty new post (no quote, no parent thread). Type chips visible (post type selector — only on new post, not on reply).

#### English ✓
- `New post` (top bar)
- `You`
- `Add to thread`
- `Type`, `Mood`, `Public`, `0/500`, `Everyone can reply`, `Publish`

#### Bug — XML escape error in EN string
- `What\'s on your mind?` — visible literal backslash before the apostrophe. Source: `values/strings.xml` likely has `What\'s on your mind?` (over-escaped). Fix: remove the leading `\` so XML parses to `What's on your mind?`. Same bug almost certainly present in other locales for any string containing apostrophe.

#### Italian (hardcoded — to fix)
- Type chips: `Scoperta`, `Sfida`, `Domanda` (post-type enum labels — same data-class anti-pattern)
- Mood chips: `Felice`, `Sereno`, `Grato`, `Motivato` (Mood enum, same as screenshots 10-12)

---

### 14 — `Screenshot_20260428_161442_Calmify.jpg` (Feed — post options bottom sheet)

**Context:** Long-press / `...` menu on a post opens this bottom sheet with 5 actions.

#### Italian (hardcoded — to fix)
- `Salva` (save)
- `Nascondi` (hide)
- `Silenzia` (mute)
- `Blocca` (block — destructive, red)
- `Segnala` (report — destructive, red)

#### Already covered (same as screenshot 10)
- Tabs `Tutti`/`Scoperte`/`Sfide`/`Domande`, mood chip `Sereno`

---

### 15 — `Screenshot_20260428_161448_Calmify.jpg` (Il Mio Percorso / Journey dashboard — full scroll)

**Context:** Dashboard for the "Percorso" tab (linked to `RootDestination.Percorso` in `DecomposeApp.kt:951`). Shows progress across 4 pillars (Mente/Corpo/Spirito/Abitudini). **Almost entirely hardcoded IT** — same anti-pattern as Garden.

#### Top
- `Il Mio Percorso` (screen title — IT)
- `Mappa del Percorso` (section header — IT)
- Map dot labels: `Abitudini`, `Spirito`, `Mente`, `Corpo` (4 pillar names)

#### Section: Mente
- Header `Mente`
- Stats:
  - `Meditazione` / `0 sessioni`
  - `Reframing` / `0 fatti`
  - `Gratitudine` / `0/7`
- Activity chips: `Meditazione`, `Reframing`, `Blocchi`, `Pensieri`

#### Section: Corpo
- Header `Corpo`
- Stats:
  - `Energia` / `0.0`
  - `Sonno` / `8.0h`
  - `Movimento` / `0/7`
- Activity chips: `Energia`, `Sonno`, `Movimento`, `Dashboard`

#### Section: Spirito
- Header `Spirito`
- Stats:
  - `Valori` / `Da scoprire`
  - `Connessioni` / `0 atti`
- Activity chips: `Valori`, `Ikigai`, `Awe`, `Silenzio`, `Connessioni`, `Ispirazione`

#### Section: Abitudini
- Header `Abitudini`
- Stats:
  - `Oggi` / `0/1`
  - `Totali` / `1 abitudini`
- Activity chips: `Abitudini`, `Ambiente`

#### Categorization
- ~30 unique IT strings on this screen
- Heavy reuse with Garden labels (Meditazione, Reframing, Energia, Sonno, etc.) — **single fix point** = enum-style activity registry, migrated once, used everywhere
- Suffix patterns: `0 sessioni` / `0 fatti` / `0 atti` / `1 abitudini` — count-with-suffix needs `pluralStringResource` not raw concatenation

---

### 16 — `Screenshot_20260428_161516_Calmify.jpg` (Habit Stacking info sheet)

**Context:** Bottom sheet info popup, likely from an `(i)` icon tap.

#### English ✓
- `Habit Stacking` (title)

#### Italian (hardcoded — to fix)
- Body: `È una tecnica che collega una nuova abitudine a una già consolidata. Esempio: "Dopo aver fatto colazione, scrivo tre cose per cui sono grato". Usando un comportamento esistente come "gancio", è molto più facile costruire qualcosa di nuovo.`

Likely source: an `InfoSheet` or `InfoTooltip` data registry; possibly `core/ui/.../tooltips/InfoTooltip.kt` (which already has `"Il trend mostra come il tuo benessere..."` — same anti-pattern).

---

### 17 — `Screenshot_20260428_161525_Calmify.jpg` (Daily Streak info sheet)

**Context:** Same info-sheet pattern as #16. Title migrated, body still hardcoded.

#### English ✓
- `What is the daily streak?` (title)

#### Italian (hardcoded — to fix)
- Body: `Indica quanti giorni consecutivi hai scritto nel diario. Costruire una serie continua rinforza l'abitudine e ti aiuta a mantenere costanza nel tuo percorso di crescita.`

**Pattern confirmed**: info-tooltip registry has migrated TITLES but left BODIES hardcoded. Single registry to fix → all info sheets get translated bodies.

---

### 18 — `Screenshot_20260428_161534_Calmify.jpg` (Minimum Action info sheet)

**Context:** Same info-sheet pattern as #16-#17.

#### English ✓
- `Minimum Action` (title)

#### Italian (hardcoded — to fix)
- Body: `Il punto di ingresso più piccolo possibile per un'abitudine. Invece di "fare 30 minuti di meditazione", il minimum action è "mettiti sul cuscino". Abbassare la soglia iniziale rende quasi impossibile non iniziare — e spesso si finisce per fare molto di più.`

---

### 19 — `Screenshot_20260428_161546_Calmify.jpg` (Trend di benessere info sheet)

**Context:** Info sheet on the home dashboard trend chart.

#### Italian (hardcoded — title AND body to fix)
- Title: `Trend di benessere`
- Body: `Il trend mostra come il tuo benessere complessivo è cambiato nel tempo, combinando umore, energia, sonno e qualità delle riflessioni. Non cercare la perfezione: anche un piccolo miglioramento costante nel tempo ha un impatto enorme.`

This body matches the snippet `core/ui/.../tooltips/InfoTooltip.kt:173` already grep-found earlier ("Il trend mostra come il tuo benessere complessivo è cambiato nel tempo, " + ...). **Confirmed source: `InfoTooltip.kt`** — that file is the registry to migrate.

---

### 20 — `Screenshot_20260428_161555_Calmify.jpg` (DUPLICATE of #19)

Same `Trend di benessere` sheet as screenshot 19. No new strings. Skipped.

---

### 21 — `Screenshot_20260428_161607_Calmify.jpg` (Recurring themes info sheet)

#### English ✓
- `What are recurring themes?` (title)

#### Italian (hardcoded — to fix)
- Body: `Sono gli argomenti che compaiono più spesso nei tuoi diari. Riconoscere i temi che tornano ti aiuta a capire cosa occupa la tua mente e su cosa vale la pena riflettere con più attenzione.`

Same registry: `InfoTooltip.kt`.

---

(continuing...)
