package com.lifo.profile

import com.lifo.home.domain.aggregator.WellbeingAggregator
import com.lifo.home.domain.model.WellbeingAggregationResult
import com.lifo.util.auth.AuthProvider
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.ProfileRepository
import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.RequestState
import com.lifo.util.model.getWeekLabel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * MVI Contract for the Profile screen.
 */
object ProfileContract {

    sealed interface Intent : MviContract.Intent {
        /** Load profiles for the given number of weeks. */
        data class LoadProfiles(val weeks: Int = 4) : Intent
        /** Refresh profiles (shortcut for LoadProfiles with default weeks). */
        data object Refresh : Intent
    }

    // State is the existing ProfileUiState sealed hierarchy (see below).
    // It already covers Loading / Success / Error / Empty.

    sealed interface Effect : MviContract.Effect {
        // No one-shot effects needed for now; placeholder for future use.
    }
}

/**
 * ProfileViewModel
 *
 * Manages psychological profile display and chart data transformation
 * Week 7 - PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 5 (Week 7)
 */
class ProfileViewModel constructor(
    private val profileRepository: ProfileRepository,
    private val authProvider: AuthProvider,
    private val wellbeingAggregator: WellbeingAggregator
) : MviViewModel<ProfileContract.Intent, ProfileUiState, ProfileContract.Effect>(
    initialState = ProfileUiState.Loading
) {

    /**
     * Backward-compatible alias so existing callers that read
     * `viewModel.uiState.collectAsState()` keep compiling.
     */
    val uiState: StateFlow<ProfileUiState> get() = state

    private val currentUserId: String
        get() = authProvider.currentUserId ?: ""

    init {
        onIntent(ProfileContract.Intent.LoadProfiles())
    }

    // ── MVI plumbing ────────────────────────────────────────────────────

    override fun handleIntent(intent: ProfileContract.Intent) {
        when (intent) {
            is ProfileContract.Intent.LoadProfiles -> loadProfiles(intent.weeks)
            is ProfileContract.Intent.Refresh -> loadProfiles()
        }
    }

    // ── Business logic (private) ────────────────────────────────────────

    /**
     * Load last N weeks of profiles for trend visualization.
     */
    private fun loadProfiles(weeks: Int = 4) {
        if (currentUserId.isEmpty()) {
            updateState { ProfileUiState.Error("User not authenticated") }
            return
        }

        scope.launch {
            profileRepository.getProfilesForUser(currentUserId, weeks).collect { result ->
                when (result) {
                    is RequestState.Loading -> {
                        updateState { ProfileUiState.Loading }
                    }
                    is RequestState.Success -> {
                        val profiles = result.data
                        if (profiles.isEmpty() || profiles.all { !it.hasSufficientData() }) {
                            updateState { ProfileUiState.Empty }
                        } else {
                            // Transform profiles to chart data
                            val chartData = transformToChartData(profiles)
                            val aggregation = try {
                                wellbeingAggregator.aggregate()
                            } catch (e: Exception) {
                                null
                            }
                            updateState {
                                ProfileUiState.Success(
                                    profiles = profiles,
                                    chartData = chartData,
                                    aggregation = aggregation
                                )
                            }
                        }
                    }
                    is RequestState.Error -> {
                        updateState {
                            ProfileUiState.Error(
                                result.error.message ?: "Failed to load profiles"
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Transform profiles to chart-ready data.
     * Returns data in chronological order (oldest to newest) for proper chart display.
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
                        timestamp = peak.timestampMillis
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

    // ── Backward-compatible public API ──────────────────────────────────

    /**
     * Refresh profiles. Delegates to the MVI intent.
     * Kept as a public function so existing callers (`viewModel.refresh()`) compile.
     */
    fun refresh() {
        onIntent(ProfileContract.Intent.Refresh)
    }
}

/**
 * UI State for profile screen
 */
sealed class ProfileUiState : MviContract.State {
    data object Loading : ProfileUiState()

    data class Success(
        val profiles: List<PsychologicalProfile>,
        val chartData: ChartData,
        val aggregation: WellbeingAggregationResult? = null
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
