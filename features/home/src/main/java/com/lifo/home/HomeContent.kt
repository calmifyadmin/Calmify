package com.lifo.home

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.lifo.home.domain.model.*
import com.lifo.home.presentation.components.dashboard.*
import com.lifo.home.presentation.components.common.*
import com.lifo.util.model.Diary
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    paddingValues: PaddingValues,
    diaryNotes: Map<LocalDate, List<Diary>>?,
    onClick: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    navigateToWellbeingSnapshot: () -> Unit = {},
    navigateToWrite: () -> Unit = {},
    navigateToLive: () -> Unit = {},
    navigateToFeed: () -> Unit = {},
    navigateToThreadDetail: (String) -> Unit = {},
    navigateToSocialProfile: () -> Unit = {},
) {
    val dailyInsights by viewModel.dailyInsights.collectAsState()
    val currentWeekOffset by viewModel.currentWeekOffset.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    // Redesign state
    val homeRedesignState by viewModel.homeRedesignState.collectAsState()
    val todayPulse by viewModel.todayPulse.collectAsState()
    val moodDistribution by viewModel.moodDistribution.collectAsState()
    val dominantMood by viewModel.dominantMood.collectAsState()
    val topicsFrequency by viewModel.topicsFrequency.collectAsState()
    val emergingTopic by viewModel.emergingTopic.collectAsState()
    val achievementsState by viewModel.achievementsState.collectAsState()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsState()
    val quickActionState by viewModel.quickActionState.collectAsState()
    val communityThreads by viewModel.communityThreads.collectAsState()
    val socialAvatarUrl by viewModel.socialAvatarUrl.collectAsState()

    val onRefresh: () -> Unit = {
        isRefreshing = true
        coroutineScope.launch {
            viewModel.loadDailyInsights()
            viewModel.refreshDiaries()
            viewModel.refreshRedesignData()
            kotlinx.coroutines.delay(1000)
            isRefreshing = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        ) {
            when {
                isLoading && dailyInsights.isEmpty() && homeRedesignState.heroLoadingState.isLoading -> {
                    DashboardSkeleton()
                }
                dailyInsights.isEmpty() && !isLoading && todayPulse == null -> {
                    EmptyDashboardState(
                        onWrite = navigateToWrite,
                        onTalkToEve = navigateToLive
                    )
                }
                else -> {
                    // Main dashboard content
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Dashboard Pulse Header
                        item(key = "pulse_header") {
                            DashboardHeader(
                                todayPulse = todayPulse,
                                userName = viewModel.getUserFirstName(),
                                userPhotoUrl = socialAvatarUrl ?: viewModel.getUserPhotoUrl(),
                                onProfileClick = navigateToSocialProfile,
                                onJourneyClick = navigateToWellbeingSnapshot,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntrance(index = 0)
                            )
                        }

                        // 2. Weekly Activity Tracker
                        item(key = "weekly_tracker") {
                            WeeklyActivityTracker(
                                dailyInsights = dailyInsights,
                                weeklyGoal = achievementsState?.weeklyGoal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntrance(index = 1)
                            )
                        }

                        // 3. Quick Action Cards
                        item(key = "quick_actions") {
                            QuickActionCards(
                                onTalkToEve = navigateToLive,
                                onWrite = navigateToWrite,
                                onSnapshot = navigateToWellbeingSnapshot,
                                isSnapshotDue = quickActionState.isSnapshotDue,
                                daysSinceSnapshot = quickActionState.daysSinceLastSnapshot,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntrance(index = 2)
                            )
                        }

                        // 4. Stats Metric Row
                        achievementsState?.let { achievements ->
                            item(key = "stats_row") {
                                StatsMetricRow(
                                    streakDays = achievements.streak.currentStreak,
                                    monthlyEntries = achievements.monthlyStats.entriesThisMonth,
                                    goalProgress = achievements.weeklyGoal.progress,
                                    badgesEarned = achievements.totalBadgesEarned,
                                    streakActive = achievements.streak.isActiveToday,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredEntrance(index = 3)
                                )
                            }
                        }

                        // 5. AI Reflection Card
                        item(key = "reflection") {
                            ReflectionCard(
                                todayPulse = todayPulse,
                                recurringThemes = topicsFrequency.take(3).map { it.topic },
                                userName = viewModel.getUserFirstName(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntrance(index = 4)
                            )
                        }

                        // 6. Mood Distribution Card
                        if (moodDistribution != null && dominantMood != null) {
                            item(key = "mood_insight") {
                                MoodInsightCard(
                                    distribution = moodDistribution!!,
                                    dominantMood = dominantMood!!,
                                    timeRange = selectedTimeRange,
                                    onTimeRangeChange = { viewModel.updateTimeRange(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredEntrance(index = 5)
                                )
                            }
                        }

                        // 7. Topics Card
                        if (topicsFrequency.isNotEmpty()) {
                            item(key = "topics_insight") {
                                TopicsInsightCard(
                                    topics = topicsFrequency,
                                    emergingTopic = emergingTopic,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredEntrance(index = 6)
                                )
                            }
                        }

                        // 8. Community Preview
                        if (communityThreads.isNotEmpty()) {
                            item(key = "community") {
                                CommunityPreviewCard(
                                    threads = communityThreads,
                                    onThreadClick = navigateToThreadDetail,
                                    onViewMore = navigateToFeed,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredEntrance(index = 7)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== SKELETON ====================

@Composable
private fun DashboardSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pulse header skeleton
        item {
            SkeletonBox(
                height = 200.dp,
                cornerRadius = 16.dp
            )
        }
        // Weekly tracker skeleton
        item {
            SkeletonBox(
                height = 120.dp,
                cornerRadius = 16.dp
            )
        }
        // Quick actions skeleton
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonBox(
                    modifier = Modifier.weight(0.6f),
                    height = 64.dp,
                    cornerRadius = 16.dp
                )
                SkeletonBox(
                    modifier = Modifier.weight(0.4f),
                    height = 64.dp,
                    cornerRadius = 16.dp
                )
            }
        }
        // Stats row skeleton
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) {
                    SkeletonBox(
                        modifier = Modifier.weight(1f),
                        height = 80.dp,
                        cornerRadius = 12.dp
                    )
                }
            }
        }
        // Reflection skeleton
        item {
            SkeletonBox(
                height = 100.dp,
                cornerRadius = 16.dp
            )
        }
        // Mood insight skeleton
        item {
            SkeletonBox(
                height = 180.dp,
                cornerRadius = 16.dp
            )
        }
    }
}

// ==================== EMPTY STATE ====================

@Composable
private fun EmptyDashboardState(
    onWrite: () -> Unit,
    onTalkToEve: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "La tua dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Inizia a scrivere per vedere insights, streak e il tuo percorso",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(onClick = onWrite) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Scrivi")
                }
                OutlinedButton(onClick = onTalkToEve) {
                    Text("Parla con Eve")
                }
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
internal fun EnhancedDateHeader(
    localDate: LocalDate,
    diaryCount: Int
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val yesterday = today.minus(DatePeriod(days = 1))
    val displayText = when (localDate) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> localDate.toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$diaryCount entries",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
