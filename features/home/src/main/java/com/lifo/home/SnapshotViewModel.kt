package com.lifo.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifo.mongo.repository.WellbeingRepository
import com.lifo.util.model.RequestState
import com.lifo.util.model.WellbeingMetrics
import com.lifo.util.model.WellbeingSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SnapshotViewModel
 *
 * Manages wellbeing snapshot state and submission
 * Week 2 - PSYCHOLOGICAL_INSIGHTS_PLAN.md
 */
@HiltViewModel
class SnapshotViewModel @Inject constructor(
    private val wellbeingRepository: WellbeingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnapshotUiState())
    val uiState: StateFlow<SnapshotUiState> = _uiState.asStateFlow()

    // Track start time for completion time metric
    private var startTime: Long = System.currentTimeMillis()

    var metrics by mutableStateOf(WellbeingMetrics())
        private set

    private var _notes by mutableStateOf("")
    val notes: String get() = _notes

    init {
        startTime = System.currentTimeMillis()
    }

    // Life Domains
    fun setLifeSatisfaction(value: Int) {
        metrics = metrics.copy(lifeSatisfaction = value)
    }

    fun setWorkSatisfaction(value: Int) {
        metrics = metrics.copy(workSatisfaction = value)
    }

    fun setRelationshipsQuality(value: Int) {
        metrics = metrics.copy(relationshipsQuality = value)
    }

    // Psychological Health
    fun setMindfulnessScore(value: Int) {
        metrics = metrics.copy(mindfulnessScore = value)
    }

    fun setPurposeMeaning(value: Int) {
        metrics = metrics.copy(purposeMeaning = value)
    }

    fun setGratitude(value: Int) {
        metrics = metrics.copy(gratitude = value)
    }

    // SDT Pillars
    fun setAutonomy(value: Int) {
        metrics = metrics.copy(autonomy = value)
    }

    fun setCompetence(value: Int) {
        metrics = metrics.copy(competence = value)
    }

    fun setRelatedness(value: Int) {
        metrics = metrics.copy(relatedness = value)
    }

    // Risk Factors
    fun setLoneliness(value: Int) {
        metrics = metrics.copy(loneliness = value)
    }

    fun updateNotes(value: String) {
        _notes = value
    }

    /**
     * Submit the wellbeing snapshot
     */
    fun submitSnapshot(
        ownerId: String,
        wasReminded: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            val completionTime = System.currentTimeMillis() - startTime
            val snapshot = metrics.toSnapshot(
                ownerId = ownerId,
                notes = notes,
                completionTime = completionTime,
                wasReminded = wasReminded
            )

            when (val result = wellbeingRepository.insertSnapshot(snapshot)) {
                is RequestState.Success -> {
                    _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
                    onSuccess()
                }
                is RequestState.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = result.error.message ?: "Failed to save snapshot"
                        )
                    }
                    onError(result.error.message ?: "Unknown error")
                }
                else -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                }
            }
        }
    }

    /**
     * Load recent snapshots for display
     */
    fun loadRecentSnapshots() {
        viewModelScope.launch {
            wellbeingRepository.getAllSnapshots().collect { result ->
                when (result) {
                    is RequestState.Success -> {
                        _uiState.update { it.copy(recentSnapshots = result.data) }
                    }
                    is RequestState.Error -> {
                        _uiState.update { it.copy(error = result.error.message) }
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Reset the form to defaults
     */
    fun resetForm() {
        metrics = WellbeingMetrics()
        _notes = ""
        startTime = System.currentTimeMillis()
        _uiState.update { SnapshotUiState() }
    }
}

/**
 * UI State for wellbeing snapshot screen
 */
data class SnapshotUiState(
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val recentSnapshots: List<WellbeingSnapshot> = emptyList()
)
