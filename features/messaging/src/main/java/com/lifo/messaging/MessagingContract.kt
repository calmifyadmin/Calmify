package com.lifo.messaging

import androidx.compose.runtime.Immutable
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.SocialMessagingRepository

object MessagingContract {

    sealed interface Intent : MviContract.Intent {
        data object LoadConversations : Intent
        data class OpenConversation(val conversationId: String) : Intent
        data object CloseConversation : Intent
        data class SendMessage(val text: String) : Intent
        data class UpdateDraft(val text: String) : Intent
        data class SetTyping(val isTyping: Boolean) : Intent
        data class CreateConversation(val participantId: String) : Intent
        data class MarkRead(val conversationId: String) : Intent
        data object ShowUserPicker : Intent
        data object HideUserPicker : Intent
        data class SearchUsers(val query: String) : Intent
    }

    @Immutable
    data class State(
        val currentUserId: String = "",
        val conversations: List<SocialMessagingRepository.Conversation> = emptyList(),
        val currentConversationId: String? = null,
        val messages: List<SocialMessagingRepository.Message> = emptyList(),
        val typingUsers: List<String> = emptyList(),
        val draftText: String = "",
        val isLoadingConversations: Boolean = false,
        val isLoadingMessages: Boolean = false,
        val isSending: Boolean = false,
        val error: String? = null,
        val isUserPickerOpen: Boolean = false,
        val userPickerQuery: String = "",
        val userPickerResults: List<SocialGraphRepository.SocialUser> = emptyList(),
        val isSearchingUsers: Boolean = false,
    ) : MviContract.State {
        val isInChatRoom: Boolean get() = currentConversationId != null
    }

    sealed interface Effect : MviContract.Effect {
        data object MessageSent : Effect
        data class ConversationCreated(val conversationId: String) : Effect
        data class ShowError(val message: String) : Effect
        data class NavigateToUserProfile(val userId: String) : Effect
    }
}
