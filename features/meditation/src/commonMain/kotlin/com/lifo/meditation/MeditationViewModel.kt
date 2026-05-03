package com.lifo.meditation

import com.lifo.meditation.voice.CoachKey
import com.lifo.meditation.voice.SubPhaseCueKey
import com.lifo.meditation.voice.VoiceUtterance
import com.lifo.util.auth.AuthProvider
import com.lifo.util.currentTimeMillis
import com.lifo.util.model.MeditationAudio
import com.lifo.util.model.MeditationSession
import com.lifo.util.model.MeditationType
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.MeditationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel for the redesigned 5-phase meditation flow.
 *
 * State machine: WELCOME → SCREENING → CONFIGURE → SESSION → OVERVIEW.
 * Within SESSION: settling (15%) → practice (70%) → integration (15%).
 *
 * The session timer runs as a coroutine ticking every [TICK_INTERVAL_MILLIS]
 * (~250ms / 4Hz). It is pause-aware (cancelled on PauseSession, restarted on
 * ResumeSession) and auto-fires [MeditationContract.Intent.SessionAutoComplete]
 * when elapsed reaches [MeditationContract.SessionRuntime.totalActiveMillis].
 *
 * 250ms granularity is enough for crisp segment boundary detection (segments
 * are >= 4s for all techniques) and for the cue countdown text. Per-frame scale
 * smoothness comes from Compose's `Animatable.animateTo` in the pacer composable,
 * not from the VM tick.
 */
