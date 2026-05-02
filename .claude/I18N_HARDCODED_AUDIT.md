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

**Cumulative Tier 1 + 2: ~189 keys × 6 langs (EN+IT+ES+FR+DE+PT) ≈ 1134 translations, 28 Kotlin files refactored, BUILD GREEN (assembleDebug). Commit `da065aa`.**

**Tier 3.A + 3.B (2026-04-29) — DONE, BUILD GREEN**:
- `DateFormatters.getTimeOfDayGreeting()` → `getTimeOfDayGreetingRes(): StringResource`. New keys `datetime_greeting_night` + `datetime_week_label` (parameterized). Caller `HeroGreetingCard.kt` updated.
- `Trend` enum (PsychologicalProfile.kt) — dropped `displayName: String` (would create circular `core/util→core/ui` dep). Callers `PercorsoScreen.kt`/`ProfileDashboard.kt` use inline `when (trend)→Strings.Trend.X`. 4 trend label + 4 trend message keys added.
- `getWeekLabelFull()` deleted. 2 callers use `stringResource(Strings.DateTime.weekLabel, num, year)`.
- `TimePeriod` enum (HomeUiModels.kt) `label: String` → `labelRes: StringResource`. 6 period keys + 2 entry-count plural keys with `%1$d` format. FeedSectionHeader pluralizes via stringResource.
- **Massive dead code purge** (CLAUDE.md rule 12) — `DateFormatters.kt` 250→35 LOC: deleted 15 unused IT-hardcoded utility funcs (formatFullDate/formatMediumDate/formatShortDate/formatTime/formatDayMonth/formatMonth/formatShortMonth/getRelativeTime/getTimePeriod/formatSectionHeader/getWeekRangeString/getWeekLabel/getDayLabel/getFullDayName/getMonthLabels). `CalculateStreaksUseCase.dayOfWeekItalian()` deleted (consumer field unused).
- Cumulative Tier 3.A+3.B: **~28 keys × 6 langs ≈ 168 translations + 8 file Kotlin refactor**.

**Total Phase J cumulative (Tier 1+2+3.A+3.B): ~217 keys × 6 langs ≈ 1302 translations + 36 Kotlin files refactored**.

**Tier 3.C + 3.E (2026-04-30) — DONE, BUILD GREEN**:

- **Tier 3.C — BlockType + BlockScreen** (commit pending). `BlockType` enum (`core/util/.../model/Block.kt`) had `displayName: String` + `suggestion: String` IT — would create circular `core/util→core/ui` dep. Pattern: drop both fields, UI sites resolve via inline `@Composable` extensions `BlockType.label()` / `BlockType.suggestion()` returning `stringResource(when (this)→Strings.Block.X)`. `BlockResolution.displayName` was unused in UI (only `.name` for canonical lookup) — field deleted entirely (CLAUDE.md rule 12). `BlockScreen.kt` migrated: DescribeStep title/body/active hint, DiagnosisStep "Eve dice:", ActionStep title/subtitle + 13 ActionItems (4 reusable titles + 11 per-type subtitles via `Strings.Block.action*`), HistoryStep empty title/subtitle, BlockHistoryCard resolution note prefix. `ActionItem` data class fields `title: String`/`subtitle: String` → `StringResource`. ~36 keys × 6 langs ≈ 216 translations.
- **Tier 3.E — JournalHomeScreen + buildWeeklyReflection** (commit pending). `getContextualPrompt(diaries): String` → `@Composable` returning resolved String via `stringResource(when (cond)→Strings.JournalPrompt.X)`. `getMoodFollowUp(mood, hour): String?` → `@Composable` (returns null for moods with no follow-up). `formatDiaryTimestamp(millis): String` → `@Composable` (uses `Strings.JournalPrompt.timestampToday`/`timestampYesterday` parameterized format). `buildWeeklyReflection(profile)`/`buildWeeklyReflection(profile, chartData)` → `@Composable` returning concatenated parts via `Strings.Weekly.X` + `Strings.Trend.msgX`. Both PercorsoScreen + ProfileDashboard callers updated; `remember(profile) { buildWeeklyReflection(profile) }` → direct call (composable scope). "La tua settimana" / "Il tuo percorso" headers + "Inizia a scrivere le tue riflessioni" subtitle migrated. ~35 keys × 6 langs ≈ 210 translations.
- **Cumulative Tier 3.C+3.E: ~71 keys × 6 langs ≈ 426 translations + 4 Kotlin files refactored** (Block.kt, BlockScreen.kt, JournalHomeScreen.kt, PercorsoScreen.kt + ProfileDashboard.kt parallel buildWeeklyReflection).
- **`Strings.Block` group** added to facade (37 keys: 5 type labels + 5 suggestions + 4 reusable action titles + 11 per-type subtitles + 5 chrome + 2 history empty + 1 resolution note + 4 misc).
- **`Strings.JournalPrompt` group** extended (3 existing → 22 total: +13 contextual + +7 mood + +3 chrome).
- **`Strings.Weekly` group** added (10 keys: cardTitle + journeyHeader + 3 mood + 3 stress + resilience + diaryCount).
- **Build verified**: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL after both tiers.

