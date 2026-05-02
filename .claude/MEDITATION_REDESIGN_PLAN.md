# Meditation Feature Redesign — Strategic Plan

> Companion to `memory/meditation_redesign.md` (long-term memory) and `.claude/MEDITATION_REDESIGN_STATUS.md` (live status). This file is the **strategic plan** — what + how + in what order. Read after the memory file when starting a meditation-redesign session.
>
> **Design source (canonical, do not deviate without recording)**:
> - [`C:\Design\Calmify\Meditation\Calmify Meditation.html`](C:\Design\Calmify\Meditation\Calmify Meditation.html) — 1396 LOC
> - [`C:\Design\Calmify\Meditation\calmify.css`](C:\Design\Calmify\Meditation\calmify.css) — 326 LOC

## Why this plan exists

The meditation redesign is too large for a single session at the user's quality bar (1:1 design fidelity, no hardcoded strings, full a11y, NASA-level rigor). This plan splits the work into **3 phases** with explicit acceptance criteria. Each phase commits + pushes independently and leaves the app in a verified-green state.

## Phase boundary contract

| Phase | Deliverable | Build state | User-visible result |
|---|---|---|---|
| **1** | Domain + Strings + 4 new screens (Welcome/Screening/Configure/Overview skeleton) + state-machine wiring | Green | New 5-phase flow works; Session screen still uses existing animation; Overview is functional but not polished |
| **2** | Session animated pacer (1:1 with design) + Overview enhanced + Stop modal + coach rotation + keyboard | Green | Full design fidelity reached; pacer matches HTML demo |
| **3** | TTS voice + chime sync + reduced-motion + TalkBack + screenshot regression | Green | Production-ready, accessibility-audited, ready for Play Store screenshots |

Each phase is a single commit (or 2-3 atomic commits within the same session) + push. No phase ships partial work.

---

## Phase 1 — Foundation (THIS SESSION)

### Scope

#### 1.1 Domain refactor

File: [`core/util/src/commonMain/kotlin/com/lifo/util/model/MeditationSession.kt`](core/util/src/commonMain/kotlin/com/lifo/util/model/MeditationSession.kt)

```kotlin
// BEFORE: 3 enum entries with displayName: String IT
enum class BreathingPattern(val displayName, ...) { BOX_BREATHING(...), RELAXATION_478(...), DIAPHRAGMATIC(...) }

// AFTER: 6 enum entries, no displayName, with metadata for technique resolution
enum class BreathingPattern(
    val inhaleSeconds: Float,
    val holdInSeconds: Float,
    val exhaleSeconds: Float,
    val holdOutSeconds: Float,
    val cycleCap: Int? = null,        // null = no cap; RELAXATION_478 = 4
    val requiresExperience: Boolean = false,  // true = not for FIRST-time users
    val isGentle: Boolean = false,    // true = allowed in restricted/gentle track
) {
    COHERENT(5.5f, 0f, 5.5f, 0f),
    EXTENDED_EXHALE(4f, 0f, 6f, 0f),
    BOX_BREATHING(4f, 4f, 4f, 4f),
    RELAXATION_478(4f, 7f, 8f, 0f, cycleCap = 4, requiresExperience = true),
    BELLY_NATURAL(0f, 0f, 0f, 0f, isGentle = true),  // no pacer
    BODY_SCAN_NATURAL(0f, 0f, 0f, 0f, isGentle = true);  // no pacer

    val hasPattern: Boolean get() = inhaleSeconds > 0f
    val totalCycleSeconds: Float get() = inhaleSeconds + holdInSeconds + exhaleSeconds + holdOutSeconds
}

enum class MeditationType { TIMER, BREATHING, BODY_SCAN }  // drop displayName
```

Plus new types in same file (or new files in `core/util/.../model/meditation/`):

