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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lifo.home.components.*
import com.lifo.util.repository.Diaries
import com.lifo.ui.components.loading.*
import com.lifo.util.model.RequestState
import com.lifo.util.model.HomeContentItem
import com.lifo.util.auth.AuthProvider
import com.lifo.util.repository.NotificationRepository
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject
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
    navController: NavHostController? = null,
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
    navigateToExistingChat: (String) -> Unit,
    navigateToLiveScreen: () -> Unit,
    navigateToWellbeingSnapshot: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    navigateToFeed: () -> Unit = {},
    navigateToThreadDetail: (String) -> Unit = {},
    // New unified content navigation parameters
    onDiaryClicked: (HomeContentItem.DiaryItem) -> Unit = { navigateToWriteWithArgs(it.id) },
    onChatClicked: (HomeContentItem.ChatItem) -> Unit = { navigateToExistingChat(it.id) },
    navigateToSocialProfile: () -> Unit = {},
) {
    // Use enterAlwaysScrollBehavior for consistent appearance
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .background(MaterialTheme.colorScheme.surface),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            MinimalHomeTopBar(
                scrollBehavior = scrollBehavior,
                onMenuClicked = onMenuClicked,
                onNotificationsClick = onNotificationsClick,
                userProfileImageUrl = userProfileImageUrl
            )
        },
        // FAB rimosso - gestito a livello Scaffold principale in CalmifyApp
        content = { paddingValues ->
            // Observe state and show appropriate content
            val isLoading by viewModel.isLoading.collectAsState()

            when (diaries) {
                is RequestState.Success -> {
                    HomeContent(
                        paddingValues = paddingValues,
                        diaryNotes = diaries.data,
                        onClick = navigateToWriteWithArgs,
                        isLoading = isLoading,
                        viewModel = viewModel,
                        navigateToWellbeingSnapshot = navigateToWellbeingSnapshot,
                        navigateToWrite = navigateToWrite,
                        navigateToLive = navigateToLiveScreen,
                        navigateToFeed = navigateToFeed,
                        navigateToThreadDetail = navigateToThreadDetail,
                        navigateToSocialProfile = navigateToSocialProfile,
                    )
                }
                is RequestState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Error loading diaries",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = diaries.error.message ?: "Unknown error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is RequestState.Loading -> {
                    GoogleStyleLoadingIndicator(
                        loadingState = LoadingState.Loading("Loading your memories..."),
                        style = LoadingStyle.Skeleton,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
                else -> {
                    // Idle state - show placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    )
}

/**
 * Minimal TopBar for the Home screen — with global notification bell + badge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalHomeTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onMenuClicked: () -> Unit,
    onNotificationsClick: () -> Unit,
    userProfileImageUrl: String?
) {
    val notificationRepository: NotificationRepository = koinInject()
    val authProvider: AuthProvider = koinInject()
    val userId = authProvider.currentUserId
    val unreadCount by remember(userId) {
        if (userId != null) notificationRepository.getUnreadCount(userId)
        else flowOf(0)
    }.collectAsState(initial = 0)

    TopAppBar(
        title = {
            Text(
                text = "Calmify",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClicked) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu"
                )
            }
        },
        actions = {
            IconButton(onClick = onNotificationsClick) {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge {
                                Text(
                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifiche"
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
    )
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
