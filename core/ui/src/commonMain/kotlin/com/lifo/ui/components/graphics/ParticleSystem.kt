package com.lifo.ui.components.graphics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlin.random.Random

/**
 * ParticleSystem — Generalized particle emitter for celebrations, milestones, etc.
 *
 * Configurable: gravity, spread, lifetime, colors, shapes.
 * Generalized from the existing LikeParticleBurst pattern.
 */
data class ParticleConfig(
    val count: Int = 40,
    val colors: List<Color> = listOf(Color(0xFFFFD700), Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFAB7EF6)),
    val minSpeed: Float = 150f,
    val maxSpeed: Float = 500f,
    val gravity: Float = 400f,
    val lifetime: Float = 2f,
    val minSize: Float = 3f,
    val maxSize: Float = 8f,
    val spread: Float = 360f,       // Degrees of spread (360 = all directions)
    val baseAngle: Float = -90f,    // Upward by default
    val fadeOut: Boolean = true,
    val shape: ParticleShape = ParticleShape.CIRCLE,
)

enum class ParticleShape { CIRCLE, SQUARE, STAR }

private data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val life: Float,
    val maxLife: Float,
)

@Composable
fun ParticleSystem(
    trigger: Boolean,
    origin: Offset = Offset.Unspecified,
    config: ParticleConfig = ParticleConfig(),
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {},
) {
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var isActive by remember { mutableStateOf(false) }

    // Spawn particles on trigger
    LaunchedEffect(trigger) {
        if (trigger && !isActive) {
            isActive = true
            val random = Random(kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
            val ox = if (origin == Offset.Unspecified) 0f else origin.x
            val oy = if (origin == Offset.Unspecified) 0f else origin.y

            particles = List(config.count) {
                val angleDeg = config.baseAngle - config.spread / 2 + random.nextFloat() * config.spread
                val angleRad = angleDeg.toDouble() * PI / 180.0
                val speed = config.minSpeed + random.nextFloat() * (config.maxSpeed - config.minSpeed)
                Particle(
                    x = ox,
                    y = oy,
                    vx = (speed * cos(angleRad)).toFloat(),
                    vy = (speed * sin(angleRad)).toFloat(),
                    color = config.colors[random.nextInt(config.colors.size)],
                    size = config.minSize + random.nextFloat() * (config.maxSize - config.minSize),
                    rotation = random.nextFloat() * 360f,
                    rotationSpeed = -180f + random.nextFloat() * 360f,
                    life = config.lifetime,
                    maxLife = config.lifetime,
                )
            }
        }
    }

    // Animation loop
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val tick by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16, easing = LinearEasing), RepeatMode.Restart),
        label = "particle_tick",
    )

    LaunchedEffect(tick) {
        if (!isActive || particles.isEmpty()) return@LaunchedEffect
        val dt = 0.016f
        particles = particles.mapNotNull { p ->
            val newLife = p.life - dt
            if (newLife <= 0f) return@mapNotNull null
            p.copy(
                x = p.x + p.vx * dt,
                y = p.y + p.vy * dt + 0.5f * config.gravity * dt * dt,
                vy = p.vy + config.gravity * dt,
                rotation = p.rotation + p.rotationSpeed * dt,
                life = newLife,
            )
        }
        if (particles.isEmpty()) {
            isActive = false
            onComplete()
        }
    }

    if (isActive) {
        Canvas(modifier = modifier.fillMaxSize()) {
            particles.forEach { p ->
                val alpha = if (config.fadeOut) (p.life / p.maxLife).coerceIn(0f, 1f) else 1f
                val color = p.color.copy(alpha = alpha)

                when (config.shape) {
                    ParticleShape.CIRCLE -> {
                        drawCircle(color, p.size, Offset(p.x, p.y))
                    }
                    ParticleShape.SQUARE -> {
                        drawRect(
                            color,
                            topLeft = Offset(p.x - p.size / 2, p.y - p.size / 2),
                            size = androidx.compose.ui.geometry.Size(p.size, p.size),
                        )
                    }
                    ParticleShape.STAR -> {
                        // Simplified star as a slightly larger circle with glow
                        drawCircle(color.copy(alpha = alpha * 0.3f), p.size * 2f, Offset(p.x, p.y))
                        drawCircle(color, p.size, Offset(p.x, p.y))
                    }
                }
            }
        }
    }
}
