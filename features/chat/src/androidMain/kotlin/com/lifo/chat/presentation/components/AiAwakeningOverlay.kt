package com.lifo.chat.presentation.components

import android.graphics.RuntimeShader
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.chat.R
import kotlinx.coroutines.delay

/**
 * AI Awakening Overlay — dissolves organically to reveal the avatar underneath.
 *
 * Architecture:
 * 1. AGSL noise-dissolve shader (API 33+) OR animated blur fallback (API <33)
 * 2. Audio-synced concentric pulse rings emanating from center
 * 3. Center breathing orb that expands and fades
 * 4. Frosted blur that decreases progressively
 * 5. MediaPlayer for the 6.5s awakening audio
 *
 * The overlay sits ON TOP of the avatar. As progress goes 0→1, it dissolves
 * from the center outward with glowing edges, revealing the avatar beneath.
 * Everything is theme-aware and blends with the avatar's visual space.
 */
@Composable
fun AiAwakeningOverlay(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    durationMs: Long = 6500L,
    onComplete: () -> Unit = {}
) {
    if (!isActive) return

    val context = LocalContext.current

    // Master progress: 0.0 → 1.0 over durationMs
    val progress = remember { Animatable(0f) }

    // Audio envelope — pre-extracted from lifo2.wav (65 samples, 100ms each)
    val audioEnvelope = remember {
        floatArrayOf(
            0.42f, 0.442f, 0.444f, 0.697f, 0.963f, 0.918f, 1.0f, 0.8f, 0.72f, 0.912f,
            0.549f, 0.504f, 0.631f, 0.55f, 0.372f, 0.348f, 0.348f, 0.596f, 0.43f, 0.367f,
            0.296f, 0.494f, 0.59f, 0.483f, 0.507f, 0.635f, 0.427f, 0.301f, 0.431f, 0.429f,
            0.381f, 0.339f, 0.439f, 0.286f, 0.222f, 0.247f, 0.236f, 0.209f, 0.312f, 0.279f,
            0.153f, 0.465f, 0.362f, 0.251f, 0.178f, 0.31f, 0.194f, 0.104f, 0.28f, 0.18f,
            0.133f, 0.088f, 0.164f, 0.085f, 0.02f, 0.03f, 0.017f, 0.012f, 0.008f, 0.014f,
            0.006f, 0.004f, 0.006f, 0.003f, 0.002f
        )
    }

    // Current audio level interpolated from envelope
    val currentAudioLevel by remember {
        derivedStateOf {
            val idx = (progress.value * (audioEnvelope.size - 1)).toInt()
                .coerceIn(0, audioEnvelope.size - 2)
            val frac = progress.value * (audioEnvelope.size - 1) - idx
            audioEnvelope[idx] * (1f - frac) + audioEnvelope[idx + 1] * frac
        }
    }

    // Blur amount: starts at max, decreases to 0
    val blurDp by remember {
        derivedStateOf {
            val p = progress.value
            // Ease-out: fast initial clear, slow final polish
            val eased = 1f - (1f - p) * (1f - p)
            (1f - eased) * 24f
        }
    }

    // Wait for Filament to finish heavy init before starting animation + audio.
    // On re-entry, Engine/Scene/VRM reload blocks main thread for ~500-800ms.
    // This delay lets that complete first so our animation runs stutter-free.
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Wait for Filament to finish heavy init — yield frames then add buffer
        delay(500) // let Engine/Scene/VRM model load complete
        ready = true
    }

    // Audio playback — starts only when ready
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        val mp = MediaPlayer().apply {
            try {
                val afd = context.resources.openRawResourceFd(R.raw.ai_awakening)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setVolume(0.7f, 0.7f)
                setOnPreparedListener { player ->
                    player.start()
                }
                prepareAsync()
                mediaPlayerRef.value = this
            } catch (e: Exception) {
                release()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerRef.value?.let {
                try {
                    if (it.isPlaying) it.stop()
                    it.release()
                } catch (_: Exception) {}
            }
            mediaPlayerRef.value = null
        }
    }

    // Master animation — starts only when ready
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs.toInt(),
                easing = LinearEasing
            )
        )
    }

    // Reveal UI controls at ~80% progress — toolbar fades in while overlay is still dissolving
    LaunchedEffect(progress.value) {
        if (progress.value >= 0.80f) {
            onComplete()
        }
    }

    // Pulse rings state
    val infiniteTransition = rememberInfiniteTransition(label = "awakeningPulse")
    val ringPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringPulse"
    )

    // Secondary ring offset
    val ringPulse2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringPulse2"
    )

    // Orb breathing
    val orbBreath by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreath"
    )

    // Theme colors — surfaceColor semi-transparent so avatar is faintly visible underneath
    val glowColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: Animated blur over the entire overlay area
        // This blurs the avatar underneath via the overlay's own content
        val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurDp > 0.5f) {
            Modifier.fillMaxSize().blur(blurDp.dp)
        } else {
            Modifier.fillMaxSize()
        }

        // Layer 2: AGSL shader dissolve (API 33+) or Canvas fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ShaderDissolveLayer(
                progress = progress.value,
                audioLevel = currentAudioLevel,
                glowColor = glowColor,
                surfaceColor = surfaceColor,
                modifier = blurMod
            )
        } else {
            // Fallback: simple radial gradient that fades
            CanvasDissolveLayer(
                progress = progress.value,
                audioLevel = currentAudioLevel,
                glowColor = glowColor,
                surfaceColor = surfaceColor,
                modifier = blurMod
            )
        }

        // Layer 3: Pulse rings — concentric rings emanating from center
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val maxRadius = size.minDimension * 0.6f

            // Ring visibility decreases with progress
            val ringAlpha = (1f - progress.value).coerceIn(0f, 1f) * 0.4f *
                    (0.5f + currentAudioLevel * 0.5f)

            if (ringAlpha > 0.01f) {
                // Ring 1
                val r1 = ringPulse * maxRadius
                drawCircle(
                    color = glowColor.copy(alpha = ringAlpha * (1f - ringPulse)),
                    radius = r1,
                    center = Offset(centerX, centerY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx() + currentAudioLevel * 2.dp.toPx()
                    )
                )

                // Ring 2 (offset timing)
                val r2 = ringPulse2 * maxRadius * 0.8f
                drawCircle(
                    color = glowColor.copy(alpha = ringAlpha * 0.6f * (1f - ringPulse2)),
                    radius = r2,
                    center = Offset(centerX, centerY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.5.dp.toPx() + currentAudioLevel * 1.dp.toPx()
                    )
                )
            }
        }

        // Layer 4: Center orb — breathing glow that fades with progress
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            // Orb visibility: strong at start, fades after 60% progress
            val orbAlpha = when {
                progress.value < 0.6f -> 0.5f + currentAudioLevel * 0.3f
                else -> ((1f - progress.value) / 0.4f).coerceIn(0f, 1f) * 0.5f
            }

            if (orbAlpha > 0.01f) {
                val orbRadius = 32.dp.toPx() * orbBreath *
                        (0.8f + currentAudioLevel * 0.4f)

                // Outer glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = orbAlpha * 0.6f),
                            glowColor.copy(alpha = orbAlpha * 0.2f),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY),
                        radius = orbRadius * 3f
                    ),
                    radius = orbRadius * 3f,
                    center = Offset(centerX, centerY)
                )

                // Inner orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = orbAlpha),
                            glowColor.copy(alpha = orbAlpha * 0.4f),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY),
                        radius = orbRadius
                    ),
                    radius = orbRadius,
                    center = Offset(centerX, centerY)
                )
            }
        }

        // Layer 5: Subtle text — fades in then out
        val textAlpha = when {
            progress.value < 0.08f -> progress.value / 0.08f  // fade in
            progress.value < 0.55f -> 1f                       // visible
            progress.value < 0.75f -> (0.75f - progress.value) / 0.2f  // fade out
            else -> 0f
        }

        if (textAlpha > 0.01f) {
            Text(
                text = "Eve",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = onSurfaceColor.copy(alpha = textAlpha * 0.7f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 80.dp)
            )
        }
    }
}

