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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * User avatar with fallback to initial letter.
 * Supports optional border ring for profile display.
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String?,
    size: Dp = 36.dp,
    showBorder: Boolean = true,
    modifier: Modifier = Modifier
) {
    val initial = displayName?.firstOrNull()?.uppercase() ?: "?"

    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = displayName ?: "User avatar",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (showBorder) Modifier.border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    ) else Modifier
                )
        )
    } else {
        // Fallback: colored circle with initial
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .then(
                    if (showBorder) Modifier.border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    ) else Modifier
                )
        ) {
            Text(
                text = initial,
                style = if (size >= 48.dp) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
