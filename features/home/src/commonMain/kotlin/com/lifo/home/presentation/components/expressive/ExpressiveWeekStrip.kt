package com.lifo.home.presentation.components.expressive

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import com.lifo.home.presentation.components.common.GrowthLeafIndicator
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.home.domain.model.DailyInsightData
import com.lifo.home.domain.model.WeeklyGoal
import com.lifo.home.presentation.components.common.animatedCounter
import com.lifo.home.util.EmotionAwareColors
import com.lifo.ui.components.tooltips.InfoTooltip
import com.lifo.ui.i18n.Strings
import kotlinx.datetime.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExpressiveWeekStrip(
    dailyInsights: List<DailyInsightData>,
    weeklyGoal: WeeklyGoal?,
    streakDays: Int = 0,
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

    // Breathing for today indicator
    val infiniteTransition = rememberInfiniteTransition(label = "todayBreathing")
    val todayScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "todayScale"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: streak like reference image
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GrowthLeafIndicator(
                        streakDays = streakDays,
                        size = 32.dp,
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(Strings.Screen.Home.weekGrowthLabel),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                ),
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                            InfoTooltip(
                                title = stringResource(Strings.Screen.Home.streakTooltipTitle),
                                description = stringResource(Strings.Screen.Home.weekStreakTooltipBody),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "$streakDays",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-1).sp,
                                ),
                                color = colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(if (streakDays == 1) Strings.Screen.Home.weekDaySingular else Strings.Screen.Home.weekDayPlural),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp,
                                ),
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(bottom = 5.dp),
                            )
                        }
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }

            // Day pills row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (dayIndex in 1..7) {
                    val insight = insightsByDay[dayIndex]
                    val hasEntries = insight != null && insight.diaryCount > 0
                    val isToday = dayIndex == todayDow
                    val isFuture = dayIndex > todayDow

                    val sentimentColor = if (hasEntries && insight != null)
                        EmotionAwareColors.getSentimentColor(insight.dominantEmotion)
                    else null

                    DayPill(
                        label = dayLabels[dayIndex - 1],
                        hasEntries = hasEntries,
                        isToday = isToday,
                        isFuture = isFuture,
                        sentimentColor = sentimentColor,
                        primaryColor = primary,
                        todayScale = if (isToday) todayScale else 1f
                    )
                }
            }

            // Goal progress
            weeklyGoal?.let { goal ->
                val animatedProgress = animatedCounter(
                    targetValue = goal.progress.coerceIn(0f, 1f),
                    durationMs = 1000
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Strings.Screen.Home.weekGoalProgress, goal.currentEntries, goal.targetEntries),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = primary,
                        trackColor = colorScheme.surfaceContainerHighest,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}

// ==================== DAY PILL ====================

@Composable
private fun DayPill(
    label: String,
    hasEntries: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    sentimentColor: androidx.compose.ui.graphics.Color?,
    primaryColor: androidx.compose.ui.graphics.Color,
    todayScale: Float
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.graphicsLayer {
            scaleX = todayScale
            scaleY = todayScale
        }
    ) {
        // Day label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = when {
                isToday -> primaryColor
                isFuture -> colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                else -> colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }
        )

        // Indicator
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                hasEntries -> {
                    // Filled circle with check
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(primaryColor),
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
                    // Today: rounded square with border
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(primaryColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                        )
                    }
                }
                isFuture -> {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.3f))
                    )
                }
                else -> {
                    // Past without entry
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    )
                }
            }
        }

        // Sentiment dot
        if (sentimentColor != null) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(sentimentColor)
            )
        } else {
            Spacer(modifier = Modifier.height(5.dp))
        }
    }
}
