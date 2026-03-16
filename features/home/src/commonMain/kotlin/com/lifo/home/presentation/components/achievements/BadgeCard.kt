package com.lifo.home.presentation.components.achievements

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.Badge
import com.lifo.home.domain.model.BadgeRarity
import com.lifo.home.util.EmotionAwareColors

/**
 * Badge Card - Display for earned badges with rarity glow
 */
@Composable
fun BadgeCard(
    badge: Badge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rarityColor = EmotionAwareColors.AchievementColors.getRarityColor(badge.rarity)

    // Glow animation for rare+ badges
    val glowAlpha = if (badge.rarity != BadgeRarity.COMMON) {
        val infiniteTransition = rememberInfiniteTransition(label = "glow")
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        ).value
    } else 0f

    // New badge pulse
    val newBadgeScale = if (badge.isNew) {
        val infiniteTransition = rememberInfiniteTransition(label = "new")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "newScale"
        ).value
    } else 1f

    Box(modifier = modifier) {
        // Glow background
        if (badge.rarity != BadgeRarity.COMMON && badge.earnedAt != null) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = glowAlpha }
            ) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            rarityColor.copy(alpha = 0.4f),
                            rarityColor.copy(alpha = 0.1f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2, size.height / 2),
                        radius = size.maxDimension / 1.5f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = newBadgeScale
                    scaleY = newBadgeScale
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (badge.earnedAt != null) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ),
            border = if (badge.earnedAt != null && badge.rarity != BadgeRarity.COMMON) {
                CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            rarityColor.copy(alpha = 0.7f),
                            rarityColor.copy(alpha = 0.3f)
                        )
                    )
                )
            } else null,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge icon
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge.icon,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (badge.earnedAt != null) 1f else 0.3f
                        }
                    )

                    // Lock overlay if not earned
                    if (badge.earnedAt == null) {
                        Text(
                            text = "🔒",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Badge info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = badge.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (badge.earnedAt != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // New indicator
                        if (badge.isNew) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "NEW",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = badge.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Progress bar if not earned
                    if (badge.earnedAt == null && badge.progress > 0f) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ProgressIndicator(progress = badge.progress)
                    }
                }

                // Rarity indicator
                RarityBadge(rarity = badge.rarity)
            }
        }
    }
}

@Composable
private fun ProgressIndicator(progress: Float) {
    var animatedProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(progress) {
        animate(
            initialValue = 0f,
            targetValue = progress,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        ) { value: Float, _: Float ->
            animatedProgress = value
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RarityBadge(rarity: BadgeRarity) {
    val rarityColor = EmotionAwareColors.AchievementColors.getRarityColor(rarity)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = rarityColor.copy(alpha = 0.15f)
    ) {
        Text(
            text = rarity.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = rarityColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Compact badge display
 */
@Composable
fun CompactBadge(
    badge: Badge,
    modifier: Modifier = Modifier
) {
    val rarityColor = EmotionAwareColors.AchievementColors.getRarityColor(badge.rarity)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (badge.earnedAt != null) {
            rarityColor.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge.icon,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.graphicsLayer {
                    alpha = if (badge.earnedAt != null) 1f else 0.3f
                }
            )

            if (badge.earnedAt == null) {
                Text(
                    text = "🔒",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

/**
 * Badge grid for achievements screen
 */
@Composable
fun BadgeGrid(
    badges: List<Badge>,
    onBadgeClick: (Badge) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        badges.chunked(columns).forEach { rowBadges ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowBadges.forEach { badge ->
                    CompactBadge(
                        badge = badge,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                }
                // Fill remaining space
                repeat(columns - rowBadges.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
