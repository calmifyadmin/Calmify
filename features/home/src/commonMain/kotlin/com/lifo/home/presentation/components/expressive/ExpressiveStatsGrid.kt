package com.lifo.home.presentation.components.expressive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import com.lifo.home.presentation.components.common.GrowthLeafIndicator
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.home.presentation.components.common.animatedIntCounter
import com.lifo.home.util.EmotionAwareColors

@Composable
internal fun ExpressiveStatsGrid(
    streakDays: Int,
    monthlyEntries: Int,
    goalProgress: Float,
    badgesEarned: Int,
    streakActive: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val streakColor = EmotionAwareColors.AchievementColors.getStreakColor(streakDays)
    val goalColor = if (goalProgress >= 1f) EmotionAwareColors.positiveLight else colorScheme.tertiary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Hero row: Streak + Entries — like SleepSummaryCard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StreakStat(
                    value = streakDays,
                    accentColor = streakColor,
                )

                // Vertical divider
                Surface(
                    modifier = Modifier
                        .width(1.dp)
                        .height(64.dp),
                    color = colorScheme.onSurface.copy(alpha = 0.08f),
                ) {}

                EntriesStat(
                    value = monthlyEntries,
                )
            }

            // Compact row: Goal + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompactStatPill(
                    icon = Icons.Default.TrackChanges,
                    value = (goalProgress * 100).toInt(),
                    label = "goal",
                    suffix = "%",
                    accentColor = goalColor,
                    modifier = Modifier.weight(1f),
                )
                CompactStatPill(
                    icon = Icons.Default.EmojiEvents,
                    value = badgesEarned,
                    label = "badges",
                    accentColor = EmotionAwareColors.AchievementColors.legendary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ==================== STREAK STAT (icon + notification badge) ====================

@Composable
private fun StreakStat(
    value: Int,
    accentColor: Color,
) {
    val animatedValue = animatedIntCounter(targetValue = value, durationMs = 1000)

    Box {
        GrowthLeafIndicator(
            streakDays = value,
            size = 40.dp,
        )
        // Notification-style badge in top-right
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = accentColor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-4).dp),
        ) {
            Text(
                text = "$animatedValue",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                ),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

// ==================== ENTRIES STAT (number only, no icon) ====================

@Composable
private fun EntriesStat(
    value: Int,
) {
    val animatedValue = animatedIntCounter(targetValue = value, durationMs = 1000)
    val colorScheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$animatedValue",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.5).sp,
            ),
            color = colorScheme.onSurface,
        )
        Text(
            text = "diari questo mese",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// ==================== COMPACT STAT PILL ====================

@Composable
private fun CompactStatPill(
    icon: ImageVector,
    value: Int,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    suffix: String = "",
) {
    val animatedValue = animatedIntCounter(targetValue = value, durationMs = 800)
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accentColor,
                    )
                }
            }
            Column {
                Text(
                    text = "$animatedValue$suffix",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = colorScheme.onSurface,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}
