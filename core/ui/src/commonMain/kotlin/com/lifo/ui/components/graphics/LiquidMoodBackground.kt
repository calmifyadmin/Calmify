package com.lifo.ui.components.graphics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.*

/**
 * LiquidMoodBackground — Fluid blob background that responds to emotional state.
 *
 * Renders animated, softly moving gradient blobs that shift color
 * based on the current mood palette.
 */
@Composable
fun LiquidMoodBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    blobCount: Int = 4,
    speed: Float = 1f,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_mood")

    val phases = remember(blobCount) {
        List(blobCount) { i -> i * 2.0 * PI / blobCount }
    }

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(durationMillis = (12000 / speed).toInt(), easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "liquid_time",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        for (i in 0 until blobCount) {
            val phase = phases[i]
            val color = colors[i % colors.size]

            // Lissajous-like motion for each blob
            val cx = w * (0.3f + 0.4f * sin(time + phase).toFloat())
            val cy = h * (0.3f + 0.4f * cos(time * 0.7f + phase * 1.3f).toFloat())
            val radius = minOf(w, h) * (0.25f + 0.1f * sin(time * 1.5f + phase * 0.7f).toFloat())

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.3f),
                        color.copy(alpha = 0.1f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
    }
}
