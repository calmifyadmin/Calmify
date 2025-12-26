package com.lifo.home

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.lifo.home.domain.model.*
import com.lifo.home.domain.usecase.*
import com.lifo.mongo.database.dao.ImageToDeleteDao
import com.lifo.mongo.database.entity.ImageToDelete
import com.lifo.mongo.repository.Diaries
import com.lifo.mongo.repository.MongoRepository
import com.lifo.mongo.repository.UnifiedContentRepository
import com.lifo.util.connectivity.ConnectivityObserver
import com.lifo.util.connectivity.NetworkConnectivityObserver
import com.lifo.util.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.coroutines.resumeWithException

// ViewModel state representation
data class HomeUiState(
    val diaries: Diaries = RequestState.Loading,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val dateIsSelected: Boolean = false,
    val selectedDate: ZonedDateTime? = null,
    val error: String? = null
)

// New unified state for the home feed
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

// Daily insight data for chart visualization (last 7 days)
data class DailyInsightData(
    val date: ZonedDateTime,
    val dayLabel: String,                // "M", "T", "W", etc.
    val sentimentMagnitude: Float,       // Average sentiment magnitude for the day
    val dominantEmotion: SentimentLabel, // Dominant sentiment for color
    val diaryCount: Int                  // Number of diaries for that day
)

