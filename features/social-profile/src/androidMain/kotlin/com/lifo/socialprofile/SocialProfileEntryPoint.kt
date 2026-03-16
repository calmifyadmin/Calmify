package com.lifo.socialprofile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import org.koin.compose.viewmodel.koinViewModel

/**
 * Public entry point for the Social Profile screen.
 *
 * Obtains the [SocialProfileViewModel] via Koin, dispatches [SocialProfileContract.Intent.LoadProfile]
 * on first composition, collects state and effects, and delegates rendering to [SocialProfileScreen].
 *
 * @param userId  The ID of the user whose profile should be displayed.
 * @param onNavigateBack  Called when the user presses the back button.
 * @param onThreadClick   Called when a thread card is tapped, with the thread ID.
 */
@Composable
fun SocialProfileRouteContent(
    userId: String,
    onNavigateBack: () -> Unit = {},
    onThreadClick: (String) -> Unit = {},
    onUserClick: (String) -> Unit = {},
    onEditProfileClick: (String) -> Unit = {},
    onFollowersClick: (String) -> Unit = {},
    onFollowingClick: (String) -> Unit = {},
) {
    val viewModel: SocialProfileViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    // Load profile once when the composable enters the composition
    LaunchedEffect(userId) {
        viewModel.onIntent(SocialProfileContract.Intent.LoadProfile(userId))
    }

    // Collect one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SocialProfileContract.Effect.NavigateToThread -> {
                    onThreadClick(effect.threadId)
                }
                is SocialProfileContract.Effect.UserBlocked -> {
                    onNavigateBack()
                }
                is SocialProfileContract.Effect.ShowError -> {
                    // Error is already handled via state.error in the screen
                }
                is SocialProfileContract.Effect.LaunchImagePicker -> {
                    // Image picker is handled at the screen level
                }
                is SocialProfileContract.Effect.ProfileSaved -> {
                    // Profile saved successfully; UI already reflects changes
                }
                is SocialProfileContract.Effect.NavigateToEditProfile -> {
                    onEditProfileClick(state.userId)
                }
            }
        }
    }

    SocialProfileScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onThreadClick = onThreadClick,
        onUserClick = onUserClick,
        onFollowersClick = onFollowersClick,
        onFollowingClick = onFollowingClick,
    )
}
