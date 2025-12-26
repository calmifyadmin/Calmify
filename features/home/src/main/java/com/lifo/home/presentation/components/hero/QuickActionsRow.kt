package com.lifo.home.presentation.components.hero

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Quick Actions Row - 3 primary action buttons with Material3 Expressive styling
 * Enhanced with varied shapes, sizes, colors and typography for expressive design
 *
 * Layout:
 * ┌──────────┐  ┌──────────┐  ┌──────────┐
 * │    ✏️    │  │    🎤    │  │    📊    │
 * │  Scrivi  │  │   Live   │  │ Snapshot │
 * └──────────┘  └──────────┘  └──────────┘
 */
@Composable
fun QuickActionsRow(
    onWriteDiary: () -> Unit,
    onStartLive: () -> Unit,
    onTakeSnapshot: () -> Unit,
    snapshotDueIndicator: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Staggered entrance animation
    val animationDelays = listOf(0, 50, 100)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Button 1: Left - "Parla con Eve" takes 2 spaces, fully rounded
        QuickActionButton(
            icon = Icons.Default.AutoAwesome,
            label = "Parla con Eve",
            onClick = onStartLive,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            animationDelay = animationDelays[0],
            modifier = Modifier.weight(2f),
            shape = RoundedCornerShape(50),
            iconSize = 28.dp,
            textStyle = MaterialTheme.typography.labelLarge,
            elevation = 2.dp
        )

        // Button 2: Right - "Snapshot" takes 1 space
        QuickActionButton(
            icon = Icons.Default.SelfImprovement,
            label = "Snapshot",
            onClick = onTakeSnapshot,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            showBadge = snapshotDueIndicator,
            animationDelay = animationDelays[1],
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            iconSize = 28.dp,
            textStyle = MaterialTheme.typography.labelLarge,
            elevation = 2.dp
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
    animationDelay: Int = 0,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    elevation: androidx.compose.ui.unit.Dp = 1.dp
) {
    val haptic = LocalHapticFeedback.current

    // Entrance animation
    val enterTransition = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        enterTransition.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    // Press animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    // Badge pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    val badgeScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgePulse"
    )

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale * enterTransition.value
                    scaleY = scale * enterTransition.value
                    alpha = enterTransition.value
                    translationY = (1f - enterTransition.value) * 20f
                },
            shape = shape,
            color = containerColor,
            tonalElevation = elevation,
            shadowElevation = elevation,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(iconSize),
                    tint = contentColor
                )
                Text(
                    text = label,
                    style = textStyle,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }

        // Badge indicator for snapshot due
        if (showBadge) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    },
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Text("!")
            }
        }
    }
}

/**
 * Compact Quick Actions for smaller spaces
 */
@Composable
fun CompactQuickActionsRow(
    onWriteDiary: () -> Unit,
    onStartLive: () -> Unit,
    onTakeSnapshot: () -> Unit,
    snapshotDueIndicator: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactActionButton(
            icon = Icons.Default.Edit,
            onClick = onWriteDiary,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )

        CompactActionButton(
            icon = Icons.Default.Mic,
            onClick = onStartLive,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )

        CompactActionButton(
            icon = Icons.Default.SelfImprovement,
            onClick = onTakeSnapshot,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            showBadge = snapshotDueIndicator,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompactActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false
) {
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier) {
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }

        if (showBadge) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp),
                containerColor = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Large Floating Action Button style for primary action
 */
@Composable
fun PrimaryWriteActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "fab")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fabBreathing"
    )

    ExtendedFloatingActionButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(20.dp),
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Scrivi",
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Scrivi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
