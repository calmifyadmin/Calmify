package com.lifo.home.presentation.components.achievements

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.StreakData
import com.lifo.home.presentation.components.common.StreakFlameIndicator
import com.lifo.home.util.EmotionAwareColors

/**
 * Streak Counter - Animated streak display with shape-based fire indicator
 * Uses M3 Expressive shapes instead of emojis
 */
@Composable
fun StreakCounter(
    streak: StreakData,
    modifier: Modifier = Modifier
) {
    val streakColor = EmotionAwareColors.AchievementColors.getStreakColor(streak.currentStreak)

    // Counter animation
    var animatedCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(streak.currentStreak) {
        if (streak.currentStreak > 0) {
            val step = maxOf(1, streak.currentStreak / 20)
            for (i in 0..streak.currentStreak step step) {
                animatedCount = minOf(i, streak.currentStreak)
                kotlinx.coroutines.delay(30)
            }
            animatedCount = streak.currentStreak
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Shape-based flame indicator (M3 Expressive)
        StreakFlameIndicator(
            streakDays = streak.currentStreak,
            size = 32.dp,
            isActive = streak.currentStreak > 0
        )

        // Count
        Text(
            text = animatedCount.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = streakColor
        )

        // Label
        Text(
            text = "di streak",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Streak at risk indicator with Icon
        if (streak.streakAtRisk && !streak.isActiveToday) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = EmotionAwareColors.negativeLight.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = EmotionAwareColors.negativeLight
                    )
                    Text(
                        text = "A rischio",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmotionAwareColors.negativeLight
                    )
                }
            }
        }
    }
}

/**
 * Large streak display for hero section
 * Uses M3 Expressive shapes instead of emojis
 */
@Composable
fun LargeStreakCounter(
    streak: StreakData,
    modifier: Modifier = Modifier
) {
    val streakColor = EmotionAwareColors.AchievementColors.getStreakColor(streak.currentStreak)

    // Flame particles animation
    val infiniteTransition = rememberInfiniteTransition(label = "flames")
    val flameOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flameOffset"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Flame background effect
        if (streak.currentStreak >= 7) {
            Canvas(modifier = Modifier.size(100.dp)) {
                val center = Offset(size.width / 2, size.height / 2)

                // Outer glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            streakColor.copy(alpha = 0.3f),
                            streakColor.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension / 2 + flameOffset
                    ),
                    center = center,
                    radius = size.minDimension / 2 + flameOffset
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Shape-based flame indicators based on streak level
            if (streak.currentStreak > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Show multiple flames for higher streaks
                    val flameCount = when {
                        streak.currentStreak >= 30 -> 3
                        streak.currentStreak >= 14 -> 2
                        else -> 1
                    }
                    repeat(flameCount) { index ->
                        StreakFlameIndicator(
                            streakDays = streak.currentStreak,
                            size = 48.dp,
                            isActive = true
                        )
                    }
                }
            } else {
                // Inactive state - sleep icon
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Count with description
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${streak.currentStreak}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = streakColor
                )
                Text(
                    text = if (streak.currentStreak == 1) "giorno di streak" else "giorni di streak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Longest streak comparison
            if (streak.longestStreak > streak.currentStreak) {
                Text(
                    text = "Record: ${streak.longestStreak} giorni",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (streak.currentStreak > 0 && streak.currentStreak == streak.longestStreak) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmotionAwareColors.AchievementColors.legendary.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = EmotionAwareColors.AchievementColors.legendary
                        )
                        Text(
                            text = "Nuovo record!",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmotionAwareColors.AchievementColors.legendary
                        )
                    }
                }
            }

            // Today's status with Icons
            if (streak.isActiveToday) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = EmotionAwareColors.positiveLight
                    )
                    Text(
                        text = "Hai scritto oggi",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmotionAwareColors.positiveLight
                    )
                }
            } else if (streak.streakAtRisk) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = EmotionAwareColors.negativeLight
                    )
                    Text(
                        text = "Scrivi oggi per mantenere lo streak!",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmotionAwareColors.negativeLight
                    )
                }
            }
        }
    }
}

/**
 * Mini streak badge for compact displays
 * Uses M3 Expressive shapes instead of emojis
 */
@Composable
fun MiniStreakBadge(
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    val streakColor = EmotionAwareColors.AchievementColors.getStreakColor(streakDays)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = streakColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shape-based mini flame
            StreakFlameIndicator(
                streakDays = streakDays,
                size = 18.dp,
                isActive = streakDays > 0
            )
            Text(
                text = streakDays.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = streakColor
            )
        }
    }
}
