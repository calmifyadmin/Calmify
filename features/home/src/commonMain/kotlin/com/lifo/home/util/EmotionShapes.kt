package com.lifo.home.util

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lifo.util.model.SentimentLabel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 Expressive Emotion Shapes
 *
 * Replaces emojis with expressive geometric shapes that communicate
 * emotional states through form, not symbols.
 *
 * Shape Language:
 * - VERY_POSITIVE: Radiant starburst with soft curves (expansive, joyful)
 * - POSITIVE: Soft rounded blob/flower shape (warm, welcoming)
 * - NEUTRAL: Balanced rounded square (stable, calm)
 * - NEGATIVE: Wavy/irregular edges (unsettled, uncertain)
 * - VERY_NEGATIVE: Angular with sharp corners (tense, stressed)
 *
 * Based on Material 3 Expressive shape system with 35+ expressive geometries
 */

// ==================== SHAPE SCALE (M3 Expressive) ====================

/**
 * M3 Expressive Corner Radius Scale
 * Expanded from 5 to 10 values for finer emotional expression
 */
object EmotionCornerRadius {
    val None = 0.dp
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val ExtraLarge = 24.dp
    val ExtraExtraLarge = 28.dp
    val Full = 100.dp  // Fully circular

    // Semantic mappings for emotions
    val Joyful = Full            // Maximum softness
    val Positive = ExtraLarge    // Very soft
    val Calm = Large             // Balanced
    val Neutral = Medium         // Standard
    val Unsettled = Small        // Less soft
    val Tense = ExtraSmall       // More angular
    val Stressed = None          // Sharp/angular
}

// ==================== EMOTION SHAPE DEFINITIONS ====================

/**
 * Shape configuration for each emotion
 */
@Immutable
data class EmotionShapeStyle(
    val shape: Shape,
    val cornerRadius: Dp,
    val primaryColor: Color,
    val secondaryColor: Color,
    val glowColor: Color,
    val strokeWidth: Dp = 0.dp,
    val elevation: Dp = 2.dp
)

/**
 * Expressive Shapes for Emotions
 * Each emotion has a unique geometric representation
 */
object EmotionShapes {

    // ==================== VERY POSITIVE - Radiant Starburst ====================

