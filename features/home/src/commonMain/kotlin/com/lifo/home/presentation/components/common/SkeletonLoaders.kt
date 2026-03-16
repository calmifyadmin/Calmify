package com.lifo.home.presentation.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeleton Loaders for Home Screen
 * Material3 Expressive shimmer effects for loading states
 */

// ==================== SHIMMER EFFECT ====================

/**
 * Create shimmer brush for skeleton animation
 */
@Composable
fun shimmerBrush(showShimmer: Boolean = true): Brush {
    if (!showShimmer) {
        return Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnimation - 200f, translateAnimation - 200f),
        end = Offset(translateAnimation, translateAnimation)
    )
}

// ==================== BASE SKELETON SHAPES ====================

/**
 * Basic skeleton box with shimmer
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    width: Dp? = null,
    cornerRadius: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .then(
                if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
            )
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(shimmerBrush())
    )
}

/**
 * Circular skeleton (for avatars, icons)
 */
@Composable
fun SkeletonCircle(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(shimmerBrush())
    )
}

// ==================== HERO SECTION SKELETONS ====================

/**
 * Skeleton for Hero Greeting Card
 */
@Composable
fun HeroGreetingCardSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Greeting line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(
                    width = 180.dp,
                    height = 28.dp,
                    cornerRadius = 8.dp
                )
                SkeletonCircle(size = 32.dp)
            }

            // Subtitle
            SkeletonBox(
                width = 140.dp,
                height = 20.dp,
                cornerRadius = 6.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Pulse card inner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Emoji placeholder
                    SkeletonCircle(size = 48.dp)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Score
                        SkeletonBox(
                            width = 60.dp,
                            height = 36.dp,
                            cornerRadius = 8.dp
                        )
                        // Trend text
                        SkeletonBox(
                            width = 120.dp,
                            height = 16.dp,
                            cornerRadius = 4.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Skeleton for Quick Actions Row
 */
@Composable
fun QuickActionsRowSkeleton(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonCircle(size = 32.dp)
                    SkeletonBox(
                        width = 60.dp,
                        height = 14.dp,
                        cornerRadius = 4.dp
                    )
                }
            }
        }
    }
}

// ==================== CHART SKELETONS ====================

/**
 * Skeleton for Daily Insights Chart
 */
@Composable
fun DailyInsightsChartSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(
                    width = 120.dp,
                    height = 24.dp,
                    cornerRadius = 6.dp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        SkeletonCircle(size = 28.dp)
                    }
                }
            }

            // Main stats
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonBox(
                    width = 80.dp,
                    height = 56.dp,
                    cornerRadius = 8.dp
                )
                SkeletonBox(
                    width = 100.dp,
                    height = 20.dp,
                    cornerRadius = 4.dp
                )
            }

            // Chart area with pillars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val heights = listOf(120, 80, 140, 60, 100, 90, 70)
                heights.forEach { height ->
                    SkeletonBox(
                        width = 40.dp,
                        height = height.dp,
                        cornerRadius = 20.dp
                    )
                }
            }

            // Day labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(7) {
                    SkeletonBox(
                        width = 20.dp,
                        height = 14.dp,
                        cornerRadius = 4.dp
                    )
                }
            }
        }
    }
}

// ==================== INSIGHT CARD SKELETONS ====================

/**
 * Skeleton for Mood Distribution Card
 */
@Composable
fun MoodDistributionCardSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SkeletonBox(width = 150.dp, height = 20.dp)
                SkeletonBox(width = 80.dp, height = 16.dp)
            }

            // Donut chart and legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut placeholder
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush())
                )

                // Legend items
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SkeletonCircle(size = 12.dp)
                            SkeletonBox(width = 80.dp, height = 14.dp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Skeleton for Cognitive Patterns Card
 */
@Composable
fun CognitivePatternsCardSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SkeletonBox(width = 140.dp, height = 20.dp)
                SkeletonBox(width = 100.dp, height = 16.dp)
            }

            // Pattern items
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    SkeletonCircle(size = 8.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SkeletonBox(height = 16.dp)
                        SkeletonBox(width = 160.dp, height = 12.dp)
                    }
                    SkeletonBox(width = 60.dp, height = 20.dp, cornerRadius = 10.dp)
                }
            }
        }
    }
}

/**
 * Skeleton for Topics Cloud Card
 */
@Composable
fun TopicsCloudCardSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            SkeletonBox(width = 100.dp, height = 20.dp)

            // Topic chips
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonBox(width = 70.dp, height = 32.dp, cornerRadius = 16.dp)
                    SkeletonBox(width = 90.dp, height = 32.dp, cornerRadius = 16.dp)
                    SkeletonBox(width = 60.dp, height = 32.dp, cornerRadius = 16.dp)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonBox(width = 80.dp, height = 32.dp, cornerRadius = 16.dp)
                    SkeletonBox(width = 100.dp, height = 32.dp, cornerRadius = 16.dp)
                }
            }

            // Emerging topic
            SkeletonBox(height = 16.dp)
        }
    }
}

// ==================== ACTIVITY FEED SKELETONS ====================

/**
 * Skeleton for Activity Card
 */
@Composable
fun ActivityCardSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sentiment indicator bar
            SkeletonBox(
                width = 4.dp,
                height = 60.dp,
                cornerRadius = 2.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title and time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkeletonBox(width = 140.dp, height = 18.dp)
                    SkeletonBox(width = 50.dp, height = 14.dp)
                }

                // Preview text
                SkeletonBox(height = 14.dp)
                SkeletonBox(width = 200.dp, height = 14.dp)

                // Tags
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(width = 60.dp, height = 20.dp, cornerRadius = 10.dp)
                    SkeletonBox(width = 50.dp, height = 20.dp, cornerRadius = 10.dp)
                }
            }
        }
    }
}

/**
 * Skeleton for Feed Section Header
 */
@Composable
fun FeedSectionHeaderSkeleton(
    modifier: Modifier = Modifier
) {
    SkeletonBox(
        modifier = modifier,
        width = 80.dp,
        height = 16.dp,
        cornerRadius = 4.dp
    )
}

// ==================== ACHIEVEMENTS SKELETONS ====================

/**
 * Skeleton for Achievements Row
 */
@Composable
fun AchievementsRowSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            SkeletonBox(width = 120.dp, height = 18.dp)

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(3) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SkeletonBox(width = 50.dp, height = 32.dp)
                        SkeletonBox(width = 70.dp, height = 12.dp)
                    }
                }
            }

            // Badge card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonCircle(size = 40.dp)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SkeletonBox(width = 100.dp, height = 16.dp)
                        SkeletonBox(width = 180.dp, height = 12.dp)
                    }
                }
            }
        }
    }
}

// ==================== COMPLETE HOME SKELETON ====================

/**
 * Complete Home Screen Skeleton
 */
@Composable
fun HomeScreenSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeroGreetingCardSkeleton()
        QuickActionsRowSkeleton()
        DailyInsightsChartSkeleton()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MoodDistributionCardSkeleton(modifier = Modifier.weight(1f))
        }

        AchievementsRowSkeleton()

        // Activity feed items
        repeat(2) {
            ActivityCardSkeleton()
        }
    }
}
