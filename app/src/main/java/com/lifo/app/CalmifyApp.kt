package com.lifo.app

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.lifo.app.navigation.*
import com.lifo.auth.navigation.authenticationRoute
import com.lifo.chat.navigation.chatRoute
import com.lifo.chat.navigation.navigateToChat
import com.lifo.home.navigation.homeRoute
import com.lifo.mongo.repository.MongoRepository
import com.lifo.ui.components.DisplayAlertDialog
import com.lifo.ui.components.navigation.CalmifyNavigationBar
import com.lifo.ui.components.navigation.NavigationDestination
import com.lifo.util.Screen
import com.lifo.write.navigation.writeRoute
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ln

/**
 * Composable principale dell'app con Navigation Bar Material 3 e Drawer globale
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CalmifyApp(
    startDestination: String,
    onDataLoaded: () -> Unit = {}
) {

    val navigationState = rememberNavigationState()
    val shouldShowBottomBar = navigationState.shouldShowBottomBar

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

    // Animazione per il padding del contenuto
    val bottomPadding by animateDpAsState(
        targetValue = if (shouldShowBottomBar) 80.dp else 0.dp,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "BottomPaddingAnimation"
    )

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
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content con padding animato
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding)
            ) {
                // Navigation Host senza Scaffold
                CalmifyNavHost(
                    navController = navigationState.navController,
                    startDestination = startDestination,
                    onDataLoaded = onDataLoaded,
                    drawerState = drawerState
                )
            }

            // Navigation Bar posizionata in basso
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                AnimatedVisibility(
                    visible = shouldShowBottomBar,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing
                        )
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = FastOutSlowInEasing
                        )
                    )
                ) {
                    // Usa lo stesso colore del drawer per coerenza visiva
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        CalmifyNavigationBar(
                            navController = navigationState.navController,
                            destinations = listOf(
                                NavigationDestination.Home,
                                NavigationDestination.Write,
                                NavigationDestination.Profile
                            )
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

    // Delete All Dialog
    AnimatedVisibility(
        visible = deleteAllDialogOpened,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        DisplayAlertDialog(
            title = "Delete All Diaries",
            message = "This action cannot be undone. All your diaries and associated images will be permanently deleted.",
            dialogOpened = deleteAllDialogOpened,
            onDialogClosed = { deleteAllDialogOpened = false },
            onYesClicked = {
                deleteAllDialogOpened = false
                scope.launch {
                    try {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Deleting all diaries...", Toast.LENGTH_SHORT).show()
                        }

                        // Delete all diaries
                        deleteAllDiaries(
                            context = context,
                            onSuccess = {
                                scope.launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "All diaries deleted successfully",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onError = { error ->
                                scope.launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        error.message ?: "Failed to delete diaries",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
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
 * Delete all diaries function
 * Note: This function should ideally be called from HomeViewModel instead
 * Keeping it here for backward compatibility but it's not recommended practice
 */
private suspend fun deleteAllDiaries(
    context: android.content.Context,
    onSuccess: () -> Unit,
    onError: (Throwable) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            // Note: This is a workaround. Ideally this should be in a ViewModel
            // For now, we skip the actual deletion as it requires repository injection
            // The HomeViewModel already has the proper deleteAllDiaries implementation
            android.util.Log.w("CalmifyApp", "Delete all diaries called from CalmifyApp - this should be done via HomeViewModel")
            onError(Exception("Please use the delete option from the Home screen menu"))
        } catch (e: Exception) {
            android.util.Log.e("CalmifyApp", "Error in deleteAllDiaries", e)
            onError(e)
        }
    }
}

/**
 * Drawer Content Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerContent(
    userProfileImageUrl: String?,
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
    drawerState: DrawerState
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

        // Home - passa il drawerState
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
            onDataLoaded = onDataLoaded,
            drawerState = drawerState // Passa il drawer state
        )

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
            }
        )

        // Profile
        composable(route = "profile_screen") {
            ProfileScreen(
                navController = navController
            )
        }
    }
}