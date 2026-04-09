package com.lifo.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class PsychologicalProfileProto(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val ownerId: String = "",
    @ProtoNumber(3) val weekNumber: Int = 0,
    @ProtoNumber(4) val year: Int = 2025,
    @ProtoNumber(5) val weekKey: String = "",
    @ProtoNumber(6) val sourceTimezone: String = "Europe/Rome",
    @ProtoNumber(7) val computedAtMillis: Long = 0L,
    @ProtoNumber(8) val stressBaseline: Float = 5f,
    @ProtoNumber(9) val stressVolatility: Float = 0f,
    @ProtoNumber(10) val stressPeaks: List<StressPeakProto> = emptyList(),
    @ProtoNumber(11) val moodBaseline: Float = 5f,
    @ProtoNumber(12) val moodVolatility: Float = 0f,
    @ProtoNumber(13) val moodTrend: String = "STABLE",
    @ProtoNumber(14) val resilienceIndex: Float = 0.5f,
    @ProtoNumber(15) val recoverySpeed: Float = 0f,
    @ProtoNumber(16) val diaryCount: Int = 0,
    @ProtoNumber(17) val snapshotCount: Int = 0,
    @ProtoNumber(18) val confidence: Float = 0f,
)

@Serializable
data class StressPeakProto(
    @ProtoNumber(1) val timestampMillis: Long = 0L,
    @ProtoNumber(2) val level: Int = 0,
    @ProtoNumber(3) val trigger: String = "",
    @ProtoNumber(4) val resolved: Boolean = false,
)
