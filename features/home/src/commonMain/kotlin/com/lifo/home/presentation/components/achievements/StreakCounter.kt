package com.lifo.home.presentation.components.achievements

import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.StreakData
import com.lifo.home.presentation.components.common.GrowthLeafIndicator
import com.lifo.home.util.EmotionAwareColors

/**
 * Streak Counter — Growth leaf that reflects writing consistency
 */
@Composable
fun StreakCounter(
    streak: StreakData,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary

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
        GrowthLeafIndicator(
            streakDays = streak.currentStreak,
            size = 32.dp
        )

        Text(
            text = animatedCount.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = primary
        )

        Text(
            text = "di crescita",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Streak at risk indicator
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
 * Large streak display for hero section — growing leaf
 */
@Composable
fun LargeStreakCounter(
    streak: StreakData,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (streak.currentStreak > 0) {
                GrowthLeafIndicator(
                    streakDays = streak.currentStreak,
                    size = 56.dp
                )
            } else {
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
                    color = primary
                )
                Text(
                    text = if (streak.currentStreak == 1) "giorno di crescita" else "giorni di crescita",
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
                    color = primary.copy(alpha = 0.15f)
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
                            tint = primary
                        )
                        Text(
                            text = "Nuovo record!",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primary
                        )
                    }
                }
            }

            // Today's status
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
                        text = "Scrivi oggi per continuare a crescere!",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmotionAwareColors.negativeLight
                    )
                }
            }
        }
    }
}

/**
 * Mini streak badge for compact displays — leaf + count
 */
@Composable
fun MiniStreakBadge(
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = primary.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GrowthLeafIndicator(
                streakDays = streakDays,
                size = 18.dp
            )
            Text(
                text = streakDays.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = primary
            )
        }
    }
}
