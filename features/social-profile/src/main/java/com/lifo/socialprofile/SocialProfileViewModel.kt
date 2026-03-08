package com.lifo.socialprofile

import com.lifo.util.auth.AuthProvider
import com.lifo.util.auth.UserIdentityResolver
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.MediaUploadRepository
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.ThreadRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * SocialProfileViewModel
 *
 * Manages the social profile screen state via MVI.
 * Handles profile loading, follow/unfollow actions, blocking,
 * tab selection, and fetching threads authored by the viewed user.
 */
class SocialProfileViewModel(
    private val socialGraphRepository: SocialGraphRepository,
    private val threadRepository: ThreadRepository,
    private val authProvider: AuthProvider,
    private val mediaUploadRepository: MediaUploadRepository,
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
            is SocialProfileContract.Intent.SelectProfileTab -> handleSelectTab(intent.tab)
            is SocialProfileContract.Intent.LikeThread -> likeThread(intent.threadId)
            is SocialProfileContract.Intent.UnlikeThread -> unlikeThread(intent.threadId)
            is SocialProfileContract.Intent.ShareThread -> { /* Handled in UI layer */ }
            is SocialProfileContract.Intent.OpenEditProfile -> openEditProfile()
            is SocialProfileContract.Intent.CloseEditProfile -> updateState { copy(isEditSheetOpen = false) }
            is SocialProfileContract.Intent.UpdateEditUsername -> updateEditUsername(intent.username)
            is SocialProfileContract.Intent.UpdateEditName -> updateState { copy(editName = intent.name) }
            is SocialProfileContract.Intent.UpdateEditBio -> updateState { copy(editBio = intent.bio) }
            is SocialProfileContract.Intent.UpdateEditLink -> updateState { copy(editLink = intent.link) }
            is SocialProfileContract.Intent.AddInterest -> updateState {
                if (intent.interest.isNotBlank() && intent.interest !in editInterests)
                    copy(editInterests = editInterests + intent.interest.trim())
                else this
            }
            is SocialProfileContract.Intent.RemoveInterest -> updateState {
                copy(editInterests = editInterests - intent.interest)
            }
            is SocialProfileContract.Intent.SetAvatarUri -> updateState { copy(editAvatarUri = intent.uri) }
            is SocialProfileContract.Intent.SetAvatarBytes -> updateState {
                copy(editAvatarUri = intent.previewUri, pendingAvatarBytes = intent.bytes)
            }
            is SocialProfileContract.Intent.SaveProfile -> saveProfile()
            is SocialProfileContract.Intent.EditProfile -> sendEffect(SocialProfileContract.Effect.NavigateToEditProfile)
        }
    }

    // -- Private handlers -----------------------------------------------------

    private fun handleSelectTab(tab: SocialProfileContract.ProfileTab) {
        updateState { copy(selectedProfileTab = tab) }
        // Reload content for the selected tab
        loadThreads()
    }

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

        // Load profile data from user_profiles collection
        scope.launch {
            socialGraphRepository.getProfile(userId).collectLatest { result ->
                when (result) {
                    is RequestState.Success -> {
                        updateState { copy(profile = result.data, isLoading = false) }
                    }
                    is RequestState.Error -> {
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

    private fun likeThread(threadId: String) {
        if (currentUserId.isEmpty()) return

        // Optimistic update
        updateState {
            copy(
                threads = threads.map { thread ->
                    if (thread.threadId == threadId) {
                        thread.copy(likeCount = thread.likeCount + 1, isLikedByCurrentUser = true)
                    } else thread
                }
            )
        }

        scope.launch {
            when (val result = threadRepository.likeThread(currentUserId, threadId)) {
                is RequestState.Error -> {
                    // Revert optimistic update
                    updateState {
                        copy(
                            threads = threads.map { thread ->
                                if (thread.threadId == threadId) {
                                    thread.copy(likeCount = (thread.likeCount - 1).coerceAtLeast(0), isLikedByCurrentUser = false)
                                } else thread
                            }
                        )
                    }
                    sendEffect(SocialProfileContract.Effect.ShowError(result.message))
                }
                else -> { /* Success / Loading */ }
            }
        }
    }

    private fun unlikeThread(threadId: String) {
        if (currentUserId.isEmpty()) return

        // Optimistic update
        updateState {
            copy(
                threads = threads.map { thread ->
                    if (thread.threadId == threadId) {
                        thread.copy(likeCount = (thread.likeCount - 1).coerceAtLeast(0), isLikedByCurrentUser = false)
                    } else thread
                }
            )
        }

        scope.launch {
            when (val result = threadRepository.unlikeThread(currentUserId, threadId)) {
                is RequestState.Error -> {
                    // Revert optimistic update
                    updateState {
                        copy(
                            threads = threads.map { thread ->
                                if (thread.threadId == threadId) {
                                    thread.copy(likeCount = thread.likeCount + 1, isLikedByCurrentUser = true)
                                } else thread
                            }
                        )
                    }
                    sendEffect(SocialProfileContract.Effect.ShowError(result.message))
                }
                else -> { /* Success / Loading */ }
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
                        // Filter based on selected tab
                        val filtered = when (currentState.selectedProfileTab) {
                            SocialProfileContract.ProfileTab.THREADS -> {
                                result.data.filter { it.parentThreadId == null }
                            }
                            SocialProfileContract.ProfileTab.REPLIES -> {
                                result.data.filter { it.parentThreadId != null }
                            }
                            SocialProfileContract.ProfileTab.REPOSTS -> {
                                result.data.filter { it.isRepostedByCurrentUser }
                            }
                        }
                        updateState { copy(threads = filtered) }
                    }
                    is RequestState.Error -> {
                        sendEffect(SocialProfileContract.Effect.ShowError(result.message))
                    }
                    else -> { /* Loading / Idle */ }
                }
            }
        }
    }

    // -- Edit Profile handlers ----------------------------------------------------

    private fun openEditProfile() {
        val profile = currentState.profile
        updateState {
            copy(
                isEditSheetOpen = true,
                editUsername = profile?.username.orEmpty(),
                editUsernameError = null,
                editName = profile?.displayName.orEmpty(),
                editBio = profile?.bio.orEmpty(),
                editAvatarUri = profile?.avatarUrl,
                editLink = profile?.links?.firstOrNull().orEmpty(),
                editInterests = profile?.interests.orEmpty(),
            )
        }
    }

    private fun updateEditUsername(username: String) {
        val error = UserIdentityResolver.getUsernameError(username)
        updateState { copy(editUsername = username, editUsernameError = error) }
    }

    private fun saveProfile() {
        val state = currentState
        val userId = state.userId
        if (userId.isEmpty()) return

        // Validate username if changed
        val newUsername = state.editUsername.trim()
        val currentUsername = state.profile?.username.orEmpty()
        if (newUsername.isNotBlank() && newUsername != currentUsername) {
            val usernameError = UserIdentityResolver.getUsernameError(newUsername)
            if (usernameError != null) {
                updateState { copy(editUsernameError = usernameError) }
                return
            }
        }

        updateState { copy(isSaving = true) }

        scope.launch {
            // Check username availability if changed
            if (newUsername.isNotBlank() && newUsername != currentUsername) {
                when (val availResult = socialGraphRepository.isUsernameAvailable(newUsername)) {
                    is RequestState.Success -> {
                        if (!availResult.data) {
                            updateState {
                                copy(
                                    isSaving = false,
                                    editUsernameError = "Username gia' in uso"
                                )
                            }
                            return@launch
                        }
                    }
                    is RequestState.Error -> {
                        updateState { copy(isSaving = false) }
                        sendEffect(SocialProfileContract.Effect.ShowError("Errore verifica username"))
                        return@launch
                    }
                    else -> {}
                }
            }

            // Upload avatar if user picked a new image
            var avatarUrl = state.editAvatarUri
            val pendingBytes = state.pendingAvatarBytes
            if (pendingBytes != null && pendingBytes.isNotEmpty()) {
                when (val uploadResult = mediaUploadRepository.uploadImage(userId, pendingBytes)) {
                    is RequestState.Success -> {
                        avatarUrl = uploadResult.data.url
                        updateState { copy(pendingAvatarBytes = null) }
                    }
                    is RequestState.Error -> {
                        updateState { copy(isSaving = false) }
                        sendEffect(SocialProfileContract.Effect.ShowError("Upload avatar fallito"))
                        return@launch
                    }
                    else -> {}
                }
            }

            val updates = mutableMapOf<String, Any?>(
                "displayName" to state.editName,
                "bio" to state.editBio,
                "interests" to state.editInterests,
            )
            // Save username if provided
            if (newUsername.isNotBlank()) {
                updates["username"] = newUsername
            }
            if (state.editLink.isNotBlank()) {
                updates["links"] = listOf(state.editLink)
            }
            if (avatarUrl != null) {
                updates["avatarUrl"] = avatarUrl
            }

            when (val result = socialGraphRepository.updateProfile(userId, updates)) {
                is RequestState.Success -> {
                    updateState { copy(isEditSheetOpen = false, isSaving = false, pendingAvatarBytes = null) }
                    sendEffect(SocialProfileContract.Effect.ProfileSaved)
                    loadProfile(userId)
                }
                is RequestState.Error -> {
                    updateState { copy(isSaving = false) }
                    sendEffect(SocialProfileContract.Effect.ShowError(result.message))
                }
                else -> {
                    updateState { copy(isSaving = false) }
                }
            }
        }
    }
}
