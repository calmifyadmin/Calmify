package com.lifo.threaddetail

import com.lifo.util.currentTimeMillis
import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.ThreadHydrator
import com.lifo.util.repository.ThreadRepository
import com.lifo.util.repository.ThreadRepository.Thread
import kotlinx.coroutines.launch

class ThreadDetailViewModel(
    private val threadId: String,
    private val threadRepository: ThreadRepository,
    private val threadHydrator: ThreadHydrator,
    private val authProvider: AuthProvider,
) : MviViewModel<ThreadDetailContract.Intent, ThreadDetailContract.State, ThreadDetailContract.Effect>(
    initialState = ThreadDetailContract.State()
) {

    init {
        onIntent(ThreadDetailContract.Intent.LoadThread(threadId))
    }

    override fun handleIntent(intent: ThreadDetailContract.Intent) {
        when (intent) {
            is ThreadDetailContract.Intent.LoadThread -> loadThread(intent.threadId)
            is ThreadDetailContract.Intent.UpdateReplyText -> updateState { copy(replyText = intent.text) }
            is ThreadDetailContract.Intent.SubmitReply -> submitReply()
            is ThreadDetailContract.Intent.LikeThread -> likeThread(intent.threadId)
            is ThreadDetailContract.Intent.UnlikeThread -> unlikeThread(intent.threadId)
            is ThreadDetailContract.Intent.ReplyToReply -> updateState { copy(replyingToReply = intent.thread) }
            is ThreadDetailContract.Intent.ClearReplyTarget -> updateState { copy(replyingToReply = null) }
            is ThreadDetailContract.Intent.ChangeSortOrder -> changeSortOrder(intent.order)
            is ThreadDetailContract.Intent.ToggleNestedReplies -> toggleNestedReplies(intent.parentReplyId)
        }
    }

    private fun changeSortOrder(order: ReplySortOrder) {
        updateState {
            val sortedReplies = when (val current = replies) {
                is RequestState.Success -> {
                    val sorted = when (order) {
                        ReplySortOrder.MostPopular -> current.data.sortedByDescending { it.likeCount }
                        ReplySortOrder.MostRecent -> current.data.sortedBy { it.createdAt }
                    }
                    RequestState.Success(sorted)
                }
                else -> replies
            }
            copy(sortOrder = order, replies = sortedReplies)
        }
    }

    private fun toggleNestedReplies(parentReplyId: String) {
        val isCurrentlyExpanded = currentState.expandedReplies.contains(parentReplyId)

        updateState {
            val newExpanded = if (isCurrentlyExpanded) {
                expandedReplies - parentReplyId
            } else {
                expandedReplies + parentReplyId
            }
            copy(expandedReplies = newExpanded)
        }

        // Fetch nested replies if expanding and not already loaded
        if (!isCurrentlyExpanded && currentState.nestedReplies[parentReplyId] == null) {
            fetchNestedReplies(parentReplyId)
        }
    }

    private fun fetchNestedReplies(parentReplyId: String) {
        val userId = authProvider.currentUserId ?: return

        updateState {
            copy(nestedReplies = nestedReplies + (parentReplyId to RequestState.Loading))
        }

        scope.launch {
            threadRepository.getReplies(parentReplyId).collect { result ->
                when (result) {
                    is RequestState.Success -> {
                        val hydrated = threadHydrator.hydrate(result.data, userId)
                        updateState {
                            copy(nestedReplies = nestedReplies + (parentReplyId to RequestState.Success(hydrated)))
                        }
                    }
                    is RequestState.Error -> {
                        updateState {
                            copy(nestedReplies = nestedReplies + (parentReplyId to RequestState.Error(result.error)))
                        }
                    }
                    is RequestState.Loading -> {
                        updateState {
                            copy(nestedReplies = nestedReplies + (parentReplyId to RequestState.Loading))
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadThread(threadId: String) {
        val userId = authProvider.currentUserId ?: return

        scope.launch {
            threadRepository.getThreadById(threadId).collect { result ->
                when (result) {
                    is RequestState.Success -> {
                        val thread = result.data
                        if (thread != null) {
                            val hydrated = threadHydrator.hydrateSingle(thread, userId)
                            updateState { copy(thread = RequestState.Success(hydrated)) }
                        } else {
                            updateState { copy(thread = RequestState.Success(null)) }
                        }
                    }
                    is RequestState.Error -> updateState { copy(thread = RequestState.Error(result.error)) }
                    is RequestState.Loading -> updateState { copy(thread = RequestState.Loading) }
                    else -> {}
                }
            }
        }

        // Load replies
        scope.launch {
            threadRepository.getReplies(threadId).collect { result ->
                when (result) {
                    is RequestState.Success -> {
                        val hydrated = threadHydrator.hydrate(result.data, userId)
                        updateState { copy(replies = RequestState.Success(hydrated)) }
                    }
                    is RequestState.Error -> updateState { copy(replies = RequestState.Error(result.error)) }
                    is RequestState.Loading -> updateState { copy(replies = RequestState.Loading) }
                    else -> {}
                }
            }
        }
    }

    private fun submitReply() {
        val userId = authProvider.currentUserId ?: return
        val currentState = state.value
        val text = currentState.replyText.trim()
        if (text.isBlank()) return

        // Reply to a specific reply, or to the main thread
        val targetParentId = currentState.replyingToReply?.threadId ?: threadId

        updateState { copy(isSendingReply = true) }

        scope.launch {
            val reply = Thread(
                authorId = userId,
                parentThreadId = targetParentId,
                text = text,
                visibility = "public",
                createdAt = currentTimeMillis()
            )

            when (val result = threadRepository.createThread(reply)) {
                is RequestState.Success -> {
                    updateState { copy(replyText = "", isSendingReply = false, replyingToReply = null) }
                    sendEffect(ThreadDetailContract.Effect.ReplyPosted)
                }
                is RequestState.Error -> {
                    updateState { copy(isSendingReply = false) }
                    sendEffect(ThreadDetailContract.Effect.ShowError(result.error.message ?: "Failed to post reply"))
                }
                else -> {}
            }
        }
    }

    private fun likeThread(targetThreadId: String) {
        val userId = authProvider.currentUserId ?: return

        // Optimistic update
        if (targetThreadId == threadId) {
            updateState {
                val current = (thread as? RequestState.Success)?.data ?: return@updateState this
                copy(thread = RequestState.Success(current.copy(isLikedByCurrentUser = true, likeCount = current.likeCount + 1)))
            }
        } else {
            updateState {
                val currentReplies = (replies as? RequestState.Success)?.data ?: return@updateState this
                copy(replies = RequestState.Success(currentReplies.map {
                    if (it.threadId == targetThreadId) it.copy(isLikedByCurrentUser = true, likeCount = it.likeCount + 1)
                    else it
                }))
            }
        }

        scope.launch {
            val result = threadRepository.likeThread(userId, targetThreadId)
            if (result is RequestState.Error) {
                // Revert optimistic update
                if (targetThreadId == threadId) {
                    updateState {
                        val current = (thread as? RequestState.Success)?.data ?: return@updateState this
                        copy(thread = RequestState.Success(current.copy(isLikedByCurrentUser = false, likeCount = current.likeCount - 1)))
                    }
                }
            }
        }
    }

    private fun unlikeThread(targetThreadId: String) {
        val userId = authProvider.currentUserId ?: return

        // Optimistic update
        if (targetThreadId == threadId) {
            updateState {
                val current = (thread as? RequestState.Success)?.data ?: return@updateState this
                copy(thread = RequestState.Success(current.copy(isLikedByCurrentUser = false, likeCount = current.likeCount - 1)))
            }
        } else {
            updateState {
                val currentReplies = (replies as? RequestState.Success)?.data ?: return@updateState this
                copy(replies = RequestState.Success(currentReplies.map {
                    if (it.threadId == targetThreadId) it.copy(isLikedByCurrentUser = false, likeCount = it.likeCount - 1)
                    else it
                }))
            }
        }

        scope.launch {
            val result = threadRepository.unlikeThread(userId, targetThreadId)
            if (result is RequestState.Error) {
                if (targetThreadId == threadId) {
                    updateState {
                        val current = (thread as? RequestState.Success)?.data ?: return@updateState this
                        copy(thread = RequestState.Success(current.copy(isLikedByCurrentUser = true, likeCount = current.likeCount + 1)))
                    }
                }
            }
        }
    }
}
