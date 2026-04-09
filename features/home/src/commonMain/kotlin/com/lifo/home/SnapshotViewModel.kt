package com.lifo.home

import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.WellbeingRepository
import com.lifo.util.model.RequestState
import com.lifo.util.model.WellbeingMetrics
import com.lifo.util.model.WellbeingSnapshot
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ── Contract ────────────────────────────────────────────────────────────────────

object SnapshotContract {

    sealed interface Intent : MviContract.Intent {
        data class UpdateMetric(val field: MetricField, val value: Int) : Intent
        data class UpdateNotes(val notes: String) : Intent
        data class SubmitSnapshot(val ownerId: String, val wasReminded: Boolean = false) : Intent
        data object LoadRecentSnapshots : Intent
        data object ResetForm : Intent
    }

    data class State(
        val isSubmitting: Boolean = false,
        val isSuccess: Boolean = false,
        val error: String? = null,
        val recentSnapshots: List<WellbeingSnapshot> = emptyList(),
        val metrics: WellbeingMetrics = WellbeingMetrics(),
        val notes: String = ""
    ) : MviContract.State

    enum class MetricField {
        LIFE_SATISFACTION,
        WORK_SATISFACTION,
        RELATIONSHIPS_QUALITY,
        MINDFULNESS_SCORE,
        PURPOSE_MEANING,
        GRATITUDE,
        AUTONOMY,
        COMPETENCE,
        RELATEDNESS,
        LONELINESS
    }

    sealed interface Effect : MviContract.Effect {
        data object SubmissionSuccess : Effect
        data class SubmissionError(val message: String) : Effect
    }
}

// ── ViewModel ───────────────────────────────────────────────────────────────────

/**
 * SnapshotViewModel
 *
 * Manages wellbeing snapshot state and submission (MVI).
 * Week 2 - PSYCHOLOGICAL_INSIGHTS_PLAN.md
 */
