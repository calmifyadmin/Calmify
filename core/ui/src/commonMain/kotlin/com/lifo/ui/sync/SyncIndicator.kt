package com.lifo.ui.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Sync state for the indicator.
 */
enum class SyncUiState {
    SYNCED,      // All changes uploaded
    SYNCING,     // Currently pushing/pulling
    PENDING,     // Has unsynced changes (offline or waiting)
    ERROR,       // Sync failed after retries
}

/**
 * Compact sync indicator — shows in app bar or status area.
 *
 * Auto-hides after 3s when SYNCED.
 * Stays visible when SYNCING/PENDING/ERROR.
 */
@Composable
fun SyncIndicator(
    state: SyncUiState,
    pendingCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }

    // Auto-hide when synced
    LaunchedEffect(state) {
        if (state == SyncUiState.SYNCED) {
            visible = true
            delay(3000)
            visible = false
        } else {
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible && state != SyncUiState.SYNCED,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val (icon, text, containerColor) = when (state) {
            SyncUiState.SYNCED -> Triple(
                Icons.Default.CloudDone,
                "Sincronizzato",
                MaterialTheme.colorScheme.primaryContainer,
            )
            SyncUiState.SYNCING -> Triple(
                Icons.Default.CloudSync,
                "Sincronizzazione...",
                MaterialTheme.colorScheme.secondaryContainer,
            )
            SyncUiState.PENDING -> Triple(
                Icons.Default.CloudOff,
                if (pendingCount > 0) "$pendingCount non sincronizzati" else "In attesa...",
                MaterialTheme.colorScheme.tertiaryContainer,
            )
            SyncUiState.ERROR -> Triple(
                Icons.Default.CloudOff,
                "Errore sync",
                MaterialTheme.colorScheme.errorContainer,
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Pending badge — shows a small count on a navigation item.
 */
@Composable
fun SyncPendingBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return

    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
        )
    }
}