/**
 * AGSL shader-based dissolve layer (API 33+).
 * Uses fractal noise for organic, non-uniform reveal pattern.
 */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderDissolveLayer(
    progress: Float,
    audioLevel: Float,
    glowColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Compile shader off main thread to avoid stutter on re-entry
    val shader by produceState<RuntimeShader?>(initialValue = null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val src = context.resources.openRawResource(R.raw.ai_awakening_dissolve)
                    .bufferedReader().use { it.readText() }
                RuntimeShader(src)
            } catch (e: Exception) {
                null
            }
        }
    }

    // Elapsed time for subtle shader animation
    val elapsedMs = remember { System.nanoTime() }
    val currentTime by remember {
        derivedStateOf {
            (System.nanoTime() - elapsedMs) / 1_000_000_000f
        }
    }

    // Animate time continuously for shader
    val animatedTime by rememberInfiniteTransition(label = "shaderTime").animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(100_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shaderTime"
    )

    val resolvedShader = shader
    if (resolvedShader != null) {
        Canvas(modifier = modifier) {
            resolvedShader.setFloatUniform("resolution", size.width, size.height)
            resolvedShader.setFloatUniform("progress", progress)
            resolvedShader.setFloatUniform("audioLevel", audioLevel)
            resolvedShader.setFloatUniform("time", animatedTime)
            resolvedShader.setFloatUniform(
                "glowColor",
                glowColor.red, glowColor.green, glowColor.blue, glowColor.alpha
            )
            resolvedShader.setFloatUniform(
                "surfaceColor",
                surfaceColor.red, surfaceColor.green, surfaceColor.blue, surfaceColor.alpha
            )

            drawRect(
                brush = ShaderBrush(resolvedShader),
                size = size
            )
        }
    } else {
        // Shader failed to load, use canvas fallback
        CanvasDissolveLayer(
            progress = progress,
            audioLevel = audioLevel,
            glowColor = glowColor,
            surfaceColor = surfaceColor,
            modifier = modifier
        )
    }
}

/**
 * Canvas-based dissolve fallback for API < 33.
 * Radial gradient from center with animated alpha.
 */
@Composable
private fun CanvasDissolveLayer(
    progress: Float,
    audioLevel: Float,
    glowColor: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val maxRadius = size.maxDimension

        // Eased progress for smooth reveal
        val p = progress * progress * (3f - 2f * progress)
        val revealRadius = p * maxRadius * 0.8f

        // Outer area: still covered
        drawRect(
            color = surfaceColor.copy(alpha = (1f - p).coerceIn(0f, 1f) * 0.95f),
            size = size
        )

        // Reveal hole from center with soft edge
        if (revealRadius > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.7f to Color.Transparent,
                        0.85f to glowColor.copy(alpha = 0.3f * (1f - p) * (0.5f + audioLevel * 0.5f)),
                        1f to surfaceColor.copy(alpha = (1f - p) * 0.9f)
                    ),
                    center = Offset(centerX, centerY),
                    radius = revealRadius
                ),
                radius = revealRadius,
                center = Offset(centerX, centerY)
            )
        }
    }
}