```kotlin
enum class MeditationGoal {
    STRESS,    // → COHERENT
    ANXIETY,   // → EXTENDED_EXHALE
    SLEEP,     // → RELAXATION_478, fallback EXTENDED_EXHALE if first-time
    FOCUS,     // → BOX_BREATHING
    GROUNDING, // → BODY_SCAN_NATURAL
}

enum class MeditationExperience { FIRST, OCCASIONAL, REGULAR }
enum class MeditationAudio { VOICE, CHIMES, SILENT }

enum class MeditationRiskFlag(val id: String, val hasSubText: Boolean = false) {
    PREGNANCY("pregnancy", hasSubText = true),
    CARDIO("cardio", hasSubText = true),
    RESPIRATORY("respiratory"),
    EPILEPSY("epilepsy"),
    PANIC("panic"),
    RECENT_SURGERY("recent_surgery"),
    EYE("eye"),
    DRIVING("driving"),
}
```

**Migration**: existing Firestore documents have old enum names (`BOX_BREATHING`, `RELAXATION_478`, `DIAPHRAGMATIC`). Map `DIAPHRAGMATIC` → `BELLY_NATURAL` in deserializer (FirestoreMeditationRepository) for backward compat.

#### 1.2 Strings (~150 keys × 6 langs)

Add to `core/ui/src/commonMain/composeResources/values/strings.xml` (and 5 lang siblings).

**Key naming convention**: `meditation_{section}_{element}` — sections: `welcome`, `screening`, `configure`, `session`, `overview`, `technique`, `goal`, `exp`, `audio`, `risk`, `cue`, `coach`.

Full key catalog (drafts — refine in implementation):

