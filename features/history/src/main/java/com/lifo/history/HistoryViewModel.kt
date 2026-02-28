package com.lifo.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lifo.util.repository.MongoRepository
import com.lifo.util.repository.UnifiedContentRepository
import com.lifo.util.model.ContentFilter
import com.lifo.util.model.HomeContentItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val isDiariesEmpty: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val unifiedContentRepository: UnifiedContentRepository,
    private val diaryRepository: MongoRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    companion object {
        private const val RECENT_ITEMS_LIMIT = 4 // Show last 4 items in each section
    }

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")
    }

    /**
     * Load history for both chats and diaries
     */
    fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val userId = getCurrentUserId()

                // Observe unified content and separate into chat/diary sections
                unifiedContentRepository.getUnifiedContent(userId)
                    .catch { e ->
                        println("[HistoryViewModel] ERROR: Error loading history: ${e.message}")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Failed to load history"
                            )
                        }
                    }
                    .collect { allItems ->
                        // Separate chats and diaries
                        val chats = allItems
                            .filterIsInstance<HomeContentItem.ChatItem>()
                            .take(RECENT_ITEMS_LIMIT)

                        val diaries = allItems
                            .filterIsInstance<HomeContentItem.DiaryItem>()
                            .take(RECENT_ITEMS_LIMIT)

                        _uiState.update {
                            it.copy(
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }

    /**
     * Refresh history data
     */
    fun refreshHistory() {
        loadHistory()
    }

    /**
     * Load full chat history with search query
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
     * Load full diary history with search query
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
