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
 * Gemini-style Liquid Visualizer using GLSL shaders
 *
 * This component creates a liquid wave effect similar to Gemini Live,
 * responding to voice activity states without requiring real-time audio analysis.
 *
 * Features:
 * - GLSL shader-based liquid wave animation
 * - Smooth transitions between speaking/idle states
 * - GPU-accelerated rendering for 60fps performance
 * - Customizable amplitude and intensity parameters
 * - Compatible with Android API 33+ (RuntimeShader)
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun GeminiLiquidVisualizer(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    backgroundColor: Color = Color.Black
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

    // Amplitude animation based on speaking state (slower transitions)
    val amplitude by animateFloatAsState(
        targetValue = if (isSpeaking) 2.0f else 0.6f,
        animationSpec = tween(
            durationMillis = 2000, // 2 second transition for smoothness
            easing = FastOutSlowInEasing
        ),
        label = "amplitude"
    )

    // Intensity animation for glow and brightness (slower transitions)
    val intensity by animateFloatAsState(
        targetValue = if (isSpeaking) 1.5f else 0.8f,
        animationSpec = tween(
            durationMillis = 2000, // 2 second transition
            easing = FastOutSlowInEasing
        ),
        label = "intensity"
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
            // Update shader uniforms
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("time", time)
            shader.setFloatUniform("amplitude", amplitude)
            shader.setFloatUniform("intensity", intensity)

            // Pass background color to shader (convert Color to RGB floats)
            shader.setFloatUniform(
                "backgroundColor",
                backgroundColor.red,
                backgroundColor.green,
                backgroundColor.blue
            )

            // Pass primary color to shader
            shader.setFloatUniform(
                "primaryColor",
                primaryColor.red,
                primaryColor.green,
                primaryColor.blue
            )

            // Pass secondary color to shader
            shader.setFloatUniform(
                "secondaryColor",
                secondaryColor.red,
                secondaryColor.green,
                secondaryColor.blue
            )

            // Draw the shader to fill entire canvas
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