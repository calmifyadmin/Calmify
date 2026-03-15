package com.lifo.avatarcreator.presentation

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.Avatar
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.AvatarRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AvatarListViewModel(
    private val avatarRepository: AvatarRepository,
    private val authProvider: AuthProvider,
) : MviViewModel<AvatarListViewModel.Intent, AvatarListViewModel.State, AvatarListViewModel.Effect>(State()) {

    data class State(
        val avatars: List<Avatar> = emptyList(),
        val isLoading: Boolean = true,
    ) : MviContract.State

    sealed interface Intent : MviContract.Intent
    sealed interface Effect : MviContract.Effect

    init {
        observeAvatars()
    }

    override fun handleIntent(intent: Intent) {}

    private fun observeAvatars() {
        val userId = authProvider.currentUserId ?: return
        scope.launch {
            avatarRepository.observeUserAvatars(userId)
                .catch { updateState { copy(isLoading = false) } }
                .collectLatest { avatars ->
                    updateState { copy(avatars = avatars, isLoading = false) }
                }
        }
    }
}
