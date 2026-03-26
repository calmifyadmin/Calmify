package com.lifo.home.presentation.components.achievements

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.Badge
import com.lifo.home.domain.model.MonthlyStats
import com.lifo.home.domain.model.StreakData
import com.lifo.home.domain.model.WeeklyGoal
import com.lifo.home.presentation.components.common.GrowthLeafIndicator
import com.lifo.home.presentation.components.common.BadgeShapeIndicator

/**
 * Achievements Row - Gamification section for user engagement
 *
 * Layout:
 * ┌─────────────────────────────────────────────────┐
 * │  I tuoi progressi                               │
 * │                                                 │
 * │  🔥 7 giorni      ✍️ 23 entries    🎯 80%      │
 * │     di streak        questo mese      goal     │
 * │                                                 │
 * │  ┌─────────────────────────────────────────┐   │
 * │  │ 🏆 Nuovo badge: "Mindful Writer"        │   │
 * │  │    Hai scritto per 7 giorni consecutivi │   │
 * │  └─────────────────────────────────────────┘   │
 * └─────────────────────────────────────────────────┘
 */
@Composable
fun AchievementsRow(
    streak: StreakData,
    monthlyStats: MonthlyStats,
    weeklyGoal: WeeklyGoal,
    latestBadge: Badge?,
    onViewAllBadges: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "I tuoi progressi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(onClick = onViewAllBadges) {
                    Text(
                        text = "Tutti i badge →",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Stats row with shape-based icons (M3 Expressive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Streak
                StreakCounter(
                    streak = streak,
                    modifier = Modifier.weight(1f)
                )

                // Monthly entries with Icon
                StatItemWithIcon(
                    icon = Icons.Default.Edit,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    value = monthlyStats.entriesThisMonth.toString(),
                    label = "questo mese",
                    modifier = Modifier.weight(1f)
                )

                // Weekly goal
                GoalProgress(
                    goal = weeklyGoal,
                    modifier = Modifier.weight(1f)
                )
            }

            // Latest badge (if available)
            latestBadge?.let { badge ->
                if (badge.isNew) {
                    NewBadgeCard(badge = badge)
                }
            }
        }
    }
}

/**
 * Shape-based stat item with Icon (M3 Expressive)
 */
@Composable
private fun StatItemWithIcon(
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = iconColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GoalProgress(
    goal: WeeklyGoal,
    modifier: Modifier = Modifier
) {
    // Animated progress
    var animatedProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(goal.progress) {
        animate(
            initialValue = 0f,
            targetValue = goal.progress,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        ) { value: Float, _: Float ->
            animatedProgress = value
        }
    }

    val goalColor = if (goal.isAchieved) {
        com.lifo.home.util.EmotionAwareColors.positiveLight
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Shape-based goal icon (M3 Expressive)
        Icon(
            imageVector = if (goal.isAchieved) Icons.Default.CheckCircle else Icons.Default.TrackChanges,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = goalColor
        )
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (goal.isAchieved) {
                com.lifo.home.util.EmotionAwareColors.positiveLight
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        Text(
            text = "goal settimanale",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewBadgeCard(badge: Badge) {
    // Glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val badgeColor = com.lifo.home.util.EmotionAwareColors.AchievementColors.getRarityColor(badge.rarity)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = badgeColor.copy(alpha = glowAlpha * 0.3f),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(badgeColor.copy(alpha = 0.5f))
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shape-based badge indicator (M3 Expressive)
            BadgeShapeIndicator(
                size = 48.dp,
                color = badgeColor,
                isNew = true
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // New badge indicator with Icon instead of emoji
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = badgeColor
                    )
                    Text(
                        text = "Nuovo badge: \"${badge.name}\"",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compact achievements for smaller spaces
 * Uses shape-based icons instead of emojis (M3 Expressive)
 */
@Composable
fun CompactAchievementsRow(
    streak: StreakData,
    monthlyEntries: Int,
    goalProgress: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Streak with growth leaf
        CompactStatBadgeWithShape(
            icon = null,
            value = streak.currentStreak.toString(),
            showLeaf = true,
            streakDays = streak.currentStreak
        )
        // Monthly entries
        CompactStatBadgeWithIcon(
            icon = Icons.Default.Edit,
            value = monthlyEntries.toString(),
            iconColor = MaterialTheme.colorScheme.secondary
        )
        // Goal progress
        CompactStatBadgeWithIcon(
            icon = Icons.Default.TrackChanges,
            value = "${(goalProgress * 100).toInt()}%",
            iconColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CompactStatBadgeWithIcon(
    icon: ImageVector,
    value: String,
    iconColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = iconColor
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompactStatBadgeWithShape(
    icon: ImageVector?,
    value: String,
    showLeaf: Boolean = false,
    streakDays: Int = 0
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showLeaf) {
                GrowthLeafIndicator(
                    streakDays = streakDays,
                    size = 18.dp
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
