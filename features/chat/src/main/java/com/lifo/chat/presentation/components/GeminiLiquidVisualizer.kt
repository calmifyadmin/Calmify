package com.lifo.chat.presentation.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifo.chat.R

/**
 * Intelligent Dual-Voice Liquid Visualizer using GLSL shaders
 *
 * Enhanced Gemini-style liquid wave effect with real-time audio intelligence.
 * Responds to actual voice levels, conversation context, and emotional intensity.
 *
 * Features:
 * - GLSL shader-based liquid wave animation
 * - Real-time audio level visualization (User + AI separate)
 * - Conversation context awareness
 * - Emotional intensity mapping
 * - Spatial voice positioning
 * - GPU-accelerated rendering for 60fps performance
 * - Compatible with Android API 33+ (RuntimeShader)
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun GeminiLiquidVisualizer(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = Color.Black,
    // NEW: Advanced audio intelligence parameters
    userVoiceLevel: Float = 0f,
    aiVoiceLevel: Float = 0f,
    emotionalIntensity: Float = 0.5f,
    conversationMode: String = "casual",
    isUserSpeaking: Boolean = false,
    isAiSpeaking: Boolean = false
) {
    val context = LocalContext.current

    // Load and compile the GLSL shader
    val shader = remember {
        try {
            val shaderSource = context.resources.openRawResource(R.raw.liquid_wave)
                .bufferedReader().use { it.readText() }
            RuntimeShader(shaderSource)
        } catch (e: Exception) {
            // Fallback if shader loading fails
            null
        }
    }

    // Continuous time animation for wave movement (MUCH slower)
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_wave_time")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 60000, // 60 second loop for ultra smooth
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // BALANCED: Responsive but elegant amplitude calculation
    val intelligentAmplitude = remember(userVoiceLevel, aiVoiceLevel, emotionalIntensity) {
        val baseAmplitude = when {
            isUserSpeaking && isAiSpeaking -> 2.2f + (userVoiceLevel + aiVoiceLevel) * 1.8f // Noticeable for overlapping speech
            isUserSpeaking -> 1.5f + userVoiceLevel * 2.2f // Good user speaking response
            isAiSpeaking -> 1.4f + aiVoiceLevel * 2.0f // Good AI speaking response
            else -> 0.5f + (userVoiceLevel + aiVoiceLevel) * 1.2f // Subtle background activity
        }
        
        // Apply emotional intensity multiplier
        val emotionalMultiplier = 0.8f + emotionalIntensity * 0.7f // Moderate emotional response
        
        // Apply conversation mode modifier
        val modeMultiplier = when (conversationMode.lowercase()) {
            "business", "meeting" -> 0.85f // Professional but visible
            "presentation" -> 0.7f // Minimal but present
            "brainstorm", "excited" -> 1.3f // Higher energy but controlled
            "intimate", "calm" -> 0.8f // Gentle and elegant
            else -> 1.0f // Balanced default
        }
        
        (baseAmplitude * emotionalMultiplier * modeMultiplier).coerceIn(0.3f, 4.5f) // Elegant range that stays on screen
    }

    // Smooth amplitude transitions with fluid interpolation
    val amplitude by animateFloatAsState(
        targetValue = intelligentAmplitude,
        animationSpec = spring(
            dampingRatio = when {
                isUserSpeaking && !isAiSpeaking -> Spring.DampingRatioMediumBouncy // Responsive for user
                isAiSpeaking && !isUserSpeaking -> Spring.DampingRatioMediumBouncy // More responsive for AI
                conversationMode == "presentation" -> Spring.DampingRatioLowBouncy // Still smooth but more responsive
                else -> Spring.DampingRatioMediumBouncy // Balanced default
            },
            stiffness = when {
                emotionalIntensity > 0.7f -> Spring.StiffnessMediumLow // Enhanced responsiveness for high energy
                conversationMode == "business" -> Spring.StiffnessMediumLow // More responsive for business
                else -> Spring.StiffnessMediumLow // Enhanced balanced response
            }
        ),
        label = "intelligent_amplitude"
    )

    // INTELLIGENT: Dynamic intensity based on conversation context
    val intelligentIntensity = remember(emotionalIntensity, conversationMode, userVoiceLevel, aiVoiceLevel) {
        val baseIntensity = when {
            isUserSpeaking && isAiSpeaking -> 2.0f // Maximum intensity for overlapping speech
            isUserSpeaking -> 1.2f + userVoiceLevel * 0.8f // User-driven intensity
            isAiSpeaking -> 1.0f + aiVoiceLevel * 0.6f // AI-driven intensity  
            else -> 0.6f + emotionalIntensity * 0.4f // Ambient based on emotion
        }
        
        // Context-aware intensity modulation
        val contextMultiplier = when (conversationMode.lowercase()) {
            "business", "meeting" -> 0.9f // Slightly subdued professionalism
            "presentation" -> 0.7f // Minimal glow during presentations
            "brainstorm" -> 1.4f // High energy creativity
            "intimate" -> 1.1f // Warm, inviting glow
            else -> 1.0f
        }
        
        (baseIntensity * contextMultiplier * (0.8f + emotionalIntensity * 0.4f)).coerceIn(0.5f, 2.5f)
    }

    // Smooth intensity transitions with spring physics
    val intensity by animateFloatAsState(
        targetValue = intelligentIntensity,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow // Slow, smooth transitions for intensity
        ),
        label = "intelligent_intensity"
    )
    
    // SPATIAL: Calculate voice positioning for dual-voice separation
    val voicePosition = remember(isUserSpeaking, isAiSpeaking, userVoiceLevel, aiVoiceLevel) {
        when {
            isUserSpeaking && !isAiSpeaking -> -0.3f + userVoiceLevel * 0.4f // User left-center
            isAiSpeaking && !isUserSpeaking -> 0.3f + aiVoiceLevel * 0.4f // AI right-center  
            isUserSpeaking && isAiSpeaking -> 0f // Center when both speaking
            else -> 0f // Neutral center
        }.coerceIn(-0.7f, 0.7f)
    }
    
    val spatialPosition by animateFloatAsState(
        targetValue = voicePosition,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, // More smooth, less bouncy
            stiffness = Spring.StiffnessVeryLow // Very slow, fluid transitions between speakers
        ),
        label = "spatial_position"
    )

    // Fallback when shader is not available (API < 33 or loading failed)
    if (shader == null) {
        FallbackLiquidVisualizer(
            isSpeaking = isSpeaking,
            modifier = modifier,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            backgroundColor = backgroundColor
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor) // Set background here
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Update basic shader uniforms
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("amplitude", amplitude)
            shader.setFloatUniform("intensity", intensity)

            // NEW: Advanced audio intelligence uniforms
            shader.setFloatUniform("userVoiceLevel", userVoiceLevel)
            shader.setFloatUniform("aiVoiceLevel", aiVoiceLevel)
            shader.setFloatUniform("emotionalIntensity", emotionalIntensity)
            shader.setFloatUniform("spatialPosition", spatialPosition)
            
            // Voice state indicators for shader logic
            shader.setFloatUniform("isUserSpeaking", if (isUserSpeaking) 1.0f else 0.0f)
            shader.setFloatUniform("isAiSpeaking", if (isAiSpeaking) 1.0f else 0.0f)
            
            // Conversation mode as numeric value for shader
            val modeValue = when (conversationMode.lowercase()) {
                "business", "meeting" -> 1.0f
                "presentation" -> 2.0f
                "brainstorm", "excited" -> 3.0f
                "intimate", "calm" -> 4.0f
                else -> 0.0f // casual
            }
            shader.setFloatUniform("conversationMode", modeValue)

            // Pass color uniforms (existing)
            shader.setFloatUniform(
                "backgroundColor",
                backgroundColor.red,
                backgroundColor.green,
                backgroundColor.blue
            )

            shader.setFloatUniform(
                "primaryColor",
                primaryColor.red,
                primaryColor.green,
                primaryColor.blue
            )

            shader.setFloatUniform(
                "secondaryColor",
                secondaryColor.red,
                secondaryColor.green,
                secondaryColor.blue
            )

            // Draw the intelligent shader to fill entire canvas
            drawRect(
                brush = ShaderBrush(shader),
                size = size
            )
        }
    }
}

/**
 * Fallback visualizer for devices that don't support RuntimeShader (API < 33)
 */
