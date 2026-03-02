package com.lifo.messaging

import androidx.compose.runtime.Composable

/**
 * Wrapper composable that renders either the conversation list or the chat room
 * based on [MessagingContract.State.isInChatRoom].
 *
 * When [MessagingContract.State.currentConversationId] is non-null the user is
 * inside a conversation; otherwise the conversation list is shown.
 */
@Composable
fun MessagingScreen(
    state: MessagingContract.State,
    currentUserId: String,
    onIntent: (MessagingContract.Intent) -> Unit,
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
) {
    if (state.isInChatRoom) {
        ChatRoomScreen(
            state = state,
            currentUserId = currentUserId,
            onIntent = onIntent,
            onNavigateBack = { onIntent(MessagingContract.Intent.CloseConversation) },
            onUserClick = onUserClick
        )
    } else {
        ConversationListScreen(
            conversations = state.conversations,
            isLoading = state.isLoadingConversations,
            onConversationClick = { onIntent(MessagingContract.Intent.OpenConversation(it)) },
            onNewConversation = { /* TODO: show user picker */ }
        )
    }
}
