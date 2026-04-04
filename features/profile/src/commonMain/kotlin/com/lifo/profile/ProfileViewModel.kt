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

    sealed interface Effect : MviContract.Effect {
        // No one-shot effects needed for now; placeholder for future use.
    }
}

/**
 * ProfileViewModel
 *
 * Manages psychological profile display, chart data, and cross-domain
 * wellbeing aggregation (sleep↔mood, activity↔energy, growth progress,
 * SDT wellbeing trend).
 */
class ProfileViewModel constructor(
    private val profileRepository: ProfileRepository,
    private val authProvider: AuthProvider,
    private val wellbeingAggregator: WellbeingAggregator,
) : MviViewModel<ProfileContract.Intent, ProfileUiState, ProfileContract.Effect>(
    initialState = ProfileUiState.Loading
) {

    val uiState: StateFlow<ProfileUiState> get() = state

    private val currentUserId: String
        get() = authProvider.currentUserId ?: ""

    init {
        onIntent(ProfileContract.Intent.LoadProfiles())
    }

    // ── MVI ──────────────────────────────────────────────────────────────────

    override fun handleIntent(intent: ProfileContract.Intent) {
        when (intent) {
            is ProfileContract.Intent.LoadProfiles -> loadProfiles(intent.weeks)
            is ProfileContract.Intent.Refresh -> loadProfiles()
        }
    }

    // ── Business logic ────────────────────────────────────────────────────────

    private fun loadProfiles(weeks: Int = 4) {
        if (currentUserId.isEmpty()) {
            updateState { ProfileUiState.Error("User not authenticated") }
            return
        }

        scope.launch {
            profileRepository.getProfilesForUser(currentUserId, weeks).collect { result ->
                when (result) {
                    is RequestState.Loading -> updateState { ProfileUiState.Loading }
                    is RequestState.Success -> {
                        val profiles = result.data
                        if (profiles.isEmpty() || profiles.all { !it.hasSufficientData() }) {
                            updateState { ProfileUiState.Empty }
                        } else {
                            val chartData = transformToChartData(profiles)
                            // Emit profiles immediately, then enrich with aggregation
                            updateState { ProfileUiState.Success(profiles, chartData) }
                            loadAggregation()
                        }
                    }
                    is RequestState.Error -> updateState {
                        ProfileUiState.Error(result.error.message ?: "Failed to load profiles")
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Runs aggregation in the background and patches the current Success state
     * with the result once ready.
     */
    private fun loadAggregation() {
        scope.launch {
            val aggregation = runCatching { wellbeingAggregator.aggregate() }.getOrNull()
            val current = state.value
            if (current is ProfileUiState.Success) {
                updateState { current.copy(aggregation = aggregation) }
            }
        }
    }

    private fun transformToChartData(profiles: List<PsychologicalProfile>): ChartData {
        val sorted = profiles.sortedWith(compareBy({ it.year }, { it.weekNumber }))
        val peaks = mutableListOf<PeakMarker>()
        sorted.forEachIndexed { index, profile ->
            profile.stressPeaks.forEach { peak ->
                peaks.add(PeakMarker(weekIndex = index, level = peak.level, timestamp = peak.timestampMillis))
            }
        }
        return ChartData(
            weekLabels = sorted.map { it.getWeekLabel() },
            stressLine = sorted.map { it.stressBaseline },
            moodLine = sorted.map { it.moodBaseline },
            peaks = peaks
        )
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun refresh() = onIntent(ProfileContract.Intent.Refresh)
}

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class ProfileUiState : MviContract.State {
    data object Loading : ProfileUiState()

    data class Success(
        val profiles: List<PsychologicalProfile>,
        val chartData: ChartData,
        /** Null until background aggregation completes. */
        val aggregation: WellbeingAggregationResult? = null,
    ) : ProfileUiState()

    data class Error(val message: String) : ProfileUiState()
    data object Empty : ProfileUiState()
}

// ── Chart models ──────────────────────────────────────────────────────────────

data class ChartData(
    val weekLabels: List<String>,
    val stressLine: List<Float>,
    val moodLine: List<Float>,
    val peaks: List<PeakMarker>
)

data class PeakMarker(
    val weekIndex: Int,
    val level: Int,
    val timestamp: Long
)