@Composable
private fun FallbackLiquidVisualizer(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = Color.Black
) {
    // Time-based animation for wave movement
    val infiniteTransition = rememberInfiniteTransition(label = "fallback_wave")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f, // 2π for full sine wave cycle
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_time"
    )

    // Amplitude based on speaking state
    val amplitude by animateFloatAsState(
        targetValue = if (isSpeaking) 0.3f else 0.1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "fallback_amplitude"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = center
            val width = size.width
            val height = size.height

            // Create gradient brush
            val brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.8f),
                    secondaryColor.copy(alpha = 0.8f)
                )
            )

            // Draw animated wave using Path
            val path = androidx.compose.ui.graphics.Path()
            val waveHeight = height * 0.6f

            path.moveTo(0f, height)

            // Generate wave points
            for (x in 0..width.toInt() step 5) {
                val normalizedX = x / width
                val wave1 = kotlin.math.sin((normalizedX * 8f + time) * 2f) * amplitude * 20f
                val wave2 = kotlin.math.sin((normalizedX * 12f - time * 1.5f) * 2f) * amplitude * 15f
                val y = waveHeight + wave1 + wave2
                path.lineTo(x.toFloat(), y)
            }

            path.lineTo(width, height)
            path.close()

            // Draw the wave
            drawPath(
                path = path,
                brush = brush,
                alpha = if (isSpeaking) 0.9f else 0.6f
            )

            // Add glow effect
            if (isSpeaking) {
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    blendMode = BlendMode.Overlay
                )
            }
        }
    }
}

/**
 * Compact version for smaller spaces
 */
@Composable
fun CompactGeminiLiquidVisualizer(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GeminiLiquidVisualizer(
            isSpeaking = isSpeaking,
            modifier = modifier.size(100.dp),
            primaryColor = primaryColor,
            secondaryColor = secondaryColor
        )
    } else {
        FallbackLiquidVisualizer(
            isSpeaking = isSpeaking,
            modifier = modifier.size(100.dp),
            primaryColor = primaryColor,
            secondaryColor = secondaryColor
        )
    }
}