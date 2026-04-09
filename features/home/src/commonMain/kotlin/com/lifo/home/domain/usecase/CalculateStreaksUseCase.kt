package com.lifo.home.domain.usecase

import com.lifo.home.domain.model.MonthlyStats
import com.lifo.home.domain.model.StreakData
import com.lifo.home.domain.model.WeeklyGoal
import com.lifo.util.model.Diary
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
/**
 * Calculate Streaks Use Case
 *
 * Calculates writing streaks, monthly stats, and weekly goals
 * for the achievements/gamification system
 */
class CalculateStreaksUseCase {

    companion object {
        private const val DEFAULT_WEEKLY_GOAL = 5

        /**
         * Map DayOfWeek to Italian display names
         */
        private fun dayOfWeekItalian(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
            DayOfWeek.MONDAY -> "Lunedi'"
            DayOfWeek.TUESDAY -> "Martedi'"
            DayOfWeek.WEDNESDAY -> "Mercoledi'"
            DayOfWeek.THURSDAY -> "Giovedi'"
            DayOfWeek.FRIDAY -> "Venerdi'"
            DayOfWeek.SATURDAY -> "Sabato"
            DayOfWeek.SUNDAY -> "Domenica"
            else -> dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }
        }

        /**
         * Get the Monday of the week containing the given date.
         * kotlinx.datetime DayOfWeek: MONDAY=0 ordinal
         */
        private fun previousOrSameMonday(date: LocalDate): LocalDate {
            val daysSinceMonday = date.dayOfWeek.ordinal
            return date.minus(daysSinceMonday, DateTimeUnit.DAY)
        }
    }

    /**
     * Calculate streak data from diary entries
     *
     * @param diaries List of all diary entries
     * @return StreakData with current and longest streak info
     */
    fun calculateStreak(diaries: List<Diary>): StreakData {
        if (diaries.isEmpty()) {
            return createEmptyStreakData()
        }

        // Get unique days with entries (using dayKey for business date)
        val daysWithEntries = diaries
            .mapNotNull { diary ->
                try {
                    if (diary.dayKey.isNotBlank()) {
                        LocalDate.parse(diary.dayKey)
                    } else {
                        Instant.fromEpochMilliseconds(diary.dateMillis)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date
                    }
                } catch (_: Exception) {
                    null
                }
            }
            .distinct()
            .sortedDescending()

        if (daysWithEntries.isEmpty()) {
            return createEmptyStreakData()
        }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val lastWriteDate = daysWithEntries.first()

        // Check if wrote today
        val isActiveToday = lastWriteDate == today

        // Check if streak is at risk (wrote yesterday but not today)
        val streakAtRisk = lastWriteDate == yesterday

        // Calculate current streak
        val currentStreak = calculateCurrentStreak(daysWithEntries, today)

        // Calculate longest streak
        val longestStreak = calculateLongestStreak(daysWithEntries)

        return StreakData(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastWriteDate = Instant.fromEpochMilliseconds(
                lastWriteDate.toEpochDays().toLong() * 86400000L
            ),
            isActiveToday = isActiveToday,
            streakAtRisk = streakAtRisk && currentStreak > 0
        )
    }

    /**
     * Calculate monthly statistics
     *
     * @param diaries List of all diary entries
     * @return MonthlyStats with this month's data
     */
    fun calculateMonthlyStats(diaries: List<Diary>): MonthlyStats {
        if (diaries.isEmpty()) {
            return createEmptyMonthlyStats()
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val thisMonthStart = LocalDate(now.year, now.month, 1)
        val lastMonthStart = thisMonthStart.minus(DatePeriod(months = 1))
        val lastMonthEnd = thisMonthStart.minus(1, DateTimeUnit.DAY)

        // Filter diaries for this month
        val thisMonthDiaries = filterDiariesByDateRange(diaries, thisMonthStart, now)
        val lastMonthDiaries = filterDiariesByDateRange(diaries, lastMonthStart, lastMonthEnd)

        // Calculate entries this month
        val entriesThisMonth = thisMonthDiaries.size

        // Calculate unique days with entries this month
        val daysWithEntries = thisMonthDiaries
            .mapNotNull { getDiaryDate(it) }
            .distinct()
            .size

        // Calculate average entries per day (only counting days with entries)
        val averageEntriesPerDay = if (daysWithEntries > 0) {
            entriesThisMonth.toFloat() / daysWithEntries
        } else 0f

        // Calculate comparison vs last month
        val lastMonthEntries = lastMonthDiaries.size
        val comparisonVsLastMonth = if (lastMonthEntries > 0) {
            ((entriesThisMonth - lastMonthEntries).toFloat() / lastMonthEntries) * 100
        } else if (entriesThisMonth > 0) {
            100f  // New user, infinite improvement
        } else {
            0f
        }

        // Find most productive day
        val dayOfWeekCounts = thisMonthDiaries
            .mapNotNull { getDiaryDate(it)?.dayOfWeek }
            .groupingBy { it }
            .eachCount()

        val mostProductiveDay = dayOfWeekCounts.maxByOrNull { it.value }?.key
            ?.let { dayOfWeekItalian(it) }

        return MonthlyStats(
            entriesThisMonth = entriesThisMonth,
            daysWithEntries = daysWithEntries,
            averageEntriesPerDay = averageEntriesPerDay,
            comparisonVsLastMonth = comparisonVsLastMonth,
            mostProductiveDay = mostProductiveDay
        )
    }

    /**
     * Calculate weekly goal progress
     *
     * @param diaries List of all diary entries
     * @param targetEntries Weekly goal (default: 5)
     * @return WeeklyGoal with current progress
     */
    fun calculateWeeklyGoal(
        diaries: List<Diary>,
        targetEntries: Int = DEFAULT_WEEKLY_GOAL
    ): WeeklyGoal {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val weekStart = previousOrSameMonday(now)
        val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)

        // Filter diaries for this week
        val thisWeekDiaries = filterDiariesByDateRange(diaries, weekStart, now)
        val currentEntries = thisWeekDiaries.size

        // Calculate progress
        val progress = (currentEntries.toFloat() / targetEntries).coerceAtMost(1f)
        val isAchieved = currentEntries >= targetEntries

        // Calculate days remaining
        val daysRemaining = (weekEnd.toEpochDays() - now.toEpochDays()).coerceAtLeast(0)

        // Calculate consecutive weeks achieved
        val consecutiveWeeksAchieved = calculateConsecutiveWeeksAchieved(diaries, targetEntries)

        return WeeklyGoal(
            targetEntries = targetEntries,
            currentEntries = currentEntries,
            progress = progress,
            daysRemaining = daysRemaining,
            isAchieved = isAchieved,
            consecutiveWeeksAchieved = consecutiveWeeksAchieved
        )
    }

    private fun calculateCurrentStreak(sortedDays: List<LocalDate>, today: LocalDate): Int {
        if (sortedDays.isEmpty()) return 0

        val mostRecent = sortedDays.first()

        // Streak is broken if didn't write today or yesterday
        if (mostRecent != today && mostRecent != today.minus(1, DateTimeUnit.DAY)) {
            return 0
        }

        var streak = 0
        var expectedDate = if (mostRecent == today) today else today.minus(1, DateTimeUnit.DAY)

        for (date in sortedDays) {
            if (date == expectedDate) {
                streak++
                expectedDate = expectedDate.minus(1, DateTimeUnit.DAY)
            } else if (date < expectedDate) {
                break
            }
        }

        return streak
    }

    private fun calculateLongestStreak(sortedDays: List<LocalDate>): Int {
        if (sortedDays.isEmpty()) return 0

        var longestStreak = 1
        var currentStreak = 1
        var previousDate = sortedDays.first()

        for (i in 1 until sortedDays.size) {
            val currentDate = sortedDays[i]
            val daysBetween = previousDate.toEpochDays() - currentDate.toEpochDays()

            if (daysBetween == 1) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
            previousDate = currentDate
        }

        return longestStreak
    }

    private fun calculateConsecutiveWeeksAchieved(
        diaries: List<Diary>,
        targetEntries: Int
    ): Int {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        var consecutiveWeeks = 0
        var weekStart = previousOrSameMonday(now)

        // Check previous weeks (up to 52 weeks)
        repeat(52) {
            val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
            val weekDiaries = filterDiariesByDateRange(diaries, weekStart, weekEnd)

            if (weekDiaries.size >= targetEntries) {
                consecutiveWeeks++
                weekStart = weekStart.minus(DatePeriod(days = 7))
            } else {
                return consecutiveWeeks
            }
        }

        return consecutiveWeeks
    }

    private fun filterDiariesByDateRange(
        diaries: List<Diary>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Diary> {
        return diaries.filter { diary ->
            val date = getDiaryDate(diary)
            date != null && date >= startDate && date <= endDate
        }
    }

    private fun getDiaryDate(diary: Diary): LocalDate? {
        return try {
            if (diary.dayKey.isNotBlank()) {
                LocalDate.parse(diary.dayKey)
            } else {
                Instant.fromEpochMilliseconds(diary.dateMillis)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createEmptyStreakData(): StreakData {
        return StreakData(
            currentStreak = 0,
            longestStreak = 0,
            lastWriteDate = null,
            isActiveToday = false,
            streakAtRisk = false
        )
    }

    private fun createEmptyMonthlyStats(): MonthlyStats {
        return MonthlyStats(
            entriesThisMonth = 0,
            daysWithEntries = 0,
            averageEntriesPerDay = 0f,
            comparisonVsLastMonth = 0f,
            mostProductiveDay = null
        )
    }
}
