package com.lifo.home.presentation.components.common

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

/**
 * Home Animations - Micro-interactions and transitions
 * Material3 Expressive animation specifications
 */
object HomeAnimations {

    // ==================== ANIMATION SPECS ====================

    /**
     * Standard card press feedback
     */
    val CardPressSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /**
     * Counter animation spec for numbers
     */
    val CounterSpec = tween<Float>(
        durationMillis = 1000,
        easing = FastOutSlowInEasing
    )

    /**
     * Chart reveal animation
     */
    val ChartRevealSpec = tween<Float>(
        durationMillis = 800,
        easing = FastOutSlowInEasing
    )

    /**
     * Donut segment animation with stagger
     */
    fun donutSegmentSpec(index: Int) = tween<Float>(
        durationMillis = 500,
        delayMillis = index * 100,
        easing = FastOutSlowInEasing
    )

    /**
     * Week transition in chart
     */
    val WeekTransitionSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    /**
     * Card expansion animation
     */
    val CardExpansionSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * Pull to refresh spring
     */
    val PullRefreshSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    /**
     * Staggered entrance delay
     */
    fun staggeredDelay(index: Int, baseDelayMs: Int = 50): Int {
        return index * baseDelayMs
    }

    // ==================== ENTER/EXIT TRANSITIONS ====================

    /**
     * Fade and slide up entrance
     */
    val fadeSlideIn = fadeIn(
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + slideInVertically(
        initialOffsetY = { 20 },
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )

    /**
     * Fade and slide down exit
     */
    val fadeSlideOut = fadeOut(
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    ) + slideOutVertically(
        targetOffsetY = { -20 },
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    )

    /**
     * Expand with fade entrance
     */
    val expandFadeIn = fadeIn(
        animationSpec = tween(200)
    ) + expandVertically(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    /**
     * Shrink with fade exit
     */
    val shrinkFadeOut = fadeOut(
        animationSpec = tween(150)
    ) + shrinkVertically(
        animationSpec = tween(200)
    )

    // ==================== CONTENT TRANSITION SPECS ====================

    /**
     * Crossfade content transition
     */
    val contentCrossfade = ContentTransform(
        targetContentEnter = fadeIn(tween(300)),
        initialContentExit = fadeOut(tween(200))
    )

    /**
     * Slide horizontal content transition (for week navigation)
     */
    fun slideHorizontal(forward: Boolean) = ContentTransform(
        targetContentEnter = fadeIn(tween(200)) + slideInHorizontally(
            initialOffsetX = { if (forward) it else -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        initialContentExit = fadeOut(tween(150)) + slideOutHorizontally(
            targetOffsetX = { if (forward) -it else it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        )
    )
}

// ==================== COMPOSABLE ANIMATION UTILITIES ====================

/**
 * Animated counter for displaying numeric values
 */
@Composable
fun animatedCounter(
    targetValue: Float,
    durationMs: Int = 1000
): Float {
    var animatedValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(targetValue) {
        animate(
            initialValue = animatedValue,
            targetValue = targetValue,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = FastOutSlowInEasing
            )
        ) { value, _ ->
            animatedValue = value
        }
    }

    return animatedValue
}

/**
 * Animated integer counter
 */
@Composable
fun animatedIntCounter(
    targetValue: Int,
    durationMs: Int = 800
): Int {
    var animatedValue by remember { mutableIntStateOf(0) }

    LaunchedEffect(targetValue) {
        animate(
            initialValue = animatedValue.toFloat(),
            targetValue = targetValue.toFloat(),
            animationSpec = tween(
                durationMillis = durationMs,
                easing = FastOutSlowInEasing
            )
        ) { value, _ ->
            animatedValue = value.toInt()
        }
    }

    return animatedValue
}

/**
 * Staggered entrance modifier.
 *
 * Phase 7.1 (2026-05-17) — when the platform reports reduced-motion preference
 * (Android `ANIMATOR_DURATION_SCALE == 0` / iOS `UIAccessibilityIsReduceMotionEnabled`)
 * we snap to the end state immediately. The cards still appear (the layout doesn't
 * change), they just don't slide+fade in. Affects every Home item that uses this
 * modifier (10+ surfaces including the 4 Phase 5/6 bio cards).
 */
@Composable
fun Modifier.staggeredEntrance(
    index: Int,
    baseDelayMs: Int = 50,
    durationMs: Int = 300
): Modifier {
    val reducedMotion = com.lifo.ui.accessibility.isReducedMotionEnabled()
    if (reducedMotion) return this
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay((index * baseDelayMs).toLong())
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = FastOutSlowInEasing
            )
        )
    }

    return this.graphicsLayer {
        alpha = animatable.value
        translationY = (1f - animatable.value) * 20f
    }
}

/**
 * Press scale modifier
 */
@Composable
fun Modifier.pressScale(isPressed: Boolean): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = HomeAnimations.CardPressSpec,
        label = "pressScale"
    )

    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Breathing animation modifier (subtle scale pulse)
 */
@Composable
fun Modifier.breathingAnimation(
    minScale: Float = 1f,
    maxScale: Float = 1.03f,
    durationMs: Int = 2000
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Shimmer animation values
 */
@Composable
fun shimmerOffset(durationMs: Int = 1200): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    return offset
}

/**
 * Glow alpha animation
 */
@Composable
fun glowAlpha(
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 0.6f,
    durationMs: Int = 2000
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    return alpha
}

/**
 * Bounce animation for notifications/badges
 */
@Composable
fun bounceScale(durationMs: Int = 600): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceScale"
    )
    return scale
}

/**
 * Rotation animation (for loading or attention)
 */
@Composable
fun rotationAnimation(
    durationMs: Int = 2000,
    infinite: Boolean = true
): Float {
    return if (infinite) {
        val infiniteTransition = rememberInfiniteTransition(label = "rotation")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotationInfinite"
        )
        rotation
    } else {
        var rotation by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            animate(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = tween(durationMs, easing = LinearEasing)
            ) { value, _ ->
                rotation = value
            }
        }
        rotation
    }
}