```xml
<!-- Welcome (13 keys) -->
<string name="meditation_welcome_topbar">Breathe</string>
<string name="meditation_welcome_pill">Guided breath</string>
<string name="meditation_welcome_title">A few minutes.\nA softer body.</string>
<string name="meditation_welcome_lede">Pick how long you have, what you need, and we\'ll choose a breath technique to match. Always second person, always your pace.</string>
<string name="meditation_welcome_card_title">What you can expect</string>
<string name="meditation_welcome_b1_title">A short safety check first</string>
<string name="meditation_welcome_b1_sub">So we offer only what\'s right for your body today.</string>
<string name="meditation_welcome_b2_title">A guided breath, paced visually</string>
<string name="meditation_welcome_b2_sub">Calming circle, gentle counts, your nose-led breath.</string>
<string name="meditation_welcome_b3_title">A short reflection at the end</string>
<string name="meditation_welcome_b3_sub">What you may notice, and what builds with practice.</string>
<string name="meditation_welcome_fineprint">This is a wellness tool, not medical treatment. If you feel dizzy, lightheaded, tingling, nauseous, or short of breath at any point, stop and breathe naturally.</string>
<string name="meditation_welcome_cta">Begin</string>

<!-- Screening (9 keys + 8 risk × 2) -->
<string name="meditation_screening_topbar">Safety check</string>
<string name="meditation_screening_title">Before we begin.</string>
<string name="meditation_screening_lede">Tick anything that applies. We\'ll keep you on a gentle track if any do — natural-breath only, no pacing or holds.</string>
<string name="meditation_screening_banner_stop">Stop anytime if you feel dizzy, lightheaded, tingling, nauseous, or short of breath. The Stop button is always reachable.</string>
<string name="meditation_screening_banner_stop_lead">Stop anytime</string>
<string name="meditation_screening_none_apply">None of these apply to me right now.</string>
<string name="meditation_screening_none_apply_sub">You can re-screen any time before a session.</string>
<string name="meditation_screening_warn_lead">Gentle track only.</string>
<string name="meditation_screening_warn">Based on what you ticked, we\'ll guide you through natural-breath observation today — no pacing override, no holds. Talk to a clinician about pacing techniques in person.</string>
<string name="meditation_screening_fineprint">Calmify is a wellness tool, not a medical service. This screening helps us avoid breathing techniques that could be unsafe in certain conditions, but it isn\'t a substitute for advice from your clinician.</string>
<string name="meditation_screening_continue">Continue</string>

<string name="meditation_risk_pregnancy">Pregnancy</string>
<string name="meditation_risk_pregnancy_sub">especially first trimester</string>
<string name="meditation_risk_cardio">Cardiovascular concerns</string>
<string name="meditation_risk_cardio_sub">uncontrolled hypertension, recent cardiac event, arrhythmia, heart failure</string>
<string name="meditation_risk_respiratory">Severe asthma, COPD, or active respiratory infection</string>
<string name="meditation_risk_epilepsy">Epilepsy or seizure history</string>
<string name="meditation_risk_panic">Panic disorder with hyperventilation episodes</string>
<string name="meditation_risk_recent_surgery">Recent abdominal, chest, or eye surgery</string>
<string name="meditation_risk_eye">Glaucoma or retinal detachment history</string>
<string name="meditation_risk_driving">Currently driving, operating machinery, or in/near water</string>

<!-- Configure (~25 keys) -->
<string name="meditation_configure_topbar">Set up your session</string>
<string name="meditation_configure_title">How long, what for.</string>
<string name="meditation_configure_lockpill">Gentle track</string>
<string name="meditation_configure_card_duration">Duration</string>
<string name="meditation_configure_card_duration_sub">Pick a length you can give without watching the clock.</string>
<string name="meditation_duration_min_suffix">min</string>
<string name="meditation_configure_card_goal">What you\'re here for</string>
<string name="meditation_configure_card_goal_sub">We\'ll match a technique to your goal — you can change it below.</string>
<string name="meditation_goal_stress">Stress relief</string>
<string name="meditation_goal_focus">Focus</string>
<string name="meditation_goal_sleep">Sleep</string>
<string name="meditation_goal_anxiety">Anxiety</string>
<string name="meditation_goal_grounding">Grounding</string>
<string name="meditation_configure_card_experience">How familiar with breathwork are you?</string>
<string name="meditation_exp_first">First time</string>
<string name="meditation_exp_occasional">Occasional</string>
<string name="meditation_exp_regular">Regular</string>
<string name="meditation_configure_first_time_banner">Welcome. We\'ll start you on coherent breathing — equal, gentle breaths, easiest on the body.</string>
<string name="meditation_configure_card_audio">Audio</string>
<string name="meditation_audio_voice">Voice guidance</string>
<string name="meditation_audio_chimes">Chimes only</string>
<string name="meditation_audio_silent">Silent</string>
<string name="meditation_configure_card_technique">Technique</string>
<string name="meditation_configure_tech_sub_overridable">We picked this for you. Tap to swap if you prefer another.</string>
<string name="meditation_configure_tech_sub_restricted">Locked to the gentle track today.</string>
<string name="meditation_configure_tech_sub_first_time">First-timers stay on coherent breathing.</string>
<string name="meditation_configure_tech_auto_pill">Auto</string>
<string name="meditation_configure_tech_478_cap">4 cycles max</string>
<string name="meditation_configure_fineprint">Wellness tool, not medical care. Stop and breathe naturally if anything feels off.</string>
<string name="meditation_configure_redo_screening_link">Re-do safety check</string>
<string name="meditation_configure_cta">Begin breathing</string>

<!-- Techniques (6 × 6 fields = 36) -->
<string name="meditation_tech_coherent_name">Coherent breathing</string>
<string name="meditation_tech_coherent_short">5.5 in · 5.5 out</string>
<string name="meditation_tech_coherent_summary">Equal, gentle breaths around 5.5 per minute — nervous-system reset.</string>
<string name="meditation_tech_coherent_mechanism">At about 5.5 breaths a minute, heart rate variability rises and the vagus nerve signals safety, easing the body out of stress mode.</string>
<string name="meditation_tech_coherent_coach1">Through the nose. Belly soft, shoulders quiet.</string>
<string name="meditation_tech_coherent_coach2">Let the breath find a smooth, even rhythm.</string>
<string name="meditation_tech_coherent_coach3">If your body wants more air, take more air.</string>

<string name="meditation_tech_exhale_name">Extended exhale</string>
<string name="meditation_tech_exhale_short">4 in · 6 out</string>
<string name="meditation_tech_exhale_summary">Longer exhales than inhales — the most reliable way to downshift.</string>
<string name="meditation_tech_exhale_mechanism">A longer exhale activates the parasympathetic ("rest and digest") branch of your nervous system, slowing heart rate and softening tension.</string>
<string name="meditation_tech_exhale_coach1">Inhale through the nose. Exhale slowly through the nose or softly through pursed lips.</string>
<string name="meditation_tech_exhale_coach2">Let the exhale be unhurried — never forced.</string>
<string name="meditation_tech_exhale_coach3">Belly rises on inhale; releases on exhale.</string>

<string name="meditation_tech_box_name">Box breathing</string>
<string name="meditation_tech_box_short">4 · 4 · 4 · 4</string>
<string name="meditation_tech_box_summary">Four-count square — steady, focused, used by clinicians and pilots.</string>
<string name="meditation_tech_box_mechanism">Equal, contained intervals create a metronome the mind can follow, narrowing attention and calming reactivity.</string>
<string name="meditation_tech_box_coach1">Holds are gentle pauses, not breath-holding contests.</string>
<string name="meditation_tech_box_coach2">If a hold feels too long, shorten it — the rhythm is yours.</string>
<string name="meditation_tech_box_coach3">Through the nose, soft and even.</string>

<string name="meditation_tech_478_name">4-7-8 breath</string>
<string name="meditation_tech_478_short">4 in · 7 hold · 8 out</string>
<string name="meditation_tech_478_summary">Dr. Weil\'s pattern. Strong parasympathetic effect — capped at 4 cycles.</string>
<string name="meditation_tech_478_mechanism">A long hold and longer exhale amplify the vagal response. Even a few cycles can noticeably slow your pulse.</string>
<string name="meditation_tech_478_coach1">Inhale silently through the nose.</string>
<string name="meditation_tech_478_coach2">Exhale slowly through the mouth, lips slightly pursed.</string>
<string name="meditation_tech_478_coach3">Four cycles is plenty. If you feel lightheaded, return to natural breath.</string>

<string name="meditation_tech_belly_name">Belly breathing</string>
<string name="meditation_tech_belly_short">Diaphragmatic, no count</string>
<string name="meditation_tech_belly_summary">Soft, deep breath into the belly. No pacing — just steady awareness.</string>
<string name="meditation_tech_belly_mechanism">Engaging the diaphragm instead of the chest deepens oxygen exchange and signals safety to the body.</string>
<string name="meditation_tech_belly_coach1">One hand on your belly, one on your chest.</string>
<string name="meditation_tech_belly_coach2">On the inhale, only the belly hand should rise.</string>
<string name="meditation_tech_belly_coach3">No counting. Let the rhythm be yours.</string>

<string name="meditation_tech_scan_name">Body scan</string>
<string name="meditation_tech_scan_short">Natural breath, no count</string>
<string name="meditation_tech_scan_summary">Sweep attention through the body. Breath stays exactly as it is.</string>
<string name="meditation_tech_scan_mechanism">Naming sensation without changing it interrupts rumination and grounds you in the present moment.</string>
<string name="meditation_tech_scan_coach1">Notice your feet. Your legs. Your hips.</string>
<string name="meditation_tech_scan_coach2">Belly. Chest. Shoulders. Jaw.</string>
<string name="meditation_tech_scan_coach3">Don\'t change anything. Just notice.</string>

<!-- Session (~18 keys) -->
<string name="meditation_session_phase_settling">Settling</string>
<string name="meditation_session_phase_practice">Practice</string>
<string name="meditation_session_phase_integration">Integration</string>
<string name="meditation_cue_breathe_in">Breathe in</string>
<string name="meditation_cue_hold">Hold</string>
<string name="meditation_cue_breathe_out">Breathe out</string>
<string name="meditation_cue_arrive">Arrive</string>
<string name="meditation_cue_release">Release</string>
<string name="meditation_cue_breathe">Breathe</string>
<string name="meditation_settle_coach1">Find a comfortable seat. Relax your shoulders, soften your jaw.</string>
<string name="meditation_settle_coach2">Let your eyes rest, or close them. Notice your natural breath, without changing it.</string>
<string name="meditation_settle_coach3">No pacing yet. Just arriving, here.</string>
<string name="meditation_integrate_coach1">Let the pacing go. Breathe how your body wants to.</string>
<string name="meditation_integrate_coach2">Notice what\'s shifted — a softer chest, a slower thought, anything at all.</string>
<string name="meditation_integrate_coach3">When you\'re ready, slowly open your eyes. Return to the room.</string>
<string name="meditation_session_paused">Paused</string>
<string name="meditation_session_stop_button">Stop</string>
<string name="meditation_a11y_pause">Pause</string>
<string name="meditation_a11y_resume">Resume</string>
<string name="meditation_a11y_stop_session">Stop session</string>
<string name="meditation_session_audio_voice">voice</string>
<string name="meditation_session_audio_chimes">chimes</string>

<!-- Stop modal (4 keys) -->
<string name="meditation_stop_title">End the session?</string>
<string name="meditation_stop_body">You can stop any time. We\'ll show a short reflection on what you completed so far.</string>
<string name="meditation_stop_keep">Keep breathing</string>
<string name="meditation_stop_end">End session</string>

<!-- Overview (~18 keys) -->
<string name="meditation_overview_topbar">Reflection</string>
<string name="meditation_overview_pill">Session complete</string>
<string name="meditation_overview_title_done">Done.\nWell done.</string>
<string name="meditation_overview_title_stopped">You stopped early.\nThat counts too.</string>
<string name="meditation_overview_lede_with_cycles">%1$s · %2$s · %3$d cycles</string>
<string name="meditation_overview_lede_no_cycles">%1$s · %2$s</string>
<string name="meditation_overview_card_notice_title">What you may notice now</string>
<string name="meditation_overview_notice_b1">A slower pulse, or a softer chest and shoulders.</string>
<string name="meditation_overview_notice_b2">A small gap between thought and reaction.</string>
<string name="meditation_overview_notice_b3">A clearer signal when your body is ready to rest.</string>
<string name="meditation_overview_card_practice_title">With regular practice (4–8 weeks)</string>
<string name="meditation_overview_card_practice_body">People who practice daily often report calmer baseline mood, steadier sleep, and a lower stress response in everyday situations. Frequency matters more than duration — a few minutes daily beats a long session weekly.</string>
<string name="meditation_overview_card_daily_title">Try it in daily life</string>
<string name="meditation_overview_daily_b1_title">Pair it with a habit you already have.</string>
<string name="meditation_overview_daily_b1_sub">Three breaths after you sit down at your desk, or before you check your phone.</string>
<string name="meditation_overview_daily_b2_title">Use it when stress hits.</string>
<string name="meditation_overview_daily_b2_sub_template">Two minutes of %1$s before a hard conversation can change how it lands.</string>
<string name="meditation_overview_banner">This is a wellness tool, not medical treatment. Persistent anxiety, panic, sleep issues, or breathing difficulty warrant a conversation with a clinician.</string>
<string name="meditation_overview_btn_different">Different session</string>
<string name="meditation_overview_btn_redo">Redo</string>

<!-- Time formatting (3) -->
<string name="meditation_time_minutes_only">%1$d min</string>
<string name="meditation_time_min_sec">%1$d min %2$ds</string>
<string name="meditation_time_seconds_only">%1$ds</string>
```

