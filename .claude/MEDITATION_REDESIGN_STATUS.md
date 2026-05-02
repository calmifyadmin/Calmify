# Meditation Feature Redesign — Live Status & Checklist

> Companion to `memory/meditation_redesign.md` (long-term memory) and `.claude/MEDITATION_REDESIGN_PLAN.md` (strategy). This file is the **live status tracker** — checked off as work completes. Update BEFORE commit per `feedback_update_all_trackers.md`.
>
> **Design source**: `C:\Design\Calmify\Meditation\Calmify Meditation.html` + `calmify.css`
> **Branch**: `backend-architecture-refactor`
> **Quality bar**: 1:1 design fidelity (or 1:1.5 — exceeding where Compose / Material3 makes it cleaner)

---

## Phase tracker

| Phase | Status | Started | Completed | Commit | Notes |
|---|---|---|---|---|---|
| 1 — Foundation | **DONE** | 2026-05-02 | 2026-05-02 | _pending_ | Domain + 161 keys × 6 langs + Strings facade + Contract/VM + 5 screens. **Build green** (`./gradlew :app:assembleDebug`). |
| 2 — Polish | NOT STARTED | — | — | — | Session pacer + Stop modal + Overview + keyboard |
| 3 — Production | NOT STARTED | — | — | — | TTS + chime sync + a11y + screenshot regression |

---

## Phase 1 — Foundation (DONE 2026-05-02)

Target: ~6h dedicated session. Single (or 2-3 atomic) commit + push.

### 1.1 Domain refactor — `core/util/.../MeditationSession.kt` ✅

- [x] `BreathingPattern` enum: drop `displayName: String` field
- [x] `BreathingPattern` enum: expand from 3 → 6 entries:
  - [x] `COHERENT` (5.5/0/5.5/0)
  - [x] `EXTENDED_EXHALE` (4/0/6/0)
  - [x] `BOX_BREATHING` (4/4/4/4)  *kept, unchanged*
  - [x] `RELAXATION_478` (4/7/8/0, cap=4, requiresExperience)  *kept, add metadata*
  - [x] `BELLY_NATURAL` (0/0/0/0, isGentle)  *replaces DIAPHRAGMATIC*
  - [x] `BODY_SCAN_NATURAL` (0/0/0/0, isGentle)
- [x] Add fields: `cycleCap: Int?`, `requiresExperience: Boolean`, `isGentle: Boolean`
- [x] Add computed: `hasPattern: Boolean`
- [x] `MeditationType` enum: drop `displayName`
- [x] **Backward-compat mapper** in `FirestoreMeditationRepository`: `BreathingPattern.fromCanonicalName()` companion accepts both `DIAPHRAGMATIC` (legacy) and `BELLY_NATURAL` (current). Also persists new `stopped` + `cyclesCompleted` fields.

### 1.2 New domain types ✅

- [x] `MeditationGoal` enum (5 entries: STRESS/ANXIETY/SLEEP/FOCUS/GROUNDING) in `core/util/.../model/`
- [x] `MeditationExperience` enum (3 entries: FIRST/OCCASIONAL/REGULAR)
- [x] `MeditationAudio` enum (3 entries: VOICE/CHIMES/SILENT)
- [x] `MeditationRiskFlag` enum (8 entries with `id: String` + `hasSubText: Boolean`)
- [x] All 4 enums: `@Serializable`, no displayName fields

### 1.3 Strings XML — 6 Latin langs ✅

- [x] `values/strings.xml` (EN default) — 161 keys added
- [x] `values-it/strings.xml` — 161 keys, IT translation
- [x] `values-es/strings.xml` — 161 keys, ES translation
- [x] `values-fr/strings.xml` — 161 keys, FR translation
- [x] `values-de/strings.xml` — 161 keys, DE translation
- [x] `values-pt/strings.xml` — 161 keys, PT translation
- [x] All 6 langs share key count = **966 translations total**

Section breakdown (161 keys total — final):
- [x] Welcome (13)
- [x] Screening chrome (10) + risks × 2 (16) = 26
- [x] Configure (28)
- [x] Techniques (6 × 6 fields = 36)
- [x] Session (15)
- [x] Stop modal (4)
- [x] Overview (18)
- [x] Time formatting (3)
- [x] Mood / Goal / Experience / Audio facades

