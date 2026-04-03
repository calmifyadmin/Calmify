package com.lifo.messaging

import androidx.compose.runtime.Composable

/**
 * Wrapper composable that renders either the conversation list, user picker,
 * or chat room based on state.
 */
@Composable
fun MessagingScreen(
    state: MessagingContract.State,
    currentUserId: String,
    onIntent: (MessagingContract.Intent) -> Unit,
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onPickImages: () -> Unit = {},
) {
    when {
        state.isInChatRoom -> {
            ChatRoomScreen(
                state = state,
                currentUserId = currentUserId,
                onIntent = onIntent,
                onNavigateBack = { onIntent(MessagingContract.Intent.CloseConversation) },
                onUserClick = onUserClick,
                onPickImages = onPickImages
            )
        }
        state.isUserPickerOpen -> {
            UserPickerScreen(
                query = state.userPickerQuery,
                results = state.userPickerResults,
                isSearching = state.isSearchingUsers,
                onQueryChange = { onIntent(MessagingContract.Intent.SearchUsers(it)) },
                onUserSelected = { userId ->
                    onIntent(MessagingContract.Intent.HideUserPicker)
                    onIntent(MessagingContract.Intent.CreateConversation(userId))
                },
                onNavigateBack = { onIntent(MessagingContract.Intent.HideUserPicker) },
            )
        }
        else -> {
            ConversationListScreen(
                conversations = state.conversations,
                isLoading = state.isLoadingConversations,
                participantProfiles = state.participantProfiles,
                onConversationClick = { onIntent(MessagingContract.Intent.OpenConversation(it)) },
                onNewConversation = { onIntent(MessagingContract.Intent.ShowUserPicker) }
            )
        }
    }
}