    /**
     * Radiant shape with soft points - communicates joy, expansion, celebration
     * Inspired by M3 "Sunny" and "Burst" shapes
     */
    class RadiantShape(
        private val points: Int = 8,
        private val innerRadius: Float = 0.6f,
        private val smoothness: Float = 0.3f
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val outerRadius = min(centerX, centerY)
            val innerRadiusValue = outerRadius * innerRadius

            for (i in 0 until points * 2) {
                val angle = (i * PI / points) - PI / 2
                val radius = if (i % 2 == 0) outerRadius else innerRadiusValue
                val x = centerX + (radius * cos(angle)).toFloat()
                val y = centerY + (radius * sin(angle)).toFloat()

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    // Use quadratic bezier for smooth curves
                    val prevAngle = ((i - 1) * PI / points) - PI / 2
                    val prevRadius = if ((i - 1) % 2 == 0) outerRadius else innerRadiusValue
                    val controlX = centerX + ((prevRadius + radius) / 2 * smoothness * cos((prevAngle + angle) / 2)).toFloat()
                    val controlY = centerY + ((prevRadius + radius) / 2 * smoothness * sin((prevAngle + angle) / 2)).toFloat()
                    path.quadraticBezierTo(controlX, controlY, x, y)
                }
            }
            path.close()
            return Outline.Generic(path)
        }
    }

    // ==================== POSITIVE - Soft Blob/Flower ====================

    /**
     * Soft organic blob shape - communicates warmth, comfort, welcoming
     * Inspired by M3 "Clover" and "Cookie" shapes
     */
    class SoftBlobShape(
        private val lobes: Int = 4,
        private val lobeFactor: Float = 0.3f
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = min(centerX, centerY)

            val points = lobes * 12  // Smooth curve resolution
            for (i in 0 until points) {
                val angle = (i * 2 * PI / points).toFloat()
                // Create gentle waves
                val lobeAngle = angle * lobes
                val r = radius * (1 + lobeFactor * sin(lobeAngle).toFloat())
                val x = centerX + r * cos(angle)
                val y = centerY + r * sin(angle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()
            return Outline.Generic(path)
        }
    }

    // ==================== NEUTRAL - Balanced Squircle ====================

    /**
     * Balanced squircle (super-ellipse) - communicates stability, equilibrium
     * The classic M3 shape for neutral states
     */
    class SquircleShape(
        private val cornerRadius: Float = 0.5f  // 0 = square, 1 = circle
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val width = size.width
            val height = size.height
            val n = 4 + (cornerRadius * 4)  // Super-ellipse exponent

            val points = 100
            for (i in 0 until points) {
                val angle = (i * 2 * PI / points).toFloat()
                val cosA = cos(angle)
                val sinA = sin(angle)

                // Super-ellipse formula
                val x = width / 2 * Math.pow(Math.abs(cosA).toDouble(), 2.0 / n).toFloat() *
                        if (cosA >= 0) 1 else -1
                val y = height / 2 * Math.pow(Math.abs(sinA).toDouble(), 2.0 / n).toFloat() *
                        if (sinA >= 0) 1 else -1

                val px = width / 2 + x
                val py = height / 2 + y

                if (i == 0) {
                    path.moveTo(px, py)
                } else {
                    path.lineTo(px, py)
                }
            }
            path.close()
            return Outline.Generic(path)
        }
    }

    // ==================== NEGATIVE - Wavy/Irregular ====================

    /**
     * Wavy irregular shape - communicates uncertainty, unease
     * Inspired by M3 "Wavy" shapes
     */
    class WavyShape(
        private val waves: Int = 6,
        private val amplitude: Float = 0.15f,
        private val irregularity: Float = 0.1f
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = min(centerX, centerY) * 0.9f

            val points = waves * 20
            for (i in 0 until points) {
                val angle = (i * 2 * PI / points).toFloat()
                // Create irregular waves
                val waveOffset = amplitude * sin(angle * waves).toFloat()
                val irregularOffset = irregularity * sin(angle * 3 + 0.5f).toFloat()
                val r = radius * (1 + waveOffset + irregularOffset)
                val x = centerX + r * cos(angle)
                val y = centerY + r * sin(angle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()
            return Outline.Generic(path)
        }
    }

    // ==================== VERY NEGATIVE - Angular/Sharp ====================

    /**
     * Angular shape with sharp corners - communicates tension, stress
     * Uses cut corners and angular geometry
     */
    class AngularShape(
        private val sides: Int = 6,
        private val sharpness: Float = 0.2f
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = min(centerX, centerY) * 0.9f

            for (i in 0 until sides) {
                val angle = (i * 2 * PI / sides - PI / 2).toFloat()
                val nextAngle = ((i + 1) * 2 * PI / sides - PI / 2).toFloat()

                val x1 = centerX + radius * cos(angle)
                val y1 = centerY + radius * sin(angle)
                val x2 = centerX + radius * cos(nextAngle)
                val y2 = centerY + radius * sin(nextAngle)

                // Add sharp indent between vertices
                val midAngle = (angle + nextAngle) / 2
                val indentRadius = radius * (1 - sharpness)
                val midX = centerX + indentRadius * cos(midAngle)
                val midY = centerY + indentRadius * sin(midAngle)

                if (i == 0) {
                    path.moveTo(x1, y1)
                }
                path.lineTo(midX, midY)
                path.lineTo(x2, y2)
            }
            path.close()
            return Outline.Generic(path)
        }
    }

    // ==================== STREAK SHAPES ====================

    /**
     * Flame shape for streak counter - dynamic and energetic
     */
    class FlameShape : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val width = size.width
            val height = size.height

            path.moveTo(width * 0.5f, 0f)  // Top point
            path.cubicTo(
                width * 0.7f, height * 0.2f,
                width * 0.9f, height * 0.4f,
                width * 0.8f, height * 0.6f
            )
            path.cubicTo(
                width * 0.9f, height * 0.8f,
                width * 0.7f, height,
                width * 0.5f, height
            )
            path.cubicTo(
                width * 0.3f, height,
                width * 0.1f, height * 0.8f,
                width * 0.2f, height * 0.6f
            )
            path.cubicTo(
                width * 0.1f, height * 0.4f,
                width * 0.3f, height * 0.2f,
                width * 0.5f, 0f
            )
            path.close()
            return Outline.Generic(path)
        }
    }

    /**
     * Badge/Medal shape for achievements
     */
    class BadgeShape(
        private val notches: Int = 12
    ) : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val centerX = size.width / 2
            val centerY = size.height / 2
            val outerRadius = min(centerX, centerY)
            val innerRadius = outerRadius * 0.85f

            for (i in 0 until notches * 2) {
                val angle = (i * PI / notches - PI / 2).toFloat()
                val radius = if (i % 2 == 0) outerRadius else innerRadius
                val x = centerX + radius * cos(angle)
                val y = centerY + radius * sin(angle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()
            return Outline.Generic(path)
        }
    }

    /**
     * Heart shape for wellbeing
     */
    class HeartShape : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val path = Path()
            val width = size.width
            val height = size.height

            path.moveTo(width / 2, height * 0.25f)
            // Left curve
            path.cubicTo(
                width * 0.1f, height * 0.1f,
                0f, height * 0.4f,
                width * 0.1f, height * 0.55f
            )
            path.cubicTo(
                width * 0.2f, height * 0.7f,
                width * 0.5f, height,
                width * 0.5f, height
            )
            // Right curve
            path.cubicTo(
                width * 0.5f, height,
                width * 0.8f, height * 0.7f,
                width * 0.9f, height * 0.55f
            )
            path.cubicTo(
                width, height * 0.4f,
                width * 0.9f, height * 0.1f,
                width / 2, height * 0.25f
            )
            path.close()
            return Outline.Generic(path)
        }
    }

    // ==================== HELPER FUNCTIONS ====================

    /**
     * Get the appropriate shape for a sentiment
     */
    fun getShapeForSentiment(sentiment: SentimentLabel): Shape {
        return when (sentiment) {
            SentimentLabel.VERY_POSITIVE -> RadiantShape(points = 8, innerRadius = 0.7f)
            SentimentLabel.POSITIVE -> SoftBlobShape(lobes = 4, lobeFactor = 0.2f)
            SentimentLabel.NEUTRAL -> SquircleShape(cornerRadius = 0.5f)
            SentimentLabel.NEGATIVE -> WavyShape(waves = 5, amplitude = 0.12f)
            SentimentLabel.VERY_NEGATIVE -> AngularShape(sides = 6, sharpness = 0.15f)
        }
    }

    /**
     * Get corner radius for sentiment-based containers
     */
    fun getCornerRadiusForSentiment(sentiment: SentimentLabel): Dp {
        return when (sentiment) {
            SentimentLabel.VERY_POSITIVE -> EmotionCornerRadius.Full
            SentimentLabel.POSITIVE -> EmotionCornerRadius.ExtraLarge
            SentimentLabel.NEUTRAL -> EmotionCornerRadius.Large
            SentimentLabel.NEGATIVE -> EmotionCornerRadius.Small
            SentimentLabel.VERY_NEGATIVE -> EmotionCornerRadius.ExtraSmall
        }
    }

    /**
     * Get RoundedCornerShape for sentiment
     */
    fun getRoundedShapeForSentiment(sentiment: SentimentLabel): RoundedCornerShape {
        return RoundedCornerShape(getCornerRadiusForSentiment(sentiment))
    }
}

