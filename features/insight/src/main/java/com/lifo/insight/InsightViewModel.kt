package com.lifo.insight

import androidx.lifecycle.SavedStateHandle
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.InsightRepository
import com.lifo.util.model.RequestState
import kotlinx.coroutines.launch

/**
 * InsightContract - MVI contract for the Insight screen.
 *
 * Defines all user intents, the single immutable UI state,
 * and one-shot side effects.
 */
object InsightContract {

    sealed interface Intent : MviContract.Intent {
        data class LoadInsight(val diaryId: String) : Intent
        data class SubmitFeedback(val isHelpful: Boolean, val correction: String = "") : Intent
        data object ToggleSummaryExpanded : Intent
        data class ShowCorrectionDialog(val show: Boolean) : Intent
    }

    /**
     * UI State for insight screen.
     * Replaces the old top-level InsightUiState data class.
     */
    data class State(
        val isLoading: Boolean = false,
        val insight: com.lifo.util.model.DiaryInsight? = null,
        val error: String? = null,
        val isSummaryExpanded: Boolean = false,
        val isSubmittingFeedback: Boolean = false,
        val feedbackSubmitted: Boolean = false,
        val showCorrectionDialog: Boolean = false
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object ShowFeedbackConfirmation : Effect
        data class ShowFeedbackError(val message: String) : Effect
    }
}

/**
 * InsightViewModel
 *
 * Manages AI-generated diary insights display and user feedback.
 * Migrated from MVVM to MVI pattern.
 * Week 5 - PSYCHOLOGICAL_INSIGHTS_PLAN.md
 */
class InsightViewModel constructor(
    private val savedStateHandle: SavedStateHandle,
    private val insightRepository: InsightRepository
) : MviViewModel<InsightContract.Intent, InsightContract.State, InsightContract.Effect>(
    initialState = InsightContract.State()
) {

    private val diaryId: String? = savedStateHandle.get<String>("diaryId")

    init {
        diaryId?.let { onIntent(InsightContract.Intent.LoadInsight(it)) }
    }

    override fun handleIntent(intent: InsightContract.Intent) {
        when (intent) {
            is InsightContract.Intent.LoadInsight -> loadInsight(intent.diaryId)
            is InsightContract.Intent.SubmitFeedback -> submitFeedback(intent.isHelpful, intent.correction)
            is InsightContract.Intent.ToggleSummaryExpanded -> toggleSummaryExpanded()
            is InsightContract.Intent.ShowCorrectionDialog -> showCorrectionDialog(intent.show)
        }
    }

    /**
     * Load insight for a specific diary
     */
    private fun loadInsight(diaryId: String) {
        scope.launch {
            insightRepository.getInsightByDiaryId(diaryId).collect { result ->
                when (result) {
                    is RequestState.Loading -> {
                        updateState { copy(isLoading = true, error = null) }
                    }
                    is RequestState.Success -> {
                        updateState {
                            copy(
                                isLoading = false,
                                insight = result.data,
                                error = if (result.data == null) "No insight available yet" else null
                            )
                        }
                    }
                    is RequestState.Error -> {
                        updateState {
                            copy(
                                isLoading = false,
                                error = result.error.message ?: "Failed to load insight"
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Submit user feedback (helpful/not helpful with rating 1-5)
     */
    private fun submitFeedback(isHelpful: Boolean, correction: String = "") {
        val insightId = currentState.insight?.id ?: return
        val rating = if (isHelpful) 4 else 2 // 4 = helpful, 2 = not helpful

        scope.launch {
            updateState { copy(isSubmittingFeedback = true) }

            when (val result = insightRepository.submitFeedback(insightId, rating, correction)) {
                is RequestState.Success -> {
                    updateState {
                        copy(
                            isSubmittingFeedback = false,
                            feedbackSubmitted = true,
                            insight = insight?.copy(
                                userRating = rating,
                                userCorrection = correction
                            )
                        )
                    }
                    sendEffect(InsightContract.Effect.ShowFeedbackConfirmation)
                }
                is RequestState.Error -> {
                    val errorMsg = result.error.message ?: "Failed to submit feedback"
                    updateState {
                        copy(
                            isSubmittingFeedback = false,
                            error = errorMsg
                        )
                    }
                    sendEffect(InsightContract.Effect.ShowFeedbackError(errorMsg))
                }
                else -> {}
            }
        }
    }

    /**
     * Expand/collapse AI summary
     */
    private fun toggleSummaryExpanded() {
        updateState { copy(isSummaryExpanded = !isSummaryExpanded) }
    }

    /**
     * Show/hide correction dialog
     */
    private fun showCorrectionDialog(show: Boolean) {
        updateState { copy(showCorrectionDialog = show) }
    }
}
