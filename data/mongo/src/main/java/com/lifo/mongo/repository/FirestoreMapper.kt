package com.lifo.mongo.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.lifo.util.model.BodySensation
import com.lifo.util.model.CognitivePattern
import com.lifo.util.model.Diary
import com.lifo.util.model.DiaryInsight
import com.lifo.util.model.Mood
import com.lifo.util.model.ProfileSettings
import com.lifo.util.model.PsychologicalProfile
import com.lifo.util.model.StressPeak
import com.lifo.util.model.Trigger
import com.lifo.util.model.WellbeingSnapshot
import kotlinx.datetime.Clock
import java.util.Date

// ===================== DIARY =====================

fun DocumentSnapshot.toDiary(): Diary? {
    if (!exists()) return null
    return try {
        Diary(
            _id = id,
            ownerId = getString("ownerId") ?: "",
            mood = getString("mood") ?: Mood.Neutral.name,
            title = getString("title") ?: "",
            description = getString("description") ?: "",
            images = get("images") as? List<String> ?: emptyList(),
            dateMillis = getTimestamp("date")?.toDate()?.time
                ?: Clock.System.now().toEpochMilliseconds(),
            dayKey = getString("dayKey") ?: "",
            timezone = getString("timezone") ?: "",
            emotionIntensity = getLong("emotionIntensity")?.toInt() ?: 5,
            stressLevel = getLong("stressLevel")?.toInt() ?: 5,
            energyLevel = getLong("energyLevel")?.toInt() ?: 5,
            calmAnxietyLevel = getLong("calmAnxietyLevel")?.toInt() ?: 5,
            primaryTrigger = getString("primaryTrigger") ?: Trigger.NONE.name,
            dominantBodySensation = getString("dominantBodySensation")
                ?: BodySensation.NONE.name
        )
    } catch (e: Exception) {
        null
    }
}

fun Diary.toFirestoreMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId,
    "mood" to mood,
    "title" to title,
    "description" to description,
    "images" to images,
    "date" to Timestamp(Date(dateMillis)),
    "dayKey" to dayKey,
    "timezone" to timezone,
    "emotionIntensity" to emotionIntensity,
    "stressLevel" to stressLevel,
    "energyLevel" to energyLevel,
    "calmAnxietyLevel" to calmAnxietyLevel,
    "primaryTrigger" to primaryTrigger,
    "dominantBodySensation" to dominantBodySensation
)

// ===================== DIARY INSIGHT =====================

fun DocumentSnapshot.toDiaryInsight(): DiaryInsight? {
    if (!exists()) return null
    return try {
        @Suppress("UNCHECKED_CAST")
        val patternsRaw = get("cognitivePatterns") as? List<Map<String, Any?>> ?: emptyList()
        val patterns = patternsRaw.map { map ->
            CognitivePattern(
                patternType = map["patternType"] as? String ?: "",
                patternName = map["patternName"] as? String ?: "",
                description = map["description"] as? String ?: "",
                evidence = map["evidence"] as? String ?: "",
                confidence = (map["confidence"] as? Number)?.toFloat() ?: 0f
            )
        }

        DiaryInsight(
            id = id,
            diaryId = getString("diaryId") ?: "",
            ownerId = getString("ownerId") ?: "",
            generatedAtMillis = getTimestamp("generatedAt")?.toDate()?.time
                ?: Clock.System.now().toEpochMilliseconds(),
            dayKey = getString("dayKey") ?: "",
            sourceTimezone = getString("sourceTimezone") ?: "",
            sentimentPolarity = getDouble("sentimentPolarity")?.toFloat() ?: 0f,
            sentimentMagnitude = getDouble("sentimentMagnitude")?.toFloat() ?: 0f,
            topics = get("topics") as? List<String> ?: emptyList(),
            keyPhrases = get("keyPhrases") as? List<String> ?: emptyList(),
            cognitivePatterns = patterns,
            summary = getString("summary") ?: "",
            suggestedPrompts = get("suggestedPrompts") as? List<String> ?: emptyList(),
            confidence = getDouble("confidence")?.toFloat() ?: 0f,
            modelUsed = getString("modelUsed") ?: "gemini-2.0-flash-exp",
            processingTimeMs = getLong("processingTimeMs"),
            userCorrection = getString("userCorrection"),
            userRating = getLong("userRating")?.toInt()
        )
    } catch (e: Exception) {
        null
    }
}

