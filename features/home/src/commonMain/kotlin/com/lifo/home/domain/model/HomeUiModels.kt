package com.lifo.home.domain.model

import androidx.compose.runtime.Immutable
import com.lifo.ui.i18n.Strings
import com.lifo.util.model.SentimentLabel
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.StringResource

/**
 * Home UI Models - Data classes for the redesigned Home screen
 * Following Material3 Expressive design principles
 */

// ==================== HERO SECTION MODELS ====================

/**
 * Trend direction for metrics comparison
 */
enum class TrendDirection {
    UP,
    DOWN,
    STABLE
}

/**
 * Today's emotional pulse data for hero section
 */
@Immutable
data class TodayPulse(
    val score: Float,                    // 0-10 sentiment score
    val trend: TrendDirection,           // Comparison vs yesterday
    val trendDelta: Float,               // Absolute change vs yesterday
    val weekSummary: String,             // e.g., "Tendenza positiva"
    val dominantEmotion: SentimentLabel, // Most common emotion today
    val entriesCount: Int                // Number of entries today
)

/**
 * Quick action state
 */
@Immutable
data class QuickActionState(
    val isSnapshotDue: Boolean = false,  // True if 7+ days since last snapshot
    val daysSinceLastSnapshot: Int = 0,
    val hasUnreadInsights: Boolean = false
)

// ==================== INSIGHTS FEED MODELS ====================

/**
 * Time range for aggregation filters. UI label resolved at render site via
 * inline `when (range) -> Strings.Screen.Home.periodFilter*` to keep this
 * enum free of UI dependencies (same pattern as Trend/BlockType).
 */
enum class TimeRange(val days: Int) {
    WEEK(7),
    MONTH(30),
    QUARTER(90)
}

/** Localized label for a [TimeRange] — resolved at render site via stringResource. */
val TimeRange.labelRes: StringResource
    get() = when (this) {
        TimeRange.WEEK -> Strings.Screen.Home.periodFilter7
        TimeRange.MONTH -> Strings.Screen.Home.periodFilter30
        TimeRange.QUARTER -> Strings.Screen.Home.periodFilter90
    }

/**
 * Mood distribution for donut chart
 */
@Immutable
data class MoodDistribution(
    val positive: Float,                          // 0-1 percentage
    val neutral: Float,                           // 0-1 percentage
    val negative: Float,                          // 0-1 percentage
    val detailedBreakdown: Map<SentimentLabel, Float>, // Detailed breakdown
    val totalEntries: Int,
    val timeRange: TimeRange
)

/**
 * Dominant mood with metadata
 * Note: Uses shape-based indicators instead of emojis (M3 Expressive)
 */
@Immutable
data class DominantMood(
    val sentiment: SentimentLabel,
    val percentage: Float,
)

/** Localized label for the dominant mood (resolved at render site via stringResource). */
val DominantMood.labelRes: StringResource
    get() = sentiment.labelRes

/** Localized label for a [SentimentLabel]. */
val SentimentLabel.labelRes: StringResource
    get() = when (this) {
        SentimentLabel.VERY_NEGATIVE -> Strings.Sentiment.veryNegative
        SentimentLabel.NEGATIVE -> Strings.Sentiment.negative
        SentimentLabel.NEUTRAL -> Strings.Sentiment.neutral
        SentimentLabel.POSITIVE -> Strings.Sentiment.positive
        SentimentLabel.VERY_POSITIVE -> Strings.Sentiment.veryPositive
    }

/**
 * Pattern sentiment classification for CBT patterns
 */
enum class PatternSentiment {
    ADAPTIVE,      // Positive/healthy pattern
    MALADAPTIVE,   // Negative/unhealthy pattern
    NEUTRAL        // Neutral observation
}

/**
 * Cognitive pattern summary for patterns card
 */
@Immutable
data class CognitivePatternSummary(
    val patternName: String,
    val description: String,
    val occurrences: Int,
    val trend: TrendDirection,
    val sentiment: PatternSentiment,
    val examples: List<String> = emptyList()
)

/**
 * Topic frequency for word cloud
 */
@Immutable
data class TopicFrequency(
    val topic: String,
    val frequency: Int,
    val sentimentAverage: Float,    // -1 to +1
    val isEmerging: Boolean,
    val changePercent: Float = 0f   // Change vs previous period
)

/**
 * Topic trend for emerging topics
 */
@Immutable
data class TopicTrend(
    val topic: String,
    val changePercent: Float,
    val direction: TrendDirection
)

