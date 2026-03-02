package com.lifo.util.model

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Wellbeing Snapshot Model - Pure Kotlin (KMP)
 *
 * Weekly psychological wellbeing assessment based on Self-Determination Theory (SDT)
 * Firestore mapping handled by FirestoreMapper in data/mongo
 */
@OptIn(ExperimentalUuidApi::class)
data class WellbeingSnapshot(
    var id: String = Uuid.random().toString(),
    var ownerId: String = "",
    var timestampMillis: Long = Clock.System.now().toEpochMilliseconds(),
    var dayKey: String = "",
    var timezone: String = "",

    // Core SDT (Self-Determination Theory) Dimensions
    var lifeSatisfaction: Int = 5,
    var workSatisfaction: Int = 5,
    var relationshipsQuality: Int = 5,

    // Psychological Constructs
    var mindfulnessScore: Int = 5,
    var purposeMeaning: Int = 5,
    var gratitude: Int = 5,

    // SDT Pillars
    var autonomy: Int = 5,
    var competence: Int = 5,
    var relatedness: Int = 5,

    // Risk Indicator
    var loneliness: Int = 5,

    // Open-ended
    var notes: String = "",

    // Metadata
    var completionTime: Long = 0L,
    var wasReminded: Boolean = false
) {
    companion object {
        fun WellbeingSnapshot.calculateOverallScore(): Float {
            return (
                lifeSatisfaction * 1.5f +
                workSatisfaction * 1.0f +
                relationshipsQuality * 1.2f +
                mindfulnessScore * 1.0f +
                purposeMeaning * 1.2f +
                gratitude * 1.0f +
                autonomy * 1.3f +
                competence * 1.3f +
                relatedness * 1.3f +
                (10 - loneliness) * 1.2f
            ) / 11.8f
        }

        fun Float.getWellbeingLabel(): String = when {
            this >= 8.0f -> "Eccellente"
            this >= 6.5f -> "Buono"
            this >= 5.0f -> "Discreto"
            this >= 3.5f -> "Fragile"
            else -> "Critico"
        }
    }
}

/**
 * UI State wrapper for wellbeing metrics
 */
data class WellbeingMetrics(
    val lifeSatisfaction: Int = 5,
    val workSatisfaction: Int = 5,
    val relationshipsQuality: Int = 5,
    val mindfulnessScore: Int = 5,
    val purposeMeaning: Int = 5,
    val gratitude: Int = 5,
    val autonomy: Int = 5,
    val competence: Int = 5,
    val relatedness: Int = 5,
    val loneliness: Int = 5
) {
    fun toSnapshot(
        ownerId: String,
        notes: String,
        completionTime: Long,
        wasReminded: Boolean
    ): WellbeingSnapshot {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val localDate = now.toLocalDateTime(tz).date
        val dayKey = localDate.toString() // "YYYY-MM-DD"
        val timezone = tz.id

        return WellbeingSnapshot(
            ownerId = ownerId,
            timestampMillis = now.toEpochMilliseconds(),
            dayKey = dayKey,
            timezone = timezone,
            lifeSatisfaction = lifeSatisfaction,
            workSatisfaction = workSatisfaction,
            relationshipsQuality = relationshipsQuality,
            mindfulnessScore = mindfulnessScore,
            purposeMeaning = purposeMeaning,
            gratitude = gratitude,
            autonomy = autonomy,
            competence = competence,
            relatedness = relatedness,
            loneliness = loneliness,
            notes = notes,
            completionTime = completionTime,
            wasReminded = wasReminded
        )
    }
}
