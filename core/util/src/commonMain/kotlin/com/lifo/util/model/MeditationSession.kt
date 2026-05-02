package com.lifo.util.model

import kotlinx.serialization.Serializable

/**
 * A completed meditation or breathing session.
 *
 * Persisted in Firestore (`meditation_sessions` collection per user).
 * The `breathingPattern` field uses canonical enum names — see
 * [BreathingPattern] for backward-compat notes (DIAPHRAGMATIC was renamed
 * to BELLY_NATURAL in Phase 1 of the meditation redesign).
 */
@Serializable
data class MeditationSession(
    val id: String = "",
    val ownerId: String = "",
    val timestampMillis: Long = 0L,
    val type: MeditationType = MeditationType.BREATHING,
    val breathingPattern: BreathingPattern? = null,
    val durationSeconds: Int = 300,
    val completedSeconds: Int = 0,
    val postNote: String = "",
    /** True if user stopped the session early via the Stop button. */
    val stopped: Boolean = false,
    /** Number of breath cycles completed during the practice phase. Null if technique has no pacer. */
    val cyclesCompleted: Int? = null,
)

/**
 * High-level meditation category. UI label resolved at render site via
 * `Strings.Meditation.X` (in `core/ui`) — `core/util` cannot depend on
 * `core/ui`, so display fields are not stored here.
 */
@Serializable
enum class MeditationType {
    TIMER,
    BREATHING,
    BODY_SCAN,
}

/**
 * The 6 evidence-based breathing techniques offered in the redesigned
 * meditation flow. Each carries the timing pattern (in seconds) for the
 * pacer animation, plus metadata for the technique resolver and screening
 * gates.
 *
 * Display labels live in `Strings.Meditation.Technique.X` (resolved at UI
 * layer to keep `core/util` free of resource dependencies).
 *
 * **Backward compatibility**: pre-redesign documents persisted enum value
 * `DIAPHRAGMATIC`. The Firestore mapper accepts that name and maps it to
 * [BELLY_NATURAL] on read. New writes always use the new name.
 */
@Serializable
enum class BreathingPattern(
    val inhaleSeconds: Float,
    val holdInSeconds: Float,
    val exhaleSeconds: Float,
    val holdOutSeconds: Float,
    /** Maximum cycles allowed in a single session (e.g. 4-7-8 capped at 4). Null = uncapped. */
    val cycleCap: Int? = null,
    /** True if technique should not be offered to first-time users (e.g. 4-7-8). */
    val requiresExperience: Boolean = false,
    /** True if technique is allowed on the gentle track (when risk flags are present). */
    val isGentle: Boolean = false,
) {
    /** Coherent breathing — equal, gentle 5.5s in / 5.5s out. Default for stress + first-time users. */
    COHERENT(5.5f, 0f, 5.5f, 0f),

    /** Extended exhale — 4 in / 6 out. Default for anxiety; sleep fallback for first-timers. */
    EXTENDED_EXHALE(4f, 0f, 6f, 0f),

    /** Box breathing — 4·4·4·4. Default for focus. */
    BOX_BREATHING(4f, 4f, 4f, 4f),

    /** Dr. Weil's 4-7-8 pattern. Default for sleep (non-first-time). Capped at 4 cycles for safety. */
    RELAXATION_478(4f, 7f, 8f, 0f, cycleCap = 4, requiresExperience = true),

    /** Belly (diaphragmatic) breathing — no pacer, awareness-only. Gentle-track default. */
    BELLY_NATURAL(0f, 0f, 0f, 0f, isGentle = true),

    /** Body scan — sweep attention through the body, breath stays as it is. Default for grounding. */
    BODY_SCAN_NATURAL(0f, 0f, 0f, 0f, isGentle = true);

    /** True when the technique drives the pacer animation (false for BELLY_NATURAL / BODY_SCAN_NATURAL). */
    val hasPattern: Boolean get() = inhaleSeconds > 0f || exhaleSeconds > 0f

    /** Total cycle length in seconds. 0 for techniques without a pattern. */
    val totalCycleSeconds: Float
        get() = inhaleSeconds + holdInSeconds + exhaleSeconds + holdOutSeconds

    companion object {
        /**
         * Backward-compat parser. Accepts both new (`BELLY_NATURAL`) and old
         * (`DIAPHRAGMATIC`) names. Returns null if no match — caller should
         * fall back to a sensible default (e.g. [COHERENT]).
         */
        fun fromCanonicalName(name: String): BreathingPattern? = when (name) {
            "DIAPHRAGMATIC" -> BELLY_NATURAL
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

/**
 * What the user is here for. Drives the auto-recommended [BreathingPattern]
 * in the Configure screen. User can override unless restricted (gentle track)
 * or first-time (locked to COHERENT).
 */
@Serializable
enum class MeditationGoal {
    /** → COHERENT */
    STRESS,
    /** → EXTENDED_EXHALE */
    ANXIETY,
    /** → RELAXATION_478 (or EXTENDED_EXHALE if first-time) */
    SLEEP,
    /** → BOX_BREATHING */
    FOCUS,
    /** → BODY_SCAN_NATURAL */
    GROUNDING,
}

/**
 * Self-reported breathwork familiarity. FIRST locks recommendation to
 * [BreathingPattern.COHERENT] regardless of goal, and disables techniques
 * with `requiresExperience = true` (e.g. 4-7-8).
 */
@Serializable
enum class MeditationExperience {
    FIRST,
    OCCASIONAL,
    REGULAR,
}

/**
 * Audio mode during the session.
 * - VOICE: TTS guidance (Sherpa-ONNX) speaking cue words + coach lines (Phase 3 of redesign)
 * - CHIMES: bell at sub-phase boundaries
 * - SILENT: no audio
 */
@Serializable
enum class MeditationAudio {
    VOICE,
    CHIMES,
    SILENT,
}

/**
 * Medical screening flags. Any flag toggled on activates "gentle track" mode:
 * the technique override is locked to gentle techniques only (no pacing,
 * no holds — see [BreathingPattern.isGentle]).
 *
 * `id` is the canonical short identifier (used in serialization / metrics if
 * we ever persist screening results). `hasSubText` indicates whether the
 * label has an accompanying sub-text in the UI (more clinical detail).
 */
@Serializable
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
