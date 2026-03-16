package com.lifo.socialui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Vertical thread line connecting posts in a thread.
 * Placed between the avatar and the reply avatars in the left column.
 */
@Composable
fun ThreadLine(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(2.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(1.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
