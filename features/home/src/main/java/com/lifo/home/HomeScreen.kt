package com.lifo.home

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import com.lifo.mongo.repository.Diaries
import com.lifo.ui.components.loading.*
import com.lifo.util.model.RequestState
import kotlinx.coroutines.delay
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
    userProfileImageUrl: String?
) {
    // Collect states
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // Determine screen state - don't use rememberSaveable for sealed class
    val screenState = remember(diaries) {
        when (diaries) {
            is RequestState.Loading -> HomeScreenState.Loading
            is RequestState.Error -> HomeScreenState.Error(diaries.error.message ?: "Unknown error")
            is RequestState.Success -> HomeScreenState.Ready(isEmpty = diaries.data.isEmpty())
            else -> HomeScreenState.Loading
        }
    }

    // FAB expansion state - this can be saved as it's a boolean
    var fabExpanded by rememberSaveable { mutableStateOf(true) }

    // Use enterAlwaysScrollBehavior for consistent appearance
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Track scroll for FAB expansion
    LaunchedEffect(scrollBehavior.state.heightOffset) {
        fabExpanded = scrollBehavior.state.heightOffset > -50
    }

    NavigationDrawer(
        drawerState = drawerState,
        onSignOutClicked = onSignOutClicked,
        onDeleteAllClicked = onDeleteAllClicked,
        userProfileImageUrl = userProfileImageUrl
    ) {
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

            // FAB posizionato sopra lo Scaffold per evitare clipping dell'ombra
            AnimatedVisibility(
                visible = screenState is HomeScreenState.Ready,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp) // Padding generoso per l'ombra
            ) {
                AnimatedFAB(
                    onClick = navigateToWrite,
                    expanded = fabExpanded && !isLoading
                )
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
            .padding(16.dp) // Aggiungi qui il padding
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
 * Enhanced navigation drawer with animations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavigationDrawer(
    drawerState: DrawerState,
    onSignOutClicked: () -> Unit,
    onDeleteAllClicked: () -> Unit,
    userProfileImageUrl: String?,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.85f),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                // Animated drawer content
                val drawerContentAlpha by animateFloatAsState(
                    targetValue = if (drawerState.isOpen) 1f else 0f,
                    animationSpec = tween(300),
                    label = "DrawerAlpha"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = drawerContentAlpha }
                ) {
                    // Header with user info
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // App Logo/Name with animation
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 24.dp)
                            ) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = scaleIn() + fadeIn()
                                ) {
                                    Image(
                                        painter = painterResource(id = com.lifo.ui.R.drawable.logo_calmify),
                                        contentDescription = "Calmify Logo",
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Calmify",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }

                            // User Profile with animation
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .animateContentSize()
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            model = userProfileImageUrl,
                                            error = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic),
                                            placeholder = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic)
                                        ),
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Text(
                                        text = "Welcome back!",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Manage your account",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Navigation Items with stagger animation
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                painter = painterResource(id = com.lifo.ui.R.drawable.google_logo_ic),
                                contentDescription = null
                            )
                        },
                        label = { Text("Sign Out") },
                        selected = false,
                        onClick = onSignOutClicked,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null
                            )
                        },
                        label = { Text("Delete All Diaries") },
                        selected = false,
                        onClick = onDeleteAllClicked,
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Footer
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        },
        content = content
    )
}