package com.lifo.home.util

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lifo.util.model.SentimentLabel

/**
 * Emotion-Aware Color System
 *
 * All greens now derive from the Calmify sage seed (0xFF4CAF7D) tonal family
 * defined in core/ui Color.kt — no more stock Material green.
 *
 * Sage tonal family:
 *   SageBright  0xFF8AF2BE  (tone 90)
 *   Sage        0xFF6DD4A0  (tone 70)
 *   SageMedium  0xFF4CAF7D  (tone 50 — seed)
 *   SageDim     0xFF2E7D55  (tone 35 — primary)
 *   SageSoft    0xFF7BB89A  (desaturated)
 */
@Stable
object EmotionAwareColors {

    // ==================== BASE EMOTION COLORS (sage-aligned) ====================

    val veryPositiveLight = Color(0xFF4CAF7D)   // SageMedium (seed)
    val veryPositiveDark = Color(0xFF2E7D55)    // SageDim (primary)
    val veryPositiveSurface = Color(0x1A4CAF7D)

    val positiveLight = Color(0xFF7BB89A)       // SageSoft
    val positiveDark = Color(0xFF6DD4A0)        // Sage (tone 70)
    val positiveSurface = Color(0x1A7BB89A)

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

    // ==================== CHART COLORS (sage-aligned) ====================

    object ChartColors {
        val positiveSegment = Color(0xFF4CAF7D)       // SageMedium
        val neutralSegment = Color(0xFF607D8B)
        val negativeSegment = Color(0xFFEF5350)

        val veryPositiveSegment = Color(0xFF2E7D55)    // SageDim
        val positiveSegmentLight = Color(0xFF7BB89A)   // SageSoft
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

    // ==================== PATTERN COLORS (sage-aligned) ====================

    object PatternColors {
        val adaptive = Color(0xFF4CAF7D)               // SageMedium
        val maladaptive = Color(0xFFEF5350)
        val neutral = Color(0xFF607D8B)

        val adaptiveLight = Color(0xFFDFF5EA)           // sage-tinted surface
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
        // Growth leaf tones — monochrome sage scale from seed
        val growthLush = Color(0xFF2E7D55)      // SageDim (30+, deepest)
        val growthFull = Color(0xFF4CAF7D)       // SageMedium (14+)
        val growthYoung = Color(0xFF6DD4A0)      // Sage (7+)
        val growthSprout = Color(0xFF8AF2BE)     // SageBright (1-6)

        val goalComplete = Color(0xFF4CAF7D)     // SageMedium
        val goalInProgress = Color(0xFF6DD4A0)   // Sage
        val goalNotStarted = Color(0xFFBDBDBD)

        val common = Color(0xFF90A4AE)
        val rare = Color(0xFF42A5F5)
        val epic = Color(0xFFAB47BC)
        val legendary = Color(0xFFFFCA28)

        /** Returns a sage-toned color by growth stage */
        fun getStreakColor(days: Int): Color {
            return when {
                days >= 30 -> growthLush
                days >= 14 -> growthFull
                days >= 7 -> growthYoung
                else -> growthSprout
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
