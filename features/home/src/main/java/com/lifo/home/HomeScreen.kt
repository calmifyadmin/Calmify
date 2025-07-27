package com.lifo.home

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.lifo.mongo.repository.Diaries
import com.lifo.ui.components.loading.*
import com.lifo.util.model.RequestState
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
    navigateToChat: () -> Unit
) {
    // Collect states
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

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
                // Chat FAB - sopra il Write FAB
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