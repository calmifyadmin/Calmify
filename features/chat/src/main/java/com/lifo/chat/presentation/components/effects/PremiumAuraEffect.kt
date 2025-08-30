package com.lifo.chat.presentation.components.effects

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifo.util.model.ChatEmotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium multi-layer aura effect with pulsating animation.
 * Creates a stunning visual effect with 3 gradient layers:
 * - Inner glow (solid)
 * - Middle pulse (animated)
 * - Outer shimmer (particle-like)
 * 
 * Optimized for 60 FPS performance with GPU acceleration.
 */
@Composable
fun PremiumAuraEffect(
    isActive: Boolean,
    emotion: ChatEmotion = ChatEmotion.NEUTRAL,
    intensity: Float = 1f,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    
    // Animation state for pulsing effect
    val infiniteTransition = rememberInfiniteTransition(
        label = "aura_transition"
    )
    
    // Main pulsing animation with emotion-based speed
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1500f / emotion.pulseSpeed).toInt(),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    // Secondary shimmer animation
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
    
    // Alpha animation for fade in/out
    val alpha by animateFloatAsState(
        targetValue = if (isActive) intensity else 0f,
        animationSpec = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        label = "aura_alpha"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                if (alpha > 0.01f) {
                    drawPremiumAura(
                        emotion = emotion,
                        pulseScale = pulseScale,
                        shimmerOffset = shimmerOffset,
                        alpha = alpha,
                        sizePx = sizePx
                    )
                }
            }
    )
}

/**
 * Draws the premium aura effect with multiple layers
 */
private fun DrawScope.drawPremiumAura(
    emotion: ChatEmotion,
    pulseScale: Float,
    shimmerOffset: Float,
    alpha: Float,
    sizePx: Float
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val center = Offset(centerX, centerY)
    val baseRadius = sizePx / 3f
    
    // Layer 1: Inner glow (solid)
    drawInnerGlow(
        center = center,
        radius = baseRadius * 0.6f,
        emotion = emotion,
        alpha = alpha * 0.8f
    )
    
    // Layer 2: Middle pulse (animated)
    drawMiddlePulse(
        center = center,
        radius = baseRadius * pulseScale,
        emotion = emotion,
        alpha = alpha * 0.6f
    )
    
    // Layer 3: Outer shimmer (particle-like)
    drawOuterShimmer(
        center = center,
        radius = baseRadius * 1.4f,
        emotion = emotion,
        shimmerOffset = shimmerOffset,
        alpha = alpha * 0.4f
    )
}

/**
 * Draws the inner glow layer
 */
private fun DrawScope.drawInnerGlow(
    center: Offset,
    radius: Float,
    emotion: ChatEmotion,
    alpha: Float
) {
    val gradient = Brush.radialGradient(
        colors = listOf(
            emotion.primaryColor.copy(alpha = alpha),
            emotion.secondaryColor.copy(alpha = alpha * 0.7f),
            Color.Transparent
        ),
        center = center,
        radius = radius
    )
    
    drawCircle(
        brush = gradient,
        radius = radius,
        center = center
    )
}

/**
 * Draws the middle pulsing layer
 */
private fun DrawScope.drawMiddlePulse(
    center: Offset,
    radius: Float,
    emotion: ChatEmotion,
    alpha: Float
) {
    val gradient = Brush.radialGradient(
        colors = listOf(
            Color.Transparent,
            emotion.primaryColor.copy(alpha = alpha * 0.8f),
            emotion.tertiaryColor.copy(alpha = alpha * 0.6f),
            Color.Transparent
        ),
        center = center,
        radius = radius
    )
    
    drawCircle(
        brush = gradient,
        radius = radius,
        center = center
    )
    
    // Add pulsing ring effect
    drawCircle(
        color = emotion.secondaryColor.copy(alpha = alpha * 0.3f),
        radius = radius * 0.9f,
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
}

/**
 * Draws the outer shimmer layer with particle effects
 */
private fun DrawScope.drawOuterShimmer(
    center: Offset,
    radius: Float,
    emotion: ChatEmotion,
    shimmerOffset: Float,
    alpha: Float
) {
    // Create shimmer particles around the circle
    val particleCount = 12
    val particleColors = emotion.getParticleColors()
    
    for (i in 0 until particleCount) {
        val angle = (i * 360f / particleCount) + shimmerOffset
        val radians = Math.toRadians(angle.toDouble())
        
        val particleX = center.x + (radius * cos(radians)).toFloat()
        val particleY = center.y + (radius * sin(radians)).toFloat()
        
        val particleAlpha = alpha * (0.5f + 0.5f * cos(radians + shimmerOffset * 0.01f)).toFloat()
        val colorIndex = i % particleColors.size
        
        // Main particle
        drawCircle(
            color = particleColors[colorIndex].copy(alpha = particleAlpha),
            radius = 4.dp.toPx(),
            center = Offset(particleX, particleY)
        )
        
        // Particle glow
        val glowGradient = Brush.radialGradient(
            colors = listOf(
                particleColors[colorIndex].copy(alpha = particleAlpha * 0.6f),
                Color.Transparent
            ),
            center = Offset(particleX, particleY),
            radius = 8.dp.toPx()
        )
        
        drawCircle(
            brush = glowGradient,
            radius = 8.dp.toPx(),
            center = Offset(particleX, particleY)
        )
    }
    
    // Outer ring shimmer (simplified for compatibility)
    drawCircle(
        color = emotion.primaryColor.copy(alpha = alpha * 0.3f),
        radius = radius * 1.1f,
        center = center,
        style = Stroke(width = 3.dp.toPx())
    )
    
    // Additional shimmer ring
    drawCircle(
        color = emotion.secondaryColor.copy(alpha = alpha * 0.2f),
        radius = radius * 1.2f,
        center = center,
        style = Stroke(width = 1.dp.toPx())
    )
}

/**
 * Compact aura effect for smaller UI elements
 */
@Composable
fun CompactAuraEffect(
    isActive: Boolean,
    emotion: ChatEmotion = ChatEmotion.NEUTRAL,
    modifier: Modifier = Modifier
) {
    PremiumAuraEffect(
        isActive = isActive,
        emotion = emotion,
        intensity = 0.7f,
        size = 60.dp,
        modifier = modifier
    )
}

/**
 * Subtle aura effect for background elements
 */
@Composable
fun SubtleAuraEffect(
    isActive: Boolean,
    emotion: ChatEmotion = ChatEmotion.NEUTRAL,
    modifier: Modifier = Modifier
) {
    PremiumAuraEffect(
        isActive = isActive,
        emotion = emotion,
        intensity = 0.4f,
        size = 200.dp,
        modifier = modifier
    )
}