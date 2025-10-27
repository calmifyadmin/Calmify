package com.lifo.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lifo.mongo.repository.ProfileRepository
import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.RequestState
import com.lifo.util.model.getWeekLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ProfileViewModel
 *
 * Manages psychological profile display and chart data transformation
 * Week 7 - PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 5 (Week 7)
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        loadProfiles()
    }

    /**
     * Load last 4 weeks of profiles for trend visualization
     */
    fun loadProfiles(weeks: Int = 4) {
        if (currentUserId.isEmpty()) {
            _uiState.value = ProfileUiState.Error("User not authenticated")
            return
        }

        viewModelScope.launch {
            profileRepository.getProfilesForUser(currentUserId, weeks).collect { result ->
                when (result) {
                    is RequestState.Loading -> {
                        _uiState.value = ProfileUiState.Loading
                    }
                    is RequestState.Success -> {
                        val profiles = result.data
                        if (profiles.isEmpty() || profiles.all { !it.hasSufficientData() }) {
                            _uiState.value = ProfileUiState.Empty
                        } else {
                            // Transform profiles to chart data
                            val chartData = transformToChartData(profiles)
                            _uiState.value = ProfileUiState.Success(
                                profiles = profiles,
                                chartData = chartData
                            )
                        }
                    }
                    is RequestState.Error -> {
                        _uiState.value = ProfileUiState.Error(
                            result.error.message ?: "Failed to load profiles"
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Transform profiles to chart-ready data
     * Returns data in chronological order (oldest to newest) for proper chart display
     */
    private fun transformToChartData(profiles: List<PsychologicalProfile>): ChartData {
        // Sort profiles chronologically (oldest first) for chart X-axis
        val sortedProfiles = profiles.sortedWith(
            compareBy({ it.year }, { it.weekNumber })
        )

        val weekLabels = sortedProfiles.map { it.getWeekLabel() }
        val stressLine = sortedProfiles.map { it.stressBaseline }
        val moodLine = sortedProfiles.map { it.moodBaseline }

        // Collect stress peaks with their week index
        val peaks = mutableListOf<PeakMarker>()
        sortedProfiles.forEachIndexed { index, profile ->
            profile.stressPeaks.forEach { peak ->
                peaks.add(
                    PeakMarker(
                        weekIndex = index,
                        level = peak.level,
                        timestamp = peak.timestamp
                    )
                )
            }
        }

        return ChartData(
            weekLabels = weekLabels,
            stressLine = stressLine,
            moodLine = moodLine,
            peaks = peaks
        )
    }

    /**
     * Refresh profiles
     */
    fun refresh() {
        loadProfiles()
    }
}

/**
 * UI State for profile screen
 */
sealed class ProfileUiState {
    data object Loading : ProfileUiState()

    data class Success(
        val profiles: List<PsychologicalProfile>,
        val chartData: ChartData
    ) : ProfileUiState()

    data class Error(val message: String) : ProfileUiState()

    data object Empty : ProfileUiState()
}

/**
 * Chart data model
 * Contains transformed data ready for Vico chart rendering
 */
data class ChartData(
    val weekLabels: List<String>,          // e.g., ["W40", "W41", "W42", "W43"]
    val stressLine: List<Float>,           // Stress baseline values (0-10)
    val moodLine: List<Float>,             // Mood baseline values (0-10)
    val peaks: List<PeakMarker>            // Stress peaks with positions
)

/**
 * Peak marker for chart annotations
 */
data class PeakMarker(
    val weekIndex: Int,                    // Index in the chart (0-based)
    val level: Int,                        // Peak stress level (0-10)
    val timestamp: Long                    // Unix timestamp for tooltip
)
