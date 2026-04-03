package com.lifo.app

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import coil3.compose.rememberAsyncImagePainter
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
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
import com.lifo.humanoid.api.asHumanoidController
import com.lifo.humanoid.lipsync.LipSyncController
import com.lifo.humanoid.presentation.HumanoidScreen
import com.lifo.humanoid.presentation.HumanoidViewModel
import com.lifo.insight.InsightScreen
import com.lifo.onboarding.OnboardingScreen
import com.lifo.profile.ProfileDashboard
import com.lifo.settings.navigation.SettingsAiPreferencesRouteContent
import com.lifo.settings.navigation.SettingsGoalsRouteContent
import com.lifo.settings.navigation.SettingsHealthInfoRouteContent
import com.lifo.settings.navigation.SettingsLifestyleRouteContent
import com.lifo.settings.navigation.SettingsMainRouteContent
import com.lifo.settings.navigation.SettingsPersonalInfoRouteContent
import com.lifo.ui.components.DisplayAlertDialog
import com.lifo.ui.components.navigation.NavigationDestination
import com.lifo.util.model.RequestState
import com.lifo.util.repository.MongoRepository
import com.lifo.write.navigation.ActivityGardenRouteContent
import com.lifo.write.navigation.GardenRouteContent
import com.lifo.write.navigation.JournalHomeRouteContent
import com.lifo.write.navigation.WriteRouteContent
import com.lifo.feed.FeedRouteContent
import com.lifo.composer.ComposerRouteContent
import com.lifo.socialprofile.EditProfileRouteContent
import com.lifo.socialprofile.FollowListScreen
import com.lifo.socialprofile.SocialProfileRouteContent
import com.lifo.search.SearchRouteContent
import com.lifo.notifications.NotificationsRouteContent
import com.lifo.messaging.MessagingRouteContent
import com.lifo.subscription.SubscriptionRouteContent
import com.lifo.threaddetail.ThreadDetailRouteContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.lifo.util.auth.AuthProvider
import com.lifo.util.repository.FeatureFlagRepository
import com.lifo.util.repository.NotificationRepository
import com.lifo.util.repository.ProfileSettingsRepository

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

    // ── Auth state observer — kickout on sign-out/token revocation ──
    val authProvider: AuthProvider = koinInject()
    val profileSettingsRepository: ProfileSettingsRepository = koinInject()
    val featureFlagRepository: FeatureFlagRepository = koinInject()
    val featureFlags by featureFlagRepository.flags.collectAsState()
    val authUserId by authProvider.authStateFlow.collectAsState()

    LaunchedEffect(authUserId) {
        // If user becomes null while NOT on Auth screen, force navigate to Auth
        if (authUserId == null && activeDestination !is RootDestination.Auth) {
            rootComponent.navigateToAuth()
        }
    }

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
                is RootDestination.Garden,
                is RootDestination.JournalHome,
                is RootDestination.Feed,
                is RootDestination.Percorso -> true
                else -> false
            }
        }
    }

    val isHomeScreen by remember {
        derivedStateOf {
            childStack.active.configuration is RootDestination.Home
        }
    }

    // Scroll-based bottom bar hide (Instagram-style) — smooth offset animation
    var bottomBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }
    val bottomBarScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx + delta
                bottomBarOffsetHeightPx = newOffset.coerceIn(-300f, 0f)
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    // Reset bar position when switching tabs
    LaunchedEffect(activeDestination) { bottomBarOffsetHeightPx = 0f }

    // Drawer state
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // User info
    val user = auth.currentUser
    val userProfileImageUrl = user?.photoUrl?.toString()

    // ── Centralized notification badge count (collected once, passed to all screens) ──
    val notificationRepository: NotificationRepository = koinInject()
    val currentUserId = authProvider.currentUserId
    val unreadNotificationCount by remember(currentUserId) {
        if (currentUserId != null) notificationRepository.getUnreadCount(currentUserId)
        else kotlinx.coroutines.flow.flowOf(0)
    }.collectAsState(initial = 0)

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
                    onHistoryClicked = {
                        scope.launch { drawerState.close() }
                        rootComponent.navigateToHistory()
                    },
                    onAvatarClicked = {
                        scope.launch { drawerState.close() }
                        rootComponent.navigateToAvatarList()
                    },
                    onCreateAvatarClicked = {
                        scope.launch { drawerState.close() }
                        rootComponent.navigateToAvatarCreator()
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
        // Navbar + FAB as overlays outside Scaffold to avoid reserved padding
        val density = LocalDensity.current
        val navBarHeightDp = 80.dp
        val navBarHeightPx = with(density) { navBarHeightDp.toPx() }

        // Animated bottom bar offset: 0 = fully visible, navBarHeightPx = fully hidden
        val animatedBarOffset by animateFloatAsState(
            targetValue = if (bottomBarOffsetHeightPx < -navBarHeightPx / 2) navBarHeightPx else 0f,
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            label = "barOffset"
        )
        val barVisible = animatedBarOffset < navBarHeightPx

        // FAB configuration per destination
        data class FabConfig(
            val label: String,
            val icon: androidx.compose.ui.graphics.vector.ImageVector,
            val onClick: () -> Unit
        )
        val fabConfig: FabConfig? by remember {
            derivedStateOf {
                when (childStack.active.configuration) {
                    is RootDestination.Home -> FabConfig(
                        label = "Parla con Eve",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        onClick = { rootComponent.navigateToChat() }
                    )
                    is RootDestination.Feed -> FabConfig(
                        label = "Nuovo post",
                        icon = Icons.Filled.Edit,
                        onClick = { rootComponent.navigateToComposer() }
                    )
                    is RootDestination.JournalHome -> FabConfig(
                        label = "Scrivi",
                        icon = Icons.Filled.Edit,
                        onClick = { rootComponent.navigateToWrite() }
                    )
                    is RootDestination.Profile -> FabConfig(
                        label = "Parla con Eve",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        onClick = { rootComponent.navigateToChat() }
                    )
                    else -> null
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .nestedScroll(bottomBarScrollConnection)
        ) {
            // Content fills entire screen
            Children(
                stack = rootComponent.childStack,
                animation = stackAnimation(fade() + slide()),
                modifier = Modifier.fillMaxSize()
            ) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.Auth -> {
                        AuthenticationRouteContent(
                            navigateToHome = {
                                // After login, check onboarding status before going to Home
                                scope.launch {
                                    val onboardingResult = withContext(Dispatchers.IO) {
                                        profileSettingsRepository.hasCompletedOnboarding()
                                    }
                                    val hasCompleted = (onboardingResult as? RequestState.Success)?.data ?: false
                                    if (hasCompleted) {
                                        rootComponent.navigateToHome()
                                    } else {
                                        rootComponent.replaceAll(RootDestination.Onboarding)
                                    }
                                }
                            },
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
                            unreadNotificationCount = unreadNotificationCount,
                            onNotificationsClick = { rootComponent.navigateToNotifications() },
                            navigateToFeed = { rootComponent.navigateToFeed() },
                            navigateToThreadDetail = { threadId -> rootComponent.navigateToThreadDetail(threadId) },
                            navigateToSocialProfile = {
                                val userId = auth.currentUser?.uid
                                if (userId != null) rootComponent.navigateToUserProfile(userId)
                            },
                            onGratitudeClick = { rootComponent.navigateToGratitude() },
                            onEnergyCheckInClick = { rootComponent.navigateToEnergyCheckIn() },
                            onSleepLogClick = { rootComponent.navigateToSleepLog() },
                            onHabitsClick = { rootComponent.navigateToHabits() },
                            onMeditationClick = { rootComponent.navigateToMeditation() },
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
                            onNavigateToAiPreferences = { rootComponent.navigateToSettingsAiPreferences() },
                            onNavigateToEnvironment = { rootComponent.navigateToEnvironment() },
                            onNavigateToAvatarDebug = { rootComponent.navigateToAvatarDebug() },
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

                    is RootComponent.Child.SettingsAiPreferences -> {
                        SettingsAiPreferencesRouteContent(
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
                            isBrainDump = instance.isBrainDump,
                            isGratitude = instance.isGratitude,
                            isEnergyCheckIn = instance.isEnergyCheckIn,
                            isSleepLog = instance.isSleepLog,
                            isReframe = instance.isReframe,
                            isBlock = instance.isBlock,
                            isMovement = instance.isMovement,
                            navigateBack = { rootComponent.navigateBack() },
                            onShareToComposer = { prefilledContent ->
                                rootComponent.navigateBack()
                                rootComponent.navigateToComposer(prefilledContent = prefilledContent)
                            },
                            onNavigateToInsight = { id ->
                                rootComponent.navigateToInsight(id)
                            },
                            onNavigateToBrainDump = {
                                rootComponent.navigateBack()
                                rootComponent.navigateToBrainDump()
                            },
                            onNavigateToReframing = {
                                rootComponent.navigateBack()
                                rootComponent.navigateToReframe()
                            },
                            onNavigateToMeditation = {
                                rootComponent.navigateBack()
                                rootComponent.navigateToMeditation()
                            },
                        )
                    }

                    is RootComponent.Child.Chat -> {
                        ChatScreen(
                            navigateBack = { rootComponent.navigateBack() },
                            navigateToWriteWithContent = { _ ->
                                rootComponent.navigateToWrite()
                            },
                            navigateToLiveScreen = { rootComponent.navigateToLiveChat() },
                            navigateToPaywall = { rootComponent.navigateToSubscription() },
                            sessionId = instance.sessionId
                        )
                    }

                    is RootComponent.Child.LiveChat -> {
                        val humanoidViewModel: HumanoidViewModel = koinViewModel()
                        val lipSyncController: LipSyncController = koinInject()
                        val humanoidController = remember {
                            humanoidViewModel.asHumanoidController(lipSyncController)
                        }
                        LiveScreen(
                            onClose = { rootComponent.navigateBack() },
                            showAvatar = true,
                            avatarContent = {
                                HumanoidAvatarView(
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = humanoidViewModel
                                )
                            },
                            speechAnimationTarget = humanoidController,
                            gestureAnimationCallback = { animationName ->
                                humanoidController.playAnimationByName(animationName)
                            }
                        )
                    }

                    is RootComponent.Child.JournalHome -> {
                        JournalHomeRouteContent(
                            onWriteClick = { rootComponent.navigateToWrite() },
                            onBrainDumpClick = { rootComponent.navigateToBrainDump() },
                            onGratitudeClick = { rootComponent.navigateToGratitude() },
                            onDiaryClick = { diaryId -> rootComponent.navigateToWrite(diaryId) },
                            onInsightClick = { diaryId -> rootComponent.navigateToInsight(diaryId) },
                            onMenuClicked = {
                                scope.launch {
                                    if (!drawerState.isAnimationRunning && drawerState.isClosed) {
                                        drawerState.open()
                                    }
                                }
                            },
                            unreadNotificationCount = unreadNotificationCount,
                            onNotificationsClick = { rootComponent.navigateToNotifications() },
                            onEnergyClick = { rootComponent.navigateToEnergyCheckIn() },
                            onSleepClick = { rootComponent.navigateToSleepLog() },
                            onHabitsClick = { rootComponent.navigateToHabits() },
                            onMeditationClick = { rootComponent.navigateToMeditation() },
                            onMovementClick = { rootComponent.navigateToMovement() },
                            onAllActivitiesClick = { rootComponent.navigateToActivityGarden() },
                        )
                    }

                    is RootComponent.Child.Profile -> {
                        ProfileDashboard()
                    }

                    is RootComponent.Child.Humanoid -> {
                        HumanoidScreen(
                            navigateBack = { rootComponent.navigateBack() },
                            onCreateAvatar = { rootComponent.navigateToAvatarCreator() },
                            avatarId = instance.avatarId,
                        )
                    }

                    // === Social Features (Wave 7) ===

                    is RootComponent.Child.Feed -> {
                        if (!featureFlags.socialEnabled || !featureFlags.feedEnabled) {
                            FeatureUnavailableScreen(onBack = { rootComponent.navigateBack() })
                        } else FeedRouteContent(
                            onThreadClick = { threadId -> rootComponent.navigateToThreadDetail(threadId) },
                            onUserClick = { userId -> rootComponent.navigateToUserProfile(userId) },
                            onComposeClick = { rootComponent.navigateToComposer() },
                            onReplyClick = { threadId, authorName ->
                                rootComponent.navigateToComposer(
                                    parentThreadId = threadId,
                                    replyToAuthorName = authorName,
                                )
                            },
                            onMenuClicked = {
                                scope.launch {
                                    if (!drawerState.isAnimationRunning && drawerState.isClosed) {
                                        drawerState.open()
                                    }
                                }
                            },
                            unreadNotificationCount = unreadNotificationCount,
                            onSearchClick = { rootComponent.navigateToSearch() },
                            onNotificationsClick = { rootComponent.navigateToNotifications() },
                            onMessagingClick = { rootComponent.navigateToMessaging() },
                        )
                    }

                    is RootComponent.Child.UserProfile -> {
                        if (!featureFlags.socialEnabled) {
                            FeatureUnavailableScreen(onBack = { rootComponent.navigateBack() })
                        } else SocialProfileRouteContent(
                            userId = instance.userId,
                            onNavigateBack = { rootComponent.navigateBack() },
                            onThreadClick = { threadId -> rootComponent.navigateToThreadDetail(threadId) },
                            onEditProfileClick = { userId -> rootComponent.navigateToEditProfile(userId) },
                            onFollowersClick = { userId -> rootComponent.navigateToFollowList(userId, showFollowers = true) },
                            onFollowingClick = { userId -> rootComponent.navigateToFollowList(userId, showFollowers = false) },
                        )
                    }

                    is RootComponent.Child.EditProfile -> {
                        if (!featureFlags.socialEnabled) {
                            FeatureUnavailableScreen(onBack = { rootComponent.navigateBack() })
                        } else EditProfileRouteContent(
                            userId = instance.userId,
                            onNavigateBack = { rootComponent.navigateBack() },
                        )
                    }

                    is RootComponent.Child.FollowList -> {
                        if (!featureFlags.socialEnabled) {
                            FeatureUnavailableScreen(onBack = { rootComponent.navigateBack() })
                        } else FollowListScreen(
                            userId = instance.userId,
                            showFollowers = instance.showFollowers,
                            onNavigateBack = { rootComponent.navigateBack() },
                            onUserClick = { userId -> rootComponent.navigateToUserProfile(userId) },
                        )
                    }

                    is RootComponent.Child.Composer -> {
                        if (!featureFlags.socialEnabled || !featureFlags.feedEnabled) {
                            FeatureUnavailableScreen(onBack = { rootComponent.navigateBack() })
                        } else ComposerRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() },
                            onPostCreated = { rootComponent.navigateBack() },
                            parentThreadId = instance.parentThreadId,
                            replyToAuthorName = instance.replyToAuthorName,
                            prefilledContent = instance.prefilledContent,
                        )
                    }

                    is RootComponent.Child.Search -> {
                        if (!featureFlags.socialEnabled) {
                            FeatureUnavailableScreen(onBack = { rootComponent.navigateBack() })
                        } else SearchRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() },
                            onThreadClick = { threadId -> rootComponent.navigateToThreadDetail(threadId) },
                            onUserClick = { userId -> rootComponent.navigateToUserProfile(userId) },
                        )
                    }

                    is RootComponent.Child.Notifications -> {
                        if (!featureFlags.socialEnabled) {
                            FeatureUnavailableScreen(onBack = { rootComponent.navigateBack() })
                        } else NotificationsRouteContent(
                            onNavigateBack = { rootComponent.navigateBack() },
                            onThreadClick = { threadId -> rootComponent.navigateToThreadDetail(threadId) },
                            onUserClick = { userId -> rootComponent.navigateToUserProfile(userId) },
                        )
                    }

                    // === Social Features (Wave 8) ===

                    is RootComponent.Child.ThreadDetail -> {
                        if (!featureFlags.socialEnabled) {
                            FeatureUnavailableScreen(onBack = { rootComponent.navigateBack() })
                        } else ThreadDetailRouteContent(
                            threadId = instance.threadId,
                            onNavigateBack = { rootComponent.navigateBack() },
                            onUserClick = { userId -> rootComponent.navigateToUserProfile(userId) },
                            onThreadClick = { threadId -> rootComponent.navigateToThreadDetail(threadId) },
                        )
                    }

                    is RootComponent.Child.Messaging -> {
                        if (!featureFlags.socialEnabled || !featureFlags.messagingEnabled) {
                            FeatureUnavailableScreen(onBack = { rootComponent.navigateBack() })
                        } else MessagingRouteContent(
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

                    // === Avatar System (Wave 10) ===

                    is RootComponent.Child.AvatarCreator -> {
                        val avatarCreatorViewModel: com.lifo.avatarcreator.presentation.AvatarCreatorViewModel = koinViewModel()
                        com.lifo.avatarcreator.presentation.AvatarCreatorScreen(
                            viewModel = avatarCreatorViewModel,
                            onNavigateBack = { rootComponent.navigateBack() },
                            onAvatarCreated = { avatarId ->
                                rootComponent.navigateBack()
                            },
                        )
                    }

                    is RootComponent.Child.AvatarList -> {
                        val avatarListViewModel: com.lifo.avatarcreator.presentation.AvatarListViewModel = koinViewModel()
                        com.lifo.avatarcreator.presentation.AvatarListScreen(
                            viewModel = avatarListViewModel,
                            onNavigateBack = { rootComponent.navigateBack() },
                            onCreateAvatar = { rootComponent.navigateToAvatarCreator() },
                            onAvatarSelected = { avatarId ->
                                rootComponent.navigateToHumanoid(avatarId = avatarId)
                            },
                        )
                    }

                    // === Holistic Growth (Sprint 1+2) ===

                    is RootComponent.Child.Habits -> {
                        com.lifo.habits.HabitRouteContent(
                            navigateBack = { rootComponent.navigateBack() },
                        )
                    }

                    is RootComponent.Child.Meditation -> {
                        com.lifo.meditation.MeditationRouteContent(
                            navigateBack = { rootComponent.navigateBack() },
                        )
                    }

                    is RootComponent.Child.Environment -> {
                        com.lifo.settings.navigation.EnvironmentRouteContent(
                            navigateBack = { rootComponent.navigateBack() },
                        )
                    }

                    is RootComponent.Child.Dashboard -> {
                        val dashboardVm: com.lifo.write.DashboardViewModel = koinViewModel(key = "dashboard_vm")
                        val dashboardState by dashboardVm.state.collectAsState()
                        com.lifo.write.DashboardScreen(
                            state = dashboardState,
                            onBackPressed = { rootComponent.navigateBack() },
                        )
                    }

                    is RootComponent.Child.RecurringThoughts -> {
                        val rtVm: com.lifo.write.RecurringThoughtsViewModel = koinViewModel(key = "recurring_thoughts_vm")
                        val rtState by rtVm.state.collectAsState()
                        com.lifo.write.RecurringThoughtsScreen(
                            state = rtState,
                            onIntent = rtVm::onIntent,
                            onBackPressed = { rootComponent.navigateBack() },
                            onNavigateToReframe = { rootComponent.navigateToReframe() },
                        )
                    }

                    is RootComponent.Child.Values -> {
                        val valuesVm: com.lifo.write.ValuesViewModel = koinViewModel(key = "values_vm")
                        val valuesState by valuesVm.state.collectAsState()
                        com.lifo.write.ValuesScreen(
                            state = valuesState,
                            onIntent = valuesVm::onIntent,
                            onBackPressed = { rootComponent.navigateBack() },
                        )
                    }

                    is RootComponent.Child.Ikigai -> {
                        val ikigaiVm: com.lifo.write.IkigaiViewModel = koinViewModel(key = "ikigai_vm")
                        val ikigaiState by ikigaiVm.state.collectAsState()
                        com.lifo.write.IkigaiScreen(
                            state = ikigaiState,
                            onIntent = ikigaiVm::onIntent,
                            onBackPressed = { rootComponent.navigateBack() },
                        )
                    }

                    is RootComponent.Child.Awe -> {
                        val aweVm: com.lifo.write.AweViewModel = koinViewModel(key = "awe_vm")
                        val aweState by aweVm.state.collectAsState()
                        com.lifo.write.AweScreen(
                            state = aweState,
                            onIntent = aweVm::onIntent,
                            onBackPressed = { rootComponent.navigateBack() },
                        )
                    }

                    is RootComponent.Child.Silence -> {
                        val silenceVm: com.lifo.write.SilenceViewModel = koinViewModel(key = "silence_vm")
                        val silenceState by silenceVm.state.collectAsState()
                        com.lifo.write.SilenceScreen(
                            state = silenceState,
                            onIntent = silenceVm::onIntent,
                            onBackPressed = { rootComponent.navigateBack() },
                        )
                    }
                    is RootComponent.Child.Connection -> {
                        val connectionVm: com.lifo.write.ConnectionViewModel = koinViewModel(key = "connection_vm")
                        val connectionState by connectionVm.state.collectAsState()
                        com.lifo.write.ConnectionScreen(
                            state = connectionState,
                            onIntent = connectionVm::onIntent,
                            navigateBack = { rootComponent.navigateBack() },
                        )
                    }
                    is RootComponent.Child.Inspiration -> {
                        val inspirationVm: com.lifo.write.InspirationViewModel = koinViewModel(key = "inspiration_vm")
                        val inspirationState by inspirationVm.state.collectAsState()
                        com.lifo.write.InspirationScreen(
                            state = inspirationState,
                            onIntent = inspirationVm::onIntent,
                            navigateBack = { rootComponent.navigateBack() },
                        )
                    }
                    is RootComponent.Child.Percorso -> {
                        val percorsoVm: com.lifo.write.PercorsoViewModel = koinViewModel(key = "percorso_vm")
                        val percorsoState by percorsoVm.state.collectAsState()

                        // Profile data for psychological insights
                        val profileVm: com.lifo.profile.ProfileViewModel = koinViewModel(key = "percorso_profile_vm")
                        val profileUiState by profileVm.uiState.collectAsState()
                        val latestProfile = (profileUiState as? com.lifo.profile.ProfileUiState.Success)?.profiles?.firstOrNull()
                        val profileChartData = (profileUiState as? com.lifo.profile.ProfileUiState.Success)?.chartData
                        val percorsoChartData = com.lifo.write.PercorsoChartData(
                            moodLine = profileChartData?.moodLine ?: emptyList(),
                            weekLabels = profileChartData?.weekLabels ?: emptyList(),
                        )

                        com.lifo.write.PercorsoScreen(
                            state = percorsoState,
                            onIntent = percorsoVm::onIntent,
                            onMenuClicked = {
                                scope.launch {
                                    if (!drawerState.isAnimationRunning && drawerState.isClosed) {
                                        drawerState.open()
                                    }
                                }
                            },
                            unreadNotificationCount = unreadNotificationCount,
                            onNotificationsClick = { rootComponent.navigateToNotifications() },
                            latestProfile = latestProfile,
                            chartData = percorsoChartData,
                            profileLoading = profileUiState is com.lifo.profile.ProfileUiState.Loading,
                            onProfileRetry = { profileVm.refresh() },
                            onMeditationClick = { rootComponent.navigateToMeditation() },
                            onReframeClick = { rootComponent.navigateToReframe() },
                            onBlockClick = { rootComponent.navigateToBlock() },
                            onRecurringThoughtsClick = { rootComponent.navigateToRecurringThoughts() },
                            onEnergyCheckInClick = { rootComponent.navigateToEnergyCheckIn() },
                            onSleepLogClick = { rootComponent.navigateToSleepLog() },
                            onMovementClick = { rootComponent.navigateToMovement() },
                            onValuesClick = { rootComponent.navigateToValues() },
                            onIkigaiClick = { rootComponent.navigateToIkigai() },
                            onAweClick = { rootComponent.navigateToAwe() },
                            onSilenceClick = { rootComponent.navigateToSilence() },
                            onConnectionClick = { rootComponent.navigateToConnection() },
                            onInspirationClick = { rootComponent.navigateToInspiration() },
                            onHabitsClick = { rootComponent.navigateToHabits() },
                            onEnvironmentClick = { rootComponent.navigateToEnvironment() },
                            onDashboardClick = { rootComponent.navigateToDashboard() },
                        )
                    }

                    is RootComponent.Child.ActivityGarden -> {
                        ActivityGardenRouteContent(
                            onBackPressed = { rootComponent.navigateBack() },
                            onMenuClicked = {
                                scope.launch {
                                    if (!drawerState.isAnimationRunning && drawerState.isClosed) {
                                        drawerState.open()
                                    }
                                }
                            },
                            onNotificationsClick = { rootComponent.navigateToNotifications() },
                            onWriteClick = { rootComponent.navigateToWrite() },
                            onBrainDumpClick = { rootComponent.navigateToBrainDump() },
                            onGratitudeClick = { rootComponent.navigateToGratitude() },
                            onMeditationClick = { rootComponent.navigateToMeditation() },
                            onReframeClick = { rootComponent.navigateToReframe() },
                            onBlockClick = { rootComponent.navigateToBlock() },
                            onRecurringThoughtsClick = { rootComponent.navigateToRecurringThoughts() },
                            onEnergyClick = { rootComponent.navigateToEnergyCheckIn() },
                            onSleepClick = { rootComponent.navigateToSleepLog() },
                            onMovementClick = { rootComponent.navigateToMovement() },
                            onDashboardClick = { rootComponent.navigateToDashboard() },
                            onValuesClick = { rootComponent.navigateToValues() },
                            onIkigaiClick = { rootComponent.navigateToIkigai() },
                            onAweClick = { rootComponent.navigateToAwe() },
                            onSilenceClick = { rootComponent.navigateToSilence() },
                            onConnectionClick = { rootComponent.navigateToConnection() },
                            onInspirationClick = { rootComponent.navigateToInspiration() },
                            onHabitsClick = { rootComponent.navigateToHabits() },
                            onEnvironmentClick = { rootComponent.navigateToEnvironment() },
                        )
                    }

                    is RootComponent.Child.Garden -> {
                        GardenRouteContent(
                            onMenuClicked = {
                                scope.launch {
                                    if (!drawerState.isAnimationRunning && drawerState.isClosed) {
                                        drawerState.open()
                                    }
                                }
                            },
                            unreadNotificationCount = unreadNotificationCount,
                            onNotificationsClick = { rootComponent.navigateToNotifications() },
                            onActivityClick = { activityId ->
                                when (activityId) {
                                    "diary" -> rootComponent.navigateToWrite()
                                    "braindump" -> rootComponent.navigateToBrainDump()
                                    "gratitude" -> rootComponent.navigateToGratitude()
                                    "meditation" -> rootComponent.navigateToMeditation()
                                    "reframe" -> rootComponent.navigateToReframe()
                                    "block" -> rootComponent.navigateToBlock()
                                    "recurring" -> rootComponent.navigateToRecurringThoughts()
                                    "energy" -> rootComponent.navigateToEnergyCheckIn()
                                    "sleep" -> rootComponent.navigateToSleepLog()
                                    "movement" -> rootComponent.navigateToMovement()
                                    "dashboard" -> rootComponent.navigateToDashboard()
                                    "values" -> rootComponent.navigateToValues()
                                    "ikigai" -> rootComponent.navigateToIkigai()
                                    "awe" -> rootComponent.navigateToAwe()
                                    "silence" -> rootComponent.navigateToSilence()
                                    "connection" -> rootComponent.navigateToConnection()
                                    "inspiration" -> rootComponent.navigateToInspiration()
                                    "habits" -> rootComponent.navigateToHabits()
                                    "environment" -> rootComponent.navigateToEnvironment()
                                }
                            },
                        )
                    }

                    is RootComponent.Child.AvatarDebug -> {
                        com.lifo.humanoid.debug.AvatarDebugScreen(
                            onClose = { rootComponent.navigateBack() }
                        )
                    }
                }
            }

            // ── Bottom Navigation Bar (overlay) ──
            if (shouldShowBottomBar) {
                val onDestinationClickStable = remember(rootComponent) { { dest: NavigationDestination ->
                    when (dest) {
                        is NavigationDestination.Home -> rootComponent.switchTab(RootDestination.Home)
                        is NavigationDestination.Garden -> rootComponent.switchTab(RootDestination.Garden)
                        is NavigationDestination.Journal -> rootComponent.switchTab(RootDestination.JournalHome)
                        is NavigationDestination.Community -> rootComponent.switchTab(RootDestination.Feed)
                        is NavigationDestination.Journey -> rootComponent.switchTab(RootDestination.Percorso)
                        else -> {}
                    }
                } }
                val destinations = remember {
                    listOf(
                        NavigationDestination.Home,
                        NavigationDestination.Garden,
                        NavigationDestination.Journal,
                        NavigationDestination.Community,
                        NavigationDestination.Journey,
                    )
                }
                DecomposeBottomBar(
                    activeDestination = activeDestination,
                    onDestinationClick = onDestinationClickStable,
                    destinations = destinations,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(x = 0, y = animatedBarOffset.roundToInt()) }
                )
            }

            // ── Contextual FAB (overlay) — offset synced with navbar animation ──
            fabConfig?.let { config ->
                val fabGapPx = with(density) { 24.dp.toPx() } // M3 standard gap above nav bar
                val fabBottomOffset = navBarHeightPx - animatedBarOffset + fabGapPx

                // Text appears for 1 second on first show, then collapses (one-shot)
                var fabExpanded by remember { mutableStateOf(true) }
                var fabTextShown by remember { mutableStateOf(false) }
                LaunchedEffect(config.label) {
                    if (!fabTextShown) {
                        fabExpanded = true
                        kotlinx.coroutines.delay(1500)
                        fabExpanded = false
                        fabTextShown = true
                    }
                }

                val useLogo = config.label == "Parla con Eve"

                ExtendedFloatingActionButton(
                    onClick = config.onClick,
                    expanded = fabExpanded,
                    icon = {
                        if (useLogo) {
                            Image(
                                painter = painterResource(id = com.lifo.ui.R.mipmap.calmify_logo_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(52.dp)
                            )
                        } else {
                            Icon(
                                imageVector = config.icon,
                                contentDescription = null
                            )
                        }
                    },
                    text = { Text(config.label) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset { IntOffset(x = -(with(density) { 16.dp.toPx() }).roundToInt(), y = -fabBottomOffset.roundToInt()) }
                )
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
                                    // Sign out after deleting all data
                                    auth.signOut()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Account eliminato. Tutti i dati sono stati cancellati.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        // AuthStateListener will auto-navigate to Auth
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
 * Placeholder screen shown when a feature is disabled via feature flags.
 * Displays a message and a back button so the user can navigate away.
 */
@Composable
private fun FeatureUnavailableScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "Funzionalità non ancora disponibile",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Text(
                text = "Questa sezione sarà disponibile a breve.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
            OutlinedButton(onClick = onBack) {
                Text("Torna indietro")
            }
        }
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
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        destinations.forEach { destination ->
            val selected = when (destination) {
                is NavigationDestination.Home -> activeDestination is RootDestination.Home
                is NavigationDestination.Garden -> activeDestination is RootDestination.Garden
                is NavigationDestination.Journal -> activeDestination is RootDestination.JournalHome
                is NavigationDestination.AIChat -> activeDestination is RootDestination.Chat
                is NavigationDestination.Journey -> activeDestination is RootDestination.Percorso
                is NavigationDestination.Community -> activeDestination is RootDestination.Feed
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
    onHistoryClicked: () -> Unit,
    onAvatarClicked: () -> Unit,
    onCreateAvatarClicked: () -> Unit,
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
                        painter = painterResource(id = com.lifo.ui.R.mipmap.calmify_logo_foreground),
                        contentDescription = "Calmify Logo",
                        modifier = Modifier.size(48.dp)
                    )
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
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null
                )
            },
            label = { Text("Storico") },
            selected = false,
            onClick = onHistoryClicked,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null
                )
            },
            label = { Text("Avatar") },
            selected = false,
            onClick = onAvatarClicked,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null
                )
            },
            label = { Text("Crea Avatar") },
            selected = false,
            onClick = onCreateAvatarClicked,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

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
        route == "humanoid_screen" -> RootDestination.Humanoid()
        route == "wellbeing_snapshot_screen" -> RootDestination.WellbeingSnapshot
        route.startsWith("insight_screen?diaryId=") -> {
            val diaryId = route.removePrefix("insight_screen?diaryId=")
            RootDestination.Insight(diaryId = diaryId)
        }
        route == "journal_home_screen" -> RootDestination.JournalHome
        // Social deep links
        route == "feed_screen" -> RootDestination.Feed
        route == "composer_screen" -> RootDestination.Composer()
        route == "search_screen" -> RootDestination.Search
        route == "notifications_screen" -> RootDestination.Notifications
        route.startsWith("user_profile_screen?userId=") -> {
            val userId = route.removePrefix("user_profile_screen?userId=")
            RootDestination.UserProfile(userId = userId)
        }
        route.startsWith("thread_detail_screen?threadId=") -> {
            val threadId = route.removePrefix("thread_detail_screen?threadId=")
            RootDestination.ThreadDetail(threadId = threadId)
        }
        else -> {
            println("[DecomposeApp] Unknown deep link route: $route")
            null
        }
    }
}
