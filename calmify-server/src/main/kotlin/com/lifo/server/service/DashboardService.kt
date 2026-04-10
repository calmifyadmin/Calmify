package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DashboardService(
    private val db: Firestore,
    private val diaryService: DiaryService,
) {
    private val logger = LoggerFactory.getLogger(DashboardService::class.java)

    companion object {
        private const val HABITS_COLLECTION = "habits"
        private const val HABIT_COMPLETIONS_COLLECTION = "habit_completions"
        private const val OWNER_FIELD = "ownerId"
    }

    /**
     * Build the home dashboard. Accepts optional [timezone] (IANA zone ID, e.g. "Europe/Rome")
     * so dayKey calculations are accurate for the user's locale.
     */
    suspend fun getHomeDashboard(userId: String, timezone: String = "UTC"): HomeDashboardProto {
        val now = System.currentTimeMillis()
        val weekAgo = now - 7 * 24 * 60 * 60 * 1000L

        val recentDiaries = diaryService.getDiariesByDateRange(userId, weekAgo, now)

        // Calculate weekly mood from diaries
        val weeklyMood = recentDiaries
            .groupBy { it.dayKey }
            .map { (dayKey, diaries) ->
                DailyMoodProto(
                    dateEpoch = diaries.first().dateMillis,
                    dayLabel = dayKey,
                    sentimentMagnitude = diaries.map { it.emotionIntensity.toFloat() / 10f }.average().toFloat(),
                    dominantEmotion = diaries.groupBy { it.mood }.maxByOrNull { it.value.size }?.key ?: "Neutral",
                    diaryCount = diaries.size,
                )
            }
            .sortedBy { it.dateEpoch }

        // Calculate streak (consecutive days with diaries)
        val streak = calculateStreak(userId, timezone)

        // Today's pulse
        val zoneId = try { ZoneId.of(timezone) } catch (_: Exception) { ZoneId.of("UTC") }
        val todayKey = LocalDate.now(zoneId).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val todayDiaries = recentDiaries.filter { it.dayKey == todayKey }

        val todayPulse = if (todayDiaries.isNotEmpty()) {
            val avgIntensity = todayDiaries.map { it.emotionIntensity }.average().toFloat()
            val dominant = todayDiaries.groupBy { it.mood }.maxByOrNull { it.value.size }?.key ?: "Neutral"
            TodayPulseProto(
                score = avgIntensity / 10f,
                dominantEmotion = dominant,
                trend = "STABLE",
                trendDelta = 0f,
            )
        } else null

        // Habit completions today
        val habitStats = getHabitStatsToday(userId, todayKey)

        return HomeDashboardProto(
            recentDiaryCount = recentDiaries.size,
            todayPulse = todayPulse ?: TodayPulseProto(),
            weeklyMood = weeklyMood,
            currentStreak = streak,
            habitCompletionsToday = habitStats.first,
            totalHabits = habitStats.second,
        )
    }

    /**
     * Calculate the current consecutive-day journaling streak.
     * Verifies actual day continuity using dayKey (YYYY-MM-DD format).
     */
    private suspend fun calculateStreak(userId: String, timezone: String): Int = withContext(Dispatchers.IO) {
        val zoneId = try { ZoneId.of(timezone) } catch (_: Exception) { ZoneId.of("UTC") }
        val today = LocalDate.now(zoneId)

        val docs = db.collection(DiaryService.COLLECTION)
            .whereEqualTo(OWNER_FIELD, userId)
            .orderBy("dayKey", Query.Direction.DESCENDING)
            .limit(60)
            .get().get().documents

        val dayKeys = docs.mapNotNull { it.getString("dayKey") }
            .distinct()
            .sorted()
            .reversed() // Most recent first

        if (dayKeys.isEmpty()) return@withContext 0

        // Parse all dayKeys into LocalDate
        val dates = dayKeys.mapNotNull { key ->
            try { LocalDate.parse(key, DateTimeFormatter.ISO_LOCAL_DATE) } catch (_: Exception) { null }
        }.sortedDescending()

        if (dates.isEmpty()) return@withContext 0

        // Streak must include today or yesterday to be "current"
        val mostRecent = dates.first()
        val daysSinceMostRecent = ChronoUnit.DAYS.between(mostRecent, today)
        if (daysSinceMostRecent > 1) return@withContext 0 // Streak broken

        var streak = 1
        for (i in 1 until dates.size) {
            val gap = ChronoUnit.DAYS.between(dates[i], dates[i - 1])
            if (gap == 1L) {
                streak++
            } else {
                break // Streak broken
            }
        }
        streak
    }

    private suspend fun getHabitStatsToday(userId: String, todayKey: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val totalHabits = db.collection(HABITS_COLLECTION)
            .whereEqualTo(OWNER_FIELD, userId)
            .whereEqualTo("isActive", true)
            .get().get().documents.size

        val completionsToday = db.collection(HABIT_COMPLETIONS_COLLECTION)
            .whereEqualTo(OWNER_FIELD, userId)
            .whereEqualTo("dayKey", todayKey)
            .get().get().documents.size

        completionsToday to totalHabits
    }
}
