package com.lifo.home.presentation.components.feed

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifo.home.domain.model.EnhancedActivityItem
import com.lifo.home.domain.model.SwipeAction
import com.lifo.home.domain.model.TimePeriod

/**
 * Activity Feed - Unified stream of diary and chat entries
 * With grouping by time period and smooth animations
 */
@Composable
fun ActivityFeed(
    groupedItems: Map<TimePeriod, List<EnhancedActivityItem>>,
    onItemClick: (EnhancedActivityItem) -> Unit,
    onItemSwipeAction: (EnhancedActivityItem, SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (groupedItems.isEmpty() || groupedItems.values.all { it.isEmpty() }) {
        EmptyFeedState(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groupedItems.forEach { (period, items) ->
            if (items.isNotEmpty()) {
                // Section header (sticky)
                stickyHeader(key = "header_${period.name}") {
                    FeedSectionHeader(
                        period = period,
                        itemCount = items.size
                    )
                }

                // Items in this section
                items(
                    items = items,
                    key = { it.id }
                ) { item ->
                    val index = items.indexOf(item)
                    EnhancedActivityCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        index = index
                    )
                }

                // Spacer between sections
                item(key = "spacer_${period.name}") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Bottom padding for FAB
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

/**
 * Simple activity feed without grouping
 */
@Composable
fun SimpleActivityFeed(
    items: List<EnhancedActivityItem>,
    onItemClick: (EnhancedActivityItem) -> Unit,
    modifier: Modifier = Modifier,
    maxItems: Int = Int.MAX_VALUE
) {
    if (items.isEmpty()) {
        EmptyFeedState(modifier = modifier)
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.take(maxItems).forEachIndexed { index, item ->
            EnhancedActivityCard(
                item = item,
                onClick = { onItemClick(item) },
                index = index
            )
        }

        if (items.size > maxItems) {
            TextButton(
                onClick = { /* Navigate to full history */ },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Mostra tutti (${items.size})",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * Compact activity feed for dashboard
 */
@Composable
fun CompactActivityFeed(
    items: List<EnhancedActivityItem>,
    onItemClick: (EnhancedActivityItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    maxItems: Int = 3
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attività recente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )

                TextButton(onClick = onSeeAll) {
                    Text(
                        text = "Vedi tutto",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = "Nessuna attività recente",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                items.take(maxItems).forEach { item ->
                    CompactActivityCard(
                        item = item,
                        onClick = { onItemClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFeedState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Text(
                text = "Nessuna attività",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Inizia a scrivere un diario o avvia una chat per vedere la tua attività qui",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
