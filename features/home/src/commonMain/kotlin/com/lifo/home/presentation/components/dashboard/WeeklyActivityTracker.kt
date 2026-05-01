package com.lifo.home.presentation.components.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.DailyInsightData
import com.lifo.home.domain.model.WeeklyGoal
import com.lifo.home.presentation.components.common.animatedCounter
import com.lifo.home.util.EmotionAwareColors
import com.lifo.ui.i18n.Strings
import kotlinx.datetime.*
import org.jetbrains.compose.resources.stringResource


@Composable
internal fun WeeklyActivityTracker(
    dailyInsights: List<DailyInsightData>,
    weeklyGoal: WeeklyGoal?,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val primary = colorScheme.primary
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    val insightsByDay = remember(dailyInsights) {
        val map = mutableMapOf<Int, DailyInsightData>()
        dailyInsights.forEach { insight ->
            val dayOfWeek = insight.date.toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.value
            map[dayOfWeek] = insight
        }
        map
    }

    val dayLabels = listOf(
        stringResource(Strings.Screen.Home.dayInitialMonday),
        stringResource(Strings.Screen.Home.dayInitialTuesday),
        stringResource(Strings.Screen.Home.dayInitialWednesday),
        stringResource(Strings.Screen.Home.dayInitialThursday),
        stringResource(Strings.Screen.Home.dayInitialFriday),
        stringResource(Strings.Screen.Home.dayInitialSaturday),
        stringResource(Strings.Screen.Home.dayInitialSunday),
    )
    val todayDow = today.dayOfWeek.value

    val activeDays = insightsByDay.count { it.value.diaryCount > 0 }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Strings.Screen.Home.weekThis),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = stringResource(Strings.Screen.Home.weekProgress, activeDays),
                    style = MaterialTheme.typography.labelMedium,
                    color = primary
                )
            }

            // Day indicators row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (dayIndex in 1..7) {
                    val insight = insightsByDay[dayIndex]
                    val hasEntries = insight != null && insight.diaryCount > 0
                    val isToday = dayIndex == todayDow
                    val isFuture = dayIndex > todayDow

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = dayLabels[dayIndex - 1],
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) primary
                            else colorScheme.onSurfaceVariant.copy(
                                alpha = if (isFuture) 0.3f else 0.7f
                            )
                        )

                        Box(
                            modifier = Modifier.size(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                hasEntries -> {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = colorScheme.surface
                                        )
                                    }
                                }
                                isToday -> {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(primary)
                                        )
                                    }
                                }
                                isFuture -> {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                            )
                                    )
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                            )
                                    )
                                }
                            }
                        }

                        if (hasEntries && insight != null) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        EmotionAwareColors.getSentimentColor(insight.dominantEmotion)
                                    )
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            // Weekly goal progress bar
            weeklyGoal?.let { goal ->
                val animatedProgress = animatedCounter(
                    targetValue = goal.progress.coerceIn(0f, 1f),
                    durationMs = 1000
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Obiettivo: ${goal.currentEntries}/${goal.targetEntries}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = primary,
                        trackColor = colorScheme.surfaceContainerHighest
                    )
                }
            }
        }
    }
}
