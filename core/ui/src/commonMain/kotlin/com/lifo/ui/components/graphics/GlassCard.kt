package com.lifo.ui.components.graphics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * GlassCard — Glassmorphism card using Haze blur.
 *
 * Renders a frosted-glass effect with emotional tint.
 * Requires a parent with `Modifier.haze(hazeState)` applied for the blur source.
 */
@Composable
fun GlassCard(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Unspecified,
    tintAlpha: Float = 0.15f,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedTint = if (tintColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        tintColor
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .hazeChild(
                state = hazeState,
                style = HazeStyle(
                    blurRadius = 20.dp,
                    tints = listOf(
                        HazeTint(
                            color = resolvedTint.copy(alpha = tintAlpha),
                        ),
                    ),
                    noiseFactor = 0.05f,
                ),
            ),
        shape = shape,
        color = Color.Transparent,
        shadowElevation = elevation,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}
