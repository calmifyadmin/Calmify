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
| 1 — Foundation | NOT STARTED | — | — | — | Domain + Strings + 4 new screens + state-machine wiring |
| 2 — Polish | NOT STARTED | — | — | — | Session pacer + Stop modal + Overview + keyboard |
| 3 — Production | NOT STARTED | — | — | — | TTS + chime sync + a11y + screenshot regression |

---

## Phase 1 — Foundation (active)

Target: ~6h dedicated session. Single (or 2-3 atomic) commit + push.

### 1.1 Domain refactor — `core/util/.../MeditationSession.kt`

- [ ] `BreathingPattern` enum: drop `displayName: String` field
- [ ] `BreathingPattern` enum: expand from 3 → 6 entries:
  - [ ] `COHERENT` (5.5/0/5.5/0)
  - [ ] `EXTENDED_EXHALE` (4/0/6/0)
  - [ ] `BOX_BREATHING` (4/4/4/4)  *kept, unchanged*
  - [ ] `RELAXATION_478` (4/7/8/0, cap=4, requiresExperience)  *kept, add metadata*
  - [ ] `BELLY_NATURAL` (0/0/0/0, isGentle)  *replaces DIAPHRAGMATIC*
  - [ ] `BODY_SCAN_NATURAL` (0/0/0/0, isGentle)
- [ ] Add fields: `cycleCap: Int?`, `requiresExperience: Boolean`, `isGentle: Boolean`
- [ ] Add computed: `hasPattern: Boolean`
- [ ] `MeditationType` enum: drop `displayName`
- [ ] **Backward-compat mapper** in FirestoreMeditationRepository: `DIAPHRAGMATIC` → `BELLY_NATURAL` on read

### 1.2 New domain types

- [ ] `MeditationGoal` enum (5 entries: STRESS/ANXIETY/SLEEP/FOCUS/GROUNDING) in `core/util/.../model/`
- [ ] `MeditationExperience` enum (3 entries: FIRST/OCCASIONAL/REGULAR)
- [ ] `MeditationAudio` enum (3 entries: VOICE/CHIMES/SILENT)
- [ ] `MeditationRiskFlag` enum (8 entries with `id: String` + `hasSubText: Boolean`)
- [ ] All 4 enums: `@Serializable`, no displayName fields

### 1.3 Strings XML — 6 Latin langs

- [ ] `values/strings.xml` (EN default) — ~145 keys added
- [ ] `values-it/strings.xml` — same keys, IT translation
- [ ] `values-es/strings.xml` — same keys, ES translation
- [ ] `values-fr/strings.xml` — same keys, FR translation
- [ ] `values-de/strings.xml` — same keys, DE translation
- [ ] `values-pt/strings.xml` — same keys, PT translation
- [ ] Verify same key count per lang (script: `grep -c '<string ' values{,-it,-es,-fr,-de,-pt}/strings.xml`)

Section breakdown (~145 keys total):
- [ ] Welcome (13)
- [ ] Screening (9 chrome + 8 risks × 2 = 25)
- [ ] Configure (~25)
- [ ] Techniques (6 × 6 fields = 36)
- [ ] Session (~18)
- [ ] Stop modal (4)
- [ ] Overview (~18)
- [ ] Time formatting (3)

### 1.4 Strings facade — `Strings.kt`

- [ ] Add `Strings.Meditation` group with sub-objects:
  - [ ] `Welcome` — 13 entries
  - [ ] `Screening` — 9 entries
  - [ ] `Configure` — ~25 entries
  - [ ] `Technique` — 6 sub-objects (one per technique) × 6 fields each = 36 entries
  - [ ] `Session` — ~18 entries
  - [ ] `Stop` — 4 entries
  - [ ] `Overview` — ~18 entries
  - [ ] `Goal` — 5 entries
  - [ ] `Experience` — 3 entries
  - [ ] `Audio` — 3 entries
  - [ ] `Risk` — 8 sub-objects (label + sub) = 16 entries
  - [ ] `Cue` — 6 entries (BreatheIn/Hold/BreatheOut/Arrive/Release/Breathe)
  - [ ] `Coach` — 6 entries (3 settle + 3 integrate)
  - [ ] `Time` — 3 entries
- [ ] Helper: `BreathingPattern.nameRes()` `.shortRes()` `.summaryRes()` `.mechanismRes()` `.coachRes(idx)` extension functions in `core/ui` (since `core/util` enum has no StringResource)
- [ ] Helper: `MeditationGoal.labelRes()`, `MeditationExperience.labelRes()`, `MeditationAudio.labelRes()`, `MeditationRiskFlag.labelRes()` + `subRes()`

### 1.5 Contract refactor — `MeditationContract.kt`

- [ ] `SessionPhase` enum: 3 entries → 5 entries (WELCOME/SCREENING/CONFIGURE/SESSION/OVERVIEW)
- [ ] Add `SubPhase` enum (SETTLING/PRACTICE/INTEGRATION)
- [ ] Add `SessionConfig` data class (duration/goal/experience/audio/techniqueOverride)
- [ ] Add `SessionRuntime` data class (technique/durationSeconds/elapsed/isPaused/cycles/stopped + computed sub-phase/settle/integrate/practice)
- [ ] State: replace fields with `phase / risks / config / session: SessionRuntime? / showStopDialog`
- [ ] Computed: `restricted: Boolean` (risks not empty), `effectiveTechnique: BreathingPattern` (resolver function)
- [ ] Drop `BreathingPhase` enum (replaced by per-segment in SessionRuntime)
- [ ] Add intents: `NavigateToScreening / ToggleRiskFlag / ClearAllRisks / SetDuration / SetGoal / SetExperience / SetAudio / SetTechniqueOverride / RequestStopSession / ConfirmStopSession / DismissStopDialog / NavigateBackFrom* / NavigateRedoFromOverview / NavigateDifferentFromOverview`
- [ ] Drop unused intents: old `SelectType` etc.

