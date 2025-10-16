package com.lifo.mongo.repository

import com.lifo.mongo.database.dao.ChatSessionDao
import com.lifo.util.model.*
import kotlinx.coroutines.flow.*
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedContentRepositoryImpl @Inject constructor(
    private val chatSessionDao: ChatSessionDao,
    private val diaryRepository: MongoRepository  // Inject Firestore repository
) : UnifiedContentRepository {

    override fun getUnifiedContent(ownerId: String): Flow<List<HomeContentItem>> {
        android.util.Log.d("UnifiedContent", "Loading unified content for owner: $ownerId")

        return combine(
            // Load chat sessions (uses ownerId for Room database filtering)
            chatSessionDao.getAllSessions(ownerId),
            // Load diaries (Firestore uses current authenticated user)
            diaryRepository.getAllDiaries()
                .onStart {
                    android.util.Log.d("UnifiedContent", "Starting Firestore diary load")
                }
                .catch { e ->
                    android.util.Log.w("UnifiedContent", "Firestore diary load failed: ${e.message}", e)
                    // Emit empty success as fallback - don't block the entire feed
                    emit(RequestState.Success(emptyMap<LocalDate, List<Diary>>()))
                }
        ) { chatSessions, diariesResult ->
            android.util.Log.d("UnifiedContent", "Combining content: diaries=${diariesResult::class.simpleName}, chats=${chatSessions.size}")
            
            // Process chat items (always available from Room)
            val chatItems: List<HomeContentItem.ChatItem> = chatSessions.map { session -> 
                session.toHomeContentItem() 
            }
            
            // Process diary items (from Firestore)
            val diaryItems: List<HomeContentItem.DiaryItem> = when (diariesResult) {
                is RequestState.Success -> {
                    val diaryMap = diariesResult.data
                    android.util.Log.d("UnifiedContent", "Processing ${diaryMap.size} diary date groups")
                    val diaries = diaryMap.values.flatten()
                    android.util.Log.d("UnifiedContent", "Found ${diaries.size} total diary entries")
                    diaries.map { diary -> diary.toHomeContentItem() }
                }
                is RequestState.Error -> {
                    android.util.Log.w("UnifiedContent", "Diary result error: ${diariesResult.error.message}")
                    emptyList()
                }
                else -> {
                    android.util.Log.d("UnifiedContent", "Diary result idle/loading")
                    emptyList()
                }
            }
            
            android.util.Log.d("UnifiedContent", "Final counts: ${diaryItems.size} diary items, ${chatItems.size} chat items")
            
            // Combine and sort by creation date (most recent first)
            val allItems: List<HomeContentItem> = chatItems + diaryItems
            allItems.sortedByDescending { item -> item.createdAt }
        }
    }

    override fun searchContent(ownerId: String, query: String): Flow<List<HomeContentItem>> {
        return getUnifiedContent(ownerId).map { items ->
            if (query.isBlank()) {
                items
            } else {
                items.filter { item ->
                    when (item) {
                        is HomeContentItem.DiaryItem -> {
                            item.title.contains(query, ignoreCase = true) ||
                            item.content.contains(query, ignoreCase = true)
                        }
                        is HomeContentItem.ChatItem -> {
                            item.title.contains(query, ignoreCase = true) ||
                            item.summary?.contains(query, ignoreCase = true) == true ||
                            item.lastMessage?.contains(query, ignoreCase = true) == true
                        }
                    }
                }
            }
        }
    }

    override fun filterByType(ownerId: String, type: ContentType): Flow<List<HomeContentItem>> {
        return getUnifiedContent(ownerId).map { items ->
            when (type) {
                ContentType.ALL -> items
                ContentType.DIARY -> items.filterIsInstance<HomeContentItem.DiaryItem>()
                ContentType.CHAT -> items.filterIsInstance<HomeContentItem.ChatItem>()
            }
        }
    }

    override fun filterByDateRange(
        ownerId: String, 
        startDate: Long, 
        endDate: Long
    ): Flow<List<HomeContentItem>> {
        return getUnifiedContent(ownerId).map { items ->
            items.filter { item ->
                item.createdAt >= startDate && item.createdAt <= endDate
            }
        }
    }

    override fun applyFilter(
        ownerId: String,
        filter: ContentFilter,
        searchQuery: String
    ): Flow<List<HomeContentItem>> {
        val baseFlow = when (filter) {
            ContentFilter.ALL -> getUnifiedContent(ownerId)
            ContentFilter.DIARY -> filterByType(ownerId, ContentType.DIARY)
            ContentFilter.CHAT -> filterByType(ownerId, ContentType.CHAT)
            ContentFilter.TODAY -> {
                val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDay = LocalDate.now().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                filterByDateRange(ownerId, startOfDay, endOfDay)
            }
            ContentFilter.THIS_WEEK -> {
                val now = LocalDate.now()
                val startOfWeek = now.with(DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfWeek = now.with(DayOfWeek.SUNDAY).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                filterByDateRange(ownerId, startOfWeek, endOfWeek)
            }
            ContentFilter.THIS_MONTH -> {
                val now = LocalDate.now()
                val startOfMonth = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfMonth = now.withDayOfMonth(now.lengthOfMonth()).atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                filterByDateRange(ownerId, startOfMonth, endOfMonth)
            }
        }
        
        return if (searchQuery.isBlank()) {
            baseFlow
        } else {
            baseFlow.map { items ->
                items.filter { item ->
                    when (item) {
                        is HomeContentItem.DiaryItem -> {
                            item.title.contains(searchQuery, ignoreCase = true) ||
                            item.content.contains(searchQuery, ignoreCase = true)
                        }
                        is HomeContentItem.ChatItem -> {
                            item.title.contains(searchQuery, ignoreCase = true) ||
                            item.summary?.contains(searchQuery, ignoreCase = true) == true ||
                            item.lastMessage?.contains(searchQuery, ignoreCase = true) == true
                        }
                    }
                }
            }
        }
    }
}