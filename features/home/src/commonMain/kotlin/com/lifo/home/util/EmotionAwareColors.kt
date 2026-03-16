package com.lifo.home.util

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lifo.util.model.SentimentLabel

/**
 * Emotion-Aware Color System
 * Semantic colors for emotions and sentiments.
 * For theme accent colors, use MaterialTheme.colorScheme (primary, secondary, tertiary).
 */
@Stable
object EmotionAwareColors {

    // ==================== BASE EMOTION COLORS ====================

    val veryPositiveLight = Color(0xFF4CAF50)
    val veryPositiveDark = Color(0xFF2E7D32)
    val veryPositiveSurface = Color(0x1A4CAF50)

    val positiveLight = Color(0xFF81C784)
    val positiveDark = Color(0xFF66BB6A)
    val positiveSurface = Color(0x1A81C784)

    val neutralLight = Color(0xFF90A4AE)
    val neutralDark = Color(0xFF607D8B)
    val neutralSurface = Color(0x1A607D8B)

    val negativeLight = Color(0xFFFF7043)
    val negativeDark = Color(0xFFE64A19)
    val negativeSurface = Color(0x1AFF7043)

    val veryNegativeLight = Color(0xFFEF5350)
    val veryNegativeDark = Color(0xFFD32F2F)
    val veryNegativeSurface = Color(0x1AEF5350)

    // ==================== GRADIENTS ====================

    val positiveGradient = Brush.linearGradient(
        colors = listOf(positiveLight, veryPositiveLight)
    )

    val neutralGradient = Brush.linearGradient(
        colors = listOf(neutralLight, neutralDark)
    )

    val negativeGradient = Brush.linearGradient(
        colors = listOf(negativeLight, veryNegativeLight)
    )

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

    // ==================== ACCENT GRADIENT HELPERS ====================

    fun accentGradient(color: Color) = Brush.linearGradient(
        colors = listOf(color.copy(alpha = 0.15f), color.copy(alpha = 0.05f))
    )

    fun accentVerticalGradient(color: Color) = Brush.verticalGradient(
        colors = listOf(color.copy(alpha = 0.12f), Color.Transparent)
    )

    // ==================== SENTIMENT TO COLOR MAPPING ====================

    fun getSentimentColor(sentiment: SentimentLabel): Color {
        return when (sentiment) {
            SentimentLabel.VERY_POSITIVE -> veryPositiveLight
            SentimentLabel.POSITIVE -> positiveLight
            SentimentLabel.NEUTRAL -> neutralLight
            SentimentLabel.NEGATIVE -> negativeLight
            SentimentLabel.VERY_NEGATIVE -> veryNegativeLight
        }
    }

    fun getSentimentSurfaceTint(sentiment: SentimentLabel): Color {
        return when (sentiment) {
            SentimentLabel.VERY_POSITIVE -> veryPositiveSurface
            SentimentLabel.POSITIVE -> positiveSurface
            SentimentLabel.NEUTRAL -> neutralSurface
            SentimentLabel.NEGATIVE -> negativeSurface
            SentimentLabel.VERY_NEGATIVE -> veryNegativeSurface
        }
    }

    fun getColorForScore(score: Float): Color {
        return when {
            score >= 0.6f -> veryPositiveLight
            score >= 0.2f -> positiveLight
            score >= -0.2f -> neutralLight
            score >= -0.6f -> negativeLight
            else -> veryNegativeLight
        }
    }

    @Composable
    fun getSentimentColorWithScheme(
        sentiment: Float,
        scheme: ColorScheme
    ): Color {
        return when {
            sentiment > 0.3f -> scheme.tertiary
            sentiment < -0.3f -> scheme.error
            else -> scheme.secondary
        }
    }

    fun getDaySurfaceTint(dominantSentiment: SentimentLabel): Color {
        return getSentimentSurfaceTint(dominantSentiment)
    }

    // ==================== WELLBEING SCORE COLORS ====================

    fun getWellbeingScoreColor(score: Float): Color {
        return when {
            score >= 8f -> veryPositiveLight
            score >= 6f -> positiveLight
            score >= 4f -> neutralLight
            score >= 2f -> negativeLight
            else -> veryNegativeLight
        }
    }

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

    object ChartColors {
        val positiveSegment = Color(0xFF4CAF50)
        val neutralSegment = Color(0xFF607D8B)
        val negativeSegment = Color(0xFFEF5350)

        val veryPositiveSegment = Color(0xFF2E7D32)
        val positiveSegmentLight = Color(0xFF81C784)
        val neutralSegmentLight = Color(0xFF90A4AE)
        val negativeSegmentLight = Color(0xFFFF7043)
        val veryNegativeSegment = Color(0xFFD32F2F)

        val emptySegment = Color(0xFFE0E0E0)

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

    object PatternColors {
        val adaptive = Color(0xFF4CAF50)
        val maladaptive = Color(0xFFEF5350)
        val neutral = Color(0xFF607D8B)

        val adaptiveLight = Color(0xFFE8F5E9)
        val maladaptiveLight = Color(0xFFFFEBEE)
        val neutralLight = Color(0xFFECEFF1)
    }

    // ==================== TOPIC COLORS ====================

    fun getTopicColor(sentimentAverage: Float): Color {
        return getColorForScore(sentimentAverage)
    }

    fun getTopicChipBackground(sentimentAverage: Float): Color {
        return getColorForScore(sentimentAverage).copy(alpha = 0.15f)
    }

    // ==================== STREAK/ACHIEVEMENT COLORS ====================

    object AchievementColors {
        val streakFire = Color(0xFFFF3434)
        val streakHot = Color(0xFFFF6600)
        val streakWarm = Color(0xFFF5F108)

        val goalComplete = Color(0xFF4CAF50)
        val goalInProgress = Color(0xFF42A5F5)
        val goalNotStarted = Color(0xFFBDBDBD)

        val common = Color(0xFF90A4AE)
        val rare = Color(0xFF42A5F5)
        val epic = Color(0xFFAB47BC)
        val legendary = Color(0xFFFFCA28)

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

    // ==================== ANIMATION HELPERS ====================

    fun interpolateSentimentColors(
        from: SentimentLabel,
        to: SentimentLabel,
        fraction: Float
    ): Color {
        val fromColor = getSentimentColor(from)
        val toColor = getSentimentColor(to)
        return lerp(fromColor, toColor, fraction)
    }

    private fun lerp(start: Color, stop: Color, fraction: Float): Color {
        return Color(
            red = start.red + (stop.red - start.red) * fraction,
            green = start.green + (stop.green - start.green) * fraction,
            blue = start.blue + (stop.blue - start.blue) * fraction,
            alpha = start.alpha + (stop.alpha - start.alpha) * fraction
        )
    }
}