### 1.6 ViewModel refactor — `MeditationViewModel.kt`

- [ ] Wire all new intents to state mutations
- [ ] `resolveTechnique()` private function with restricted/first-time/goal logic
- [ ] Coroutine-driven elapsed timer in SESSION phase (1Hz tick)
- [ ] Pause-aware: `isPaused` halts ticker
- [ ] Auto-complete on `elapsed >= settle + practiceCap + integrate`
- [ ] On complete: persist `MeditationSession` record (existing repository), emit `SessionCompleted` effect, transition to OVERVIEW with last `SessionRuntime`
- [ ] On confirm-stop: `stopped = true` + persist + transition to OVERVIEW
- [ ] Stats loading unchanged
- [ ] Bell scheduling deferred to Phase 2 (existing logic OK for now)

### 1.7 New screen composables

- [ ] `features/meditation/src/commonMain/kotlin/com/lifo/meditation/screens/MeditationWelcomeScreen.kt`
  - [ ] Top bar with leaf logo + "Breathe" title (Strings.Meditation.Welcome.topbar)
  - [ ] "Guided breath" pill
  - [ ] Display title (multiline)
  - [ ] Lede paragraph
  - [ ] Decorative animated pacer preview (smaller, looping)
  - [ ] "What you can expect" card with 3 bullets (icons: health_and_safety, spa, auto_awesome)
  - [ ] Fineprint disclaimer
  - [ ] "Begin" CTA → triggers `NavigateToScreening`
- [ ] `screens/MeditationScreeningScreen.kt`
  - [ ] Top bar with back button + "Safety check" title
  - [ ] Display title + lede
  - [ ] "Stop anytime" info banner (M3 SuggestionBox or custom)
  - [ ] Card with 8 risk flag checkboxes (M3 ListItem with Checkbox)
  - [ ] "None apply" reset row (clears all)
  - [ ] Conditional warn banner when any flag ticked ("Gentle track only")
  - [ ] Medical disclaimer fineprint
  - [ ] Bottom bar: Back + Continue
  - [ ] Continue triggers `NavigateToConfigure`
- [ ] `screens/MeditationConfigureScreen.kt`
  - [ ] Top bar with back button + "Set up your session" title
  - [ ] Display title + conditional "Gentle track" lockpill
  - [ ] **Duration card**: 5-button grid (3/5/10/15/20 + "min" label)
  - [ ] **Goal card**: 5-button grid with icons (self_improvement, visibility, bedtime, spa, public)
  - [ ] **Experience card**: 3-button grid + first-time conditional banner
  - [ ] **Audio card**: 3-button grid with icons (record_voice_over, notifications_active, volume_off)
  - [ ] **Technique card**: 2-column grid showing 6 techniques (or 2 in restricted mode), with auto-pill on top + selected technique summary box at bottom + 4-cycle-max pill on RELAXATION_478
  - [ ] Fineprint with "Re-do safety check" link
  - [ ] Bottom bar: Back + "Begin breathing" CTA
- [ ] `screens/MeditationOverviewScreen.kt`
  - [ ] Top bar with leaf logo + "Reflection" title
  - [ ] "Session complete" pill
  - [ ] Conditional title (success vs stopped)
  - [ ] Lede with technique name + duration + cycles (parameterized)
  - [ ] **Mechanism card**: technique name + mechanism explainer
  - [ ] **"What you may notice now" card**: 3 bullets (favorite, psychology, bedtime icons)
  - [ ] **"With regular practice" card**: title + body
  - [ ] **"Try it in daily life" card**: 2 bullets (alarm, warning icons), second bullet sub uses `daily_b2_sub_template` with technique name
  - [ ] Medical disclaimer banner
  - [ ] Bottom bar: "Different session" + "Redo"

### 1.8 Phase dispatcher — `MeditationScreen.kt`

- [ ] Reduce to thin dispatcher: `when (state.phase) -> ...Screen(...)`
- [ ] Existing SETUP/ACTIVE/COMPLETED logic moved into `MeditationSessionScreen.kt` (kept as-is for Phase 1, polished in Phase 2)
- [ ] Wire all new intents

### 1.9 Build verification

- [ ] `./gradlew :app:assembleDebug` GREEN
- [ ] `./gradlew :features:meditation:compileDebugKotlinAndroid` GREEN
- [ ] No regressions in other features (smoke compile of `:features:home`, `:features:write`)
- [ ] Grep audit: no hardcoded IT strings in `features/meditation/`

### 1.10 Tracker updates (BEFORE commit)

- [ ] Update this file (mark Phase 1 sections as DONE)
- [ ] Update `memory/meditation_redesign.md` if any decision changed
- [ ] Update `memory/MEMORY.md` index entry to point to redesign tracker
- [ ] Update `CLAUDE.md` "Active workstream" section
- [ ] Update `.claude/MEDITATION_REDESIGN_PLAN.md` with any deviations

### 1.11 Commit + push

- [ ] Single commit: `refactor(meditation): Phase 1 — domain + 5-phase wizard + screens (Welcome/Screening/Configure/Overview)`
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
