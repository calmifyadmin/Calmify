package com.lifo.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.lifo.onboarding.OnboardingScreen
import com.lifo.util.Screen

/**
 * Navigation extension for onboarding module
 */
fun NavGraphBuilder.onboardingRoute(
    onComplete: () -> Unit
) {
    composable(route = Screen.Onboarding.route) {
        OnboardingScreen(onComplete = onComplete)
    }
}