**Total**: ~145 unique keys. Add same keys with quality translations to IT/ES/FR/DE/PT.

#### 1.3 Strings facade

Add `Strings.Meditation` group (large, ~150 entries). Sub-objects per section: `Welcome`, `Screening`, `Configure`, `Session`, `Overview`, `Technique`, `Goal`, `Experience`, `Audio`, `Risk`, `Cue`, `Coach`, `Time`.

#### 1.4 Contract refactor

```kotlin
object MeditationContract {
    sealed interface Intent : MviContract.Intent {
        data object NavigateToScreening : Intent
        data object NavigateToConfigure : Intent
        data class ToggleRiskFlag(val flag: MeditationRiskFlag) : Intent
        data object ClearAllRisks : Intent

        data class SetDuration(val minutes: Int) : Intent
        data class SetGoal(val goal: MeditationGoal) : Intent
        data class SetExperience(val exp: MeditationExperience) : Intent
        data class SetAudio(val audio: MeditationAudio) : Intent
        data class SetTechniqueOverride(val pattern: BreathingPattern?) : Intent

        data object StartSession : Intent
        data object PauseSession : Intent
        data object ResumeSession : Intent
        data object RequestStopSession : Intent  // shows modal
        data object ConfirmStopSession : Intent
        data object DismissStopDialog : Intent

        data object NavigateBackFromScreening : Intent  // → Welcome
        data object NavigateBackFromConfigure : Intent  // → Screening
        data object NavigateRedoFromOverview : Intent   // → Session (same config)
        data object NavigateDifferentFromOverview : Intent  // → Configure
        data object LoadStats : Intent
    }

    data class State(
        val phase: SessionPhase = SessionPhase.WELCOME,
        val risks: Set<MeditationRiskFlag> = emptySet(),
        val config: SessionConfig = SessionConfig(),
        val session: SessionRuntime? = null,
        val showStopDialog: Boolean = false,
        val totalMinutes: Int = 0,
        val sessionCount: Int = 0,
        val recentSessions: List<MeditationSession> = emptyList(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    ) : MviContract.State {
        val restricted: Boolean get() = risks.isNotEmpty()
        val effectiveTechnique: BreathingPattern get() = resolveTechnique(this)
    }

    data class SessionConfig(
        val duration: Int = 5,                 // minutes
        val goal: MeditationGoal = MeditationGoal.STRESS,
        val experience: MeditationExperience = MeditationExperience.OCCASIONAL,
        val audio: MeditationAudio = MeditationAudio.CHIMES,
        val techniqueOverride: BreathingPattern? = null,
    )

    data class SessionRuntime(
        val technique: BreathingPattern,
        val durationSeconds: Int,
        val elapsedSeconds: Int = 0,
        val isPaused: Boolean = false,
        val cyclesCompleted: Int = 0,
        val stopped: Boolean = false,
    ) {
        val settleSeconds: Int get() = maxOf(20, (durationSeconds * 0.15f).toInt())
        val integrateSeconds: Int get() = maxOf(20, (durationSeconds * 0.15f).toInt())
        val practiceSeconds: Int get() = durationSeconds - settleSeconds - integrateSeconds
        val subPhase: SubPhase get() = when {
            elapsedSeconds < settleSeconds -> SubPhase.SETTLING
            elapsedSeconds >= settleSeconds + practiceSeconds -> SubPhase.INTEGRATION
            else -> SubPhase.PRACTICE
        }
    }

    enum class SessionPhase { WELCOME, SCREENING, CONFIGURE, SESSION, OVERVIEW }
    enum class SubPhase { SETTLING, PRACTICE, INTEGRATION }

    sealed interface Effect : MviContract.Effect {
        data object SessionCompleted : Effect
        data object SessionSaved : Effect
        data class Error(val message: String) : Effect
        data object PlayBell : Effect
    }
}

private fun resolveTechnique(state: MeditationContract.State): BreathingPattern {
    if (state.config.techniqueOverride != null && !state.restricted && state.config.experience != MeditationExperience.FIRST) {
        return state.config.techniqueOverride
    }
    if (state.restricted) return BreathingPattern.BELLY_NATURAL
    if (state.config.experience == MeditationExperience.FIRST) return BreathingPattern.COHERENT
    return when (state.config.goal) {
        MeditationGoal.STRESS -> BreathingPattern.COHERENT
        MeditationGoal.ANXIETY -> BreathingPattern.EXTENDED_EXHALE
        MeditationGoal.SLEEP -> BreathingPattern.RELAXATION_478
        MeditationGoal.FOCUS -> BreathingPattern.BOX_BREATHING
        MeditationGoal.GROUNDING -> BreathingPattern.BODY_SCAN_NATURAL
    }
}
```

