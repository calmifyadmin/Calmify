package com.lifo.home.domain.model

import androidx.compose.runtime.Immutable

/**
 * Correlation between sleep duration and daily mood (from diary sentiment).
 * Narrative: "Quando dormi più di 7h, il tuo umore migliora del 23%"
 */
@Immutable
data class SleepMoodCorrelation(
    val sleepThresholdHours: Float,
    val moodAboveThreshold: Float,       // 0-10 avg mood on long-sleep days
    val moodBelowThreshold: Float,       // 0-10 avg mood on short-sleep days
    val improvementPercent: Float,
    val sampleSize: Int,
    val narrative: String
)

/**
 * Impact of physical activity on self-reported energy levels and gratitude on sentiment.
 * Narrative: "Nelle giornate attive, la tua energia è del 35% più alta"
 */
@Immutable
data class ActivityImpact(
    val avgEnergyActiveDay: Float,       // 1-10 avg energy on days with movement
    val avgEnergyRestDay: Float,
    val energyBoostPercent: Float,
    val gratitudeSentimentLift: Float,   // avg sentiment polarity lift on gratitude days
    val sampleSize: Int,
    val narrative: String
)

/**
 * Aggregated progress across all self-growth dimensions.
 */
@Immutable
data class GrowthProgress(
    val habitCompletionRate7d: Float,    // 0-1 last 7 days
    val gratitudeDays7d: Int,            // days with gratitude, last 7
    val reframesCompleted: Int,
    val blocksActive: Int,
    val blocksResolved: Int,
    val ikigaiProgress: Float,           // 0-1 (circles filled / 4)
    val valuesCount: Int,                // confirmed values
    val recurringThoughtsTamed: Int,     // thoughts with a reframe applied
    val strengths: List<String>,         // Italian narrative strength statements
    val suggestions: List<String>        // Italian narrative suggestion statements
)

/**
 * SDT-based wellbeing trend from weekly snapshots.
 */
@Immutable
data class WellbeingTrend(
    val recentScores: List<Float>,       // chronological 0-10 scores (oldest→newest)
    val currentScore: Float,
    val trend: TrendDirection,
    val dominantStrength: String,        // Italian dimension name
    val areaToImprove: String,           // Italian dimension name
    val narrative: String
)

/**
 * Complete cross-domain aggregation result produced by [com.lifo.home.domain.aggregator.WellbeingAggregator].
 */
@Immutable
data class WellbeingAggregationResult(
    val sleepMoodCorrelation: SleepMoodCorrelation?,
    val activityImpact: ActivityImpact?,
    val growthProgress: GrowthProgress,
    val wellbeingTrend: WellbeingTrend?,
    val dataCompleteness: Float,         // 0-1 fraction of domains with data
    val computedAtMillis: Long
)
