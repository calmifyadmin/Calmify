package com.lifo.home.util

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lifo.util.model.SentimentLabel

/**
 * Emotion-Aware Color System
 * Adapts colors based on emotional state following Material3 Expressive principles
 */
@Stable
object EmotionAwareColors {

    // ==================== BASE EMOTION COLORS ====================

    // Very Positive - Vibrant Green
    val veryPositiveLight = Color(0xFF4CAF50)
    val veryPositiveDark = Color(0xFF2E7D32)
    val veryPositiveSurface = Color(0x1A4CAF50)

    // Positive - Light Green
    val positiveLight = Color(0xFF81C784)
    val positiveDark = Color(0xFF66BB6A)
    val positiveSurface = Color(0x1A81C784)

    // Neutral - Blue Gray
    val neutralLight = Color(0xFF90A4AE)
    val neutralDark = Color(0xFF607D8B)
    val neutralSurface = Color(0x1A607D8B)

    // Negative - Orange
    val negativeLight = Color(0xFFFF7043)
    val negativeDark = Color(0xFFE64A19)
    val negativeSurface = Color(0x1AFF7043)

    // Very Negative - Red
    val veryNegativeLight = Color(0xFFEF5350)
    val veryNegativeDark = Color(0xFFD32F2F)
    val veryNegativeSurface = Color(0x1AEF5350)

    // ==================== GRADIENT DEFINITIONS ====================

    val positiveGradient = Brush.linearGradient(
        colors = listOf(positiveLight, veryPositiveLight)
    )

    val neutralGradient = Brush.linearGradient(
        colors = listOf(neutralLight, neutralDark)
    )

    val negativeGradient = Brush.linearGradient(
        colors = listOf(negativeLight, veryNegativeLight)
    )

    // Radial gradients for charts
    fun positiveRadialGradient(center: androidx.compose.ui.geometry.Offset) = Brush.radialGradient(
        colors = listOf(positiveLight, veryPositiveLight),
        center = center,
        radius = 200f
    )

    fun negativeRadialGradient(center: androidx.compose.ui.geometry.Offset) = Brush.radialGradient(
        colors = listOf(negativeLight, veryNegativeLight),
        center = center,
        radius = 200f
    )

    // ==================== SENTIMENT TO COLOR MAPPING ====================

    /**
     * Get primary color for a sentiment label
     */
    fun getSentimentColor(sentiment: SentimentLabel): Color {
        return when (sentiment) {
            SentimentLabel.VERY_POSITIVE -> veryPositiveLight
            SentimentLabel.POSITIVE -> positiveLight
            SentimentLabel.NEUTRAL -> neutralLight
            SentimentLabel.NEGATIVE -> negativeLight
            SentimentLabel.VERY_NEGATIVE -> veryNegativeLight
        }
    }

    /**
     * Get surface tint color for a sentiment (for card backgrounds)
     */
    fun getSentimentSurfaceTint(sentiment: SentimentLabel): Color {
        return when (sentiment) {
            SentimentLabel.VERY_POSITIVE -> veryPositiveSurface
            SentimentLabel.POSITIVE -> positiveSurface
            SentimentLabel.NEUTRAL -> neutralSurface
            SentimentLabel.NEGATIVE -> negativeSurface
            SentimentLabel.VERY_NEGATIVE -> veryNegativeSurface
        }
    }

    /**
     * Get color based on sentiment score (-1 to +1)
     */
    fun getColorForScore(score: Float): Color {
        return when {
            score >= 0.6f -> veryPositiveLight
            score >= 0.2f -> positiveLight
            score >= -0.2f -> neutralLight
            score >= -0.6f -> negativeLight
            else -> veryNegativeLight
        }
    }

    /**
     * Get color based on sentiment magnitude (0 to 10)
     * Used with ColorScheme for Material3 integration
     */
    @Composable
    fun getSentimentColorWithScheme(
        sentiment: Float,  // -1 to +1
        scheme: ColorScheme
    ): Color {
        return when {
            sentiment > 0.3f -> scheme.tertiary      // Positive
            sentiment < -0.3f -> scheme.error        // Negative
            else -> scheme.secondary                 // Neutral
        }
    }

    /**
     * Get day surface tint based on dominant sentiment
     */
    fun getDaySurfaceTint(dominantSentiment: SentimentLabel): Color {
        return getSentimentSurfaceTint(dominantSentiment)
    }

    // ==================== WELLBEING SCORE COLORS ====================

    /**
     * Get color for wellbeing score (0-10)
     */
    fun getWellbeingScoreColor(score: Float): Color {
        return when {
            score >= 8f -> veryPositiveLight    // Excellent
            score >= 6f -> positiveLight        // Good
            score >= 4f -> neutralLight         // Fair
            score >= 2f -> negativeLight        // Poor
            else -> veryNegativeLight           // Critical
        }
    }

