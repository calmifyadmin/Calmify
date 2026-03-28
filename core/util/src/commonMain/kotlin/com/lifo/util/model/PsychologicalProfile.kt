package com.lifo.util.model

import kotlinx.datetime.Clock

/**
 * PsychologicalProfile Model - Pure Kotlin (KMP)
 *
 * Weekly psychological profile computed by Cloud Function
 * Firestore mapping handled by FirestoreMapper in data/mongo
 */
data class PsychologicalProfile(
    var id: String = "",
    var ownerId: String = "",
    var weekNumber: Int = 0,
    var year: Int = 2025,
    var weekKey: String = "",
    var sourceTimezone: String = "Europe/Rome",
    var computedAtMillis: Long = Clock.System.now().toEpochMilliseconds(),

    // Stress Dynamics
    var stressBaseline: Float = 5f,
    var stressVolatility: Float = 0f,
    var stressPeaks: List<StressPeak> = emptyList(),

    // Mood Dynamics
    var moodBaseline: Float = 5f,
    var moodVolatility: Float = 0f,
    var moodTrend: String = "STABLE",

    // Resilience Metrics
    var resilienceIndex: Float = 0.5f,
    var recoverySpeed: Float = 0f,

    // Data Quality Metadata
    var diaryCount: Int = 0,
    var snapshotCount: Int = 0,
    var confidence: Float = 0f
) {
    fun getMoodTrendEnum(): Trend = when (moodTrend.uppercase()) {
        "IMPROVING" -> Trend.IMPROVING
        "DECLINING" -> Trend.DECLINING
        "INSUFFICIENT_DATA" -> Trend.INSUFFICIENT_DATA
        else -> Trend.STABLE
    }

    fun hasSufficientData(): Boolean = confidence >= 0.3f && diaryCount >= 1
}

/**
 * StressPeak data class
 */
data class StressPeak(
    var timestampMillis: Long = 0L,
    var level: Int = 0,
    var trigger: String? = null,
    var resolved: Boolean = false
)

/**
 * Trend enum for UI display
 */
enum class Trend(val displayName: String, val colorName: String) {
    IMPROVING("Miglioramento", "primary"),
    STABLE("Stabile", "onSurfaceVariant"),
    DECLINING("In calo", "error"),
    INSUFFICIENT_DATA("Dati insufficienti", "onSurfaceVariant"),
}

fun PsychologicalProfile.getWeekLabel(): String = "W$weekNumber"

fun PsychologicalProfile.getWeekLabelFull(): String = "Settimana $weekNumber, $year"