class SnapshotViewModel constructor(
    private val wellbeingRepository: WellbeingRepository,
    private val authProvider: com.lifo.util.auth.AuthProvider
) : MviViewModel<SnapshotContract.Intent, SnapshotContract.State, SnapshotContract.Effect>(
    initialState = SnapshotContract.State()
) {

    // ── Backward-compatible aliases ─────────────────────────────────────────
    @Suppress("unused")
    val uiState: StateFlow<SnapshotContract.State> get() = state

    /** Computed getter — callers reading `viewModel.metrics` still work. */
    val metrics: WellbeingMetrics get() = currentState.metrics

    /** Computed getter — callers reading `viewModel.notes` still work. */
    val notes: String get() = currentState.notes

    // ── Internal (not in state) ─────────────────────────────────────────────
    private var startTime: Long = System.currentTimeMillis()

    init {
        startTime = System.currentTimeMillis()
    }

    // ── Pure query (not a state mutation) ───────────────────────────────────
    fun getCurrentUserId(): String = authProvider.currentUserId ?: ""

    // ── Intent dispatch ─────────────────────────────────────────────────────

    override fun handleIntent(intent: SnapshotContract.Intent) {
        when (intent) {
            is SnapshotContract.Intent.UpdateMetric -> doUpdateMetric(intent.field, intent.value)
            is SnapshotContract.Intent.UpdateNotes -> doUpdateNotes(intent.notes)
            is SnapshotContract.Intent.SubmitSnapshot -> doSubmitSnapshot(intent.ownerId, intent.wasReminded)
            is SnapshotContract.Intent.LoadRecentSnapshots -> doLoadRecentSnapshots()
            is SnapshotContract.Intent.ResetForm -> doResetForm()
        }
    }

    // ── Backward-compatible public delegating functions ──────────────────────

    // Life Domains
    fun setLifeSatisfaction(value: Int) =
        onIntent(SnapshotContract.Intent.UpdateMetric(SnapshotContract.MetricField.LIFE_SATISFACTION, value))

    fun setWorkSatisfaction(value: Int) =
        onIntent(SnapshotContract.Intent.UpdateMetric(SnapshotContract.MetricField.WORK_SATISFACTION, value))

    fun setRelationshipsQuality(value: Int) =
        onIntent(SnapshotContract.Intent.UpdateMetric(SnapshotContract.MetricField.RELATIONSHIPS_QUALITY, value))

    // Psychological Health
    fun setMindfulnessScore(value: Int) =
        onIntent(SnapshotContract.Intent.UpdateMetric(SnapshotContract.MetricField.MINDFULNESS_SCORE, value))

    fun setPurposeMeaning(value: Int) =
        onIntent(SnapshotContract.Intent.UpdateMetric(SnapshotContract.MetricField.PURPOSE_MEANING, value))

    fun setGratitude(value: Int) =
        onIntent(SnapshotContract.Intent.UpdateMetric(SnapshotContract.MetricField.GRATITUDE, value))

    // SDT Pillars
    fun setAutonomy(value: Int) =
        onIntent(SnapshotContract.Intent.UpdateMetric(SnapshotContract.MetricField.AUTONOMY, value))

    fun setCompetence(value: Int) =
        onIntent(SnapshotContract.Intent.UpdateMetric(SnapshotContract.MetricField.COMPETENCE, value))

    fun setRelatedness(value: Int) =
        onIntent(SnapshotContract.Intent.UpdateMetric(SnapshotContract.MetricField.RELATEDNESS, value))

    // Risk Factors
    fun setLoneliness(value: Int) =
        onIntent(SnapshotContract.Intent.UpdateMetric(SnapshotContract.MetricField.LONELINESS, value))

    fun updateNotes(value: String) =
        onIntent(SnapshotContract.Intent.UpdateNotes(value))

    /**
     * Submit the wellbeing snapshot.
     *
     * Backward-compatible overload: callers using callbacks will still compile,
     * but they should migrate to collecting [effects] for [SnapshotContract.Effect].
     */
    @Deprecated(
        message = "Use onIntent(SubmitSnapshot) and collect effects instead",
        replaceWith = ReplaceWith("onIntent(SnapshotContract.Intent.SubmitSnapshot(ownerId, wasReminded))")
    )
    fun submitSnapshot(
        ownerId: String,
        wasReminded: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onIntent(SnapshotContract.Intent.SubmitSnapshot(ownerId, wasReminded))
    }

    /** Fire-and-forget variant -- result delivered via effects. */
    fun submitSnapshot(ownerId: String, wasReminded: Boolean = false) =
        onIntent(SnapshotContract.Intent.SubmitSnapshot(ownerId, wasReminded))

    /** Load recent snapshots for display */
    fun loadRecentSnapshots() = onIntent(SnapshotContract.Intent.LoadRecentSnapshots)

    /** Reset the form to defaults */
    fun resetForm() = onIntent(SnapshotContract.Intent.ResetForm)

    // ── Private business logic ──────────────────────────────────────────────

    private fun doUpdateMetric(field: SnapshotContract.MetricField, value: Int) {
        updateState {
            val m = metrics
            copy(
                metrics = when (field) {
                    SnapshotContract.MetricField.LIFE_SATISFACTION -> m.copy(lifeSatisfaction = value)
                    SnapshotContract.MetricField.WORK_SATISFACTION -> m.copy(workSatisfaction = value)
                    SnapshotContract.MetricField.RELATIONSHIPS_QUALITY -> m.copy(relationshipsQuality = value)
                    SnapshotContract.MetricField.MINDFULNESS_SCORE -> m.copy(mindfulnessScore = value)
                    SnapshotContract.MetricField.PURPOSE_MEANING -> m.copy(purposeMeaning = value)
                    SnapshotContract.MetricField.GRATITUDE -> m.copy(gratitude = value)
                    SnapshotContract.MetricField.AUTONOMY -> m.copy(autonomy = value)
                    SnapshotContract.MetricField.COMPETENCE -> m.copy(competence = value)
                    SnapshotContract.MetricField.RELATEDNESS -> m.copy(relatedness = value)
                    SnapshotContract.MetricField.LONELINESS -> m.copy(loneliness = value)
                }
            )
        }
    }

    private fun doUpdateNotes(notes: String) {
        updateState { copy(notes = notes) }
    }

    private fun doSubmitSnapshot(ownerId: String, wasReminded: Boolean) {
        scope.launch {
            updateState { copy(isSubmitting = true, error = null) }

            val completionTime = System.currentTimeMillis() - startTime
            val snapshot = currentState.metrics.toSnapshot(
                ownerId = ownerId,
                notes = currentState.notes,
                completionTime = completionTime,
                wasReminded = wasReminded
            )

            when (val result = wellbeingRepository.insertSnapshot(snapshot)) {
                is RequestState.Success -> {
                    updateState { copy(isSubmitting = false, isSuccess = true) }
                    sendEffect(SnapshotContract.Effect.SubmissionSuccess)
                }
                is RequestState.Error -> {
                    val errorMessage = result.error.message ?: "Failed to save snapshot"
                    updateState { copy(isSubmitting = false, error = errorMessage) }
                    sendEffect(SnapshotContract.Effect.SubmissionError(errorMessage))
                }
                else -> {
                    updateState { copy(isSubmitting = false) }
                }
            }
        }
    }

    private fun doLoadRecentSnapshots() {
        scope.launch {
            wellbeingRepository.getAllSnapshots().collect { result ->
                when (result) {
                    is RequestState.Success -> {
                        updateState { copy(recentSnapshots = result.data) }
                    }
                    is RequestState.Error -> {
                        updateState { copy(error = result.error.message) }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun doResetForm() {
        startTime = System.currentTimeMillis()
        updateState { SnapshotContract.State() }
    }
}
