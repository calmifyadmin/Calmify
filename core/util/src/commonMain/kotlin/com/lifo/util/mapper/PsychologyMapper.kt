package com.lifo.util.mapper

import com.lifo.shared.model.PsychologicalProfileProto
import com.lifo.shared.model.StressPeakProto
import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.StressPeak

fun PsychologicalProfile.toProto(): PsychologicalProfileProto = PsychologicalProfileProto(
    id = id,
    ownerId = ownerId,
    weekNumber = weekNumber,
    year = year,
    weekKey = weekKey,
    sourceTimezone = sourceTimezone,
    computedAtMillis = computedAtMillis,
    stressBaseline = stressBaseline,
    stressVolatility = stressVolatility,
    stressPeaks = stressPeaks.map { it.toProto() },
    moodBaseline = moodBaseline,
    moodVolatility = moodVolatility,
    moodTrend = moodTrend,
    resilienceIndex = resilienceIndex,
    recoverySpeed = recoverySpeed,
    diaryCount = diaryCount,
    snapshotCount = snapshotCount,
    confidence = confidence,
)

fun PsychologicalProfileProto.toDomain(): PsychologicalProfile = PsychologicalProfile(
    id = id,
    ownerId = ownerId,
    weekNumber = weekNumber,
    year = year,
    weekKey = weekKey,
    sourceTimezone = sourceTimezone,
    computedAtMillis = computedAtMillis,
    stressBaseline = stressBaseline,
    stressVolatility = stressVolatility,
    stressPeaks = stressPeaks.map { it.toDomain() },
    moodBaseline = moodBaseline,
    moodVolatility = moodVolatility,
    moodTrend = moodTrend,
    resilienceIndex = resilienceIndex,
    recoverySpeed = recoverySpeed,
    diaryCount = diaryCount,
    snapshotCount = snapshotCount,
    confidence = confidence,
)

fun StressPeak.toProto(): StressPeakProto = StressPeakProto(
    timestampMillis = timestampMillis,
    level = level,
    trigger = trigger ?: "",
    resolved = resolved,
)

fun StressPeakProto.toDomain(): StressPeak = StressPeak(
    timestampMillis = timestampMillis,
    level = level,
    trigger = trigger.takeIf { it.isNotEmpty() },
    resolved = resolved,
)
