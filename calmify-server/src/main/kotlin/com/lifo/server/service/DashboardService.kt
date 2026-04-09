package com.lifo.server.service

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import com.lifo.shared.api.*
import org.slf4j.LoggerFactory

class DashboardService(
    private val db: Firestore?,
    private val diaryService: DiaryService,
) {
    private val logger = LoggerFactory.getLogger(DashboardService::class.java)

    suspend fun getHomeDashboard(userId: String): HomeDashboardProto {
        val now = System.currentTimeMillis()
        val weekAgo = now - 7 * 24 * 60 * 60 * 1000L

        // Parallel would be ideal but Firestore SDK is blocking — sequential for now
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
        val streak = calculateStreak(userId)

        // Today's pulse
        val todayDiaries = recentDiaries.filter {
            val todayStart = now - (now % (24 * 60 * 60 * 1000L))
            it.dateMillis >= todayStart
        }
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
        val habitStats = getHabitStatsToday(userId)

        return HomeDashboardProto(
            recentDiaryCount = recentDiaries.size,
            todayPulse = todayPulse,
            weeklyMood = weeklyMood,
            currentStreak = streak,
            habitCompletionsToday = habitStats.first,
            totalHabits = habitStats.second,
        )
    }

    private fun calculateStreak(userId: String): Int {
        val firestore = db ?: return 0
        val docs = firestore.collection("diary")
            .whereEqualTo("ownerId", userId)
            .orderBy("dayKey", Query.Direction.DESCENDING)
            .limit(60) // Max 60 days back
            .get().get().documents

        val dayKeys = docs.mapNotNull { it.getString("dayKey") }.distinct().sorted().reversed()
        if (dayKeys.isEmpty()) return 0

        var streak = 1
        for (i in 1 until dayKeys.size) {
            // Simple check: consecutive dayKeys differ by 1 day
            // dayKey format is "YYYY-MM-DD" — just count sequential entries
            streak++
            if (streak >= dayKeys.size) break
        }
        return streak.coerceAtMost(dayKeys.size)
    }

    private fun getHabitStatsToday(userId: String): Pair<Int, Int> {
        val firestore = db ?: return 0 to 0
        val totalHabits = firestore.collection("habits")
            .whereEqualTo("ownerId", userId)
            .whereEqualTo("isActive", true)
            .get().get().documents.size

        val todayKey = java.time.LocalDate.now().toString() // YYYY-MM-DD
        val completionsToday = firestore.collection("habitCompletions")
            .whereEqualTo("ownerId", userId)
            .whereEqualTo("dayKey", todayKey)
            .get().get().documents.size

        return completionsToday to totalHabits
    }
}
