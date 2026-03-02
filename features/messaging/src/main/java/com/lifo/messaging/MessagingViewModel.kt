package com.lifo.messaging

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.SocialMessagingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MessagingViewModel
 *
 * Manages both the conversation list and chat room views via MVI.
 * Uses separate [Job] fields for collection coroutines so they can
 * be cancelled independently when navigating between views.
 */
class MessagingViewModel(
    private val messagingRepository: SocialMessagingRepository,
    private val authProvider: AuthProvider
) : MviViewModel<MessagingContract.Intent, MessagingContract.State, MessagingContract.Effect>(
    initialState = MessagingContract.State()
) {

    private val currentUserId: String
        get() = authProvider.currentUserId.orEmpty()

    init {
        // Expose currentUserId in state so the UI layer can access it without FirebaseAuth
        updateState { copy(currentUserId = this@MessagingViewModel.currentUserId) }
    }

    private var conversationCollectionJob: Job? = null
    private var messageCollectionJob: Job? = null
    private var typingCollectionJob: Job? = null
    private var typingDebounceJob: Job? = null

    // -- MVI dispatch ---------------------------------------------------------

    override fun handleIntent(intent: MessagingContract.Intent) {
        when (intent) {
            is MessagingContract.Intent.LoadConversations -> loadConversations()
            is MessagingContract.Intent.OpenConversation -> openConversation(intent.conversationId)
            is MessagingContract.Intent.CloseConversation -> closeConversation()
            is MessagingContract.Intent.SendMessage -> sendMessage(intent.text)
            is MessagingContract.Intent.UpdateDraft -> updateDraft(intent.text)
            is MessagingContract.Intent.SetTyping -> setTyping(intent.isTyping)
            is MessagingContract.Intent.CreateConversation -> createConversation(intent.participantId)
            is MessagingContract.Intent.MarkRead -> markRead(intent.conversationId)
        }
    }

    // -- Private handlers -----------------------------------------------------

    private fun loadConversations() {
        val userId = currentUserId
        if (userId.isEmpty()) {
            sendEffect(MessagingContract.Effect.ShowError("Not authenticated"))
            return
        }

        conversationCollectionJob?.cancel()
        conversationCollectionJob = scope.launch {
            updateState { copy(isLoadingConversations = true, error = null) }

            messagingRepository.getConversations(userId).collectLatest { result ->
                when (result) {
                    is RequestState.Success -> {
                        updateState {
                            copy(
                                conversations = result.data,
                                isLoadingConversations = false,
                                error = null
                            )
                        }
                    }
                    is RequestState.Error -> {
                        updateState {
                            copy(
                                isLoadingConversations = false,
                                error = result.message
                            )
                        }
                        sendEffect(MessagingContract.Effect.ShowError(result.message))
                    }
                    is RequestState.Loading -> {
                        updateState { copy(isLoadingConversations = true) }
                    }
                    else -> { /* Idle -- ignore */ }
                }
            }
        }
    }

    private fun openConversation(conversationId: String) {
        val userId = currentUserId
        if (userId.isEmpty()) return

        updateState {
            copy(
                currentConversationId = conversationId,
                messages = emptyList(),
                typingUsers = emptyList(),
                draftText = "",
                isLoadingMessages = true,
                error = null
            )
        }

        // Collect messages
        messageCollectionJob?.cancel()
        messageCollectionJob = scope.launch {
            messagingRepository.getMessages(conversationId).collectLatest { result ->
                when (result) {
                    is RequestState.Success -> {
                        updateState {
                            copy(
                                messages = result.data,
                                isLoadingMessages = false,
                                error = null
                            )
                        }
                    }
                    is RequestState.Error -> {
                        updateState {
                            copy(
                                isLoadingMessages = false,
                                error = result.message
                            )
                        }
                        sendEffect(MessagingContract.Effect.ShowError(result.message))
                    }
                    is RequestState.Loading -> {
                        updateState { copy(isLoadingMessages = true) }
                    }
                    else -> { /* Idle -- ignore */ }
                }
            }
        }

        // Collect typing status
        typingCollectionJob?.cancel()
        typingCollectionJob = scope.launch {
            messagingRepository.getTypingStatus(conversationId).collectLatest { typingUserIds ->
                // Filter out current user from typing indicators
                updateState {
                    copy(typingUsers = typingUserIds.filter { it != userId })
                }
            }
        }

        // Mark conversation as read
        markRead(conversationId)
    }

    private fun closeConversation() {
        val conversationId = currentState.currentConversationId
        val userId = currentUserId

        // Set typing to false before leaving
        if (conversationId != null && userId.isNotEmpty()) {
            scope.launch {
                messagingRepository.setTyping(conversationId, userId, false)
            }
        }

        messageCollectionJob?.cancel()
        messageCollectionJob = null
        typingCollectionJob?.cancel()
        typingCollectionJob = null
        typingDebounceJob?.cancel()
        typingDebounceJob = null

        updateState {
            copy(
                currentConversationId = null,
                messages = emptyList(),
                typingUsers = emptyList(),
                draftText = "",
                isLoadingMessages = false,
                isSending = false,
                error = null
            )
        }
    }

    private fun sendMessage(text: String) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) return

        val conversationId = currentState.currentConversationId ?: return
        val userId = currentUserId
        if (userId.isEmpty()) return

        updateState { copy(isSending = true) }

        scope.launch {
            // Stop typing indicator
            messagingRepository.setTyping(conversationId, userId, false)
            typingDebounceJob?.cancel()

            val message = SocialMessagingRepository.Message(
                senderId = userId,
                text = trimmedText,
                createdAt = System.currentTimeMillis(),
                isRead = false
            )

            when (val result = messagingRepository.sendMessage(conversationId, message)) {
                is RequestState.Success -> {
                    updateState { copy(draftText = "", isSending = false) }
                    sendEffect(MessagingContract.Effect.MessageSent)
                }
                is RequestState.Error -> {
                    updateState { copy(isSending = false) }
                    sendEffect(MessagingContract.Effect.ShowError(result.message))
                }
                else -> { /* Loading / Idle */ }
            }
        }
    }

    private fun updateDraft(text: String) {
        updateState { copy(draftText = text) }
    }

    private fun setTyping(isTyping: Boolean) {
        val conversationId = currentState.currentConversationId ?: return
        val userId = currentUserId
        if (userId.isEmpty()) return

        typingDebounceJob?.cancel()

        if (isTyping) {
            // Set typing immediately, then auto-clear after 3 seconds of inactivity
            scope.launch {
                messagingRepository.setTyping(conversationId, userId, true)
            }
            typingDebounceJob = scope.launch {
                delay(TYPING_TIMEOUT_MS)
                messagingRepository.setTyping(conversationId, userId, false)
            }
        } else {
            scope.launch {
                messagingRepository.setTyping(conversationId, userId, false)
            }
        }
    }

    private fun createConversation(participantId: String) {
        val userId = currentUserId
        if (userId.isEmpty()) {
            sendEffect(MessagingContract.Effect.ShowError("Not authenticated"))
            return
        }

        scope.launch {
            updateState { copy(isLoadingConversations = true) }

            when (val result = messagingRepository.createConversation(listOf(userId, participantId))) {
                is RequestState.Success -> {
                    val newConversationId = result.data
                    updateState { copy(isLoadingConversations = false) }
                    sendEffect(MessagingContract.Effect.ConversationCreated(newConversationId))
                    openConversation(newConversationId)
                }
                is RequestState.Error -> {
                    updateState { copy(isLoadingConversations = false) }
                    sendEffect(MessagingContract.Effect.ShowError(result.message))
                }
                else -> { /* Loading / Idle */ }
            }
        }
    }

    private fun markRead(conversationId: String) {
        val userId = currentUserId
        if (userId.isEmpty()) return

        scope.launch {
            messagingRepository.markConversationRead(conversationId, userId)
        }
    }

    companion object {
        private const val TYPING_TIMEOUT_MS = 3_000L
    }
}