#### 1.5 ViewModel refactor

Same intents wired. Drive elapsed seconds with a coroutine `flow { while (isActive && !isPaused) { delay(1000); emit(++sec) } }` instead of rAF (Compose-friendly). Bell scheduling stays via existing `MeditationBellPlayer`. Stats loading unchanged.

#### 1.6 New screen composables

Create files:
- `features/meditation/src/commonMain/kotlin/com/lifo/meditation/screens/MeditationWelcomeScreen.kt`
- `.../screens/MeditationScreeningScreen.kt`
- `.../screens/MeditationConfigureScreen.kt`
- `.../screens/MeditationOverviewScreen.kt`

Each ~200-300 LOC. Use Material3 + design tokens (sage colors already in theme).

#### 1.7 Phase dispatcher

Refactor `MeditationScreen.kt` to be a thin dispatcher:

```kotlin
when (state.phase) {
    SessionPhase.WELCOME -> MeditationWelcomeScreen(...)
    SessionPhase.SCREENING -> MeditationScreeningScreen(...)
    SessionPhase.CONFIGURE -> MeditationConfigureScreen(...)
    SessionPhase.SESSION -> MeditationSessionScreen(...)  // existing logic, rewired
    SessionPhase.OVERVIEW -> MeditationOverviewScreen(...)
}
```

