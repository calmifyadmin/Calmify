package com.lifo.home.presentation.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.presentation.components.common.pressScale
import com.lifo.home.util.EmotionAwareColors

@Composable
internal fun QuickActionCards(
    onTalkToEve: () -> Unit,
    onWrite: () -> Unit,
    onSnapshot: () -> Unit,
    isSnapshotDue: Boolean,
    daysSinceSnapshot: Int,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val primary = colorScheme.primary

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top row: Talk to Eve (60%) + Write (40%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionCard(
                icon = Icons.Default.AutoAwesome,
                title = "Parla con Eve",
                subtitle = "Voce AI",
                onClick = onTalkToEve,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                overlayBrush = EmotionAwareColors.accentGradient(primary),
                accentColor = primary,
                modifier = Modifier.weight(0.6f)
            )

            QuickActionCard(
                icon = Icons.Default.Edit,
                title = "Scrivi",
                subtitle = "Nuovo diario",
                onClick = onWrite,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                accentColor = colorScheme.onSurface,
                modifier = Modifier.weight(0.4f)
            )
        }

        // Bottom row: Snapshot
        QuickActionCard(
            icon = Icons.Default.SelfImprovement,
            title = "Wellbeing Snapshot",
            subtitle = if (isSnapshotDue) "$daysSinceSnapshot giorni dall'ultimo"
            else "Verifica il tuo benessere",
            onClick = onSnapshot,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            accentColor = if (isSnapshotDue) primary else colorScheme.onSurfaceVariant,
            showBadge = isSnapshotDue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    overlayBrush: androidx.compose.ui.graphics.Brush? = null,
    showBadge: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        modifier = modifier
            .pressScale(isPressed)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Box {
            if (overlayBrush != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(overlayBrush)
                )
            }

            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = accentColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (showBadge) {
                    Badge(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ) {
                        Text("!")
                    }
                }
            }
        }
    }
}
