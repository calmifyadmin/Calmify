package com.lifo.app

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.lifo.app.navigation.*
import com.lifo.auth.navigation.authenticationRoute
import com.lifo.chat.navigation.chatRoute
import com.lifo.chat.navigation.liveRoute
import com.lifo.chat.navigation.navigateToChat
import com.lifo.history.navigation.historyRoute
import com.lifo.home.navigation.homeRoute
import com.lifo.humanoid.api.HumanoidAvatarView
import com.lifo.humanoid.api.asHumanoidController
import com.lifo.humanoid.presentation.navigation.humanoidRoute
import com.lifo.settings.navigation.settingsRoute
import com.lifo.insight.InsightScreen
import com.lifo.mongo.repository.MongoRepository
import com.lifo.onboarding.navigation.onboardingRoute
import com.lifo.profile.ProfileDashboard
import com.lifo.ui.components.DisplayAlertDialog
import com.lifo.ui.components.navigation.CalmifyBottomAppBar
import com.lifo.ui.components.navigation.CalmifyNavigationBar
import com.lifo.ui.components.navigation.NavigationDestination
import com.lifo.util.Screen
import com.lifo.write.navigation.writeRoute
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lifo.util.model.RequestState
import kotlin.math.ln

/**
 * Composable principale dell'app con NavigationBar Material 3 e Drawer globale
 *
 * # Architettura UI con Scroll Behavior (come Gmail):
 *
 * ## NavigationBar con Scroll Behavior Custom
 * - Componente standard Material 3 per navigazione principale
 * - **Scroll behavior**: Si nasconde durante scroll down, riappare durante scroll up
 * - Implementato via `NestedScrollConnection` + `graphicsLayer.translationY`
 * - Smooth animations senza hardcoded values
 * - 3-5 destinazioni principali con icone + label
 *
 * ## Perché Custom Scroll Behavior?
 * NavigationBar di Material 3 non ha scroll behavior built-in (solo BottomAppBar ce l'ha).
 * Questo è il pattern usato dalle app Google (Gmail, Photos) per massimizzare lo spazio
 * del contenuto mantenendo la navigazione facilmente accessibile.
 *
 * ## Layout Hierarchy:
 * 1. ModalNavigationDrawer (livello più esterno)
 * 2. Box con NestedScrollConnection
 *    ├─ NavHost (main content con bottom padding)
 *    └─ NavigationBar (overlay in bottom con graphicsLayer translation)
 *
 * ## Scroll Behavior Implementation:
 * - `NestedScrollConnection.onPreScroll`: Intercetta scroll events
 * - `mutableFloatStateOf`: Traccia l'offset corrente (-height a 0)
 * - `graphicsLayer.translationY`: Anima la posizione senza recomposition
 * - `coerceIn(-height, 0f)`: Limita l'offset ai bounds corretti
 *
 * ## FAB Positioning:
 * - FAB posizionati nelle schermate con bottom padding
 * - Non più nascosti dalla NavigationBar perché si ritira durante lo scroll
 *
 * @see androidx.compose.material3.NavigationBar
 * @see androidx.compose.ui.input.nestedscroll.NestedScrollConnection
 * @see androidx.compose.ui.graphics.graphicsLayer
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CalmifyApp(
    startDestination: String,
    repository: MongoRepository,
    deepLinkRoute: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    onDataLoaded: () -> Unit = {}
) {

    val navigationState = rememberNavigationState()
    val shouldShowBottomBar = navigationState.shouldShowBottomBar

    // Handle deep linking from FCM notifications
    LaunchedEffect(deepLinkRoute) {
        deepLinkRoute?.let { route ->
            android.util.Log.d("CalmifyApp", "Navigating to deep link: $route")
            navigationState.navController.navigate(route) {
                // Pop up to home to avoid building a large back stack
                popUpTo(Screen.Home.route) {
                    inclusive = false
                }
                launchSingleTop = true
            }
            onDeepLinkHandled()
        }
    }

    // Drawer state a livello globale - SENZA rememberSaveable per evitare state restoration problematiche
    // Forziamo sempre Closed all'avvio/ricomposizione
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Get current route to determine if we're on home screen
    val navBackStackEntry by navigationState.navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isHomeScreen = currentRoute == Screen.Home.route

    // User info
    val user = FirebaseAuth.getInstance().currentUser
    val userProfileImageUrl = user?.photoUrl?.toString()

    // Dialog states
    var signOutDialogOpened by remember { mutableStateOf(false) }
    var deleteAllDialogOpened by remember { mutableStateOf(false) }

    // Navigation Drawer wrapper - ora avvolge tutto
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Abilita il gesto solo se siamo nella home
        gesturesEnabled = isHomeScreen,
        drawerContent = {
            // Sempre mostra il drawer content, ma controlla la visibilità tramite gesturesEnabled
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.85f),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                DrawerContent(
                    userProfileImageUrl = userProfileImageUrl,
                    onHeaderClicked = {
                        scope.launch {
                            drawerState.close()
                        }
                        navigationState.navController.navigate(Screen.Settings.route)
                    },
                    onSignOutClicked = {
                        scope.launch {
                            drawerState.close()
                        }
                        signOutDialogOpened = true
                    },
                    onDeleteAllClicked = {
                        scope.launch {
                            drawerState.close()
                        }
                        deleteAllDialogOpened = true
                    }
                )
            }
        }
    ) {
        // Scaffold con NavigationBar e FAB - pattern ufficiale M3
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // NavigationBar persistente - spec M3 ufficiale
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
                    CalmifyBottomAppBar(
                        navController = navigationState.navController,
                        destinations = listOf(
                            NavigationDestination.Home,
                            NavigationDestination.Humanoid,
                            NavigationDestination.History,
                            NavigationDestination.Profile
                        )
                    )
                }
            },
            // FAB gestito a livello Scaffold - Scaffold posiziona automaticamente sopra NavigationBar
            floatingActionButton = {
                // Mostra FAB solo nella Home
                if (isHomeScreen && shouldShowBottomBar) {
                    // Dual FAB layout - Chat + Write
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Chat FAB (smaller, top)
                        FloatingActionButton(
                            onClick = {
                                navigationState.navController.navigate(Screen.Chat.route)
                            },
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

                        // New Diary Extended FAB (primary action, bottom)
                        ExtendedFloatingActionButton(
                            onClick = {
                                navigationState.navController.navigate(Screen.Write.routeNew)
                            },
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
            floatingActionButtonPosition = FabPosition.End // Posizionamento standard M3
        ) { paddingValues ->
            // Main content con padding automatico dallo Scaffold
            CalmifyNavHost(
                navController = navigationState.navController,
                startDestination = startDestination,
                onDataLoaded = onDataLoaded,
                drawerState = drawerState,
                contentPadding = paddingValues // Scaffold fornisce padding per NavigationBar
            )
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
                            // Sign out from Firebase
                            FirebaseAuth.getInstance().signOut()
                        }

                        withContext(Dispatchers.Main) {
                            navigationState.navController.navigate(Screen.Authentication.route) {
                                popUpTo(Screen.Home.route) {
                                    inclusive = true
                                }
                            }
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
            message = "⚠️ THIS ACTION CANNOT BE UNDONE!\n\nThis will permanently delete:\n• All diaries\n• All AI insights\n• All wellbeing snapshots\n• All psychological profiles\n• All chat sessions\n\nYour account will remain active, but all data will be lost forever.",
            dialogOpened = deleteAllDialogOpened,
            onDialogClosed = { deleteAllDialogOpened = false },
            onYesClicked = {
                deleteAllDialogOpened = false
                scope.launch {
                    try {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Deleting all your data...", Toast.LENGTH_SHORT).show()
                        }

                        // Delete ALL user data
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
 * Drawer Content Component
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        // Header with user info - CLICKABLE
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
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

                // User Profile with animation
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

        // Navigation Items
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

        // Footer
        Text(
            text = "Version 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * Extension per creare elevation colors
 */
