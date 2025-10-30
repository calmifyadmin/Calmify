package com.lifo.util.model

import com.google.firebase.firestore.ServerTimestamp
import java.time.Instant
import java.util.Date

/**
 * PsychologicalProfile Model - Firestore Compatible
 *
 * Weekly psychological profile computed by Cloud Function
 * Week 6 - PSYCHOLOGICAL_INSIGHTS_PLAN.md Section 1.4
 *
 * Firestore collection: psychological_profiles
 * Database: calmify-native
 *
 * CRITICAL: Field types MUST match Cloud Function schema EXACTLY
 * - stressBaseline: Float (NOT Double)
 * - moodTrend: String (NOT enum - Firestore stores as string)
 * - All timestamps as Long (milliseconds)
 */
data class PsychologicalProfile(
    var id: String = "",                            // Format: "{ownerId}_week_{weekNumber}_{year}" - Regular field from Firestore
    var ownerId: String = "",                       // User ID
    var weekNumber: Int = 0,                        // ISO week number (1-53)
    var year: Int = 2025,
    var weekKey: String = "",                       // Business week key (YYYY-Www) for grouping
    var sourceTimezone: String = "Europe/Rome",     // Server timezone reference
    @ServerTimestamp
    var computedAt: Date = Date.from(Instant.now()),

    // Stress Dynamics
    var stressBaseline: Float = 5f,                 // Weighted average (0-10)
    var stressVolatility: Float = 0f,               // Standard deviation
    var stressPeaks: List<StressPeak> = emptyList(), // Peak events

    // Mood Dynamics
    var moodBaseline: Float = 5f,                   // Weighted average (0-10)
    var moodVolatility: Float = 0f,                 // Standard deviation
    var moodTrend: String = "STABLE",               // "IMPROVING", "STABLE", "DECLINING"

    // Resilience Metrics
    var resilienceIndex: Float = 0.5f,              // 0-1 (recovery speed)
    var recoverySpeed: Float = 0f,                  // Days to return to baseline

    // Data Quality Metadata
    var diaryCount: Int = 0,                        // Diary entries this week
    var snapshotCount: Int = 0,                     // Wellbeing snapshots this week
    var confidence: Float = 0f                      // 0-1 (based on data density)
) {
    // No-arg constructor required by Firestore
    constructor() : this(
        id = "",
        ownerId = "",
        weekNumber = 0,
        year = 2025,
        weekKey = "",
        sourceTimezone = "Europe/Rome",
        computedAt = Date.from(Instant.now()),
        stressBaseline = 5f,
        stressVolatility = 0f,
        stressPeaks = emptyList(),
        moodBaseline = 5f,
        moodVolatility = 0f,
        moodTrend = "STABLE",
        resilienceIndex = 0.5f,
        recoverySpeed = 0f,
        diaryCount = 0,
        snapshotCount = 0,
        confidence = 0f
    )

    /**
     * Get trend as enum for UI logic
     */
    fun getMoodTrendEnum(): Trend = when (moodTrend.uppercase()) {
        "IMPROVING" -> Trend.IMPROVING
        "DECLINING" -> Trend.DECLINING
        "INSUFFICIENT_DATA" -> Trend.INSUFFICIENT_DATA
        else -> Trend.STABLE
    }

    /**
     * Check if profile has sufficient data for display
     */
    fun hasSufficientData(): Boolean = confidence >= 0.3f && diaryCount >= 1
}

/**
 * StressPeak data class
 * Nested object within PsychologicalProfile
 */
data class StressPeak(
    var timestamp: Long = 0L,                       // Unix timestamp (milliseconds)
    var level: Int = 0,                             // Stress level (0-10)
    var trigger: String? = null,                    // Trigger type (nullable)
    var resolved: Boolean = false                   // Whether peak has been resolved
) {
    constructor() : this(0L, 0, null, false)

    /**
     * Get Date object from timestamp
     */
    fun getDate(): Date = Date(timestamp)
}

/**
 * Trend enum for UI display
 */
enum class Trend(val displayName: String, val emoji: String, val colorName: String) {
    IMPROVING("Miglioramento", "↑", "primary"),
    STABLE("Stabile", "→", "onSurfaceVariant"),
    DECLINING("In calo", "↓", "error"),
    INSUFFICIENT_DATA("Dati insufficienti", "?", "onSurfaceVariant")
}

/**
 * Extension function to format week label
 */
fun PsychologicalProfile.getWeekLabel(): String = "W$weekNumber"

/**
 * Extension function to format full week label
 */
fun PsychologicalProfile.getWeekLabelFull(): String = "Settimana $weekNumber, $year"
