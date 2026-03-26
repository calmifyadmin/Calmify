package com.lifo.home.presentation.components.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.presentation.components.common.animatedIntCounter
import com.lifo.home.util.EmotionAwareColors

@Composable
internal fun StatsMetricRow(
    streakDays: Int,
    monthlyEntries: Int,
    goalProgress: Float,
    badgesEarned: Int,
    streakActive: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatMetricCell(
            icon = Icons.Default.Eco,
            value = streakDays,
            label = "crescita",
            accentColor = EmotionAwareColors.AchievementColors.getStreakColor(streakDays),
            modifier = Modifier.weight(1f)
        )
        StatMetricCell(
            icon = Icons.Default.EditNote,
            value = monthlyEntries,
            label = "entries",
            accentColor = colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatMetricCell(
            icon = Icons.Default.TrackChanges,
            value = (goalProgress * 100).toInt(),
            label = "goal",
            suffix = "%",
            accentColor = if (goalProgress >= 1f) EmotionAwareColors.positiveLight else colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        StatMetricCell(
            icon = Icons.Default.EmojiEvents,
            value = badgesEarned,
            label = "badges",
            accentColor = EmotionAwareColors.AchievementColors.legendary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatMetricCell(
    icon: ImageVector,
    value: Int,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    val animatedValue = animatedIntCounter(targetValue = value, durationMs = 800)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = accentColor
            )
            Text(
                text = "$animatedValue$suffix",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