**Total Phase J cumulative (Tier 1+2+3.A+3.B+3.C+3.E): ~288 keys × 6 langs ≈ 1728 translations + 40 Kotlin files refactored**.

**Tier 3.G (2026-04-30) — DONE, BUILD GREEN** (Home/Feed/Mood/Journal pass driven by user device screenshots + grep audit):

Coverage:
- **`SentimentLabel` enum** in `core/util/.../model/DiaryInsight.kt` — dropped `displayName: String` IT field. Same circular-dep pattern as `Trend`/`BlockType`. UI sites (`InsightScreen.kt`) resolve via inline `when (sentiment)→Strings.Sentiment.X`. New `Strings.Sentiment` group (5 keys: veryNegative/negative/neutral/positive/veryPositive).
- **`TimeRange` enum** in `features/home/.../HomeUiModels.kt` — dropped `label: String` IT field. New `TimeRange.labelRes: StringResource` extension property. 3 callers updated (ExpressiveMoodCard inline, MoodInsightCard, MoodDistributionCard chip + dropdown).
- **`DominantMood` data class** — dropped `label: String` field (was always derived from `sentiment.displayName`). New `DominantMood.labelRes` extension. 3 consumer sites migrated (ExpressiveMoodCard, MoodInsightCard donut + bottom dominant indicator, MoodDistributionCard).
- **`FeedContract.FeedTab` enum** — dropped `label: String` field. `FeedScreen.kt` resolves via inline `when (tab)→Strings.Feed.X`. 4 keys (tabAll/tabDiscoveries/tabChallenges/tabQuestions).
- **`Strings.moodTagLocalizedRes(canonical)` static helper** in Strings facade — single point that maps canonical IT mood tags (stored as-is in Firestore for backward compat per CLAUDE.md) to localized `StringResource?`. Used by `ThreadPostCard.kt` to render mood chips on feed posts in user's language.
- **Hero greeting**: `HeroGreetingCard.kt` "Come ti senti oggi?" → `Strings.SnapshotWizard.howFeelToday`.
- **Quick action**: `ExpressiveQuickActions.kt` "Scrivi" pill → `Strings.Screen.Home.quickActionWrite`.
- **Week strip**: `ExpressiveWeekStrip.kt` "CRESCITA" + "GIORNI"/"GIORNO" + "obiettivo" + day initials L M M G V S D + InfoTooltip body migrated. Same for `WeeklyActivityTracker.kt` (parallel implementation). `HomeContent.kt` "Questa settimana" header.
- **Mood card**: `HomeContent.kt` "Umore" section header. `ExpressiveMoodCard.kt` "Il tuo mood" + 7/30/90 giorni filter chips (now resolved via `range.labelRes`). 3 mood pills (Positivo/Neutro/Negativo) localized in 4 places: ExpressiveMoodCard, MoodInsightCard, MoodDistributionCard legend, DonutChartLegend.
- **Daily actions**: `HomeContent.kt` "Azioni quotidiane" section header.
- **Reflection card**: `ExpressiveReflection.kt` + `ReflectionCard.kt` "La tua riflessione" title (both render paths).
- **Community preview**: `CommunityPreviewCard.kt` "X discussioni recenti" param + "Vedi tutto" button. Same "Vedi tutto" in `ActivityFeed.kt`.
- **Snapshot wizard / WriteContent**: "Come ti senti?" + "Scorri per scegliere il tuo mood" + "Avanti" button — now `Strings.SnapshotWizard.howFeel`/`swipeToChoose` + `Strings.Coach.buttonNext`.
- **Journal filter chips**: `JournalHomeScreen.kt::ActivityPill` data class field `label: String` → `StringResource`. 9 chip labels migrated (Tutte/Diario/Gratitudine/Energia/Sonno/Meditazione/Abitudini/Brain Dump/Movimento). New `Strings.JournalFilter` group.
- **Welcome card**: `JournalHomeScreen.kt` "Spunto del giorno" + "Tocca per iniziare a scrivere" + "Il tuo diario è vuoto" — already migrated mid-session before screenshots arrived.
- **Feed tabs**: `FeedScreen.kt` rendered via `Strings.Feed.X`. 4 tabs (Tutti/Scoperte/Sfide/Domande).
- **Mood chip on posts**: `ThreadPostCard.kt` resolves `thread.moodTag` (canonical IT) via `Strings.moodTagLocalizedRes(mood)`. Storage stays canonical IT.

