package com.lifo.home.presentation.components.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.home.domain.model.TopicFrequency
import com.lifo.home.domain.model.TopicTrend
import com.lifo.ui.theme.Sage
import com.lifo.ui.theme.SageSoft
import com.lifo.ui.theme.SageMedium

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopicsInsightCard(
    topics: List<TopicFrequency>,
    emergingTopic: TopicTrend?,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Temi ricorrenti",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                emergingTopic?.let { trend ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.primary
                        )
                        Text(
                            text = trend.topic,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary
                        )
                    }
                }
            }

            // Topics as FlowRow chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topics.take(8).forEach { topic ->
                    val chipSize = when {
                        topic.frequency >= 10 -> MaterialTheme.typography.labelLarge
                        topic.frequency >= 5 -> MaterialTheme.typography.labelMedium
                        else -> MaterialTheme.typography.labelSmall
                    }
                    val chipFontSize = when {
                        topic.frequency >= 10 -> 14.sp
                        topic.frequency >= 5 -> 12.sp
                        else -> 11.sp
                    }

                    // Color by frequency: more frequent = brighter sage tone
                    val chipColor = when {
                        topic.frequency >= 10 -> Sage
                        topic.frequency >= 5 -> SageSoft
                        else -> SageMedium
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = chipColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = topic.topic,
                                style = chipSize.copy(fontSize = chipFontSize),
                                color = chipColor
                            )
                            if (topic.isEmerging) {
                                Text(
                                    text = "\u2197",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
