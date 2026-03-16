package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.AchievementsState
import com.lifo.home.domain.model.Badge
import com.lifo.home.domain.model.BadgeCategory
import com.lifo.home.domain.model.KnownBadges
import com.lifo.home.domain.model.MonthlyStats
import com.lifo.home.domain.model.StreakData
import com.lifo.home.domain.model.WeeklyGoal
import com.lifo.util.model.Diary
import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.SentimentLabel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.days
/**
 * Get Achievements Use Case
 *
 * Calculates all achievements, badges, and progress
 * for the gamification system
 */
class GetAchievementsUseCase(
    private val calculateStreaksUseCase: CalculateStreaksUseCase
) {

    /**
     * Calculate complete achievements state
     *
     * @param diaries List of all diary entries
     * @param insights List of all diary insights
     * @param earnedBadgeIds Set of badge IDs the user has earned
     * @param badgeEarnedDates Map of badge ID to earn date
     * @return AchievementsState with all achievement data
     */
    operator fun invoke(
        diaries: List<Diary>,
        insights: List<DiaryInsight>,
        earnedBadgeIds: Set<String> = emptySet(),
        badgeEarnedDates: Map<String, Instant> = emptyMap(),
        weeklyGoalTarget: Int = 5
    ): AchievementsState {
        // Calculate streaks and stats using dedicated use case
        val streakData = calculateStreaksUseCase.calculateStreak(diaries)
        val monthlyStats = calculateStreaksUseCase.calculateMonthlyStats(diaries)
        val weeklyGoal = calculateStreaksUseCase.calculateWeeklyGoal(diaries, weeklyGoalTarget)

        // Calculate badge progress
        val badgeProgress = calculateBadgeProgress(
            diaries = diaries,
            insights = insights,
            streakData = streakData
        )

        // Build badge list with progress and earned status
        val allBadges = KnownBadges.allBadges.map { definition ->
            val isEarned = earnedBadgeIds.contains(definition.id)
            val earnedAt = badgeEarnedDates[definition.id]
            val progress = badgeProgress[definition.id] ?: 0f

            // Check if badge was earned in last 7 days (is "new")
            val sevenDaysAgo = Clock.System.now() - 7.days
            val isNew = earnedAt != null && earnedAt > sevenDaysAgo

            definition.toBadge(
                earnedAt = earnedAt,
                progress = if (isEarned) 1f else progress,
                isNew = isNew
            )
        }

        val earnedBadges = allBadges.filter { it.earnedAt != null }
            .sortedByDescending { it.earnedAt }

        val inProgressBadges = allBadges
            .filter { it.earnedAt == null && it.progress > 0f }
            .sortedByDescending { it.progress }

        val latestBadge = earnedBadges.firstOrNull()

        val nextMilestone = inProgressBadges.firstOrNull()
            ?: allBadges.firstOrNull { it.earnedAt == null }

        return AchievementsState(
            streak = streakData,
            monthlyStats = monthlyStats,
            weeklyGoal = weeklyGoal,
            earnedBadges = earnedBadges,
            latestBadge = latestBadge,
            inProgressBadges = inProgressBadges,
            totalBadgesEarned = earnedBadges.size,
            nextMilestone = nextMilestone
        )
    }

    /**
     * Check for newly earned badges
     *
     * @return List of badge IDs that should now be earned
     */
    fun checkNewBadges(
        diaries: List<Diary>,
        insights: List<DiaryInsight>,
        currentEarnedIds: Set<String>
    ): List<String> {
        val progress = calculateBadgeProgress(
            diaries = diaries,
            insights = insights,
            streakData = calculateStreaksUseCase.calculateStreak(diaries)
        )

        return progress.filter { (badgeId, prog) ->
            prog >= 1f && !currentEarnedIds.contains(badgeId)
        }.keys.toList()
    }

    private fun calculateBadgeProgress(
        diaries: List<Diary>,
        insights: List<DiaryInsight>,
        streakData: StreakData
    ): Map<String, Float> {
        val progress = mutableMapOf<String, Float>()

        // Writing badges
        val diaryCount = diaries.size
        progress["first_entry"] = if (diaryCount >= 1) 1f else 0f
        progress["prolific_writer"] = (diaryCount.toFloat() / 50).coerceAtMost(1f)
        progress["novelist"] = (diaryCount.toFloat() / 200).coerceAtMost(1f)

        // Consistency badges (streaks)
        val currentStreak = streakData.currentStreak
        val longestStreak = streakData.longestStreak
        val maxStreak = maxOf(currentStreak, longestStreak)

        progress["streak_7"] = (maxStreak.toFloat() / 7).coerceAtMost(1f)
        progress["streak_30"] = (maxStreak.toFloat() / 30).coerceAtMost(1f)
        progress["streak_100"] = (maxStreak.toFloat() / 100).coerceAtMost(1f)

        // Mood awareness badges
        val uniqueSentiments = insights
            .map { it.getSentimentLabel() }
            .distinct()
            .size
        progress["mood_explorer"] = (uniqueSentiments.toFloat() / 5).coerceAtMost(1f)

        // Check positive streak (7 consecutive positive days)
        val positiveStreak = calculatePositiveStreak(insights)
        progress["positivity_champion"] = (positiveStreak.toFloat() / 7).coerceAtMost(1f)

        // Wellbeing snapshots (placeholder - would need snapshot data)
        progress["wellbeing_master"] = 0f

        // Growth badges
        val uniquePatterns = insights
            .flatMap { it.cognitivePatterns }
            .map { it.patternType }
            .distinct()
            .size
        progress["pattern_spotter"] = (uniquePatterns.toFloat() / 10).coerceAtMost(1f)
        progress["self_aware"] = 0f  // Requires tracking pattern improvement over time

        // Exploration badges
        val uniqueTopics = insights
            .flatMap { it.topics }
            .distinct()
            .size
        progress["topic_explorer"] = (uniqueTopics.toFloat() / 10).coerceAtMost(1f)

        // Night owl (entries after midnight, before 5am)
        val nightEntries = countEntriesByHourRange(diaries, 0, 5)
        progress["night_owl"] = (nightEntries.toFloat() / 10).coerceAtMost(1f)

        // Early bird (entries before 7am but after 5am)
        val earlyEntries = countEntriesByHourRange(diaries, 5, 7)
        progress["early_bird"] = (earlyEntries.toFloat() / 10).coerceAtMost(1f)

        // Anniversary (1 year)
        val oldestEntry = diaries.minByOrNull { it.dateMillis }
        val daysUsing = if (oldestEntry != null) {
            val oldest = java.time.Instant.ofEpochMilli(oldestEntry.dateMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            (LocalDate.now().toEpochDay() - oldest.toEpochDay()).toInt()
        } else 0
        progress["anniversary"] = (daysUsing.toFloat() / 365).coerceAtMost(1f)

        // Beta tester (would need to check app version or specific flag)
        progress["beta_tester"] = 0f

        return progress
    }

    private fun calculatePositiveStreak(insights: List<DiaryInsight>): Int {
        if (insights.isEmpty()) return 0

        // Group by day and check sentiment
        val dailySentiments = insights
            .groupBy { insight ->
                try {
                    if (insight.dayKey.isNotBlank()) {
                        insight.dayKey
                    } else {
                        java.time.Instant.ofEpochMilli(insight.generatedAtMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                    }
                } catch (_: Exception) {
                    null
                }
            }
            .filterKeys { it != null }
            .mapValues { (_, dayInsights) ->
                // Average sentiment for the day
                dayInsights.map { it.sentimentPolarity }.average().toFloat()
            }
            .mapKeys { LocalDate.parse(it.key) }
            .toSortedMap(reverseOrder())

        var streak = 0
        var expectedDate = LocalDate.now()

        for ((date, avgSentiment) in dailySentiments) {
            if (date == expectedDate || date == expectedDate.minusDays(1)) {
                if (avgSentiment > 0.2f) {  // Positive threshold
                    streak++
                    expectedDate = date.minusDays(1)
                } else {
                    break
                }
            } else if (date.isBefore(expectedDate.minusDays(1))) {
                break
            }
        }

        return streak
    }

    private fun countEntriesByHourRange(
        diaries: List<Diary>,
        startHour: Int,
        endHour: Int
    ): Int {
        return diaries.count { diary ->
            try {
                val hour = java.time.Instant.ofEpochMilli(diary.dateMillis)
                    .atZone(ZoneId.systemDefault())
                    .hour
                hour in startHour until endHour
            } catch (_: Exception) {
                false
            }
        }
    }
}