fun DiaryInsight.toFirestoreMap(): Map<String, Any?> = mapOf(
    "diaryId" to diaryId,
    "ownerId" to ownerId,
    "generatedAt" to Timestamp(Date(generatedAtMillis)),
    "dayKey" to dayKey,
    "sourceTimezone" to sourceTimezone,
    "sentimentPolarity" to sentimentPolarity,
    "sentimentMagnitude" to sentimentMagnitude,
    "topics" to topics,
    "keyPhrases" to keyPhrases,
    "cognitivePatterns" to cognitivePatterns.map { pattern ->
        mapOf(
            "patternType" to pattern.patternType,
            "patternName" to pattern.patternName,
            "description" to pattern.description,
            "evidence" to pattern.evidence,
            "confidence" to pattern.confidence
        )
    },
    "summary" to summary,
    "suggestedPrompts" to suggestedPrompts,
    "confidence" to confidence,
    "modelUsed" to modelUsed,
    "processingTimeMs" to processingTimeMs,
    "userCorrection" to userCorrection,
    "userRating" to userRating
)

// ===================== WELLBEING SNAPSHOT =====================

fun DocumentSnapshot.toWellbeingSnapshot(): WellbeingSnapshot? {
    if (!exists()) return null
    return try {
        WellbeingSnapshot(
            id = id,
            ownerId = getString("ownerId") ?: "",
            timestampMillis = getTimestamp("timestamp")?.toDate()?.time
                ?: Clock.System.now().toEpochMilliseconds(),
            dayKey = getString("dayKey") ?: "",
            timezone = getString("timezone") ?: "",
            lifeSatisfaction = getLong("lifeSatisfaction")?.toInt() ?: 5,
            workSatisfaction = getLong("workSatisfaction")?.toInt() ?: 5,
            relationshipsQuality = getLong("relationshipsQuality")?.toInt() ?: 5,
            mindfulnessScore = getLong("mindfulnessScore")?.toInt() ?: 5,
            purposeMeaning = getLong("purposeMeaning")?.toInt() ?: 5,
            gratitude = getLong("gratitude")?.toInt() ?: 5,
            autonomy = getLong("autonomy")?.toInt() ?: 5,
            competence = getLong("competence")?.toInt() ?: 5,
            relatedness = getLong("relatedness")?.toInt() ?: 5,
            loneliness = getLong("loneliness")?.toInt() ?: 5,
            notes = getString("notes") ?: "",
            completionTime = getLong("completionTime") ?: 0L,
            wasReminded = getBoolean("wasReminded") ?: false
        )
    } catch (e: Exception) {
        null
    }
}

fun WellbeingSnapshot.toFirestoreMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId,
    "timestamp" to Timestamp(Date(timestampMillis)),
    "dayKey" to dayKey,
    "timezone" to timezone,
    "lifeSatisfaction" to lifeSatisfaction,
    "workSatisfaction" to workSatisfaction,
    "relationshipsQuality" to relationshipsQuality,
    "mindfulnessScore" to mindfulnessScore,
    "purposeMeaning" to purposeMeaning,
    "gratitude" to gratitude,
    "autonomy" to autonomy,
    "competence" to competence,
    "relatedness" to relatedness,
    "loneliness" to loneliness,
    "notes" to notes,
    "completionTime" to completionTime,
    "wasReminded" to wasReminded
)

// ===================== PSYCHOLOGICAL PROFILE =====================