Volume: **~60 keys × 6 langs ≈ 360 translations + ~16 Kotlin files refactored**.

Files touched:
- core/util/.../DiaryInsight.kt (SentimentLabel enum drop)
- core/ui/.../i18n/Strings.kt (Sentiment + SnapshotWizard + JournalFilter + Feed groups + moodTagLocalizedRes helper + Home extension)
- core/ui/.../composeResources/values{,-it,-es,-fr,-de,-pt}/strings.xml (~60 keys × 6 langs)
- core/social-ui/.../ThreadPostCard.kt
- features/home/.../HomeUiModels.kt (TimeRange + DominantMood + labelRes extensions)
- features/home/.../CalculateMoodDistributionUseCase.kt
- features/home/.../HomeContent.kt
- features/home/.../components/expressive/ExpressiveQuickActions.kt
- features/home/.../components/expressive/ExpressiveWeekStrip.kt
- features/home/.../components/expressive/ExpressiveMoodCard.kt
- features/home/.../components/expressive/ExpressiveReflection.kt
- features/home/.../components/dashboard/WeeklyActivityTracker.kt
- features/home/.../components/dashboard/MoodInsightCard.kt
- features/home/.../components/dashboard/CommunityPreviewCard.kt
- features/home/.../components/dashboard/ReflectionCard.kt
- features/home/.../components/insights/MoodDistributionCard.kt
- features/home/.../components/charts/DonutChart.kt
- features/home/.../components/feed/EnhancedActivityCard.kt
- features/home/.../components/feed/ActivityFeed.kt
- features/home/.../components/hero/HeroGreetingCard.kt
- features/feed/.../FeedContract.kt + FeedScreen.kt
- features/write/.../JournalHomeScreen.kt (welcome card 3 strings + 9 ActivityPill chips)
- features/write/.../androidMain/.../WriteContent.kt (Snapshot scroll wizard CTA)
- features/insight/.../InsightScreen.kt (SentimentLabel inline mapping)

**Cumulative Phase J after Tier 3.G: ~348 keys × 6 langs ≈ 2088 translations + 56 Kotlin files refactored**.

## Post-3.G screenshots (2026-05-02) — NEW DEBT discovered

User shared 4 device screenshots after Tier 3.G commit `00d11b7`. Reveals two large unaddressed surfaces (each warrants its own tier):

