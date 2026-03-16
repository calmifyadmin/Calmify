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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * Threads-style clustered avatar bubbles.
 *
 * Layout (matches Meta's Threads):
 * - 0: Nothing
 * - 1: Single circle, centered
 * - 2: Two circles side-by-side with slight overlap
 * - 3: Triangle formation — 1 on top centered, 2 on bottom
 *
 * Each avatar has a 2dp white border. Container auto-sizes.
 *
 * @param avatarUrls List of avatar URLs (max 3 displayed)
 * @param avatarSize Size of each individual avatar circle
 * @param borderWidth White border width around each avatar
 */
@Composable
fun ClusteredAvatars(
    avatarUrls: List<String>,
    avatarSize: Dp = 20.dp,
    borderWidth: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    if (avatarUrls.isEmpty()) return

    val displayUrls = avatarUrls.take(3)

    Layout(
        modifier = modifier,
        content = {
            displayUrls.forEach { url ->
                ClusterAvatar(
                    url = url,
                    size = avatarSize,
                    borderWidth = borderWidth,
                )
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val itemSizePx = placeables.firstOrNull()?.width ?: 0
        val halfItem = itemSizePx / 2
        val gap = (2.dp.toPx()).roundToInt()

        when (placeables.size) {
            1 -> {
                // Centered single avatar
                layout(itemSizePx, itemSizePx) {
                    placeables[0].placeRelative(0, 0)
                }
            }
            2 -> {
                // Side-by-side with slight overlap
                val overlapPx = (avatarSize.toPx() * 0.25f).roundToInt()
                val totalWidth = itemSizePx * 2 - overlapPx
                layout(totalWidth, itemSizePx) {
                    placeables[0].placeRelative(0, 0)
                    placeables[1].placeRelative(itemSizePx - overlapPx, 0)
                }
            }
            else -> {
                // Triangle: 1 top-center, 2 bottom
                val overlapPx = (avatarSize.toPx() * 0.15f).roundToInt()
                val bottomWidth = itemSizePx * 2 - overlapPx
                val totalWidth = bottomWidth
                val totalHeight = itemSizePx * 2 - overlapPx

                layout(totalWidth, totalHeight) {
                    // Top avatar — centered horizontally
                    val topX = (totalWidth - itemSizePx) / 2
                    placeables[0].placeRelative(topX, 0)

                    // Bottom left
                    placeables[1].placeRelative(0, itemSizePx - overlapPx)

                    // Bottom right
                    placeables[2].placeRelative(itemSizePx - overlapPx, itemSizePx - overlapPx)
                }
            }
        }
    }
}

@Composable
private fun ClusterAvatar(
    url: String,
    size: Dp,
    borderWidth: Dp,
    modifier: Modifier = Modifier,
) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(borderWidth, Color.White, CircleShape),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(borderWidth, Color.White, CircleShape),
        ) {
            Text(
                text = "?",
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
