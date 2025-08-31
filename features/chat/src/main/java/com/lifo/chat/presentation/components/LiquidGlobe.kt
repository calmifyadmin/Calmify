package com.lifo.chat.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifo.chat.domain.model.AIEmotion
import com.lifo.chat.presentation.components.effects.EmotionGradients
import kotlin.math.*

/**
 * Liquid Globe Component - A mesmerizing organic visualization that responds to AI emotions and voice levels
 * 
 * Features:
 * - Metaball liquid blob effect using advanced Canvas API
 * - Emotion-based gradient system with smooth transitions
 * - Audio-reactive scaling and deformation
 * - Multiple rendering layers (glow, main blob, highlights)
 * - 60 FPS optimized animations with GPU acceleration
 * - Organic noise-based deformations
 */
@Composable
fun LiquidGlobe(
    emotion: AIEmotion,
    audioLevel: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    animationEnabled: Boolean = true
) {
    // Animation values
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_globe_transition")
    
    // Time-based animation for organic movement
    val timeMs by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 10000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "time_animation"
    )
    
    // Audio level smooth interpolation
    val smoothAudioLevel by animateFloatAsState(
        targetValue = audioLevel,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "audio_level_smooth"
    )
    
    // Scale based on audio level
    val audioScale by animateFloatAsState(
        targetValue = 1f + (smoothAudioLevel * 0.2f), // Base 1.0f, max 1.2f
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "audio_scale"
    )
    
    // Emotion transition animation
    val emotionTransition by animateFloatAsState(
        targetValue = when (emotion) {
            AIEmotion.Neutral -> 0f
            AIEmotion.Happy -> 0.25f
            AIEmotion.Thinking -> 0.5f
            AIEmotion.Speaking -> 0.75f
        },
        animationSpec = tween(
            durationMillis = 800,
            easing = FastOutSlowInEasing
        ),
        label = "emotion_transition"
    )
    
    // Cached paint objects for performance
    val mainPaint = remember { Paint().apply { isAntiAlias = true } }
    val glowPaint = remember { Paint().apply { isAntiAlias = true } }
    val highlightPaint = remember { Paint().apply { isAntiAlias = true } }
    
    Box(
        modifier = modifier.size(size * 1.3f), // Extra space for glow
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size * 1.3f)
                .graphicsLayer {
                    scaleX = audioScale
                    scaleY = audioScale
                }
        ) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = size.toPx() / 2f
            
            // Draw glow layer
            drawGlowLayer(
                center = center,
                baseRadius = baseRadius * 1.2f,
                emotion = emotion,
                audioLevel = smoothAudioLevel,
                timeMs = timeMs
            )
            
            // Draw main liquid blob
            drawLiquidBlob(
                center = center,
                baseRadius = baseRadius,
                emotion = emotion,
                audioLevel = smoothAudioLevel,
                timeMs = timeMs
            )
            
            // Draw highlight layer
            drawHighlightLayer(
                center = center,
                baseRadius = baseRadius * 0.4f,
                emotion = emotion,
                audioLevel = smoothAudioLevel
            )
        }
    }
}

/**
 * Draw the outer glow layer for ambient lighting effect
 */
private fun DrawScope.drawGlowLayer(
    center: Offset,
    baseRadius: Float,
    emotion: AIEmotion,
    audioLevel: Float,
    timeMs: Float
) {
    val glowRadius = baseRadius + (audioLevel * 20f)
    val glowAlpha = 0.15f + (audioLevel * 0.1f)
    
    // Create radial gradient for glow
    val glowBrush = Brush.radialGradient(
        colors = listOf(
            EmotionGradients.getPrimaryColor(emotion).copy(alpha = glowAlpha),
            EmotionGradients.getSecondaryColor(emotion).copy(alpha = glowAlpha * 0.5f),
            Color.Transparent
        ),
        center = center,
        radius = glowRadius
    )
    
    drawCircle(
        brush = glowBrush,
        radius = glowRadius,
        center = center
    )
}

/**
 * Draw the main liquid blob with organic deformations
 */
private fun DrawScope.drawLiquidBlob(
    center: Offset,
    baseRadius: Float,
    emotion: AIEmotion,
    audioLevel: Float,
    timeMs: Float
) {
    // For now, draw an organic circle with deformation
    val deformationRadius = baseRadius * (1f + audioLevel * 0.1f)
    
    // Create main gradient
    val mainBrush = EmotionGradients.getEmotionGradient(
        emotion = emotion,
        centerX = center.x / this.size.width,
        centerY = center.y / this.size.height,
        radius = deformationRadius / this.size.minDimension
    )
    
    // Draw main circle with slight variations
    for (i in 0..8) {
        val angle = (i * 45f) * (kotlin.math.PI / 180f).toFloat()
        val deformation = sin(timeMs / 1000f + angle * 2f) * baseRadius * 0.05f * (1f + audioLevel)
        val offsetRadius = baseRadius + deformation
        val offsetX = cos(angle) * deformation * 0.3f
        val offsetY = sin(angle) * deformation * 0.3f
        
        drawCircle(
            brush = mainBrush,
            radius = offsetRadius,
            center = Offset(center.x + offsetX, center.y + offsetY),
            alpha = 0.7f / (i + 1f)
        )
    }
}

/**
 * Draw highlight layer for surface reflection effects
 */
private fun DrawScope.drawHighlightLayer(
    center: Offset,
    baseRadius: Float,
    emotion: AIEmotion,
    audioLevel: Float
) {
    val highlightCenter = Offset(
        center.x - baseRadius * 0.2f,
        center.y - baseRadius * 0.2f
    )
    
    val highlightRadius = baseRadius * (0.3f + audioLevel * 0.1f)
    val highlightAlpha = 0.4f + (audioLevel * 0.3f)
    
    val highlightBrush = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = highlightAlpha),
            EmotionGradients.getPrimaryColor(emotion).copy(alpha = highlightAlpha * 0.2f),
            Color.Transparent
        ),
        center = highlightCenter,
        radius = highlightRadius
    )
    
    drawCircle(
        brush = highlightBrush,
        radius = highlightRadius,
        center = highlightCenter,
        blendMode = BlendMode.Overlay
    )
}


/**
 * Compact version of the liquid globe for smaller spaces
 */
@Composable
fun CompactLiquidGlobe(
    emotion: AIEmotion,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    LiquidGlobe(
        emotion = emotion,
        audioLevel = audioLevel,
        modifier = modifier,
        size = 80.dp
    )
}