package com.lifo.chat.presentation.components

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifo.chat.domain.model.AIEmotion
import com.lifo.chat.domain.model.ConnectionStatus
import com.lifo.chat.domain.model.PTTState

/**
 * Live Chat Visualizer - Main orchestrator for all visual components
 * 
 * This component manages the visual layout and animations for:
 * - Central liquid globe responding to AI emotions and voice
 * - Audio level indicators for user input
 * - State-based text and visual feedback
 * - Coordinated animations between components
 */
@Composable
fun LiveChatVisualizer(
    connectionStatus: ConnectionStatus,
    aiEmotion: AIEmotion,
    audioLevel: Float, // AI audio level 0.0 to 1.0
    userAudioLevel: Float, // User audio level 0.0 to 1.0
    pushToTalkState: PTTState,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    useGeminiStyle: Boolean = true // Switch between Gemini-style and original liquid globe
) {
    // Main animation coordination
    val isConnected = connectionStatus == ConnectionStatus.Connected
    val isAiActive = audioLevel > 0.1f || aiEmotion == AIEmotion.Speaking
    
    // Visibility animations
    val globeVisibility by animateFloatAsState(
        targetValue = if (isConnected) 1f else 0.3f,
        animationSpec = tween(durationMillis = 600),
        label = "globe_visibility"
    )
    
    // AI activity pulse
    val aiActivityPulse by rememberInfiniteTransition(label = "ai_activity").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ai_pulse"
    )
    
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Main visualization area - conditional rendering based on style preference
        if (useGeminiStyle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Gemini-style liquid wave background that fills available space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize() // Taller to create immersive background feel
                    .graphicsLayer {
                        alpha = globeVisibility
                        scaleX = if (isAiActive) aiActivityPulse else 1f
                        scaleY = if (isAiActive) aiActivityPulse else 1f
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background liquid wave that fills the entire container
                GeminiLiquidVisualizer(
                    isSpeaking = isAiActive,
                    modifier = Modifier.fillMaxSize(),
                    backgroundColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f)
                )
                
                // User audio level indicator overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = isRecording && pushToTalkState == PTTState.Listening,
                    enter = scaleIn(animationSpec = spring()) + fadeIn(),
                    exit = scaleOut(animationSpec = spring()) + fadeOut()
                ) {
                    UserAudioLevelOverlay(
                        audioLevel = userAudioLevel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // Original liquid globe style - more minimal when used on liquid background
            Box(
                modifier = Modifier
                    .size(200.dp) // Smaller size to work better over liquid background
                    .graphicsLayer {
                        alpha = globeVisibility * 0.8f // More transparent
                        scaleX = if (isAiActive) aiActivityPulse else 1f
                        scaleY = if (isAiActive) aiActivityPulse else 1f
                    },
                contentAlignment = Alignment.Center
            ) {
                // Simplified liquid globe without background glow (since liquid background provides it)
                LiquidGlobe(
                    emotion = aiEmotion,
                    audioLevel = audioLevel,
                    size = 100.dp, // Smaller size
                    modifier = Modifier.size(100.dp)
                )
                
                // User audio level indicator positioned around the globe
                androidx.compose.animation.AnimatedVisibility(
                    visible = isRecording && pushToTalkState == PTTState.Listening,
                    enter = scaleIn(animationSpec = spring()) + fadeIn(),
                    exit = scaleOut(animationSpec = spring()) + fadeOut()
                ) {
                    UserAudioLevelRings(
                        audioLevel = userAudioLevel,
                        globeSize = 100.dp,
                        modifier = Modifier.size(160.dp) // Proportionally smaller
                    )
                }
            }
        }
        
        // Status information
        StatusTextSection(
            connectionStatus = connectionStatus,
            aiEmotion = aiEmotion,
            pushToTalkState = pushToTalkState,
            isRecording = isRecording,
            audioLevel = audioLevel
        )
    }
}

/**
 * Background ambient glow that responds to AI state
 */
