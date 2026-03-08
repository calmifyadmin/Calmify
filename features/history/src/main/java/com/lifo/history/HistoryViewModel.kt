package com.lifo.history

import com.lifo.util.auth.AuthProvider
import com.lifo.util.mvi.MviContract
import com.lifo.util.mvi.MviViewModel
import com.lifo.util.repository.MongoRepository
import com.lifo.util.repository.UnifiedContentRepository
import com.lifo.util.model.ContentFilter
import com.lifo.util.model.HomeContentItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * MVI Contract for the History screen.
 */
object HistoryContract {

    sealed interface Intent : MviContract.Intent {
        /** Load recent history (chat + diary). */
        data object LoadHistory : Intent
        /** Refresh history data. */
        data object RefreshHistory : Intent
        /** Set active filter. */
        data class SetFilter(val filter: ContentFilter) : Intent
    }

    // State is the existing HistoryUiState data class (see below).

    sealed interface Effect : MviContract.Effect {
        // No one-shot effects needed for now; placeholder for future use.
    }
}

/**
 * ViewModel for the History screen
 * Manages chat and diary history with separate sections
 */
data class HistoryUiState(
    val chatHistory: List<HomeContentItem.ChatItem> = emptyList(),
    val diaryHistory: List<HomeContentItem.DiaryItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isChatsEmpty: Boolean = true,
    val isDiariesEmpty: Boolean = true,
    val activeFilter: ContentFilter = ContentFilter.ALL
) : MviContract.State

class HistoryViewModel constructor(
    private val unifiedContentRepository: UnifiedContentRepository,
    private val diaryRepository: MongoRepository,
    private val authProvider: AuthProvider
) : MviViewModel<HistoryContract.Intent, HistoryUiState, HistoryContract.Effect>(
    initialState = HistoryUiState()
) {

    companion object {
        private const val RECENT_ITEMS_LIMIT = 4 // Show last 4 items in each section
    }

    /**
     * Backward-compatible alias so existing callers that read
     * `viewModel.uiState.collectAsState()` keep compiling.
     */
    val uiState: StateFlow<HistoryUiState> get() = state

    init {
        onIntent(HistoryContract.Intent.LoadHistory)
    }

    // ── MVI plumbing ────────────────────────────────────────────────────

    override fun handleIntent(intent: HistoryContract.Intent) {
        when (intent) {
            is HistoryContract.Intent.LoadHistory -> loadHistory()
            is HistoryContract.Intent.RefreshHistory -> loadHistory()
            is HistoryContract.Intent.SetFilter -> {
                updateState { copy(activeFilter = intent.filter) }
                loadHistory()
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun getCurrentUserId(): String {
        return authProvider.currentUserId
            ?: throw IllegalStateException("User not authenticated")
    }

    /**
     * Load history for both chats and diaries
     */
    private fun loadHistory() {
        scope.launch {
            updateState { copy(isLoading = true, error = null) }

            try {
                val userId = getCurrentUserId()
                val filter = currentState.activeFilter

                val contentFlow = if (filter == ContentFilter.ALL) {
                    unifiedContentRepository.getUnifiedContent(userId)
                } else {
                    unifiedContentRepository.applyFilter(
                        ownerId = userId,
                        filter = filter,
                        searchQuery = ""
                    )
                }

                contentFlow
                    .catch { e ->
                        println("[HistoryViewModel] ERROR: Error loading history: ${e.message}")
                        updateState {
                            copy(
                                isLoading = false,
                                error = e.message ?: "Failed to load history"
                            )
                        }
                    }
                    .collect { allItems ->
                        val chats = allItems.filterIsInstance<HomeContentItem.ChatItem>()
                        val diaries = allItems.filterIsInstance<HomeContentItem.DiaryItem>()

                        updateState {
                            copy(
                                chatHistory = chats,
                                diaryHistory = diaries,
                                isLoading = false,
                                isChatsEmpty = chats.isEmpty(),
                                isDiariesEmpty = diaries.isEmpty(),
                                error = null
                            )
                        }
                    }
            } catch (e: Exception) {
                println("[HistoryViewModel] ERROR: Error loading history: ${e.message}")
                updateState {
                    copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    // ── Backward-compatible public API ──────────────────────────────────

    /**
     * Refresh history data. Delegates to the MVI intent.
     * Kept as a public function so existing callers (`viewModel.refreshHistory()`) compile.
     */
    fun refreshHistory() {
        onIntent(HistoryContract.Intent.RefreshHistory)
    }

    /**
     * Load full chat history with search query.
     *
     * Returns a Flow directly (used by ChatHistoryFullScreen composable).
     * Kept as a regular function for backward compatibility — the full-screen
     * composables call `viewModel.loadFullChatHistory(query)` and collect the
     * returned Flow via `collectAsState`.
     */
    fun loadFullChatHistory(searchQuery: String = ""): Flow<List<HomeContentItem.ChatItem>> {
        return try {
            val userId = getCurrentUserId()
            unifiedContentRepository.applyFilter(
                ownerId = userId,
                filter = ContentFilter.CHAT,
                searchQuery = searchQuery
            ).map { items ->
                items.filterIsInstance<HomeContentItem.ChatItem>()
            }
        } catch (e: Exception) {
            println("[HistoryViewModel] ERROR: Error loading full chat history: ${e.message}")
            flowOf(emptyList())
        }
    }

    /**
     * Load full diary history with search query.
     *
     * Returns a Flow directly (used by DiaryHistoryFullScreen composable).
     * Kept as a regular function for backward compatibility — the full-screen
     * composables call `viewModel.loadFullDiaryHistory(query)` and collect the
     * returned Flow via `collectAsState`.
     */
    fun loadFullDiaryHistory(searchQuery: String = ""): Flow<List<HomeContentItem.DiaryItem>> {
        return try {
            val userId = getCurrentUserId()
            unifiedContentRepository.applyFilter(
                ownerId = userId,
                filter = ContentFilter.DIARY,
                searchQuery = searchQuery
            ).map { items ->
                items.filterIsInstance<HomeContentItem.DiaryItem>()
            }
        } catch (e: Exception) {
            println("[HistoryViewModel] ERROR: Error loading full diary history: ${e.message}")
            flowOf(emptyList())
        }
    }
}
