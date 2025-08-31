package com.lifo.home

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lifo.home.components.*
import com.lifo.mongo.repository.Diaries
import com.lifo.ui.components.loading.*
import com.lifo.util.model.RequestState
import com.lifo.util.model.HomeContentItem
import com.lifo.util.model.ContentFilter
import java.time.ZonedDateTime

// Screen state management
sealed class HomeScreenState {
    object Loading : HomeScreenState()
    data class Ready(val isEmpty: Boolean = false) : HomeScreenState()
    data class Error(val message: String) : HomeScreenState()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun HomeScreen(
    diaries: Diaries,
    navController: NavHostController,
    drawerState: DrawerState,
    onMenuClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
    onDeleteAllClicked: () -> Unit,
    navigateToWrite: () -> Unit,
    navigateToWriteWithArgs: (String) -> Unit,
    dateIsSelected: Boolean,
    onDateSelected: (ZonedDateTime) -> Unit,
    onDateReset: () -> Unit,
    viewModel: HomeViewModel,
    userProfileImageUrl: String?,
    navigateToChat: () -> Unit,
    navigateToLiveChat: () -> Unit,
    navigateToExistingChat: (String) -> Unit,
    // New unified content navigation parameters
    onDiaryClicked: (HomeContentItem.DiaryItem) -> Unit = { navigateToWriteWithArgs(it.id) },
    onChatClicked: (HomeContentItem.ChatItem) -> Unit = { navigateToExistingChat(it.id) }
) {
    // Collect states
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val unifiedState by viewModel.unifiedState.collectAsStateWithLifecycle()
    
    // View toggle state
    var showUnifiedView by rememberSaveable { mutableStateOf(true) }

    // Determine screen state
    val screenState = remember(diaries) {
        when (diaries) {
            is RequestState.Loading -> HomeScreenState.Loading
            is RequestState.Error -> HomeScreenState.Error(diaries.error.message ?: "Unknown error")
            is RequestState.Success -> HomeScreenState.Ready(isEmpty = diaries.data.isEmpty())
            else -> HomeScreenState.Loading
        }
    }

    // FAB expansion state
    var fabExpanded by rememberSaveable { mutableStateOf(true) }

    // Use enterAlwaysScrollBehavior for consistent appearance
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Track scroll for FAB expansion
    LaunchedEffect(scrollBehavior.state.heightOffset) {
        fabExpanded = scrollBehavior.state.heightOffset > -50
    }

    // Rimuoviamo il NavigationDrawer wrapper - ora usiamo solo il Scaffold
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MaterialTheme.colorScheme.background),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AnimatedHomeTopBar(
                    scrollBehavior = scrollBehavior,
                    onMenuClicked = onMenuClicked,
                    dateIsSelected = dateIsSelected,
                    onDateSelected = onDateSelected,
                    onDateReset = onDateReset,
                    userProfileImageUrl = userProfileImageUrl,
                    screenState = screenState
                )
            },
            content = { paddingValues ->
                // Main content with proper state handling
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AnimatedContent(
                        targetState = screenState,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) with
                                    fadeOut(animationSpec = tween(200))
                        },
                        contentAlignment = Alignment.Center,
                        label = "HomeContentTransition"
                    ) { state ->
                        when (state) {
                            is HomeScreenState.Loading -> {
                                LoadingContent()
                            }
                            is HomeScreenState.Ready -> {
                                if (showUnifiedView) {
                                    // New unified content view
                                    UnifiedContentView(
                                        unifiedState = unifiedState,
                                        paddingValues = paddingValues,
                                        onDiaryClick = onDiaryClicked,
                                        onChatClick = onChatClicked,
                                        onFilterChange = { filter -> 
                                            viewModel.updateFilter(filter) 
                                        },
                                        onSearchQueryChange = { query -> 
                                            viewModel.updateSearchQuery(query) 
                                        },
                                        onRefresh = { 
                                            viewModel.refreshUnifiedContent() 
                                        },
                                        onClearSearch = { 
                                            viewModel.clearSearch() 
                                        },
                                        onViewToggle = { showUnifiedView = false }
                                    )
                                } else {
                                    // Classic diary view for backward compatibility
                                    if (diaries is RequestState.Success) {
                                        MissingPermissionsComponent {
                                            HomeContent(
                                                paddingValues = paddingValues,
                                                diaryNotes = diaries.data,
                                                onClick = navigateToWriteWithArgs,
                                                isLoading = isLoading,
                                                viewModel = viewModel
                                            )
                                        }
                                    }
                                }
                            }
                            is HomeScreenState.Error -> {
                                ErrorContent(
                                    message = state.message,
                                    onRetry = { viewModel.fetchDiaries() }
                                )
                            }
                        }
                    }
                }
            }
        )

        // Multiple FABs - Explicitly positioned at bottom right
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.End,  // Allineamento esplicito a destra
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // LiveChat FAB - sopra tutti gli altri FAB
                AnimatedVisibility(
                    visible = screenState is HomeScreenState.Ready,
                    enter = scaleIn(
                        initialScale = 0.3f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(),
                    exit = scaleOut(targetScale = 0.3f) + fadeOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = navigateToLiveChat,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Live Chat",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Chat FAB - secondo FAB
                AnimatedVisibility(
                    visible = screenState is HomeScreenState.Ready,
                    enter = scaleIn(
                        initialScale = 0.3f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + fadeIn(),
                    exit = scaleOut(targetScale = 0.3f) + fadeOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = navigateToChat,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(48.dp)  // Dimensione esplicita per il FAB piccolo
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = "AI Chat",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Write FAB - sotto il Chat FAB
                AnimatedVisibility(
                    visible = screenState is HomeScreenState.Ready,
                    enter = scaleIn(
                        initialScale = 0.3f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                            visibilityThreshold = 0.1f
                        )
                    ) + fadeIn(),
                    exit = scaleOut(targetScale = 0.3f) + fadeOut()
                ) {
                    ExtendedFloatingActionButton(
                        onClick = navigateToWrite,
                        expanded = fabExpanded && !isLoading,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "New Diary",
                                modifier = Modifier.animateContentSize()
                            )
                        },
                        text = {
                            AnimatedVisibility(
                                visible = fabExpanded && !isLoading,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                Text("Write")
                            }
                        },
                        modifier = Modifier
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedFAB(
    onClick: () -> Unit,
    expanded: Boolean
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "New Diary",
                modifier = Modifier.animateContentSize()
            )
        },
        text = {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text("Write")
            }
        },
        modifier = Modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            .padding(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedHomeTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onMenuClicked: () -> Unit,
    dateIsSelected: Boolean,
    onDateSelected: (ZonedDateTime) -> Unit,
    onDateReset: () -> Unit,
    userProfileImageUrl: String?,
    screenState: HomeScreenState
) {
    val topBarAlpha by animateFloatAsState(
        targetValue = if (screenState is HomeScreenState.Ready) 1f else 0.7f,
        animationSpec = tween(300),
        label = "TopBarAlpha"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { alpha = topBarAlpha }
    ) {
        HomeTopBar(
            scrollBehavior = scrollBehavior,
            onMenuClicked = onMenuClicked,
            dateIsSelected = dateIsSelected,
            onDateSelected = onDateSelected,
            onDateReset = onDateReset,
            userProfileImageUrl = userProfileImageUrl
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Custom loading animation with diary theme
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )

            Icon(
                imageVector = Icons.Default.List,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { rotationZ = rotation },
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Loading your memories...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
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
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = "Error",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Unable to load diaries",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}

/**
 * New unified content view that combines diary entries and chat sessions
 */
@Composable
private fun UnifiedContentView(
    unifiedState: UnifiedHomeState,
    paddingValues: PaddingValues,
    onDiaryClick: (HomeContentItem.DiaryItem) -> Unit,
    onChatClick: (HomeContentItem.ChatItem) -> Unit,
    onFilterChange: (ContentFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onClearSearch: () -> Unit,
    onViewToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            bottom = paddingValues.calculateBottomPadding() + 80.dp // Extra padding for FAB
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search Bar
        item("search") {
            UnifiedSearchBar(
                query = unifiedState.searchQuery,
                onQueryChange = onSearchQueryChange,
                onClearSearch = onClearSearch,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Filter Chips
        item("filters") {
            FilterChipRow(
                selectedFilter = unifiedState.selectedFilter,
                onFilterSelected = onFilterChange,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // View Toggle Button
        item("view_toggle") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onViewToggle
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewAgenda,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Classic View")
                }
            }
        }

        // Loading State
        if (unifiedState.isLoading && unifiedState.items.isEmpty()) {
            items(5) {
                // Skeleton loading cards
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(120.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
                    )
                ) {
                    // Skeleton content would go here
                }
            }
        }

        // Content Items
        if (unifiedState.items.isNotEmpty()) {
            items(
                items = unifiedState.items,
                key = { item -> "${item.contentType.name}-${item.id}" }
            ) { item ->
                UnifiedContentCard(
                    item = item,
                    onClick = {
                        when (item) {
                            is HomeContentItem.DiaryItem -> onDiaryClick(item)
                            is HomeContentItem.ChatItem -> onChatClick(item)
                        }
                    }
                )
            }
        }

        // Empty State
        if (!unifiedState.isLoading && unifiedState.isEmpty) {
            item("empty_state") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Article,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "No content yet",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Start by writing a diary entry or having a chat",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Error State
        if (unifiedState.error != null) {
            item("error_state") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = unifiedState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(
                            onClick = onRefresh
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}