// ==================== COMPOSABLE DRAWING UTILITIES ====================

/**
 * Draw emotion indicator shape
 */
fun DrawScope.drawEmotionShape(
    sentiment: SentimentLabel,
    color: Color,
    center: Offset,
    size: Float
) {
    val halfSize = size / 2
    val path = when (sentiment) {
        SentimentLabel.VERY_POSITIVE -> createRadiantPath(center, halfSize, 8)
        SentimentLabel.POSITIVE -> createSoftBlobPath(center, halfSize, 4)
        SentimentLabel.NEUTRAL -> createSquirclePath(center, halfSize)
        SentimentLabel.NEGATIVE -> createWavyPath(center, halfSize, 5)
        SentimentLabel.VERY_NEGATIVE -> createAngularPath(center, halfSize, 6)
    }
    drawPath(path, color)
}

private fun createRadiantPath(center: Offset, radius: Float, points: Int): Path {
    val path = Path()
    val innerRadius = radius * 0.6f

    for (i in 0 until points * 2) {
        val angle = (i * PI / points - PI / 2).toFloat()
        val r = if (i % 2 == 0) radius else innerRadius
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)

        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun createSoftBlobPath(center: Offset, radius: Float, lobes: Int): Path {
    val path = Path()
    val points = lobes * 20
    val lobeFactor = 0.2f

    for (i in 0 until points) {
        val angle = (i * 2 * PI / points).toFloat()
        val r = radius * (1 + lobeFactor * sin(angle * lobes))
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)

        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun createSquirclePath(center: Offset, radius: Float): Path {
    val path = Path()
    val n = 5.0  // Super-ellipse exponent
    val points = 100

    for (i in 0 until points) {
        val angle = (i * 2 * PI / points).toFloat()
        val cosA = cos(angle)
        val sinA = sin(angle)

        val x = radius * Math.pow(Math.abs(cosA).toDouble(), 2.0 / n).toFloat() *
                if (cosA >= 0) 1 else -1
        val y = radius * Math.pow(Math.abs(sinA).toDouble(), 2.0 / n).toFloat() *
                if (sinA >= 0) 1 else -1

        val px = center.x + x
        val py = center.y + y

        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    return path
}

private fun createWavyPath(center: Offset, radius: Float, waves: Int): Path {
    val path = Path()
    val points = waves * 20
    val amplitude = 0.15f

    for (i in 0 until points) {
        val angle = (i * 2 * PI / points).toFloat()
        val r = radius * (1 + amplitude * sin(angle * waves))
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)

        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun createAngularPath(center: Offset, radius: Float, sides: Int): Path {
    val path = Path()
    val sharpness = 0.15f

    for (i in 0 until sides) {
        val angle = (i * 2 * PI / sides - PI / 2).toFloat()
        val nextAngle = ((i + 1) * 2 * PI / sides - PI / 2).toFloat()

        val x1 = center.x + radius * cos(angle)
        val y1 = center.y + radius * sin(angle)
        val x2 = center.x + radius * cos(nextAngle)
        val y2 = center.y + radius * sin(nextAngle)

        val midAngle = (angle + nextAngle) / 2
        val indentRadius = radius * (1 - sharpness)
        val midX = center.x + indentRadius * cos(midAngle)
        val midY = center.y + indentRadius * sin(midAngle)

        if (i == 0) path.moveTo(x1, y1)
        path.lineTo(midX, midY)
        path.lineTo(x2, y2)
    }
    path.close()
    return path
}
