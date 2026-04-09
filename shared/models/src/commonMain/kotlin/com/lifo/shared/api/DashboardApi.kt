package com.lifo.shared.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class HomeDashboardResponse(
    @ProtoNumber(1) val data: HomeDashboardProto? = null,
    @ProtoNumber(2) val error: ApiError? = null,
)

@Serializable
data class HomeDashboardProto(
    @ProtoNumber(1) val recentDiaryCount: Int = 0,
    @ProtoNumber(2) val todayPulse: TodayPulseProto? = null,
    @ProtoNumber(3) val weeklyMood: List<DailyMoodProto> = emptyList(),
    @ProtoNumber(4) val currentStreak: Int = 0,
    @ProtoNumber(5) val achievements: List<AchievementProto> = emptyList(),
    @ProtoNumber(6) val pendingSync: Int = 0,
    @ProtoNumber(7) val habitCompletionsToday: Int = 0,
    @ProtoNumber(8) val totalHabits: Int = 0,
)

@Serializable
data class TodayPulseProto(
    @ProtoNumber(1) val score: Float = 0f,
    @ProtoNumber(2) val dominantEmotion: String = "",
    @ProtoNumber(3) val trend: String = "STABLE",
    @ProtoNumber(4) val trendDelta: Float = 0f,
    @ProtoNumber(5) val weekSummary: String = "",
)

@Serializable
data class DailyMoodProto(
    @ProtoNumber(1) val dateEpoch: Long = 0L,
    @ProtoNumber(2) val dayLabel: String = "",
    @ProtoNumber(3) val sentimentMagnitude: Float = 0f,
    @ProtoNumber(4) val dominantEmotion: String = "",
    @ProtoNumber(5) val diaryCount: Int = 0,
)

@Serializable
data class AchievementProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val title: String = "",
    @ProtoNumber(3) val description: String = "",
    @ProtoNumber(4) val unlockedAtMillis: Long = 0L,
    @ProtoNumber(5) val progress: Float = 0f,
)
