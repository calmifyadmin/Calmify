package com.lifo.ui.components.biosignal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.ui.theme.CalmifyRadius
import com.lifo.ui.theme.CalmifySpacing

/**
 * Shared empty-state atom for bio surfaces (Phase 9.3, 2026-05-17).
 *
 * Until 9.3 each surface (BioTimeline, BioPatternFeed, BioContext sections)
 * shipped its own ad-hoc empty block. Consolidating into one atom gives every
 * bio surface the same visual grammar:
 *
 *  - 48dp outlined icon
 *  - title (titleSmall + SemiBold)
 *  - body (bodySmall)
 *  - large outer padding, surfaceVariant container
 *
 * The icon and copy stay caller-supplied — empty meaning ("no data yet" vs
 * "nothing to detect" vs "you paused this surface") is surface-specific.
 */
@Composable
fun BioEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalmifyRadius.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(CalmifySpacing.xl),
        verticalArrangement = Arrangement.spacedBy(CalmifySpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
