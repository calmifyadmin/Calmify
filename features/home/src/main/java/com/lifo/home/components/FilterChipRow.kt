package com.lifo.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lifo.util.model.ContentFilter

/**
 * Filter chip row component for filtering content by type and date range
 * Uses Material3 FilterChip for proper state handling and accessibility
 */
@Composable
fun FilterChipRow(
    selectedFilter: ContentFilter,
    onFilterSelected: (ContentFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val filterOptions = listOf(
        FilterOption(
            filter = ContentFilter.ALL,
            label = "All",
            icon = Icons.Default.ViewAgenda
        ),
        FilterOption(
            filter = ContentFilter.DIARY,
            label = "Diary",
            icon = Icons.Default.Edit
        ),
        FilterOption(
            filter = ContentFilter.CHAT,
            label = "Chat",
            icon = Icons.Default.Chat
        ),
        FilterOption(
            filter = ContentFilter.TODAY,
            label = "Today",
            icon = Icons.Default.Today
        ),
        FilterOption(
            filter = ContentFilter.THIS_WEEK,
            label = "This Week",
            icon = Icons.Default.DateRange
        ),
        FilterOption(
            filter = ContentFilter.THIS_MONTH,
            label = "This Month",
            icon = Icons.Default.CalendarMonth
        )
    )

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(
            items = filterOptions,
            key = { it.filter }
        ) { filterOption ->
            FilterChipItem(
                filterOption = filterOption,
                isSelected = selectedFilter == filterOption.filter,
                onClick = { onFilterSelected(filterOption.filter) }
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    filterOption: FilterOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = filterOption.label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = filterOption.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

private data class FilterOption(
    val filter: ContentFilter,
    val label: String,
    val icon: ImageVector
)