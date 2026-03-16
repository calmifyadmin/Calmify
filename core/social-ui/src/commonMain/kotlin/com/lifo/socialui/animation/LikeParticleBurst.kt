package com.lifo.socialui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val ParticleColors = listOf(
    Color(0xFFFF3040),
    Color(0xFFFF6B81),
    Color(0xFFFF9EB5),
    Color(0xFFFFB3C6),
    Color(0xFFFF4D6A),
    Color(0xFFE91E63),
)

private data class Particle(
    val angle: Float,
    val velocity: Float,
    val size: Float,
    val color: Color,
    val gravity: Float = 120f,
)

/**
 * Canvas-based heart particle burst effect.
 * Triggered when [trigger] transitions from false to true.
 * Emits 8 tiny heart particles that fly outward with gravity and fade out over 600ms.
 */
@Composable
fun LikeParticleBurst(
    trigger: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val particles = remember {
        List(8) {
            Particle(
                angle = Random.nextFloat() * 2f * PI.toFloat(),
                velocity = 60f + Random.nextFloat() * 80f,
                size = 3f + Random.nextFloat() * 4f,
                color = ParticleColors.random(),
            )
        }
    }

    LaunchedEffect(trigger) {
        if (trigger) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600),
            )
        }
    }

    if (progress.value > 0f && progress.value < 1f) {
        Canvas(modifier = modifier.size(48.dp)) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val t = progress.value

            particles.forEach { particle ->
                val x = centerX + cos(particle.angle) * particle.velocity * t
                val y = centerY + sin(particle.angle) * particle.velocity * t + particle.gravity * t * t
                val alpha = (1f - t).coerceIn(0f, 1f)
                val scale = (1f - t * 0.5f).coerceAtLeast(0.3f)

                drawMiniHeart(
                    center = Offset(x, y),
                    size = particle.size * scale,
                    color = particle.color.copy(alpha = alpha),
                )
            }
        }
    }
}

private fun DrawScope.drawMiniHeart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        val s = size
        moveTo(center.x, center.y + s * 0.4f)
        cubicTo(
            center.x - s, center.y - s * 0.2f,
            center.x - s * 0.5f, center.y - s,
            center.x, center.y - s * 0.4f,
        )
        cubicTo(
            center.x + s * 0.5f, center.y - s,
            center.x + s, center.y - s * 0.2f,
            center.x, center.y + s * 0.4f,
        )
        close()
    }
    drawPath(path, color)
}
