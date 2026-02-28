package com.lifo.insight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifo.util.repository.InsightRepository
import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * InsightViewModel
 *
 * Manages AI-generated diary insights display and user feedback
 * Week 5 - PSYCHOLOGICAL_INSIGHTS_PLAN.md
 */
@HiltViewModel
class InsightViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val insightRepository: InsightRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightUiState())
    val uiState: StateFlow<InsightUiState> = _uiState.asStateFlow()

    private val diaryId: String? = savedStateHandle.get<String>("diaryId")

    init {
        diaryId?.let { loadInsight(it) }
    }

    /**
     * Load insight for a specific diary
     */
    fun loadInsight(diaryId: String) {
        viewModelScope.launch {
            insightRepository.getInsightByDiaryId(diaryId).collect { result ->
                when (result) {
                    is RequestState.Loading -> {
                        _uiState.update { it.copy(isLoading = true, error = null) }
                    }
                    is RequestState.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                insight = result.data,
                                error = if (result.data == null) "No insight available yet" else null
                            )
                        }
                    }
                    is RequestState.Error -> {
                        _uiState.update {
                            it.copy(
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
    fun submitFeedback(isHelpful: Boolean, correction: String = "") {
        val insightId = _uiState.value.insight?.id ?: return
        val rating = if (isHelpful) 4 else 2 // 4 = helpful, 2 = not helpful

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingFeedback = true) }

            when (val result = insightRepository.submitFeedback(insightId, rating, correction)) {
                is RequestState.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingFeedback = false,
                            feedbackSubmitted = true,
                            insight = it.insight?.copy(
                                userRating = rating,
                                userCorrection = correction
                            )
                        )
                    }
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingFeedback = false,
                            error = result.error.message ?: "Failed to submit feedback"
                        )
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * Expand/collapse AI summary
     */
    fun toggleSummaryExpanded() {
        _uiState.update { it.copy(isSummaryExpanded = !it.isSummaryExpanded) }
    }

    /**
     * Show/hide correction dialog
     */
    fun showCorrectionDialog(show: Boolean) {
        _uiState.update { it.copy(showCorrectionDialog = show) }
    }
}

/**
 * UI State for insight screen
 */
data class InsightUiState(
    val isLoading: Boolean = false,
    val insight: DiaryInsight? = null,
    val error: String? = null,
    val isSummaryExpanded: Boolean = false,
    val isSubmittingFeedback: Boolean = false,
    val feedbackSubmitted: Boolean = false,
    val showCorrectionDialog: Boolean = false
)