### Screenshot A — Snapshot wizard step 1/10
Fully EN ✓ (Tier 3.G coverage works). No new debt.

### Screenshot B — Diary creation wizard step 2/10 (title input)
- `Dai un titolo` — IT (`WriteContent.kt:312`)
- `Un titolo breve per il tuo diario` — IT (`WriteContent.kt:319`)
- `Indietro` — IT (`WriteContent.kt:557`)

### Screenshot C — Diary creation wizard step 3/10 (description)
- `Racconta` — IT (`WriteContent.kt:357`)
- `Scrivi come ti senti, cosa e' successo` — IT (`WriteContent.kt:364`)
- `Indietro` — IT (same shared button)

### Screenshot D — Journey/Percorso dashboard
- `Progresso Settimanale` — IT (`PercorsoScreen.kt:149`)
- `Mappa del Percorso` — IT (`PercorsoScreen.kt:209`)
- 4 pillar map labels: `Abitudini` / `Spirito` / `Mente` / `Corpo` (likely a `Pillar` enum with IT displayName, or hardcoded in the map renderer)
- Section headers: `Mente`, `Corpo`, `Spirito`, `Abitudini` (same pillar labels reused as section headers)
- Per-pillar stats labels: `Meditazione` / `0 sessioni`, `Reframing` / `0 fatti`, `Gratitudine` / `0/7`, `Energia`, `Sonno`, `Movimento`, `Valori`, `Connessioni`, `Ikigai`, `Awe`, `Silenzio`, `Ispirazione`, `Ambiente`
- Suffix patterns: `0 sessioni`, `0 fatti`, `0 atti`, `1 abitudini` — count + IT noun (parameterized)

## Tier 3.H + 3.I — DONE 2026-05-02 (BUILD GREEN)

**Tier 3.H (diary creation wizard)**: `WriteContent.kt` 10 steps fully migrated. Per-step heading + subtitle (title/description/6 metric steps via MetricStepWrapper/almost-done) + Indietro button + Salva diario CTA + Smart Capture + Rianalizza. New `Strings.WriteWizard` group (22 keys). Reused `Strings.Action.back` and `Strings.Coach.buttonNext` for wizard chrome (no duplication). Plus `Strings.WriteWizard.complete` for final step "Completa" / "Complete" wizard CTA.

**"Indietro" sweep**: 10 wellness/diary files migrated to `Strings.Action.back`:
- features/write/.../DashboardScreen.kt
- features/write/.../ConnectionScreen.kt
- features/write/.../BrainDumpScreen.kt
- features/write/.../SleepLogScreen.kt
- features/write/.../MovementScreen.kt
- features/write/.../GratitudeScreen.kt
- features/write/.../EnergyCheckInScreen.kt
- features/write/.../WriteContent.kt (wizard back)
- features/write/.../wizard/WizardComponents.kt (back + next + complete buttons)
- features/write/.../androidMain/.../DiaryDetailScreen.kt
- features/humanoid/.../HumanoidScreen.kt

**Tier 3.I (Percorso/Journey interior)**: `PercorsoScreen.kt` + `PercorsoContract.kt` + `PercorsoViewModel.kt`. Full migration:
- "Progresso Settimanale" (top weekly card) → `Strings.Percorso.weeklyProgress`
- "Mappa del Percorso" (interactive node graph header) → `Strings.Percorso.journeyMap`
- 4 pillar map labels (Mente/Corpo/Spirito/Abitudini) — graphNodes literal labels migrated, `pillarMind/pillarBody/pillarSpirit/pillarHabits` keys + `remember` re-keyed to include pillar Strings
- 4 per-pillar section headers → `SectionSummary.titleRes: StringResource`
- Stats with locale-aware plurals: `statSessionsOne/Many` (Meditazione count), `statFactsOne/Many` (Reframing count), `statActsOne/Many` (Connessioni), `statHabitsOne/Many` (Totali habits) — ViewModel chooses singular/plural based on `count == 1`
- Stats with non-plural values (`X/7` fractions, `8.0h` hours, `Confermati`/`Da scoprire` discrete states): `valueOverride: String?` field in `SectionItem` carries pre-formatted string; UI prefers `valueOverride` over `valueRes` when present
- `PercorsoToolAction.label: String` → `labelRes: StringResource`. All 16 tool actions across 4 sections refactored to reuse `Strings.Garden.Activity.*` keys from Tier 2.A (no duplication)
- Activity name reuse — all 16 unique tool labels in Percorso (Meditazione/Reframing/Blocchi/RecurringThoughts/Energia/Sonno/Movimento/Dashboard/Valori/Ikigai/Awe/Silenzio/Connessioni/Ispirazione/Abitudini/Ambiente) come from `Strings.Garden.Activity.*` populated in Tier 2.A