@Composable
fun ColorScheme.surfaceColorAtElevation(
    elevation: Dp,
): Color {
    return if (this.surface == this.surfaceVariant) {
        this.surface
    } else {
        val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
        this.primary.copy(alpha = alpha).compositeOver(this.surface)
    }
}

/**
 * Utility function per color composition
 */
fun Color.compositeOver(background: Color): Color {
    val fg = this
    val bg = background

    val a = fg.alpha + bg.alpha * (1 - fg.alpha)
    val r = (fg.red * fg.alpha + bg.red * bg.alpha * (1 - fg.alpha)) / a
    val g = (fg.green * fg.alpha + bg.green * bg.alpha * (1 - fg.alpha)) / a
    val b = (fg.blue * fg.alpha + bg.blue * bg.alpha * (1 - fg.alpha)) / a

    return Color(r, g, b, a)
}
/**
 * Navigation Host principale
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalmifyNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    onDataLoaded: () -> Unit,
    drawerState: DrawerState,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Authentication
        authenticationRoute(
            navigateToHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Authentication.route) {
                        inclusive = true
                    }
                }
            },
            onDataLoaded = onDataLoaded
        )

        // Onboarding
        onboardingRoute(
            onComplete = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Onboarding.route) {
                        inclusive = true
                    }
                }
            }
        )

        // Home - passa il drawerState e contentPadding per layout corretto
        homeRoute(
            navController = navController,
            navigateToWrite = {
                navController.navigate(Screen.Write.routeNew)
            },
            navigateToWriteWithArgs = { diaryId ->
                val writeRoute = Screen.Write.passDiaryId(diaryId = diaryId)
                navController.navigate(writeRoute) {
                    launchSingleTop = true
                    restoreState = true
                }
            },
            navigateToAuth = {
                navController.navigate(Screen.Authentication.route) {
                    popUpTo(Screen.Home.route) {
                        inclusive = true
                    }
                }
            },
            navigateToChat = {
                navController.navigate(Screen.Chat.route)
            },
            navigateToExistingChat = { sessionId ->
                navController.navigateToChat(sessionId)
            },
            navigateToLiveScreen = {
                navController.navigate(Screen.LiveChat.route) {
                    launchSingleTop = true
                }
            },
            navigateToWellbeingSnapshot = {
                navController.navigate(Screen.WellbeingSnapshot.route) {
                    launchSingleTop = true
                }
            },
            onDataLoaded = onDataLoaded,
            drawerState = drawerState // Passa il drawer state
        )

        // History - new activity/history screen
        historyRoute(
            navController = navController,
            navigateToWriteWithArgs = { diaryId ->
                val writeRoute = Screen.Write.passDiaryId(diaryId = diaryId)
                navController.navigate(writeRoute) {
                    launchSingleTop = true
                    restoreState = true
                }
            },
            navigateToExistingChat = { sessionId ->
                navController.navigateToChat(sessionId)
            },
            navigateToInsight = { diaryId ->
                val insightRoute = Screen.Insight.passDiaryId(diaryId)
                navController.navigate(insightRoute) {
                    launchSingleTop = true
                }
            },
            onMenuClicked = {
                // History screen doesn't have a drawer, so we can leave this empty
            }
        )

        // Settings - new module with subscreens
        settingsRoute(
            navController = navController,
            onNavigateBack = {
                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                    navController.popBackStack()
                }
            },
            onLogout = {
                navController.navigate(Screen.Authentication.route) {
                    popUpTo(Screen.Home.route) {
                        inclusive = true
                    }
                }
            }
        )

        // Wellbeing Snapshot
        composable(route = Screen.WellbeingSnapshot.route) {
            com.lifo.home.SnapshotScreen(
                onBackPressed = {
                    if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                        navController.popBackStack()
                    }
                },
                onSnapshotComplete = {
                    if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                        navController.popBackStack()
                    }
                }
            )
        }

        // Insight Screen
        composable(
            route = Screen.Insight.route,
            arguments = listOf(
                androidx.navigation.navArgument("diaryId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = false
                }
            )
        ) { backStackEntry ->
            val diaryId = backStackEntry.arguments?.getString("diaryId") ?: ""
            com.lifo.insight.InsightScreen(
                diaryId = diaryId,
                onBackPressed = {
                    if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                        navController.popBackStack()
                    }
                },
                onPromptClicked = { prompt ->
                    // Navigate to chat with pre-filled prompt
                    navController.navigate(Screen.Chat.route)
                }
            )
        }

        // Write
        writeRoute(
            navigateBack = {
                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                    navController.popBackStack()
                }
            }
        )

        // Chat
        chatRoute(
            navigateBack = {
                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                    navController.popBackStack()
                }
            },
            navigateToWriteWithContent = { content ->
                navController.navigate(Screen.Write.routeNew)
            },
            navigateToLiveScreen = {
                navController.navigate(Screen.LiveChat.route) {
                    launchSingleTop = true
                }
            },
            navigateToAvatarLiveChat = {
                navController.navigate(Screen.AvatarLiveChat.route) {
                    launchSingleTop = true
                }
            }
        )

        // Live Chat - dedicated screen
        liveRoute(
            navigateBack = {
                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                    navController.popBackStack()
                }
            }
        )

        // Profile - Week 7 Implementation
        composable(route = Screen.Profile.route) {
            ProfileDashboard()
        }

        // Humanoid Avatar - 3D Avatar with Filament
        humanoidRoute(
            navigateBack = {
                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                    navController.popBackStack()
                }
            }
        )

        // Avatar Chat - Integrated VRM + Chat (Removed temporarily - consolidation in progress)

        // Avatar Live Chat - VRM + Gemini Live API (Real-time voice)
        // Now uses unified LiveScreen from features/chat with avatar mode enabled
        composable(
            route = Screen.AvatarLiveChat.route,
            enterTransition = {
                fadeIn(tween(400)) + scaleIn(
                    initialScale = 0.95f,
                    animationSpec = tween(400)
                )
            },
            exitTransition = {
                fadeOut(tween(300)) + scaleOut(
                    targetScale = 0.95f,
                    animationSpec = tween(300)
                )
            }
        ) {
            val humanoidViewModel: com.lifo.humanoid.presentation.HumanoidViewModel = hiltViewModel()
            val liveChatViewModel: com.lifo.chat.presentation.viewmodel.LiveChatViewModel = hiltViewModel()

            val context = LocalContext.current

            // Setup avatar integration
            val humanoidController = remember(humanoidViewModel) {
                val lipSyncController = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.lifo.app.integration.avatar.AvatarIntegrationEntryPoint::class.java
                ).lipSyncController()
                humanoidViewModel.asHumanoidController(lipSyncController)
            }

            LaunchedEffect(humanoidController) {
                liveChatViewModel.attachHumanoidController(humanoidController)
            }

            com.lifo.chat.presentation.screen.LiveScreen(
                onClose = {
                    liveChatViewModel.detachHumanoidController()
                    if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                        navController.popBackStack()
                    }
                },
                showAvatar = true,
                avatarContent = {
                    HumanoidAvatarView(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = humanoidViewModel,
                        blurAmount = 0f
                    )
                },
                viewModel = liveChatViewModel
            )
        }
    }
}