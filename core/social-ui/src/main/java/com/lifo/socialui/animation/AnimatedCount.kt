package com.lifo.socialui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.lifo.socialui.post.formatCount

/**
 * Animated count display that slides numbers up/down on change.
 * Slides UP when count increases, DOWN when count decreases.
 */
@Composable
fun AnimatedCount(
    count: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var previousCount by remember { mutableLongStateOf(count) }
    val isIncreasing = count > previousCount

    SideEffect { previousCount = count }

    AnimatedContent(
        targetState = count,
        modifier = modifier,
        transitionSpec = {
            if (isIncreasing) {
                slideInVertically { -it } togetherWith slideOutVertically { it }
            } else {
                slideInVertically { it } togetherWith slideOutVertically { -it }
            }
        },
        label = "animatedCount",
    ) { target ->
        Text(
            text = formatCount(target),
            style = style,
            color = color,
        )
    }
}
