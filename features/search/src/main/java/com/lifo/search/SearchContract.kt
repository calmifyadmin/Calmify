package com.lifo.search

import androidx.compose.runtime.Immutable
import com.lifo.util.mvi.MviContract
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.ThreadRepository

object SearchContract {

    enum class SearchFilter { ALL, THREADS, USERS }

    sealed interface Intent : MviContract.Intent {
        data class UpdateQuery(val query: String) : Intent
        data object Search : Intent
        data object ClearSearch : Intent
        data class SelectFilter(val filter: SearchFilter) : Intent
    }

    @Immutable
    data class State(
        val query: String = "",
        val threadResults: List<ThreadRepository.Thread> = emptyList(),
        val userResults: List<SocialGraphRepository.SocialUser> = emptyList(),
        val isSearching: Boolean = false,
        val selectedFilter: SearchFilter = SearchFilter.ALL,
        val hasSearched: Boolean = false,
        val error: String? = null,
    ) : MviContract.State

    sealed interface Effect : MviContract.Effect {
        data class ShowError(val message: String) : Effect
        data class NavigateToThread(val threadId: String) : Effect
        data class NavigateToUserProfile(val userId: String) : Effect
    }
}