New `Strings.Percorso` group (19 keys). Files refactored: PercorsoContract.kt + PercorsoViewModel.kt + PercorsoScreen.kt.

**Cumulative Phase J after Tier 3.H+3.I: ~398 keys × 6 langs ≈ 2388 translations + 67 Kotlin files refactored**.

Build: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL.

## Tier 3.H — DEFERRED (diary creation wizard, NEW from screenshots B+C)

`WriteContent.kt` 10-step diary wizard. Per-step heading + subtitle hardcoded IT. Grep audit on the file:

| Step | Title | Subtitle |
|---|---|---|
| Mood (1/10) | (already EN ✓ Tier 3.G) | (already EN ✓) |
| Title (2/10) | `Dai un titolo` | `Un titolo breve per il tuo diario` |
| Description (3/10) | `Racconta` | `Scrivi come ti senti, cosa e' successo` |
| EmotionIntensity (4/10) | `Intensita' emotiva` | `Quanto e' intensa l'emozione che provi?` |
| Stress (5/10) | `Livello di stress` | `Quanto ti senti stressato/a?` |
| Energy (6/10) | `Livello di energia` | `Quanta energia hai?` |
| CalmAnxiety (7/10) | `Calma / Ansia` | `Quanto ti senti calmo/a o ansioso/a?` |
| Trigger (8/10) | `Trigger principale` | `Cosa ha influenzato il tuo stato d'animo?` |
| Sensation (9/10) | `Sensazione corporea` | `Cosa senti nel corpo?` |
| Photos+Save (10/10) | `Quasi fatto!` | `Aggiungi foto e salva il tuo diario` |

Plus chrome:
- `Indietro` (back button — line 557, also hardcoded as contentDescription in 12+ other files)
- `Salva diario` (line 519)
- `Smart Capture` (line 621)
- `Rianalizza` (line 639)
- `Mood: %s | %s` template (line 628 — already partially EN)

**Volume estimate: ~25 keys × 6 langs ≈ 150 translations + 1 file (WriteContent.kt) refactor.**

**Bonus shared-key opportunity**: `Indietro` contentDescription appears in 12 wellness wizard files (BrainDump/EnergyCheckIn/Connection/SleepLog/Movement/Gratitude/Reframe/Inspiration/Awe/Dashboard/Humanoid/DiaryDetail) — should use existing `Strings.Action.back` or `Strings.A11y.back` (verify which one). Single sweep-fix across all wizard files.

## Tier 3.I — DEFERRED (Percorso/Journey screen, NEW from screenshot D + reinforces audit screenshot 15)

`PercorsoScreen.kt`. The screen title was migrated in J.7 ("Il Mio Percorso" → `Strings.Screen.Percorso.title`) and `buildWeeklyReflection` in 3.E, but interior content NOT covered:

**Top card — Weekly Progress**:
- `Progresso Settimanale` (line 149)

**Map section**:
- `Mappa del Percorso` (line 209)
- 4 pillar map labels (Abitudini / Spirito / Mente / Corpo) — investigate: enum or list literal?

