package com.lifo.util.mapper

import com.lifo.shared.model.CognitivePatternProto
import com.lifo.shared.model.DiaryInsightProto
import com.lifo.shared.model.DiaryProto
import com.lifo.util.model.CognitivePattern
import com.lifo.util.model.Diary
import com.lifo.util.model.DiaryInsight

// --- Diary ---

fun Diary.toProto(): DiaryProto = DiaryProto(
    id = _id,
    ownerId = ownerId,
    mood = mood,
    title = title,
    description = description,
    images = images,
    dateMillis = dateMillis,
    dayKey = dayKey,
    timezone = timezone,
    emotionIntensity = emotionIntensity,
    stressLevel = stressLevel,
    energyLevel = energyLevel,
    calmAnxietyLevel = calmAnxietyLevel,
    primaryTrigger = primaryTrigger,
    dominantBodySensation = dominantBodySensation,
)

fun DiaryProto.toDomain(): Diary = Diary(
    _id = id,
    ownerId = ownerId,
    mood = mood,
    title = title,
    description = description,
    images = images,
    dateMillis = dateMillis,
    dayKey = dayKey,
    timezone = timezone,
    emotionIntensity = emotionIntensity,
    stressLevel = stressLevel,
    energyLevel = energyLevel,
    calmAnxietyLevel = calmAnxietyLevel,
    primaryTrigger = primaryTrigger,
    dominantBodySensation = dominantBodySensation,
)

// --- DiaryInsight ---

fun DiaryInsight.toProto(): DiaryInsightProto = DiaryInsightProto(
    id = id,
    diaryId = diaryId,
    ownerId = ownerId,
    generatedAtMillis = generatedAtMillis,
    dayKey = dayKey,
    sourceTimezone = sourceTimezone,
    sentimentPolarity = sentimentPolarity,
    sentimentMagnitude = sentimentMagnitude,
    topics = topics,
    keyPhrases = keyPhrases,
    cognitivePatterns = cognitivePatterns.map { it.toProto() },
    summary = summary,
    suggestedPrompts = suggestedPrompts,
    confidence = confidence,
    modelUsed = modelUsed,
    processingTimeMs = processingTimeMs ?: 0L,
    userCorrection = userCorrection ?: "",
    userRating = userRating ?: 0,
)

fun DiaryInsightProto.toDomain(): DiaryInsight = DiaryInsight(
    id = id,
    diaryId = diaryId,
    ownerId = ownerId,
    generatedAtMillis = generatedAtMillis,
    dayKey = dayKey,
    sourceTimezone = sourceTimezone,
    sentimentPolarity = sentimentPolarity,
    sentimentMagnitude = sentimentMagnitude,
    topics = topics,
    keyPhrases = keyPhrases,
    cognitivePatterns = cognitivePatterns.map { it.toDomain() },
    summary = summary,
    suggestedPrompts = suggestedPrompts,
    confidence = confidence,
    modelUsed = modelUsed,
    processingTimeMs = processingTimeMs.takeIf { it != 0L },
    userCorrection = userCorrection.takeIf { it.isNotEmpty() },
    userRating = userRating.takeIf { it != 0 },
)

fun CognitivePattern.toProto(): CognitivePatternProto = CognitivePatternProto(
    patternType = patternType,
    patternName = patternName,
    description = description,
    evidence = evidence,
    confidence = confidence,
)

fun CognitivePatternProto.toDomain(): CognitivePattern = CognitivePattern(
    patternType = patternType,
    patternName = patternName,
    description = description,
    evidence = evidence,
    confidence = confidence,
)