#### 1.8 Build verify

`./gradlew :app:assembleDebug` GREEN before commit.

#### 1.9 Commit + push

Single commit: `refactor(meditation): Phase 1 — domain + 5-phase wizard + screens (Welcome/Screening/Configure/Overview) + ~150 keys × 6 langs`.

### Phase 1 acceptance checklist

See `.claude/MEDITATION_REDESIGN_STATUS.md` for the live checklist.

---

## Phase 2 — Polish (NEXT SESSION)

### Scope

#### 2.1 New animated pacer composable

`features/meditation/src/commonMain/kotlin/com/lifo/meditation/components/BreathingPacer.kt`:

```kotlin
@Composable
fun BreathingPacer(
    technique: BreathingPattern,
    elapsedSeconds: Float,  // continuous, sub-second precision
    isInPractice: Boolean,
    modifier: Modifier = Modifier,
) {
    // Halo (radial gradient) + outer ring + mid ring + circle (radial gradient)
    // Scale animation with smoothstep easing per segment
    // Cue word + count overlay (Center)
}
```

Animation timing: per-segment via `animateFloatAsState` with `tween(seg.s * 1000, easing = EaseInOut)`.

Match design 1:1: 320dp wrap, 280dp outer ring, 200dp mid ring, 130dp circle. Sage radial gradient.