**Per-pillar sections** (4 sections × ~3-5 entries each):
- Section header per pillar: Mente / Corpo / Spirito / Abitudini
- Per-activity stats labels:
  - Mente: Meditazione (`0 sessioni`), Reframing (`0 fatti`), Gratitudine (`0/7`), Blocchi, Pensieri
  - Corpo: Energia (`0.0`), Sonno (`8.0h`), Movimento (`0/7`), Dashboard
  - Spirito: Valori (`Da scoprire`), Connessioni (`0 atti`), Ikigai, Awe, Silenzio, Ispirazione
  - Abitudini: Oggi (`0/1`), Totali (`1 abitudini`), Ambiente
- Suffix nouns are pluralized IT — need `pluralStringResource` or parameterized format:
  - `%d sessioni` / `%d sessione`
  - `%d fatti` / `%d fatto`
  - `%d atti` / `%d atto`
  - `%d abitudini` / `%d abitudine`

**Volume estimate: ~30 keys × 6 langs ≈ 180 translations + 1 file (PercorsoScreen.kt) refactor.**

Activity name labels (Meditazione, Reframing, Energia, Sonno, ...) are mostly already in `Strings.Garden.Activity.*` (Tier 2.A migrated 19 activities × name+desc) — **reuse existing keys**, don't duplicate.

## Tier 3.D — DEFERRED (wellness wizard screens, sessione futura)

The remaining wellness wizard screens share the same screen-level migration pattern as Tier 2.A but spread across ~8 files. Volume non trivial (~80 strings) and per-screen ripetitivo, batch migration warranted.

| Target | Strings approx | Refactor type |
|---|---|---|
| Wellness wizard screens: `BrainDumpScreen`, `GratitudeScreen`, `EnergyCheckInScreen`, `MovementScreen`, `SleepLogScreen`, `ConnectionScreen`, `InspirationScreen`, `ReframeScreen` | ~80 strings (titles, sections, "Salva"/"Aggiorna" CTAs, body copy) | Same pattern as Tier 2.A but spread across ~8 files |
| `ConnectionType` / `EnergyType` / `MovementType` / `SleepDisturbance` / `Trigger` / `Sensation` / `gratitudeCategory` / `ReframeCategory` enums | per-enum displayName fields | Same circular-dep pattern as Trend/BlockType — drop displayName, callers resolve via inline `when (type)→Strings.X` |
| `HabitListScreen.kt` save labels | ~5 strings | Per-screen migration |
| `MainActivity.kt` "Calmify"/"Error"/"Try Again" | 3 strings | app/ launcher is `com.android.application` Android-only; possibly via `app/src/main/res/values/strings.xml` since `compose.components.resources` 1.7.1 dep was added in Tier 1 |

## Tier 3.F — DEFERRED (Garden activity expanded card, sessione futura)

Discovered via screenshots 23-25 (post-Tier 2.A). Garden activities have collapsed view (name + desc, migrated in Tier 2.A) AND expanded view with long body + 3 benefit bullets + chrome that were not catalogued. Scope: `data class GardenActivity` likely needs `longDescriptionRes: StringResource` + `benefitsRes: List<StringResource>`. ~78 new keys × 6 langs ≈ 468 translations across 19 activities.

## Tier 3.G — DEFERRED (residual misc, sessione futura)

