package com.lifo.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifo.ui.emotion.MiniMoodShape
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*
import com.lifo.util.model.ContentFilter
import com.lifo.util.model.HomeContentItem
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

private val ITALIAN_MONTHS = arrayOf(
    "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
    "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
)

/**
 * Unified timeline content for the History screen.
 * Merges chat and diary items into a single chronological stream
 * grouped by date (Oggi, Ieri, date).
 */
@Composable
internal fun HistoryContent(
    uiState: HistoryUiState,
    paddingValues: PaddingValues,
    onChatClick: (HomeContentItem.ChatItem) -> Unit,
    onDiaryClick: (HomeContentItem.DiaryItem) -> Unit,
    onChatHistoryHeaderClick: () -> Unit,
    onDiaryHistoryHeaderClick: () -> Unit,
    onRefresh: () -> Unit,
    onFilterSelected: (ContentFilter) -> Unit = {},
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> LoadingContent(modifier = modifier)
        uiState.error != null -> ErrorContent(
            message = uiState.error,
            onRetry = onRefresh,
            modifier = modifier
        )
        uiState.isChatsEmpty && uiState.isDiariesEmpty -> EmptyTimeline(modifier = modifier)
        else -> {
            // Merge all items into unified timeline, sorted by date descending
            val timelineItems = remember(uiState.chatHistory, uiState.diaryHistory) {
                buildUnifiedTimeline(uiState.chatHistory, uiState.diaryHistory)
            }

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Filter chips
                item(key = "filters") {
                    HistoryFilterChips(
                        activeFilter = uiState.activeFilter,
                        onFilterSelected = onFilterSelected
                    )
                }

                items(
                    items = timelineItems,
                    key = { it.key }
                ) { item ->
                    when (item) {
                        is TimelineItem.DateHeader -> {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        is TimelineItem.ChatEntry -> {
                            ChatHistoryItem(
                                chat = item.chat,
                                onClick = { onChatClick(item.chat) }
                            )
                        }
                        is TimelineItem.DiaryEntry -> {
                            DiaryHistoryItem(
                                diary = item.diary,
                                onClick = { onDiaryClick(item.diary) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Unified timeline item types */
private sealed class TimelineItem(val key: String) {
    class DateHeader(val label: String, val date: LocalDate) : TimelineItem("date-$date")
    class ChatEntry(val chat: HomeContentItem.ChatItem) : TimelineItem("chat-${chat.id}")
    class DiaryEntry(val diary: HomeContentItem.DiaryItem) : TimelineItem("diary-${diary.id}")
}

/** Merge and group items by date */
private fun buildUnifiedTimeline(
    chats: List<HomeContentItem.ChatItem>,
    diaries: List<HomeContentItem.DiaryItem>
): List<TimelineItem> {
    data class Timestamped(val millis: Long, val item: Any)

    val tz = TimeZone.currentSystemDefault()
    val all = chats.map { Timestamped(it.createdAt, it) } +
            diaries.map { Timestamped(it.createdAt, it) }

    val sorted = all.sortedByDescending { it.millis }

    val nowDateTime = Clock.System.now().toLocalDateTime(tz)
    val today = nowDateTime.date
    val yesterday = today.minus(DatePeriod(days = 1))
    val result = mutableListOf<TimelineItem>()
    var currentDate: LocalDate? = null

    for (entry in sorted) {
        val date = Instant.fromEpochMilliseconds(entry.millis)
            .toLocalDateTime(tz).date

        if (date != currentDate) {
            currentDate = date
            val label = when (date) {
                today -> "Oggi"
                yesterday -> "Ieri"
                else -> "${date.dayOfMonth} ${ITALIAN_MONTHS[date.monthNumber - 1]} ${date.year}"
            }
            result.add(TimelineItem.DateHeader(label, date))
        }

        when (val item = entry.item) {
            is HomeContentItem.ChatItem -> result.add(TimelineItem.ChatEntry(item))
            is HomeContentItem.DiaryItem -> result.add(TimelineItem.DiaryEntry(item))
        }
    }

    return result
}

@Composable
private fun ChatHistoryItem(
    chat: HomeContentItem.ChatItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Chat,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = chat.title.ifBlank { "Conversazione" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatTime(chat.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiaryHistoryItem(
    diary: HomeContentItem.DiaryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniMoodShape(
                mood = diary.mood,
                modifier = Modifier.size(24.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = diary.title.ifBlank { "Senza titolo" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (diary.content.isNotBlank()) {
                    Text(
                        text = diary.content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = formatTime(diary.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyTimeline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = stringResource(Res.string.history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(Res.string.history_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.history_loading_error),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(Res.string.retry))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFilterChips(
    activeFilter: ContentFilter,
    onFilterSelected: (ContentFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val filters = listOf(
            ContentFilter.ALL to "Tutto",
            ContentFilter.DIARY to "Diari",
            ContentFilter.CHAT to "Chat",
            ContentFilter.TODAY to "Oggi",
            ContentFilter.THIS_WEEK to "Settimana",
            ContentFilter.THIS_MONTH to "Mese"
        )
        items(filters.size) { index ->
            val (filter, label) = filters[index]
            FilterChip(
                selected = filter == activeFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(label) },
                leadingIcon = if (filter == activeFilter) {
                    { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                } else null
            )
        }
    }
}

private fun formatTime(timestampMillis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val instant = Instant.fromEpochMilliseconds(timestampMillis)
    val localDateTime = instant.toLocalDateTime(tz)
    val h = localDateTime.hour.toString()
    val min = localDateTime.minute.toString().padStart(2, '0')
    return "$h:$min"
}
