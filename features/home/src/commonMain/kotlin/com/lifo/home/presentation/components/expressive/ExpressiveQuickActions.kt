package com.lifo.home.presentation.components.expressive

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.home.presentation.components.common.pressScale

@Composable
internal fun ExpressiveQuickActions(
    onTalkToEve: () -> Unit,
    onWrite: () -> Unit,
    onSnapshot: () -> Unit,
    isSnapshotDue: Boolean,
    daysSinceSnapshot: Int,
    modifier: Modifier = Modifier,
    eveCardModifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeroActionCard(
            icon = Icons.Default.AutoAwesome,
            title = "Parla con Eve",
            subtitle = "La tua assistente AI vocale",
            onClick = onTalkToEve,
            accentColor = colorScheme.primary,
            modifier = Modifier.fillMaxWidth().then(eveCardModifier)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillActionButton(
                icon = Icons.Default.Edit,
                label = "Scrivi",
                onClick = onWrite,
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )

            PillActionButton(
                icon = Icons.Default.SelfImprovement,
                label = "Snapshot",
                onClick = onSnapshot,
                containerColor = if (isSnapshotDue)
                    colorScheme.tertiaryContainer
                else
                    colorScheme.surfaceContainerHigh,
                contentColor = if (isSnapshotDue)
                    colorScheme.onTertiaryContainer
                else
                    colorScheme.onSurfaceVariant,
                showBadge = isSnapshotDue,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ==================== HERO ACTION CARD ====================

@Composable
private fun HeroActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val infiniteTransition = rememberInfiniteTransition(label = "heroCard")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowNudge"
    )

    val shape = RoundedCornerShape(28.dp)

    Surface(
        modifier = modifier
            .pressScale(isPressed)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        shape = shape,
        color = colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(24.dp),
                        tint = accentColor
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { translationX = arrowOffset },
                        tint = accentColor
                    )
                }
            }
        }
    }
}

// ==================== PILL ACTION BUTTON ====================

@Composable
private fun PillActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        modifier = modifier
            .height(56.dp)
            .pressScale(isPressed)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        shape = RoundedCornerShape(20.dp),
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = contentColor,
                    maxLines = 1
                )
            }

            if (showBadge) {
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) {
                    Text("!")
                }
            }
        }
    }
}