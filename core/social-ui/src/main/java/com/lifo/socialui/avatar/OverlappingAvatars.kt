package com.lifo.socialui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * Overlapping avatars showing who replied to a thread.
 * Displays up to 3 small circular avatars with overlap.
 */
@Composable
fun OverlappingAvatars(
    avatarUrls: List<String>,
    avatarSize: Dp = 16.dp,
    overlapFraction: Float = 0.35f,
    modifier: Modifier = Modifier
) {
    if (avatarUrls.isEmpty()) return

    Layout(
        modifier = modifier,
        content = {
            avatarUrls.take(3).forEachIndexed { index, url ->
                MiniAvatar(
                    url = url,
                    size = avatarSize,
                    modifier = Modifier.zIndex((avatarUrls.size - index).toFloat())
                )
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val itemWidth = placeables.firstOrNull()?.width ?: 0
        val overlapPx = (avatarSize.toPx() * overlapFraction).roundToInt()
        val totalWidth = if (placeables.isEmpty()) 0
        else itemWidth + (placeables.size - 1) * (itemWidth - overlapPx)

        layout(totalWidth, placeables.firstOrNull()?.height ?: 0) {
            var xOffset = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x = xOffset, y = 0)
                xOffset += placeable.width - overlapPx
            }
        }
    }
}

@Composable
private fun MiniAvatar(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
        ) {
            Text(
                text = "?",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