fun DocumentSnapshot.toPsychologicalProfile(): PsychologicalProfile? {
    if (!exists()) return null
    return try {
        @Suppress("UNCHECKED_CAST")
        val peaksRaw = get("stressPeaks") as? List<Map<String, Any?>> ?: emptyList()
        val peaks = peaksRaw.map { map ->
            StressPeak(
                timestampMillis = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                level = (map["level"] as? Number)?.toInt() ?: 0,
                trigger = map["trigger"] as? String,
                resolved = map["resolved"] as? Boolean ?: false
            )
        }

        PsychologicalProfile(
            id = getString("id") ?: id,
            ownerId = getString("ownerId") ?: "",
            weekNumber = getLong("weekNumber")?.toInt() ?: 0,
            year = getLong("year")?.toInt() ?: 2025,
            weekKey = getString("weekKey") ?: "",
            sourceTimezone = getString("sourceTimezone") ?: "Europe/Rome",
            computedAtMillis = getTimestamp("computedAt")?.toDate()?.time
                ?: Clock.System.now().toEpochMilliseconds(),
            stressBaseline = getDouble("stressBaseline")?.toFloat() ?: 5f,
            stressVolatility = getDouble("stressVolatility")?.toFloat() ?: 0f,
            stressPeaks = peaks,
            moodBaseline = getDouble("moodBaseline")?.toFloat() ?: 5f,
            moodVolatility = getDouble("moodVolatility")?.toFloat() ?: 0f,
            moodTrend = getString("moodTrend") ?: "STABLE",
            resilienceIndex = getDouble("resilienceIndex")?.toFloat() ?: 0.5f,
            recoverySpeed = getDouble("recoverySpeed")?.toFloat() ?: 0f,
            diaryCount = getLong("diaryCount")?.toInt() ?: 0,
            snapshotCount = getLong("snapshotCount")?.toInt() ?: 0,
            confidence = getDouble("confidence")?.toFloat() ?: 0f
        )
    } catch (e: Exception) {
        null
    }
}

// ===================== PROFILE SETTINGS =====================

fun DocumentSnapshot.toProfileSettings(): ProfileSettings? {
    if (!exists()) return null
    return try {
        @Suppress("UNCHECKED_CAST")
        ProfileSettings(
            id = id,
            ownerId = getString("ownerId") ?: "",
            createdAtMillis = getTimestamp("createdAt")?.toDate()?.time
                ?: Clock.System.now().toEpochMilliseconds(),
            updatedAtMillis = getTimestamp("updatedAt")?.toDate()?.time
                ?: Clock.System.now().toEpochMilliseconds(),
            isOnboardingCompleted = getBoolean("isOnboardingCompleted") ?: false,
            fullName = getString("fullName") ?: "",
            dateOfBirth = getString("dateOfBirth") ?: "",
            gender = getString("gender") ?: "PREFER_NOT_TO_SAY",
            height = getLong("height")?.toInt() ?: 0,
            weight = getDouble("weight")?.toFloat() ?: 0f,
            location = getString("location") ?: "",
            primaryConcerns = get("primaryConcerns") as? List<String> ?: emptyList(),
            mentalHealthHistory = getString("mentalHealthHistory") ?: "NO_DIAGNOSIS",
            currentTherapy = getBoolean("currentTherapy") ?: false,
            medication = getBoolean("medication") ?: false,
            occupation = getString("occupation") ?: "",
            sleepHoursAvg = getDouble("sleepHoursAvg")?.toFloat() ?: 7f,
            exerciseFrequency = getString("exerciseFrequency") ?: "MODERATE",
            socialSupport = getString("socialSupport") ?: "MODERATE",
            primaryGoals = get("primaryGoals") as? List<String> ?: emptyList(),
            preferredCopingStrategies = get("preferredCopingStrategies") as? List<String>
                ?: emptyList(),
            shareDataForResearch = getBoolean("shareDataForResearch") ?: false,
            enableAdvancedInsights = getBoolean("enableAdvancedInsights") ?: true
        )
    } catch (e: Exception) {
        null
    }
}

fun ProfileSettings.toFirestoreMap(): Map<String, Any?> = mapOf(
    "ownerId" to ownerId,
    "createdAt" to Timestamp(Date(createdAtMillis)),
    "updatedAt" to Timestamp(Date(updatedAtMillis)),
    "isOnboardingCompleted" to isOnboardingCompleted,
    "fullName" to fullName,
    "dateOfBirth" to dateOfBirth,
    "gender" to gender,
    "height" to height,
    "weight" to weight,
    "location" to location,
    "primaryConcerns" to primaryConcerns,
    "mentalHealthHistory" to mentalHealthHistory,
    "currentTherapy" to currentTherapy,
    "medication" to medication,
    "occupation" to occupation,
    "sleepHoursAvg" to sleepHoursAvg,
    "exerciseFrequency" to exerciseFrequency,
    "socialSupport" to socialSupport,
    "primaryGoals" to primaryGoals,
    "preferredCopingStrategies" to preferredCopingStrategies,
    "shareDataForResearch" to shareDataForResearch,
    "enableAdvancedInsights" to enableAdvancedInsights
)
