package com.lifo.ui.i18n

import com.lifo.util.model.BreathSegmentKind
import com.lifo.util.model.BreathingPattern
import com.lifo.util.model.MeditationAudio
import com.lifo.util.model.MeditationExperience
import com.lifo.util.model.MeditationGoal
import com.lifo.util.model.MeditationRiskFlag
import org.jetbrains.compose.resources.StringResource

/**
 * Extension bridges between domain enums (in `core/util`) and localized string
 * resources (in `core/ui`). Keep these in `core/ui` because `core/util` cannot
 * import resource APIs without creating a circular dependency.
 *
 * UI sites resolve labels via `stringResource(pattern.nameRes)` instead of the
 * old `pattern.displayName` field that was dropped during the meditation
 * redesign (Phase 1, 2026-05-02).
 */

// ── BreathingPattern → display fields ─────────────────────────────────────

val BreathingPattern.nameRes: StringResource
    get() = when (this) {
        BreathingPattern.COHERENT -> Strings.Meditation.Technique.coherentName
        BreathingPattern.EXTENDED_EXHALE -> Strings.Meditation.Technique.exhaleName
        BreathingPattern.BOX_BREATHING -> Strings.Meditation.Technique.boxName
        BreathingPattern.RELAXATION_478 -> Strings.Meditation.Technique.w478Name
        BreathingPattern.BELLY_NATURAL -> Strings.Meditation.Technique.bellyName
        BreathingPattern.BODY_SCAN_NATURAL -> Strings.Meditation.Technique.scanName
    }

val BreathingPattern.shortRes: StringResource
    get() = when (this) {
        BreathingPattern.COHERENT -> Strings.Meditation.Technique.coherentShort
        BreathingPattern.EXTENDED_EXHALE -> Strings.Meditation.Technique.exhaleShort
        BreathingPattern.BOX_BREATHING -> Strings.Meditation.Technique.boxShort
        BreathingPattern.RELAXATION_478 -> Strings.Meditation.Technique.w478Short
        BreathingPattern.BELLY_NATURAL -> Strings.Meditation.Technique.bellyShort
        BreathingPattern.BODY_SCAN_NATURAL -> Strings.Meditation.Technique.scanShort
    }

val BreathingPattern.summaryRes: StringResource
    get() = when (this) {
        BreathingPattern.COHERENT -> Strings.Meditation.Technique.coherentSummary
        BreathingPattern.EXTENDED_EXHALE -> Strings.Meditation.Technique.exhaleSummary
        BreathingPattern.BOX_BREATHING -> Strings.Meditation.Technique.boxSummary
        BreathingPattern.RELAXATION_478 -> Strings.Meditation.Technique.w478Summary
        BreathingPattern.BELLY_NATURAL -> Strings.Meditation.Technique.bellySummary
        BreathingPattern.BODY_SCAN_NATURAL -> Strings.Meditation.Technique.scanSummary
    }

val BreathingPattern.mechanismRes: StringResource
    get() = when (this) {
        BreathingPattern.COHERENT -> Strings.Meditation.Technique.coherentMechanism
        BreathingPattern.EXTENDED_EXHALE -> Strings.Meditation.Technique.exhaleMechanism
        BreathingPattern.BOX_BREATHING -> Strings.Meditation.Technique.boxMechanism
        BreathingPattern.RELAXATION_478 -> Strings.Meditation.Technique.w478Mechanism
        BreathingPattern.BELLY_NATURAL -> Strings.Meditation.Technique.bellyMechanism
        BreathingPattern.BODY_SCAN_NATURAL -> Strings.Meditation.Technique.scanMechanism
    }

/**
 * Per-technique coaching lines. Each technique has 3 (`idx` 0..2). Caller
 * decides rotation cadence (e.g. 12s in practice phase per the design).
 * Returns null for out-of-range index.
 */
