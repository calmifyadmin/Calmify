package com.lifo.write.wizard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Schermata finale del wizard con animazione celebrativa
 */
@Composable
fun CompletionStep(
    metrics: PsychologicalMetrics,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Animazione di entrata
    val bounceScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Haptic feedback celebrativo
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(100)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Animazione bounce
        bounceScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Confetti in background
        ConfettiAnimation(
            modifier = Modifier.fillMaxSize()
        )

        // Contenuto principale
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .graphicsLayer {
                    scaleX = bounceScale.value
                    scaleY = bounceScale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Emoji celebrativa
            Text(
                text = "\uD83C\uDF89", // Party popper
                fontSize = 72.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Grazie!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Il tuo contributo ci aiutera' a fornirti\nun'assistenza piu' mirata",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Riepilogo metriche (opzionale, compatto)
            if (metrics.hasBeenModified()) {
                MetricsSummaryChips(metrics = metrics)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Pulsante per chiudere
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Fatto")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "\uD83D\uDC9C", // Purple heart
                fontSize = 24.sp
            )
        }
    }
}

/**
 * Chips riepilogo metriche
 */
@Composable
private fun MetricsSummaryChips(
    metrics: PsychologicalMetrics,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Le tue risposte:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = buildString {
                append("Emozione: ${metrics.emotionIntensity}/10 | ")
                append("Stress: ${metrics.stressLevel}/10 | ")
                append("Energia: ${metrics.energyLevel}/10")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Animazione confetti semplice
 */
@Composable
fun ConfettiAnimation(
    modifier: Modifier = Modifier,
    particleCount: Int = 50
) {
    val confettiColors = listOf(
        Color(0xFFE91E63), // Rosa
        Color(0xFF9C27B0), // Viola
        Color(0xFF2196F3), // Blu
        Color(0xFF4CAF50), // Verde
        Color(0xFFFFEB3B), // Giallo
        Color(0xFFFF9800), // Arancione
        Color(0xFF00BCD4)  // Ciano
    )

    val particles = remember {
        mutableStateListOf<ConfettiParticle>().apply {
            repeat(particleCount) {
                add(
                    ConfettiParticle(
                        x = Random.nextFloat(),
                        y = Random.nextFloat() * -1f, // Parte da sopra
                        color = confettiColors.random(),
                        size = Random.nextFloat() * 10f + 5f,
                        speedY = Random.nextFloat() * 2f + 1f,
                        speedX = Random.nextFloat() * 2f - 1f,
                        rotation = Random.nextFloat() * 360f
                    )
                )
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confettiProgress"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        particles.forEachIndexed { index, particle ->
            val progress = (animationProgress + index * 0.02f) % 1f
            val currentY = particle.y + progress * (1f + particle.speedY)
            val currentX = particle.x + progress * particle.speedX * 0.3f
            val rotation = particle.rotation + progress * 360f

            if (currentY <= 1.2f) {
                drawCircle(
                    color = particle.color.copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                    radius = particle.size,
                    center = Offset(
                        x = (currentX % 1f) * width,
                        y = currentY * height
                    )
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val color: Color,
    val size: Float,
    val speedY: Float,
    val speedX: Float,
    val rotation: Float
)
