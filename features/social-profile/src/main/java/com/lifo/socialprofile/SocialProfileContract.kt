package com.lifo.socialprofile

import androidx.compose.runtime.Immutable
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.ThreadRepository

object SocialProfileContract {

    sealed interface Intent : MviContract.Intent {
        data class LoadProfile(val userId: String) : Intent
        data object FollowUser : Intent
        data object UnfollowUser : Intent
        data object BlockUser : Intent
        data object LoadThreads : Intent
    }

    @Immutable
    data class State(
        val userId: String = "",
        val profile: SocialGraphRepository.SocialUser? = null,
        val threads: List<ThreadRepository.Thread> = emptyList(),
        val isLoading: Boolean = false,
        val isFollowing: Boolean = false,
        val isOwnProfile: Boolean = false,
        val error: String? = null,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class ShowError(val message: String) : Effect
        data class UserBlocked(val userId: String) : Effect
        data class NavigateToThread(val threadId: String) : Effect
    }
}
