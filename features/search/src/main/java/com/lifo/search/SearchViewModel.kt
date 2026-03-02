package com.lifo.search

import com.lifo.util.model.RequestState
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.SearchRepository
import com.lifo.util.repository.SocialGraphRepository
import com.lifo.util.repository.ThreadRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepository: SearchRepository
) : MviViewModel<SearchContract.Intent, SearchContract.State, SearchContract.Effect>(
    initialState = SearchContract.State()
) {

    private var searchJob: Job? = null

    override fun handleIntent(intent: SearchContract.Intent) {
        when (intent) {
            is SearchContract.Intent.UpdateQuery -> updateQuery(intent.query)
            is SearchContract.Intent.Search -> performSearch()
            is SearchContract.Intent.ClearSearch -> clearSearch()
            is SearchContract.Intent.SelectFilter -> selectFilter(intent.filter)
        }
    }

    private fun updateQuery(query: String) {
        updateState { copy(query = query, error = null) }
    }

    private fun performSearch() {
        val query = currentState.query.trim()
        if (query.isBlank()) {
            sendEffect(SearchContract.Effect.ShowError("Please enter a search query"))
            return
        }

        searchJob?.cancel()
        updateState { copy(isSearching = true, error = null) }

        searchJob = scope.launch {
            var threads: List<ThreadRepository.Thread> = emptyList()
            var users: List<SocialGraphRepository.SocialUser> = emptyList()
            var errorMessage: String? = null

            // Launch both searches in parallel
            val threadJob = launch {
                searchRepository.searchThreads(query).collect { result ->
                    when (result) {
                        is RequestState.Success -> threads = result.data
                        is RequestState.Error -> errorMessage = result.message
                        else -> { /* Loading / Idle — handled by isSearching flag */ }
                    }
                }
            }

            val userJob = launch {
                searchRepository.searchUsers(query).collect { result ->
                    when (result) {
                        is RequestState.Success -> users = result.data
                        is RequestState.Error -> {
                            if (errorMessage == null) errorMessage = result.message
                        }
                        else -> { /* Loading / Idle */ }
                    }
                }
            }

            // Wait for both to complete
            threadJob.join()
            userJob.join()

            if (errorMessage != null) {
                updateState {
                    copy(
                        isSearching = false,
                        hasSearched = true,
                        error = errorMessage
                    )
                }
                sendEffect(SearchContract.Effect.ShowError(errorMessage!!))
            } else {
                updateState {
                    copy(
                        threadResults = threads,
                        userResults = users,
                        isSearching = false,
                        hasSearched = true,
                        error = null
                    )
                }
            }
        }
    }

    private fun clearSearch() {
        searchJob?.cancel()
        updateState {
            SearchContract.State()
        }
    }

    private fun selectFilter(filter: SearchContract.SearchFilter) {
        updateState { copy(selectedFilter = filter) }

        // Re-search if we already have a query
        if (currentState.query.isNotBlank() && currentState.hasSearched) {
            performSearch()
        }
    }
}
