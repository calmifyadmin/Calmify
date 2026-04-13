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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifo.home.components.*
import com.lifo.util.repository.Diaries
import com.lifo.util.repository.SubscriptionRepository
import com.lifo.ui.components.CalmifyTopBar
import com.lifo.ui.components.ProBadge
import com.lifo.ui.components.loading.*
import com.lifo.util.model.RequestState
import com.lifo.util.model.HomeContentItem
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import java.time.ZonedDateTime
import org.jetbrains.compose.resources.stringResource
import com.lifo.ui.resources.Res
import com.lifo.ui.resources.*

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
    unreadNotificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    navigateToFeed: () -> Unit = {},
    navigateToThreadDetail: (String) -> Unit = {},
    // New unified content navigation parameters
    onDiaryClicked: (HomeContentItem.DiaryItem) -> Unit = { navigateToWriteWithArgs(it.id) },
    onChatClicked: (HomeContentItem.ChatItem) -> Unit = { navigateToExistingChat(it.id) },
    navigateToSocialProfile: () -> Unit = {},
    onGratitudeClick: () -> Unit = {},
    onEnergyCheckInClick: () -> Unit = {},
    onSleepLogClick: () -> Unit = {},
    onHabitsClick: () -> Unit = {},
    onMeditationClick: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val subscriptionRepository: SubscriptionRepository = koinInject()
    val isPro by remember(subscriptionRepository) {
        subscriptionRepository.observeSubscription().map {
            it.tier == SubscriptionRepository.SubscriptionTier.PRO
        }
    }.collectAsState(initial = false)

    Box(modifier = Modifier.fillMaxSize()) {

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colorScheme.background,
        topBar = {
            CalmifyTopBar(
                title = "Calmify",
                onMenuClick = onMenuClicked,
                scrollBehavior = scrollBehavior,
                trailingBadge = if (isPro) {
                    { ProBadge() }
                } else null,
                actions = {
                    IconButton(onClick = onNotificationsClick) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationCount > 0) {
                                    Badge {
                                        Text(
                                            text = if (unreadNotificationCount > 99) "99+" else unreadNotificationCount.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Notifications, contentDescription = stringResource(Res.string.notifications_cd))
                        }
                    }
                },
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
                        onGratitudeClick = onGratitudeClick,
                        onEnergyCheckInClick = onEnergyCheckInClick,
                        onSleepLogClick = onSleepLogClick,
                        onHabitsClick = onHabitsClick,
                        onMeditationClick = onMeditationClick,
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

    } // end Box
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