### 1.4 Strings facade — `Strings.kt` ✅

- [x] Added `Strings.Meditation` group with 13 sub-objects:
  - [x] `Welcome` (13) / `Screening` (10) / `Configure` (28) / `Technique` (6×6=36)
  - [x] `Session` (15) / `Stop` (4) / `Overview` (18)
  - [x] `Goal` (5) / `Experience` (3) / `Audio` (3) / `Risk` (8 × label+sub = 16)
  - [x] `Cue` (6) / `SettleCoach` (3) + `IntegrateCoach` (3) / `Time` (3)
- [x] Helper file `core/ui/.../i18n/MeditationStrings.kt` (~155 LOC):
  - [x] `BreathingPattern.nameRes() / shortRes() / summaryRes() / mechanismRes() / coachRes(idx)`
  - [x] `MeditationGoal.labelRes()`
  - [x] `MeditationExperience.labelRes()`
  - [x] `MeditationAudio.labelRes()`
  - [x] `MeditationRiskFlag.labelRes() / subRes()`

### 1.5 Contract refactor — `MeditationContract.kt` ✅

- [x] `SessionPhase` enum: 3 entries → 5 entries (WELCOME/SCREENING/CONFIGURE/SESSION/OVERVIEW)
- [x] Added `SubPhase` enum (SETTLING/PRACTICE/INTEGRATION)
- [x] Added `SessionConfig` data class (duration/goal/experience/audio/techniqueOverride)
- [x] Added `SessionRuntime` data class (technique/durationSeconds/elapsed/isPaused/cycles/stopped + computed sub-phase/settle/integrate/practice/practiceCap/totalActiveSeconds/remainingSeconds)
- [x] State: replaced fields with `phase / risks / config / session: SessionRuntime? / showStopDialog`
- [x] Computed: `restricted: Boolean` (risks not empty), `effectiveTechnique: BreathingPattern` via `resolveTechnique()` private function
- [x] Dropped `BreathingPhase` enum (replaced by per-segment in SessionRuntime)
- [x] Added intents: `NavigateToScreening / ToggleRiskFlag / ClearAllRisks / SetDuration / SetGoal / SetExperience / SetAudio / SetTechniqueOverride / RequestStopSession / ConfirmStopSession / DismissStopDialog / NavigateBackFromScreening / NavigateBackFromConfigure / NavigateRedoFromOverview / NavigateDifferentFromOverview / SessionAutoComplete / LoadStats / RetryLoadStats`
- [x] Dropped unused intents (old `SelectType` etc.)

### 1.6 ViewModel refactor — `MeditationViewModel.kt` ✅

- [x] All new intents wired to state mutations
- [x] `resolveTechnique()` private function in contract with restricted/first-time/goal logic
- [x] Coroutine-driven elapsed timer in SESSION phase (1Hz tick)
- [x] Pause-aware: `isPaused` halts ticker
- [x] Auto-complete on `elapsed >= totalActiveSeconds` (settle + practiceCap + integrate)
- [x] On complete: persist `MeditationSession` (with `stopped` + `cyclesCompleted`), emit `SessionCompleted` effect, transition to OVERVIEW
- [x] On confirm-stop: `stopped = true` + persist + transition to OVERVIEW
- [x] `lastAnnouncedSubPhase` tracking for chime deduplication on sub-phase boundaries
- [x] Stats loading unchanged
- [x] Bell scheduling deferred to Phase 2 (existing logic OK for now)

### 1.7 New screen composables ✅

- [x] `screens/MeditationWelcomeScreen.kt` (~330 LOC)
  - [x] Top bar with leaf logo + "Breathe" title (`Strings.Meditation.Welcome.topbar`)
  - [x] "Guided breath" pill
  - [x] Display title (multiline) + lede paragraph
  - [x] `AmbientBreathPreview` decorative looping pacer (halo + outer ring + inner circle, marked `hideFromAccessibility()`)
  - [x] "What you can expect" card with 3 bullets (icons: health_and_safety, spa, auto_awesome)
  - [x] Fineprint disclaimer
  - [x] Full-width "Begin" CTA → triggers `NavigateToScreening`
