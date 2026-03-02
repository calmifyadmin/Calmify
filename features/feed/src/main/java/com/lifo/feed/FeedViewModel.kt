package com.lifo.feed

import com.lifo.util.auth.AuthProvider
import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.FeedRepository
import com.lifo.util.repository.ThreadRepository
import kotlinx.coroutines.launch

class FeedViewModel(
    private val feedRepository: FeedRepository,
    private val threadRepository: ThreadRepository,
    private val authProvider: AuthProvider,
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
        }
    }

    private fun loadFeed() {
        val userId = currentUserId ?: run {
            sendEffect(FeedContract.Effect.ShowError("User not authenticated"))
            return
        }

        scope.launch {
            val feedFlow = when (currentState.selectedTab) {
                FeedContract.FeedTab.FOR_YOU -> feedRepository.getForYouFeed(userId)
                FeedContract.FeedTab.FOLLOWING -> feedRepository.getFollowingFeed(userId)
            }

            feedFlow.collect { requestState ->
                when (requestState) {
                    is RequestState.Idle -> { /* no-op */ }
                    is RequestState.Loading -> {
                        updateState { copy(isLoading = true, error = null) }
                    }
                    is RequestState.Success -> {
                        val page = requestState.data
                        updateState {
                            copy(
                                threads = page.items,
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
            val feedFlow = when (currentState.selectedTab) {
                FeedContract.FeedTab.FOR_YOU -> feedRepository.getForYouFeed(userId, cursor = cursor)
                FeedContract.FeedTab.FOLLOWING -> feedRepository.getFollowingFeed(userId, cursor = cursor)
            }

            feedFlow.collect { requestState ->
                when (requestState) {
                    is RequestState.Success -> {
                        val page = requestState.data
                        updateState {
                            copy(
                                threads = threads + page.items,
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
        updateState {
            copy(
                selectedTab = tab,
                threads = emptyList(),
                nextCursor = null,
                hasMore = false,
                error = null,
            )
        }
        loadFeed()
    }

    private fun likeThread(threadId: String) {
        val userId = currentUserId ?: return

        // Optimistic update
        updateState {
            copy(
                threads = threads.map { thread ->
                    if (thread.threadId == threadId) {
                        thread.copy(likeCount = thread.likeCount + 1)
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
                                    thread.copy(likeCount = (thread.likeCount - 1).coerceAtLeast(0))
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
                        thread.copy(likeCount = (thread.likeCount - 1).coerceAtLeast(0))
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
                                    thread.copy(likeCount = thread.likeCount + 1)
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
}