@RequiresApi(Build.VERSION_CODES.N)
@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val connectivity: NetworkConnectivityObserver,
    private val imageToDeleteDao: ImageToDeleteDao,
    private val unifiedContentRepository: UnifiedContentRepository,
    private val diaryRepository: MongoRepository,
    private val insightRepository: com.lifo.mongo.repository.InsightRepository,
    private val savedStateHandle: SavedStateHandle,
    // New Use Cases for Home Redesign
    private val calculateMoodDistributionUseCase: CalculateMoodDistributionUseCase,
    private val aggregateCognitivePatternsUseCase: AggregateCognitivePatternsUseCase,
    private val calculateTopicsFrequencyUseCase: CalculateTopicsFrequencyUseCase,
    private val calculateTodayPulseUseCase: CalculateTodayPulseUseCase,
    private val getAchievementsUseCase: GetAchievementsUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
        private const val KEY_DATE_SELECTED = "date_is_selected"
        private const val KEY_SELECTED_DATE = "selected_date"
        private const val RETRY_DELAY = 1000L
        private const val MAX_RETRIES = 3
    }

    // Jobs management
    private var diariesJob: Job? = null
    private var refreshJob: Job? = null
    private var deleteJob: Job? = null

    // Network status
    private val _networkStatus = MutableStateFlow(ConnectivityObserver.Status.Unavailable)
    val networkStatus: StateFlow<ConnectivityObserver.Status> = _networkStatus.asStateFlow()

    // UI State using StateFlow for better state management
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Unified content state
    private val _unifiedState = MutableStateFlow(UnifiedHomeState())
    val unifiedState: StateFlow<UnifiedHomeState> = _unifiedState.asStateFlow()

    // Daily insights state (current week M-S)
    private val _dailyInsights = MutableStateFlow<List<DailyInsightData>>(emptyList())
    val dailyInsights: StateFlow<List<DailyInsightData>> = _dailyInsights.asStateFlow()

    // Week navigation state
    private val _currentWeekOffset = MutableStateFlow(0) // 0 = current week, -1 = last week, etc.
    val currentWeekOffset: StateFlow<Int> = _currentWeekOffset.asStateFlow()

    // ==================== NEW REDESIGN STATE ====================

    // Home Redesign complete state
    private val _homeRedesignState = MutableStateFlow(HomeRedesignState())
    val homeRedesignState: StateFlow<HomeRedesignState> = _homeRedesignState.asStateFlow()

    // Today's Pulse (Hero Section)
    private val _todayPulse = MutableStateFlow<TodayPulse?>(null)
    val todayPulse: StateFlow<TodayPulse?> = _todayPulse.asStateFlow()

    // Mood Distribution (Donut Chart)
    private val _moodDistribution = MutableStateFlow<MoodDistribution?>(null)
    val moodDistribution: StateFlow<MoodDistribution?> = _moodDistribution.asStateFlow()

    // Dominant Mood
    private val _dominantMood = MutableStateFlow<DominantMood?>(null)
    val dominantMood: StateFlow<DominantMood?> = _dominantMood.asStateFlow()

    // Cognitive Patterns
    private val _cognitivePatterns = MutableStateFlow<List<CognitivePatternSummary>>(emptyList())
    val cognitivePatterns: StateFlow<List<CognitivePatternSummary>> = _cognitivePatterns.asStateFlow()

    // Topics Frequency (Word Cloud)
    private val _topicsFrequency = MutableStateFlow<List<TopicFrequency>>(emptyList())
    val topicsFrequency: StateFlow<List<TopicFrequency>> = _topicsFrequency.asStateFlow()

    // Emerging Topic
    private val _emergingTopic = MutableStateFlow<TopicTrend?>(null)
    val emergingTopic: StateFlow<TopicTrend?> = _emergingTopic.asStateFlow()

    // Achievements State
    private val _achievementsState = MutableStateFlow<AchievementsState?>(null)
    val achievementsState: StateFlow<AchievementsState?> = _achievementsState.asStateFlow()

    // Selected time range for insights
    private val _selectedTimeRange = MutableStateFlow(TimeRange.MONTH)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    // Quick action state
    private val _quickActionState = MutableStateFlow(QuickActionState())
    val quickActionState: StateFlow<QuickActionState> = _quickActionState.asStateFlow()

    // Cached insights and diaries for calculations
    private var cachedInsights: List<DiaryInsight> = emptyList()
    private var cachedDiaries: List<Diary> = emptyList()

    // Legacy state holders for backward compatibility
    var diaries: MutableState<Diaries> = mutableStateOf(RequestState.Loading)
        private set

    var dateIsSelected by mutableStateOf(
        savedStateHandle.get<Boolean>(KEY_DATE_SELECTED) ?: false
    )
        private set

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Restore state if available
        restoreState()

        // Start observing network connectivity
        viewModelScope.launch {
            connectivity.observe()
                .catch { e ->
                    Log.e(TAG, "Network observation error", e)
                }
                .collect { status ->
                    _networkStatus.value = status
                    // Retry loading if we regain connectivity and had an error
                    if (status == ConnectivityObserver.Status.Available &&
                        _uiState.value.error != null) {
                        retryLoading()
                    }
                }
        }

        // Initial load
        loadDiaries()

        // Load unified content
        loadUnifiedContent()

        // Load daily insights for chart
        loadDailyInsights()

        // Load redesign data (new components)
        loadRedesignData()
    }

    private fun restoreState() {
        savedStateHandle.get<Long>(KEY_SELECTED_DATE)?.let { millis ->
            // Restore selected date if available
            try {
                val selectedDate = ZonedDateTime.now().withNano(millis.toInt())
                _uiState.update { it.copy(
                    selectedDate = selectedDate,
                    dateIsSelected = true
                )}
                dateIsSelected = true
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring date", e)
            }
        }
    }

    private fun saveState() {
        savedStateHandle[KEY_DATE_SELECTED] = dateIsSelected
        _uiState.value.selectedDate?.let {
            savedStateHandle[KEY_SELECTED_DATE] = it.toEpochSecond()
        }
    }

    fun loadDiaries() {
        getDiaries(_uiState.value.selectedDate)
    }

    fun refreshDiaries() {
        if (_uiState.value.isRefreshing) return

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            _isLoading.value = true

            try {
                // Add minimum refresh time for better UX
                delay(500)
                getDiaries(_uiState.value.selectedDate)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
                _isLoading.value = false
            }
        }
    }

    fun getDiaries(zonedDateTime: ZonedDateTime? = null) {
        dateIsSelected = zonedDateTime != null
        _uiState.update { it.copy(
            dateIsSelected = dateIsSelected,
            selectedDate = zonedDateTime,
            error = null
        )}

        // Update unified state with selected date
        _unifiedState.update { it.copy(
            selectedDate = zonedDateTime,
            dateIsSelected = zonedDateTime != null
        )}

        // Save state
        saveState()

        // Update legacy state
        diaries.value = RequestState.Loading

        // Cancel previous job gracefully
        diariesJob?.cancel()

        diariesJob = viewModelScope.launch {
            try {
                if (dateIsSelected && zonedDateTime != null) {
                    observeFilteredDiaries(zonedDateTime)
                } else {
                    observeAllDiaries()
                }
            } catch (e: CancellationException) {
                // Expected cancellation when switching dates - don't log as error
                Log.d(TAG, "Diaries loading cancelled (switching filters)")
                throw e // Re-throw to properly cancel the coroutine
            } catch (e: Exception) {
                Log.e(TAG, "Error in getDiaries", e)
                handleError(e)
            }
        }

        // Reload unified content with new date filter
        loadUnifiedContent()
    }

    private suspend fun observeAllDiaries() {
        diaryRepository.getAllDiaries()
            .catch { e ->
                // Don't log cancellation as error - it's expected when switching filters
                if (e !is CancellationException) {
                    Log.e(TAG, "Error observing all diaries", e)
                    handleError(e)
                    emit(RequestState.Error(e as Exception))
                } else {
                    throw e // Re-throw cancellation to properly cancel flow
                }
            }
            .collect { result ->
                updateDiariesState(result)
            }
    }

    private suspend fun observeFilteredDiaries(zonedDateTime: ZonedDateTime) {
        diaryRepository.getFilteredDiaries(zonedDateTime = zonedDateTime)
            .catch { e ->
                // Don't log cancellation as error - it's expected when switching filters
                if (e !is CancellationException) {
                    Log.e(TAG, "Error observing filtered diaries", e)
                    handleError(e)
                    emit(RequestState.Error(e as Exception))
                } else {
                    throw e // Re-throw cancellation to properly cancel flow
                }
            }
            .collect { result ->
                updateDiariesState(result)
            }
    }

    private fun updateDiariesState(result: Diaries) {
        // Update new state
        _uiState.update { currentState ->
            currentState.copy(
                diaries = result,
                isLoading = result is RequestState.Loading,
                error = if (result is RequestState.Error) {
                    result.error.message ?: "Unknown error"
                } else null
            )
        }

        // Update legacy state
        diaries.value = result
        _isLoading.value = result is RequestState.Loading
    }

    private fun handleError(error: Throwable) {
        Log.e(TAG, "Error loading diaries", error)
        val errorMessage = when {
            _networkStatus.value != ConnectivityObserver.Status.Available ->
                "No internet connection"
            error.message?.contains("timeout", ignoreCase = true) == true ->
                "Connection timeout. Please try again."
            else -> error.message ?: "Failed to load diaries"
        }

        _uiState.update { it.copy(error = errorMessage) }
    }

    private fun retryLoading() {
        if (_uiState.value.error != null && !_uiState.value.isLoading) {
            getDiaries(_uiState.value.selectedDate)
        }
    }

    fun getUserPhotoUrl(): String? {
        return try {
            FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user photo", e)
            null
        }
    }

    fun deleteAllDiaries(
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (_networkStatus.value != ConnectivityObserver.Status.Available) {
            onError(Exception("No Internet Connection."))
            return
        }

        // Cancel any existing delete job
        deleteJob?.cancel()

        deleteJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                    ?: throw Exception("User not authenticated")

                deleteAllDiariesInternal(userId, onSuccess, onError)
            } catch (e: Exception) {
                Log.e(TAG, "Error in deleteAllDiaries", e)
                onError(e)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun deleteAllDiariesInternal(
        userId: String,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val imagesDirectory = "images/$userId"
        val storage = FirebaseStorage.getInstance().reference

        try {
            // Delete images from Firebase Storage
            val listResult = storage.child(imagesDirectory).listAll().await()

            // Delete each image
            listResult.items.forEach { ref ->
                try {
                    ref.delete().await()
                } catch (e: Exception) {
                    // Queue for later deletion if fails
                    val imagePath = "images/$userId/${ref.name}"
                    withContext(Dispatchers.IO) {
                        imageToDeleteDao.addImageToDelete(
                            ImageToDelete(remoteImagePath = imagePath)
                        )
                    }
                }
            }

            // Delete ALL user data from Firestore (diaries, insights, profiles, chat, profile_settings, etc.)
            withContext(Dispatchers.IO) {
                when (val result = diaryRepository.deleteAllUserData()) {
                    is RequestState.Success -> {
                        withContext(Dispatchers.Main) {
                            onSuccess()
                            // Refresh diaries after deletion
                            getDiaries()
                        }
                    }
                    is RequestState.Error -> {
                        withContext(Dispatchers.Main) {
                            onError(result.error)
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unexpected state during deleteAllUserData")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all diaries", e)
            onError(e)
        }
    }

    // Aliases for backward compatibility
    fun reloadDiaries() = loadDiaries()
    fun fetchDiaries(zonedDateTime: ZonedDateTime? = null) = getDiaries(zonedDateTime)
    fun loadStuff() = refreshDiaries()

    // UNIFIED CONTENT METHODS
    
    private fun getCurrentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid 
            ?: throw IllegalStateException("User not authenticated")
    }

    fun loadUnifiedContent() {
        viewModelScope.launch {
            _unifiedState.update { it.copy(isLoading = true, error = null) }

            try {
                val userId = getCurrentUserId()
                val currentState = _unifiedState.value

                // Choose appropriate filtering method based on whether date is selected
                val contentFlow = if (currentState.dateIsSelected && currentState.selectedDate != null) {
                    // Filter by date range (for the selected day)
                    val selectedDate = currentState.selectedDate
                    val startOfDay = selectedDate.toLocalDate().atStartOfDay(selectedDate.zone)
                    val endOfDay = startOfDay.plusDays(1).minusNanos(1)

                    unifiedContentRepository.filterByDateRange(
                        ownerId = userId,
                        startDate = startOfDay.toEpochSecond() * 1000, // Convert to millis
                        endDate = endOfDay.toEpochSecond() * 1000
                    )
                } else {
                    // Normal filtering (no date constraint)
                    unifiedContentRepository.applyFilter(
                        ownerId = userId,
                        filter = currentState.selectedFilter,
                        searchQuery = currentState.searchQuery
                    )
                }

                contentFlow.collect { items ->
                    _unifiedState.update { state ->
                        state.copy(
                            items = items,
                            isLoading = false,
                            isEmpty = items.isEmpty()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading unified content", e)
                _unifiedState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    fun refreshUnifiedContent() {
        if (_unifiedState.value.isRefreshing) return
        
        viewModelScope.launch {
            _unifiedState.update { it.copy(isRefreshing = true) }
            
            try {
                delay(500) // Minimum refresh time for better UX
                val userId = getCurrentUserId()
                unifiedContentRepository.getUnifiedContent(userId)
                    .collect { items ->
                        _unifiedState.update { state ->
                            state.copy(
                                items = items,
                                isRefreshing = false,
                                isEmpty = items.isEmpty(),
                                error = null
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing unified content", e)
                _unifiedState.update { 
                    it.copy(
                        isRefreshing = false,
                        error = e.message ?: "Unknown error occurred"
                    ) 
                }
            }
        }
    }

    fun updateFilter(filter: ContentFilter) {
        _unifiedState.update { it.copy(selectedFilter = filter) }
        loadUnifiedContent()
    }

    fun updateSearchQuery(query: String) {
        _unifiedState.update { it.copy(searchQuery = query) }
        
        // Debounce search
        viewModelScope.launch {
            delay(300) // 300ms debounce
            if (_unifiedState.value.searchQuery == query) {
                loadUnifiedContent()
            }
        }
    }

    fun clearSearch() {
        _unifiedState.update { it.copy(searchQuery = "") }
        loadUnifiedContent()
    }

    // Navigation methods
    fun onDiaryItemClicked(diaryItem: HomeContentItem.DiaryItem) {
        // Will be handled by navigation in the UI layer
        Log.d(TAG, "Diary item clicked: ${diaryItem.title}")
    }

    fun onChatItemClicked(chatItem: HomeContentItem.ChatItem) {
        // Will be handled by navigation in the UI layer
        Log.d(TAG, "Chat item clicked: ${chatItem.title}")
    }

    /**
     * Navigate to previous week
     */
    fun navigateToPreviousWeek() {
        _currentWeekOffset.value -= 1
        loadDailyInsights()
    }

    /**
     * Navigate to next week
     */
    fun navigateToNextWeek() {
        _currentWeekOffset.value += 1
        loadDailyInsights()
    }

    /**
     * Reset to current week
     */
    fun resetToCurrentWeek() {
        _currentWeekOffset.value = 0
        loadDailyInsights()
    }

    /**
     * Load daily insights for the current week (M-S) for chart visualization
     * Week starts on Monday and ends on Sunday
     */
    fun loadDailyInsights() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading daily insights for week offset: ${_currentWeekOffset.value}...")
                insightRepository.getAllInsights().collect { result ->
                    when (result) {
                        is RequestState.Success -> {
                            val insights = result.data
                            Log.d(TAG, "Loaded ${insights.size} insights from repository")
                            val now = ZonedDateTime.now()

                            // Calculate Monday of the target week
                            val currentDayOfWeek = now.dayOfWeek.value // 1 = Monday, 7 = Sunday
                            val daysFromMonday = currentDayOfWeek - 1 // Days since last Monday
                            val currentWeekMonday = now.minusDays(daysFromMonday.toLong())

                            // Apply week offset
                            val targetWeekMonday = currentWeekMonday.plusWeeks(_currentWeekOffset.value.toLong())

                            // Group insights by day (Monday to Sunday)
                            val dailyData = (0 until 7).map { dayIndex ->
                                val targetDate = targetWeekMonday.plusDays(dayIndex.toLong())
                                val targetDayKey = targetDate.toLocalDate().toString() // "YYYY-MM-DD"

                                // Filter insights for this day using dayKey - NO timezone conversion needed!
                                val dayInsights = insights.filter { insight ->
                                    insight.dayKey == targetDayKey
                                }

                                Log.d(TAG, "Day $dayIndex ($targetDayKey): ${dayInsights.size} insights")

                                // Calculate average sentiment magnitude
                                val avgMagnitude = if (dayInsights.isNotEmpty()) {
                                    dayInsights.map { it.sentimentMagnitude }.average().toFloat()
                                } else {
                                    0f
                                }

                                // Find dominant emotion (most common sentiment label)
                                val dominantEmotion = if (dayInsights.isNotEmpty()) {
                                    dayInsights
                                        .groupBy { it.getSentimentLabel() }
                                        .maxByOrNull { it.value.size }
                                        ?.key ?: SentimentLabel.NEUTRAL
                                } else {
                                    SentimentLabel.NEUTRAL
                                }

                                // Get day label (M, T, W, T, F, S, S)
                                val dayLabel = when (targetDate.dayOfWeek.value) {
                                    1 -> "M" // Monday
                                    2 -> "T" // Tuesday
                                    3 -> "W" // Wednesday
                                    4 -> "T" // Thursday
                                    5 -> "F" // Friday
                                    6 -> "S" // Saturday
                                    7 -> "S" // Sunday
                                    else -> "?"
                                }

                                DailyInsightData(
                                    date = targetDate,
                                    dayLabel = dayLabel,
                                    sentimentMagnitude = avgMagnitude,
                                    dominantEmotion = dominantEmotion,
                                    diaryCount = dayInsights.size
                                )
                            }

                            _dailyInsights.value = dailyData
                            Log.d(TAG, "Daily insights processed: ${dailyData.size} days (M-S)")
                            dailyData.forEachIndexed { index, data ->
                                Log.d(TAG, "Day $index: ${data.dayLabel}, magnitude: ${data.sentimentMagnitude}, emotion: ${data.dominantEmotion}, diaries: ${data.diaryCount}")
                            }
                        }
                        is RequestState.Error -> {
                            Log.e(TAG, "Error loading daily insights", result.error)
                        }
                        else -> {
                            // Loading state
                            Log.d(TAG, "Loading daily insights...")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadDailyInsights", e)
            }
        }
    }

    // ==================== REDESIGN DATA LOADING ====================

    /**
     * Load all redesign data (hero, insights, achievements)
     */
    private fun loadRedesignData() {
        viewModelScope.launch {
            try {
                // Update hero loading state
                _homeRedesignState.update {
                    it.copy(heroLoadingState = SectionLoadingState(isLoading = true))
                }

                // Load insights and diaries in parallel
                val insightsDeferred = async { loadInsightsForRedesign() }
                val diariesDeferred = async { loadDiariesForRedesign() }

                cachedInsights = insightsDeferred.await()
                cachedDiaries = diariesDeferred.await()

                // Now calculate all aggregations
                calculateTodayPulse()
                calculateMoodDistribution()
                calculateCognitivePatterns()
                calculateTopicsFrequency()
                calculateAchievements()

                // Get user name
                val userName = FirebaseAuth.getInstance().currentUser?.displayName
                    ?: FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")
                    ?: "Utente"

                // Update complete state
                _homeRedesignState.update {
                    it.copy(
                        userName = userName,
                        todayPulse = _todayPulse.value,
                        moodDistribution = _moodDistribution.value,
                        cognitivePatterns = _cognitivePatterns.value,
                        topicsFrequency = _topicsFrequency.value,
                        emergingTopic = _emergingTopic.value,
                        heroLoadingState = SectionLoadingState(isLoading = false),
                        insightsLoadingState = SectionLoadingState(isLoading = false),
                        achievementsLoadingState = SectionLoadingState(isLoading = false)
                    )
                }

                Log.d(TAG, "Redesign data loaded successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading redesign data", e)
                _homeRedesignState.update {
                    it.copy(
                        heroLoadingState = SectionLoadingState(
                            isLoading = false,
                            hasError = true,
                            errorMessage = e.message
                        )
                    )
                }
            }
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
            Log.e(TAG, "Error loading insights for redesign", e)
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
            Log.e(TAG, "Error loading diaries for redesign", e)
            emptyList()
        }
    }

    private fun calculateTodayPulse() {
        try {
            val result = calculateTodayPulseUseCase(cachedInsights)
            _todayPulse.value = result.pulse

            // Update quick action state
            _quickActionState.update {
                it.copy(
                    hasUnreadInsights = cachedInsights.any { insight ->
                        // Check if insight is from today
                        insight.dayKey == java.time.LocalDate.now().toString()
                    }
                )
            }

            Log.d(TAG, "Today's pulse calculated: ${result.pulse.score}")
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating today's pulse", e)
        }
    }

    private fun calculateMoodDistribution() {
        try {
            val timeRange = _selectedTimeRange.value
            val distribution = calculateMoodDistributionUseCase(cachedInsights, timeRange)
            val dominant = calculateMoodDistributionUseCase.getDominantMood(distribution)

            _moodDistribution.value = distribution
            _dominantMood.value = dominant

            Log.d(TAG, "Mood distribution calculated: positive=${distribution.positive}, neutral=${distribution.neutral}, negative=${distribution.negative}")
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating mood distribution", e)
        }
    }

    private fun calculateCognitivePatterns() {
        try {
            val timeRange = _selectedTimeRange.value
            val result = aggregateCognitivePatternsUseCase(cachedInsights, timeRange)
            _cognitivePatterns.value = result.patterns

            Log.d(TAG, "Cognitive patterns calculated: ${result.patterns.size} patterns found")
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cognitive patterns", e)
        }
    }

    private fun calculateTopicsFrequency() {
        try {
            val timeRange = _selectedTimeRange.value
            val result = calculateTopicsFrequencyUseCase(cachedInsights, timeRange)
            _topicsFrequency.value = result.topics
            _emergingTopic.value = result.emergingTopic

            Log.d(TAG, "Topics frequency calculated: ${result.topics.size} topics, emerging: ${result.emergingTopic?.topic}")
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating topics frequency", e)
        }
    }

    private fun calculateAchievements() {
        try {
            // For now, we don't have earned badges stored - we'll calculate progress
            val result = getAchievementsUseCase(
                diaries = cachedDiaries,
                insights = cachedInsights,
                earnedBadgeIds = emptySet(),  // TODO: Load from user preferences/Firestore
                badgeEarnedDates = emptyMap()
            )
            _achievementsState.value = result

            Log.d(TAG, "Achievements calculated: streak=${result.streak.currentStreak}, badges=${result.earnedBadges.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating achievements", e)
        }
    }

    /**
     * Change time range for insights aggregation
     */
    fun updateTimeRange(timeRange: TimeRange) {
        _selectedTimeRange.value = timeRange
        _homeRedesignState.update { it.copy(selectedTimeRange = timeRange) }

        // Recalculate with new time range
        viewModelScope.launch {
            calculateMoodDistribution()
            calculateCognitivePatterns()
            calculateTopicsFrequency()
        }
    }

    /**
     * Refresh all redesign data
     */
    fun refreshRedesignData() {
        loadRedesignData()
    }

    /**
     * Get user's first name for greeting
     */
    fun getUserFirstName(): String {
        return FirebaseAuth.getInstance().currentUser?.displayName
            ?.split(" ")?.firstOrNull()
            ?: FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")
            ?: "Utente"
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel all jobs
        diariesJob?.cancel()
        refreshJob?.cancel()
        deleteJob?.cancel()
    }
}

// Extension function to await Firebase tasks
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            cont.resume(result, null)
        }
        addOnFailureListener { exception ->
            cont.resumeWithException(exception)
        }
    }
}