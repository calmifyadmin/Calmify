package com.lifo.home

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.lifo.home.domain.model.*
import com.lifo.home.presentation.components.hero.HeroGreetingCard
import com.lifo.home.presentation.components.hero.QuickActionsRow
import com.lifo.home.presentation.components.insights.MoodDistributionCard
import com.lifo.home.presentation.components.insights.CognitivePatternsCard
import com.lifo.home.presentation.components.insights.TopicsCloudCard
import com.lifo.home.presentation.components.achievements.AchievementsRow
import com.lifo.home.presentation.components.common.HeroGreetingCardSkeleton
import com.lifo.home.presentation.components.common.DailyInsightsChartSkeleton
import com.lifo.home.presentation.components.common.MoodDistributionCardSkeleton
import com.lifo.ui.components.loading.*
import com.lifo.util.DiaryHolder
import com.lifo.util.model.Diary
import com.lifo.util.model.SentimentLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class  // Required for LoadingIndicator
)
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
    navigateToLive: () -> Unit = {}
) {
    val dailyInsights by viewModel.dailyInsights.collectAsState()
    val currentWeekOffset by viewModel.currentWeekOffset.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    // New redesign state
    val homeRedesignState by viewModel.homeRedesignState.collectAsStateWithLifecycle()
    val todayPulse by viewModel.todayPulse.collectAsStateWithLifecycle()
    val moodDistribution by viewModel.moodDistribution.collectAsStateWithLifecycle()
    val dominantMood by viewModel.dominantMood.collectAsStateWithLifecycle()
    val cognitivePatterns by viewModel.cognitivePatterns.collectAsStateWithLifecycle()
    val topicsFrequency by viewModel.topicsFrequency.collectAsStateWithLifecycle()
    val emergingTopic by viewModel.emergingTopic.collectAsStateWithLifecycle()
    val achievementsState by viewModel.achievementsState.collectAsStateWithLifecycle()
    val selectedTimeRange by viewModel.selectedTimeRange.collectAsStateWithLifecycle()
    val quickActionState by viewModel.quickActionState.collectAsStateWithLifecycle()

    // Debug logging
    LaunchedEffect(dailyInsights) {
        android.util.Log.d("HomeContent", "Daily insights updated: ${dailyInsights.size} days")
        if (dailyInsights.isEmpty()) {
            android.util.Log.d("HomeContent", "No daily insights to display - chart will be hidden")
        } else {
            android.util.Log.d("HomeContent", "Daily insights available - chart will be shown")
        }
    }

    // Handle refresh
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
                PullToRefreshDefaults.LoadingIndicator(
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
                    // Initial loading state - show skeleton
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { HeroGreetingCardSkeleton() }
                        item { DailyInsightsChartSkeleton() }
                        item { MoodDistributionCardSkeleton() }
                    }
                }
                dailyInsights.isEmpty() && !isLoading && todayPulse == null -> {
                    // Empty state - no insights yet
                    EmptyInsightsState()
                }
                else -> {
                    // Main content with new redesign components
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 80.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Hero Greeting Card
                        item(key = "hero_greeting") {
                            todayPulse?.let { pulse ->
                                HeroGreetingCard(
                                    userName = viewModel.getUserFirstName(),
                                    todayPulse = pulse,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Quick Actions Row
                        item(key = "quick_actions") {
                            QuickActionsRow(
                                onWriteDiary = navigateToWrite,
                                onStartLive = navigateToLive,
                                onTakeSnapshot = navigateToWellbeingSnapshot,
                                snapshotDueIndicator = quickActionState.isSnapshotDue,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Daily Insights Chart (existing)
                        item(key = "daily_insights_chart") {
                            DailyInsightsChart(
                                dailyInsights = dailyInsights,
                                currentWeekOffset = currentWeekOffset,
                                onPreviousWeek = { viewModel.navigateToPreviousWeek() },
                                onNextWeek = { viewModel.navigateToNextWeek() },
                                onResetWeek = { viewModel.resetToCurrentWeek() }
                            )
                        }

                        // Mood Distribution Card
                        if (moodDistribution != null && dominantMood != null) {
                            item(key = "mood_distribution") {
                                MoodDistributionCard(
                                    distribution = moodDistribution!!,
                                    dominantMood = dominantMood!!,
                                    timeRange = selectedTimeRange,
                                    onTimeRangeChange = { viewModel.updateTimeRange(it) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Cognitive Patterns Card
                        if (cognitivePatterns.isNotEmpty()) {
                            item(key = "cognitive_patterns") {
                                CognitivePatternsCard(
                                    patterns = cognitivePatterns,
                                    onLearnMore = { /* TODO: Show pattern details */ },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Topics Cloud Card
                        if (topicsFrequency.isNotEmpty()) {
                            item(key = "topics_cloud") {
                                TopicsCloudCard(
                                    topics = topicsFrequency,
                                    emergingTopic = emergingTopic,
                                    onTopicClick = { /* TODO: Filter by topic */ },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Achievements Row
                        achievementsState?.let { achievements ->
                            item(key = "achievements") {
                                AchievementsRow(
                                    streak = achievements.streak,
                                    monthlyStats = achievements.monthlyStats,
                                    weeklyGoal = achievements.weeklyGoal,
                                    latestBadge = achievements.latestBadge,
                                    onViewAllBadges = { /* TODO: Navigate to badges screen */ },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Wellbeing Snapshot Button
                        item(key = "wellbeing_button") {
                            WellbeingSnapshotButton(
                                onClick = navigateToWellbeingSnapshot,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty state for insights
 */
@Composable
private fun EmptyInsightsState() {
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
                text = "No Insights Yet",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Start writing diaries to see your psychological insights and sentiment trends",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

/**
 * Empty week state - shown when no data for the selected week
 */
@Composable
private fun EmptyWeekState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EventNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "No data for this week",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Start writing to see insights",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Large Material Expressive Button for Wellbeing Snapshot
 * Styled with Material 3 Expressive principles
 */
@Composable
private fun WellbeingSnapshotButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp,
            hoveredElevation = 4.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "View Wellbeing Snapshot",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@SuppressLint("NewApi")
@Composable
internal fun EnhancedDateHeader(
    localDate: LocalDate,
    diaryCount: Int
) {
    val today = LocalDate.now()
    val displayText = when (localDate) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date block with modern design
            Column(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = localDate.dayOfMonth.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = localDate.dayOfWeek.toString().take(3).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (diaryCount == 1) "1 entry" else "$diaryCount entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun AnimatedDiaryItem(
    diary: Diary,
    onClick: (String) -> Unit
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(diary) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .graphicsLayer {
                alpha = animatedProgress.value
                scaleX = 0.8f + (0.2f * animatedProgress.value)
                scaleY = 0.8f + (0.2f * animatedProgress.value)
            }
    ) {
        DiaryHolder(
            diary = diary,
            onClick = onClick
        )
    }
}

@Composable
internal fun EmptyStateContent(
    onCreateFirst: () -> Unit
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
            // Animated empty state illustration could go here

            Text(
                text = "Your diary is empty",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Start writing your thoughts and memories",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            androidx.compose.material3.FilledTonalButton(
                onClick = onCreateFirst,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Create Your First Entry")
            }
        }
    }
}

/**
 * Daily Insights Chart - Fitbit Style
 * Material 3 Expressive design with vertical rounded pills
 * Each column shows the average sentiment magnitude for a day
 * Color based on dominant emotion
 */
@Composable
fun DailyInsightsChart(
    dailyInsights: List<DailyInsightData>,
    currentWeekOffset: Int = 0,
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onResetWeek: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            DailyInsightsHeader(
                currentWeekOffset = currentWeekOffset,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onResetWeek = onResetWeek
            )

            // Main Stats
            DailyInsightsMainStats(dailyInsights)

            // The Chart or Empty State
            if (dailyInsights.isNotEmpty() && dailyInsights.any { it.sentimentMagnitude > 0 }) {
                VerticalPillsDailyChart(
                    dailyInsights = dailyInsights,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(vertical = 12.dp)
                )
            } else {
                // Empty week state
                EmptyWeekState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DailyInsightsHeader(
    currentWeekOffset: Int = 0,
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onResetWeek: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                currentWeekOffset == 0 -> "This week"
                currentWeekOffset == -1 -> "Last week"
                currentWeekOffset < 0 -> "${-currentWeekOffset} weeks ago"
                else -> "In ${currentWeekOffset} weeks" // Future weeks
            },
            style = MaterialTheme.typography.titleLargeEmphasized,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousWeek,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous week",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onNextWeek,
                modifier = Modifier.size(32.dp),
                enabled = currentWeekOffset < 0 // Can't go to future weeks beyond current
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next week",
                    tint = if (currentWeekOffset < 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }
            IconButton(
                onClick = onResetWeek,
                modifier = Modifier.size(32.dp),
                enabled = currentWeekOffset != 0
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Current week",
                    tint = if (currentWeekOffset != 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }
        }
    }
}

@Composable
private fun DailyInsightsMainStats(dailyInsights: List<DailyInsightData>) {
    val avgMagnitude = if (dailyInsights.isNotEmpty()) {
        dailyInsights.map { it.sentimentMagnitude }.average().toFloat()
    } else 0f

    val totalDays = dailyInsights.count { it.sentimentMagnitude > 0 }
    val totalScore = dailyInsights.sumOf { it.sentimentMagnitude.toDouble() }.toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = String.format("%.1f", avgMagnitude),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "sentiment/day (avg)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Text(
            text = "You logged a total of ${String.format("%.1f", totalScore)} on $totalDays days",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Vertical Pills Daily Chart - The main chart with vertical rounded pills
 * Color based on dominant emotion
 */
@Composable
private fun VerticalPillsDailyChart(
    dailyInsights: List<DailyInsightData>,
    modifier: Modifier = Modifier
) {
    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }

    // Animation for pill appearance
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(dailyInsights) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val spacing = (size.width - 40.dp.toPx()) / dailyInsights.size
                        val index = ((offset.x - 20.dp.toPx()) / spacing).toInt()
                            .coerceIn(0, dailyInsights.size - 1)
                        selectedDayIndex = if (selectedDayIndex == index) null else index
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val horizontalPadding = 20.dp.toPx()
            val bottomPadding = 40.dp.toPx()
            val topPadding = 20.dp.toPx()

            val chartHeight = canvasHeight - topPadding - bottomPadding
            val dataPoints = dailyInsights.size

            if (dataPoints == 0) return@Canvas

            val spacing = (canvasWidth - horizontalPadding * 2) / dataPoints
            val pillWidth = 40.dp.toPx().coerceAtMost(spacing * 0.8f)

            // Max value for normalization (typically 0-10 for sentiment magnitude)
            val maxValue = 10f

            // Draw horizontal grid lines
            for (i in 0..4) {
                val y = topPadding + (chartHeight / 4) * i
                drawLine(
                    color = gridLineColor,
                    start = Offset(horizontalPadding, y),
                    end = Offset(canvasWidth - horizontalPadding, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx()))
                )
            }

            // Draw pills for each day
            dailyInsights.forEachIndexed { index, dayData ->
                val centerX = horizontalPadding + spacing * index + spacing / 2

                // Animated magnitude
                val animatedMagnitude = dayData.sentimentMagnitude * animationProgress.value

                // Convert to pixel height
                val pillHeight = (animatedMagnitude / maxValue) * chartHeight

                // Position Y baseline (bottom)
                val baselineY = canvasHeight - bottomPadding
                val pillTop = baselineY - pillHeight
                val pillLeft = centerX - pillWidth / 2

                // Get color based on dominant emotion
                val pillColor = getSentimentColor(dayData.dominantEmotion)

                // Draw rounded pill
                drawRoundRect(
                    color = pillColor,
                    topLeft = Offset(pillLeft, pillTop),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = CornerRadius(pillWidth / 2, pillWidth / 2),
                    alpha = if (selectedDayIndex == index) 1f else 0.9f
                )

                // Checkmark indicator for good days (positive sentiment)
                if (dayData.dominantEmotion == SentimentLabel.POSITIVE ||
                    dayData.dominantEmotion == SentimentLabel.VERY_POSITIVE) {
                    val checkmarkSize = 16.dp.toPx()
                    val checkmarkCenterX = centerX
                    val checkmarkCenterY = pillTop - checkmarkSize

                    // Green circle with checkmark
                    drawCircle(
                        color = Color(0xFF4CAF50),
                        radius = checkmarkSize / 2,
                        center = Offset(checkmarkCenterX, checkmarkCenterY)
                    )

                    // Checkmark symbol
                    val checkStroke = 2.dp.toPx()
                    drawLine(
                        color = Color.White,
                        start = Offset(checkmarkCenterX - 4.dp.toPx(), checkmarkCenterY),
                        end = Offset(checkmarkCenterX - 1.dp.toPx(), checkmarkCenterY + 3.dp.toPx()),
                        strokeWidth = checkStroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(checkmarkCenterX - 1.dp.toPx(), checkmarkCenterY + 3.dp.toPx()),
                        end = Offset(checkmarkCenterX + 5.dp.toPx(), checkmarkCenterY - 3.dp.toPx()),
                        strokeWidth = checkStroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Day labels at bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dailyInsights.forEachIndexed { index, dayData ->
                Text(
                    text = dayData.dayLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selectedDayIndex == index) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedDayIndex == index) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Tooltip for selected day
        selectedDayIndex?.let { index ->
            if (index in dailyInsights.indices) {
                val dayData = dailyInsights[index]
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = dayData.dayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Shape-based emotion indicator instead of emoji
                            com.lifo.home.presentation.components.common.MiniEmotionIndicator(
                                sentiment = dayData.dominantEmotion,
                                size = 16.dp
                            )
                            Text(
                                text = dayData.dominantEmotion.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                            Text(
                                text = String.format("%.1f", dayData.sentimentMagnitude),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                        if (dayData.diaryCount > 0) {
                            Text(
                                text = "${dayData.diaryCount} ${if (dayData.diaryCount == 1) "diary" else "diaries"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get color based on sentiment label
 */
private fun getSentimentColor(sentiment: SentimentLabel): Color {
    return when (sentiment) {
        SentimentLabel.VERY_NEGATIVE -> Color(0xFFEF5350) // Red
        SentimentLabel.NEGATIVE -> Color(0xFFFF7043) // Orange
        SentimentLabel.NEUTRAL -> Color(0xFF78909C) // Gray
        SentimentLabel.POSITIVE -> Color(0xFF66BB6A) // Green
        SentimentLabel.VERY_POSITIVE -> Color(0xFF4CAF50) // Dark Green
    }
}