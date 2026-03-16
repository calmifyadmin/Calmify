package com.lifo.socialui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Spring spec for like button bounce animation
 */
val LikeBounceSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * Spring spec for follow button animation
 */
val FollowButtonSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

/**
 * Spring spec for card entrance animations
 */
val CardEntranceSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow
)

/**
 * Tween spec for staggered item entrance
 */
fun staggeredEntranceTween(index: Int, baseDelay: Int = 50) = tween<Float>(
    durationMillis = 300,
    delayMillis = index * baseDelay,
    easing = FastOutSlowInEasing
)

/**
 * Modifier that adds a press scale effect (shrinks slightly on press)
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * Modifier for staggered entrance animation (fade + slide up)
 */
fun Modifier.staggeredEntrance(
    index: Int,
    baseDelay: Int = 50
): Modifier = composed {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = staggeredEntranceTween(index, baseDelay)
        )
    }

    this.graphicsLayer {
        alpha = animatable.value
        translationY = (1f - animatable.value) * 24f
    }
}

/**
 * Like button bounce composable helper
 */
@Composable
fun rememberLikeBounce(isLiked: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (isLiked) 1f else 1f,
        animationSpec = LikeBounceSpec,
        label = "likeBounce"
    )
    return scale
}
