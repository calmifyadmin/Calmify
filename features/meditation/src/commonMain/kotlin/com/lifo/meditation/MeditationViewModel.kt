package com.lifo.meditation

import com.lifo.util.auth.AuthProvider
import com.lifo.util.currentTimeMillis
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
 * The session timer runs as a coroutine ticking 1Hz. It is pause-aware
 * (cancelled on PauseSession, restarted on ResumeSession) and auto-fires
 * [MeditationContract.Intent.SessionAutoComplete] when elapsed reaches
 * [MeditationContract.SessionRuntime.totalActiveSeconds].
 *
 * Phase 1 keeps the timer simple (1Hz integer ticks). Phase 2 will add
 * the per-segment chime/voice scheduling on sub-phase boundaries.
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
                delay(1000L)
                val s = state.value
                val current = s.session ?: break
                if (current.isPaused) continue

                val newElapsed = current.elapsedSeconds + 1
                val newCycles = if (current.technique.hasPattern && newElapsed > current.settleSeconds) {
                    val practiceElapsed = (newElapsed - current.settleSeconds)
                        .coerceAtMost(current.practiceCapSeconds)
                    (practiceElapsed / current.technique.totalCycleSeconds).toInt()
                } else current.cyclesCompleted

                val updated = current.copy(elapsedSeconds = newElapsed, cyclesCompleted = newCycles)
                updateState { copy(session = updated) }

                // Sub-phase change → bell
                val newSubPhase = updated.subPhase
                if (newSubPhase != lastAnnouncedSubPhase) {
                    lastAnnouncedSubPhase = newSubPhase
                    sendEffect(MeditationContract.Effect.PlayBell)
                }

                // Auto-complete
                if (newElapsed >= updated.totalActiveSeconds) {
                    onIntent(MeditationContract.Intent.SessionAutoComplete)
                    break
                }
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