#### 2.2 Sub-phase progression

In `MeditationSessionScreen.kt`:
- Settle (15%): no pacer, slow ambient pulse, "Arrive" cue, 3 settle coach lines progressing through duration
- Practice (70%): full pacer, technique cycling, technique coach rotating every 12s
- Integration (15%): pacer fades to ambient, "Release" cue, 3 integrate coach lines progressing
- Cycle counter visible in bottom meta when technique has pattern

#### 2.3 Stop confirmation BottomSheet

Replace dialog with M3 `ModalBottomSheet`:
- Title: "End the session?"
- Body: "You can stop any time. We'll show a short reflection on what you completed so far."
- Two buttons: "Keep breathing" (text button) / "End session" (primary)

#### 2.4 Coach line rotation

Practice: rotate every 12s through `technique.coach[3]`. Settle/integrate: progress through 3 lines linearly with elapsed time.

#### 2.5 Keyboard shortcuts

Compose `Modifier.onKeyEvent`:
- ESC → request stop
- SPACE → toggle pause

#### 2.6 Overview enhanced

All 4 cards rendered with mechanism + 3 notice bullets + practice card + 2 daily-life bullets (technique-aware via `daily_b2_sub_template`).

#### 2.7 4-cycle cap for 4-7-8

In `SessionRuntime`: `practiceCapSeconds = if (technique.cycleCap != null) min(practiceSeconds, technique.cycleCap * technique.totalCycleSeconds) else practiceSeconds`. Auto-complete fires when elapsed reaches `settle + practiceCap + integrate`.

