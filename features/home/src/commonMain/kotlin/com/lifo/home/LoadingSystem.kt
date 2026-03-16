package com.lifo.ui.components.loading

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning

/**
 * Loading state representation
 */
sealed class LoadingState {
    object Initial : LoadingState()
    data class Loading(
        val message: String = "Loading...",
        val progress: Float? = null
    ) : LoadingState()
    data class Error(val message: String, val retry: (() -> Unit)? = null) : LoadingState()
    object Success : LoadingState()
}

/**
 * Main loading composable with multiple styles
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GoogleStyleLoadingIndicator(
    loadingState: LoadingState,
    modifier: Modifier = Modifier,
    style: LoadingStyle = LoadingStyle.Material3,
    onErrorRetry: (() -> Unit)? = null
) {
    AnimatedContent(
        targetState = loadingState,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) with
                    fadeOut(animationSpec = tween(300)) using
                    SizeTransform(clip = false)
        },
        modifier = modifier,
        label = "LoadingStateAnimation"
    ) { state ->
        when (state) {
            is LoadingState.Loading -> {
                when (style) {
                    LoadingStyle.Material3 -> Material3LoadingIndicator(
                        message = state.message,
                        progress = state.progress
                    )
                    LoadingStyle.GoogleDots -> GoogleDotsLoadingIndicator(
                        message = state.message
                    )
                    LoadingStyle.Shimmer -> ShimmerLoadingIndicator()
                    LoadingStyle.Skeleton -> SkeletonLoadingIndicator()
                }
            }
            is LoadingState.Error -> {
                ErrorStateComponent(
                    message = state.message,
                    onRetry = state.retry ?: onErrorRetry
                )
            }
            else -> Box(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Loading styles enum
 */
enum class LoadingStyle {
    Material3,
    GoogleDots,
    Shimmer,
    Skeleton
}

/**
 * Material 3 style loading with progress
 */
@Composable
private fun Material3LoadingIndicator(
    message: String,
    progress: Float?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            if (progress != null) {
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (progress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Google style animated dots
 */
@Composable
private fun GoogleDotsLoadingIndicator(
    message: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GoogleDotsAnimation")

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val googleColors = listOf(
                    Color(0xFF4285F4), // Blue
                    Color(0xFFEA4335), // Red
                    Color(0xFFFBBC04), // Yellow
                    Color(0xFF34A853)  // Green
                )

                googleColors.forEachIndexed { index, color ->
                    val animatedScale by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1200
                                0.3f at 0 + (index * 100)
                                1f at 300 + (index * 100)
                                0.3f at 600 + (index * 100)
                            }
                        ),
                        label = "DotScale$index"
                    )

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .scale(animatedScale)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedLoadingText(text = message)
        }
    }
}

/**
 * Animated loading text with dots
 */
@Composable
private fun AnimatedLoadingText(
    text: String,
    modifier: Modifier = Modifier
) {
    var dotCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotCount = (dotCount + 1) % 4
        }
    }

    Text(
        text = text + ".".repeat(dotCount),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * Shimmer effect loading
 */
@Composable
private fun ShimmerLoadingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ShimmerAnimation")
    val shimmerTranslateAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "ShimmerTranslate"
    )

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            repeat(5) { index ->
                ShimmerItem(
                    shimmerTranslateAnim = shimmerTranslateAnim,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ShimmerItem(
    shimmerTranslateAnim: Float,
    modifier: Modifier = Modifier
) {
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surface
        ),
        start = androidx.compose.ui.geometry.Offset(shimmerTranslateAnim - 200f, 0f),
        end = androidx.compose.ui.geometry.Offset(shimmerTranslateAnim, 0f)
    )

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

/**
 * Skeleton loading for content
 */
@Composable
private fun SkeletonLoadingIndicator(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header skeleton
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Content skeleton
        repeat(3) {
            SkeletonCard()
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SkeletonCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
        }
    }
}

@Composable
private fun SkeletonBox(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SkeletonPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SkeletonAlpha"
    )

    Box(
        modifier = modifier
            .alpha(alpha)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                RoundedCornerShape(4.dp)
            )
    )
}

/**
 * Error state component
 */
@Composable
private fun ErrorStateComponent(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Oops! Something went wrong",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (onRetry != null) {
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    Text("Try Again")
                }
            }
        }
    }
}

/**
 * Extension function for easy usage
 */
@Composable
fun LoadingStateWrapper(
    loadingState: LoadingState,
    style: LoadingStyle = LoadingStyle.Material3,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (loadingState is LoadingState.Success) {
            content()
        } else {
            GoogleStyleLoadingIndicator(
                loadingState = loadingState,
                style = style
            )
        }
    }
}