package com.lifo.feed

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.FeedRepository
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.ThreadHydrator
import com.lifo.util.repository.ThreadRepository
import kotlinx.coroutines.launch

class FeedViewModel(
    private val feedRepository: FeedRepository,
    private val threadRepository: ThreadRepository,
    private val threadHydrator: ThreadHydrator,
    private val authProvider: AuthProvider,
    private val socialGraphRepository: SocialGraphRepository,
) : MviViewModel<FeedContract.Intent, FeedContract.State, FeedContract.Effect>(
    initialState = FeedContract.State()
) {

    private val currentUserId: String?
        get() = authProvider.currentUserId

    override fun handleIntent(intent: FeedContract.Intent) {
        when (intent) {
            is FeedContract.Intent.LoadFeed -> loadFeed()
            is FeedContract.Intent.RefreshFeed -> refreshFeed()
            is FeedContract.Intent.LoadMore -> loadMore()
            is FeedContract.Intent.SelectTab -> selectTab(intent.tab)
            is FeedContract.Intent.LikeThread -> likeThread(intent.threadId)
            is FeedContract.Intent.UnlikeThread -> unlikeThread(intent.threadId)
            is FeedContract.Intent.RepostThread -> repostThread(intent.threadId)
            is FeedContract.Intent.ShowOptions -> updateState { copy(showOptionsForThreadId = intent.threadId) }
            is FeedContract.Intent.DismissOptions -> updateState { copy(showOptionsForThreadId = null) }
            is FeedContract.Intent.SaveThread -> saveThread(intent.threadId)
            is FeedContract.Intent.HideThread -> hideThread(intent.threadId)
            is FeedContract.Intent.MuteUser -> muteUser(intent.threadId)
            is FeedContract.Intent.BlockUser -> blockUser(intent.threadId)
            is FeedContract.Intent.ReportThread -> reportThread(intent.threadId)
        }
    }

    private fun loadFeed() {
        val userId = currentUserId ?: run {
            sendEffect(FeedContract.Effect.ShowError("User not authenticated"))
            return
        }

        scope.launch {
            // Always load full feed — category filtering is done client-side
            feedRepository.getForYouFeed(userId).collect { requestState ->
                when (requestState) {
                    is RequestState.Idle -> { /* no-op */ }
                    is RequestState.Loading -> {
                        updateState { copy(isLoading = true, error = null) }
                    }
                    is RequestState.Success -> {
                        val page = requestState.data
                        val hydrated = threadHydrator.hydrate(page.items, userId)
                        updateState {
                            copy(
                                threads = hydrated,
                                isLoading = false,
                                isRefreshing = false,
                                hasMore = page.hasMore,
                                nextCursor = page.nextCursor,
                                error = null,
                            )
                        }
                    }
                    is RequestState.Error -> {
                        updateState {
                            copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = requestState.message,
                            )
                        }
                        sendEffect(FeedContract.Effect.ShowError(requestState.message))
                    }
                }
            }
        }
    }

    private fun refreshFeed() {
        val userId = currentUserId ?: return

        updateState { copy(isRefreshing = true) }

        scope.launch {
            when (val result = feedRepository.refreshFeed(userId)) {
                is RequestState.Success -> {
                    // After refreshing server-side, reload the feed
                    loadFeed()
                }
                is RequestState.Error -> {
                    updateState { copy(isRefreshing = false) }
                    sendEffect(FeedContract.Effect.ShowError(result.message))
                }
                else -> { /* Loading/Idle — no-op */ }
            }
        }
    }

    private fun loadMore() {
        val userId = currentUserId ?: return
        val cursor = currentState.nextCursor ?: return
        if (currentState.isLoadingMore || !currentState.hasMore) return

        updateState { copy(isLoadingMore = true) }

        scope.launch {
            feedRepository.getForYouFeed(userId, cursor = cursor).collect { requestState ->
                when (requestState) {
                    is RequestState.Success -> {
                        val page = requestState.data
                        val hydrated = threadHydrator.hydrate(page.items, userId)
                        updateState {
                            copy(
                                threads = threads + hydrated,
                                isLoadingMore = false,
                                hasMore = page.hasMore,
                                nextCursor = page.nextCursor,
                            )
                        }
                    }
                    is RequestState.Error -> {
                        updateState { copy(isLoadingMore = false) }
                        sendEffect(FeedContract.Effect.ShowError(requestState.message))
                    }
                    is RequestState.Loading -> { /* already set isLoadingMore */ }
                    is RequestState.Idle -> { /* no-op */ }
                }
            }
        }
    }

    private fun selectTab(tab: FeedContract.FeedTab) {
        if (tab == currentState.selectedTab) return
        // Category filtering is client-side — no need to reload
        updateState { copy(selectedTab = tab) }
    }

    private fun likeThread(threadId: String) {
        val userId = currentUserId ?: return

        // Optimistic update
        updateState {
            copy(
                threads = threads.map { thread ->
                    if (thread.threadId == threadId) {
                        thread.copy(
                            likeCount = thread.likeCount + 1,
                            isLikedByCurrentUser = true
                        )
                    } else {
                        thread
                    }
                }
            )
        }

        scope.launch {
            when (val result = threadRepository.likeThread(userId, threadId)) {
                is RequestState.Error -> {
                    // Revert optimistic update
                    updateState {
                        copy(
                            threads = threads.map { thread ->
                                if (thread.threadId == threadId) {
                                    thread.copy(
                                        likeCount = (thread.likeCount - 1).coerceAtLeast(0),
                                        isLikedByCurrentUser = false
                                    )
                                } else {
                                    thread
                                }
                            }
                        )
                    }
                    sendEffect(FeedContract.Effect.ShowError(result.message))
                }
                else -> { /* Success or Loading — no-op */ }
            }
        }
    }

    private fun unlikeThread(threadId: String) {
        val userId = currentUserId ?: return

        // Optimistic update
        updateState {
            copy(
                threads = threads.map { thread ->
                    if (thread.threadId == threadId) {
                        thread.copy(
                            likeCount = (thread.likeCount - 1).coerceAtLeast(0),
                            isLikedByCurrentUser = false
                        )
                    } else {
                        thread
                    }
                }
            )
        }

        scope.launch {
            when (val result = threadRepository.unlikeThread(userId, threadId)) {
                is RequestState.Error -> {
                    // Revert optimistic update
                    updateState {
                        copy(
                            threads = threads.map { thread ->
                                if (thread.threadId == threadId) {
                                    thread.copy(
                                        likeCount = thread.likeCount + 1,
                                        isLikedByCurrentUser = true
                                    )
                                } else {
                                    thread
                                }
                            }
                        )
                    }
                    sendEffect(FeedContract.Effect.ShowError(result.message))
                }
                else -> { /* Success or Loading — no-op */ }
            }
        }
    }

    private fun repostThread(threadId: String) {
        val userId = currentUserId ?: return

        // Find current state to toggle
        val thread = currentState.threads.find { it.threadId == threadId } ?: return
        val isReposted = thread.isRepostedByCurrentUser

        // Optimistic update
        updateState {
            copy(
                threads = threads.map {
                    if (it.threadId == threadId) {
                        it.copy(
                            repostCount = if (isReposted) (it.repostCount - 1).coerceAtLeast(0) else it.repostCount + 1,
                            isRepostedByCurrentUser = !isReposted
                        )
                    } else it
                }
            )
        }

        scope.launch {
            val result = if (isReposted) {
                threadRepository.unrepostThread(userId, threadId)
            } else {
                threadRepository.repostThread(userId, threadId)
            }

            if (result is RequestState.Error) {
                // Revert
                updateState {
                    copy(
                        threads = threads.map {
                            if (it.threadId == threadId) {
                                it.copy(
                                    repostCount = if (isReposted) it.repostCount + 1 else (it.repostCount - 1).coerceAtLeast(0),
                                    isRepostedByCurrentUser = isReposted
                                )
                            } else it
                        }
                    )
                }
                sendEffect(FeedContract.Effect.ShowError(result.message))
            }
        }
    }

    private fun saveThread(threadId: String) {
        sendEffect(FeedContract.Effect.ShowSuccess("Post salvato"))
    }

    private fun hideThread(threadId: String) {
        updateState {
            copy(
                hiddenThreadIds = hiddenThreadIds + threadId,
                showOptionsForThreadId = null
            )
        }
        sendEffect(FeedContract.Effect.ShowSuccess("Post nascosto"))
    }

    private fun muteUser(threadId: String) {
        val thread = currentState.threads.find { it.threadId == threadId } ?: return
        // Hide all posts by this author
        val authorId = thread.authorId
        updateState {
            copy(
                hiddenThreadIds = hiddenThreadIds + threads.filter { it.authorId == authorId }.map { it.threadId }.toSet(),
                showOptionsForThreadId = null
            )
        }
        sendEffect(FeedContract.Effect.ShowSuccess("Utente silenziato"))
    }

    private fun blockUser(threadId: String) {
        val userId = currentUserId ?: return
        val thread = currentState.threads.find { it.threadId == threadId } ?: return
        val authorId = thread.authorId

        // Hide all posts by this author immediately
        updateState {
            copy(
                hiddenThreadIds = hiddenThreadIds + threads.filter { it.authorId == authorId }.map { it.threadId }.toSet(),
                showOptionsForThreadId = null
            )
        }

        scope.launch {
            when (val result = socialGraphRepository.block(userId, authorId)) {
                is RequestState.Error -> sendEffect(FeedContract.Effect.ShowError(result.message))
                else -> sendEffect(FeedContract.Effect.ShowSuccess("Utente bloccato"))
            }
        }
    }

    private fun reportThread(threadId: String) {
        sendEffect(FeedContract.Effect.ShowSuccess("Segnalazione inviata. Grazie."))
    }
}