@Composable
private fun BackgroundAmbientGlow(
    emotion: AIEmotion,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val glowIntensity by animateFloatAsState(
        targetValue = 0.3f + (audioLevel * 0.4f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glow_intensity"
    )
    
    // Large liquid globe for ambient glow
    LiquidGlobe(
        emotion = emotion,
        audioLevel = audioLevel * 0.5f, // Reduced responsivity for background
        size = 180.dp,
        modifier = modifier.graphicsLayer {
            alpha = glowIntensity * 0.4f
            scaleX = 1.2f
            scaleY = 1.2f
        }
    )
}

/**
 * User audio level visualization as rings around the main globe
 */
@Composable
private fun UserAudioLevelRings(
    audioLevel: Float,
    globeSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val globeRadiusInPx = with(density) { globeSize.toPx() / 2f }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Multiple rings at different distances from the globe
        for (i in 1..3) {
            val ringDistance = globeRadiusInPx + (i * with(density) { 20.dp.toPx() })
            val ringSize = with(density) { (ringDistance * 2f).toDp() }
            
            AudioLevelIndicator(
                audioLevel = audioLevel,
                isRecording = true,
                size = ringSize,
                ringCount = 1,
                modifier = Modifier
                    .size(ringSize)
                    .graphicsLayer {
                        alpha = (1f - (i * 0.2f)).coerceAtLeast(0.2f)
                    }
            )
        }
    }
}

/**
 * User audio level overlay for Gemini-style visualizer
 */
@Composable
private fun UserAudioLevelOverlay(
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val pulseAnimation by rememberInfiniteTransition(label = "user_pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (1000 - (audioLevel * 500)).toInt().coerceIn(200, 1000),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Subtle overlay rings that respond to user voice
        for (i in 1..2) {
            val alpha = (audioLevel * 0.3f * (1f - i * 0.1f)).coerceIn(0f, 0.3f)
            
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pulseAnimation * (1f + i * 0.1f)
                        scaleY = pulseAnimation * (1f + i * 0.1f)
                    }
            ) {
                val center = this.center
                val radius = size.minDimension * 0.3f * (1f + audioLevel * 0.5f)
                
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White,
                    radius = radius,
                    center = center,
                    alpha = alpha,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

/**
 * Status text section with animated transitions
 */
@Composable
private fun StatusTextSection(
    connectionStatus: ConnectionStatus,
    aiEmotion: AIEmotion,
    pushToTalkState: PTTState,
    isRecording: Boolean,
    audioLevel: Float
) {
    // Status text with smooth transitions
    val statusText = remember(connectionStatus, aiEmotion, pushToTalkState, isRecording) {
        when {
            connectionStatus != ConnectionStatus.Connected -> "Connecting to AI..."
            isRecording -> "Listening..."
            pushToTalkState == PTTState.Processing -> "AI is processing..."
            audioLevel > 0.1f -> "AI is speaking"
            else -> when (aiEmotion) {
                AIEmotion.Neutral -> "Ready to chat"
                AIEmotion.Happy -> "AI is in a good mood"
                AIEmotion.Thinking -> "AI is thinking..."
                AIEmotion.Speaking -> "AI is responding"
            }
        }
    }
    
    // Status text with smooth transitions - enhanced visibility on liquid background
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White, // White text for better contrast on liquid background
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .background(
                Color.Black.copy(alpha = 0.3f),
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
    
    // Additional context indicators
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Connection indicator
        androidx.compose.animation.AnimatedVisibility(
            visible = connectionStatus == ConnectionStatus.Connected,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            ConnectionIndicatorDot(isConnected = true)
        }
        
        // Recording indicator
        androidx.compose.animation.AnimatedVisibility(
            visible = isRecording,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            LinearAudioLevelIndicator(
                audioLevel = audioLevel,
                isRecording = isRecording,
                modifier = Modifier.width(80.dp)
            )
        }
    }
}

/**
 * Small connection status indicator
 */
@Composable
private fun ConnectionIndicatorDot(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val pulseAnimation by rememberInfiniteTransition(label = "connection_pulse").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = modifier
            .size(8.dp)
            .graphicsLayer {
                scaleX = if (isConnected) pulseAnimation else 0.7f
                scaleY = if (isConnected) pulseAnimation else 0.7f
                alpha = if (isConnected) 1f else 0.3f
            }
    ) {
        LiquidGlobe(
            emotion = AIEmotion.Neutral,
            audioLevel = 0f,
            size = 8.dp
        )
    }
}

/**
 * Compact version for smaller screens or embedded usage
 */
@Composable
fun CompactLiveChatVisualizer(
    connectionStatus: ConnectionStatus,
    aiEmotion: AIEmotion,
    audioLevel: Float,
    userAudioLevel: Float,
    pushToTalkState: PTTState,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Smaller globe
        CompactLiquidGlobe(
            emotion = aiEmotion,
            audioLevel = audioLevel
        )
        
        // Compact audio indicator
        if (isRecording) {
            CompactAudioLevelIndicator(
                audioLevel = userAudioLevel,
                isRecording = isRecording
            )
        }
    }
}