    /**
     * Get gradient for wellbeing chart area fill
     */
    fun getWellbeingGradient(averageScore: Float): Brush {
        val baseColor = getWellbeingScoreColor(averageScore)
        return Brush.verticalGradient(
            colors = listOf(
                baseColor.copy(alpha = 0.4f),
                baseColor.copy(alpha = 0.1f),
                Color.Transparent
            )
        )
    }

    // ==================== CHART COLORS ====================

    /**
     * Colors for donut chart segments
     */
    object ChartColors {
        val positiveSegment = Color(0xFF4CAF50)
        val neutralSegment = Color(0xFF607D8B)
        val negativeSegment = Color(0xFFEF5350)

        // Extended palette for detailed breakdown
        val veryPositiveSegment = Color(0xFF2E7D32)
        val positiveSegmentLight = Color(0xFF81C784)
        val neutralSegmentLight = Color(0xFF90A4AE)
        val negativeSegmentLight = Color(0xFFFF7043)
        val veryNegativeSegment = Color(0xFFD32F2F)

        // Background for empty/no data
        val emptySegment = Color(0xFFE0E0E0)

        // Get segment colors in order
        fun getSegmentColors(): List<Color> = listOf(
            positiveSegment,
            neutralSegment,
            negativeSegment
        )

        fun getDetailedSegmentColors(): List<Color> = listOf(
            veryPositiveSegment,
            positiveSegmentLight,
            neutralSegmentLight,
            negativeSegmentLight,
            veryNegativeSegment
        )
    }

    // ==================== PATTERN COLORS ====================

    /**
     * Colors for cognitive pattern indicators
     */
    object PatternColors {
        val adaptive = Color(0xFF4CAF50)      // Green - positive patterns
        val maladaptive = Color(0xFFEF5350)   // Red - negative patterns
        val neutral = Color(0xFF607D8B)       // Gray - neutral patterns

        val adaptiveLight = Color(0xFFE8F5E9)
        val maladaptiveLight = Color(0xFFFFEBEE)
        val neutralLight = Color(0xFFECEFF1)
    }

    // ==================== TOPIC COLORS ====================

    /**
     * Generate color for topic based on sentiment average
     */
    fun getTopicColor(sentimentAverage: Float): Color {
        return getColorForScore(sentimentAverage)
    }

    /**
     * Get topic chip background with appropriate alpha
     */
    fun getTopicChipBackground(sentimentAverage: Float): Color {
        return getColorForScore(sentimentAverage).copy(alpha = 0.15f)
    }

    // ==================== STREAK/ACHIEVEMENT COLORS ====================

    object AchievementColors {
        val streakFire = Color(0xFFFF5722)       // Orange fire
        val streakHot = Color(0xFFFF9800)        // Yellow-orange
        val streakWarm = Color(0xFFFFC107)       // Yellow

        val goalComplete = Color(0xFF4CAF50)     // Green
        val goalInProgress = Color(0xFF2196F3)   // Blue
        val goalNotStarted = Color(0xFFBDBDBD)   // Gray

        // Badge rarity colors
        val common = Color(0xFF90A4AE)           // Gray
        val rare = Color(0xFF42A5F5)             // Blue
        val epic = Color(0xFFAB47BC)             // Purple
        val legendary = Color(0xFFFFCA28)        // Gold

        fun getStreakColor(days: Int): Color {
            return when {
                days >= 30 -> streakFire
                days >= 14 -> streakHot
                days >= 7 -> streakWarm
                else -> goalInProgress
            }
        }

        fun getRarityColor(rarity: com.lifo.home.domain.model.BadgeRarity): Color {
            return when (rarity) {
                com.lifo.home.domain.model.BadgeRarity.COMMON -> common
                com.lifo.home.domain.model.BadgeRarity.RARE -> rare
                com.lifo.home.domain.model.BadgeRarity.EPIC -> epic
                com.lifo.home.domain.model.BadgeRarity.LEGENDARY -> legendary
            }
        }
    }

    // ==================== ANIMATION HELPER COLORS ====================

    /**
     * Get interpolated color between two sentiments for animations
     */
    fun interpolateSentimentColors(
        from: SentimentLabel,
        to: SentimentLabel,
        fraction: Float
    ): Color {
        val fromColor = getSentimentColor(from)
        val toColor = getSentimentColor(to)
        return lerp(fromColor, toColor, fraction)
    }

    /**
     * Linear interpolation between two colors
     */
    private fun lerp(start: Color, stop: Color, fraction: Float): Color {
        return Color(
            red = start.red + (stop.red - start.red) * fraction,
            green = start.green + (stop.green - start.green) * fraction,
            blue = start.blue + (stop.blue - start.blue) * fraction,
            alpha = start.alpha + (stop.alpha - start.alpha) * fraction
        )
    }
}