/**
 * Wellbeing data point for trend chart
 */
@Immutable
data class WellbeingDataPoint(
    val date: Instant,
    val score: Float,                // 0-10
    val label: String                // Month label for x-axis
)

/**
 * Wellbeing score labels
 */
enum class WellbeingScoreLabel(val label: String, val minScore: Float) {
    EXCELLENT("Eccellente", 8f),
    GOOD("Buono", 6f),
    FAIR("Discreto", 4f),
    POOR("Scarso", 2f),
    CRITICAL("Critico", 0f);

    companion object {
        fun fromScore(score: Float): WellbeingScoreLabel {
            return entries.first { score >= it.minScore }
        }
    }
}

// ==================== ACTIVITY FEED MODELS ====================

/**
 * Time period grouping for activity feed
 */
enum class TimePeriod(val labelRes: StringResource) {
    TODAY(Strings.Screen.Home.periodToday),
    YESTERDAY(Strings.Screen.Home.periodYesterday),
    THIS_WEEK(Strings.Screen.Home.periodThisWeek),
    LAST_WEEK(Strings.Screen.Home.periodLastWeek),
    THIS_MONTH(Strings.Screen.Home.periodThisMonth),
    OLDER(Strings.Screen.Home.periodOlder)
}

/**
 * Sentiment indicator for activity cards
 */
@Immutable
data class SentimentIndicator(
    val color: Int,                 // Color resource/value
    val label: SentimentLabel,
    val magnitude: Float            // 0-10
)

/**
 * Enhanced activity item for feed
 */
@Immutable
data class EnhancedActivityItem(
    val id: String,
    val type: ActivityType,
    val title: String,
    val preview: String,
    val timestamp: Instant,
    val relativeTime: String,       // "2 ore fa"
    val sentimentIndicator: SentimentIndicator?,
    val keyInsight: String?,        // "Hai parlato di lavoro e stress"
    val topics: List<String>,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false
)

enum class ActivityType {
    DIARY,
    CHAT_SESSION
}

/**
 * Swipe actions for activity cards
 */
enum class SwipeAction {
    ARCHIVE,
    FAVORITE,
    DELETE
}

// ==================== ENHANCED DAY TOOLTIP ====================

/**
 * Enhanced tooltip data for chart day selection
 */
@Immutable
data class DayDetailTooltip(
    val date: Instant,
    val sentimentMagnitude: Float,
    val dominantEmotion: SentimentLabel,
    val diaryCount: Int,
    val topTopic: String?,           // Most frequent topic
    val cognitivePattern: String?,   // Notable pattern if any
    val keyMoment: String?           // Brief excerpt from entry
)

// ==================== LOADING STATES ====================

/**
 * Loading priority for lazy sections
 */
enum class LoadPriority {
    CRITICAL,   // Hero section, today's pulse
    HIGH,       // Weekly chart
    MEDIUM,     // Insights cards
    LOW         // Activity feed (paginated)
}

/**
 * Section loading state
 */
@Immutable
data class SectionLoadingState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val isEmpty: Boolean = false
)

// ==================== COMPLETE HOME STATE ====================

/**
 * Complete home screen state
 */
@Immutable
data class HomeRedesignState(
    // Hero section
    val userName: String = "",
    val todayPulse: TodayPulse? = null,
    val quickActionState: QuickActionState = QuickActionState(),

    // Insights
    val moodDistribution: MoodDistribution? = null,
    val cognitivePatterns: List<CognitivePatternSummary> = emptyList(),
    val topicsFrequency: List<TopicFrequency> = emptyList(),
    val emergingTopic: TopicTrend? = null,
    val wellbeingTrend: List<WellbeingDataPoint> = emptyList(),
    val latestWellbeingScore: Float? = null,
    val daysSinceLastSnapshot: Int = 0,

    // Activity feed
    val activityItems: Map<TimePeriod, List<EnhancedActivityItem>> = emptyMap(),

    // Loading states per section
    val heroLoadingState: SectionLoadingState = SectionLoadingState(),
    val insightsLoadingState: SectionLoadingState = SectionLoadingState(),
    val feedLoadingState: SectionLoadingState = SectionLoadingState(),
    val achievementsLoadingState: SectionLoadingState = SectionLoadingState(),

    // Global state
    val isRefreshing: Boolean = false,
    val selectedTimeRange: TimeRange = TimeRange.MONTH
)
