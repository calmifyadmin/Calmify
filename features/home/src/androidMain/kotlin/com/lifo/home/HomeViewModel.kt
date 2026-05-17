package com.lifo.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import com.google.firebase.storage.FirebaseStorage
import com.lifo.util.auth.AuthProvider
import com.lifo.util.auth.UserIdentityResolver
import com.lifo.home.domain.model.*
import com.lifo.home.domain.model.DailyInsightData
import com.lifo.home.domain.usecase.*
import com.lifo.mongo.database.ImageToDeleteQueries
import com.lifo.util.connectivity.ConnectivityObserver
import com.lifo.util.model.*
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.Diaries
import com.lifo.util.repository.FeedRepository
import com.lifo.util.repository.MongoRepository
import com.lifo.util.repository.ThreadHydrator
import com.lifo.util.repository.ThreadRepository
import com.lifo.util.repository.ProfileSettingsRepository
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.UnifiedContentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.time.ZonedDateTime
import kotlin.coroutines.resumeWithException

// ── Data classes used by callers (kept outside Contract) ──────────────────────

data class HomeUiState(
    val diaries: Diaries = RequestState.Loading,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val dateIsSelected: Boolean = false,
    val selectedDate: ZonedDateTime? = null,
    val error: String? = null
)

data class UnifiedHomeState(
    val items: List<HomeContentItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedFilter: ContentFilter = ContentFilter.ALL,
    val searchQuery: String = "",
    val selectedDate: ZonedDateTime? = null,
    val dateIsSelected: Boolean = false,
    val error: String? = null,
    val isEmpty: Boolean = false
)

// ── MVI Contract ──────────────────────────────────────────────────────────────

object HomeContract {

    sealed interface Intent : MviContract.Intent {
        data object LoadDiaries : Intent
        data object RefreshDiaries : Intent
        data class GetDiaries(val zonedDateTime: ZonedDateTime? = null) : Intent
        data object DeleteAllDiaries : Intent
        data object SignOut : Intent
        data class UpdateFilter(val filter: ContentFilter) : Intent
        data class UpdateSearchQuery(val query: String) : Intent
        data object ClearSearch : Intent
        data object NavigateToPreviousWeek : Intent
        data object NavigateToNextWeek : Intent
        data object ResetToCurrentWeek : Intent
        data class UpdateTimeRange(val timeRange: TimeRange) : Intent
        data object RefreshRedesignData : Intent
        data object LoadUnifiedContent : Intent
        data object RefreshUnifiedContent : Intent
        data object LoadDailyInsights : Intent
    }

