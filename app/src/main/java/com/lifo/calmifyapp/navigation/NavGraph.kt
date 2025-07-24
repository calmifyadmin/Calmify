package com.lifo.calmifyapp.navigation

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.lifo.app.CalmifyApp
import com.lifo.auth.navigation.authenticationRoute
import com.lifo.chat.navigation.chatRoute
import com.lifo.home.navigation.homeRoute
import com.lifo.ui.components.navigation.NavigationDestination
import com.lifo.util.Screen
import com.lifo.write.navigation.writeRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
@Composable
fun SetupNavGraph(
    startDestination: String,
    navController: NavHostController,
    onDataLoaded: () -> Unit
) {
    // Delega tutto a CalmifyApp
    CalmifyApp(
        startDestination = startDestination,
        onDataLoaded = onDataLoaded
    )
}

// Custom enter/exit animations for specific routes
@OptIn(ExperimentalAnimationApi::class)
private fun getEnterTransition(route: String?): EnterTransition {
    return when (route) {
        Screen.Write.route -> {
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn()
        }
        else -> {
            fadeIn(animationSpec = tween(300)) +
                    scaleIn(
                        initialScale = 0.95f,
                        animationSpec = tween(300)
                    )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
private fun getExitTransition(route: String?): ExitTransition {
    return when (route) {
        Screen.Write.route -> {
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeOut()
        }
        else -> {
            fadeOut(animationSpec = tween(300)) +
                    scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(300)
                    )
        }
    }
}