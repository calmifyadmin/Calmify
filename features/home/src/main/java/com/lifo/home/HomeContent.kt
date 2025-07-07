package com.lifo.home

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.lifo.ui.components.loading.*
import com.lifo.util.DiaryHolder
import com.lifo.util.model.Diary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeContent(
    paddingValues: PaddingValues,
    diaryNotes: Map<LocalDate, List<Diary>>?,
    onClick: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel
) {
    val listState = rememberLazyListState()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        refreshing = isLoading
    }

    when {
        diaryNotes == null && isLoading -> {
            // Show skeleton loading for initial load
            GoogleStyleLoadingIndicator(
                loadingState = LoadingState.Loading("Loading your memories..."),
                style = LoadingStyle.Skeleton,
                modifier = Modifier.fillMaxSize()
            )
        }
        diaryNotes.isNullOrEmpty() && !isLoading -> {
            EmptyStateContent(
                onCreateFirst = { /* Navigate to write */ }
            )
        }
        else -> {
            SwipeRefresh(
                state = rememberSwipeRefreshState(refreshing),
                onRefresh = {
                    viewModel.loadStuff()
                },
                indicator = { state, trigger ->
                    SwipeRefreshIndicator(
                        state = state,
                        refreshTriggerDistance = trigger,
                        scale = true,
                        backgroundColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(top = paddingValues.calculateTopPadding()),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    diaryNotes?.forEach { (localDate, diaries) ->
                        stickyHeader(key = localDate) {
                            EnhancedDateHeader(
                                localDate = localDate,
                                diaryCount = diaries.size
                            )
                        }
                        items(
                            items = diaries,
                            key = { it._id.toString() }
                        ) { diary ->
                            AnimatedDiaryItem(
                                diary = diary,
                                onClick = onClick
                            )
                        }
                    }

                    // Bottom padding for FAB
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
internal fun EnhancedDateHeader(
    localDate: LocalDate,
    diaryCount: Int
) {
    val today = LocalDate.now()
    val displayText = when (localDate) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date block with modern design
            Column(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = localDate.dayOfMonth.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = localDate.dayOfWeek.toString().take(3).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (diaryCount == 1) "1 entry" else "$diaryCount entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun AnimatedDiaryItem(
    diary: Diary,
    onClick: (String) -> Unit
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(diary) {
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .graphicsLayer {
                alpha = animatedProgress.value
                scaleX = 0.8f + (0.2f * animatedProgress.value)
                scaleY = 0.8f + (0.2f * animatedProgress.value)
            }
    ) {
        DiaryHolder(
            diary = diary,
            onClick = onClick
        )
    }
}

@Composable
internal fun EmptyStateContent(
    onCreateFirst: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated empty state illustration could go here

            Text(
                text = "Your diary is empty",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Start writing your thoughts and memories",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            androidx.compose.material3.FilledTonalButton(
                onClick = onCreateFirst,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Create Your First Entry")
            }
        }
    }
}