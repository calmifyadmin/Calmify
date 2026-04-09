package com.lifo.home.presentation.components.hero

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.TodayPulse
import com.lifo.home.domain.model.TrendDirection
import com.lifo.home.presentation.components.common.EmotionIndicator
import com.lifo.home.util.DateFormatters
import com.lifo.home.util.EmotionAwareColors
import com.lifo.util.formatDecimal
import com.lifo.util.model.SentimentLabel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Hero Greeting Card - Main hero section component
 * Material3 Expressive design with emotional context
 *
 * Layout:
 * ┌────────────────────────────────────────────────┐
 * │  Buongiorno, Marco                       ☀️   │
 * │  Come ti senti oggi?                          │
 * │                                               │
 * │  ┌─────────────────────────────────────────┐ │
 * │  │  😊  7.2  ↑ 0.8 rispetto a ieri         │ │
 * │  │       Tendenza positiva questa settimana │ │
 * │  └─────────────────────────────────────────┘ │
 * └────────────────────────────────────────────────┘
 */
@Composable
fun HeroGreetingCard(
    userName: String,
    todayPulse: TodayPulse?,
    modifier: Modifier = Modifier,
    onPulseClick: () -> Unit = {}
) {
    val greeting = DateFormatters.getTimeOfDayGreeting()
    val timeIcon = getTimeIcon()

    // Entrance animation
    val enterTransition = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enterTransition.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enterTransition.value
                translationY = (1f - enterTransition.value) * 30f
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Greeting row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$greeting, $userName",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Come ti senti oggi?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Time-based icon with subtle animation
                Icon(
                    imageVector = timeIcon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Pulse indicator card
            AnimatedVisibility(
                visible = todayPulse != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                todayPulse?.let { pulse ->
                    TodayPulseCard(
                        pulse = pulse,
                        onClick = onPulseClick
                    )
                }
            }

            // Empty state when no pulse data
            if (todayPulse == null) {
                EmptyPulseState()
            }
        }
    }
}

@Composable
private fun TodayPulseCard(
    pulse: TodayPulse,
    onClick: () -> Unit
) {
    val sentimentColor = EmotionAwareColors.getSentimentColor(pulse.dominantEmotion)
    val backgroundBrush = Brush.horizontalGradient(
        colors = listOf(
            sentimentColor.copy(alpha = 0.08f),
            sentimentColor.copy(alpha = 0.15f)
        )
    )

    // Animated score counter
    var animatedScore by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pulse.score) {
        animate(
            initialValue = animatedScore,
            targetValue = pulse.score,
            animationSpec = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            )
        ) { value, _ ->
            animatedScore = value
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundBrush)
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Shape-based emotion indicator (M3 Expressive)
                EmotionIndicator(
                    sentiment = pulse.dominantEmotion,
                    size = 52.dp,
                    animated = true,
                    showGlow = true,
                    glowIntensity = 0.6f
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Score and trend row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Animated score
                        Text(
                            text = formatDecimal(1, animatedScore),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = sentimentColor
                        )

                        // Trend indicator
                        TrendIndicator(
                            direction = pulse.trend,
                            delta = pulse.trendDelta
                        )
                    }

                    // Week summary
                    Text(
                        text = pulse.weekSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// PulsingEmoji removed - replaced with shape-based EmotionIndicator (M3 Expressive)

@Composable
private fun TrendIndicator(
    direction: TrendDirection,
    delta: Float
) {
    val (icon, color, text) = when (direction) {
        TrendDirection.UP -> Triple(
            Icons.Default.TrendingUp,
            EmotionAwareColors.positiveLight,
            "+${formatDecimal(1, delta)}"
        )
        TrendDirection.DOWN -> Triple(
            Icons.Default.TrendingDown,
            EmotionAwareColors.negativeLight,
            formatDecimal(1, delta)
        )
        TrendDirection.STABLE -> Triple(
            Icons.Default.TrendingFlat,
            EmotionAwareColors.neutralLight,
            "stabile"
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun EmptyPulseState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Shape-based icon instead of emoji
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                text = "Inizia a scrivere per vedere i tuoi insight",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun getTimeIcon(): ImageVector {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when {
        hour in 6..17 -> Icons.Default.WbSunny
        else -> Icons.Outlined.NightsStay
    }
}
