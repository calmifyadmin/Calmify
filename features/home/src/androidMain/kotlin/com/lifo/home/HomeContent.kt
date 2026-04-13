package com.lifo.home

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.lifo.home.domain.model.*
import com.lifo.home.presentation.components.dashboard.*
import com.lifo.home.presentation.components.common.*
import com.lifo.home.presentation.components.expressive.*
import com.lifo.ui.components.coaching.CoachMarkKeys
import com.lifo.ui.components.coaching.CoachMarkOverlay
import com.lifo.ui.components.coaching.ScreenTutorials
import com.lifo.ui.components.coaching.coachMarkTarget
import com.lifo.ui.components.coaching.rememberCoachMarkState
import com.lifo.ui.components.tooltips.InfoTooltip
import com.lifo.ui.components.tooltips.TooltipContent
import com.lifo.ui.onboarding.OnboardingManager
import com.lifo.util.model.Diary
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

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
    // Daily quick actions
    onGratitudeClick: () -> Unit = {},
    onEnergyCheckInClick: () -> Unit = {},
    onSleepLogClick: () -> Unit = {},
    onHabitsClick: () -> Unit = {},
    onMeditationClick: () -> Unit = {},
) {
    val dailyInsights by viewModel.dailyInsights.collectAsState()
    val currentWeekOffset by viewModel.currentWeekOffset.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    // ── Coach marks ──────────────────────────────────────────────────────────
    val onboardingManager: OnboardingManager = koinInject()
    val coachState = rememberCoachMarkState(ScreenTutorials.home)
    val lazyListState = rememberLazyListState()

    // Avvia il tour al primo caricamento
    LaunchedEffect(Unit) {
        if (onboardingManager.shouldShowTutorial(ScreenTutorials.KEY_HOME)) {
            coachState.start()
        }
    }

    // Auto-scroll per rivelare il target se fuori schermo
    // Usa animateScrollToItem per transizione smooth, sincronizzato con BringIntoViewRequester
    LaunchedEffect(coachState.currentStep?.targetKey) {
        val targetKey = coachState.currentStep?.targetKey
        if (targetKey != null) {
            try {
                when (targetKey) {
                    CoachMarkKeys.HOME_GREETING -> {
                        lazyListState.animateScrollToItem(0)
                    }
                    CoachMarkKeys.HOME_QUICK_ACTIONS, CoachMarkKeys.HOME_AVATAR -> {
                        lazyListState.animateScrollToItem(1)
                    }
                    CoachMarkKeys.HOME_MOOD -> {
                        // Scroll al target HOME_MOOD (indice ~5 nella LazyColumn)
                        val totalItems = lazyListState.layoutInfo.totalItemsCount
                        val scrollIndex = if (totalItems > 5) 5 else maxOf(0, totalItems - 2)
                        lazyListState.animateScrollToItem(scrollIndex)
                    }
                    else -> { /* nessuno scroll necessario */ }
                }
            } catch (e: Exception) {
                // Lo scroll potrebbe fallire — il sistema attenderà reattivamente il layout
            }
        }
    }

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

    // Outer Box fills the full screen (no padding) so canvas coordinates
    // align with boundsInRoot() screen coordinates.
    Box(modifier = Modifier.fillMaxSize()) {
        // Content with padding
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
                            // Main dashboard — M3 Expressive layout
                            LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 100.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 1. Expressive Hero
                        item(key = "hero") {
                            ExpressiveHero(
                                todayPulse = todayPulse,
                                userName = viewModel.getUserFirstName(),
                                userPhotoUrl = socialAvatarUrl ?: viewModel.getUserPhotoUrl(),
                                onProfileClick = navigateToSocialProfile,
                                onPulseClick = navigateToWellbeingSnapshot,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntrance(index = 0, baseDelayMs = 80)
                                    .coachMarkTarget(coachState, CoachMarkKeys.HOME_GREETING)
                            )
                        }

                        // 2. Quick Actions (hero card + pills)
                        item(key = "quick_actions") {
                            ExpressiveQuickActions(
                                onTalkToEve = navigateToLive,
                                onWrite = navigateToWrite,
                                onSnapshot = navigateToWellbeingSnapshot,
                                isSnapshotDue = quickActionState.isSnapshotDue,
                                daysSinceSnapshot = quickActionState.daysSinceLastSnapshot,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntrance(index = 1, baseDelayMs = 80)
                                    .coachMarkTarget(coachState, CoachMarkKeys.HOME_QUICK_ACTIONS),
                                eveCardModifier = Modifier.coachMarkTarget(
                                    coachState, CoachMarkKeys.HOME_AVATAR
                                ),
                            )
                        }

                        // 3. Weekly Activity Strip (with streak)
                        item(key = "week_strip") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntrance(index = 2, baseDelayMs = 80),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Questa settimana",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    InfoTooltip(
                                        title = TooltipContent.habitStacking.first,
                                        description = TooltipContent.habitStacking.second
                                    )
                                }
                                ExpressiveWeekStrip(
                                    dailyInsights = dailyInsights,
                                    weeklyGoal = achievementsState?.weeklyGoal,
                                    streakDays = achievementsState?.streak?.currentStreak ?: 0,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 4. Daily Quick Actions (horizontal scroll)
                        item(key = "daily_actions") {
                            ExpressiveDailyActions(
                                onGratitudeClick = onGratitudeClick,
                                onEnergyClick = onEnergyCheckInClick,
                                onSleepClick = onSleepLogClick,
                                onHabitsClick = onHabitsClick,
                                onMeditationClick = onMeditationClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntrance(index = 3, baseDelayMs = 80)
                            )
                        }

                        // 5. AI Reflection (immersive quote card)
                        item(key = "reflection") {
                            ExpressiveReflection(
                                todayPulse = todayPulse,
                                recurringThemes = topicsFrequency.take(3).map { it.topic },
                                userName = viewModel.getUserFirstName(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredEntrance(index = 4, baseDelayMs = 80)
                            )
                        }

                        // 6. Mood Distribution (centered donut + pills)
                        if (moodDistribution != null && dominantMood != null) {
                            item(key = "mood") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredEntrance(index = 5, baseDelayMs = 80),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Umore",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        InfoTooltip(
                                            title = TooltipContent.wellbeingTrend.first,
                                            description = TooltipContent.wellbeingTrend.second
                                        )
                                    }
                                    ExpressiveMoodCard(
                                        distribution = moodDistribution!!,
                                        dominantMood = dominantMood!!,
                                        timeRange = selectedTimeRange,
                                        onTimeRangeChange = { viewModel.updateTimeRange(it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        donutModifier = Modifier.coachMarkTarget(coachState, CoachMarkKeys.HOME_MOOD),
                                    )
                                }
                            }
                        }

                        // 7. Topics
                        if (topicsFrequency.isNotEmpty()) {
                            item(key = "topics") {
                                TopicsInsightCard(
                                    topics = topicsFrequency,
                                    emergingTopic = emergingTopic,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredEntrance(index = 6, baseDelayMs = 80)
                                )
                            }
                        }

                        // 8. Community
                        if (communityThreads.isNotEmpty()) {
                            item(key = "community") {
                                CommunityPreviewCard(
                                    threads = communityThreads,
                                    onThreadClick = navigateToThreadDetail,
                                    onViewMore = navigateToFeed,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .staggeredEntrance(index = 7, baseDelayMs = 80)
                                )
                            }
                        }
                        }
                    }
                } // end when
            } // end PullToRefreshBox
        } // end inner padded Box

        // CoachMarkOverlay as overlay on top of content
        CoachMarkOverlay(
            state = coachState,
            onFinished = { onboardingManager.markTutorialSeen(ScreenTutorials.KEY_HOME) },
            modifier = Modifier.fillMaxSize()
        )
    } // end outer fullscreen Box
}

