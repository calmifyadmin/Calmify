package com.lifo.app

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.google.firebase.auth.FirebaseAuth
import com.lifo.auth.navigation.AuthenticationRouteContent
import com.lifo.calmifyapp.navigation.decompose.RootComponent
import com.lifo.calmifyapp.navigation.decompose.RootDestination
import com.lifo.chat.presentation.screen.ChatScreen
import com.lifo.chat.presentation.screen.LiveScreen
import com.lifo.history.navigation.ChatHistoryFullRouteContent
import com.lifo.history.navigation.DiaryHistoryFullRouteContent
import com.lifo.history.navigation.HistoryRouteContent
import com.lifo.home.navigation.HomeRouteContent
import com.lifo.humanoid.api.HumanoidAvatarView
import com.lifo.humanoid.presentation.HumanoidScreen
import com.lifo.humanoid.presentation.HumanoidViewModel
import com.lifo.insight.InsightScreen
import com.lifo.onboarding.OnboardingScreen
import com.lifo.profile.ProfileDashboard
import com.lifo.settings.navigation.SettingsGoalsRouteContent
import com.lifo.settings.navigation.SettingsHealthInfoRouteContent
import com.lifo.settings.navigation.SettingsLifestyleRouteContent
import com.lifo.settings.navigation.SettingsMainRouteContent
import com.lifo.settings.navigation.SettingsPersonalInfoRouteContent
import com.lifo.ui.components.DisplayAlertDialog
import com.lifo.ui.components.navigation.NavigationDestination
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import com.lifo.write.navigation.WriteRouteContent
import com.lifo.feed.FeedRouteContent
import com.lifo.composer.ComposerRouteContent
import com.lifo.socialprofile.SocialProfileRouteContent
import com.lifo.search.SearchRouteContent
import com.lifo.notifications.NotificationsRouteContent
import com.lifo.messaging.MessagingRouteContent
import com.lifo.subscription.SubscriptionRouteContent
import com.lifo.util.repository.FeatureFlagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main Decompose-based app composable.
 * Replaces the NavHost-based CalmifyApp with Decompose's Children stack renderer.
 *
 * Maintains the same UI structure:
 * - ModalNavigationDrawer (outer)
 * - Scaffold with bottom bar + FAB (inner)
 * - Children stack for screen content
 *
 * All navigation is driven by RootComponent instead of NavHostController.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun DecomposeApp(
    rootComponent: RootComponent,
    repository: MongoRepository,
    auth: FirebaseAuth,
    deepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    onDataLoaded: () -> Unit = {}
) {
    val childStack by rootComponent.childStack.subscribeAsState()
    val activeChild = childStack.active.instance
    val activeDestination = childStack.active.configuration

    // Feature flags for social features
    val featureFlagRepository: FeatureFlagRepository = koinInject()
    val featureFlags by featureFlagRepository.flags.collectAsState()

    // Handle deep linking from FCM notifications
    LaunchedEffect(deepLinkRoute) {
        deepLinkRoute?.let { route ->
            println("[DecomposeApp] Navigating to deep link: $route")
            val destination = parseDeepLinkRoute(route)
            if (destination != null) {
                rootComponent.navigateTo(destination)
            }
            onDeepLinkHandled()
        }
    }

    // Determine bottom bar visibility based on active destination.
    // Matches original NavigationState.shouldShowBottomBar logic:
    // Show on Home, History, Profile, ChatHistoryFull, DiaryHistoryFull
    // Hide on Auth, Onboarding, Write, Chat, LiveChat, Settings(*), Humanoid, Insight, WellbeingSnapshot
    val shouldShowBottomBar by remember {
        derivedStateOf {
            when (childStack.active.configuration) {
                is RootDestination.Home,
                is RootDestination.History,
                is RootDestination.ChatHistoryFull,
                is RootDestination.DiaryHistoryFull,
                is RootDestination.Profile,
                is RootDestination.Feed -> true
                else -> false
            }
        }
    }

    val isHomeScreen by remember {
        derivedStateOf {
            childStack.active.configuration is RootDestination.Home
        }
    }

    // Drawer state
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // User info
    val user = auth.currentUser
    val userProfileImageUrl = user?.photoUrl?.toString()

    // Dialog states
    var signOutDialogOpened by remember { mutableStateOf(false) }
    var deleteAllDialogOpened by remember { mutableStateOf(false) }

    // Navigation Drawer wrapper
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isHomeScreen,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.85f),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                DrawerContent(
                    userProfileImageUrl = userProfileImageUrl,
                    onHeaderClicked = {
                        scope.launch { drawerState.close() }
                        rootComponent.navigateToSettings()
                    },
                    onSignOutClicked = {
                        scope.launch { drawerState.close() }
                        signOutDialogOpened = true
                    },
                    onDeleteAllClicked = {
                        scope.launch { drawerState.close() }
                        deleteAllDialogOpened = true
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AnimatedVisibility(
                    visible = shouldShowBottomBar,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(250))
                ) {
                    val onDestinationClickStable = remember(rootComponent) { { dest: NavigationDestination ->
                        when (dest) {
                            is NavigationDestination.Home -> rootComponent.replaceAll(RootDestination.Home)
                            is NavigationDestination.Feed -> rootComponent.replaceAll(RootDestination.Feed)
                            is NavigationDestination.Humanoid -> rootComponent.replaceAll(RootDestination.Humanoid)
                            is NavigationDestination.History -> rootComponent.replaceAll(RootDestination.History)
                            is NavigationDestination.Profile -> rootComponent.replaceAll(RootDestination.Profile)
                            else -> {}
                        }
                    } }
                    val destinations = remember(featureFlags.feedEnabled) {
                        buildList {
                            add(NavigationDestination.Home)
                            if (featureFlags.feedEnabled) {
                                add(NavigationDestination.Feed)
                            }
                            add(NavigationDestination.Humanoid)
                            add(NavigationDestination.History)
                            add(NavigationDestination.Profile)
                        }
                    }
                    DecomposeBottomBar(
                        activeDestination = activeDestination,
                        onDestinationClick = onDestinationClickStable,
                        destinations = destinations
                    )
                }
            },
            floatingActionButton = {
                if (isHomeScreen && shouldShowBottomBar) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Chat FAB
                        FloatingActionButton(
                            onClick = { rootComponent.navigateToChat() },
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "Start Chat",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // New Diary Extended FAB
                        ExtendedFloatingActionButton(
                            onClick = { rootComponent.navigateToWrite() },
                            text = {
                                Text(
                                    text = "New Diary",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End
        ) { paddingValues ->
            // Decompose Children stack renderer
            Children(
                stack = rootComponent.childStack,
                animation = stackAnimation(fade()),
                modifier = Modifier.padding(paddingValues)
            ) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.Auth -> {
                        AuthenticationRouteContent(
                            navigateToHome = { rootComponent.navigateToHome() },
                            onDataLoaded = onDataLoaded
                        )
                    }

                    is RootComponent.Child.Onboarding -> {
                        OnboardingScreen(
                            onComplete = { rootComponent.navigateToHome() }
                        )
                    }

                    is RootComponent.Child.Home -> {
                        HomeRouteContent(
                            navigateToWrite = { rootComponent.navigateToWrite() },
                            navigateToWriteWithArgs = { diaryId -> rootComponent.navigateToWrite(diaryId) },
                            navigateToAuth = { rootComponent.navigateToAuth() },
                            navigateToChat = { rootComponent.navigateToChat() },
                            navigateToExistingChat = { sessionId -> rootComponent.navigateToChat(sessionId) },
                            navigateToLiveScreen = { rootComponent.navigateToLiveChat() },
                            navigateToWellbeingSnapshot = { rootComponent.navigateToWellbeingSnapshot() },
                            onDataLoaded = onDataLoaded,
                            drawerState = drawerState
                        )
                    }

                    is RootComponent.Child.History -> {
                        HistoryRouteContent(
                            onChatClick = { chatId -> rootComponent.navigateToChat(chatId) },
                            onDiaryClick = { diaryId -> rootComponent.navigateToWrite(diaryId) },
                            onChatHistoryHeaderClick = { rootComponent.navigateToChatHistoryFull() },
                            onDiaryHistoryHeaderClick = { rootComponent.navigateToDiaryHistoryFull() },
                            onMenuClicked = { }
                        )
                    }

                    is RootComponent.Child.ChatHistoryFull -> {
                        ChatHistoryFullRouteContent(
                            onBackClick = { rootComponent.navigateBack() },
                            onChatClick = { chatId -> rootComponent.navigateToChat(chatId) }
                        )
                    }

                    is RootComponent.Child.DiaryHistoryFull -> {
                        DiaryHistoryFullRouteContent(
                            onBackClick = { rootComponent.navigateBack() },
                            onDiaryClick = { diaryId -> rootComponent.navigateToWrite(diaryId) },
                            onInsightClick = { diaryId -> rootComponent.navigateToInsight(diaryId) }
                        )
                    }

                    is RootComponent.Child.Settings -> {
                        SettingsMainRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() },
                            onNavigateToPersonalInfo = { rootComponent.navigateToSettingsPersonalInfo() },
                            onNavigateToHealthInfo = { rootComponent.navigateToSettingsHealthInfo() },
                            onNavigateToLifestyle = { rootComponent.navigateToSettingsLifestyle() },
                            onNavigateToGoals = { rootComponent.navigateToSettingsGoals() },
                            onLogout = { rootComponent.navigateToAuth() }
                        )
                    }

                    is RootComponent.Child.SettingsPersonalInfo -> {
                        SettingsPersonalInfoRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() }
                        )
                    }

                    is RootComponent.Child.SettingsHealthInfo -> {
                        SettingsHealthInfoRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() }
                        )
                    }

                    is RootComponent.Child.SettingsLifestyle -> {
                        SettingsLifestyleRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() }
                        )
                    }

                    is RootComponent.Child.SettingsGoals -> {
                        SettingsGoalsRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() }
                        )
                    }

                    is RootComponent.Child.WellbeingSnapshot -> {
                        com.lifo.home.SnapshotScreen(
                            onBackPressed = { rootComponent.navigateBack() },
                            onSnapshotComplete = { rootComponent.navigateBack() }
                        )
                    }

                    is RootComponent.Child.Insight -> {
                        InsightScreen(
                            diaryId = instance.diaryId,
                            onBackPressed = { rootComponent.navigateBack() },
                            onPromptClicked = { _ ->
                                rootComponent.navigateToChat()
                            }
                        )
                    }

                    is RootComponent.Child.Write -> {
                        WriteRouteContent(
                            diaryId = instance.diaryId,
                            navigateBack = { rootComponent.navigateBack() }
                        )
                    }

                    is RootComponent.Child.Chat -> {
                        ChatScreen(
                            navigateBack = { rootComponent.navigateBack() },
                            navigateToWriteWithContent = { _ ->
                                rootComponent.navigateToWrite()
                            },
                            navigateToLiveScreen = { rootComponent.navigateToLiveChat() },
                            sessionId = instance.sessionId
                        )
                    }

                    is RootComponent.Child.LiveChat -> {
                        val humanoidViewModel: HumanoidViewModel = koinViewModel()
                        LiveScreen(
                            onClose = { rootComponent.navigateBack() },
                            showAvatar = true,
                            avatarContent = {
                                HumanoidAvatarView(
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = humanoidViewModel
                                )
                            }
                        )
                    }

                    is RootComponent.Child.Profile -> {
                        ProfileDashboard()
                    }

                    is RootComponent.Child.Humanoid -> {
                        HumanoidScreen(
                            navigateBack = { rootComponent.navigateBack() }
                        )
                    }

                    // === Social Features (Wave 7) ===

                    is RootComponent.Child.Feed -> {
                        FeedRouteContent(
                            onThreadClick = { /* TODO: ThreadDetail */ },
                            onUserClick = { userId -> rootComponent.navigateToUserProfile(userId) },
                            onComposeClick = { rootComponent.navigateToComposer() },
                            onSearchClick = { rootComponent.navigateToSearch() },
                            onNotificationsClick = { rootComponent.navigateToNotifications() },
                            onMessagingClick = { rootComponent.navigateToMessaging() },
                        )
                    }

                    is RootComponent.Child.UserProfile -> {
                        SocialProfileRouteContent(
                            userId = instance.userId,
                            onNavigateBack = { rootComponent.navigateBack() },
                            onThreadClick = { /* TODO: ThreadDetail in Wave 8 */ },
                        )
                    }

                    is RootComponent.Child.Composer -> {
                        ComposerRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() },
                            onPostCreated = { rootComponent.navigateBack() },
                        )
                    }

                    is RootComponent.Child.Search -> {
                        SearchRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() },
                            onThreadClick = { /* TODO: ThreadDetail in Wave 8 */ },
                            onUserClick = { userId -> rootComponent.navigateToUserProfile(userId) },
                        )
                    }

                    is RootComponent.Child.Notifications -> {
                        NotificationsRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() },
                            onThreadClick = { /* TODO: ThreadDetail */ },
                            onUserClick = { userId -> rootComponent.navigateToUserProfile(userId) },
                        )
                    }

                    // === Social Features (Wave 8) ===

                    is RootComponent.Child.Messaging -> {
                        MessagingRouteContent(
                            conversationId = instance.conversationId,
                            onNavigateBack = { rootComponent.navigateBack() },
                            onUserClick = { userId -> rootComponent.navigateToUserProfile(userId) },
                        )
                    }

                    // === Monetization (Wave 9) ===

                    is RootComponent.Child.Subscription -> {
                        SubscriptionRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() },
                        )
                    }
                }
            }
        }
    }

    // Sign Out Dialog
    AnimatedVisibility(
        visible = signOutDialogOpened,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        DisplayAlertDialog(
            title = "Sign Out",
            message = "Are you sure you want to sign out from your Google Account? You'll need to sign in again to access your diaries.",
            dialogOpened = signOutDialogOpened,
            onDialogClosed = { signOutDialogOpened = false },
            onYesClicked = {
                signOutDialogOpened = false
                scope.launch {
                    try {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Signing out...", Toast.LENGTH_SHORT).show()
                        }
                        withContext(Dispatchers.IO) {
                            auth.signOut()
                        }
                        withContext(Dispatchers.Main) {
                            rootComponent.navigateToAuth()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Failed to sign out. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
    }

    // Delete All User Data Dialog
    AnimatedVisibility(
        visible = deleteAllDialogOpened,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        DisplayAlertDialog(
            title = "Delete All My Data",
            message = "This action cannot be undone. All your diaries and associated images will be permanently deleted.",
            dialogOpened = deleteAllDialogOpened,
            onDialogClosed = { deleteAllDialogOpened = false },
            onYesClicked = {
                deleteAllDialogOpened = false
                scope.launch {
                    try {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Deleting all your data...", Toast.LENGTH_SHORT).show()
                        }
                        withContext(Dispatchers.IO) {
                            when (val result = repository.deleteAllUserData()) {
                                is RequestState.Success -> {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "All data deleted successfully",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                is RequestState.Error -> {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            result.error.message ?: "Failed to delete data",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Error: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        )
    }
}

/**
 * Bottom navigation bar for Decompose navigation.
 * Replaces the NavController-based CalmifyBottomAppBar.
 */
@Composable
private fun DecomposeBottomBar(
    activeDestination: RootDestination,
    onDestinationClick: (NavigationDestination) -> Unit,
    destinations: List<NavigationDestination>,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        destinations.forEach { destination ->
            val selected = when (destination) {
                is NavigationDestination.Home -> activeDestination is RootDestination.Home
                is NavigationDestination.Feed -> activeDestination is RootDestination.Feed
                is NavigationDestination.Humanoid -> activeDestination is RootDestination.Humanoid
                is NavigationDestination.History -> activeDestination is RootDestination.History ||
                        activeDestination is RootDestination.ChatHistoryFull ||
                        activeDestination is RootDestination.DiaryHistoryFull
                is NavigationDestination.Profile -> activeDestination is RootDestination.Profile
                else -> false
            }

            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationClick(destination) },
                icon = {
                    val badgeText = destination.badge
                    BadgedBox(
                        badge = {
                            when {
                                badgeText != null -> {
                                    Badge {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                destination.hasNews -> {
                                    Badge(modifier = Modifier.size(6.dp))
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label
                        )
                    }
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            )
        }
    }
}

/**
 * Drawer Content Component — same as original CalmifyApp.
 */
@Composable
private fun DrawerContent(
    userProfileImageUrl: String?,
    onHeaderClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
    onDeleteAllClicked: () -> Unit
) {
    val drawerContentAlpha by animateFloatAsState(
        targetValue = 1f,
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
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Image(
                        painter = painterResource(id = com.lifo.ui.R.drawable.logo_calmify),
                        contentDescription = "Calmify Logo",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Calmify",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onHeaderClicked),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
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
            label = { Text("Delete All My Data") },
            selected = false,
            onClick = onDeleteAllClicked,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Version 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * Parse a deep link route string into a RootDestination.
 * Supports the same route strings as the old Navigation Compose routes.
 */
private fun parseDeepLinkRoute(route: String): RootDestination? {
    return when {
        route == "home_screen" -> RootDestination.Home
        route == "chat_screen" -> RootDestination.Chat()
        route.startsWith("chat_screen/") -> {
            val sessionId = route.removePrefix("chat_screen/")
            RootDestination.Chat(sessionId = sessionId)
        }
        route == "write_screen" -> RootDestination.Write()
        route.startsWith("write_screen?diaryId=") -> {
            val diaryId = route.removePrefix("write_screen?diaryId=")
            RootDestination.Write(diaryId = diaryId)
        }
        route == "history_screen" -> RootDestination.History
        route == "profile_screen" -> RootDestination.Profile
        route == "settings_screen" -> RootDestination.Settings
        route == "live_chat_screen" -> RootDestination.LiveChat
        route == "humanoid_screen" -> RootDestination.Humanoid
        route == "wellbeing_snapshot_screen" -> RootDestination.WellbeingSnapshot
        route.startsWith("insight_screen?diaryId=") -> {
            val diaryId = route.removePrefix("insight_screen?diaryId=")
            RootDestination.Insight(diaryId = diaryId)
        }
        // Social deep links
        route == "feed_screen" -> RootDestination.Feed
        route == "composer_screen" -> RootDestination.Composer
        route == "search_screen" -> RootDestination.Search
        route == "notifications_screen" -> RootDestination.Notifications
        route.startsWith("user_profile_screen?userId=") -> {
            val userId = route.removePrefix("user_profile_screen?userId=")
            RootDestination.UserProfile(userId = userId)
        }
        else -> {
            println("[DecomposeApp] Unknown deep link route: $route")
            null
        }
    }
}