fun BreathingPattern.coachRes(idx: Int): StringResource? = when (this) {
    BreathingPattern.COHERENT -> when (idx) {
        0 -> Strings.Meditation.Technique.coherentCoach1
        1 -> Strings.Meditation.Technique.coherentCoach2
        2 -> Strings.Meditation.Technique.coherentCoach3
        else -> null
    }
    BreathingPattern.EXTENDED_EXHALE -> when (idx) {
        0 -> Strings.Meditation.Technique.exhaleCoach1
        1 -> Strings.Meditation.Technique.exhaleCoach2
        2 -> Strings.Meditation.Technique.exhaleCoach3
        else -> null
    }
    BreathingPattern.BOX_BREATHING -> when (idx) {
        0 -> Strings.Meditation.Technique.boxCoach1
        1 -> Strings.Meditation.Technique.boxCoach2
        2 -> Strings.Meditation.Technique.boxCoach3
        else -> null
    }
    BreathingPattern.RELAXATION_478 -> when (idx) {
        0 -> Strings.Meditation.Technique.w478Coach1
        1 -> Strings.Meditation.Technique.w478Coach2
        2 -> Strings.Meditation.Technique.w478Coach3
        else -> null
    }
    BreathingPattern.BELLY_NATURAL -> when (idx) {
        0 -> Strings.Meditation.Technique.bellyCoach1
        1 -> Strings.Meditation.Technique.bellyCoach2
        2 -> Strings.Meditation.Technique.bellyCoach3
        else -> null
    }
    BreathingPattern.BODY_SCAN_NATURAL -> when (idx) {
        0 -> Strings.Meditation.Technique.scanCoach1
        1 -> Strings.Meditation.Technique.scanCoach2
        2 -> Strings.Meditation.Technique.scanCoach3
        else -> null
    }
}

// ── BreathSegmentKind → cue label ─────────────────────────────────────────

/**
 * Pacer cue word per breath segment. Used by [com.lifo.meditation.screens.MeditationSessionScreen]
 * to show the live "Breathe in / Hold / Breathe out" overlay on the breathing pacer.
 */
val BreathSegmentKind.cueRes: StringResource
    get() = when (this) {
        BreathSegmentKind.INHALE -> Strings.Meditation.Cue.breatheIn
        BreathSegmentKind.EXHALE -> Strings.Meditation.Cue.breatheOut
        BreathSegmentKind.HOLD_IN -> Strings.Meditation.Cue.hold
        BreathSegmentKind.HOLD_OUT -> Strings.Meditation.Cue.hold
    }

// ── MeditationGoal → label ────────────────────────────────────────────────

val MeditationGoal.labelRes: StringResource
    get() = when (this) {
        MeditationGoal.STRESS -> Strings.Meditation.Goal.stress
        MeditationGoal.ANXIETY -> Strings.Meditation.Goal.anxiety
        MeditationGoal.SLEEP -> Strings.Meditation.Goal.sleep
        MeditationGoal.FOCUS -> Strings.Meditation.Goal.focus
        MeditationGoal.GROUNDING -> Strings.Meditation.Goal.grounding
    }

// ── MeditationExperience → label ──────────────────────────────────────────

val MeditationExperience.labelRes: StringResource
    get() = when (this) {
        MeditationExperience.FIRST -> Strings.Meditation.Experience.first
        MeditationExperience.OCCASIONAL -> Strings.Meditation.Experience.occasional
        MeditationExperience.REGULAR -> Strings.Meditation.Experience.regular
    }

// ── MeditationAudio → label ───────────────────────────────────────────────

val MeditationAudio.labelRes: StringResource
    get() = when (this) {
        MeditationAudio.VOICE -> Strings.Meditation.Audio.voice
        MeditationAudio.CHIMES -> Strings.Meditation.Audio.chimes
        MeditationAudio.SILENT -> Strings.Meditation.Audio.silent
    }

// ── MeditationRiskFlag → label + sub ──────────────────────────────────────

val MeditationRiskFlag.labelRes: StringResource
    get() = when (this) {
        MeditationRiskFlag.PREGNANCY -> Strings.Meditation.Risk.pregnancy
        MeditationRiskFlag.CARDIO -> Strings.Meditation.Risk.cardio
        MeditationRiskFlag.RESPIRATORY -> Strings.Meditation.Risk.respiratory
        MeditationRiskFlag.EPILEPSY -> Strings.Meditation.Risk.epilepsy
        MeditationRiskFlag.PANIC -> Strings.Meditation.Risk.panic
        MeditationRiskFlag.RECENT_SURGERY -> Strings.Meditation.Risk.recentSurgery
        MeditationRiskFlag.EYE -> Strings.Meditation.Risk.eye
        MeditationRiskFlag.DRIVING -> Strings.Meditation.Risk.driving
    }

/** Returns null when the flag has no clinical sub-text (only PREGNANCY / CARDIO have sub-text). */
val MeditationRiskFlag.subRes: StringResource?
    get() = when (this) {
        MeditationRiskFlag.PREGNANCY -> Strings.Meditation.Risk.pregnancySub
        MeditationRiskFlag.CARDIO -> Strings.Meditation.Risk.cardioSub
        else -> null
    }
