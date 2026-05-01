package com.lifo.feed

import androidx.compose.runtime.Immutable
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.ThreadRepository

object FeedContract {

    /**
     * Feed top tabs. UI label resolved at render site via inline
     * `when (tab) -> Strings.Feed.X` to keep this contract model UI-free.
     */
    enum class FeedTab {
        ALL,
        SCOPERTE,
        SFIDE,
        DOMANDE,
    }

    sealed interface Intent : MviContract.Intent {
        data object LoadFeed : Intent
        data object RefreshFeed : Intent
        data object LoadMore : Intent
        data class SelectTab(val tab: FeedTab) : Intent
        data class LikeThread(val threadId: String) : Intent
        data class UnlikeThread(val threadId: String) : Intent
        data class RepostThread(val threadId: String) : Intent
        data class ShowOptions(val threadId: String) : Intent
        data object DismissOptions : Intent
        data class SaveThread(val threadId: String) : Intent
        data class HideThread(val threadId: String) : Intent
        data class MuteUser(val threadId: String) : Intent
        data class BlockUser(val threadId: String) : Intent
        data class ReportThread(val threadId: String) : Intent
    }

    @Immutable
    data class State(
        val threads: List<ThreadRepository.Thread> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val selectedTab: FeedTab = FeedTab.ALL,
        val hasMore: Boolean = false,
        val nextCursor: String? = null,
        val error: String? = null,
        val showOptionsForThreadId: String? = null,
        val hiddenThreadIds: Set<String> = emptySet(),
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class ShowError(val message: String) : Effect
        data class ShowSuccess(val message: String) : Effect
        data class NavigateToThread(val threadId: String) : Effect
        data class NavigateToUserProfile(val userId: String) : Effect
    }
}