### Phase 2 acceptance checklist

See `.claude/MEDITATION_REDESIGN_STATUS.md`.

---

## Phase 3 — Production-ready (NEXT NEXT SESSION)

### Scope

#### 3.1 TTS voice guidance

Reuse Sherpa-ONNX TTS pipeline from `features/chat/audio`. New service `MeditationVoiceCoach` plays:
- Cue words at segment boundaries ("Breathe in" at segment start)
- Coach lines at sub-phase transitions
- Low volume, optional ducking

Gate behind `config.audio == VOICE`.

#### 3.2 Chime sync

Existing `MeditationBellPlayer` rings:
- Phase boundaries (settle → practice, practice → integration)
- Optional: every cycle for box/coherent (configurable)

#### 3.3 Reduced-motion verification

Detect `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` or AccessibilityManager.isReduceMotionEnabled. When true: pacer uses 200ms transitions only, no halo pulse, cue word change without animation.

#### 3.4 TalkBack pass

Test all 5 screens with TalkBack ON. Verify:
- Pacer is announced as "Decorative" (contentDescription = null with `Modifier.semantics { invisibleToUser() }`)
- Cue word + count are read aloud at change (`Modifier.semantics { liveRegion = LiveRegionMode.Polite }`)
- Stop button always reachable in focus order
- Risk checkboxes are properly toggled with TalkBack

#### 3.5 Cross-locale screenshot regression

Capture screens in EN/IT/ES/FR/DE/PT. Verify no truncation, alignment, RTL prep (AR is not Latin but layout should be ready).

#### 3.6 Final polish + Play Store screenshots

Generate marketing screenshots from Configure + Session phases for Play Store.

### Phase 3 acceptance checklist

See `.claude/MEDITATION_REDESIGN_STATUS.md`.

---

## Risks / unknowns

1. **Existing Firestore docs deserialization** after `BreathingPattern.DIAPHRAGMATIC` → `BELLY_NATURAL` rename. **Mitigation**: read-side mapper that accepts both names; write-side always emits new name.
2. **Animated pacer performance** on lower-end Android. **Mitigation**: profile with `rememberCoroutineScope` + `withFrameNanos` driver; fallback to `tween` if jank.
3. **Translation quality drift** for evidence-based mechanism copy. **Mitigation**: review IT translation against breathwork research; use UN-style careful wording for medical disclaimers.
4. **Existing meditation entry points** (Garden, Percorso, FAB) still launch the old screen. **Mitigation**: navigation contract unchanged (`onMeditationClick`); 5-phase flow lives within the same destination.
5. **Stats migration**: existing `MeditationSession` records have `type=BREATHING/TIMER/BODY_SCAN`. New flow always writes `BREATHING` (every session is technique-backed). **Mitigation**: keep enum, just always write BREATHING + actual technique pattern.

## Quality gates (every commit)

1. `./gradlew :app:assembleDebug` GREEN
2. Zero hardcoded user-facing strings (grep audit)
3. All 6 Latin XMLs in sync (no missing key per lang)
4. Strings facade compiles (no orphan keys)
5. Existing meditation entry points still navigate correctly
6. Trackers updated (this file + STATUS + memory) BEFORE commit per `feedback_update_all_trackers.md`