    data class State(
        // Legacy HomeUiState fields
        val diariesState: Diaries = RequestState.Loading,
        val isLoadingDiaries: Boolean = false,
        val isRefreshingDiaries: Boolean = false,
        val dateIsSelected: Boolean = false,
        val selectedDate: ZonedDateTime? = null,
        val diariesError: String? = null,

        // Unified content state
        val unifiedItems: List<HomeContentItem> = emptyList(),
        val isLoadingUnified: Boolean = true,
        val isRefreshingUnified: Boolean = false,
        val selectedFilter: ContentFilter = ContentFilter.ALL,
        val searchQuery: String = "",
        val unifiedError: String? = null,
        val isUnifiedEmpty: Boolean = false,

        // Daily insights
        val dailyInsights: List<DailyInsightData> = emptyList(),
        val currentWeekOffset: Int = 0,

        // Redesign state
        val homeRedesignState: HomeRedesignState = HomeRedesignState(),
        val todayPulse: TodayPulse? = null,
        val moodDistribution: MoodDistribution? = null,
        val dominantMood: DominantMood? = null,
        val cognitivePatterns: List<CognitivePatternSummary> = emptyList(),
        val topicsFrequency: List<TopicFrequency> = emptyList(),
        val emergingTopic: TopicTrend? = null,
        val achievementsState: AchievementsState? = null,
        val selectedTimeRange: TimeRange = TimeRange.MONTH,
        val quickActionState: QuickActionState = QuickActionState(),

        // Community preview
        val communityThreads: List<ThreadRepository.Thread> = emptyList(),

        // Social profile avatar
        val socialAvatarUrl: String? = null,

        // Bio-signal Today narrative (Phase 5) — null when user has no bio data
        // or hasn't connected a wearable; absence = silence, not error.
        val bioContext: com.lifo.home.domain.model.HomeBioContext? = null,

        // Cross-signal pattern (Phase 5.4) — null when <6 wks of data or no
        // meaningful lift; PRO-only render. Dismissable; resets on session reload.
        val crossSignalPattern: com.lifo.home.domain.usecase.CrossSignalPattern? = null,
        val crossSignalDismissed: Boolean = false,

        // Weekly bio narrative (Phase 8.2) — PRO-only, null when <3 days HRV
        // in last 7 or no meaningful delta vs baseline.
        val weeklyBioNarrative: com.lifo.home.domain.usecase.WeeklyBioNarrative? = null,

        // Network
        val networkStatus: ConnectivityObserver.Status = ConnectivityObserver.Status.Unavailable,

        // Global loading
        val isLoading: Boolean = false
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data object DeleteAllDiariesSuccess : Effect
        data class DeleteAllDiariesError(val error: Throwable) : Effect
        data class ShowError(val message: String) : Effect
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

internal class HomeViewModel constructor(
    private val connectivity: ConnectivityObserver,
    private val authProvider: AuthProvider,
    private val storage: FirebaseStorage,
    private val imageToDeleteQueries: ImageToDeleteQueries,
    private val unifiedContentRepository: UnifiedContentRepository,
    private val diaryRepository: MongoRepository,
    private val insightRepository: com.lifo.util.repository.InsightRepository,
    private val savedStateHandle: SavedStateHandle,
    private val calculateMoodDistributionUseCase: CalculateMoodDistributionUseCase,
    private val aggregateCognitivePatternsUseCase: AggregateCognitivePatternsUseCase,
    private val calculateTopicsFrequencyUseCase: CalculateTopicsFrequencyUseCase,
    private val calculateTodayPulseUseCase: CalculateTodayPulseUseCase,
    private val getAchievementsUseCase: GetAchievementsUseCase,
    private val feedRepository: FeedRepository,
    private val threadHydrator: ThreadHydrator,
    private val socialGraphRepository: SocialGraphRepository,
    private val profileSettingsRepository: ProfileSettingsRepository,
    private val getHomeBioContextUseCase: com.lifo.home.domain.usecase.GetHomeBioContextUseCase,
    private val getCrossSignalPatternUseCase: com.lifo.home.domain.usecase.GetCrossSignalPatternUseCase,
    private val getWeeklyBioNarrativeUseCase: com.lifo.home.domain.usecase.GetWeeklyBioNarrativeUseCase,
) : MviViewModel<HomeContract.Intent, HomeContract.State, HomeContract.Effect>(
    initialState = HomeContract.State()
) {

    companion object {
        private const val KEY_DATE_SELECTED = "date_is_selected"
        private const val KEY_SELECTED_DATE = "selected_date"
        private const val RETRY_DELAY = 1000L
        private const val MAX_RETRIES = 3
    }

    // ── Jobs management ───────────────────────────────────────────────────
    private var diariesJob: Job? = null
    private var refreshJob: Job? = null
    private var deleteJob: Job? = null

    // ── Cached data for redesign calculations ─────────────────────────────
    private var cachedInsights: List<DiaryInsight> = emptyList()
    private var cachedDiaries: List<Diary> = emptyList()

    // ── Legacy Compose state bridge (callers read these directly) ─────────
    var diaries: MutableState<Diaries> = mutableStateOf(RequestState.Loading)
        private set

    var dateIsSelected by mutableStateOf(
        savedStateHandle.get<Boolean>(KEY_DATE_SELECTED) ?: false
    )
        private set

    // ══════════════════════════════════════════════════════════════════════
    // Backward-compatible StateFlow aliases derived from the single state
    // ══════════════════════════════════════════════════════════════════════

    val uiState: StateFlow<HomeUiState> = state.map { s ->
        HomeUiState(
            diaries = s.diariesState,
            isLoading = s.isLoadingDiaries,
            isRefreshing = s.isRefreshingDiaries,
            dateIsSelected = s.dateIsSelected,
            selectedDate = s.selectedDate,
            error = s.diariesError
        )
    }.stateIn(scope, SharingStarted.Eagerly, HomeUiState())

    val unifiedState: StateFlow<UnifiedHomeState> = state.map { s ->
        UnifiedHomeState(
            items = s.unifiedItems,
            isLoading = s.isLoadingUnified,
            isRefreshing = s.isRefreshingUnified,
            selectedFilter = s.selectedFilter,
            searchQuery = s.searchQuery,
            selectedDate = s.selectedDate,
            dateIsSelected = s.dateIsSelected,
            error = s.unifiedError,
            isEmpty = s.isUnifiedEmpty
        )
    }.stateIn(scope, SharingStarted.Eagerly, UnifiedHomeState())

    val networkStatus: StateFlow<ConnectivityObserver.Status> = state.map { it.networkStatus }
        .stateIn(scope, SharingStarted.Eagerly, ConnectivityObserver.Status.Unavailable)

    val isLoading: StateFlow<Boolean> = state.map { it.isLoading }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val dailyInsights: StateFlow<List<DailyInsightData>> = state.map { it.dailyInsights }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val currentWeekOffset: StateFlow<Int> = state.map { it.currentWeekOffset }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    val homeRedesignState: StateFlow<HomeRedesignState> = state.map { it.homeRedesignState }
        .stateIn(scope, SharingStarted.Eagerly, HomeRedesignState())

    val todayPulse: StateFlow<TodayPulse?> = state.map { it.todayPulse }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val moodDistribution: StateFlow<MoodDistribution?> = state.map { it.moodDistribution }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val dominantMood: StateFlow<DominantMood?> = state.map { it.dominantMood }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val cognitivePatterns: StateFlow<List<CognitivePatternSummary>> = state.map { it.cognitivePatterns }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val topicsFrequency: StateFlow<List<TopicFrequency>> = state.map { it.topicsFrequency }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val emergingTopic: StateFlow<TopicTrend?> = state.map { it.emergingTopic }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val achievementsState: StateFlow<AchievementsState?> = state.map { it.achievementsState }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val selectedTimeRange: StateFlow<TimeRange> = state.map { it.selectedTimeRange }
        .stateIn(scope, SharingStarted.Eagerly, TimeRange.MONTH)

    val quickActionState: StateFlow<QuickActionState> = state.map { it.quickActionState }
        .stateIn(scope, SharingStarted.Eagerly, QuickActionState())

    val communityThreads: StateFlow<List<ThreadRepository.Thread>> = state.map { it.communityThreads }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val socialAvatarUrl: StateFlow<String?> = state.map { it.socialAvatarUrl }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val bioContext: StateFlow<com.lifo.home.domain.model.HomeBioContext?> =
        state.map { it.bioContext }.stateIn(scope, SharingStarted.Eagerly, null)

    val crossSignalPattern: StateFlow<com.lifo.home.domain.usecase.CrossSignalPattern?> =
        state.map { if (it.crossSignalDismissed) null else it.crossSignalPattern }
            .stateIn(scope, SharingStarted.Eagerly, null)

    fun dismissCrossSignal() {
        updateState { copy(crossSignalDismissed = true) }
    }

    val weeklyBioNarrative: StateFlow<com.lifo.home.domain.usecase.WeeklyBioNarrative?> =
        state.map { it.weeklyBioNarrative }.stateIn(scope, SharingStarted.Eagerly, null)

    // ══════════════════════════════════════════════════════════════════════
    // Init
    // ══════════════════════════════════════════════════════════════════════

    init {
        restoreState()
        loadProfileSettings()
        loadSocialAvatar()

        // Observe network connectivity
        scope.launch {
            connectivity.observe()
                .catch { e ->
                }
                .collect { status ->
                    updateState { copy(networkStatus = status) }
                    // Retry loading if we regain connectivity and had an error
                    if (status == ConnectivityObserver.Status.Available &&
                        currentState.diariesError != null
                    ) {
                        retryLoading()
                    }
                }
        }

        // Initial loads
        loadDiariesInternal()
        loadUnifiedContentInternal()
        loadDailyInsightsInternal()
        loadRedesignData()
    }

    // ══════════════════════════════════════════════════════════════════════
    // MVI handleIntent
    // ══════════════════════════════════════════════════════════════════════

    override fun handleIntent(intent: HomeContract.Intent) {
        when (intent) {
            is HomeContract.Intent.LoadDiaries -> loadDiariesInternal()
            is HomeContract.Intent.RefreshDiaries -> refreshDiariesInternal()
            is HomeContract.Intent.GetDiaries -> getDiariesInternal(intent.zonedDateTime)
            is HomeContract.Intent.DeleteAllDiaries -> deleteAllDiariesInternal()
            is HomeContract.Intent.SignOut -> signOutInternal()
            is HomeContract.Intent.UpdateFilter -> updateFilterInternal(intent.filter)
            is HomeContract.Intent.UpdateSearchQuery -> updateSearchQueryInternal(intent.query)
            is HomeContract.Intent.ClearSearch -> clearSearchInternal()
            is HomeContract.Intent.NavigateToPreviousWeek -> navigateToPreviousWeekInternal()
            is HomeContract.Intent.NavigateToNextWeek -> navigateToNextWeekInternal()
            is HomeContract.Intent.ResetToCurrentWeek -> resetToCurrentWeekInternal()
            is HomeContract.Intent.UpdateTimeRange -> updateTimeRangeInternal(intent.timeRange)
            is HomeContract.Intent.RefreshRedesignData -> loadRedesignData()
            is HomeContract.Intent.LoadUnifiedContent -> loadUnifiedContentInternal()
            is HomeContract.Intent.RefreshUnifiedContent -> refreshUnifiedContentInternal()
            is HomeContract.Intent.LoadDailyInsights -> loadDailyInsightsInternal()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Backward-compatible public methods (delegate to intents or directly)
    // ══════════════════════════════════════════════════════════════════════

    fun loadDiaries() = onIntent(HomeContract.Intent.LoadDiaries)
    fun refreshDiaries() = onIntent(HomeContract.Intent.RefreshDiaries)
    fun getDiaries(zonedDateTime: ZonedDateTime? = null) = onIntent(HomeContract.Intent.GetDiaries(zonedDateTime))
    fun signOut() = onIntent(HomeContract.Intent.SignOut)
    fun updateFilter(filter: ContentFilter) = onIntent(HomeContract.Intent.UpdateFilter(filter))
    fun updateSearchQuery(query: String) = onIntent(HomeContract.Intent.UpdateSearchQuery(query))
    fun clearSearch() = onIntent(HomeContract.Intent.ClearSearch)
    fun navigateToPreviousWeek() = onIntent(HomeContract.Intent.NavigateToPreviousWeek)
    fun navigateToNextWeek() = onIntent(HomeContract.Intent.NavigateToNextWeek)
    fun resetToCurrentWeek() = onIntent(HomeContract.Intent.ResetToCurrentWeek)
    fun updateTimeRange(timeRange: TimeRange) = onIntent(HomeContract.Intent.UpdateTimeRange(timeRange))
    fun refreshRedesignData() = onIntent(HomeContract.Intent.RefreshRedesignData)
    fun loadUnifiedContent() = onIntent(HomeContract.Intent.LoadUnifiedContent)
    fun refreshUnifiedContent() = onIntent(HomeContract.Intent.RefreshUnifiedContent)
    fun loadDailyInsights() = onIntent(HomeContract.Intent.LoadDailyInsights)

    // Aliases for backward compatibility
    fun reloadDiaries() = loadDiaries()
    fun fetchDiaries(zonedDateTime: ZonedDateTime? = null) = getDiaries(zonedDateTime)
    fun loadStuff() = refreshDiaries()

    /**
     * Backward-compatible deleteAllDiaries with callbacks.
     * Internally dispatches the intent and bridges effects to callbacks.
     */
    fun deleteAllDiaries(
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (currentState.networkStatus != ConnectivityObserver.Status.Available) {
            onError(Exception("No Internet Connection."))
            return
        }

        // Launch a bridge that listens for the effect
        scope.launch {
            val effectJob = launch {
                effects.collect { effect ->
                    when (effect) {
                        is HomeContract.Effect.DeleteAllDiariesSuccess -> {
                            onSuccess()
                            cancel() // Stop listening
                        }
                        is HomeContract.Effect.DeleteAllDiariesError -> {
                            onError(effect.error)
                            cancel() // Stop listening
                        }
                        else -> { /* ignore other effects */ }
                    }
                }
            }

            // Dispatch the intent
            onIntent(HomeContract.Intent.DeleteAllDiaries)

            // Timeout: stop listening after 60s to prevent leaks
            delay(60_000)
            effectJob.cancel()
        }
    }

    // Synchronous value getters (not intents — they return values directly)

    private var cachedSocialProfile: SocialGraphRepository.SocialUser? = null
    private var cachedProfileDisplayName: String? = null

    private fun loadProfileSettings() {
        scope.launch {
            profileSettingsRepository.getProfileSettings().collect { result ->
                if (result is RequestState.Success) {
                    cachedProfileDisplayName = result.data?.displayName?.takeIf { it.isNotBlank() }
                }
            }
        }
    }

    private fun loadSocialAvatar() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            socialGraphRepository.getProfile(userId).collect { result ->
                if (result is RequestState.Success) {
                    cachedSocialProfile = result.data
                    updateState { copy(socialAvatarUrl = result.data.avatarUrl) }
                }
            }
        }
    }

    fun getUserPhotoUrl(): String? {
        return try {
            authProvider.currentUserPhotoUrl
        } catch (e: Exception) {
            null
        }
    }

    fun getUserFirstName(): String {
        return UserIdentityResolver.resolveFirstName(
            socialProfile = cachedSocialProfile,
            profileDisplayName = cachedProfileDisplayName,
            authDisplayName = authProvider.currentUserDisplayName,
            authEmail = authProvider.currentUserEmail,
        )
    }

    // Navigation methods (no state changes, purely informational)
    fun onDiaryItemClicked(diaryItem: HomeContentItem.DiaryItem) {
    }

    fun onChatItemClicked(chatItem: HomeContentItem.ChatItem) {
    }

    // ══════════════════════════════════════════════════════════════════════
    // Private implementation — all state mutations go through updateState
    // ══════════════════════════════════════════════════════════════════════

    private fun restoreState() {
        savedStateHandle.get<Long>(KEY_SELECTED_DATE)?.let { millis ->
            try {
                val selectedDate = ZonedDateTime.now().withNano(millis.toInt())
                updateState {
                    copy(
                        selectedDate = selectedDate,
                        dateIsSelected = true
                    )
                }
                dateIsSelected = true
            } catch (e: Exception) {
            }
        }
    }

    private fun saveState() {
        savedStateHandle[KEY_DATE_SELECTED] = currentState.dateIsSelected
        currentState.selectedDate?.let {
            savedStateHandle[KEY_SELECTED_DATE] = it.toEpochSecond()
        }
    }

    // ── Diaries ───────────────────────────────────────────────────────────

    private fun loadDiariesInternal() {
        getDiariesInternal(currentState.selectedDate)
    }

    private fun refreshDiariesInternal() {
        if (currentState.isRefreshingDiaries) return

        refreshJob?.cancel()
        refreshJob = scope.launch {
            updateState { copy(isRefreshingDiaries = true) }
            updateState { copy(isLoading = true) }

            try {
                delay(500)
                getDiariesInternal(currentState.selectedDate)
            } finally {
                updateState { copy(isRefreshingDiaries = false, isLoading = false) }
            }
        }
    }

    private fun getDiariesInternal(zonedDateTime: ZonedDateTime? = null) {
        val isDateSelected = zonedDateTime != null
        dateIsSelected = isDateSelected

        updateState {
            copy(
                dateIsSelected = isDateSelected,
                selectedDate = zonedDateTime,
                diariesError = null
            )
        }

        saveState()

        // Update legacy Compose state
        diaries.value = RequestState.Loading

        diariesJob?.cancel()

        diariesJob = scope.launch {
            try {
                if (isDateSelected && zonedDateTime != null) {
                    observeFilteredDiaries(zonedDateTime)
                } else {
                    observeAllDiaries()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleDiariesError(e)
            }
        }

        // Reload unified content with new date filter
        loadUnifiedContentInternal()
    }

    private suspend fun observeAllDiaries() {
        diaryRepository.getAllDiaries()
            .catch { e ->
                if (e !is CancellationException) {
                    handleDiariesError(e)
                    emit(RequestState.Error(e as Exception))
                } else {
                    throw e
                }
            }
            .collect { result ->
                updateDiariesState(result)
            }
    }

    private suspend fun observeFilteredDiaries(zonedDateTime: ZonedDateTime) {
        val dayKey = zonedDateTime.toLocalDate().toString() // "YYYY-MM-DD"
        diaryRepository.getFilteredDiaries(dayKey = dayKey)
            .catch { e ->
                if (e !is CancellationException) {
                    handleDiariesError(e)
                    emit(RequestState.Error(e as Exception))
                } else {
                    throw e
                }
            }
            .collect { result ->
                updateDiariesState(result)
            }
    }

    private fun updateDiariesState(result: Diaries) {
        val isLoadingResult = result is RequestState.Loading
        val errorMsg = if (result is RequestState.Error) {
            result.error.message ?: "Unknown error"
        } else null

        updateState {
            copy(
                diariesState = result,
                isLoadingDiaries = isLoadingResult,
                isLoading = isLoadingResult,
                diariesError = errorMsg
            )
        }

        // Update legacy Compose state
        diaries.value = result
    }

    private fun handleDiariesError(error: Throwable) {
        val errorMessage = when {
            currentState.networkStatus != ConnectivityObserver.Status.Available ->
                "No internet connection"
            error.message?.contains("timeout", ignoreCase = true) == true ->
                "Connection timeout. Please try again."
            else -> error.message ?: "Failed to load diaries"
        }

        updateState { copy(diariesError = errorMessage) }
    }

    private fun retryLoading() {
        if (currentState.diariesError != null && !currentState.isLoadingDiaries) {
            getDiariesInternal(currentState.selectedDate)
        }
    }

    // ── Delete All Diaries ────────────────────────────────────────────────

    private fun deleteAllDiariesInternal() {
        if (currentState.networkStatus != ConnectivityObserver.Status.Available) {
            sendEffect(HomeContract.Effect.DeleteAllDiariesError(Exception("No Internet Connection.")))
            return
        }

        deleteJob?.cancel()

        deleteJob = scope.launch {
            updateState { copy(isLoadingDiaries = true, isLoading = true) }

            try {
                val userId = authProvider.currentUserId
                    ?: throw Exception("User not authenticated")

                performDeleteAllDiaries(userId)
            } catch (e: Exception) {
                sendEffect(HomeContract.Effect.DeleteAllDiariesError(e))
            } finally {
                updateState { copy(isLoadingDiaries = false, isLoading = false) }
            }
        }
    }

    private suspend fun performDeleteAllDiaries(userId: String) {
        val imagesDirectory = "images/$userId"
        val storageRef = storage.reference

        try {
            // Delete images from Firebase Storage
            val listResult = storageRef.child(imagesDirectory).listAll().await()

            listResult.items.forEach { ref ->
                try {
                    ref.delete().await()
                } catch (e: Exception) {
                    val imagePath = "images/$userId/${ref.name}"
                    withContext(Dispatchers.IO) {
                        imageToDeleteQueries.addImageToDelete(
                            remoteImagePath = imagePath
                        )
                    }
                }
            }

            // Delete ALL user data from Firestore
            withContext(Dispatchers.IO) {
                when (val result = diaryRepository.deleteAllUserData()) {
                    is RequestState.Success -> {
                        withContext(Dispatchers.Main) {
                            sendEffect(HomeContract.Effect.DeleteAllDiariesSuccess)
                            getDiariesInternal()
                        }
                    }
                    is RequestState.Error -> {
                        withContext(Dispatchers.Main) {
                            sendEffect(HomeContract.Effect.DeleteAllDiariesError(result.error))
                        }
                    }
                    else -> {
                    }
                }
            }
        } catch (e: Exception) {
            sendEffect(HomeContract.Effect.DeleteAllDiariesError(e))
        }
    }

    // ── Sign Out ──────────────────────────────────────────────────────────

    private fun signOutInternal() {
        scope.launch { authProvider.signOut() }
    }

    // ── Unified Content ───────────────────────────────────────────────────

    private fun getCurrentUserId(): String {
        return authProvider.currentUserId
            ?: throw IllegalStateException("User not authenticated")
    }

    private fun loadUnifiedContentInternal() {
        scope.launch {
            updateState { copy(isLoadingUnified = true, unifiedError = null) }

            try {
                val userId = getCurrentUserId()
                val s = currentState

                val contentFlow = if (s.dateIsSelected && s.selectedDate != null) {
                    val selectedDate = s.selectedDate
                    val startOfDay = selectedDate.toLocalDate().atStartOfDay(selectedDate.zone)
                    val endOfDay = startOfDay.plusDays(1).minusNanos(1)

                    unifiedContentRepository.filterByDateRange(
                        ownerId = userId,
                        startDate = startOfDay.toEpochSecond() * 1000,
                        endDate = endOfDay.toEpochSecond() * 1000
                    )
                } else {
                    unifiedContentRepository.applyFilter(
                        ownerId = userId,
                        filter = s.selectedFilter,
                        searchQuery = s.searchQuery
                    )
                }

                contentFlow.collect { items ->
                    updateState {
                        copy(
                            unifiedItems = items,
                            isLoadingUnified = false,
                            isUnifiedEmpty = items.isEmpty()
                        )
                    }
                }
            } catch (e: Exception) {
                updateState {
                    copy(
                        isLoadingUnified = false,
                        unifiedError = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    private fun refreshUnifiedContentInternal() {
        if (currentState.isRefreshingUnified) return

        scope.launch {
            updateState { copy(isRefreshingUnified = true) }

            try {
                delay(500)
                val userId = getCurrentUserId()
                unifiedContentRepository.getUnifiedContent(userId)
                    .collect { items ->
                        updateState {
                            copy(
                                unifiedItems = items,
                                isRefreshingUnified = false,
                                isUnifiedEmpty = items.isEmpty(),
                                unifiedError = null
                            )
                        }
                    }
            } catch (e: Exception) {
                updateState {
                    copy(
                        isRefreshingUnified = false,
                        unifiedError = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    private fun updateFilterInternal(filter: ContentFilter) {
        updateState { copy(selectedFilter = filter) }
        loadUnifiedContentInternal()
    }

    private fun updateSearchQueryInternal(query: String) {
        updateState { copy(searchQuery = query) }

        // Debounce search
        scope.launch {
            delay(300)
            if (currentState.searchQuery == query) {
                loadUnifiedContentInternal()
            }
        }
    }

    private fun clearSearchInternal() {
        updateState { copy(searchQuery = "") }
        loadUnifiedContentInternal()
    }

    // ── Week Navigation ───────────────────────────────────────────────────

    private fun navigateToPreviousWeekInternal() {
        updateState { copy(currentWeekOffset = currentWeekOffset - 1) }
        loadDailyInsightsInternal()
    }

    private fun navigateToNextWeekInternal() {
        updateState { copy(currentWeekOffset = currentWeekOffset + 1) }
        loadDailyInsightsInternal()
    }

    private fun resetToCurrentWeekInternal() {
        updateState { copy(currentWeekOffset = 0) }
        loadDailyInsightsInternal()
    }

    // ── Daily Insights ────────────────────────────────────────────────────

    private fun loadDailyInsightsInternal() {
        scope.launch {
            try {
                insightRepository.getAllInsights().collect { result ->
                    when (result) {
                        is RequestState.Success -> {
                            val insights = result.data
                            val now = ZonedDateTime.now()

                            val currentDayOfWeek = now.dayOfWeek.value
                            val daysFromMonday = currentDayOfWeek - 1
                            val currentWeekMonday = now.minusDays(daysFromMonday.toLong())
                            val targetWeekMonday = currentWeekMonday.plusWeeks(currentState.currentWeekOffset.toLong())

                            val dailyData = (0 until 7).map { dayIndex ->
                                val targetDate = targetWeekMonday.plusDays(dayIndex.toLong())
                                val targetDayKey = targetDate.toLocalDate().toString()

                                val dayInsights = insights.filter { insight ->
                                    insight.dayKey == targetDayKey
                                }


                                val avgMagnitude = if (dayInsights.isNotEmpty()) {
                                    dayInsights.map { it.sentimentMagnitude }.average().toFloat()
                                } else {
                                    0f
                                }

                                val dominantEmotion = if (dayInsights.isNotEmpty()) {
                                    dayInsights
                                        .groupBy { it.getSentimentLabel() }
                                        .maxByOrNull { it.value.size }
                                        ?.key ?: SentimentLabel.NEUTRAL
                                } else {
                                    SentimentLabel.NEUTRAL
                                }

                                val dayLabel = when (targetDate.dayOfWeek.value) {
                                    1 -> "M"
                                    2 -> "T"
                                    3 -> "W"
                                    4 -> "T"
                                    5 -> "F"
                                    6 -> "S"
                                    7 -> "S"
                                    else -> "?"
                                }

                                DailyInsightData(
                                    date = targetDate.toInstant().let { kotlinx.datetime.Instant.fromEpochMilliseconds(it.toEpochMilli()) },
                                    dayLabel = dayLabel,
                                    sentimentMagnitude = avgMagnitude,
                                    dominantEmotion = dominantEmotion,
                                    diaryCount = dayInsights.size
                                )
                            }

                            updateState { copy(dailyInsights = dailyData) }

                            dailyData.forEachIndexed { index, data ->
                            }
                        }
                        is RequestState.Error -> {
                        }
                        else -> {
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    // ── Redesign Data ─────────────────────────────────────────────────────

    private fun loadRedesignData() {
        scope.launch {
            try {
                updateState {
                    copy(
                        homeRedesignState = homeRedesignState.copy(
                            heroLoadingState = SectionLoadingState(isLoading = true)
                        )
                    )
                }

                val insightsDeferred = async { loadInsightsForRedesign() }
                val diariesDeferred = async { loadDiariesForRedesign() }
                val communityDeferred = async { loadCommunityThreads() }
                val bioDeferred = async { runCatching { getHomeBioContextUseCase() }.getOrNull() }
                val crossSignalDeferred = async { runCatching { getCrossSignalPatternUseCase() }.getOrNull() }
                val narrativeDeferred = async { runCatching { getWeeklyBioNarrativeUseCase() }.getOrNull() }

                cachedInsights = insightsDeferred.await()
                cachedDiaries = diariesDeferred.await()
                communityDeferred.await()
                val bio = bioDeferred.await()
                val pattern = crossSignalDeferred.await()
                val narrative = narrativeDeferred.await()
                updateState { copy(bioContext = bio, crossSignalPattern = pattern, weeklyBioNarrative = narrative) }

                calculateTodayPulse()
                calculateMoodDistribution()
                calculateCognitivePatterns()
                calculateTopicsFrequency()
                calculateAchievements()

                val userName = UserIdentityResolver.resolveDisplayName(
                    socialProfile = cachedSocialProfile,
                    profileDisplayName = cachedProfileDisplayName,
                    authDisplayName = authProvider.currentUserDisplayName,
                    authEmail = authProvider.currentUserEmail,
                )

                updateState {
                    copy(
                        homeRedesignState = homeRedesignState.copy(
                            userName = userName,
                            todayPulse = todayPulse,
                            moodDistribution = moodDistribution,
                            cognitivePatterns = cognitivePatterns,
                            topicsFrequency = topicsFrequency,
                            emergingTopic = emergingTopic,
                            heroLoadingState = SectionLoadingState(isLoading = false),
                            insightsLoadingState = SectionLoadingState(isLoading = false),
                            achievementsLoadingState = SectionLoadingState(isLoading = false)
                        )
                    )
                }


            } catch (e: Exception) {
                updateState {
                    copy(
                        homeRedesignState = homeRedesignState.copy(
                            heroLoadingState = SectionLoadingState(
                                isLoading = false,
                                hasError = true,
                                errorMessage = e.message
                            )
                        )
                    )
                }
            }
        }
    }

    private suspend fun loadCommunityThreads() {
        val userId = authProvider.currentUserId ?: return
        try {
            feedRepository.getForYouFeed(userId, pageSize = 3).first { requestState ->
                when (requestState) {
                    is RequestState.Success -> {
                        val hydrated = threadHydrator.hydrate(requestState.data.items, userId)
                        updateState { copy(communityThreads = hydrated.take(3)) }
                        true
                    }
                    is RequestState.Error -> true
                    else -> false
                }
            }
        } catch (e: Exception) {
            // Non-critical — silently ignore
        }
    }

    private suspend fun loadInsightsForRedesign(): List<DiaryInsight> {
        return try {
            var result: List<DiaryInsight> = emptyList()
            insightRepository.getAllInsights().first { requestState ->
                when (requestState) {
                    is RequestState.Success -> {
                        result = requestState.data
                        true
                    }
                    is RequestState.Error -> true
                    else -> false
                }
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun loadDiariesForRedesign(): List<Diary> {
        return try {
            var result: List<Diary> = emptyList()
            diaryRepository.getAllDiaries().first { requestState ->
                when (requestState) {
                    is RequestState.Success -> {
                        result = requestState.data.values.flatten()
                        true
                    }
                    is RequestState.Error -> true
                    else -> false
                }
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun calculateTodayPulse() {
        try {
            val result = calculateTodayPulseUseCase(cachedInsights)
            updateState { copy(todayPulse = result.pulse) }

            updateState {
                copy(
                    quickActionState = quickActionState.copy(
                        hasUnreadInsights = cachedInsights.any { insight ->
                            insight.dayKey == java.time.LocalDate.now().toString()
                        }
                    )
                )
            }

        } catch (e: Exception) {
        }
    }

    private fun calculateMoodDistribution() {
        try {
            val timeRange = currentState.selectedTimeRange
            val distribution = calculateMoodDistributionUseCase(cachedInsights, timeRange)
            val dominant = calculateMoodDistributionUseCase.getDominantMood(distribution)

            updateState {
                copy(
                    moodDistribution = distribution,
                    dominantMood = dominant
                )
            }

        } catch (e: Exception) {
        }
    }

    private fun calculateCognitivePatterns() {
        try {
            val timeRange = currentState.selectedTimeRange
            val result = aggregateCognitivePatternsUseCase(cachedInsights, timeRange)
            updateState { copy(cognitivePatterns = result.patterns) }

        } catch (e: Exception) {
        }
    }

    private fun calculateTopicsFrequency() {
        try {
            val timeRange = currentState.selectedTimeRange
            val result = calculateTopicsFrequencyUseCase(cachedInsights, timeRange)
            updateState {
                copy(
                    topicsFrequency = result.topics,
                    emergingTopic = result.emergingTopic
                )
            }

        } catch (e: Exception) {
        }
    }

    private fun calculateAchievements() {
        try {
            val result = getAchievementsUseCase(
                diaries = cachedDiaries,
                insights = cachedInsights,
                earnedBadgeIds = emptySet(),
                badgeEarnedDates = emptyMap()
            )
            updateState { copy(achievementsState = result) }

        } catch (e: Exception) {
        }
    }

    private fun updateTimeRangeInternal(timeRange: TimeRange) {
        updateState {
            copy(
                selectedTimeRange = timeRange,
                homeRedesignState = homeRedesignState.copy(selectedTimeRange = timeRange)
            )
        }

        scope.launch {
            calculateMoodDistribution()
            calculateCognitivePatterns()
            calculateTopicsFrequency()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        diariesJob?.cancel()
        refreshJob?.cancel()
        deleteJob?.cancel()
    }
}

