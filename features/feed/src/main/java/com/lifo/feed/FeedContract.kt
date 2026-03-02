package com.lifo.feed

import androidx.compose.runtime.Immutable
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.ThreadRepository

object FeedContract {

    enum class FeedTab { FOR_YOU, FOLLOWING }

    sealed interface Intent : MviContract.Intent {
        data object LoadFeed : Intent
        data object RefreshFeed : Intent
        data object LoadMore : Intent
        data class SelectTab(val tab: FeedTab) : Intent
        data class LikeThread(val threadId: String) : Intent
        data class UnlikeThread(val threadId: String) : Intent
    }

    @Immutable
    data class State(
        val threads: List<ThreadRepository.Thread> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val selectedTab: FeedTab = FeedTab.FOR_YOU,
        val hasMore: Boolean = false,
        val nextCursor: String? = null,
        val error: String? = null,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class ShowError(val message: String) : Effect
        data class NavigateToThread(val threadId: String) : Effect
        data class NavigateToUserProfile(val userId: String) : Effect
    }
}
