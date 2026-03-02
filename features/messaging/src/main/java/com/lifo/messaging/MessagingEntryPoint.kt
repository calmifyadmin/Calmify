package com.lifo.messaging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel

/**
 * Public entry point for the Messaging feature.
 *
 * Obtains the [MessagingViewModel] via Koin, dispatches the appropriate initial
 * intent based on [conversationId], and delegates rendering to [MessagingScreen].
 *
 * @param conversationId Optional conversation ID. When non-null the chat room is
 *                       opened directly; otherwise the conversation list is shown.
 * @param onNavigateBack Called when the user navigates away from the messaging feature.
 * @param onUserClick    Called when the user taps on another user's avatar/name.
 */
@Composable
fun MessagingRouteContent(
    conversationId: String? = null,
    onNavigateBack: () -> Unit = {},
    onUserClick: (String) -> Unit = {},
) {
    val viewModel: MessagingViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val currentUserId = state.currentUserId

    // Dispatch initial intent based on whether a conversationId was supplied
    LaunchedEffect(conversationId) {
        if (conversationId != null) {
            viewModel.onIntent(MessagingContract.Intent.OpenConversation(conversationId))
        } else {
            viewModel.onIntent(MessagingContract.Intent.LoadConversations)
        }
    }

    // Collect one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MessagingContract.Effect.MessageSent -> {
                    // Message sent successfully; UI already scrolls to bottom
                }
                is MessagingContract.Effect.ConversationCreated -> {
                    // Conversation opened automatically by the ViewModel
                }
                is MessagingContract.Effect.ShowError -> {
                    // Error is already reflected in state.error; could show a snackbar here
                }
                is MessagingContract.Effect.NavigateToUserProfile -> {
                    onUserClick(effect.userId)
                }
            }
        }
    }

    MessagingScreen(
        state = state,
        currentUserId = currentUserId,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        onUserClick = onUserClick
    )
}
