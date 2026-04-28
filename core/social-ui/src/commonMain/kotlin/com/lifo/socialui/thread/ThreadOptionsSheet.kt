package com.lifo.socialui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lifo.ui.i18n.Strings
import org.jetbrains.compose.resources.stringResource

/**
 * Threads-style bottom sheet with post options.
 * Grouped into basic actions (Save, Hide) and moderation (Mute, Block, Report).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadOptionsSheet(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onHide: () -> Unit,
    onMute: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Group 1: Basic actions
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                OptionRow(
                    icon = Icons.Outlined.BookmarkBorder,
                    label = stringResource(Strings.ThreadOptions.save),
                    onClick = { onSave(); onDismiss() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OptionRow(
                    icon = Icons.Outlined.VisibilityOff,
                    label = stringResource(Strings.ThreadOptions.hide),
                    onClick = { onHide(); onDismiss() }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Group 2: Moderation
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                OptionRow(
                    icon = Icons.AutoMirrored.Outlined.VolumeOff,
                    label = stringResource(Strings.ThreadOptions.mute),
                    onClick = { onMute(); onDismiss() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OptionRow(
                    icon = Icons.Outlined.Block,
                    label = stringResource(Strings.ThreadOptions.block),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { onBlock(); onDismiss() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OptionRow(
                    icon = Icons.Outlined.Flag,
                    label = stringResource(Strings.ThreadOptions.report),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { onReport(); onDismiss() }
                )
            }

            // Bottom spacing for nav bar
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = tint
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}