class MeditationViewModel(
    private val repository: MeditationRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<MeditationContract.Intent, MeditationContract.State, MeditationContract.Effect>(
    MeditationContract.State()
) {

    private var timerJob: Job? = null
    /** Tracks which sub-phase we last announced via PlayBell — prevents duplicate chimes. */
    private var lastAnnouncedSubPhase: MeditationContract.SubPhase? = null

    /**
     * Per-session record of which voice cues / coach lines have already been
     * spoken. Drives the "speak each coach line at most once per session"
     * policy — visual rotation continues, but the voice does not repeat.
     * Cleared on `startSession()`.
     */
    private var spokenSegmentIndex: Int = -1
    private var spokenCoachKeys: MutableSet<CoachKey> = mutableSetOf()

    private companion object {
        /** Timer tick interval. 250ms balances cue countdown smoothness with CPU. */
        const val TICK_INTERVAL_MILLIS: Long = 250L
    }

    init {
        onIntent(MeditationContract.Intent.LoadStats)
    }

    override fun handleIntent(intent: MeditationContract.Intent) {
        when (intent) {
            // ── Phase navigation ─────────────────────────────────────
            MeditationContract.Intent.NavigateToScreening ->
                updateState { copy(phase = MeditationContract.SessionPhase.SCREENING) }

            MeditationContract.Intent.NavigateToConfigure ->
                updateState { copy(phase = MeditationContract.SessionPhase.CONFIGURE) }

            MeditationContract.Intent.NavigateBackFromScreening ->
                updateState { copy(phase = MeditationContract.SessionPhase.WELCOME) }

            MeditationContract.Intent.NavigateBackFromConfigure ->
                updateState { copy(phase = MeditationContract.SessionPhase.SCREENING) }

            MeditationContract.Intent.NavigateRedoFromOverview -> startSession()

            MeditationContract.Intent.NavigateDifferentFromOverview ->
                updateState {
                    copy(
                        phase = MeditationContract.SessionPhase.CONFIGURE,
                        session = null,
                    )
                }

            // ── Screening ────────────────────────────────────────────
            is MeditationContract.Intent.ToggleRiskFlag -> updateState {
                val newRisks = if (intent.flag in risks) risks - intent.flag else risks + intent.flag
                // If user toggled into restricted state, drop any pacing-technique override
                val newConfig = if (newRisks.isNotEmpty() && config.techniqueOverride?.isGentle == false) {
                    config.copy(techniqueOverride = null)
                } else config
                copy(risks = newRisks, config = newConfig)
            }

            MeditationContract.Intent.ClearAllRisks -> updateState { copy(risks = emptySet()) }

            // ── Configure ────────────────────────────────────────────
            is MeditationContract.Intent.SetDuration -> updateState { copy(config = config.copy(duration = intent.minutes)) }

            is MeditationContract.Intent.SetGoal -> updateState {
                // Setting a new goal clears any previous override (so auto-resolution kicks in)
                copy(config = config.copy(goal = intent.goal, techniqueOverride = null))
            }

            is MeditationContract.Intent.SetExperience -> updateState {
                // Same for experience
                copy(config = config.copy(experience = intent.exp, techniqueOverride = null))
            }

            is MeditationContract.Intent.SetAudio -> updateState { copy(config = config.copy(audio = intent.audio)) }

            is MeditationContract.Intent.SetTechniqueOverride -> updateState {
                copy(config = config.copy(techniqueOverride = intent.pattern))
            }

            // ── Session lifecycle ────────────────────────────────────
            MeditationContract.Intent.StartSession -> startSession()
            MeditationContract.Intent.PauseSession -> pauseSession()
            MeditationContract.Intent.ResumeSession -> resumeSession()
            MeditationContract.Intent.RequestStopSession -> updateState { copy(showStopDialog = true) }
            MeditationContract.Intent.DismissStopDialog -> updateState { copy(showStopDialog = false) }
            MeditationContract.Intent.ConfirmStopSession -> confirmStopSession()
            MeditationContract.Intent.SessionAutoComplete -> autoCompleteSession()

            // ── Stats ────────────────────────────────────────────────
            MeditationContract.Intent.LoadStats -> loadStats()
            MeditationContract.Intent.RetryLoadStats -> {
                updateState { copy(errorMessage = null) }
                loadStats()
            }
        }
    }

    // ── Session control ─────────────────────────────────────────────────

    private fun startSession() {
        val s = state.value
        val technique = s.effectiveTechnique
        val durationSec = s.config.duration * 60
        lastAnnouncedSubPhase = null
        spokenSegmentIndex = -1
        spokenCoachKeys = mutableSetOf()
        updateState {
            copy(
                phase = MeditationContract.SessionPhase.SESSION,
                session = MeditationContract.SessionRuntime(
                    technique = technique,
                    durationSeconds = durationSec,
                ),
                showStopDialog = false,
            )
        }
        sendEffect(MeditationContract.Effect.PlayBell)
        // Voice opener for the settle phase ("Arrive")
        if (s.config.audio == MeditationAudio.VOICE) {
            sendEffect(MeditationContract.Effect.Speak(VoiceUtterance.SubPhaseCue(SubPhaseCueKey.ARRIVE)))
        }
        startTimer()
    }

    private fun pauseSession() {
        timerJob?.cancel()
        updateState { copy(session = session?.copy(isPaused = true)) }
    }

    private fun resumeSession() {
        updateState { copy(session = session?.copy(isPaused = false)) }
        startTimer()
    }

    private fun confirmStopSession() {
        timerJob?.cancel()
        val current = state.value.session ?: return
        val finalRuntime = current.copy(stopped = true)
        finalize(finalRuntime)
    }

    private fun autoCompleteSession() {
        timerJob?.cancel()
        val current = state.value.session ?: return
        finalize(current.copy(stopped = false))
    }

    private fun finalize(runtime: MeditationContract.SessionRuntime) {
        sendEffect(MeditationContract.Effect.PlayBell)
        sendEffect(MeditationContract.Effect.SessionCompleted)
        updateState {
            copy(
                phase = MeditationContract.SessionPhase.OVERVIEW,
                session = runtime,
                showStopDialog = false,
            )
        }
        // Persist the session record (best-effort; failure does not block the Overview screen)
        persistSession(runtime)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun persistSession(runtime: MeditationContract.SessionRuntime) {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            val session = MeditationSession(
                id = Uuid.random().toString(),
                ownerId = userId,
                timestampMillis = currentTimeMillis(),
                // Always BREATHING for the new flow — every session is technique-backed
                // (TIMER / BODY_SCAN are legacy enum values kept for backward-compat reads).
                type = MeditationType.BREATHING,
                breathingPattern = runtime.technique,
                durationSeconds = runtime.durationSeconds,
                completedSeconds = runtime.elapsedSeconds,
                stopped = runtime.stopped,
                cyclesCompleted = if (runtime.technique.hasPattern) runtime.cyclesCompleted else null,
            )
            repository.saveSession(session)
                .onSuccess { sendEffect(MeditationContract.Effect.SessionSaved) }
                .onFailure { sendEffect(MeditationContract.Effect.Error(it.message ?: "")) }
        }
    }

    // ── Timer ───────────────────────────────────────────────────────────

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(TICK_INTERVAL_MILLIS)
                val s = state.value
                val current = s.session ?: break
                if (current.isPaused) continue

                val newElapsedMillis = current.elapsedMillis + TICK_INTERVAL_MILLIS
                val updated = current.copy(elapsedMillis = newElapsedMillis)
                updateState { copy(session = updated) }

                val voiceOn = s.config.audio == MeditationAudio.VOICE

                // Sub-phase change → bell + voice opener cue (deduped via lastAnnouncedSubPhase)
                val newSubPhase = updated.subPhase
                if (newSubPhase != lastAnnouncedSubPhase) {
                    lastAnnouncedSubPhase = newSubPhase
                    sendEffect(MeditationContract.Effect.PlayBell)
                    if (voiceOn) {
                        when (newSubPhase) {
                            MeditationContract.SubPhase.INTEGRATION ->
                                sendEffect(MeditationContract.Effect.Speak(VoiceUtterance.SubPhaseCue(SubPhaseCueKey.RELEASE)))
                            // SETTLING opener spoken at session start, not here
                            // PRACTICE has no opener cue — pacer takes over
                            else -> Unit
                        }
                    }
                }

                if (voiceOn) {
                    emitVoiceForTick(updated)
                }

                // Auto-complete
                if (newElapsedMillis >= updated.totalActiveMillis) {
                    onIntent(MeditationContract.Intent.SessionAutoComplete)
                    break
                }
            }
        }
    }

    /**
     * Voice emission per tick — gated on `audio == VOICE` by caller.
     * Two emission paths:
     *
     * 1. **Cue per breath segment** — fired once when `currentSegmentIndex` changes
     *    (segments are 4-8s long; emitting per VM tick is bounded by segment count,
     *    not tick count).
     *
     * 2. **Coach line on first display** — settle + integrate lines speak as the
     *    sub-phase progresses (one per third of the sub-phase). Practice lines
     *    speak the first time each rotation lands on them, then stay quiet
     *    on repeats — the visual still rotates, but a meditator who's heard
     *    "Through the nose. Belly soft, shoulders quiet." once does not need
     *    to hear it every 36 seconds.
     */
    private fun emitVoiceForTick(runtime: MeditationContract.SessionRuntime) {
        // 1. Per-segment cue
        when (runtime.subPhase) {
            MeditationContract.SubPhase.PRACTICE -> {
                val seg = runtime.currentSegment
                val idx = runtime.currentSegmentIndex
                if (seg != null && idx >= 0 && idx != spokenSegmentIndex) {
                    spokenSegmentIndex = idx
                    sendEffect(MeditationContract.Effect.Speak(VoiceUtterance.Cue(seg.kind)))
                }
            }
            else -> Unit
        }

        // 2. Coach line — speak first time each key surfaces
        val coachKey = currentCoachKey(runtime) ?: return
        if (coachKey !in spokenCoachKeys) {
            spokenCoachKeys.add(coachKey)
            sendEffect(MeditationContract.Effect.Speak(coachKey.toUtterance()))
        }
    }

    /**
     * Mirror of `MeditationSessionScreen.currentCoachLine()` — must stay in sync
     * with the visual indexing rules so voice always matches what the user sees.
     */
    private fun currentCoachKey(runtime: MeditationContract.SessionRuntime): CoachKey? {
        return when (runtime.subPhase) {
            MeditationContract.SubPhase.SETTLING -> {
                val settleSec = runtime.settleSeconds.coerceAtLeast(1)
                val sliceSec = settleSec / 3f
                val idx = (runtime.elapsedSeconds.coerceAtLeast(0) / sliceSec).toInt().coerceIn(0, 2)
                CoachKey.Settle(idx)
            }
            MeditationContract.SubPhase.INTEGRATION -> {
                val intoIntegrateSec = (runtime.elapsedSeconds - runtime.settleSeconds - runtime.practiceCapSeconds)
                    .coerceAtLeast(0)
                val sliceSec = runtime.integrateSeconds.coerceAtLeast(1) / 3f
                val idx = (intoIntegrateSec / sliceSec).toInt().coerceIn(0, 2)
                CoachKey.Integrate(idx)
            }
            MeditationContract.SubPhase.PRACTICE -> {
                if (!runtime.technique.hasPattern) return null
                val practiceMillis = runtime.practiceElapsedMillis.coerceAtLeast(0L)
                val rotationIdx = ((practiceMillis / 12_000L).toInt()) % 3
                CoachKey.Practice(runtime.technique, rotationIdx)
            }
        }
    }

    // ── Stats ───────────────────────────────────────────────────────────

    private fun loadStats() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            try {
                val totalMin = repository.getTotalMinutes(userId)
                val count = repository.getSessionCount(userId)
                updateState { copy(totalMinutes = totalMin, sessionCount = count, errorMessage = null) }
            } catch (_: Exception) {
                // Stats are best-effort — surface a non-blocking error
                updateState { copy(errorMessage = "stats_load_failed") }
            }
        }
        scope.launch {
            try {
                repository.getRecentSessions(userId).collect { sessions ->
                    updateState { copy(recentSessions = sessions) }
                }
            } catch (_: Exception) {
                updateState { copy(errorMessage = "stats_load_failed") }
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