- [x] `screens/MeditationScreeningScreen.kt` (~370 LOC)
  - [x] Top bar with back button + "Safety check" title
  - [x] Display title + lede
  - [x] "Stop anytime" info banner (custom `InfoBanner`)
  - [x] Card with 8 risk flag rows (custom check rows)
  - [x] "None apply" reset row (clears all)
  - [x] Conditional WARN banner when any flag ticked ("Gentle track only")
  - [x] Medical disclaimer fineprint
  - [x] Bottom bar: Back + Continue → triggers `NavigateToConfigure`
- [x] `screens/MeditationConfigureScreen.kt` (~600 LOC)
  - [x] Top bar with back button + "Set up your session" title
  - [x] Display title + conditional "Gentle track" lockpill
  - [x] **Duration card**: 5-button grid (3/5/10/15/20)
  - [x] **Goal card**: 5 tiles with icons (self_improvement, visibility, bedtime, spa, public)
  - [x] **Experience card**: 3 tiles + first-time conditional banner
  - [x] **Audio card**: 3 tiles with icons (record_voice_over, notifications_active, AutoMirrored.Outlined.volume_off)
  - [x] **Technique card**: 2-col grid showing 6 (or 2 in restricted mode) techniques + auto-pill + summary box + 4-cycle-max pill on RELAXATION_478
  - [x] Fineprint with "Re-do safety check" link (AnnotatedString + TextDecoration.Underline)
  - [x] Bottom bar: Back + "Begin breathing" CTA
- [x] `screens/MeditationOverviewScreen.kt` (~370 LOC)
  - [x] Top bar with leaf logo + "Reflection" title
  - [x] "Session complete" pill
  - [x] Conditional title (titleDone vs titleStopped)
  - [x] Lede with technique + duration + optional cycles (parameterized)
  - [x] **Mechanism card** (technique mechanism explainer)
  - [x] **"What you may notice now" card**: 3 bullets (favorite, psychology, bedtime icons)
  - [x] **"With regular practice" card**: title + body
  - [x] **"Try it in daily life" card**: 2 bullets (alarm, warning icons), second bullet uses technique-aware template
  - [x] Medical disclaimer banner
  - [x] Bottom bar: "Different session" + "Redo"

### 1.8 Phase dispatcher — `MeditationScreen.kt` + Session screen ✅

- [x] Reduced to thin dispatcher: `AnimatedContent { when (state.phase) -> ...Screen(...) }`
- [x] SESSION phase uses pure fade transition (breath visual is protagonist), other phases use horizontal slide+fade
- [x] All intents wired through `onIntent` callback
- [x] `MeditationSessionScreen.kt` rewritten (~370 LOC) — Phase 1 baseline: phase label + remaining timer + LinearProgress + AmbientPacer + cue overlay + coach line (AnimatedContent fade) + Stop OutlinedButton + Pause/Resume IconButton + AlertDialog stop confirmation (Phase 2: ModalBottomSheet)
- [x] `MeditationEntryPoint.kt`: removed hardcoded "Sessione salvata!" Toast (silent in Phase 1; Phase 3 will surface inline message in Overview)

### 1.9 Build verification ✅

- [x] `./gradlew :app:assembleDebug` GREEN (29s after fix iteration)
- [x] `./gradlew :features:meditation:compileDebugKotlinAndroid` GREEN (5s clean)
- [x] No regressions in other features
- [x] Grep audit: no hardcoded IT/EN strings in `features/meditation/src/commonMain/` (only KDoc comments)
- [x] Deprecation warnings cleaned: `Icons.Outlined.VolumeOff` → `Icons.AutoMirrored.Outlined.VolumeOff`; `invisibleToUser()` → `hideFromAccessibility()` (×2)

### 1.10 Tracker updates (BEFORE commit) — IN PROGRESS

- [x] Update this file (mark Phase 1 sections as DONE)
- [ ] Update `memory/meditation_redesign.md` (mark Phase 1 done)
- [ ] Update `memory/MEMORY.md` index entry to mention Phase 1 done
- [ ] Update `CLAUDE.md` "Active workstream" section

