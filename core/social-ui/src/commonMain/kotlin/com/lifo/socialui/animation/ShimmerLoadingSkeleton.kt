package com.lifo.socialui.animation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Creates a shimmer brush with animated gradient for loading skeletons.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainerLow,
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim),
    )
}

/**
 * Shimmer skeleton mimicking a ThreadPostCard layout.
 * Used as loading placeholder in Feed and Profile screens.
 */
@Composable
fun ThreadPostCardSkeleton(
    modifier: Modifier = Modifier,
) {
    val brush = rememberShimmerBrush()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(brush),
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Username + timestamp row
            Row {
                ShimmerBox(brush = brush, width = 100.dp, height = 12.dp)
                Spacer(Modifier.width(8.dp))
                ShimmerBox(brush = brush, width = 32.dp, height = 12.dp)
            }
            Spacer(Modifier.height(8.dp))

            // Text lines
            ShimmerBox(brush = brush, width = 280.dp, height = 12.dp)
            Spacer(Modifier.height(6.dp))
            ShimmerBox(brush = brush, width = 220.dp, height = 12.dp)
            Spacer(Modifier.height(6.dp))
            ShimmerBox(brush = brush, width = 160.dp, height = 12.dp)
            Spacer(Modifier.height(12.dp))

            // Engagement bar placeholder
            Row {
                ShimmerBox(brush = brush, width = 40.dp, height = 12.dp)
                Spacer(Modifier.width(16.dp))
                ShimmerBox(brush = brush, width = 40.dp, height = 12.dp)
                Spacer(Modifier.width(16.dp))
                ShimmerBox(brush = brush, width = 40.dp, height = 12.dp)
                Spacer(Modifier.width(16.dp))
                ShimmerBox(brush = brush, width = 20.dp, height = 12.dp)
            }
        }
    }
}

@Composable
private fun ShimmerBox(
    brush: Brush,
    width: Dp,
    height: Dp,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(brush),
    )
}