// ==================== SKELETON (M3 Expressive) ====================

@Composable
private fun DashboardSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero skeleton
        item {
            SkeletonBox(
                height = 280.dp,
                cornerRadius = 32.dp
            )
        }
        // Quick actions skeleton
        item {
            SkeletonBox(
                height = 80.dp,
                cornerRadius = 28.dp
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonBox(
                    modifier = Modifier.weight(1f),
                    height = 56.dp,
                    cornerRadius = 20.dp
                )
                SkeletonBox(
                    modifier = Modifier.weight(1f),
                    height = 56.dp,
                    cornerRadius = 20.dp
                )
            }
        }
        // Week strip skeleton
        item {
            SkeletonBox(
                height = 140.dp,
                cornerRadius = 28.dp
            )
        }
        // Stats skeleton (asymmetric)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonBox(
                    modifier = Modifier.weight(1f),
                    height = 180.dp,
                    cornerRadius = 24.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) {
                        SkeletonBox(
                            height = 48.dp,
                            cornerRadius = 20.dp
                        )
                    }
                }
            }
        }
        // Reflection skeleton
        item {
            SkeletonBox(
                height = 140.dp,
                cornerRadius = 32.dp
            )
        }
    }
}

// ==================== EMPTY STATE (M3 Expressive) ====================

@Composable
private fun EmptyDashboardState(
    onWrite: () -> Unit,
    onTalkToEve: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(40.dp)
        ) {
            // Large expressive icon
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(32.dp),
                color = colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Inizia il tuo\npercorso",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    lineHeight = 40.sp
                ),
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Scrivi, parla, rifletti — anche solo una riga",
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onWrite,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.write_action), style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = onTalkToEve,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(stringResource(Res.string.home_talk_to_eve), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ==================== DAILY QUICK ACTIONS (Expressive) ====================

private data class DailyAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
internal fun ExpressiveDailyActions(
    onGratitudeClick: () -> Unit,
    onEnergyClick: () -> Unit,
    onSleepClick: () -> Unit,
    onHabitsClick: () -> Unit,
    onMeditationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    val actions = remember(onGratitudeClick, onEnergyClick, onSleepClick, onHabitsClick, onMeditationClick) {
        listOf(
            DailyAction(Icons.Outlined.Favorite, "Gratitudine", onGratitudeClick),
            DailyAction(Icons.Outlined.BatteryChargingFull, "Energia", onEnergyClick),
            DailyAction(Icons.Outlined.Bedtime, "Sonno", onSleepClick),
            DailyAction(Icons.Outlined.CheckCircle, "Abitudini", onHabitsClick),
            DailyAction(Icons.Outlined.SelfImprovement, "Meditazione", onMeditationClick),
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Azioni quotidiane",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = colorScheme.onSurface,
            )
            InfoTooltip(
                title = TooltipContent.minimumAction.first,
                description = TooltipContent.minimumAction.second
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(actions) { action ->
                ExpressiveDailyChip(
                    icon = action.icon,
                    label = action.label,
                    onClick = action.onClick
                )
            }
        }
    }
}

@Composable
private fun ExpressiveDailyChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1
        )
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