### 1.11 Commit + push

- [ ] Single commit: `refactor(meditation): Phase 1 — domain + 5-phase wizard + 5 screens + 161 i18n keys × 6 langs`
- [ ] Co-authored-by Claude footer
- [ ] `git push origin backend-architecture-refactor`

---

## Phase 2 — Polish (deferred)

### 2.1 Animated pacer composable

- [ ] `components/BreathingPacer.kt` — halo + 2 rings + circle
- [ ] Per-segment scale animation 0.55..1.0 with smoothstep easing
- [ ] Cue word + count overlay
- [ ] Idle ambient pulse when no pattern (settle/integrate)
- [ ] Match design dimensions (320dp wrap, 280/200/130 dp rings/circle)
- [ ] Sage radial gradient on circle
- [ ] Halo radial gradient pulse

### 2.2 Sub-phase progression UI

- [ ] Settle: ambient pacer, "Arrive" cue, settle coach lines progress through duration
- [ ] Practice: full pacer, technique cycling, technique coach rotates every 12s
- [ ] Integration: ambient pacer fade, "Release" cue, integrate coach lines progress
- [ ] Phase label in top bar updates per sub-phase

### 2.3 Stop confirmation

- [ ] M3 `ModalBottomSheet` (replaces dialog)
- [ ] Title + body + 2 buttons matching design copy
- [ ] Dismiss on outside-tap
- [ ] Pause is implicit while sheet is open

### 2.4 Coach line rotation

- [ ] Practice: 12-second rotation through `technique.coach[3]`
- [ ] Settle/integrate: linear progression through 3 coach lines based on elapsed/total

### 2.5 Keyboard shortcuts

- [ ] Compose `Modifier.onKeyEvent`
- [ ] ESC → request stop
- [ ] SPACE → toggle pause

### 2.6 4-cycle cap for 4-7-8

- [ ] Auto-complete fires at `settle + practiceCap + integrate` where `practiceCap = min(practice, cap × cycleSeconds)` for techniques with `cycleCap`

### 2.7 Overview enhanced

- [ ] All 4 cards rendered with full design copy
- [ ] "Try in daily life" technique-aware copy via parameterized template
- [ ] Cycles rendered when applicable

### 2.8 Build + commit + push

---

## Phase 3 — Production-ready (deferred)

### 3.1 TTS voice guidance

- [ ] `MeditationVoiceCoach` service using Sherpa-ONNX (existing)
- [ ] Cue words at segment start
- [ ] Coach lines at sub-phase transitions
- [ ] Volume ducking
- [ ] Gated behind `audio == VOICE`

### 3.2 Chime sync

- [ ] `MeditationBellPlayer` rings at sub-phase boundaries
- [ ] Optional: per-cycle ring (configurable via Settings)

### 3.3 Reduced-motion

- [ ] Detect `AccessibilityManager.isReduceMotionEnabled` (or settings global)
- [ ] When true: pacer transitions = 200ms; halo no pulse; cue word no animation

### 3.4 TalkBack pass

- [ ] Pacer marked invisibleToUser
- [ ] Cue word + count have `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`
- [ ] Stop button always reachable in focus order
- [ ] Risk checkboxes properly toggled
- [ ] All 5 screens tested

### 3.5 Cross-locale screenshot regression

- [ ] EN/IT/ES/FR/DE/PT screenshots all 5 phases
- [ ] No truncation, alignment OK
- [ ] Diff against design HTML reference

### 3.6 Marketing screenshots

- [ ] Configure + Session phases captured at design quality
- [ ] Ready for Play Store listing

### 3.7 Build + commit + push final

- [ ] Mark Phase 3 DONE in this tracker
- [ ] Update memory + CLAUDE.md to "Meditation Redesign COMPLETE"

---

## Out-of-scope (explicit)

The following are NOT part of this redesign — track separately:
- Meditation streaks / gamification → not in design
- Social sharing of sessions → not in design
- Custom user-defined techniques → not in design
- Background music / soundscape → not in design (chimes only)
- Apple Watch / wearable integration → not in design

If user requests any of these, log in this file as "Future" and split into separate plan.
