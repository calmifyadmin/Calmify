package com.lifo.socialprofile

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.ThreadRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * SocialProfileViewModel
 *
 * Manages the social profile screen state via MVI.
 * Handles profile loading, follow/unfollow actions, blocking,
 * and fetching threads authored by the viewed user.
 */
class SocialProfileViewModel(
    private val socialGraphRepository: SocialGraphRepository,
    private val threadRepository: ThreadRepository,
    private val authProvider: AuthProvider
) : MviViewModel<SocialProfileContract.Intent, SocialProfileContract.State, SocialProfileContract.Effect>(
    initialState = SocialProfileContract.State()
) {

    private val currentUserId: String
        get() = authProvider.currentUserId.orEmpty()

    // -- MVI dispatch ---------------------------------------------------------

    override fun handleIntent(intent: SocialProfileContract.Intent) {
        when (intent) {
            is SocialProfileContract.Intent.LoadProfile -> loadProfile(intent.userId)
            is SocialProfileContract.Intent.FollowUser -> followUser()
            is SocialProfileContract.Intent.UnfollowUser -> unfollowUser()
            is SocialProfileContract.Intent.BlockUser -> blockUser()
            is SocialProfileContract.Intent.LoadThreads -> loadThreads()
        }
    }

    // -- Private handlers -----------------------------------------------------

    private fun loadProfile(userId: String) {
        val isOwn = currentUserId.isNotEmpty() && currentUserId == userId

        updateState {
            copy(
                userId = userId,
                isLoading = true,
                isOwnProfile = isOwn,
                error = null
            )
        }

        // Load profile data from followers list (the user appears in their own followers' data)
        scope.launch {
            socialGraphRepository.getFollowers(userId, limit = 1).collectLatest { result ->
                when (result) {
                    is RequestState.Success -> {
                        // Build a SocialUser from available data
                        val user = result.data.firstOrNull()
                            ?: SocialGraphRepository.SocialUser(userId = userId)
                        updateState { copy(profile = user, isLoading = false) }
                    }
                    is RequestState.Error -> {
                        // Even on error, set a minimal profile so the screen can render
                        updateState {
                            copy(
                                profile = SocialGraphRepository.SocialUser(userId = userId),
                                isLoading = false,
                                error = result.message
                            )
                        }
                        sendEffect(SocialProfileContract.Effect.ShowError(result.message))
                    }
                    is RequestState.Loading -> {
                        updateState { copy(isLoading = true) }
                    }
                    else -> { /* Idle -- ignore */ }
                }
            }
        }

        // Observe following state
        if (!isOwn && currentUserId.isNotEmpty()) {
            scope.launch {
                socialGraphRepository.isFollowing(currentUserId, userId).collectLatest { following ->
                    updateState { copy(isFollowing = following) }
                }
            }
        }

        // Load threads
        loadThreads()
    }

    private fun followUser() {
        val targetUserId = currentState.userId
        if (currentUserId.isEmpty() || targetUserId.isEmpty()) return

        scope.launch {
            when (val result = socialGraphRepository.follow(currentUserId, targetUserId)) {
                is RequestState.Success -> {
                    updateState { copy(isFollowing = true) }
                }
                is RequestState.Error -> {
                    sendEffect(SocialProfileContract.Effect.ShowError(result.message))
                }
                else -> { /* Loading / Idle */ }
            }
        }
    }

    private fun unfollowUser() {
        val targetUserId = currentState.userId
        if (currentUserId.isEmpty() || targetUserId.isEmpty()) return

        scope.launch {
            when (val result = socialGraphRepository.unfollow(currentUserId, targetUserId)) {
                is RequestState.Success -> {
                    updateState { copy(isFollowing = false) }
                }
                is RequestState.Error -> {
                    sendEffect(SocialProfileContract.Effect.ShowError(result.message))
                }
                else -> { /* Loading / Idle */ }
            }
        }
    }

    private fun blockUser() {
        val targetUserId = currentState.userId
        if (currentUserId.isEmpty() || targetUserId.isEmpty()) return

        scope.launch {
            when (val result = socialGraphRepository.block(currentUserId, targetUserId)) {
                is RequestState.Success -> {
                    sendEffect(SocialProfileContract.Effect.UserBlocked(targetUserId))
                }
                is RequestState.Error -> {
                    sendEffect(SocialProfileContract.Effect.ShowError(result.message))
                }
                else -> { /* Loading / Idle */ }
            }
        }
    }

    private fun loadThreads() {
        val userId = currentState.userId
        if (userId.isEmpty()) return

        scope.launch {
            threadRepository.getThreadsByAuthor(userId).collectLatest { result ->
                when (result) {
                    is RequestState.Success -> {
                        updateState { copy(threads = result.data) }
                    }
                    is RequestState.Error -> {
                        sendEffect(SocialProfileContract.Effect.ShowError(result.message))
                    }
                    else -> { /* Loading / Idle */ }
                }
            }
        }
    }
}
