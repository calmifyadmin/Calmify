package com.lifo.network.repository

import com.lifo.network.KtorApiClient
import com.lifo.shared.model.ChatSessionProto
import com.lifo.shared.model.DiaryProto
import com.lifo.util.model.ContentFilter
import com.lifo.util.model.ContentType
import com.lifo.util.model.HomeContentItem
import com.lifo.util.model.Mood
import com.lifo.util.model.RequestState
import com.lifo.util.repository.UnifiedContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

/**
 * Client-side aggregation of Diary + Chat content using existing REST APIs.
 * No new server endpoint needed — combines results from two existing endpoints.
 */
class KtorUnifiedContentRepository(
    private val api: KtorApiClient,
) : UnifiedContentRepository {

    override fun getUnifiedContent(ownerId: String): Flow<List<HomeContentItem>> = flow {
        emit(getAllContent())
    }

    override fun searchContent(ownerId: String, query: String): Flow<List<HomeContentItem>> = flow {
        val all = getAllContent()
        val normalizedQuery = query.lowercase()
        emit(all.filter { item ->
            when (item) {
                is HomeContentItem.DiaryItem -> item.title.lowercase().contains(normalizedQuery) ||
                    item.content.lowercase().contains(normalizedQuery)
                is HomeContentItem.ChatItem -> item.title.lowercase().contains(normalizedQuery)
            }
        })
    }

    override fun filterByType(ownerId: String, type: ContentType): Flow<List<HomeContentItem>> = flow {
        val all = getAllContent()
        emit(when (type) {
            ContentType.DIARY -> all.filterIsInstance<HomeContentItem.DiaryItem>()
            ContentType.CHAT -> all.filterIsInstance<HomeContentItem.ChatItem>()
            ContentType.ALL -> all
        })
    }

    override fun filterByDateRange(ownerId: String, startDate: Long, endDate: Long): Flow<List<HomeContentItem>> = flow {
        val all = getAllContent()
        emit(all.filter { it.createdAt in startDate..endDate })
    }

    override fun applyFilter(ownerId: String, filter: ContentFilter, searchQuery: String): Flow<List<HomeContentItem>> = flow {
        var items = getAllContent()

        if (searchQuery.isNotBlank()) {
            val normalizedQuery = searchQuery.lowercase()
            items = items.filter { item ->
                when (item) {
                    is HomeContentItem.DiaryItem -> item.title.lowercase().contains(normalizedQuery) ||
                        item.content.lowercase().contains(normalizedQuery)
                    is HomeContentItem.ChatItem -> item.title.lowercase().contains(normalizedQuery)
                }
            }
        }

        emit(when (filter) {
            ContentFilter.DIARY -> items.filterIsInstance<HomeContentItem.DiaryItem>()
            ContentFilter.CHAT -> items.filterIsInstance<HomeContentItem.ChatItem>()
            else -> items
        })
    }

    private suspend fun getAllContent(): List<HomeContentItem> {
        val items = mutableListOf<HomeContentItem>()

        val diaryResult = api.get<UnifiedDiaryListDto>("/api/v1/diary?limit=50")
        if (diaryResult is RequestState.Success) {
            items.addAll(diaryResult.data.data.map { it.toHomeContentItem() })
        }

        val chatResult = api.get<UnifiedChatListDto>("/api/v1/chat/sessions?limit=50")
        if (chatResult is RequestState.Success) {
            items.addAll(chatResult.data.data.map { it.toHomeContentItem() })
        }

        return items.sortedByDescending { it.createdAt }
    }

    private fun DiaryProto.toHomeContentItem(): HomeContentItem.DiaryItem =
        HomeContentItem.DiaryItem(
            id = id,
            title = title,
            createdAt = dateMillis,
            content = description,
            mood = runCatching { Mood.valueOf(mood) }.getOrDefault(Mood.Neutral),
            images = images,
        )

    private fun ChatSessionProto.toHomeContentItem(): HomeContentItem.ChatItem =
        HomeContentItem.ChatItem(
            id = id,
            title = title,
            createdAt = createdAtMillis,
            summary = null,
            messageCount = messageCount,
            lastMessage = null,
            lastMessageAt = lastMessageAtMillis,
            mood = null,
            aiModel = aiModel,
            isLiveMode = false,
        )
}

@Serializable
data class UnifiedDiaryListDto(
    val data: List<DiaryProto> = emptyList(),
)

@Serializable
data class UnifiedChatListDto(
    val data: List<ChatSessionProto> = emptyList(),
)