Single-file straggler items still pending audit/migration after Tier 3.D + 3.F closure:
- `ScreenTutorials.kt` Percorso/Chat/Write tours — Already deleted as dead code in J.1; re-add only if tours re-introduced

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
- [x] Screenshot 22/25 — Notifications screen (FULLY ENGLISH ✓ — no findings)
- [x] Screenshot 23/25 — Garden Activity expanded card: Diario (NEW debt: long body + Benefici header + 3 bullets + Inizia Attivita' CTA)
- [x] Screenshot 24/25 — Garden Activity expanded card: Brain Dump (same pattern, per-activity content)
- [x] Screenshot 25/25 — Garden Activity expanded card: Gratitudine (same pattern, confirms ~19 activities × ~4 strings ≈ 76 new keys)

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

### 22 — `Screenshot_20260428_161612_Calmify.jpg` (Notifications screen)

**Context**: Notifications top-level screen with filter chips and empty state.

#### English ✓ (fully migrated)
- `Notifications` (top bar)
- `All` / `Follows` / `Replies` / `Mentions` (filter chips)
- `No notifications yet` (empty state title)
- `You'll see activity from your community here` (empty state subtitle)

**No hardcoded Italian visible**. This screen was fully migrated in sprint Phase C/D. Skip.

---

### 23 — `Screenshot_20260428_161708_Calmify.jpg` (Garden Activity expanded card — Diario)

**Context**: User taps an activity card in Garden, it expands to show a long description + benefits list + duration/difficulty + Start CTA. **Tier 2.A only migrated `name + desc` (collapsed card)**. Expanded view exposes more hardcoded IT.

#### Italian (hardcoded — to fix)
- Title `Diario` ✓ (already migrated as `gardenActivityDiaryName` in Tier 2.A)
- Subtitle `Scrivi i tuoi pensieri` ✓ (already migrated as `gardenActivityDiaryDesc`)
- **Long body** (NEW): `Scrivere i tuoi pensieri ti aiuta a elaborare emozioni e trovare chiarezza. Il diario e' il cuore del tuo percorso di crescita.`
- **Section header** `Benefici` (NEW — shared chrome)
- **3 benefit bullets** (NEW per-activity):
  - `Riduce ansia`
  - `Migliora autoconsapevolezza`
  - `Traccia la crescita personale`
- **Time meta** `~5 min` (`~%d min` likely shared format — verify for migration)
- **Difficulty** `Facile` ✓ (already migrated via `Strings.Garden.Difficulty.X`)
- **CTA** `Inizia Attivita'` (NEW — shared chrome)

#### Categorization
- `data class GardenActivity` likely has additional fields beyond `name/description`: `longDescription: String`, `benefits: List<String>`, `durationMinutes: Int`, `difficulty: Difficulty`. Need grep on `ActivityGardenScreen.kt` for the expanded card render.
- Per-activity content: 19 activities × (1 long body + 3 benefits) = **76 strings**. Plus `Benefici` header + `Inizia Attivita'` CTA = 2 shared keys. **Total: ~78 new keys** (Tier 3.F).

---

### 24 — `Screenshot_20260428_161720_Calmify.jpg` (Garden Activity expanded card — Brain Dump)

**Context**: Same expanded-card pattern as #23, different activity.

#### Italian (hardcoded — to fix)
- Title `Brain Dump` ✓ migrated
- Subtitle `Svuota la mente` ✓ migrated
- Long body (NEW): `Libera la mente da tutto cio' che la occupa, senza giudizio e senza struttura.`
- `Benefici` header (NEW shared)
- Bullets (NEW): `Riduce carico mentale`, `Migliora focus`, `Facilita il sonno`
- `~3 min`, `Facile`
- `Inizia Attivita'` CTA (NEW shared)

Confirms per-activity expanded body pattern.

---

### 25 — `Screenshot_20260428_161732_Calmify.jpg` (Garden Activity expanded card — Gratitudine)

**Context**: Same expanded-card pattern, third activity sample.

#### Italian (hardcoded — to fix)
- Title `Gratitudine` ✓ migrated
- Subtitle `3 cose belle` ✓ migrated
- Long body (NEW): `Riconoscere il bello quotidiano allena il cervello alla positivita'.`
- `Benefici` header (NEW shared)
- Bullets (NEW): `Aumenta felicita'`, `Riduce stress`, `Migliora relazioni`
- `~3 min`, `Facile`
- `Inizia Attivita'` CTA (NEW shared)

**Conclusion (3 sample activities)**: pattern holds — every Garden activity has long body + 3 benefit bullets. **Tier 3.F (new tier — not previously catalogued)** to follow Tier 3.C/D/E. Volume: ~78 keys × 6 langs ≈ 468 translations.

---
