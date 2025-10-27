package com.lifo.util.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * Wellbeing Snapshot Model - Firestore Compatible
 *
 * Weekly psychological wellbeing assessment based on Self-Determination Theory (SDT)
 * Week 2 - PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 1.2
 *
 * Target completion time: 2 minutes
 * Firestore collection: wellbeing_snapshots
 */
data class WellbeingSnapshot(
    @DocumentId
    var id: String = UUID.randomUUID().toString(),
    var ownerId: String = "",
    @ServerTimestamp
    var timestamp: Date = Date.from(Instant.now()),

    // Core SDT (Self-Determination Theory) Dimensions
    var lifeSatisfaction: Int = 5,          // 0-10 (overall life satisfaction)
    var workSatisfaction: Int = 5,          // 0-10 (work/study satisfaction)
    var relationshipsQuality: Int = 5,      // 0-10 (quality of relationships)

    // Psychological Constructs
    var mindfulnessScore: Int = 5,          // 0-10 (present awareness)
    var purposeMeaning: Int = 5,            // 0-10 (sense of direction)
    var gratitude: Int = 5,                 // 0-10 (appreciation)

    // SDT Pillars
    var autonomy: Int = 5,                  // 0-10 (control over life)
    var competence: Int = 5,                // 0-10 (mastery feeling)
    var relatedness: Int = 5,               // 0-10 (connection to others)

    // Risk Indicator
    var loneliness: Int = 5,                // 0-10 (0=connected, 10=isolated)

    // Open-ended
    var notes: String = "",                 // Optional reflection

    // Metadata
    var completionTime: Long = 0L,          // Milliseconds (UX metric)
    var wasReminded: Boolean = false        // Triggered by notification?
) {
    // No-arg constructor required by Firestore
    constructor() : this(
        id = UUID.randomUUID().toString(),
        ownerId = "",
        timestamp = Date.from(Instant.now()),
        lifeSatisfaction = 5,
        workSatisfaction = 5,
        relationshipsQuality = 5,
        mindfulnessScore = 5,
        purposeMeaning = 5,
        gratitude = 5,
        autonomy = 5,
        competence = 5,
        relatedness = 5,
        loneliness = 5,
        notes = "",
        completionTime = 0L,
        wasReminded = false
    )

    companion object {
        /**
         * Calculate overall wellbeing score (0-100)
         * Weighted average emphasizing SDT pillars and life satisfaction
         */
        fun WellbeingSnapshot.calculateOverallScore(): Float {
            return (
                lifeSatisfaction * 1.5f +      // Higher weight
                workSatisfaction * 1.0f +
                relationshipsQuality * 1.2f +
                mindfulnessScore * 1.0f +
                purposeMeaning * 1.2f +
                gratitude * 1.0f +
                autonomy * 1.3f +              // SDT pillars weighted higher
                competence * 1.3f +
                relatedness * 1.3f +
                (10 - loneliness) * 1.2f       // Invert loneliness (lower is better)
            ) / 11.8f  // Normalize to 0-10 range
        }

        /**
         * Get descriptive label for overall wellbeing
         */
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
 * Groups metrics by category for better organization
 */
data class WellbeingMetrics(
    // Life Domains
    val lifeSatisfaction: Int = 5,
    val workSatisfaction: Int = 5,
    val relationshipsQuality: Int = 5,

    // Psychological Health
    val mindfulnessScore: Int = 5,
    val purposeMeaning: Int = 5,
    val gratitude: Int = 5,

    // Self-Determination Theory (SDT)
    val autonomy: Int = 5,
    val competence: Int = 5,
    val relatedness: Int = 5,

    // Risk Factors
    val loneliness: Int = 5
) {
    fun toSnapshot(
        ownerId: String,
        notes: String,
        completionTime: Long,
        wasReminded: Boolean
    ): WellbeingSnapshot {
        return WellbeingSnapshot(
            ownerId = ownerId,
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
