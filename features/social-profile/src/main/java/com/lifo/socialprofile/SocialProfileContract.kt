package com.lifo.socialprofile

import androidx.compose.runtime.Immutable
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.ThreadRepository

object SocialProfileContract {

    enum class ProfileTab { THREADS, REPLIES, REPOSTS }

    sealed interface Intent : MviContract.Intent {
        data class LoadProfile(val userId: String) : Intent
        data object FollowUser : Intent
        data object UnfollowUser : Intent
        data object BlockUser : Intent
        data object LoadThreads : Intent
        data class SelectProfileTab(val tab: ProfileTab) : Intent
        // Engagement intents
        data class LikeThread(val threadId: String) : Intent
        data class UnlikeThread(val threadId: String) : Intent
        data class ShareThread(val threadId: String) : Intent
        // Edit profile intents
        data object OpenEditProfile : Intent
        data object CloseEditProfile : Intent
        data class UpdateEditUsername(val username: String) : Intent
        data class UpdateEditName(val name: String) : Intent
        data class UpdateEditBio(val bio: String) : Intent
        data class UpdateEditLink(val link: String) : Intent
        data class AddInterest(val interest: String) : Intent
        data class RemoveInterest(val interest: String) : Intent
        data class SetAvatarUri(val uri: String) : Intent
        data class SetAvatarBytes(val bytes: ByteArray, val previewUri: String) : Intent
        data object SaveProfile : Intent
        data object EditProfile : Intent
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
        val selectedProfileTab: ProfileTab = ProfileTab.THREADS,
        // Edit profile state
        val isEditSheetOpen: Boolean = false,
        val editUsername: String = "",
        val editUsernameError: String? = null,
        val editName: String = "",
        val editBio: String = "",
        val editAvatarUri: String? = null,
        val editLink: String = "",
        val editInterests: List<String> = emptyList(),
        val isSaving: Boolean = false,
        val pendingAvatarBytes: ByteArray? = null,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class ShowError(val message: String) : Effect
        data class UserBlocked(val userId: String) : Effect
        data class NavigateToThread(val threadId: String) : Effect
        data object LaunchImagePicker : Effect
        data object ProfileSaved : Effect
        data object NavigateToEditProfile : Effect
    }
}
